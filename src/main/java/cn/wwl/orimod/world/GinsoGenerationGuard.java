package cn.wwl.orimod.world;

import cn.wwl.orimod.OriMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.BiFunction;

/** Prevents a new placement grid from cutting a landmark across an old exploration boundary. */
public final class GinsoGenerationGuard {
    private GinsoGenerationGuard(){}
    private static final Map<RandomState,Runtime> ACTIVE=Collections.synchronizedMap(new IdentityHashMap<>());
    private record Runtime(ChunkMap chunks,Path region,SavedChunks saving){}
    public record Result(ChunkPos blocked,String source,int checked,double millis){public boolean allowed(){return blocked==null;}}

    public static void load(LevelEvent.Load e){
        if(e.getLevel() instanceof ServerLevel level && level.dimension()==Level.OVERWORLD)
            ACTIVE.put(level.getChunkSource().randomState(),new Runtime(level.getChunkSource().chunkMap,
                level.getServer().getWorldPath(LevelResource.ROOT).resolve("region"),new SavedChunks()));
    }
    public static void unload(LevelEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel level)ACTIVE.remove(level.getChunkSource().randomState());
    }
    public static void saving(ChunkDataEvent.Save e){
        if(e.getLevel() instanceof ServerLevel level){
            var runtime=ACTIVE.get(level.getChunkSource().randomState());
            if(runtime!=null)runtime.saving.add(e.getChunk().getPos());
        }
    }
    public static void loaded(ChunkEvent.Load e){
        if(e.getLevel() instanceof ServerLevel level){
            var runtime=ACTIVE.get(level.getChunkSource().randomState());
            if(runtime!=null)runtime.saving.add(e.getChunk().getPos());
        }
    }

    /** No getChunk, generation futures, joins, server-thread work, or mutable region cache. */
    public static Result check(RandomState state,BlockPos origin,int version){
        int r=GinsoShape.radius(version);
        return checkBounds(state,new net.minecraft.world.level.levelgen.structure.BoundingBox(origin.getX()-r,0,origin.getZ()-r,origin.getX()+r,0,origin.getZ()+r));
    }
    /** Shared read-only boundary guard for a versioned ruin's rectangular template footprint. */
    public static Result checkBounds(RandomState state,net.minecraft.world.level.levelgen.structure.BoundingBox bounds){
        long started=System.nanoTime();var runtime=ACTIVE.get(state);
        var origin=new BlockPos(bounds.minX(),0,bounds.minZ());
        if(runtime==null)return new Result(new ChunkPos(origin),"level-unavailable",0,(System.nanoTime()-started)/1e6);
        // Each query owns its header snapshot. Never retain a negative disk result across queries.
        var disk=new GreatTreeSites.RegionHeaders(runtime.region);
        var result=scanBounds(bounds,(x,z)->{
            if(runtime.saving.contains(x,z))return "loaded-or-saving";
            // ChunkMap publishes a volatile cloned visible map; Holder reads AtomicReferenceArray
            // futures using getNow. EMPTY/STRUCTURE_STARTS holders are normal generation work,
            // not old terrain. Once REFERENCES are complete they cannot discover a new start.
            var holder=runtime.chunks.getVisibleChunkIfPresent(ChunkPos.asLong(x,z));
            if(holder!=null && holder.getChunkIfPresentUnchecked(ChunkStatus.STRUCTURE_REFERENCES)!=null)return "live-references";
            return disk.exists(x,z)?"region-header":null;
        });
        return new Result(result.blocked,result.source,result.checked,(System.nanoTime()-started)/1e6);
    }
    static Result scan(BlockPos origin,int radius,BiFunction<Integer,Integer,String> occupied){
        return scanBounds(new net.minecraft.world.level.levelgen.structure.BoundingBox(origin.getX()-radius,0,origin.getZ()-radius,origin.getX()+radius,0,origin.getZ()+radius),occupied);
    }
    static Result scanBounds(net.minecraft.world.level.levelgen.structure.BoundingBox bounds,BiFunction<Integer,Integer,String> occupied){
        int checked=0;
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)
            for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){
                checked++;String reason=occupied.apply(x,z);
                if(reason!=null)return new Result(new ChunkPos(x,z),reason,checked,0);
            }
        return new Result(null,"clear",checked,0);
    }
    public static void report(BlockPos origin,Result result){
        OriMod.LOGGER.info("Ginso footprint {}: {} at {} ({}), {} chunks, {} ms",origin,
            result.allowed()?"OK":"GENERATED",result.blocked,result.source,result.checked,result.millis);
    }

    /** One bit per fully loaded / serialized chunk closes the unload -> asynchronous region-write gap.
     * Sparse region tables cost 128 bytes each, are safe on save/worldgen threads, and die with the level. */
    static final class SavedChunks {
        private final ConcurrentHashMap<Long,AtomicLongArray> regions=new ConcurrentHashMap<>();
        void add(ChunkPos pos){
            var bits=regions.computeIfAbsent(ChunkPos.asLong(pos.x>>5,pos.z>>5),key->new AtomicLongArray(16));
            int bit=(pos.x&31)+(pos.z&31)*32;long mask=1L<<(bit&63);
            bits.getAndUpdate(bit>>6,value->value|mask);
        }
        boolean contains(int x,int z){
            var bits=regions.get(ChunkPos.asLong(x>>5,z>>5));int bit=(x&31)+(z&31)*32;
            return bits!=null && (bits.get(bit>>6)&(1L<<(bit&63)))!=0;
        }
    }
}
