package cn.wwl.orimod.block;

import cn.wwl.orimod.combat.SpiritFlame;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.Comparator;

/** R103: a low crystal vessel, broken once by a player's attack; no block-item loot. */
public final class SpiritLightVesselBlock extends ProtectedBlock {
    private static final VoxelShape SHAPE = box(3,0,3,13,10,13);
    public SpiritLightVesselBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer server) burst(server,pos);
    }
    @Override protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (projectile.getOwner() instanceof ServerPlayer player) burst(player,hit.getBlockPos());
    }
    public static int amount(long seed, BlockPos pos) { return 25 + RandomSource.create(seed ^ pos.asLong()).nextInt(16); }
    public static boolean burst(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(OriBlocks.SPIRIT_LIGHT_VESSEL)) return false;
        var state = level.getBlockState(pos);
        // Remove first: an arrow callback, area hit or repeated click in this tick cannot pay twice.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        for (var orb : SpiritProgression.lightOrbs(level, Vec3.atBottomCenterOf(pos).add(0,.4,0), amount(level.getSeed(),pos), level.random)) level.addFreshEntity(orb);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,state),pos.getX()+.5,pos.getY()+.3,pos.getZ()+.5,16,.25,.25,.25,.05);
        level.playSound(null,pos,SoundEvents.AMETHYST_CLUSTER_BREAK,SoundSource.BLOCKS,.8f,1.3f);
        CompanionLines.offer(player,CompanionLines.Line.FIRST_VESSEL);
        return true;
    }
    /** Only called when the existing enemy selector found no target. The first collider must be this vessel. */
    public static boolean flame(ServerPlayer player) {
        var level = player.serverLevel(); double radius = SpiritFlame.RANGE;
        var pos = CrackedRuin.scan(level,player.getBoundingBox().inflate(radius),s -> s.is(OriBlocks.SPIRIT_LIGHT_VESSEL)).stream()
                .filter(p -> p.distToCenterSqr(player.position()) <= radius*radius)
                .sorted(Comparator.comparingDouble(p -> p.distToCenterSqr(player.position())))
                .filter(p -> {
                    var hit = level.clip(new ClipContext(player.getEyePosition(),Vec3.atBottomCenterOf(p).add(0,.3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                    return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(p);
                }).findFirst().orElse(null);
        if (pos == null || !burst(player,pos)) return false;
        player.getData(SpiritData.COOLDOWNS).flameReadyAt = cn.wwl.orimod.progression.SpiritCooldowns.now(player)+SpiritFlame.COOLDOWN_TICKS;
        SpiritFx.emit(level,SpiritFxKind.SPIRIT_FLAME,Vec3.atBottomCenterOf(pos).add(0,.3,0),player);
        return true;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double pulse = .5+.5*Math.sin((level.getGameTime()+pos.asLong()%60)*Math.PI/30);
        if (random.nextDouble() < .15+.5*pulse)
            level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(),0xC4EEFF,(float)(.10+.12*pulse),24),
                    pos.getX()+.5+(random.nextDouble()-.5)*.45,pos.getY()+.35,pos.getZ()+.5+(random.nextDouble()-.5)*.45,0,.015,0);
    }
}
