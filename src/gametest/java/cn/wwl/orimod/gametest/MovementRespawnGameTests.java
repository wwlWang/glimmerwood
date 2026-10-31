package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.MovementResultPayload;
import cn.wwl.orimod.spirit.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class MovementRespawnGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        tests.add(new TestFunction("movement_respawn", "movement_respawn.login_initializes_relocation", "orimod_gametest:room", 80, 0, true,
                h -> {
                    try (var c = Fixtures.connectedPlayer(h)) {
                        // Inspect synchronously: no PlayerTick may mask a skipped login teleport.
                        var s = c.player().getData(SpiritData.MOVEMENT);
                        h.assertValueEqual(s.dimension, c.player().level().dimension().location().toString(), "Login teleport did not initialize dimension");
                        h.assertTrue(s.relocations >= 1, "Login teleport did not record relocation");
                        h.assertTrue(s.previousPosition != null, "Login teleport did not initialize previous position");
                        h.assertTrue(s.airJumpSpent && s.airDashSpent && !s.groundJumpAvailable, "Login granted an airborne jump or dash before landing");
                    }
                    h.succeed();
                }));
        for (String name : List.of("respawn_packet_order", "removed_teleport_callback", "removed_cancel_callback", "replaced_instance_callback", "merchant_quote_after_respawn", "replaced_reject_callback"))
            tests.add(new TestFunction("movement_respawn", "movement_respawn." + name, "orimod_gametest:room", 80, 0, true,
                    h -> { check(h, name); h.succeed(); }));
        return tests;
    }

    private static void check(GameTestHelper h, String name) {
        var c = Fixtures.connectedPlayer(h);
        var old = c.player();
        var list = old.server.getPlayerList();
        ServerPlayer current = null;
        cn.wwl.orimod.entity.GladesMerchant merchant = null;
        cn.wwl.orimod.network.MerchantBuyPayload purchase = null;
        try {
            SpiritForm.toggle(old);
            old.setRespawnPosition(h.getLevel().dimension(), h.absolutePos(new net.minecraft.core.BlockPos(5, 2, 5)), 0, true, false);
            old.getData(SpiritData.MOVEMENT).actionId = 100;
            if (name.equals("merchant_quote_after_respawn")) {
                old.setData(SpiritData.PROGRESS, cn.wwl.orimod.progression.SpiritProgress.EMPTY.withLight(500));
                merchant = cn.wwl.orimod.entity.OriMobs.GLADES_MERCHANT.get().create(h.getLevel());
                merchant.bind(1, old.blockPosition(), old.blockPosition().east(2), 0);
                h.getLevel().addFreshEntity(merchant);
                cn.wwl.orimod.progression.MerchantTrading.open(old, merchant);
                var quote = FxGameTests.drain(c.channel()).stream().filter(p -> p instanceof ClientboundCustomPayloadPacket cp
                        && cp.payload() instanceof cn.wwl.orimod.network.MerchantOfferPayload).map(p -> (cn.wwl.orimod.network.MerchantOfferPayload)((ClientboundCustomPayloadPacket)p).payload()).reduce((a,b)->b).orElseThrow();
                purchase = new cn.wwl.orimod.network.MerchantBuyPayload(quote.entityId(), quote.merchant(), quote.day(), quote.token(), "tree");
            }
            FxGameTests.drain(c.channel());
            current = list.respawn(old, false, Entity.RemovalReason.KILLED);
            // Same assignment as vanilla ServerGamePacketListenerImpl.handleClientCommand.
            old.connection.player = current;
            var packets = FxGameTests.drain(c.channel());
            h.assertTrue(current != old && current.getId() == old.getId(), "Fixture did not replace player with same entity id");
            h.assertTrue(old.isRemoved() && list.getPlayer(old.getUUID()) == current, "Fixture did not retire old player");
            h.assertTrue(packets.stream().anyMatch(p -> p instanceof ClientboundRespawnPacket), "Fixture missed actual respawn packet");
            if (name.equals("respawn_packet_order")) {
                var stale = packets.stream().filter(p -> p instanceof ClientboundCustomPayloadPacket cp
                        && cp.payload() instanceof MovementResultPayload m && m.actionId() > 100).toList();
                h.assertTrue(stale.isEmpty(), "Old life receipt after client respawn: " + stale);
            } else if (name.equals("merchant_quote_after_respawn")) {
                cn.wwl.orimod.progression.MerchantTrading.buy(old, purchase);
                cn.wwl.orimod.progression.MerchantTrading.buy(current, purchase);
                var delayed = FxGameTests.drain(c.channel());
                h.assertFalse(delayed.stream().anyMatch(p -> p instanceof ClientboundCustomPayloadPacket cp
                        && cp.payload() instanceof cn.wwl.orimod.network.MerchantOfferPayload), "Merchant callback replied through retired player");
                h.assertFalse((boolean)cn.wwl.orimod.progression.MerchantTrading.debug(current).get("open"), "Retired trade remained busy");
                h.assertValueEqual(current.getData(SpiritData.PROGRESS).light(), 500, "Retired callback charged new life");
            } else {
                ServerPlayer source = old;
                if (name.startsWith("replaced_")) {
                    // A retained, non-removed duplicate is invalid even before isRemoved becomes true.
                    source = new ServerPlayer(old.server, h.getLevel(), old.getGameProfile(), old.clientInformation());
                    source.connection = current.connection;
                    source.setData(SpiritData.AWAKENED, true); source.setData(SpiritData.FORM, true);
                    source.getData(SpiritData.MOVEMENT).actionId = 100;
                    h.assertFalse(source.isRemoved(), "Duplicate fixture must test identity independently of removed flag");
                }
                int oldCursor = source.getData(SpiritData.MOVEMENT).actionId;
                if (name.equals("removed_teleport_callback")) {
                    current.connection.player = source;
                    try { current.connection.teleport(current.getX(), current.getY(), current.getZ(), 0, 0); }
                    finally { current.connection.player = current; }
                } else if (name.equals("replaced_reject_callback")) {
                    var stale = source.getData(SpiritData.MOVEMENT);
                    source.setPos(current.position()); source.setOnGround(false);
                    stale.dimension = source.level().dimension().location().toString(); stale.active = true;
                    stale.lastInput = source.level().getGameTime();
                    stale.jumps.add(new MovementState.JumpRequest(8, stale.lastInput - 20, stale.lastInput - 1, source.position(), Vec3.ZERO, 0, null, true));
                    SpiritMovement.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(source));
                } else SpiritMovement.cancel(source, source.getData(SpiritData.MOVEMENT));
                h.assertTrue(receipts(c).isEmpty(), "Retired instance emitted a movement result: " + name);
                h.assertValueEqual(source.getData(SpiritData.MOVEMENT).actionId, oldCursor, "Retired instance consumed an action id");
            }
            var state = current.getData(SpiritData.MOVEMENT);
            int cursor = state.actionId;
            SpiritMovement.teleported(current);
            var live = receipts(c);
            h.assertTrue(live.stream().anyMatch(m -> m.mode() == MoveMode.RELOCATE && m.actionId() == cursor + 1), "Current life lost relocation receipt");
            h.assertTrue(state.actionId < 100, "Old life counter carried into new life");
        } finally {
            cn.wwl.orimod.progression.MerchantTrading.close(old);
            cn.wwl.orimod.world.RuinLocator.cancel(old);
            if (merchant != null) merchant.discard();
            // The old fixture's close() would remove the new UUID from PlayerList: close the live instance instead.
            if (current != null) list.remove(current); else list.remove(old);
            c.channel().finishAndReleaseAll();
        }
    }
    private static List<MovementResultPayload> receipts(Fixtures.ConnectedPlayer c) {
        return FxGameTests.drain(c.channel()).stream().filter(p -> p instanceof ClientboundCustomPayloadPacket cp
                && cp.payload() instanceof MovementResultPayload).map(p -> (MovementResultPayload)((ClientboundCustomPayloadPacket)p).payload()).toList();
    }
}
