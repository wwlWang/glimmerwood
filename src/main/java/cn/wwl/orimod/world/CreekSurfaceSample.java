package cn.wwl.orimod.world;

import cn.wwl.orimod.mixin.NoiseColumnSampler;
import cn.wwl.orimod.mixin.NoiseCellSampler;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Same vanilla stopping predicate, retaining states above the first solid surface. */
final class CreekSurfaceSample {
    private record CellKey(NoiseBasedChunkGenerator generator,int minY,int maxY,int x,int z) {}
    private static final int CAPACITY=4096;
    // Same weak RandomState lifetime as RuinSitingSites. Values never retain the
    // RandomState/level, and contain only immutable completed surface values.
    private static final java.util.Map<RandomState,RuinDecisionCache<CellKey,java.util.List<Surface>>> WORLDS=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static RuinDecisionCache<CellKey,java.util.List<Surface>> shared(RandomState random){
        synchronized(WORLDS){return WORLDS.computeIfAbsent(random,key->new RuinDecisionCache<>(CAPACITY));}
    }
    // The marker's enum is protected; its public unit codec returns the exact
    // vanilla singleton without substituting a custom density function.
    private static final net.minecraft.world.level.levelgen.DensityFunctions.BeardifierOrMarker MARKER=
            (net.minecraft.world.level.levelgen.DensityFunctions.BeardifierOrMarker)
                    net.minecraft.world.level.levelgen.DensityFunctions.BeardifierOrMarker.CODEC.codec().codec()
                            .parse(com.mojang.serialization.JsonOps.INSTANCE,new com.google.gson.JsonObject()).getOrThrow();
    record Surface(int floor,int surface,boolean dry) {}
    static boolean supported(ChunkGenerator generator){
        // An alternative generator's getBaseHeight/getBaseColumn contract is
        // not inferred from vanilla internals. Keep its original two calls.
        return generator.getClass()==NoiseBasedChunkGenerator.class;
    }
    /** Local lookup backed by bounded single-flight results; never share a NoiseChunk. */
    static final class Cells {
        private final NoiseBasedChunkGenerator generator;
        private final LevelHeightAccessor level;
        private final RandomState random;
        private final int width;
        private final java.util.Map<Long,java.util.List<Surface>> cells=new java.util.HashMap<>();
        Cells(ChunkGenerator generator,LevelHeightAccessor level,RandomState random){
            this.generator=(NoiseBasedChunkGenerator)generator;this.level=level;this.random=random;
            width=this.generator.generatorSettings().value().noiseSettings().getCellWidth();
        }
        Surface at(int x,int z){
            int cx=Math.floorDiv(x,width),cz=Math.floorDiv(z,width);
            var values=cells.computeIfAbsent(((long)cx<<32)^(cz&0xffffffffL),key->shared(random).get(
                    new CellKey(generator,level.getMinBuildHeight(),level.getMaxBuildHeight(),cx*width,cz*width),
                    ()->java.util.List.copyOf(java.util.Arrays.asList(cell(generator,level,random,cx*width,cz*width)))));
            return values.get(Math.floorMod(z,width)*width+Math.floorMod(x,width));
        }
        int count(){return cells.size();}
    }

    /**
     * Exact same interpolation, Beardifier marker and aquifer as iterateNoiseColumn.
     * selectCellYZ already evaluates CacheAllInCell for every x/z; consume those
     * native values once instead of rebuilding the cell for each approach column.
     */
    private static Surface[] cell(NoiseBasedChunkGenerator generator,LevelHeightAccessor level,RandomState random,int x0,int z0){
        var settings=generator.generatorSettings().value();
        var noise=settings.noiseSettings().clampToHeightAccessor(level);
        int width=noise.getCellWidth(),height=noise.getCellHeight(),count=noise.height()/height;
        var out=new Surface[width*width];
        if(count<=0){java.util.Arrays.fill(out,new Surface(level.getMinBuildHeight(),level.getMinBuildHeight(),false));return out;}
        var surface=new int[out.length];java.util.Arrays.fill(surface,level.getMinBuildHeight());
        var above=new net.minecraft.world.level.block.state.BlockState[out.length];
        java.util.Arrays.fill(above,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var chunk=new net.minecraft.world.level.levelgen.NoiseChunk(1,random,x0,z0,noise,
                MARKER,settings,
                ((NoiseColumnSampler)generator).orimod$fluidPicker().get(),net.minecraft.world.level.levelgen.blending.Blender.empty());
        chunk.initializeForFirstCellX();
        try{
            chunk.advanceCellX(0);int left=out.length,minCell=Math.floorDiv(noise.minY(),height);
            for(int cy=count-1;cy>=0&&left>0;cy--){
                chunk.selectCellYZ(cy,0);
                for(int dy=height-1;dy>=0&&left>0;dy--){
                    int y=(minCell+cy)*height+dy;chunk.updateForY(y,(double)dy/height);
                    for(int dx=0;dx<width;dx++){
                        chunk.updateForX(x0+dx,(double)dx/width);
                        for(int dz=0;dz<width;dz++){
                            int i=dz*width+dx;if(out[i]!=null)continue;
                            chunk.updateForZ(z0+dz,(double)dz/width);
                            var state=((NoiseCellSampler)chunk).orimod$interpolatedState();if(state==null)state=settings.defaultBlock();
                            if(surface[i]==level.getMinBuildHeight()&&Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state))surface[i]=y+1;
                            if(Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)){
                                out[i]=new Surface(y+1,surface[i],above[i].getFluidState().isEmpty()&&state.getFluidState().isEmpty()&&!state.isAir());left--;
                            }
                            above[i]=state;
                        }
                    }
                }
            }
            for(int i=0;i<out.length;i++)if(out[i]==null)out[i]=new Surface(level.getMinBuildHeight(),surface[i],false);
            return out;
        }finally{chunk.stopInterpolation();}
    }
    private CreekSurfaceSample(){}
}
