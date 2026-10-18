package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.movement.AirMovement;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.network.SpiritFxPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

/** R18: persistent effects start, heartbeat and stop with authoritative state, and every exit path ends them. */
@GameTestHolder("orimod_gametest")
public final class FxLifecycleGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "glide_start_heartbeat_stop", (h, c) -> {
            var p = c.player();
            glide(p);
            SpiritFxLifecycle.sync(p, 1);
            assertPhases(h, c, SpiritFxKind.GLIDE, List.of(SpiritFxPhase.START));
            SpiritFxLifecycle.sync(p, 2);
            assertPhases(h, c, SpiritFxKind.GLIDE, List.of());
            SpiritFxLifecycle.sync(p, SpiritFxTracker.HEARTBEAT_TICKS * 5);
            assertPhases(h, c, SpiritFxKind.GLIDE, List.of(SpiritFxPhase.START));
            var s = p.getData(SpiritData.MOVEMENT);
            s.jumpHeld = false; AirMovement.glide(p, s, s.lastInput, false, false);
            SpiritFxLifecycle.sync(p, 101);
            assertPhases(h, c, SpiritFxKind.GLIDE, List.of(SpiritFxPhase.STOP));
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "glide still active");
        });
        add(tests, "grapple_tether_and_single_hook_burst", (h, c) -> {
            var p = c.player(); var s = p.getData(SpiritData.MOVEMENT);
            BlockPos flower = h.absolutePos(new BlockPos(5, 5, 9));
            h.getLevel().setBlockAndUpdate(flower, OriBlocks.GRAPPLE_FLOWER.get().defaultBlockState());
            FxGameTests.drain(c.channel());
            AirMovement.beginGrapple(p, s, new SpiritTargets.Target(null, flower, Vec3.atCenterOf(flower), true), s.lastInput);
            h.assertTrue(s.grapple != null, "grapple fixture");
            SpiritFxLifecycle.sync(p, 1);
            var got = FxGameTests.fx(FxGameTests.drain(c.channel()));
            h.assertValueEqual(got.stream().filter(x -> x.kind() == SpiritFxKind.GRAPPLE_HOOK).count(), 1L, "hook burst count");
            h.assertTrue(got.stream().anyMatch(x -> x.kind() == SpiritFxKind.GRAPPLE_TETHER && x.phase() == SpiritFxPhase.START), "tether start");
            SpiritMovement.cancel(p, s);
            SpiritFxLifecycle.sync(p, 2);
            assertPhases(h, c, SpiritFxKind.GRAPPLE_TETHER, List.of(SpiritFxPhase.STOP));
        });
        add(tests, "dash_emits_one_burst", (h, c) -> {
            var p = c.player(); var s = p.getData(SpiritData.MOVEMENT);
            FxGameTests.drain(c.channel());
            SpiritMovement.accept(p, new MovementInputPayload(1, s.lastInput, MovementInputPayload.ACTIVE | MovementInputPayload.DASH, 0, 0, 0, 0));
            h.assertValueEqual(s.mode.id(), "dash", "dash fixture");
            var got = FxGameTests.fx(FxGameTests.drain(c.channel()));
            h.assertValueEqual(got.stream().filter(x -> x.kind() == SpiritFxKind.DASH && x.phase() == SpiritFxPhase.BURST).count(), 1L, "dash bursts");
        });
        add(tests, "form_switch_stops_all", (h, c) -> {
            var p = c.player(); glide(p); SpiritFxLifecycle.sync(p, 1); FxGameTests.drain(c.channel());
            int before = SpiritFxLifecycle.stops().getOrDefault("form", 0);
            h.assertTrue(SpiritForm.toggle(p), "form switch fixture");
            var got = FxGameTests.fx(FxGameTests.drain(c.channel()));
            h.assertTrue(got.stream().anyMatch(x -> x.phase() == SpiritFxPhase.STOP_ALL), "no STOP_ALL on form switch");
            h.assertTrue(got.stream().anyMatch(x -> x.kind() == SpiritFxKind.FORM_SWITCH), "no form switch burst");
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "effects survived form switch");
            h.assertValueEqual(SpiritFxLifecycle.stops().getOrDefault("form", 0), before + 1, "form stop counted");
            SpiritFxLifecycle.sync(p, 2);
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "human form restarted glide");
        });
        add(tests, "death_stops_all", (h, c) -> {
            var p = c.player(); glide(p); SpiritFxLifecycle.sync(p, 1); FxGameTests.drain(c.channel());
            p.kill();
            var got = FxGameTests.fx(FxGameTests.drain(c.channel()));
            h.assertTrue(got.stream().anyMatch(x -> x.phase() == SpiritFxPhase.STOP_ALL), "no STOP_ALL on death");
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "effects survived death");
            SpiritFxLifecycle.sync(p, 2);
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "dead player restarted an effect");
        });
        add(tests, "dimension_change_stops_all", (h, c) -> {
            var p = c.player(); glide(p); SpiritFxLifecycle.sync(p, 1);
            var nether = h.getLevel().getServer().getLevel(Level.NETHER);
            h.assertTrue(nether != null, "nether missing");
            p.changeDimension(new DimensionTransition(nether, new Vec3(0, 100, 0), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
            h.assertTrue(p.level() == nether, "dimension fixture");
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "effects crossed dimensions");
        });
        add(tests, "logout_stops_and_forgets", (h, c) -> {
            var p = c.player(); glide(p); SpiritFxLifecycle.sync(p, 1);
            h.assertFalse(SpiritFxLifecycle.active(p).isEmpty(), "fixture");
            p.getServer().getPlayerList().remove(p);
            h.assertTrue(SpiritFxLifecycle.active(p).isEmpty(), "effects survived logout");
        });
        add(tests, "every_sound_cue_resolves", (h, c) -> {
            for (var kind : SpiritFxKind.values()) {
                var cue = SpiritSoundCue.of(kind);
                if (cue != null) h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.parse(cue.sound())), "unknown sound " + cue.sound());
            }
        });
        return tests;
    }

    /** Real glide entry: airborne spirit with glide, jump held, falling. */
    private static void glide(ServerPlayer p) {
        var s = p.getData(SpiritData.MOVEMENT);
        s.jumpHeld = true; s.observedDy = -.1;
        AirMovement.glide(p, s, s.lastInput, false, false);
        if (s.mode != cn.wwl.orimod.movement.MoveMode.GLIDE) throw new IllegalStateException("glide fixture failed");
    }

    private static void assertPhases(GameTestHelper h, Fixtures.ConnectedPlayer c, SpiritFxKind kind, List<SpiritFxPhase> expected) {
        var got = new ArrayList<SpiritFxPhase>();
        for (SpiritFxPayload x : FxGameTests.fx(FxGameTests.drain(c.channel()))) if (x.kind() == kind) got.add(x.phase());
        h.assertValueEqual(got, expected, kind + " phases");
    }

    private static void add(Collection<TestFunction> tests, String name, BiConsumer<GameTestHelper, Fixtures.ConnectedPlayer> run) {
        tests.add(new TestFunction("fx_lifecycle", "fx_lifecycle." + name, "orimod_gametest:room", 40, 0, true, h -> {
            var c = Fixtures.connectedPlayer(h);
            try {
                var p = c.player();
                SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, 63);
                p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false);
                var s = p.getData(SpiritData.MOVEMENT);
                s.dimension = p.level().dimension().location().toString(); s.previousPosition = p.position().add(0, .1, 0);
                s.lastInput = p.level().getGameTime(); s.active = true; s.airDashSpent = false;
                FxGameTests.drain(c.channel()); // the fixture's own form switch
                run.accept(h, c);
                h.succeed();
            } finally {
                if (c.player().getServer().getPlayerList().getPlayers().contains(c.player())) c.close();
                else c.channel().finishAndReleaseAll();
            }
        }));
    }
}
