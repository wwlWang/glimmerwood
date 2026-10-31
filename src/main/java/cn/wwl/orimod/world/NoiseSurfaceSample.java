package cn.wwl.orimod.world;

import cn.wwl.orimod.mixin.NoiseColumnSampler;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;

/** Both vanilla height predicates in one traversal; no surface-rule, terrain or density changes. */
public record NoiseSurfaceSample(int floor, int surface) {
    public static NoiseSurfaceSample at(ChunkGenerator generator, RandomState random, LevelHeightAccessor height, int x, int z) {
        // A custom generator may override the height contract; leave that implementation in charge.
        if (generator.getClass() != NoiseBasedChunkGenerator.class)
            return new NoiseSurfaceSample(generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,height,random),
                    generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,height,random));
        var noise=((NoiseBasedChunkGenerator)generator).generatorSettings().value().noiseSettings().clampToHeightAccessor(height);
        int cell=noise.getCellHeight();
        int[] y={(Math.floorDiv(noise.minY(),cell)+Math.floorDiv(noise.height(),cell))*cell-1};
        int[] surface={height.getMinBuildHeight()};
        boolean[] found={false};
        var floor=((NoiseColumnSampler)generator).orimod$iterateColumn(height,random,x,z,null,state->{
            int at=y[0]--;
            if(!found[0] && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state)){surface[0]=at+1;found[0]=true;}
            return Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state);
        });
        return new NoiseSurfaceSample(floor.orElse(height.getMinBuildHeight()),surface[0]);
    }
}
