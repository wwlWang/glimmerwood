package cn.wwl.orimod.client;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementRules;
import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.movement.MovementState;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Immediate local motion; each prediction is matched to an independently validated server edge. */
public final class SpiritJumpPrediction {
    private SpiritJumpPrediction() {}

    public static boolean apply(Player player, MovementState state, int sequence, int flags, float forward, float left) {
        long now = player.level().getGameTime();
        boolean active = (flags & MovementInputPayload.ACTIVE) != 0;
        boolean jump = (flags & MovementInputPayload.JUMP) != 0;
        boolean edge = jump && !state.jumpHeld;
        state.jumpHeld = jump;
        if (!active) { state.predictedJumps.clear(); return false; }
        boolean grounded = SpiritMovement.grounded(player) && now >= state.groundLockUntil;
        if (grounded) {
            state.airJumpSpent = state.airDashSpent = false;
            state.groundJumpAvailable = true; state.groundedAt = now; state.lastWallRefill = "";
        }
        if (!edge || (flags & MovementInputPayload.CROUCH) != 0 || state.predictedJumps.size() >= 4) return false;
        var combat = player.getData(SpiritData.COMBAT_VIEW);
        // During combat, wait for the existing server cancellation rules instead of predicting
        // an action whose weapon state may still be in transit.
        if (!combat.mode().equals("idle") || SpiritCombatClient.awaitingAttack()) return false;
        state.forward = forward; state.left = left; state.yaw = player.getYRot();
        var wall = SpiritMovement.touchingWall(player, SpiritMovement.inputDirection(state, false), state.wallDirection);
        MoveMode mode = MovementRules.jumpMode(state.groundJumpAvailable, now, state.groundedAt,
                grounded, wall != null, now >= state.wallReadyAt, state.airJumpSpent,
                state.activeTicks > 0 && state.mode.blocksJump());
        if (mode == IDLE) return false;
        Vec3 previous = player.getDeltaMovement();
        player.jumpFromGround();
        state.groundJumpAvailable = false; state.groundLockUntil = now + 4;
        if (mode == WALL_JUMP) {
            player.setDeltaMovement(MovementRules.wallJumpVelocity(Vec3.atLowerCornerOf(wall.getNormal()), player.getDeltaMovement().y));
            player.fallDistance = 0; state.wallReadyAt = now + 8;
            String surface = SpiritMovement.wallSurface(player, wall);
            if (!surface.equals(state.lastWallRefill)) { state.airJumpSpent = state.airDashSpent = false; state.lastWallRefill = surface; }
        } else {
            // Match the established movement contract: normal/double jumps preserve horizontal
            // momentum while wall jumps replace it with the approved outward impulse.
            player.setDeltaMovement(previous.x, player.getDeltaMovement().y, previous.z);
            if (mode == DOUBLE_JUMP) { player.fallDistance = 0; state.airJumpSpent = true; }
        }
        player.setOnGround(false);
        state.predictedJumps.put(sequence, mode);
        state.predictedJumpCount++; state.jumpPressedAt = now;
        state.lastAction = mode;
        return true;
    }
}
