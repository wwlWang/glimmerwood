package cn.wwl.orimod.world;

import cn.wwl.orimod.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Loaded chunk palettes only. One shared write budget for ALL dimensions, bloom and debug reset. */
public final class RestorationBloom {
    public static final int WRITE_BUDGET=256, READ_BUDGET=32768;
    private static final Map<Key,Boolean> NOTICES=new ConcurrentHashMap<>();
    private static final Map<ServerLevel,Set<Long>> LOADED=new IdentityHashMap<>();
    private static final LinkedHashMap<Key,Cursor> WORK=new LinkedHashMap<>();
    private record Key(ServerLevel level,long chunk){}
    private static final class Cursor{int section,index;}
    private static int lastWrites;
    private static long lastNanos,maxNanos;
    public static int pending(){return WORK.size();}
    public static int lastWrites(){return lastWrites;}
    public static double lastMillis(){return lastNanos/1e6;}
    public static double maxMillis(){return maxNanos/1e6;}
    public static void resetMetrics(){maxNanos=0;}
    public static void load(ChunkEvent.Load e){if(e.getLevel() instanceof ServerLevel l && e.getChunk() instanceof LevelChunk)NOTICES.put(new Key(l,e.getChunk().getPos().toLong()),true);}
    public static void unload(ChunkEvent.Unload e){if(e.getLevel() instanceof ServerLevel l)NOTICES.put(new Key(l,e.getChunk().getPos().toLong()),false);}
    public static void changed(ServerLevel l,BlockPos p){NOTICES.put(new Key(l,ChunkPos.asLong(p.getX()>>4,p.getZ()>>4)),true);}
    private static boolean relevant(BlockState s){return s.is(OriBlocks.SPIRIT_LEAVES)||s.is(OriBlocks.SPIRIT_TREE_HEART)||s.is(OriBlocks.GREAT_TREE_HEART);}
    private static void merge(MinecraftServer server){
        int merged=0;for(var notice:NOTICES.entrySet()){if(merged++>=128)break;var n=notice.getKey();if(n.level.getServer()!=server)continue;NOTICES.remove(n,notice.getValue());
            var set=LOADED.computeIfAbsent(n.level,k->new HashSet<>());var key=new Key(n.level,n.chunk);
            if(!notice.getValue()){set.remove(n.chunk);WORK.remove(key);continue;}
            set.add(n.chunk);
            var chunk=n.level.getChunkSource().getChunkNow(ChunkPos.getX(n.chunk),ChunkPos.getZ(n.chunk));
            if(chunk!=null && Arrays.stream(chunk.getSections()).anyMatch(s->s.maybeHas(RestorationBloom::relevant)))WORK.put(key,new Cursor());
        }
    }
    public static void canopy(ServerLevel level,BlockPos tree){
        for(int x=(tree.getX()-40)>>4;x<=(tree.getX()+40)>>4;x++)for(int z=(tree.getZ()-40)>>4;z<=(tree.getZ()+40)>>4;z++){
            if(level.getChunkSource().getChunkNow(x,z)!=null)WORK.put(new Key(level,ChunkPos.asLong(x,z)),new Cursor());
        }
    }
    public static void refresh(MinecraftServer server){
        merge(server);WORK.clear();
        // No world-sized lookup: only chunks observed as loaded in this server session.
        for(var entry:LOADED.entrySet())if(entry.getKey().getServer()==server)for(long pos:entry.getValue())WORK.put(new Key(entry.getKey(),pos),new Cursor());
    }
    public static void tick(ServerTickEvent.Post e){step(e.getServer());}
    public static void step(MinecraftServer server){
        merge(server);long start=System.nanoTime();int reads=0,writes=0;boolean target=RestorationData.get(server).restored();
        var it=WORK.entrySet().iterator();var pos=new BlockPos.MutableBlockPos();
        outer:while(it.hasNext()){
            var entry=it.next();var key=entry.getKey();var c=entry.getValue();
            var chunk=key.level.getChunkSource().getChunkNow(ChunkPos.getX(key.chunk),ChunkPos.getZ(key.chunk));
            if(chunk==null){it.remove();continue;}
            boolean temporary=cn.wwl.orimod.progression.Restoration.canopyChunk(key.level,chunk.getPos().x,chunk.getPos().z);
            while(c.section<chunk.getSectionsCount()){
                if(System.nanoTime()-start>2_000_000)break outer;
                var section=chunk.getSection(c.section);
                if(c.index==0 && !section.maybeHas(s->(s.is(OriBlocks.SPIRIT_LEAVES)||s.is(OriBlocks.GREAT_TREE_HEART)||target && s.is(OriBlocks.SPIRIT_TREE_HEART)) && (temporary || s.getValue(SpiritLeavesBlock.BLOOMED)!=target))){c.section++;continue;}
                while(c.index<4096){
                    if(writes>=WRITE_BUDGET || reads>=READ_BUDGET || System.nanoTime()-start>2_000_000)break outer;
                    int i=c.index++;reads++;int x=i&15,z=(i>>4)&15,y=i>>8;var state=section.getBlockState(x,y,z);
                    if(!(state.is(OriBlocks.SPIRIT_LEAVES)||state.is(OriBlocks.GREAT_TREE_HEART)||target && state.is(OriBlocks.SPIRIT_TREE_HEART)))continue;
                    pos.set(chunk.getPos().getMinBlockX()+x,chunk.getSectionYFromSectionIndex(c.section)*16+y,chunk.getPos().getMinBlockZ()+z);
                    boolean wanted=target || temporary && cn.wwl.orimod.progression.Restoration.bloomingAt(key.level,pos);
                    if(state.getValue(SpiritLeavesBlock.BLOOMED)==wanted)continue;
                    key.level.setBlock(pos,state.setValue(SpiritLeavesBlock.BLOOMED,wanted),Block.UPDATE_CLIENTS);writes++;
                }
                c.index=0;c.section++;
            }
            it.remove();
        }
        lastWrites=writes;lastNanos=System.nanoTime()-start;maxNanos=Math.max(maxNanos,lastNanos);
    }
    public static void clear(){WORK.clear();LOADED.clear();NOTICES.clear();lastWrites=0;maxNanos=lastNanos=0;}
    private RestorationBloom(){}
}
