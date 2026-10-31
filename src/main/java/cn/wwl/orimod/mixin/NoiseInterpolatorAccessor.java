package cn.wwl.orimod.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native interpolation slices; copied only by a thread-confined noise sampler. */
@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface NoiseInterpolatorAccessor {
    @Accessor("slice0") double[][] orimod$slice0();
    @Accessor("slice1") double[][] orimod$slice1();
}
