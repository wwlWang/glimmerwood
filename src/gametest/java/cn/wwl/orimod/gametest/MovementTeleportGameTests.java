package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.BashSession;
import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementState;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.ArrayList;
import java.util.Collection;

@GameTestHolder("orimod_gametest")
public final class MovementTeleportGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String name : new String[]{"travel_9", "travel_32", "short", "long", "relative", "glide", "bash", "queue", "human"})
            tests.add(new TestFunction("movement_teleport", "movement_teleport." + name, "orimod_gametest:room", 40, 0, true,
                    h -> { check(h, name); h.succeed(); }));
        return tests;
    }

    private static void check(GameTestHelper h, String name) {
        try (var connection = Fixtures.connectedPlayer(h)) {
            var p = connection.player();
            if (!name.equals("human")) SpiritForm.toggle(p);
            p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false);
            var s = p.getData(SpiritData.MOVEMENT);
            s.dimension = p.level().dimension().location().toString(); s.previousPosition = p.position();
            s.active = true; s.lastInput = p.level().getGameTime();
            s.airJumpSpent = s.airDashSpent = false; s.groundJumpAvailable = true;
            int relocations = s.relocations;
            if (name.startsWith("travel_")) {
                p.setPos(p.position().add(name.equals("travel_9") ? 9 : 32, 0, 0));
                SpiritMovement.tick(new PlayerTickEvent.Post(p));
                h.assertFalse(s.airJumpSpent || s.airDashSpent, "Continuous movement consumed air charges");
                h.assertValueEqual(s.relocations, relocations, "Travel inferred an unrequested teleport");
                return;
            }
            var target = h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 5, 8));
            Vec3 savedVelocity = new Vec3(.2, -.1, .3); target.setDeltaMovement(savedVelocity);
            try {
                s.mode = name.equals("glide") ? MoveMode.GLIDE : MoveMode.DASH; s.activeTicks = 3;
                if (name.equals("bash")) {
                    s.bash = new BashSession(p, new SpiritTargets.Target(target, null, target.position(), false));
                    s.mode = MoveMode.BASH_AIM;
                }
                if (name.equals("queue")) s.jumps.add(new MovementState.JumpRequest(17, s.lastInput, s.lastInput + 2,
                        p.position(), p.getDeltaMovement(), 0, null, true));
                Vec3 destination = p.position().add(name.equals("long") ? 20 : .5, 0, 0);
                if (name.equals("relative")) p.teleportRelative(.5, 0, 0);
                else p.connection.teleport(destination.x, destination.y, destination.z, 0, 0);
                // Check immediately, before another player tick or new movement packet.
                h.assertValueEqual(s.relocations, relocations + 1, "Actual teleport was not observed exactly once");
                h.assertValueEqual(s.mode, MoveMode.IDLE, "Teleport retained action ownership");
                h.assertFalse(s.active || s.groundJumpAvailable, "Teleport retained stale input or ground charge");
                h.assertTrue(s.airJumpSpent && s.airDashSpent, "Teleport retained air charges");
                h.assertTrue(s.jumps.isEmpty() && s.bash == null && s.grapple == null, "Teleport retained queued or owned actions");
                Fixtures.near(h, p.position().distanceTo(destination), 0, "Movement cleanup changed destination");
                if (name.equals("bash")) Fixtures.near(h, target.getDeltaMovement().distanceTo(savedVelocity), 0, "Teleport left Bash target frozen");
                SpiritMovement.tick(new PlayerTickEvent.Post(p));
                h.assertValueEqual(s.relocations, relocations + 1, "Following tick duplicated relocation");
            } finally { SpiritMovement.cancel(p, s); target.discard(); }
        }
    }
}
