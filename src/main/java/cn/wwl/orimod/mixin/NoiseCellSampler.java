package cn.wwl.orimod.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Use the native density/aquifer result while sharing one native interpolation cell. */
@Mixin(NoiseChunk.class)
public interface NoiseCellSampler {
    @Accessor("interpolators") java.util.List<NoiseChunk.NoiseInterpolator> orimod$interpolators();
    @Accessor("cellStartBlockX") void orimod$cellStartBlockX(int value);
    @Accessor("interpolating") void orimod$interpolating(boolean value);
    @Invoker("fillSlice") void orimod$fillSlice(boolean left,int cellX);
    @Invoker("getInterpolatedState")
    BlockState orimod$interpolatedState();
}
