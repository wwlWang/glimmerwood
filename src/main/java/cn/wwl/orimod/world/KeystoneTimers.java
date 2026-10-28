package cn.wwl.orimod.world;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Absolute game-time deadlines. Scheduled block ticks survive chunk unload and drive only due pedestals. */
public final class KeystoneTimers extends SavedData {
    public static final String ID="orimod_keystone_timers";
    public static final int COOLDOWN=24000;
    public record Entry(RuinKeystones.Site site,long readyAt){}
    private final Map<Long,Entry> pedestals=new HashMap<>();
    private final Set<String> opened=new HashSet<>();
    public static KeystoneTimers get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(KeystoneTimers::new,KeystoneTimers::load,null),ID);}
    public Entry entry(BlockPos pos){return pedestals.get(pos.asLong());}
    public void taken(BlockPos pos,RuinKeystones.Site site,long now){pedestals.put(pos.asLong(),new Entry(site,now+COOLDOWN));setDirty();}
    public boolean opened(String id){return opened.contains(id);}
    public void open(String id){if(opened.add(id)){pedestals.values().removeIf(e->e.site.id().equals(id));setDirty();}}
    public void remove(BlockPos pos){if(pedestals.remove(pos.asLong())!=null)setDirty();}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();pedestals.forEach((pos,e)->{var t=new CompoundTag();t.putLong("pos",pos);t.putString("site",e.site.id());t.putLong("ready",e.readyAt);t.putLongArray("doors",e.site.doors().stream().mapToLong(BlockPos::asLong).toArray());list.add(t);});
        tag.put("pedestals",list);var done=new ListTag();opened.forEach(id->done.add(StringTag.valueOf(id)));tag.put("opened",done);return tag;
    }
    public static KeystoneTimers load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new KeystoneTimers();
        for(var value:tag.getList("pedestals",Tag.TAG_COMPOUND)){var t=(CompoundTag)value;data.pedestals.put(t.getLong("pos"),new Entry(new RuinKeystones.Site(t.getString("site"),Arrays.stream(t.getLongArray("doors")).mapToObj(BlockPos::of).toList()),t.getLong("ready")));}
        for(var value:tag.getList("opened",Tag.TAG_STRING))data.opened.add(value.getAsString());return data;
    }
}
