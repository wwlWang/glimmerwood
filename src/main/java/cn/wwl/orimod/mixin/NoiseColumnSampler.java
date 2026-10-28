package cn.wwl.orimod.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Retain the generator's actual fluid picker, including configured aquifer behaviour. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseColumnSampler {
    @Accessor("globalFluidPicker")
    Supplier<Aquifer.FluidPicker> orimod$fluidPicker();
}
