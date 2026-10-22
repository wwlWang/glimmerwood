package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.combat.SpiritFlame;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.BladeSupport;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class BladeMotionGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String kind : List.of("floor", "edge", "gap", "trapdoor_open", "trapdoor_closed"))
            add(tests, kind, h -> {
                var p = Fixtures.player(h);
                for (int x = 3; x <= 7; x++) for (int z = 3; z <= 9; z++) {
                    h.setBlock(x, 1, z, Blocks.STONE);
                    h.setBlock(x, 2, z, Blocks.AIR); h.setBlock(x, 3, z, Blocks.AIR);
                }
                p.setPos(h.absoluteVec(new Vec3(5.5, 2, 5.5))); p.setOnGround(true);
                if (!kind.equals("floor")) for (int x = 3; x <= 7; x++) {
                    if (kind.startsWith("trapdoor")) h.setBlock(x, 1, 6, Blocks.OAK_TRAPDOOR.defaultBlockState()
                            .setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, kind.endsWith("open")));
                    else {
                        h.setBlock(x, 1, 6, Blocks.AIR);
                        if (kind.equals("edge")) { h.setBlock(x, 1, 7, Blocks.AIR); h.setBlock(x, 1, 8, Blocks.AIR); }
                    }
                }
                // Gap has a supported destination; only the complete path can reject it.
                Vec3 path = new Vec3(0, 0, kind.equals("gap") ? 2 : .6);
                h.assertValueEqual(BladeSupport.supported(p, path), kind.equals("floor") || kind.equals("trapdoor_closed"), "Real support: " + kind);
            });
        add(tests, "server_ground_keeps_velocity", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
            p.setOnGround(true); p.setDeltaMovement(.2, 0, .1); p.hurtMarked = false;
            try {
                SpiritCombat.attack(p);
                h.assertValueEqual(p.getDeltaMovement(), new Vec3(.2,0,.1), "Ground step must not send a competing velocity");
                h.assertFalse(p.hurtMarked, "Ground step marked for a velocity packet");
            } finally { SpiritCombat.cancel(p, "test"); }
        });
        add(tests, "server_air_budget", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
            p.setOnGround(false);
            try {
                for (int i = 0; i < 4; i++) {
                    p.setDeltaMovement(.1, -.5, .2);
                    SpiritCombat.attack(p);
                    Fixtures.near(h, p.getDeltaMovement().y, i < 3 ? -.04 : -.5, "Air hold limit");
                    SpiritCombat.cancel(p, "test");
                }
                p.setOnGround(true); SpiritCombat.tick(new PlayerTickEvent.Post(p));
                h.assertValueEqual(p.getData(SpiritData.COMBAT).airHolds, 0, "Idle landing resets air holds");
            } finally { SpiritCombat.cancel(p, "test"); }
        });
        add(tests, "companion_command", h -> {
            try (var connection = Fixtures.connectedPlayer(h)) {
                var p = connection.player(); SpiritForm.toggle(p);
                var target = h.spawn(EntityType.HUSK, new Vec3(5.5, 2, 7)); target.setNoAi(true);
                try {
                    var dispatcher = p.server.getCommands().getDispatcher();
                    var source = p.createCommandSourceStack().withPermission(2);
                    h.assertTrue(p.getData(SpiritData.COMPANION_FLAME), "Flame enabled by default");
                    dispatcher.execute("ori companion flame off", source);
                    h.assertFalse(p.getData(SpiritData.COMPANION_FLAME), "OP disabled flame");
                    float health = target.getHealth(); int shots = SpiritFlame.fired;
                    SpiritFlame.tick(new PlayerTickEvent.Post(p));
                    Fixtures.near(h, target.getHealth(), health, "Disabled flame damaged target");
                    h.assertValueEqual(SpiritFlame.fired, shots, "Disabled flame emitted an attack");
                    dispatcher.execute("ori companion flame on", source);
                    SpiritFlame.tick(new PlayerTickEvent.Post(p));
                    h.assertTrue(target.getHealth() < health, "Re-enabled flame did not attack");
                    boolean rejected = false;
                    try { dispatcher.execute("ori companion flame off", source.withPermission(0)); }
                    catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
                    h.assertTrue(rejected && p.getData(SpiritData.COMPANION_FLAME), "Non-OP changed flame state");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
                finally { target.discard(); }
            }
        });
        return tests;
    }

    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("blade_motion", "blade_motion." + name, "orimod_gametest:room", 40, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
