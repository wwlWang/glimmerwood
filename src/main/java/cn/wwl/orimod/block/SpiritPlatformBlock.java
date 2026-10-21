package cn.wwl.orimod.block;

import cn.wwl.orimod.fx.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

/** Ghost outline until illuminated; warning never changes collision or block-light emission. */
public final class SpiritPlatformBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT, FADING = BooleanProperty.create("fading"), BLINK = BooleanProperty.create("blink");
    public SpiritPlatformBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(LIT,false).setValue(FADING,false).setValue(BLINK,false));
    }
    public static int light(BlockState state) { return state.getValue(LIT) ? 12 : 2; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(LIT,FADING,BLINK); }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return s.getValue(LIT) ? Shapes.block() : Shapes.empty(); }
    @Override protected VoxelShape getOcclusionShape(BlockState s, BlockGetter l, BlockPos p) { return s.getValue(LIT) ? Shapes.block() : Shapes.empty(); }
    @Override protected boolean isPathfindable(BlockState s, PathComputationType type) { return !s.getValue(LIT) && type != PathComputationType.WATER; }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { PlatformLight.tick(l,p); }
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
        super.onPlace(s,l,p,old,moving);
        if (!old.is(this) && l instanceof ServerLevel server) PlatformLight.initialize(server,p);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        super.onRemove(s,l,p,next,moving);
        if (!next.is(this) && l instanceof ServerLevel server) PlatformTimers.get(server).remove(p.asLong());
    }
    @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource random) {
        if (s.getValue(FADING) && s.getValue(BLINK) && random.nextInt(3)==0)
            l.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(),0xC5F4FF,.12f,15),
                    p.getX()+random.nextDouble(),p.getY()+1.03,p.getZ()+random.nextDouble(),0,.01,0);
    }
}
