package cn.wwl.orimod.world;

import cn.wwl.orimod.OriMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Server-owned SavedData; worldgen threads receive immutable snapshots and enqueue timing only. */
public final class GreatTreeSites {
    private GreatTreeSites(){}
    public static final int MIN_DISTANCE=300,MAX_DISTANCE=800,OLD_WORLD_LIMIT=2500;
    private static final Map<RandomState,Runtime> ACTIVE=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Long,GreatTreeShape> SHAPES=new LinkedHashMap<>();
    private static final TagKey<net.minecraft.world.level.biome.Biome> PREFERRED=TagKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("orimod","has_spirit_tree"));
    public record Timing(int chunkX,int chunkZ,int writes,double shapeMs,double writeMs){}
    private static final class Runtime {
        final GreatTreeSite.Site site;
        final ConcurrentLinkedQueue<Timing> pending=new ConcurrentLinkedQueue<>();
        final Map<Long,Timing> timings=new TreeMap<>();
        Runtime(GreatTreeSite.Site site){this.site=site;}
    }
    public static GreatTreeSite.Site site(RandomState state){var r=ACTIVE.get(state);return r==null?null:r.site;}
    public static boolean reserved(RandomState state,int x,int z,int margin){var s=site(state);return s!=null&&s.covers(x,z,margin);}
    private static void publish(ServerLevel level,GreatTreeSite.Site site){ACTIVE.put(level.getChunkSource().randomState(),new Runtime(site));}
    public static void load(LevelEvent.Load e){
        if(e.getLevel() instanceof ServerLevel level && level.dimension()==Level.OVERWORLD){var data=GreatTreeSite.get(level);if(data.site()!=null)publish(level,data.site());}
    }
    public static void started(ServerStartedEvent e){
        var level=e.getServer().overworld();
        if(!e.getServer().getWorldData().worldGenOptions().generateStructures() || level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource)return;
        var data=GreatTreeSite.get(level);
        if(data.site()==null)choose(level,0);else publish(level,data.site());
    }
    public static void unload(LevelEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel level){flush(level);ACTIVE.remove(level.getChunkSource().randomState());synchronized(SHAPES){SHAPES.clear();}}
    }
    public static void tick(LevelTickEvent.Post e){if(e.getLevel() instanceof ServerLevel level)flush(level);}
    private static void flush(ServerLevel level){
        var r=ACTIVE.get(level.getChunkSource().randomState());if(r==null)return;
        for(Timing t;(t=r.pending.poll())!=null;){r.timings.put(ChunkPos.asLong(t.chunkX,t.chunkZ),t);GreatTreeSite.get(level).begun(true);}
    }
    public static List<Timing> timings(ServerLevel level){flush(level);var r=ACTIVE.get(level.getChunkSource().randomState());return r==null?List.of():List.copyOf(r.timings.values());}
    public static void record(RandomState state,ChunkPos chunk,int writes,long shapeNanos,long writeNanos){
        var r=ACTIVE.get(state);if(r!=null)r.pending.add(new Timing(chunk.x,chunk.z,writes,shapeNanos/1e6,writeNanos/1e6));
        OriMod.LOGGER.info("Great Spirit Tree chunk {},{}: {} writes, shape/cache {} ms, writes {} ms",chunk.x,chunk.z,writes,shapeNanos/1e6,writeNanos/1e6);
    }
    public static GreatTreeShape shape(long seed,int version){
        if(version!=GreatTreeShape.VERSION)throw new IllegalArgumentException("Unsupported great tree shape version "+version);
        synchronized(SHAPES){
            var shape=SHAPES.get(seed);if(shape!=null)return shape;
            shape=new GreatTreeShape(seed);if(SHAPES.size()>=4)SHAPES.remove(SHAPES.keySet().iterator().next());SHAPES.put(seed,shape);return shape;
        }
    }

    /** No chunk generation during selection. Every touched on-disk or live chunk rejects the whole footprint. */
    public static boolean choose(ServerLevel level,int salt){
        flush(level);
        var data=GreatTreeSite.get(level);if(data.begun())return false;
        BlockPos spawn=level.getSharedSpawnPos();long seed=level.getSeed();
        var generator=level.getChunkSource().getGenerator();var state=level.getChunkSource().randomState();
        var disk=new RegionHeaders(level.getServer().getWorldPath(LevelResource.ROOT).resolve("region"));
        // A start may exist before its first FEATURES callback. Re-pick must not orphan it and create two trees.
        if(data.site()!=null){
            var old=data.site().origin();
            for(int x=(old.getX()-56)>>4;x<=(old.getX()+56)>>4;x++)for(int z=(old.getZ()-56)>>4;z<=(old.getZ()+56)>>4;z++)
                if(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x,z))!=null || disk.exists(x,z))return false;
        }
        var rejected=new TreeMap<String,Integer>();long start=System.nanoTime();
        double angle=new Random(seed^0x47524541544cL^salt).nextDouble()*Math.PI*2;
        // Preferred biomes first inside 800; other dry land second. Only then expand for already-generated worlds.
        for(int pass=0;pass<3;pass++)for(int radius=pass==2?832:336;radius<=(pass==2?OLD_WORLD_LIMIT:MAX_DISTANCE-16);radius+=64){
            int samples=Math.max(24,(int)(radius*Math.PI*2/96));
            for(int i=0;i<samples;i++){
                double a=angle+i*Math.PI*2/samples;
                int x=((spawn.getX()+(int)(Math.cos(a)*radius))&~15)+8,z=((spawn.getZ()+(int)(Math.sin(a)*radius))&~15)+8;
                double distance=Math.hypot(x-spawn.getX(),z-spawn.getZ());
                if(distance<MIN_DISTANCE || distance>(pass==2?OLD_WORLD_LIMIT:MAX_DISTANCE))continue;
                String reason=null;
                for(int cx=(x-56)>>4;cx<=(x+56)>>4 && reason==null;cx++)for(int cz=(z-56)>>4;cz<=(z+56)>>4;cz++)
                    if(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(cx,cz))!=null || disk.exists(cx,cz)){reason="GENERATED";break;}
                if(reason!=null){rejected.merge(reason,1,Integer::sum);continue;}
                int y=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,state);
                var biome=generator.getBiomeSource().getNoiseBiome(x>>2,y>>2,z>>2,state.sampler());
                if(biome.is(BiomeTags.IS_OCEAN)||biome.is(BiomeTags.IS_DEEP_OCEAN)||biome.is(BiomeTags.IS_MOUNTAIN)||biome.is(BiomeTags.IS_RIVER) || pass==0&&!biome.is(PREFERRED))reason="BIOME";
                else if(y<generator.getSeaLevel()+2 || y+GreatTreeShape.MAX_Y>=level.getMaxBuildHeight())reason="HEIGHT_OR_WATER";
                int min=y,max=y,innerMax=y;
                if(reason==null)for(int dx=-32;dx<=32 && reason==null;dx+=8)for(int dz=-32;dz<=32;dz+=8){
                    if(dx*dx+dz*dz>32*32)continue;
                    int h=generator.getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,level,state);
                    min=Math.min(min,h);max=Math.max(max,h);if(Math.abs(dx)<=8 && Math.abs(dz)<=8)innerMax=Math.max(innerMax,h);
                    if(h<generator.getSeaLevel()+1 || max-min>12 || Math.abs(dx)<=8&&Math.abs(dz)<=8&&Math.abs(h-y)>4){reason="RELIEF_OR_SHORE";break;}
                }
                if(reason!=null){rejected.merge(reason,1,Integer::sum);continue;}
                var chosen=new GreatTreeSite.Site(new BlockPos(x,innerMax,z),spawn,seed^BlockPos.asLong(x,0,z)^0x115B,GreatTreeShape.VERSION);
                data.choose(chosen);
                // Persist before worldgen can see it: a crash must not lose the choice but keep its generated chunks.
                level.getDataStorage().save();publish(level,chosen);
                OriMod.LOGGER.info("Great Spirit Tree site {} spawn {} distance {} selection {} ms rejected {}",chosen.origin(),spawn,chosen.spawnDistance(),(System.nanoTime()-start)/1e6,rejected);
                return true;
            }
        }
        OriMod.LOGGER.warn("No untouched Great Spirit Tree site within {} of spawn {}: {}",OLD_WORLD_LIMIT,spawn,rejected);return false;
    }

    /** Read-only Anvil location tables. A partial or corrupt file rejects conservatively; never creates/opens a chunk. */
    static final class RegionHeaders {
        private final Path directory;private final Map<Long,int[]> headers=new HashMap<>();
        RegionHeaders(Path directory){this.directory=directory;}
        boolean exists(int cx,int cz){
            int rx=Math.floorDiv(cx,32),rz=Math.floorDiv(cz,32);
            int[] table=headers.computeIfAbsent(ChunkPos.asLong(rx,rz),key->{
                int[] entries=new int[1024];Path file=directory.resolve("r."+rx+"."+rz+".mca");
                if(!Files.exists(file))return entries;
                try(var channel=FileChannel.open(file,StandardOpenOption.READ)){
                    var bytes=ByteBuffer.allocate(4096);while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new IOException("Truncated location table");
                    bytes.flip();for(int i=0;i<entries.length;i++)entries[i]=bytes.getInt();
                }catch(IOException exception){Arrays.fill(entries,1);}
                return entries;
            });
            return table[Math.floorMod(cx,32)+Math.floorMod(cz,32)*32]!=0;
        }
    }
}
