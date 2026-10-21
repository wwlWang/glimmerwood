package cn.wwl.orimod.movement;

import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/** Server-owned airborne actions. All travel continues through vanilla solid collision. */
public final class AirMovement {
    private AirMovement() {}

    public static void beginGrapple(ServerPlayer player, MovementState s, SpiritTargets.Target target, long now) {
        if (!SpiritData.hasGrapple(player)) {
            player.displayClientMessage(Component.translatable("message.orimod.grapple_locked"), true);
            return;
        }
        if (!cn.wwl.orimod.combat.SpiritCombat.allowMovement(player)) return;
        s.grapple = target;
        s.grappleUntil = now + 24;
        s.grappleDistance = Double.MAX_VALUE;
        s.grappleStalls = 0;
        s.targetCooldowns.put(target.key(), now + 40);
        s.jumpUntil = -100;
        s.acceptedGrapples++;
        SpiritMovement.signal(player, s, GRAPPLE, 3, grappleVelocity(player, target.point()));
    }

    public static Vec3 grappleVelocity(Player player, Vec3 point) {
        return MovementRules.grappleVelocity(player.position().add(0, player.getBbHeight() * .5, 0), point);
    }

    public static void beginStomp(ServerPlayer player, MovementState s, long now) {
        if (!SpiritData.hasStomp(player)) {
            player.displayClientMessage(Component.translatable("message.orimod.stomp_locked"), true);
            return;
        }
        if (s.mode == STOMP || SpiritMovement.grounded(player)
                || !player.level().noCollision(player, player.getBoundingBox().deflate(.02).expandTowards(0, -2, 0))) return;
        if (!cn.wwl.orimod.combat.SpiritCombat.allowMovement(player)) return;
        s.stompUntil = now + 80;
        s.jumpUntil = -100;
        s.acceptedStomps++;
        SpiritMovement.signal(player, s, STOMP, 3, new Vec3(0, -1.2, 0));
    }

    public static boolean tickOwned(ServerPlayer player, MovementState s, long now, boolean grounded) {
        if (s.grapple != null) {
            var target = s.grapple;
            double distance = player.position().add(0, player.getBbHeight() * .5, 0).distanceTo(target.point());
            Vec3 velocity = grappleVelocity(player, target.point());
            if (now >= s.grappleUntil || s.crouching || !target.valid(player)
                    || !player.level().noCollision(player, player.getBoundingBox().deflate(.01).expandTowards(velocity))) {
                SpiritMovement.cancel(player, s);
            } else if (distance < .7) {
                s.grapple = null;
                s.completedGrapples++;
                s.airJumpSpent = s.airDashSpent = false;
                s.groundJumpAvailable = false;
                s.groundLockUntil = now + 4;
                player.fallDistance = 0;
                SpiritMovement.signal(player, s, GRAPPLE_END, 0, new Vec3(0, .28, 0));
            } else {
                s.grappleStalls = distance >= s.grappleDistance - .02 ? s.grappleStalls + 1 : 0;
                s.grappleDistance = distance;
                if (s.grappleStalls >= 5) SpiritMovement.cancel(player, s);
                else {
                    player.fallDistance = 0;
                    SpiritMovement.signal(player, s, GRAPPLE, 3, velocity);
                }
            }
            return true;
        }
        if (s.mode == STOMP) {
            if (grounded) impact(player, s);
            else if (now >= s.stompUntil) SpiritMovement.cancel(player, s);
            else SpiritMovement.signal(player, s, STOMP, 3, new Vec3(0, MovementRules.STOMP_SPEED, 0));
            return true;
        }
        return false;
    }

    public static void glide(ServerPlayer player, MovementState s, long now, boolean grounded, boolean touchingWall) {
        if (SpiritData.hasGlide(player) && s.jumpHeld && (s.mode == GLIDE || MovementRules.glideArmed(s.jumpEdgeAt, s.jumpPerformedAt)) && !s.crouching && !grounded && !touchingWall
                && now >= s.groundLockUntil && (s.observedDy < -.03 || s.mode == GLIDE && cn.wwl.orimod.block.UpdraftBlock.lifting(player.level(), player))) {
            // R40: rising in an updraft keeps the glide; the lift is server-sanctioned, so do not count it as flying.
            if (s.observedDy >= -.03 && player.connection instanceof cn.wwl.orimod.mixin.ServerFloatingAccessor floating) floating.orimod$setAboveGroundTickCount(0);
            if (s.mode != GLIDE) s.startedGlides++;
            player.fallDistance = 0;
            SpiritMovement.signal(player, s, GLIDE, MovementRules.GLIDE_LEASE_TICKS, Vec3.ZERO);
        } else if (s.mode == GLIDE) SpiritMovement.cancel(player, s);
    }

    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = player.getData(SpiritData.MOVEMENT);
        if (s.mode == STOMP && s.active && player.level().getGameTime() - s.lastInput <= 4 && SpiritMovement.canMove(player)) {
            event.setCanceled(true);
            impact(player, s);
            return;
        }
        // R84: spirit form never takes fall damage — Ori landings are soft. Ability landings also clear the meter.
        if (SpiritData.isSpirit(player)) {
            event.setCanceled(true);
            player.fallDistance = 0;
        }
    }

    private static void impact(ServerPlayer player, MovementState s) {
        if (s.mode != STOMP) return;
        s.stompImpacts++;
        cn.wwl.orimod.block.CrackedRuin.stomp(player);
        // Partial block landings can be observed before the final movement packet's fall check.
        player.fallDistance = 0;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3, 1, 3),
                entity -> entity instanceof Enemy && entity.isAlive())) {
            if (player.distanceToSqr(target) > 9 || !SpiritTargets.visible(player, target.getBoundingBox().getCenter())) continue;
            target.hurt(player.damageSources().playerAttack(player), 6);
            Vec3 away = target.position().subtract(player.position()).normalize();
            target.push(away.x * .35, .3, away.z * .35);
        }
        cn.wwl.orimod.fx.SpiritFx.emit(player.serverLevel(), cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, player.getX(), player.getY() + .1, player.getZ(), player);
        SpiritMovement.signal(player, s, STOMP_LAND, 0, Vec3.ZERO);
    }
}
