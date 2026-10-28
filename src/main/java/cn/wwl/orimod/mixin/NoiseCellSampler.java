package cn.wwl.orimod.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use the native density/aquifer result while sharing one native interpolation cell. */
@Mixin(NoiseChunk.class)
public interface NoiseCellSampler {
    @Invoker("getInterpolatedState")
    BlockState orimod$interpolatedState();
}
