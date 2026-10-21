package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.block.SpiritTreeHeartBlock;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Generated sites only. Durable chunk journals survive promotion/restart; SavedData is server-thread owned. */
public final class FeatureSites extends SavedData {
    public enum Kind { TREE, BLIGHT }
    public static final int RANGE=2048;
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,"orimod");
    public static final Supplier<AttachmentType<List<Long>>> TREES=journal("generated_trees"), BLIGHTS=journal("generated_blights");
    private static Supplier<AttachmentType<List<Long>>> journal(String name){return TYPES.register(name,()->AttachmentType.<List<Long>>builder(() -> List.of()).serialize(Codec.LONG.listOf()).build());}
    private record Journal(Kind kind,List<Long> positions){}
    private static final Map<ServerLevel,ConcurrentLinkedQueue<Journal>> PENDING=new ConcurrentHashMap<>();
    private final EnumMap<Kind,Set<Long>> sites=new EnumMap<>(Kind.class);
    public FeatureSites(){for(var kind:Kind.values())sites.put(kind,new HashSet<>());}
    public static void register(net.neoforged.bus.api.IEventBus bus){TYPES.register(bus);}
    public static FeatureSites get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(FeatureSites::new,FeatureSites::load,null),"orimod_feature_sites");}
    public static FeatureSites load(CompoundTag tag,HolderLookup.Provider registry){var data=new FeatureSites();for(var kind:Kind.values())for(long p:tag.getLongArray(kind.name()))data.sites.get(kind).add(p);return data;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry){for(var kind:Kind.values())tag.putLongArray(kind.name(),sites.get(kind).stream().mapToLong(Long::longValue).sorted().toArray());return tag;}
    public void add(Kind kind,BlockPos pos){if(sites.get(kind).add(pos.asLong()))setDirty();}
    public int count(Kind kind){return sites.get(kind).size();}
    /** Called only after a successful complete Feature placement, including /place. */
    public static void generated(WorldGenLevel level,BlockPos pos,Kind kind){
        var chunk=level.getChunk(pos);var type=kind==Kind.TREE?TREES:BLIGHTS;
        synchronized(chunk){var values=new HashSet<>(chunk.getData(type));values.add(pos.asLong());chunk.setData(type,List.copyOf(values));chunk.setUnsaved(true);}
        if(level instanceof ServerLevel server)get(server).add(kind,pos);
        else {
            // A crown/core can land in an already-loaded neighbour: its load event has already fired.
            // Queue that successful write too; the durable journal remains the restart recovery path.
            PENDING.computeIfAbsent(level.getLevel(),k->new ConcurrentLinkedQueue<>()).add(new Journal(kind,List.of(pos.asLong())));
        }
    }
    public static void loadChunk(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var chunk=event.getChunk();
        for(var kind:Kind.values()){
            var type=kind==Kind.TREE?TREES:BLIGHTS;
            if(chunk.hasData(type))PENDING.computeIfAbsent(level,k->new ConcurrentLinkedQueue<>()).add(new Journal(kind,List.copyOf(chunk.getData(type))));
        }
    }
    public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)merge(level);}
    public static void merge(ServerLevel level){
        var queue=PENDING.get(level);if(queue==null)return;var data=get(level);
        for(int i=0;i<256;i++){var journal=queue.poll();if(journal==null)break;for(long value:journal.positions){var pos=BlockPos.of(value);if(valid(level,journal.kind,pos))data.add(journal.kind,pos);}}
    }
    private static boolean valid(ServerLevel level,Kind kind,BlockPos pos){
        if(kind==Kind.BLIGHT && BlightData.get(level).completed(pos))return false;
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null || chunk.getBlockState(pos).is(kind==Kind.TREE?OriBlocks.SPIRIT_TREE_HEART:OriBlocks.GLOOM_CORE);
    }
    public Optional<BlockPos> nearest(ServerLevel level,Kind kind,Vec3 origin,Predicate<BlockPos> eligible){
        BlockPos best=null;double limit=(double)RANGE*RANGE;
        var it=sites.get(kind).iterator();
        while(it.hasNext()){
            var pos=BlockPos.of(it.next());double dx=pos.getX()+.5-origin.x,dz=pos.getZ()+.5-origin.z,d=dx*dx+dz*dz;
            if(d>limit)continue;
            if(!valid(level,kind,pos)){it.remove();setDirty();continue;}
            if(eligible.test(pos) && (best==null || d<limit || d==limit && pos.asLong()<best.asLong())){best=pos;limit=d;}
        }
        return Optional.ofNullable(best);
    }
    public Optional<BlockPos> nearest(ServerPlayer p,Kind kind){return nearest(p.serverLevel(),kind,p.position(),pos->kind!=Kind.TREE || !p.getData(SpiritData.PROGRESS).rewards().containsKey(SpiritTreeHeartBlock.siteKey(p.level(),pos)));}
    public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)PENDING.remove(level);}
    public static void clear(){PENDING.clear();}
}
