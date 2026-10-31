package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Invisible continuous wind in the landmark shaft. Walking is unaffected; only Glide accepts lift. */
public final class GreatTreeWindBlock extends ProtectedBlock implements LiquidBlockContainer {
    public GreatTreeWindBlock(Properties p){super(p);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    // FlowingFluid checks this before blocksMotion; noCollision alone would let water erase wind.
    @Override public boolean canPlaceLiquid(net.minecraft.world.entity.player.Player player,BlockGetter level,BlockPos pos,BlockState state,net.minecraft.world.level.material.Fluid fluid){return false;}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,net.minecraft.world.level.material.FluidState fluid){return false;}
    @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource random){
        if((p.getY()&7)!=0||random.nextInt(4)!=0)return;
        l.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(),0xBFF0FF,.18f,30),
                p.getX()+random.nextDouble(),p.getY(),p.getZ()+random.nextDouble(),0,.15,0);
    }
}
