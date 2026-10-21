package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.WillWisp;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One ledger per save, stored in the Overworld. At most three pending pickups per player. */
public final class RestorationData extends SavedData {
    public record Claim(UUID owner,WillWisp kind,String dimension,BlockPos source,UUID entity){
        public Claim { source=source.immutable(); }
    }
    private boolean restored;
    private int epoch=1;
    private final Map<UUID,EnumMap<WillWisp,Claim>> pending=new HashMap<>();
    public static RestorationData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RestorationData::new,RestorationData::load,null),"orimod_restoration");}
    public boolean restored(){return restored;}
    public int epoch(){return epoch;}
    public void complete(){if(!restored){restored=true;setDirty();}}
    /** Epoch invalidates offline personal receipts without editing player files. Login removes stale receipts. */
    public void reset(){restored=false;epoch=Math.addExact(epoch,1);setDirty();}
    public Claim claim(UUID owner,WillWisp kind){var map=pending.get(owner);return map==null?null:map.get(kind);}
    public Collection<Claim> claims(){return pending.values().stream().flatMap(m->m.values().stream()).toList();}
    public Claim offer(UUID owner,WillWisp kind,String dimension,BlockPos source){
        var map=pending.computeIfAbsent(owner,k->new EnumMap<>(WillWisp.class));
        if(!map.containsKey(kind)){map.put(kind,new Claim(owner,kind,dimension,source,UUID.randomUUID()));setDirty();}
        return map.get(kind);
    }
    public void remove(UUID owner,WillWisp kind){var map=pending.get(owner);if(map!=null && map.remove(kind)!=null){if(map.isEmpty())pending.remove(owner);setDirty();}}
    public static RestorationData load(CompoundTag tag,HolderLookup.Provider provider){
        var d=new RestorationData();d.restored=tag.getBoolean("restored");d.epoch=Math.max(1,tag.getInt("epoch"));
        for(var value:tag.getList("pending",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)value;var kind=WillWisp.byId(t.getString("kind"));
            if(kind==null || !t.hasUUID("owner") || !t.hasUUID("entity"))continue;
            var c=new Claim(t.getUUID("owner"),kind,t.getString("dimension"),BlockPos.of(t.getLong("source")),t.getUUID("entity"));
            d.pending.computeIfAbsent(c.owner(),k->new EnumMap<>(WillWisp.class)).putIfAbsent(kind,c);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
        tag.putBoolean("restored",restored);tag.putInt("epoch",epoch);var list=new ListTag();
        for(var c:claims()){var t=new CompoundTag();t.putUUID("owner",c.owner());t.putString("kind",c.kind().id());t.putString("dimension",c.dimension());t.putLong("source",c.source().asLong());t.putUUID("entity",c.entity());list.add(t);}
        tag.put("pending",list);return tag;
    }
}
