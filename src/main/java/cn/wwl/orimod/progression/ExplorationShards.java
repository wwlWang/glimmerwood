package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-owned effects; equipment checks include spirit form. No world edits or persistent timers. */
public final class ExplorationShards {
    private ExplorationShards() {}
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        DeepBreath.update(p);
        if (!p.isAlive() || p.isSpectator()) return;
        if (p.level().getGameTime() % ExplorationShardRules.MAGNET_INTERVAL == 0) pull(p);
        if (Shard.EMBER_SKIN.equipped(p) && p.getRemainingFireTicks() > 0)
            p.setRemainingFireTicks(ExplorationShardRules.fireTicks(p.getRemainingFireTicks()));
    }
    public static int pull(ServerPlayer p) {
        if (!Shard.MAGNET.equipped(p) || p.isSpectator()) return 0;
        var target = p.position().add(0, .35, 0);
        var bounds = p.getBoundingBox().inflate(ExplorationShardRules.MAGNET_RADIUS);
        int count = 0;
        for (var entity : p.level().getEntities(p, bounds, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
            if (!entity.isAlive() || entity.distanceToSqr(p) > ExplorationShardRules.MAGNET_RADIUS * ExplorationShardRules.MAGNET_RADIUS) continue;
            if (entity instanceof ItemEntity item && (item.hasPickUpDelay() || item.getTarget() != null && !item.getTarget().equals(p.getUUID()))) continue;
            var delta = target.subtract(entity.position());
            entity.setDeltaMovement(delta.normalize().scale(Math.min(ExplorationShardRules.MAGNET_SPEED, delta.length())));
            entity.hurtMarked = true;
            count++;
        }
        return count;
    }
    public static void breathe(LivingBreatheEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            boolean active = !event.canBreathe() && !Ability.WATER_DASH.unlocked(p) && Shard.DEEP_BREATH.equipped(p) && !p.isSpectator();
            event.setConsumeAirAmount(p.getData(SpiritData.ENERGY_STATE).breath.consume(event.getConsumeAirAmount(), active));
        }
    }
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Shard.EMBER_SKIN.equipped(p) && event.getSource().is(DamageTypes.LAVA))
            event.setAmount(ExplorationShardRules.lavaDamage(event.getAmount()));
    }
    public static void drops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer p) || !p.isAlive() || p.isCreative() || p.isSpectator() || event.isCanceled()) return;
        var state = event.getState();
        if (Shard.PROSPECTOR.equipped(p) && (state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES))) {
            var timers = p.getData(SpiritData.ENERGY_STATE);
            long now = p.level().getGameTime();
            if (ExplorationShardRules.ready(now, timers.prospectorAt)) {
                // Claim before the roll: unsuccessful attempts are throttled too.
                timers.prospectorAt = now;
                if (p.getRandom().nextInt(ExplorationShardRules.PROSPECTOR_CHANCE) == 0) reveal(p, event.getPos());
            }
        }
        boolean mature = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        if (Shard.FORAGER.equipped(p) && (mature || state.is(BlockTags.LEAVES))
                && p.getRandom().nextInt(ExplorationShardRules.FORAGER_CHANCE) == 0) {
            // Copy one actual loot item; never duplicate entire Fortune stacks or sheared/Silk Touch leaf blocks.
            var drop = event.getDrops().stream().filter(e -> !e.getItem().isEmpty()
                    && (!state.is(BlockTags.LEAVES) || !e.getItem().is(state.getBlock().asItem()))).findFirst();
            drop.ifPresent(original -> {
                var extra = new ItemEntity(event.getLevel(), original.getX(), original.getY(), original.getZ(), original.getItem().copyWithCount(1));
                extra.setDefaultPickUpDelay(); event.getDrops().add(extra);
            });
        }
    }
    /** Bounded, loaded-only scan, at most once per five seconds; particles point toward the nearest ore. */
    public static boolean reveal(ServerPlayer p, BlockPos mined) {
        BlockPos nearest = null;
        int radius = ExplorationShardRules.PROSPECTOR_RADIUS;
        for (var pos : BlockPos.betweenClosed(mined.offset(-radius, -radius, -radius), mined.offset(radius, radius, radius))) {
            if (pos.distSqr(mined) > radius * radius || !p.serverLevel().hasChunkAt(pos)
                    || !p.level().getBlockState(pos).is(Tags.Blocks.ORES)) continue;
            if (nearest == null || pos.distSqr(mined) < nearest.distSqr(mined)) nearest = pos.immutable();
        }
        if (nearest == null) return false;
        var eye = p.getEyePosition();
        var direction = Vec3.atCenterOf(nearest).subtract(eye).normalize();
        for (int i = 1; i <= 4; i++) {
            var at = eye.add(direction.scale(.4 + .22 * i));
            p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, false, at.x, at.y, at.z, 1, .015, .015, .015, 0);
        }
        return true;
    }
}
