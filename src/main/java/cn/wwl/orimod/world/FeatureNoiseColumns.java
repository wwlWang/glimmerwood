package cn.wwl.orimod.world;

import cn.wwl.orimod.mixin.NoiseCellSampler;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import java.util.*;

/** Exact vanilla column states, sharing a native 4x4 interpolation cell and stopping below the feature's roots. */
final class FeatureNoiseColumns {
    record Column(NoiseColumn blocks,int floor,int surface,int minY) {}
    private record Key(NoiseBasedChunkGenerator generator,int minY,int height,int x,int z) {}
    private static final Map<RandomState,RuinDecisionCache<Key,List<Column>>> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor level;
    private final int width;
    private final NativeNoiseBatch batch;
    private final Map<Long,List<Column>> cells=new HashMap<>();
    FeatureNoiseColumns(NoiseBasedChunkGenerator generator,RandomState random,LevelHeightAccessor level){
        this.generator=generator;this.random=random;this.level=level;batch=new NativeNoiseBatch(generator,random,level);width=generator.generatorSettings().value().noiseSettings().getCellWidth();
    }
    Column at(int x,int z){
        int cx=Math.floorDiv(x,width),cz=Math.floorDiv(z,width);
        var columns=cells.computeIfAbsent(ChunkPos.asLong(cx,cz),ignored->{
            RuinDecisionCache<Key,List<Column>> cache;
            synchronized(WORLDS){cache=WORLDS.computeIfAbsent(random,key->new RuinDecisionCache<>(4096));}
            return cache.get(new Key(generator,level.getMinBuildHeight(),level.getHeight(),cx,cz),()->sample(cx*width,cz*width));
        });
        return columns.get(Math.floorMod(z,width)*width+Math.floorMod(x,width));
    }
    private List<Column> sample(int x0,int z0){
        var settings=generator.generatorSettings().value();var noise=settings.noiseSettings().clampToHeightAccessor(level);
        int cellHeight=noise.getCellHeight(),count=noise.height()/cellHeight,minCell=Math.floorDiv(noise.minY(),cellHeight);
        int min=minCell*cellHeight,top=(minCell+count)*cellHeight,n=width*width;
        if(count<=0)return Collections.nCopies(n,new Column(new NoiseColumn(min,new BlockState[0]),min,min,min));
        var blocks=new BlockState[n][top-min];for(var b:blocks)Arrays.fill(b,Blocks.AIR.defaultBlockState());
        var floors=new int[n];var surfaces=new int[n];Arrays.fill(floors,min);Arrays.fill(surfaces,min);
        var found=new boolean[n];int remaining=n,stop=min;
        var cell=batch.begin(x0,z0);var chunk=cell.chunk();
        try{
            for(int cy=count-1;cy>=0;cy--){
                chunk.selectCellYZ(cy,cell.z());
                for(int dy=cellHeight-1;dy>=0;dy--){
                    int y=(minCell+cy)*cellHeight+dy;
                    if(remaining==0&&y<stop)return columns(blocks,floors,surfaces,min,stop);
                    chunk.updateForY(y,(double)dy/cellHeight);
                    for(int dx=0;dx<width;dx++){
                        chunk.updateForX(x0+dx,(double)dx/width);
                        for(int dz=0;dz<width;dz++){
                            int i=dz*width+dx;chunk.updateForZ(z0+dz,(double)dz/width);
                            var state=((NoiseCellSampler)chunk).orimod$interpolatedState();if(state==null)state=settings.defaultBlock();
                            blocks[i][y-min]=state;
                            if(surfaces[i]==min&&Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state))surfaces[i]=y+1;
                            if(!found[i]&&Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)){
                                floors[i]=y+1;found[i]=true;remaining--;
                                if(remaining==0)stop=Math.max(min,Arrays.stream(floors).min().orElse(min)-8);
                            }
                        }
                    }
                }
            }
            return columns(blocks,floors,surfaces,min,min);
        }finally{chunk.stopInterpolation();}
    }
    private static List<Column> columns(BlockState[][] blocks,int[] floors,int[] surfaces,int min,int stop){
        var out=new ArrayList<Column>();
        for(int i=0;i<blocks.length;i++)out.add(new Column(new NoiseColumn(stop,Arrays.copyOfRange(blocks[i],stop-min,Math.max(stop,surfaces[i])-min)),floors[i],surfaces[i],stop));
        return List.copyOf(out);
    }
}
