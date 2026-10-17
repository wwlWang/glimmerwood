package cn.wwl.orimod.client;

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
        if (!edge || (flags & MovementInputPayload.CROUCH) != 0 || state.predictedJumps.size() >= 4
                || player.hurtTime > state.lastHurtTime) return false;
        var combat = player.getData(SpiritData.COMBAT_VIEW);
        // During combat, wait for the existing server cancellation rules instead of predicting
        // an action whose weapon state may still be in transit.
        if (!combat.mode().equals("idle") || SpiritCombatClient.awaitingAttack()) return false;
        if (state.activeTicks > 0 && (state.mode.equals("dash") || state.mode.equals("bash_aim")
                || state.mode.equals("grapple") || state.mode.equals("stomp"))) return false;
        state.forward = forward; state.left = left; state.yaw = player.getYRot();
        var wall = SpiritMovement.touchingWall(player, SpiritMovement.inputDirection(state, false), state.wallDirection);
        String mode = state.groundJumpAvailable && now - state.groundedAt <= SpiritMovement.COYOTE_TICKS ? "jump"
                : !grounded && wall != null && now >= state.wallReadyAt ? "wall_jump"
                : !grounded && !state.airJumpSpent ? "double_jump" : "";
        if (mode.isEmpty()) return false;
        Vec3 previous = player.getDeltaMovement();
        player.jumpFromGround();
        state.groundJumpAvailable = false; state.groundLockUntil = now + 4;
        if (mode.equals("wall_jump")) {
            Vec3 away = Vec3.atLowerCornerOf(wall.getNormal()).scale(-SpiritMovement.WALL_JUMP_SPEED);
            player.setDeltaMovement(away.x, Math.max(.5, player.getDeltaMovement().y), away.z);
            player.fallDistance = 0; state.wallReadyAt = now + 8;
            String surface = SpiritMovement.wallSurface(player, wall);
            if (!surface.equals(state.lastWallRefill)) { state.airJumpSpent = state.airDashSpent = false; state.lastWallRefill = surface; }
        } else {
            // Match the established movement contract: normal/double jumps preserve horizontal
            // momentum while wall jumps replace it with the approved outward impulse.
            player.setDeltaMovement(previous.x, player.getDeltaMovement().y, previous.z);
            if (mode.equals("double_jump")) { player.fallDistance = 0; state.airJumpSpent = true; }
        }
        player.setOnGround(false);
        state.predictedJumps.put(sequence, mode);
        state.predictedJumpCount++; state.jumpPressedAt = now;
        state.lastAction = mode;
        return true;
    }
}
