package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class FlightInputGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String name : List.of("creative_ground", "creative_air", "mod_permission", "queued_permission", "survival_air"))
            tests.add(new TestFunction("flight_input", "flight_input." + name, "orimod_gametest:room", 40, 0, true, h -> {
                try (var connection = Fixtures.connectedPlayer(h)) {
                    var p = connection.player(); SpiritForm.toggle(p);
                    if (name.startsWith("creative")) p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    boolean grounded = name.endsWith("ground");
                    p.setPos(h.absoluteVec(new Vec3(5.5, grounded ? 2 : 5, 5.5))); p.setOnGround(grounded);
                    var s = p.getData(SpiritData.MOVEMENT);
                    s.dimension = p.level().dimension().location().toString(); s.previousPosition = p.position();
                    s.active = true; s.lastInput = p.level().getGameTime();
                    s.airJumpSpent = false; s.groundJumpAvailable = grounded; s.groundedAt = grounded ? s.lastInput : -100;
                    p.setDeltaMovement(0, -.1, 0);
                    if (name.equals("queued_permission")) s.jumps.add(new MovementState.JumpRequest(1, s.lastInput,
                            s.lastInput + 2, p.position(), p.getDeltaMovement(), 0, null, true));
                    p.getAbilities().mayfly = !name.equals("survival_air");
                    if (name.equals("queued_permission")) SpiritMovement.tick(new PlayerTickEvent.Post(p));
                    else for (int i = 1; i <= 4; i++) SpiritMovement.accept(p, new MovementInputPayload(i, s.lastInput,
                            MovementInputPayload.ACTIVE | (i % 2 == 1 ? MovementInputPayload.JUMP : 0), 0, 0, 0, 0));
                    h.assertValueEqual(s.acceptedJumps, name.equals("survival_air") ? 1 : 0, "Jump ownership");
                    h.assertTrue(s.jumps.isEmpty() || name.equals("survival_air"), "Permission retained a jump request");
                    if (!name.equals("survival_air")) {
                        h.assertFalse(s.airJumpSpent, "Creative input consumed air jump");
                        Fixtures.near(h, p.getDeltaMovement().y, -.1, "Ori changed vanilla vertical movement");
                    }
                }
                h.succeed();
            }));
        return tests;
    }
}
