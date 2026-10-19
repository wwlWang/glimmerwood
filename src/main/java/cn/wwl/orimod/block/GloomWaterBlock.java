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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * R61 gloom water (after Ori's poisoned water): a still pool of corruption. Walking through it hurts and slows
 * living things; it never spreads. Charge Flame, a Light Burst or the Gloom Heart's defeat cleanses it into
 * ordinary water.
 */
public final class GloomWaterBlock extends Block {
    public static final float DAMAGE = 2;
    public static final int HURT_INTERVAL = 15;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public GloomWaterBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()) return;
        living.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(.7, .6, .7));
        // Vanilla hurt immunity paces the damage (one hit per ~half second).
        if (living.hurt(level.damageSources().magic(), DAMAGE)) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false));
        }
    }

    /** Turn gloom water within the radius into still water. @return blocks cleansed. */
    public static int cleanse(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        for (var pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > radius * radius || !level.getBlockState(pos).is(OriBlocks.GLOOM_WATER.get())) continue;
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
