package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementRules;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/** R38+: original-game parity mechanics (Launch, ...). */
@GameTestHolder("orimod_gametest")
public final class ParityGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "launch_needs_ability_and_charge", h -> {
            var p = spirit(h, 63); p.setOnGround(true);
            var s = p.getData(SpiritData.MOVEMENT); s.launchCharge = MovementRules.LAUNCH_CHARGE_TICKS;
            try {
                crouchJump(p, 1, true); crouchJump(p, 2, false);
                h.assertValueEqual(s.acceptedLaunches, 0, "Launch without the ability");
                p.setData(SpiritData.ABILITIES, 127); s.launchCharge = 3;
                crouchJump(p, 3, true); crouchJump(p, 4, false);
                h.assertValueEqual(s.acceptedLaunches, 0, "Uncharged ground Launch");
                s.launchCharge = MovementRules.LAUNCH_CHARGE_TICKS;
                crouchJump(p, 5, true);
                h.assertValueEqual(s.acceptedLaunches, 1, "Charged ground Launch");
                h.assertTrue(s.mode == MoveMode.LAUNCH, "Mode after Launch: " + s.mode);
                h.assertTrue(p.getDeltaMovement().y >= MovementRules.LAUNCH_MIN_UP * MovementRules.LAUNCH_GROUND_SPEED - 1e-6, "Launch did not rise");
                Fixtures.near(h, p.getDeltaMovement().length(), MovementRules.LAUNCH_GROUND_SPEED, "Ground Launch speed");
            } finally { SpiritMovement.cancel(p, s); }
        });
        add(tests, "air_launch_once_and_refreshes_air_moves", h -> {
            var p = spirit(h, 127); p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false);
            var s = p.getData(SpiritData.MOVEMENT); s.airLaunchSpent = false; s.airJumpSpent = s.airDashSpent = true;
            try {
                crouchJump(p, 1, true);
                h.assertValueEqual(s.acceptedLaunches, 1, "Air Launch");
                h.assertFalse(s.airJumpSpent || s.airDashSpent, "Launch did not refresh air moves");
                Fixtures.near(h, p.getDeltaMovement().length(), MovementRules.LAUNCH_AIR_SPEED, "Air Launch speed");
                crouchJump(p, 2, false); crouchJump(p, 3, true);
                h.assertValueEqual(s.acceptedLaunches, 1, "Second air Launch in one airtime");
            } finally { SpiritMovement.cancel(p, s); }
        });
        return tests;
    }

    static ServerPlayer spirit(GameTestHelper h, int abilities) {
        var p = Fixtures.player(h); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, abilities);
        return p;
    }

    static void crouchJump(ServerPlayer p, int sequence, boolean jump) {
        SpiritMovement.accept(p, new MovementInputPayload(sequence, p.level().getGameTime(),
                MovementInputPayload.ACTIVE | MovementInputPayload.CROUCH | (jump ? MovementInputPayload.JUMP : 0), 0, 0, 0, -30));
    }

    static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("parity", "parity." + name, "orimod_gametest:room", 30, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
