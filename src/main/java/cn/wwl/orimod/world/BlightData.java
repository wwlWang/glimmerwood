package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.entity.OriMobs;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.nio.file.Files;
import java.util.*;

/** Per-dimension ledger. No chunk tickets, static world references or per-block tickers.
 * Unloaded partitions retain their cursor; completed regions retain only their centre.
 * Pending recipients live separately and are erased immediately on delivery/deleted player data. */
public final class BlightData extends SavedData {
    private static final String DEFENDER="orimod_blight_defender";
    public static final class Partition {
        final long[] positions; int cursor;
        Partition(long[] positions){this.positions=positions;}
    }
    public static final class Region {
        final BlockPos center; final Map<Long,Partition> chunks=new LinkedHashMap<>();
        final Set<UUID> guards=new HashSet<>(); final List<UUID> participants=new ArrayList<>();
        boolean purifying; int elapsed; double radius=1; long nextDefender;
        Region(BlockPos center,long[] positions){
            this.center=center.immutable();var groups=new LinkedHashMap<Long,List<Long>>();
            for(long value:Arrays.stream(positions).distinct().toArray()){
                var p=BlockPos.of(value);radius=Math.max(radius,Math.sqrt(radialSquared(p,center)));
                groups.computeIfAbsent(chunk(p),k->new ArrayList<>()).add(value);
            }
            groups.forEach((key,list)->{list.sort(Comparator.<Long>comparingDouble(v->radialSquared(BlockPos.of(v),center)).thenComparingInt(v->BlockPos.of(v).getY()));chunks.put(key,new Partition(list.stream().mapToLong(Long::longValue).toArray()));});
        }
        int total(){return chunks.values().stream().mapToInt(p->p.positions.length).sum();}
        int processed(){return chunks.values().stream().mapToInt(p->p.cursor).sum();}
    }
    private final Map<Long,Region> regions=new LinkedHashMap<>();
    private final Set<Long> waves=new LinkedHashSet<>();
    private final Set<Long> completed=new HashSet<>();
    private final Map<Long,Set<Long>> completedChunks=new HashMap<>();
    private final Map<Long,List<UUID>> pending=new HashMap<>();
    private int rotation;
    public int lastReads,lastWrites;
    public static BlightData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(BlightData::new,BlightData::load,null),"orimod_blight");}
    private static double radialSquared(BlockPos a,BlockPos b){double x=(double)a.getX()-b.getX(),z=(double)a.getZ()-b.getZ();return x*x+z*z;}
    private static long chunk(BlockPos p){return ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);}
    public void register(BlockPos center,long[] positions){
        if(positions.length==0 || completed.contains(center.asLong()) || regions.containsKey(center.asLong()))return;
        regions.put(center.asLong(),new Region(center,positions));setDirty();
    }
    public void removeCore(BlockPos center){var r=regions.get(center.asLong());if(r!=null && !r.purifying){regions.remove(center.asLong());setDirty();}}
    public boolean begin(ServerLevel level,BlockPos center){
        var r=regions.get(center.asLong());if(r==null || r.purifying || completed.contains(center.asLong()))return false;
        r.purifying=true;waves.add(center.asLong());r.guards.clear();
        level.players().stream().filter(p->!p.isSpectator() && p.isAlive() && p.distanceToSqr(Vec3.atCenterOf(center))<=32*32)
                .sorted(Comparator.comparingDouble(p->p.distanceToSqr(Vec3.atCenterOf(center)))).limit(BlightRules.MAX_PARTICIPANTS).forEach(p->r.participants.add(p.getUUID()));
        setDirty();return true;
    }
    public static BlockState restored(BlockState state,long salt){
        if(state.is(OriBlocks.BLIGHTED_SOIL))return Blocks.GRASS_BLOCK.defaultBlockState();
        if(state.is(OriBlocks.WITHERED_SPIRIT_LOG))return OriBlocks.SPIRIT_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,state.getValue(RotatedPillarBlock.AXIS));
        if(state.is(OriBlocks.GLOOM_WATER))return Blocks.WATER.defaultBlockState();
        if(state.is(OriBlocks.GLOOM_THORNS))return switch(Math.floorMod(Long.hashCode(salt),4)){
            case 0->Blocks.SHORT_GRASS.defaultBlockState();case 1->Blocks.AZURE_BLUET.defaultBlockState();case 2->Blocks.OXEYE_DAISY.defaultBlockState();default->Blocks.AIR.defaultBlockState();};
        return state;
    }
    public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)get(level).advance(level);}
    public void advance(ServerLevel level){
        lastReads=0;lastWrites=0;
        var work=new ArrayList<>(waves.stream().map(regions::get).toList());
        if(work.isEmpty())return;
        Collections.rotate(work,Math.floorMod(rotation++,work.size()));
        for(var r:work){
            if(lastReads>=BlightRules.READ_BUDGET || lastWrites>=BlightRules.WRITE_BUDGET)break;
            boolean loaded=r.chunks.entrySet().stream().anyMatch(e->e.getValue().cursor<e.getValue().positions.length && loaded(level,e.getKey()));
            if(!loaded && r.processed()<r.total())continue;
            r.elapsed=Math.min(BlightRules.DURATION,r.elapsed+1);setDirty();
            for(var entry:r.chunks.entrySet()){
                if(!loaded(level,entry.getKey()))continue;
                var part=entry.getValue();
                while(part.cursor<part.positions.length && lastReads<BlightRules.READ_BUDGET && lastWrites<BlightRules.WRITE_BUDGET){
                    var pos=BlockPos.of(part.positions[part.cursor]);
                    if(BlightRules.due(Math.sqrt(radialSquared(pos,r.center)),r.radius)>r.elapsed)break;
                    var state=level.getBlockState(pos);lastReads++;
                    var replacement=restored(state,pos.asLong());
                    if(!replacement.equals(state)){
                        if(!replacement.canSurvive(level,pos))replacement=Blocks.AIR.defaultBlockState();
                        level.setBlock(pos,replacement,Block.UPDATE_CLIENTS);lastWrites++;
                        level.sendParticles(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(),0xD5F4FF,.18f,24),pos.getX()+.5,pos.getY()+1.05,pos.getZ()+.5,2,.3,.05,.3,.025);
                    }
                    part.cursor++;
                }
            }
            if(r.elapsed>=BlightRules.DURATION && r.processed()==r.total())finish(level,r);
        }
    }
    private static boolean loaded(ServerLevel level,long key){return level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key))!=null;}
    private void markComplete(long center){completed.add(center);completedChunks.computeIfAbsent(chunk(BlockPos.of(center)),k->new HashSet<>()).add(center);}
    private void finish(ServerLevel level,Region r){
        long key=r.center.asLong();regions.remove(key);waves.remove(key);markComplete(key);
        if(!r.participants.isEmpty())pending.put(key,new ArrayList<>(r.participants));
        setDirty();deliver(level,null);
        SpiritFx.emit(level,SpiritFxKind.PURIFY,Vec3.atCenterOf(r.center),null);
    }
    /** Spatial lookup remains cheap even after many completed regions. */
    public boolean protects(Vec3 point){
        int x=(int)Math.floor(point.x),z=(int)Math.floor(point.z),r=BlightRules.SANCTUARY_RADIUS;
        for(int cx=(x-r)>>4;cx<=(x+r)>>4;cx++)for(int cz=(z-r)>>4;cz<=(z+r)>>4;cz++)
            for(long value:completedChunks.getOrDefault(ChunkPos.asLong(cx,cz),Set.of()))if(BlightRules.protects(BlockPos.of(value).distToCenterSqr(point)))return true;
        return false;
    }
    public boolean completed(BlockPos center){return completed.contains(center.asLong());}
    public int pendingCount(){return pending.values().stream().mapToInt(List::size).sum();}
    private void deliver(ServerLevel level,ServerPlayer joining){
        var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();var recipients=entry.getValue().iterator();
            while(recipients.hasNext()){
                var uuid=recipients.next();var player=level.getServer().getPlayerList().getPlayer(uuid);
                if(player==null){
                    if(!Files.isRegularFile(level.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid+".dat"))){recipients.remove();setDirty();}
                    continue;
                }
                if(joining!=null && !uuid.equals(joining.getUUID()))continue;
                String key="purify/"+level.dimension().location()+"/"+entry.getKey();var progress=player.getData(SpiritData.PROGRESS);
                if(!progress.rewards().containsKey(key)){
                    var rewards=new HashMap<>(progress.rewards());rewards.put(key,1);
                    player.setData(SpiritData.PROGRESS,progress.withRewards(rewards));SpiritProgression.grantLight(player,BlightRules.REWARD);
                    CompanionLines.offer(player,CompanionLines.Line.FIRST_PURIFY);
                }
                recipients.remove();setDirty();
            }
            if(entry.getValue().isEmpty())iterator.remove();
        }
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)for(var level:p.server.getAllLevels())get(level).deliver(level,p);}
    public void defend(ServerLevel level,BlockPos center,ServerPlayer target){
        var r=regions.get(center.asLong());if(r==null || r.purifying || level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL || !BlightRules.defenderReady(level.getGameTime(),r.nextDefender,r.guards.size()))return;
        r.nextDefender=level.getGameTime()+BlightRules.DEFENDER_COOLDOWN;setDirty();
        for(int attempt=0;attempt<12;attempt++){
            double angle=level.random.nextDouble()*Math.PI*2,distance=4+level.random.nextDouble()*7;
            int x=center.getX()+(int)Math.round(Math.cos(angle)*distance),z=center.getZ()+(int)Math.round(Math.sin(angle)*distance);
            if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)continue;
            var pos=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
            if(pos.distSqr(center)>144 || !GloomTide.validGround(level,pos))continue;
            var mob=OriMobs.GLOOM_SPITTER.get().create(level);if(mob==null)return;
            mob.moveTo(x+.5,pos.getY(),z+.5,0,0);
            if(!level.noCollision(mob)){mob.discard();continue;}
            mob.getPersistentData().putLong(DEFENDER,center.asLong());
            EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);
            if(level.addFreshEntity(mob)){r.guards.add(mob.getUUID());mob.setTarget(target);setDirty();return;}
            mob.discard();
        }
    }
    public static void leave(EntityLeaveLevelEvent event){
        var e=event.getEntity();var reason=e.getRemovalReason();
        if(event.getLevel() instanceof ServerLevel level && reason!=null && (reason.shouldDestroy() || reason==net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) && e.getPersistentData().contains(DEFENDER)){
            var data=get(level);var r=data.regions.get(e.getPersistentData().getLong(DEFENDER));if(r!=null && r.guards.remove(e.getUUID()))data.setDirty();
        }
    }
    public Map<String,Object> status(Vec3 point){
        Long best=null;double distance=256*256;
        var keys=new HashSet<>(regions.keySet());keys.addAll(completed);
        for(long key:keys){double d=BlockPos.of(key).distToCenterSqr(point);if(d<distance){best=key;distance=d;}}
        if(best==null)return Map.of("state","none","pendingRewards",pendingCount());
        var p=BlockPos.of(best);var r=regions.get(best);var result=new LinkedHashMap<String,Object>();
        result.put("center",List.of(p.getX(),p.getY(),p.getZ()));result.put("state",r==null?"completed":r.purifying?"purifying":"active");
        result.put("elapsed",r==null?600:r.elapsed);result.put("processed",r==null?0:r.processed());result.put("total",r==null?0:r.total());
        result.put("participants",r==null?0:r.participants.size());result.put("defenders",r==null?0:r.guards.size());result.put("pendingRewards",pendingCount());
        result.put("lastReads",lastReads);result.put("lastWrites",lastWrites);return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();
        for(var r:regions.values()){
            var e=new CompoundTag();e.putLong("center",r.center.asLong());e.putBoolean("purifying",r.purifying);e.putInt("elapsed",r.elapsed);e.putLong("next_defender",r.nextDefender);
            var chunks=new ListTag();r.chunks.forEach((key,p)->{var c=new CompoundTag();c.putLong("chunk",key);c.putLongArray("positions",p.positions);c.putInt("cursor",p.cursor);chunks.add(c);});e.put("chunks",chunks);
            e.put("participants",uuids(r.participants));e.put("guards",uuids(r.guards));list.add(e);
        }
        tag.put("regions",list);tag.putLongArray("completed",completed.stream().mapToLong(Long::longValue).toArray());
        var rewards=new ListTag();pending.forEach((key,players)->{var e=new CompoundTag();e.putLong("center",key);e.put("players",uuids(players));rewards.add(e);});tag.put("pending",rewards);return tag;
    }
    private static ListTag uuids(Collection<UUID> ids){var list=new ListTag();for(var id:ids)list.add(NbtUtils.createUUID(id));return list;}
    private static List<UUID> readIds(ListTag list,int limit){var ids=new ArrayList<UUID>();for(var value:list){if(ids.size()>=limit)break;ids.add(NbtUtils.loadUUID(value));}return ids;}
    public static BlightData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new BlightData();for(long center:tag.getLongArray("completed"))data.markComplete(center);
        for(var value:tag.getList("regions",Tag.TAG_COMPOUND)){
            var e=(CompoundTag)value;long center=e.getLong("center");if(data.completed.contains(center))continue;
            var chunks=e.getList("chunks",Tag.TAG_COMPOUND);var all=new ArrayList<Long>();
            for(var v:chunks)for(long p:((CompoundTag)v).getLongArray("positions"))all.add(p);
            var r=new Region(BlockPos.of(center),all.stream().mapToLong(Long::longValue).toArray());
            r.purifying=e.getBoolean("purifying");r.elapsed=Math.max(0,Math.min(600,e.getInt("elapsed")));r.nextDefender=e.getLong("next_defender");
            for(var v:chunks){var c=(CompoundTag)v;var p=r.chunks.get(c.getLong("chunk"));if(p!=null)p.cursor=Math.max(0,Math.min(p.positions.length,c.getInt("cursor")));}
            r.participants.addAll(readIds(e.getList("participants",Tag.TAG_INT_ARRAY),16));r.guards.addAll(readIds(e.getList("guards",Tag.TAG_INT_ARRAY),3));data.regions.put(center,r);if(r.purifying)data.waves.add(center);
        }
        for(var value:tag.getList("pending",Tag.TAG_COMPOUND)){var e=(CompoundTag)value;var ids=readIds(e.getList("players",Tag.TAG_INT_ARRAY),16);if(!ids.isEmpty())data.pending.put(e.getLong("center"),ids);}
        return data;
    }
}
