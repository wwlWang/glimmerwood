package cn.wwl.orimod.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Retain the generator's actual fluid picker, including configured aquifer behaviour. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseColumnSampler {
    @Accessor("globalFluidPicker")
    Supplier<Aquifer.FluidPicker> orimod$fluidPicker();
    @Invoker("iterateNoiseColumn")
    java.util.OptionalInt orimod$iterateColumn(net.minecraft.world.level.LevelHeightAccessor height,
            net.minecraft.world.level.levelgen.RandomState random, int x, int z,
            org.apache.commons.lang3.mutable.MutableObject<net.minecraft.world.level.NoiseColumn> column,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> stop);
}
