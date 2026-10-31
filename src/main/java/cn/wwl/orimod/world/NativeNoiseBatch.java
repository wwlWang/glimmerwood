package cn.wwl.orimod.world;

import cn.wwl.orimod.mixin.NoiseCellSampler;
import cn.wwl.orimod.mixin.NoiseColumnSampler;
import cn.wwl.orimod.mixin.NoiseInterpolatorAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.*;

/** One native NoiseChunk per 16x16 region, with lazy cells and reusable X interpolation slices.
 * Mutable instances never escape a single preflight. Shared caches contain only completed column values. */
final class NativeNoiseBatch {
    private static final DensityFunctions.BeardifierOrMarker MARKER=(DensityFunctions.BeardifierOrMarker)
            DensityFunctions.BeardifierOrMarker.CODEC.codec().codec().parse(com.mojang.serialization.JsonOps.INSTANCE,new com.google.gson.JsonObject()).getOrThrow();
    record Cell(NoiseChunk chunk,int z) {}
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor level;
    private final Map<Long,Region> regions=new HashMap<>();
    NativeNoiseBatch(NoiseBasedChunkGenerator generator,RandomState random,LevelHeightAccessor level){
        this.generator=generator;this.random=random;this.level=level;
    }
    Cell begin(int x,int z){
        var settings=generator.generatorSettings().value();
        var noise=settings.noiseSettings().clampToHeightAccessor(level);
        if(16%noise.getCellWidth()!=0){
            // Data packs may configure 12-wide cells, which do not partition a 16-wide chunk.
            var chunk=new NoiseChunk(1,random,x,z,noise,MARKER,settings,((NoiseColumnSampler)generator).orimod$fluidPicker().get(),Blender.empty());
            chunk.initializeForFirstCellX();chunk.advanceCellX(0);return new Cell(chunk,0);
        }
        return regions.computeIfAbsent(ChunkPos.asLong(x>>4,z>>4),k->new Region(x&~15,z&~15)).begin(x,z);
    }
    private final class Region {
        final NoiseChunk chunk;
        final NoiseCellSampler access;
        final int x0,z0,width;
        final double[][][][] slices;
        Region(int x,int z){
            x0=x;z0=z;var settings=generator.generatorSettings().value();
            var noise=settings.noiseSettings().clampToHeightAccessor(level);width=noise.getCellWidth();
            chunk=new NoiseChunk(16/width,random,x,z,noise,MARKER,settings,((NoiseColumnSampler)generator).orimod$fluidPicker().get(),Blender.empty());
            access=(NoiseCellSampler)chunk;slices=new double[16/width+1][][][];
        }
        Cell begin(int x,int z){
            access.orimod$interpolating(true);
            int cx=(x-x0)/width;
            slice(cx,true);slice(cx+1,false);
            access.orimod$cellStartBlockX(x0+cx*width);
            return new Cell(chunk,(z-z0)/width);
        }
        private void slice(int x,boolean left){
            var interpolators=access.orimod$interpolators();
            if(slices[x]==null){
                access.orimod$fillSlice(left,x0/width+x);
                var saved=new double[interpolators.size()][][];
                for(int i=0;i<saved.length;i++){
                    var sampler=(NoiseInterpolatorAccessor)interpolators.get(i);
                    var source=left?sampler.orimod$slice0():sampler.orimod$slice1();
                    saved[i]=Arrays.stream(source).map(double[]::clone).toArray(double[][]::new);
                }
                slices[x]=saved;
            }else for(int i=0;i<interpolators.size();i++){
                var sampler=(NoiseInterpolatorAccessor)interpolators.get(i);
                var destination=left?sampler.orimod$slice0():sampler.orimod$slice1();
                for(int z=0;z<destination.length;z++)System.arraycopy(slices[x][i][z],0,destination[z],0,destination[z].length);
            }
        }
    }
}
