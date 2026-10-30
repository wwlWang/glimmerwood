package cn.wwl.orimod.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Only the new ecology journal. Legacy feature positions/cursors remain in BlightData.Region.
 * Coordinates are partitioned by column; before/after states share one on-disk palette. */
public final class BlightRegion {
    public record Change(BlockPos pos,BlockState before,BlockState expected){
        public Change{pos=pos.immutable();Objects.requireNonNull(before);Objects.requireNonNull(expected);}
        public boolean matches(BlockState current){return expected.equals(current);}
    }
    private final BlockPos center;
    private final Map<Long,BlockPos> columns=new LinkedHashMap<>();
    private final List<BlockPos> orderedColumns=new ArrayList<>();
    private final Map<Long,Change> changes=new LinkedHashMap<>();
    private final List<Change> orderedChanges=new ArrayList<>();
    private final BitSet restored=new BitSet();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet changeChunks=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    public record Protection(long column,BlightProtection.Reason reason,long registeredTick){}
    private final Map<Long,Protection> protectedColumns=new LinkedHashMap<>();
    private final PriorityQueue<Protection> protectionExpiry=new PriorityQueue<>(Comparator.comparingLong(Protection::registeredTick));
    private boolean initialized,legacy,readOnly;
    private int initialColumns,restoreCursor,decorationColumn,decorationY=1,decorationAir;
    private long loadedTicks,randomCursor;
    private double envelope=1;
    private int plantCursor;
    private CompoundTag preserved;
    public BlightRegion(BlockPos center,boolean legacy){this.center=center.immutable();this.legacy=legacy;}
    public BlockPos center(){return center;}
    public long id(){return center.asLong();}
    public boolean legacy(){return legacy;}
    public boolean initialized(){return initialized;}
    public boolean readOnly(){return readOnly;}
    public BlockPos plantFloor(){return orderedColumns.isEmpty()?null:orderedColumns.get(Math.floorMod(plantCursor,orderedColumns.size()));}
    public void nextPlant(){if(!readOnly)plantCursor=orderedColumns.isEmpty()?0:(plantCursor+1)%orderedColumns.size();}
    public Change changeAt(BlockPos pos){return changes.get(pos.asLong());}
    public int initialColumns(){return initialColumns;}
    public BlockPos decorationFloor(){int index=initialColumns+decorationColumn;return index<orderedColumns.size()?orderedColumns.get(index):null;}
    public int decorationY(){return decorationY;}
    public int decoratedColumns(){return decorationColumn;}
    public void finishDecoration(){if(!readOnly){decorationY=1;decorationAir=0;decorationColumn++;}}
    public void nextDecoration(boolean air){if(!readOnly){decorationAir=air?decorationAir+1:0;if(++decorationY>16||decorationAir>4)finishDecoration();}}
    public int grownColumns(){return Math.max(0,columns.size()-initialColumns);}
    public int size(){return changes.size();}
    public int restoreCursor(){return restoreCursor;}
    public void restoreCursor(int value){if(!readOnly){restoreCursor=Math.clamp(value,0,changes.size());restored.set(0,restoreCursor);}}
    public Change change(int index){return orderedChanges.get(index);}
    public boolean restored(int index){return restored.get(index);}
    public int restoredCount(){return restored.cardinality();}
    public it.unimi.dsi.fastutil.longs.LongSet changeChunks(){return it.unimi.dsi.fastutil.longs.LongSets.unmodifiable(changeChunks);}
    public void restoredAt(int index){if(!readOnly){restored.set(index);restoreCursor=restored.nextClearBit(0);}}
    public long loadedTicks(){return loadedTicks;}
    public void loadedTick(){if(!readOnly&&loadedTicks<Long.MAX_VALUE)loadedTicks++;}
    public long nextRandom(){return readOnly?randomCursor:randomCursor++;}
    public Collection<BlockPos> columns(){return Collections.unmodifiableCollection(columns.values());}
    public BlockPos columnAt(int index){return orderedColumns.get(index);}
    public Collection<Change> changes(){return Collections.unmodifiableCollection(changes.values());}
    public Set<Long> protectedColumns(){return Collections.unmodifiableSet(protectedColumns.keySet());}
    public Protection protectionAt(BlockPos pos){return protectedColumns.get(column(pos));}
    public static long column(BlockPos pos){return BlockPos.asLong(pos.getX(),0,pos.getZ());}
    public boolean contains(BlockPos pos){return columns.containsKey(column(pos));}
    public BlockPos floor(BlockPos pos){return columns.get(column(pos));}
    public boolean protectedAt(BlockPos pos){return protectedColumns.containsKey(column(pos));}
    public boolean within(BlockPos pos){return BlightEcologyRules.within(pos.getX()-center.getX(),pos.getZ()-center.getZ(),BlightEcologyRules.MAX_RADIUS);}
    public boolean protect(BlockPos pos){return protect(pos,BlightProtection.Reason.ARTIFICIAL);}
    public boolean protect(BlockPos pos,BlightProtection.Reason reason){
        if(readOnly||!within(pos)||protectedColumns.size()>=BlightEcologyRules.MAX_COLUMNS||protectedAt(pos))return false;
        var entry=new Protection(column(pos),reason,loadedTicks);protectedColumns.put(entry.column(),entry);protectionExpiry.add(entry);return true;
    }
    /** One expired cache entry, then the scheduler re-arms its neighboring frontier under its normal budget. */
    public BlockPos expireProtection(){
        if(readOnly||protectionExpiry.isEmpty()||loadedTicks-protectionExpiry.peek().registeredTick()<BlightEcologyRules.PROTECTION_TICKS)return null;
        var entry=protectionExpiry.remove();protectedColumns.remove(entry.column());return BlockPos.of(entry.column());
    }
    /** Caller supplies actual legacy soil/pool floors after a budgeted, loaded-only scan. */
    public void initialize(Collection<BlockPos> floors){
        if(initialized||readOnly)return;
        for(var pos:floors)if(within(pos)&&columns.size()<BlightEcologyRules.MAX_COLUMNS)columns.putIfAbsent(column(pos),pos.immutable());
        orderedColumns.addAll(columns.values());for(var p:orderedColumns)extendEnvelope(p);initialColumns=columns.size();initialized=true;
    }
    /** Preflight the entire small transaction before recording any position; first ownership cannot be replaced. */
    public boolean canRecord(BlockPos floor,Collection<Change> batch){
        if(!initialized||readOnly||!within(floor)||contains(floor)||protectedAt(floor)||batch.isEmpty()
                ||columns.size()>=BlightEcologyRules.MAX_COLUMNS||changes.size()+batch.size()>BlightEcologyRules.MAX_RECORDS)return false;
        return validBatch(floor,batch);
    }
    private boolean validBatch(BlockPos floor,Collection<Change> batch){return validBatch(floor,batch,false);}
    private boolean validBatch(BlockPos floor,Collection<Change> batch,boolean replant){
        var seen=new HashSet<Long>();
        for(var c:batch)if(column(c.pos)!=column(floor)||!within(c.pos)||c.before.hasBlockEntity()||c.expected.hasBlockEntity()
                ||c.before.equals(c.expected)||changes.containsKey(c.pos.asLong())&&(!replant||!BlightConversion.removableCover(c.before)||!changes.get(c.pos.asLong()).expected.equals(c.expected))||!seen.add(c.pos.asLong()))return false;
        return true;
    }
    public boolean record(BlockPos floor,Collection<Change> batch){
        if(!canRecord(floor,batch))return false;
        columns.put(column(floor),floor.immutable());orderedColumns.add(floor.immutable());extendEnvelope(floor);append(batch);return true;
    }
    public boolean decorate(BlockPos floor,Collection<Change> batch){
        if(readOnly||!contains(floor)||batch.isEmpty()||changes.size()+batch.size()>BlightEcologyRules.MAX_RECORDS||!validBatch(floor,batch,true))return false;
        append(batch.stream().filter(c->!changes.containsKey(c.pos.asLong())).toList());return true;
    }
    private void append(Collection<Change> batch){for(var c:batch){changes.put(c.pos.asLong(),c);orderedChanges.add(c);changeChunks.add(net.minecraft.world.level.ChunkPos.asLong(c.pos.getX()>>4,c.pos.getZ()>>4));}}
    private void extendEnvelope(BlockPos p){envelope=Math.max(envelope,Math.hypot(p.getX()-center.getX(),p.getZ()-center.getZ()));}
    public double radius(){return envelope;}
    public CompoundTag save(){
        if(preserved!=null)return preserved.copy();
        var tag=new CompoundTag();tag.putInt("version",BlightEcologyRules.VERSION);tag.putBoolean("legacy",legacy);tag.putBoolean("initialized",initialized);
        tag.putBoolean("read_only",readOnly);tag.putInt("initial_columns",initialColumns);tag.putInt("restore_cursor",restoreCursor);
        if(!restored.isEmpty())tag.putLongArray("restored",restored.toLongArray());
        tag.putLong("loaded_ticks",loadedTicks);tag.putLong("random_cursor",randomCursor);
        tag.putInt("plant_cursor",plantCursor);
        tag.putInt("decoration_column",decorationColumn);tag.putInt("decoration_y",decorationY);tag.putInt("decoration_air",decorationAir);
        tag.putLongArray("columns",columns.values().stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("protectionVersion",BlightEcologyRules.PROTECTION_VERSION);
        var protections=new ListTag();for(var p:protectedColumns.values()){
            var t=new CompoundTag();t.putLong("column",p.column());t.putString("reason",p.reason().name());t.putLong("registeredTick",p.registeredTick());protections.add(t);
        }tag.put("protections",protections);
        var palette=new LinkedHashMap<BlockState,Integer>();var entries=new ListTag();
        for(var c:changes.values()){
            var t=new CompoundTag();t.putLong("pos",c.pos.asLong());t.putInt("before",palette.computeIfAbsent(c.before,k->palette.size()));
            t.putInt("expected",palette.computeIfAbsent(c.expected,k->palette.size()));entries.add(t);
        }
        var states=new ListTag();for(var state:palette.keySet())states.add(NbtUtils.writeBlockState(state));
        tag.put("palette",states);tag.put("changes",entries);return tag;
    }
    public static BlightRegion load(BlockPos center,CompoundTag tag,HolderLookup.Provider registries){
        var r=new BlightRegion(center,tag.getBoolean("legacy"));r.initialized=tag.getBoolean("initialized");
        r.readOnly=tag.getInt("version")!=BlightEcologyRules.VERSION||tag.getBoolean("read_only");
        r.loadedTicks=Math.max(0,tag.getLong("loaded_ticks"));r.randomCursor=tag.getLong("random_cursor");
        for(long p:tag.getLongArray("columns")){var at=BlockPos.of(p);if(r.within(at)&&r.columns.size()<BlightEcologyRules.MAX_COLUMNS)r.columns.putIfAbsent(column(at),at);else r.readOnly=true;}
        // Pre-release coordinate-only entries have no reason: discard the cache and inspect again.
        // With the versioned format, retain artificial/heart/etc. entries across structure-rule changes.
        for(var value:tag.getList("protections",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)value;BlightProtection.Reason reason;
            try{reason=BlightProtection.Reason.valueOf(t.getString("reason"));}catch(IllegalArgumentException ignored){continue;}
            if(reason==BlightProtection.Reason.STRUCTURE&&tag.getInt("protectionVersion")!=BlightEcologyRules.PROTECTION_VERSION)continue;
            long packed=t.getLong("column"),registered=Math.clamp(t.getLong("registeredTick"),0,r.loadedTicks);
            if(r.loadedTicks-registered>=BlightEcologyRules.PROTECTION_TICKS)continue;
            var at=BlockPos.of(packed);if(!r.within(at)||r.protectedColumns.size()>=BlightEcologyRules.MAX_COLUMNS){r.readOnly=true;continue;}
            var entry=new Protection(column(at),reason,registered);if(r.protectedColumns.putIfAbsent(entry.column(),entry)==null)r.protectionExpiry.add(entry);
        }
        r.initialColumns=Math.clamp(tag.getInt("initial_columns"),0,r.columns.size());
        r.orderedColumns.addAll(r.columns.values());r.decorationColumn=Math.clamp(tag.getInt("decoration_column"),0,r.grownColumns());r.decorationY=Math.clamp(tag.contains("decoration_y")?tag.getInt("decoration_y"):1,1,16);
        r.plantCursor=Math.max(0,tag.getInt("plant_cursor"));
        r.decorationAir=Math.clamp(tag.getInt("decoration_air"),0,4);
        var states=new ArrayList<BlockState>();
        var lookup=registries==null?net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup():registries.lookupOrThrow(Registries.BLOCK);
        for(var t:tag.getList("palette",Tag.TAG_COMPOUND)){
            // Unknown registry names must not silently turn into AIR and become destructive restore instructions.
            var encoded=(CompoundTag)t;var id=net.minecraft.resources.ResourceLocation.tryParse(encoded.getString("Name"));
            if(id==null||lookup.get(net.minecraft.resources.ResourceKey.create(Registries.BLOCK,id)).isEmpty()){states.add(null);r.readOnly=true;}
            else {
                var state=NbtUtils.readBlockState(lookup,encoded);
                // Unknown property names/values also must not silently become a default state.
                if(!NbtUtils.writeBlockState(state).equals(encoded)){states.add(null);r.readOnly=true;}
                else states.add(state);
            }
        }
        for(var t:tag.getList("changes",Tag.TAG_COMPOUND)){
            var e=(CompoundTag)t;int a=e.getInt("before"),b=e.getInt("expected");var p=BlockPos.of(e.getLong("pos"));
            if(a<0||b<0||a>=states.size()||b>=states.size()||states.get(a)==null||states.get(b)==null||!r.within(p)
                    ||!r.contains(p)||states.get(a).equals(states.get(b))||states.get(a).hasBlockEntity()||states.get(b).hasBlockEntity()
                    ||r.changes.size()>=BlightEcologyRules.MAX_RECORDS){r.readOnly=true;continue;}
            if(r.changes.putIfAbsent(p.asLong(),new Change(p,states.get(a),states.get(b)))!=null)r.readOnly=true;
        }
        r.restoreCursor=Math.clamp(tag.getInt("restore_cursor"),0,r.changes.size());
        r.orderedChanges.addAll(r.changes.values());r.restored.or(BitSet.valueOf(tag.getLongArray("restored")));r.restored.set(0,r.restoreCursor);
        for(var c:r.orderedChanges)r.changeChunks.add(net.minecraft.world.level.ChunkPos.asLong(c.pos.getX()>>4,c.pos.getZ()>>4));
        if(r.restored.length()>r.size()){r.readOnly=true;r.restored.clear(r.size(),r.restored.length());}
        for(var p:r.orderedColumns)r.extendEnvelope(p);
        if(r.readOnly)r.preserved=tag.copy();
        return r;
    }
}
