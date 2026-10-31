package cn.wwl.orimod.client;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementRules;
import cn.wwl.orimod.movement.MovementState;
import cn.wwl.orimod.movement.SpiritMovement;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

/**
 * R94 ledge mantle (client; spirit movement is client-authoritative like the rest of {@link SpiritControls}). While
 * airborne or wall-sliding and pushing into a wall whose top edge is within {@link MovementRules#MANTLE_REACH} of the
 * feet, Ori pops up just past the lip and is pushed onto the ledge, instead of grinding up the face and snagging on the
 * corner. A new jump press (double jump, wall jump, glide), crouch or any other movement action cancels it at once.
 */
public final class SpiritMantle {
    private static int ticks = -1, expected = 1;
    private static Direction wall;
    private static double lipY;
    private static boolean over;
    private static long readyAt, startedAt = -100, endedAt = -100;
    private static Player owner;
    private SpiritMantle() {}
    static void resetSession() {
        ticks = -1; expected = 1; wall = null; lipY = 0; over = false;
        readyAt = 0; startedAt = endedAt = -100; owner = null;
    }

    /** True while the local player is mantling. */
    public static boolean active(Player player) { return ticks >= 0 && player == owner; }
    /** 0..1 through the mantle (rise, then over the lip). */
    public static float progress() { return ticks < 0 ? 0 : Math.min(1, ticks / (float) expected); }
    public static boolean overLip() { return over; }
    public static long startedAt() { return startedAt; }
    public static long endedAt() { return endedAt; }
    public static double lipY() { return lipY; }

    static void end(Player player) {
        if (ticks >= 0) endedAt = player.level().getGameTime();
        ticks = -1; over = false; wall = null; owner = null;
    }

    /**
     * One movement-input tick. Returns true when the mantle owns this tick's velocity (the caller then skips the
     * wall-slide and feel curves).
     */
    static boolean tick(Player player, MovementState state, net.minecraft.client.player.Input input, boolean jumpEdge) {
        long now = player.level().getGameTime();
        boolean allowedMode = state.mode == MoveMode.IDLE || state.mode == MoveMode.WALL_SLIDE;
        if (ticks >= 0) {
            if (player != owner || jumpEdge || input.shiftKeyDown || !allowedMode || cn.wwl.orimod.block.OriFluids.inWater(player) || player.onClimbable()
                    || ticks >= MovementRules.MANTLE_MAX_TICKS || over && player.onGround()) {
                end(player); readyAt = now + MovementRules.MANTLE_COOLDOWN_TICKS; return false;
            }
            ticks++;
            var d = player.getDeltaMovement();
            if (!over && player.getY() >= lipY - .02) over = true;
            double push = over ? MovementRules.MANTLE_FORWARD : MovementRules.MANTLE_HOLD;
            player.setDeltaMovement(wall.getStepX() * push, d.y, wall.getStepZ() * push);
            player.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0;
            return true;
        }
        if (player.onGround() || jumpEdge || input.shiftKeyDown || !allowedMode || now < readyAt || input.forwardImpulse <= .1f
                || cn.wwl.orimod.block.OriFluids.inWater(player) || player.onClimbable() || player.getDeltaMovement().y < MovementRules.MANTLE_MIN_VY) return false;
        var direction = MovementRules.inputDirection(input.forwardImpulse, input.leftImpulse, player.getYRot(), false);
        Direction side = SpiritMovement.touchingWall(player, direction, state.wallDirection);
        if (side == null) return false;
        double rise = SpiritMovement.mantleRise(player, side);
        if (rise < 0) return false;
        double vy = MovementRules.mantleLaunch(rise);
        owner = player; wall = side; lipY = player.getY() + rise; over = false; ticks = 0; startedAt = now;
        // Ticks to the top of the pop plus about three to cross the lip, for the animation's progress.
        expected = Math.max(3, (int) Math.ceil(vy / .08) + 3);
        player.setDeltaMovement(side.getStepX() * MovementRules.MANTLE_HOLD, vy, side.getStepZ() * MovementRules.MANTLE_HOLD);
        player.fallDistance = 0;
        input.forwardImpulse = input.leftImpulse = 0;
        return true;
    }
}
