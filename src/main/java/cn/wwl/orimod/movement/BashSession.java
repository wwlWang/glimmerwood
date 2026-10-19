package cn.wwl.orimod.movement;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived position ownership. Never changes the target's saved AI or gravity flags. */
public final class BashSession {
    public static final int AIM_TICKS = 12, TARGET_COOLDOWN_TICKS = 40;
    private static final Map<UUID, BashSession> FROZEN = new HashMap<>();
    public final SpiritTargets.Target target;
    public final Vec3 playerPosition, playerVelocity, targetPosition, targetVelocity;
    public final long expiresAt;
    public final float playerFallDistance, targetFallDistance;

    public BashSession(ServerPlayer player, SpiritTargets.Target target) {
        this.target = target;
        playerPosition = player.position();
        playerVelocity = player.getDeltaMovement();
        playerFallDistance = player.fallDistance;
        targetPosition = target.entity() == null ? target.point() : target.entity().position();
        targetVelocity = target.entity() == null ? Vec3.ZERO : target.entity().getDeltaMovement();
        targetFallDistance = target.entity() == null ? 0 : target.entity().fallDistance;
        expiresAt = player.level().getGameTime() + AIM_TICKS;
        if (target.entity() != null) {
            FROZEN.put(target.entity().getUUID(), this);
            target.entity().setDeltaMovement(Vec3.ZERO);
            target.entity().hurtMarked = true;
        }
    }

    public void hold(ServerPlayer player) {
        player.setPos(playerPosition);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = playerFallDistance;
    }

    /** Minimum speed of a redirected projectile; faster ones keep a bit more than their own speed (R33). */
    public static final double REDIRECT_MIN_SPEED = 1.6, REDIRECT_GAIN = 1.2;

    /** @return the speed a projectile caught at {@code originalSpeed} leaves a Bash with. */
    public static double redirectSpeed(double originalSpeed) {
        return Math.min(4, Math.max(REDIRECT_MIN_SPEED, originalSpeed * REDIRECT_GAIN));
    }

    /** @return whether the target was a projectile still in flight when caught (lodged arrows stay plain anchors). */
    public boolean inFlightProjectile() {
        return target.entity() instanceof net.minecraft.world.entity.projectile.Projectile && targetVelocity.lengthSqr() > 0.01;
    }

    public void release(Vec3 launchDirection) { release(launchDirection, null); }

    /**
     * Ends the hold. On a launch the target is pushed opposite the player's launch, as in Ori; an in-flight
     * projectile (R33) is instead fired back that way at {@link #redirectSpeed} and now belongs to the
     * player, so it can hurt its original shooter and cannot hit the player who redirected it.
     */
    public void release(Vec3 launchDirection, ServerPlayer redirector) {
        Entity entity = target.entity();
        if (entity == null) return;
        FROZEN.remove(entity.getUUID(), this);
        if (launchDirection != null && entity instanceof cn.wwl.orimod.combat.LightBurst burst) { burst.detonate(); return; }
        if (!entity.isRemoved() && launchDirection != null && redirector != null && inFlightProjectile()) {
            var projectile = (net.minecraft.world.entity.projectile.Projectile) entity;
            Vec3 velocity = launchDirection.normalize().scale(-redirectSpeed(targetVelocity.length()));
            projectile.setOwner(redirector);
            projectile.setDeltaMovement(velocity);
            double horizontal = velocity.horizontalDistance();
            projectile.setYRot((float) (net.minecraft.util.Mth.atan2(velocity.x, velocity.z) * (180 / Math.PI)));
            projectile.setXRot((float) (net.minecraft.util.Mth.atan2(velocity.y, horizontal) * (180 / Math.PI)));
            projectile.yRotO = projectile.getYRot(); projectile.xRotO = projectile.getXRot();
            entity.fallDistance = 0;
            entity.hurtMarked = true;
            redirected++;
            return;
        }
        if (!entity.isRemoved()) {
            entity.setDeltaMovement(launchDirection == null ? targetVelocity : launchDirection.scale(-0.8));
            entity.fallDistance = launchDirection == null ? targetFallDistance : 0;
            entity.hurtMarked = true;
        }
    }

    public static void tickTarget(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide) {
            var session = FROZEN.get(entity.getUUID());
            if (session != null) {
                if (entity.level().getGameTime() > session.expiresAt || !entity.isAlive()) session.release(null);
                else { entity.setPos(session.targetPosition); entity.setDeltaMovement(Vec3.ZERO); entity.fallDistance = session.targetFallDistance; }
            }
        }
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpiritMovement.cancel(player, player.getData(SpiritData.MOVEMENT));
    }

    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpiritMovement.cancel(player, player.getData(SpiritData.MOVEMENT));
    }

    /** Projectiles fired back by Bash since server start (diagnostics). */
    public static int redirected;
    public static int frozenCount() { return FROZEN.size(); }
    public static void releaseAll() {
        for (var session : java.util.List.copyOf(FROZEN.values())) session.release(null);
        FROZEN.clear();
    }
    public static void discardAll() { FROZEN.clear(); }
}
