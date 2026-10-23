package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * R61 gloom water (after Ori's poisoned water): a short-spreading fluid. Swimming through it hurts and slows
 * living things; old source blocks keep the same registry ID. Charge Flame, a Light Burst or the Gloom Heart's defeat cleanses it into
 * ordinary water.
 */
public final class GloomWaterBlock extends net.minecraft.world.level.block.LiquidBlock {
    public static final float DAMAGE = 2;
    public static final int HURT_INTERVAL = 15;
    public GloomWaterBlock(Properties properties) { super(OriFluids.GLOOM_WATER.get(), properties); }

    // LiquidBlock's default implementation removes a source even when its fluid has no bucket.
    @Override public net.minecraft.world.item.ItemStack pickupBlock(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()) return;
        // Swimming/drag/flow come from FluidType and FluidTags.WATER, not a solid-block stuck multiplier.
        // Vanilla hurt immunity paces the damage (one hit per ~half second).
        if (living.hurt(level.damageSources().magic(), DAMAGE)) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false));
        }
    }

    /** Turn gloom water within the radius into still water. @return blocks cleansed. */
    public static int cleanse(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        for (var pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > radius * radius || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(OriBlocks.GLOOM_WATER.get())) continue;
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            level.sendParticles(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0xBFF0FF, .5f, 30),
                    pos.getX() + .5, pos.getY() + .9, pos.getZ() + .5, 2, .3, .1, .3, .02);
            n++;
        }
        if (n > 0) level.playSound(null, center, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1, 1.4f);
        return n;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        try {
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), random.nextBoolean() ? 0x5A1A6C : 0x8A2A7C, .35f, 30),
                    pos.getX() + random.nextDouble(), pos.getY() + .9, pos.getZ() + random.nextDouble(), 0, .01, 0);
        } catch (Throwable ignored) { }
    }
}
