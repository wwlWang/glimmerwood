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
    public static final int MIN_DISTANCE=300,MAX_DISTANCE=800;
    private static final Map<RandomState,Runtime> ACTIVE=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ShapeKey,GreatTreeShape> SHAPES=new LinkedHashMap<>();
    private record ShapeKey(long seed,int version){}
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
    private static boolean enabled(net.minecraft.server.MinecraftServer server){
        return server.getWorldData().worldGenOptions().generateStructures()
                && !(server.overworld().getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource);
    }
    /** loadLevel TAIL: after spawn preparation, before the dedicated-server watchdog is started. */
    public static void prepare(net.minecraft.server.MinecraftServer server){
        if(!enabled(server))return;
        var level=server.overworld();var data=GreatTreeSite.get(level);
        if(!data.needsSearch()){if(data.site()!=null)publish(level,data.site());return;}
        var search=new Search(level,0);
        if(data.completedRange()<MAX_DISTANCE){
            var found=search.run(0,MAX_DISTANCE);
            if(found!=null)data.choose(found);
            data.searched(MAX_DISTANCE);level.getDataStorage().save();
        }
        if(data.site()==null){
            var found=search.run(2,2500);
            if(found!=null)data.choose(found);
            data.searched(2500);level.getDataStorage().save();
        }
        if(data.site()!=null)publish(level,data.site());
        OriMod.LOGGER.info("Great Spirit Tree preparation finished: {} ms, candidates {}, columns {}, range {}, result {}",
                search.elapsedMs(),search.checked,search.terrain.size(),data.completedRange(),data.site()==null?"NO_SITE":data.site().origin());
    }
    /** Publish only. An unresolved result must never trigger an unbounded search after watchdog startup. */
    public static void started(ServerStartedEvent e){
        if(!enabled(e.getServer()))return;
        var level=e.getServer().overworld();var data=GreatTreeSite.get(level);
        if(data.site()!=null)publish(level,data.site());
        else if(data.needsSearch())OriMod.LOGGER.warn("Great Spirit Tree result unresolved after loadLevel; skipping late search");
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
        if(version!=1 && version!=GreatTreeShape.VERSION)throw new IllegalArgumentException("Unsupported great tree shape version "+version);
        synchronized(SHAPES){
            var key=new ShapeKey(seed,version);var shape=SHAPES.get(key);if(shape!=null)return shape;
            shape=new GreatTreeShape(seed,version);if(SHAPES.size()>=4)SHAPES.remove(SHAPES.keySet().iterator().next());SHAPES.put(key,shape);return shape;
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
            var old=data.site().origin();int extent=GreatTreeShape.radius(data.site().shapeVersion())+16;
            for(int x=(old.getX()-extent)>>4;x<=(old.getX()+extent)>>4;x++)for(int z=(old.getZ()-extent)>>4;z<=(old.getZ()+extent)>>4;z++)
                if(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x,z))!=null || disk.exists(x,z))return false;
        }
        var search=new Search(level,salt);
        var chosen=search.run(0,GreatTreeSiteRules.searchLimit(level.getGameTime()));
        if(chosen==null)return false;
        data.choose(chosen);level.getDataStorage().save();publish(level,chosen);return true;
    }

    private record Offset(int x,int z){}
    private static final List<Offset> SAMPLES=samples();
    private static List<Offset> samples(){
        var points=new ArrayList<Offset>();
        for(int dx=-80;dx<=80;dx+=8)for(int dz=-80;dz<=80;dz+=8)
            if(dx*dx+dz*dz<=80*80)points.add(new Offset(dx,dz));
        // Same exact points and predicates: early rejection only changes the work order.
        points.sort(Comparator.comparingInt(p->Math.abs(p.x)<=16&&Math.abs(p.z)<=16?0:p.x*p.x+p.z*p.z<=44*44?1:2));
        return List.copyOf(points);
    }
    private static boolean biomeAllowed(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,int pass){
        return !biome.is(BiomeTags.IS_OCEAN)&&!biome.is(BiomeTags.IS_DEEP_OCEAN)
                &&!biome.is(BiomeTags.IS_MOUNTAIN)&&!biome.is(BiomeTags.IS_RIVER)&&(pass!=0||biome.is(PREFERRED));
    }
    private static final class Search {
        final ServerLevel level;
        final BlockPos spawn;
        final long seed,start=System.nanoTime();
        final net.minecraft.world.level.chunk.ChunkGenerator generator;
        final RandomState state;
        final RegionHeaders disk;
        final Map<Long,NoiseSurfaceSample> terrain=new HashMap<>();
        final double angle;
        long nextLog=start+5_000_000_000L;
        int checked;
        Search(ServerLevel level,int salt){
            this.level=level;spawn=level.getSharedSpawnPos();seed=level.getSeed();
            generator=level.getChunkSource().getGenerator();state=level.getChunkSource().randomState();
            disk=new RegionHeaders(level.getServer().getWorldPath(LevelResource.ROOT).resolve("region"));
            angle=new Random(seed^0x47524541544cL^salt).nextDouble()*Math.PI*2;
        }
        double elapsedMs(){return (System.nanoTime()-start)/1e6;}
        NoiseSurfaceSample column(int x,int z){return terrain.computeIfAbsent(ChunkPos.asLong(x,z),k->NoiseSurfaceSample.at(generator,state,level,x,z));}
        boolean possibleBiome(int x,int z,int pass){
            // Test every possible final quart Y. This may retain a bad candidate, never reject a valid one.
            for(int y=(generator.getSeaLevel()+2)>>2;y<=(level.getMaxBuildHeight()-GreatTreeShape.MAX_Y-8)>>2;y++)
                if(biomeAllowed(generator.getBiomeSource().getNoiseBiome(x>>2,y,z>>2,state.sampler()),pass))return true;
            return false;
        }
        GreatTreeSite.Site run(int firstPass,int searchLimit){
            var rejected=new TreeMap<String,Integer>();
            record Candidate(int x,int z,int pass){}
            var examined=new HashSet<Candidate>();
            for(int pass=firstPass;pass<(searchLimit>MAX_DISTANCE?3:2);pass++)for(int radius=pass==2?832:336;radius<=(pass==2?searchLimit:MAX_DISTANCE-16);radius+=32){
                int samples=Math.max(24,(int)(radius*Math.PI*2/40));
                for(int i=0;i<samples;i++){
                    double a=angle+i*Math.PI*2/samples;
                    int x=((spawn.getX()+(int)(Math.cos(a)*radius))&~15)+8,z=((spawn.getZ()+(int)(Math.sin(a)*radius))&~15)+8;
                    double distance=Math.hypot(x-spawn.getX(),z-spawn.getZ());
                    if(distance<MIN_DISTANCE||distance>(pass==2?searchLimit:MAX_DISTANCE)||!examined.add(new Candidate(x,z,pass)))continue;
                    checked++;
                    if(System.nanoTime()>=nextLog){
                        OriMod.LOGGER.info("Great Spirit Tree search range {} pass {}: checked {}, elapsed {} ms",searchLimit,pass,checked,elapsedMs());
                        nextLog=System.nanoTime()+5_000_000_000L;
                    }
                    String reason=null;int extent=GreatTreeShape.RADIUS+16;
                    for(int cx=(x-extent)>>4;cx<=(x+extent)>>4&&reason==null;cx++)for(int cz=(z-extent)>>4;cz<=(z+extent)>>4;cz++)
                        if(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(cx,cz))!=null||disk.exists(cx,cz)){reason="GENERATED";break;}
                    if(reason==null&&!possibleBiome(x,z,pass))reason="BIOME_PREFILTER";
                    if(reason!=null){rejected.merge(reason,1,Integer::sum);continue;}
                    int y=column(x,z).floor();
                    var biome=generator.getBiomeSource().getNoiseBiome(x>>2,y>>2,z>>2,state.sampler());
                    if(!biomeAllowed(biome,pass))reason="BIOME";
                    else if(y<generator.getSeaLevel()+2||y+GreatTreeShape.MAX_Y>level.getMaxBuildHeight()-8)reason="HEIGHT_OR_WATER";
                    int min=y,max=y,innerMax=y,deepWater=0,canopySamples=0;
                    if(reason==null)for(var offset:SAMPLES){
                        int dx=offset.x,dz=offset.z;var sample=column(x+dx,z+dz);int h=sample.floor(),surface=sample.surface();
                        boolean core=dx*dx+dz*dz<=44*44,inner=Math.abs(dx)<=16&&Math.abs(dz)<=16;
                        if(core){min=Math.min(min,h);max=Math.max(max,h);}if(inner)innerMax=Math.max(innerMax,h);
                        reason=GreatTreeSiteRules.rejection(h,surface,y,min,max,core,inner,generator.getSeaLevel());
                        if(!core){canopySamples++;if(surface-h>8)deepWater++;}
                        if(reason!=null)break;
                    }
                    if(reason==null&&!GreatTreeSiteRules.canopy(deepWater,canopySamples))reason="CANOPY_DEEP_WATER";
                    if(innerMax+GreatTreeShape.MAX_Y>level.getMaxBuildHeight()-8)reason="BUILD_HEIGHT";
                    if(reason!=null){rejected.merge(reason,1,Integer::sum);continue;}
                    var chosen=new GreatTreeSite.Site(new BlockPos(x,innerMax,z),spawn,seed^BlockPos.asLong(x,0,z)^0x115B,GreatTreeShape.VERSION);
                    OriMod.LOGGER.info("Great Spirit Tree search range {} selected {} after {} ms, checked {}, columns {}, rejected {}",
                            searchLimit,chosen.origin(),elapsedMs(),checked,terrain.size(),rejected);
                    return chosen;
                }
            }
            OriMod.LOGGER.info("Great Spirit Tree search range {} finished without site: {} ms, checked {}, columns {}, rejected {}",
                    searchLimit,elapsedMs(),checked,terrain.size(),rejected);
            return null;
        }
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
                    // RegionFile opens CREATE even for a read of an absent chunk. Until the
                    // first write that is a legitimate zero-byte file, not a corrupt old region.
                    if(channel.size()==0)return entries;
                    if(channel.size()<8192)throw new IOException("Truncated region header");
                    var bytes=ByteBuffer.allocate(4096);while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new IOException("Truncated location table");
                    bytes.flip();for(int i=0;i<entries.length;i++)entries[i]=bytes.getInt();
                }catch(IOException exception){Arrays.fill(entries,1);}
                return entries;
            });
            return table[Math.floorMod(cx,32)+Math.floorMod(cz,32)*32]!=0;
        }
    }
}
