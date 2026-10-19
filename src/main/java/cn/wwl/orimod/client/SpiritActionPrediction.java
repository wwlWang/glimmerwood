package cn.wwl.orimod.client;

import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.movement.BashSession;
import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementRules;
import cn.wwl.orimod.movement.MovementState;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * R78: zero-delay local start of Dash, Glide and Bash aim. Each mirrors the server's own gate (ability, cooldown, air charge,
 * combat) so the server reaches the same decision 1-2 ticks later; the acknowledgement then only confirms
 * ({@link cn.wwl.orimod.network.MovementResultPayload} keeps the running local action instead of restarting it).
 * A prediction the server never confirms simply runs out its short lease (dash: its own length; glide/bash: a few ticks).
 */
public final class SpiritActionPrediction {
    public static final int GLIDE_LEASE = 4, BASH_LEASE = 5, ACK_SLACK = 4;
    private static boolean prevDash, prevBash;
    private SpiritActionPrediction() {}

    public static void apply(Player player, MovementState s, int flags, float forward, float left) {
        long now = player.level().getGameTime();
        boolean dash = (flags & MovementInputPayload.DASH) != 0, bash = (flags & MovementInputPayload.BASH) != 0;
        boolean dashEdge = dash && !prevDash, bashEdge = bash && !prevBash;
        prevDash = dash; prevBash = bash;
        if (s.predictedMode != IDLE && now > s.predictedUntil) { s.unconfirmedPredictions++; s.predictedMode = IDLE; }
        if ((flags & MovementInputPayload.ACTIVE) == 0) return;
        boolean crouch = (flags & MovementInputPayload.CROUCH) != 0;
        var combat = player.getData(SpiritData.COMBAT_VIEW);
        if (!combat.mode().equals("idle") || SpiritCombatClient.awaitingAttack()) return;
        boolean busy = s.activeTicks > 0 && (s.mode == STOMP || s.mode == BASH_AIM || s.mode == GRAPPLE || s.mode == DASH);
        boolean grounded = SpiritMovement.grounded(player) && now >= s.groundLockUntil;
        if (dashEdge && !crouch && !busy && SpiritData.hasDash(player) && now >= s.dashReadyAt && (grounded || !s.airDashSpent)) {
            s.airDashSpent = true;
            s.dashReadyAt = now + MovementRules.DASH_COOLDOWN_TICKS;
            s.groundLockUntil = Math.max(s.groundLockUntil, now + MovementRules.DASH_TICKS + 1);
            Vec3 velocity = MovementRules.dashVelocity(MovementRules.inputDirection(forward, left, player.getYRot(), true).scale(MovementRules.DASH_SPEED),
                    player.getDeltaMovement().y);
            player.setDeltaMovement(velocity);
            start(s, DASH, MovementRules.DASH_TICKS, velocity, now + MovementRules.DASH_TICKS + ACK_SLACK);
            return;
        }
        if (bashEdge && !busy && SpiritData.hasBash(player)) {
            var target = SpiritTargets.find(player, player.getYRot(), player.getXRot());
            if (target != null && !target.grapple() && target.valid(player)) {
                s.targetCooldowns.entrySet().removeIf(e -> e.getValue() <= now);
                if (!s.targetCooldowns.containsKey(target.key())) {
                    s.targetCooldowns.put(target.key(), now + BashSession.TARGET_COOLDOWN_TICKS);
                    s.suspendedVelocity = player.getDeltaMovement(); s.suspendedFallDistance = player.fallDistance;
                    start(s, BASH_AIM, BASH_LEASE, Vec3.ZERO, now + BASH_LEASE);
                    player.setDeltaMovement(Vec3.ZERO);
                    return;
                }
            }
        }
        boolean jump = (flags & MovementInputPayload.JUMP) != 0;
        if (jump && !crouch && s.activeTicks <= 0 && !grounded && SpiritData.hasGlide(player) && now >= s.groundLockUntil
                && player.getDeltaMovement().y < -.03 && !player.horizontalCollision
                && SpiritMovement.touchingWall(player, MovementRules.inputDirection(forward, left, player.getYRot(), false), -1) == null) {
            start(s, GLIDE, GLIDE_LEASE, Vec3.ZERO, now + GLIDE_LEASE + ACK_SLACK);
        }
    }

    private static void start(MovementState s, MoveMode mode, int ticks, Vec3 velocity, long until) {
        s.mode = mode; s.activeTicks = ticks; s.velocity = velocity; s.lastAction = mode;
        s.predictedMode = mode; s.predictedUntil = until; s.predictedActions++;
    }
}
