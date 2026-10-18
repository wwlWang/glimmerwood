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

    public void release(Vec3 launchDirection) {
        Entity entity = target.entity();
        if (entity == null) return;
        FROZEN.remove(entity.getUUID(), this);
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

    public static int frozenCount() { return FROZEN.size(); }
    public static void releaseAll() {
        for (var session : java.util.List.copyOf(FROZEN.values())) session.release(null);
        FROZEN.clear();
    }
    public static void discardAll() { FROZEN.clear(); }
}
