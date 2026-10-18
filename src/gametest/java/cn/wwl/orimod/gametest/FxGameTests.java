package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.network.SpiritFxPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** R15: spirit effects leave the server as one settings-neutral payload per burst, to the vanilla particle audience. */
@GameTestHolder("orimod_gametest")
public final class FxGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var kind : SpiritFxKind.values()) add(tests, "route_" + kind.name().toLowerCase(java.util.Locale.ROOT), h -> route(h, kind));
        add(tests, "out_of_range_receives_nothing", FxGameTests::outOfRange);
        add(tests, "blade_hit_sends_payload_not_vanilla_particles", FxGameTests::bladeHit);
        add(tests, "no_source_encodes_minus_one", FxGameTests::noSource);
        return tests;
    }

    /** Drain everything the server queued for this connection, unwrapping bundles. */
    static List<Packet<?>> drain(EmbeddedChannel channel) {
        channel.runPendingTasks();
        var out = new ArrayList<Packet<?>>();
        Object message;
        while ((message = channel.readOutbound()) != null) flatten(message, out);
        return out;
    }

    private static void flatten(Object message, List<Packet<?>> out) {
        if (message instanceof BundlePacket<?> bundle) { for (var inner : bundle.subPackets()) flatten(inner, out); }
        else if (message instanceof Packet<?> packet) out.add(packet);
    }

    static List<SpiritFxPayload> fx(List<Packet<?>> packets) {
        var out = new ArrayList<SpiritFxPayload>();
        for (var packet : packets)
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof SpiritFxPayload payload) out.add(payload);
        return out;
    }

    private static void route(GameTestHelper helper, SpiritFxKind kind) {
        try (var near = Fixtures.connectedPlayer(helper)) {
            drain(near.channel());
            Vec3 at = helper.absoluteVec(new Vec3(6, 3, 6));
            SpiritFx.emit(helper.getLevel(), kind, at, near.player());
            var got = fx(drain(near.channel()));
            helper.assertValueEqual(got.size(), 1, "one payload per burst");
            var payload = got.getFirst();
            helper.assertValueEqual(payload.kind(), kind, "kind");
            Fixtures.near(helper, payload.x(), at.x, "x"); Fixtures.near(helper, payload.y(), at.y, "y"); Fixtures.near(helper, payload.z(), at.z, "z");
            helper.assertValueEqual(payload.sourceEntity(), near.player().getId(), "source entity id");
        }
    }

    private static void outOfRange(GameTestHelper helper) {
        try (var far = Fixtures.connectedPlayer(helper)) {
            Vec3 at = helper.absoluteVec(new Vec3(6, 3, 6));
            far.player().setPos(at.add(SpiritFx.RANGE + 8, 0, 0));
            drain(far.channel());
            SpiritFx.emit(helper.getLevel(), SpiritFxKind.STOMP_IMPACT, at, null);
            helper.assertTrue(fx(drain(far.channel())).isEmpty(), "player beyond " + SpiritFx.RANGE + " blocks received a spirit effect");
        }
    }

    private static void noSource(GameTestHelper helper) {
        try (var near = Fixtures.connectedPlayer(helper)) {
            drain(near.channel());
            SpiritFx.emit(helper.getLevel(), SpiritFxKind.ARROW_TRAIL, helper.absoluteVec(new Vec3(6, 3, 6)), null);
            var got = fx(drain(near.channel()));
            helper.assertValueEqual(got.size(), 1, "payload count");
            helper.assertValueEqual(got.getFirst().sourceEntity(), SpiritFxPayload.NO_SOURCE, "missing source");
        }
    }

    private static void bladeHit(GameTestHelper helper) {
        try (var connected = Fixtures.connectedPlayer(helper)) {
            var player = connected.player();
            SpiritForm.toggle(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
            player.setYRot(0); player.setXRot(0);
            var target = (Mob)helper.spawn(EntityType.ZOMBIE, new Vec3(5.5, 2, 7));
            target.setNoAi(true);
            try {
                drain(connected.channel());
                SpiritCombat.attack(player);
                var state = player.getData(SpiritData.COMBAT);
                helper.assertValueEqual(state.mode, "blade", "Blade fixture did not start");
                state.startedAt -= SpiritCombat.windup(1);
                SpiritCombat.tick(new PlayerTickEvent.Post(player));
                helper.assertValueEqual(state.bladeHits, 1, "blade hit");
                var packets = drain(connected.channel());
                var hits = fx(packets).stream().filter(p -> p.kind() == SpiritFxKind.BLADE_HIT).toList();
                helper.assertValueEqual(hits.size(), 1, "blade hit effect payloads");
                helper.assertValueEqual(hits.getFirst().sourceEntity(), player.getId(), "blade effect source");
                helper.assertTrue(packets.stream().noneMatch(p -> p instanceof ClientboundLevelParticlesPacket),
                        "server still sent vanilla particles, bypassing client effect settings");
            } finally { SpiritCombat.cancel(player, "test_cleanup"); target.discard(); }
        }
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("fx", "fx." + name, "orimod_gametest:room", 40, 0, true,
                helper -> { run.accept(helper); helper.succeed(); }));
    }
}
