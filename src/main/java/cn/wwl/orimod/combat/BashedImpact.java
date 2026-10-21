package cn.wwl.orimod.combat;

import cn.wwl.orimod.block.CrackedRuin;
import cn.wwl.orimod.block.SpiritLightVesselBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.*;

/** Tracks only actual Bash launches, for at most two seconds. Never changes a projectile's hit resolution. */
public final class BashedImpact {
    public static final int LIFETIME = 40;
    private record Launch(ServerLevel level, UUID owner, long started) {}
    private static final Map<UUID, Launch> ACTIVE = new HashMap<>();
    public static void mark(Entity entity, Vec3 direction, ServerPlayer owner, boolean flying) {
        if (entity == null) return;
        ACTIVE.remove(entity.getUUID());
        if (owner == null || direction == null || entity.isRemoved() || entity instanceof LightBurst) return;
        if (entity instanceof Enemy || entity instanceof Projectile && flying)
            ACTIVE.put(entity.getUUID(), new Launch(owner.serverLevel(),owner.getUUID(),owner.level().getGameTime()));
    }
    private static ServerPlayer owner(Entity entity) {
        var launch = ACTIVE.get(entity.getUUID()); if (launch == null) return null;
        long now = entity.level().getGameTime();
        var player = launch.level.getServer().getPlayerList().getPlayer(launch.owner);
        if (entity.level() != launch.level || entity.isRemoved() || now < launch.started || now-launch.started > LIFETIME
                || player == null || !player.isAlive() || player.level() != launch.level) {
            ACTIVE.remove(entity.getUUID()); return null;
        }
        return player;
    }
    public static void projectile(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        var projectile = event.getProjectile(); var player = owner(projectile);
        if (player != null) {
            ACTIVE.remove(projectile.getUUID());
            CrackedRuin.hit(player.serverLevel(),hit.getBlockPos(),CrackedRuin.Source.BASHED_IMPACT);
            SpiritLightVesselBlock.burst(player,hit.getBlockPos());
        }
    }
    public static void tick(EntityTickEvent.Post event) {
        var entity = event.getEntity(); if (entity.level().isClientSide) return;
        var player = owner(entity);
        if (player == null || entity instanceof Projectile) return;
        var launch = ACTIVE.get(entity.getUUID());
        if (entity.level().getGameTime() <= launch.started || !(entity.horizontalCollision || entity.verticalCollision)) return;
        // Collision-clipped movement leaves the body touching the block face. Expand only a tiny contact margin.
        var contact = entity.getBoundingBox().inflate(entity.horizontalCollision ? .025 : 0,
                entity.verticalCollision ? .025 : 0, entity.horizontalCollision ? .025 : 0);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(contact.minX,contact.minY,contact.minZ),BlockPos.containing(contact.maxX,contact.maxY,contact.maxZ))) {
            if (!player.serverLevel().hasChunkAt(pos)) continue;
            var state = player.level().getBlockState(pos); var shape = state.getCollisionShape(player.level(),pos);
            if (!shape.isEmpty() && shape.toAabbs().stream().anyMatch(b -> b.move(pos).intersects(contact))
                    && CrackedRuin.kind(state) == CrackedRuin.Kind.WALL) {
                ACTIVE.remove(entity.getUUID()); CrackedRuin.hit(player.serverLevel(),pos,CrackedRuin.Source.BASHED_IMPACT); return;
            }
        }
    }
    public static void leave(EntityLeaveLevelEvent event) { if (!event.getLevel().isClientSide) ACTIVE.remove(event.getEntity().getUUID()); }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ACTIVE.values().removeIf(l -> l.owner.equals(event.getEntity().getUUID())); }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { ACTIVE.values().removeIf(l -> l.owner.equals(event.getEntity().getUUID())); }
    public static void unload(LevelEvent.Unload event) { ACTIVE.values().removeIf(l -> l.level == event.getLevel()); }
    public static void reset() { ACTIVE.clear(); }
    public static int tracked() { return ACTIVE.size(); }
    private BashedImpact() {}
}
