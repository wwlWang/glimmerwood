package cn.wwl.orimod.world;

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
    private static final class World {
        final RuinDecisionCache<CellKey,java.util.List<Surface>> cells=new RuinDecisionCache<>(CAPACITY);
        final RuinDecisionCache<CellKey,Surface> columns=new RuinDecisionCache<>(8192);
    }
    // Sparse and dense results have one lifecycle: clearing a world's cache must invalidate both.
    private static final java.util.Map<RandomState,World> WORLDS=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static World shared(RandomState random){
        synchronized(WORLDS){return WORLDS.computeIfAbsent(random,key->new World());}
    }
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
        private final NativeNoiseBatch batch;
        private final java.util.Map<Long,Surface[]> cells=new java.util.HashMap<>();
        Cells(ChunkGenerator generator,LevelHeightAccessor level,RandomState random){
            this.generator=(NoiseBasedChunkGenerator)generator;this.level=level;this.random=random;batch=new NativeNoiseBatch(this.generator,random,level);
            width=this.generator.generatorSettings().value().noiseSettings().getCellWidth();
        }
        Surface at(int x,int z){
            int cx=Math.floorDiv(x,width),cz=Math.floorDiv(z,width),index=Math.floorMod(z,width)*width+Math.floorMod(x,width);
            long key=((long)cx<<32)^(cz&0xffffffffL);var values=cells.get(key);
            if(values!=null&&values[index]!=null)return values[index];
            var cellKey=new CellKey(generator,level.getMinBuildHeight(),level.getMaxBuildHeight(),cx*width,cz*width);
            var world=shared(random);var shared=world.cells;var existing=shared.completed(cellKey);
            if(existing!=null){values=existing.toArray(Surface[]::new);cells.put(key,values);return values[index];}
            if(values==null){
                // Most four-block footprint samples need just one of a cell's sixteen columns.
                // A second distinct query promotes the cell to a dense sample (entry stairs, etc.).
                values=new Surface[width*width];cells.put(key,values);
                values[index]=world.columns.get(new CellKey(generator,level.getMinBuildHeight(),level.getMaxBuildHeight(),x,z),()->column(generator,level,batch,x,z));
            }else{
                values=shared.get(cellKey,()->java.util.List.copyOf(java.util.Arrays.asList(cell(generator,level,batch,cx*width,cz*width)))).toArray(Surface[]::new);
                cells.put(key,values);
            }
            return values[index];
        }
        int count(){return cells.size();}
    }

    /** The requested column only; the native density/aquifer result and stopping predicate are unchanged. */
    private static Surface column(NoiseBasedChunkGenerator generator,LevelHeightAccessor level,NativeNoiseBatch batch,int x,int z){
        var settings=generator.generatorSettings().value();var noise=settings.noiseSettings().clampToHeightAccessor(level);
        int width=noise.getCellWidth(),height=noise.getCellHeight(),count=noise.height()/height;
        if(count<=0)return new Surface(level.getMinBuildHeight(),level.getMinBuildHeight(),false);
        int x0=Math.floorDiv(x,width)*width,z0=Math.floorDiv(z,width)*width;
        int top=level.getMinBuildHeight(),minCell=Math.floorDiv(noise.minY(),height);
        var above=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var cell=batch.begin(x0,z0);var chunk=cell.chunk();
        try{
            for(int cy=count-1;cy>=0;cy--){
                chunk.selectCellYZ(cy,cell.z());
                for(int dy=height-1;dy>=0;dy--){
                    int y=(minCell+cy)*height+dy;
                    chunk.updateForY(y,(double)dy/height);chunk.updateForX(x,(double)(x-x0)/width);chunk.updateForZ(z,(double)(z-z0)/width);
                    var state=((NoiseCellSampler)chunk).orimod$interpolatedState();if(state==null)state=settings.defaultBlock();
                    if(top==level.getMinBuildHeight()&&Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state))top=y+1;
                    if(Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state))return new Surface(y+1,top,above.getFluidState().isEmpty()&&state.getFluidState().isEmpty()&&!state.isAir());
                    above=state;
                }
            }
            return new Surface(level.getMinBuildHeight(),top,false);
        }finally{chunk.stopInterpolation();}
    }

    /**
     * Exact same interpolation, Beardifier marker and aquifer as iterateNoiseColumn.
     * selectCellYZ already evaluates CacheAllInCell for every x/z; consume those
     * native values once instead of rebuilding the cell for each approach column.
     */
    private static Surface[] cell(NoiseBasedChunkGenerator generator,LevelHeightAccessor level,NativeNoiseBatch batch,int x0,int z0){
        var settings=generator.generatorSettings().value();
        var noise=settings.noiseSettings().clampToHeightAccessor(level);
        int width=noise.getCellWidth(),height=noise.getCellHeight(),count=noise.height()/height;
        var out=new Surface[width*width];
        if(count<=0){java.util.Arrays.fill(out,new Surface(level.getMinBuildHeight(),level.getMinBuildHeight(),false));return out;}
        var surface=new int[out.length];java.util.Arrays.fill(surface,level.getMinBuildHeight());
        var above=new net.minecraft.world.level.block.state.BlockState[out.length];
        java.util.Arrays.fill(above,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var cell=batch.begin(x0,z0);var chunk=cell.chunk();
        try{
            int left=out.length,minCell=Math.floorDiv(noise.minY(),height);
            for(int cy=count-1;cy>=0&&left>0;cy--){
                chunk.selectCellYZ(cy,cell.z());
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
