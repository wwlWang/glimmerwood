package cn.wwl.orimod.movement;

import cn.wwl.orimod.combat.CombatView;
import cn.wwl.orimod.combat.HammerSlam;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.player.Player;

/** Slime owns vertical travel until the bounce settles or a new action replaces it. */
public final class SlimeBounce {
    private SlimeBounce() {}
    public static final class State {
        private boolean bouncing;
        private double origin = Double.NaN;
        private double fallOrigin = Double.NaN;
        private net.minecraft.world.phys.Vec3 lastPosition;
        private int until, hammerAction = -1;
        private long stompToken = Long.MIN_VALUE, finishedStompToken = Long.MIN_VALUE;
        public void clear() { bouncing = false; origin = fallOrigin = Double.NaN; lastPosition = null; }
    }
    public static void beginSlam(Player player, double origin) {
        var s = player.getData(SpiritData.MOVEMENT).slime;
        s.bouncing = false; s.origin = origin; s.until = player.tickCount + HammerSlam.TIMEOUT;
    }
    public static boolean receiveStomp(Player player, double origin, long token) {
        var s = player.getData(SpiritData.MOVEMENT).slime;
        if (token == s.finishedStompToken) return false;
        if (token != s.stompToken) { s.stompToken = token; beginSlam(player, origin); }
        return !s.bouncing;
    }
    /** New movement ownership must not inherit native bounce gravity; landing/idle acknowledgements may. */
    public static void takeover(MovementState movement, MoveMode mode) {
        switch (mode) {
            case JUMP, DOUBLE_JUMP, WALL_JUMP, DASH, WALL_SLIDE, BASH_AIM, BASH_LAUNCH,
                    GRAPPLE, GRAPPLE_END, GLIDE, RESUME, RELOCATE, LAUNCH -> movement.slime.clear();
            default -> { }
        }
    }
    public static void observeHammer(Player player, CombatView view) {
        var s = player.getData(SpiritData.MOVEMENT).slime;
        if (HammerSlam.MODE.equals(view.mode()) && view.actionId() != s.hammerAction) {
            s.hammerAction = view.actionId(); beginSlam(player, view.originY());
        }
    }
    public static boolean active(Player player) {
        var s = player.getData(SpiritData.MOVEMENT).slime;
        if (!SpiritData.isSpirit(player) || player.isInWaterOrBubble() || player.isInLava()
                || player.onClimbable() || player.getAbilities().flying || player.isFallFlying()) s.clear();
        // Vanilla's post-travel gravity leaves a small negative dy even when standing still.
        if (player.onGround() && player.getDeltaMovement().y <= 0) s.bouncing = false;
        return s.bouncing;
    }
    public static void clear(Player player) { player.getData(SpiritData.MOVEMENT).slime.clear(); }

    /** Track the actual apex, before collision resets fallDistance. Does not change ordinary air motion. */
    public static void observeFall(Player player) {
        var s = player.getData(SpiritData.MOVEMENT).slime;
        if (!SpiritData.isSpirit(player) || player.isInWaterOrBubble() || player.isInLava()
                || player.onClimbable() || player.getAbilities().flying || player.isFallFlying()
                || player.isPassenger() || VanillaMotion.riptide(player)) { s.clear(); return; }
        var position = player.position();
        if (s.lastPosition != null && position.distanceToSqr(s.lastPosition) > 64) s.clear();
        s.lastPosition = position;
        if (s.bouncing) return; // Later bounces already use vanilla gravity and drag, with no new impulse.
        if (!Double.isFinite(s.fallOrigin) || player.onGround() || player.getY() > s.fallOrigin)
            s.fallOrigin = player.getY();
        switch (player.getData(SpiritData.MOVEMENT).mode) {
            case GLIDE, GRAPPLE, BASH_AIM, DASH -> s.fallOrigin = player.getY();
            default -> { }
        }
    }

    /** Incoming speed from a vanilla fall, not from the accelerated stomp/hammer or fallDistance (which resets). */
    public static double freeFallSpeed(double height, double gravity) {
        if (!(height > 0) || !(gravity > 0)) return 0;
        double y = 0, speed = 0;
        // World-height falls finish well inside this bound; also bounds unusual zero-like modded gravity.
        for (int t = 0; t < 4096; t++) {
            y += speed;
            if (y >= height) return speed;
            speed = (speed + gravity) * .98;
        }
        return Math.min(speed, Math.sqrt(2 * gravity * height));
    }
    /** Called after vanilla flips the impact velocity, before travel applies gravity and drag. */
    public static void bounced(Player player) {
        if (!SpiritData.isSpirit(player) || player.getDeltaMovement().y <= 0) return;
        var movement = player.getData(SpiritData.MOVEMENT);
        var s = movement.slime;
        boolean alreadyBouncing = s.bouncing;
        s.bouncing = true; s.finishedStompToken = s.stompToken;
        double from = Double.isFinite(s.origin) && player.tickCount <= s.until ? s.origin : s.fallOrigin;
        if (!alreadyBouncing && Double.isFinite(from)) {
            double gravity = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) gravity = Math.min(gravity, .01);
            var v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, freeFallSpeed(from - player.getY(), gravity), v.z);
        }
        s.origin = s.fallOrigin = Double.NaN;
        // Don't let an unexpired network lease slam the client into the slime a second time.
        if (movement.mode == MoveMode.STOMP) { movement.mode = MoveMode.IDLE; movement.activeTicks = 0; }
    }
}
