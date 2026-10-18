package cn.wwl.orimod.gametest;

import cn.wwl.orimod.OriServerLifecycle;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.BashSession;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class LifecycleGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "shutdown_releases_orphan_frozen_entity", false, h -> {
            var p = Fixtures.player(h); var target = h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 2, 7.5));
            Vec3 velocity = new Vec3(.2, -.3, .4); target.setDeltaMovement(velocity); target.fallDistance = 2;
            var session = new BashSession(p, new SpiritTargets.Target(target, null, target.position(), false));
            try {
                h.assertTrue(BashSession.frozenCount() > 0, "Freeze fixture absent");
                OriServerLifecycle.stopping(new ServerStoppingEvent(h.getLevel().getServer()));
                h.assertValueEqual(BashSession.frozenCount(), 0, "Shutdown retained frozen entity");
                Fixtures.near(h, target.getDeltaMovement().distanceTo(velocity), 0, "Shutdown lost target momentum");
                Fixtures.near(h, target.fallDistance, 2, "Shutdown lost target fall history");
                target.setDeltaMovement(.7, .2, .1); BashSession.tickTarget(new EntityTickEvent.Post(target));
                Fixtures.near(h, target.getDeltaMovement().x, .7, "Target still frozen after shutdown");
                OriServerLifecycle.stopping(new ServerStoppingEvent(h.getLevel().getServer()));
                Fixtures.near(h, target.getDeltaMovement().x, .7, "Second shutdown replayed restoration");
            } finally { session.release(null); target.discard(); }
        });
        add(tests, "stopped_fallback_drops_reference_without_mutation", false, h -> {
            var p = Fixtures.player(h); var target = h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 2, 7.5));
            var session = new BashSession(p, new SpiritTargets.Target(target, null, target.position(), false));
            try {
                target.setDeltaMovement(.4, .5, .6);
                OriServerLifecycle.stopped(new ServerStoppedEvent(h.getLevel().getServer()));
                h.assertValueEqual(BashSession.frozenCount(), 0, "Fallback retained frozen entity");
                Fixtures.near(h, target.getDeltaMovement().y, .5, "Fallback mutated a closed world");
            } finally { session.release(null); target.discard(); }
        });
        add(tests, "shutdown_clears_locator_cooldown", false, h -> {
            var p = Fixtures.player(h);
            try {
                RuinLocator.request(p); RuinLocator.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(RuinLocator.lifecycleState().get("cooldowns") > 0, "Locator fixture did not start");
                // The flat GameTest generator has no ruin placement candidates. Pending region
                // tickets are checked separately in the normal-world client lifecycle test.
                OriServerLifecycle.stopping(new ServerStoppingEvent(h.getLevel().getServer()));
                h.assertTrue(RuinLocator.lifecycleState().values().stream().allMatch(n -> n == 0), "Shutdown retained search state");
                RuinLocator.request(p);
                h.assertTrue(RuinLocator.lifecycleState().get("cooldowns") > 0, "Next world inherited old cooldown");
            } finally { RuinLocator.stopAll(); }
        });
        for (String reason : new String[]{"exit", "form", "death", "dimension", "shutdown", "orphan", "complete"}) {
            add(tests, "challenge_" + reason + "_cleans_target", true, h -> challenge(h, reason));
        }
        return tests;
    }

    private static void challenge(GameTestHelper h, String reason) {
        Fixtures.ConnectedPlayer connection = reason.equals("orphan") ? null : Fixtures.connectedPlayer(h);
        ServerPlayer p = connection == null ? Fixtures.player(h) : connection.player();
        var kind = RuinKind.CAVE;
        var origin = h.absolutePos(new BlockPos(0, 1, 0));
        var shrine = origin.offset(kind.layout().shrine());
        try {
            SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, 63);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
            p.setPos(Vec3.atLowerCornerOf(origin).add(kind.layout().safe()));
            SpiritChallenge.request(p, shrine, true);
            var session = SpiritChallenge.session(p);
            h.assertTrue(session != null, "Cave challenge fixture did not start at " + shrine + ": " + h.getLevel().getBlockState(shrine));
            h.assertTrue(p.getData(SpiritData.CHALLENGE_ACTIVE), "Challenge active attachment missing");
            h.assertFalse(p.getData(SpiritData.CHALLENGE_RECOVERY).dimension().isEmpty(), "Recovery marker missing");
            var movement = p.getData(SpiritData.MOVEMENT);
            // Lifecycle tests advance the real checkpoint detector; ability motion has its own suite.
            for (int i = 0; i < 2; i++) {
                var point = kind.layout().checkpoints().get(i);
                p.setPos(Vec3.atLowerCornerOf(origin).add(point.position())); p.setOnGround(true);
                if (i == 0) movement.completedGrapples++; else movement.stompImpacts++;
                SpiritChallenge.tick(new PlayerTickEvent.Post(p));
            }
            h.assertTrue(session.target != null, "Blade checkpoint did not spawn target");
            var target = (PracticeTarget)h.getLevel().getEntity(session.target);
            h.assertTrue(target != null && target.isAlive(), "Challenge target missing");
            int light = p.getData(SpiritData.PROGRESS).light();
            switch (reason) {
                case "exit" -> SpiritChallenge.finish(p, "exit", true);
                case "form" -> { SpiritForm.toggle(p); SpiritChallenge.tick(new PlayerTickEvent.Post(p)); }
                case "death" -> SpiritChallenge.death(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p, p.damageSources().generic()));
                case "dimension" -> SpiritChallenge.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p, p.level().dimension(), net.minecraft.world.level.Level.NETHER));
                case "shutdown", "orphan" -> OriServerLifecycle.stopping(new ServerStoppingEvent(h.getLevel().getServer()));
                case "complete" -> { h.assertTrue(target.hurt(p.damageSources().playerAttack(p), 30), "Owned blade did not defeat target"); }
            }
            h.assertTrue(SpiritChallenge.session(p) == null, "Ended challenge retained session");
            h.assertTrue(target.isRemoved(), "Ended challenge retained temporary target");
            if (!reason.equals("orphan")) {
                h.assertFalse(p.getData(SpiritData.CHALLENGE_ACTIVE), "Ended challenge retained active flag");
                h.assertTrue(p.getData(SpiritData.CHALLENGE_RECOVERY).dimension().isEmpty(), "Ended challenge retained recovery marker");
            }
            int expected = light + (reason.equals("complete") ? 30 : 0);
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), expected, "Incorrect completion reward");
            SpiritChallenge.finish(p, "exit", true); SpiritChallenge.targetDefeated(p, target);
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), expected, "Repeated cleanup changed reward");
        } finally {
            SpiritChallenge.finish(p, "exit", false);
            if (connection != null) connection.close();
        }
    }

    private static void add(Collection<TestFunction> tests, String name, boolean cave, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("lifecycle", "lifecycle." + name, cave ? "orimod_gametest:cave" : "orimod_gametest:room", 40, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
