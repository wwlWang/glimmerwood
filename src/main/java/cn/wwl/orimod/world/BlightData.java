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
    static final String DEFENDER="orimod_blight_defender";
    public static final class Partition {
        final long[] positions; int cursor;
        Partition(long[] positions){this.positions=positions;}
    }
    public static final class Region {
        final BlockPos center; final Map<Long,Partition> chunks=new LinkedHashMap<>();
        final Set<UUID> animals=new HashSet<>();
        final Set<UUID> guards=new HashSet<>(); final List<UUID> participants=new ArrayList<>();
        final Set<UUID> extraMobs=new HashSet<>();
        boolean purifying; int elapsed; double radius=1; long nextDefender;
        boolean naturalEcology;
        boolean rewardEligible = true;
        BlightRegion ecology;
        int duration=600,reward=60,ecologyScan;
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
    private final Map<Long,Set<Long>> coreChunks=new HashMap<>();
    private long atmosphereRevision;
    private final Set<Long> waves=new LinkedHashSet<>();
    private final Set<Long> completed=new HashSet<>();
    private final Map<Long,Set<Long>> completedChunks=new HashMap<>();
    private final Map<Long,List<UUID>> pending=new HashMap<>();
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap rewardAmounts=new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet purifiedColumns=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap columnOwners=new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap(),
            blockOwners=new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private int rotation;
    private int ecologyRecords;
    private int extraMobs;
    public int extraMobCount(){return extraMobs;}
    void extraAdded(){extraMobs++;setDirty();}
    public int lastReads,lastWrites;
    public static BlightData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(BlightData::new,BlightData::load,null),"orimod_blight");}
    private static double radialSquared(BlockPos a,BlockPos b){double x=(double)a.getX()-b.getX(),z=(double)a.getZ()-b.getZ();return x*x+z*z;}
    private static long chunk(BlockPos p){return ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);}
    public void register(BlockPos center,long[] positions){
        register(center,positions,false);
    }
    public void register(BlockPos center,long[] positions,boolean naturalEcology){
        register(center,positions,naturalEcology,true);
    }
    public void register(BlockPos center,long[] positions,boolean naturalEcology,boolean rewardEligible){
        if(positions.length==0 || completed.contains(center.asLong()) || regions.containsKey(center.asLong()))return;
        var r=new Region(center,positions);r.naturalEcology=naturalEcology;r.rewardEligible=rewardEligible;regions.put(center.asLong(),r);indexCore(r);setDirty();
    }
    private void indexCore(Region r){coreChunks.computeIfAbsent(chunk(r.center),k->new HashSet<>()).add(r.center.asLong());atmosphereRevision++;}
    private void removeCoreIndex(Region r){var set=coreChunks.get(chunk(r.center));if(set!=null){set.remove(r.center.asLong());if(set.isEmpty())coreChunks.remove(chunk(r.center));}atmosphereRevision++;}
    public long atmosphereRevision(){return atmosphereRevision;}
    public List<cn.wwl.orimod.network.BlightAtmospherePayload.Core> nearbySummaries(Vec3 point){
        var candidates=new ArrayList<Region>();int x=(int)Math.floor(point.x),z=(int)Math.floor(point.z);
        for(int cx=(x-96)>>4;cx<=(x+96)>>4;cx++)for(int cz=(z-96)>>4;cz<=(z+96)>>4;cz++)
            for(long id:coreChunks.getOrDefault(ChunkPos.asLong(cx,cz),Set.of())){
                var r=regions.get(id);if(r.ecology==null||r.ecology.readOnly())continue;
                double radius=Math.max(r.radius,r.ecology.radius());if(Math.hypot(point.x-r.center.getX(),point.z-r.center.getZ())<=radius+16)candidates.add(r);
            }
        var occupied=activeAt(point);
        candidates.sort(Comparator.<Region>comparingInt(r->occupied.isPresent()&&occupied.get().equals(r.center)?0:1)
                .thenComparingDouble(r->r.center.distToCenterSqr(point)).thenComparingLong(r->r.center.asLong()));
        return candidates.stream().limit(4).map(r->new cn.wwl.orimod.network.BlightAtmospherePayload.Core(r.center.asLong(),(float)Math.min(96,Math.max(r.radius,r.ecology.radius())),r.purifying,r.purifying?Math.clamp(r.elapsed/(float)r.duration,0,1):0)).toList();
    }
    public BlightRegion ecology(BlockPos center){var r=regions.get(center.asLong());return r==null?null:r.ecology;}
    Region region(BlockPos center){return regions.get(center.asLong());}
    Collection<Region> regions(){return Collections.unmodifiableCollection(regions.values());}
    public int ecologyRecords(){return ecologyRecords;}
    /** No world reads here. Caller supplies only verified initial floors, with its own read budget. */
    public boolean initializeEcology(BlockPos center,Collection<BlockPos> floors,boolean enableLegacy){
        var r=regions.get(center.asLong());
        if(r==null||r.purifying||r.ecology!=null||!r.naturalEcology&&!enableLegacy)return false;
        r.ecology=new BlightRegion(center,!r.naturalEcology);
        r.ecology.initialize(floors.stream().filter(p->!columnOwners.containsKey(BlightRegion.column(p))&&!purifiedColumns.contains(BlightRegion.column(p))).toList());
        index(r);setDirty();return true;
    }
    public boolean recordGrowth(BlockPos center,BlockPos floor,Collection<BlightRegion.Change> changes){
        var r=regions.get(center.asLong());
        if(r==null||r.purifying||r.ecology==null||purifiedColumns.contains(BlightRegion.column(floor))||columnOwners.containsKey(BlightRegion.column(floor))
                ||changes.stream().anyMatch(c->blockOwners.containsKey(c.pos().asLong()))||!r.ecology.record(floor,changes))return false;
        columnOwners.put(BlightRegion.column(floor),center.asLong());
        for(var c:changes)blockOwners.put(c.pos().asLong(),center.asLong());ecologyRecords+=changes.size();setDirty();return true;
    }
    public boolean recordDecoration(BlockPos center,BlockPos floor,Collection<BlightRegion.Change> changes){
        var r=regions.get(center.asLong());
        if(r==null||r.purifying||r.ecology==null||changes.stream().anyMatch(c->blockOwners.containsKey(c.pos().asLong())&&blockOwners.get(c.pos().asLong())!=center.asLong()))return false;
        int before=r.ecology.size();if(!r.ecology.decorate(floor,changes))return false;
        for(var c:changes)blockOwners.put(c.pos().asLong(),center.asLong());ecologyRecords+=r.ecology.size()-before;setDirty();return true;
    }
    public boolean protectColumn(BlockPos center,BlockPos floor){
        return protectColumn(center,floor,BlightProtection.Reason.ARTIFICIAL);
    }
    public boolean protectColumn(BlockPos center,BlockPos floor,BlightProtection.Reason reason){
        var r=regions.get(center.asLong());if(r==null||r.ecology==null||!r.ecology.protect(floor,reason))return false;setDirty();return true;
    }
    public Optional<BlockPos> columnOwner(BlockPos pos){long key=BlightRegion.column(pos);return columnOwners.containsKey(key)?Optional.of(BlockPos.of(columnOwners.get(key))):Optional.empty();}
    /** Exact occupied column and limited vertical band, never the client visual envelope. */
    public Optional<BlockPos> activeAt(Vec3 point){
        var pos=BlockPos.containing(point);var owner=columnOwner(pos);if(owner.isEmpty()||ecologyProtects(pos))return Optional.empty();
        var r=regions.get(owner.get().asLong());if(r==null||r.purifying||r.ecology==null||r.ecology.readOnly()||r.ecology.protectedAt(pos))return Optional.empty();
        var floor=r.ecology.floor(pos);return floor!=null&&point.y>=floor.getY()-2&&point.y<=floor.getY()+8?owner:Optional.empty();
    }
    public boolean activeCore(BlockPos center){var r=regions.get(center.asLong());return r!=null&&!r.purifying&&r.ecology!=null&&!r.ecology.readOnly();}
    public boolean claimAnimal(BlockPos center,UUID id,int cap){
        var r=regions.get(center.asLong());if(r==null||r.purifying)return false;
        if(r.animals.contains(id))return true;if(r.animals.size()>=cap)return false;r.animals.add(id);setDirty();return true;
    }
    public void releaseAnimal(BlockPos center,UUID id){var r=regions.get(center.asLong());if(r!=null&&r.animals.remove(id))setDirty();}
    public int animalCount(BlockPos center){var r=regions.get(center.asLong());return r==null?0:r.animals.size();}
    public boolean animalRestoreDue(BlockPos center,double distance){
        var r=regions.get(center.asLong());return r==null||r.purifying&&r.elapsed>=Math.max(1,Math.min(r.duration,(int)Math.ceil(r.duration*distance/Math.max(1,r.radius))));
    }
    private void index(Region r){if(r.ecology!=null){
        ecologyRecords+=r.ecology.size();
        for(var p:r.ecology.columns())columnOwners.putIfAbsent(BlightRegion.column(p),r.center.asLong());
        for(var c:r.ecology.changes())blockOwners.putIfAbsent(c.pos().asLong(),r.center.asLong());
    }}
    private void unindex(Region r){if(r.ecology!=null){
        ecologyRecords-=r.ecology.size();
        for(var p:r.ecology.columns())columnOwners.remove(BlightRegion.column(p),r.center.asLong());
        for(var c:r.ecology.changes())blockOwners.remove(c.pos().asLong(),r.center.asLong());
    }}
    public void removeCore(BlockPos center){var r=regions.get(center.asLong());if(r!=null && !r.purifying){unindex(r);removeCoreIndex(r);regions.remove(center.asLong());setDirty();}}
    public void removed(ServerLevel level,BlockPos center){
        var r=regions.get(center.asLong());
        if(r!=null&&r.ecology!=null&&!r.purifying){begin(level,center);r.participants.clear();setDirty();}
        else removeCore(center);
    }
    public boolean begin(ServerLevel level,BlockPos center){
        var r=regions.get(center.asLong());if(r==null || r.purifying || completed.contains(center.asLong()))return false;
        r.purifying=true;waves.add(center.asLong());BlightMobs.purify(level,r);extraMobs-=r.extraMobs.size();r.extraMobs.clear();r.guards.clear();atmosphereRevision++;
        if(r.ecology!=null){
            r.radius=Math.max(r.radius,r.ecology.radius());r.duration=BlightEcologyRules.duration(r.radius);r.reward=BlightEcologyRules.reward(r.ecology.grownColumns());
            for(var p:r.ecology.columns())purifiedColumns.add(BlightRegion.column(p));
        }
        double capture=r.ecology==null?32:Math.min(64,r.radius+8);
        level.players().stream().filter(p->!p.isSpectator() && p.isAlive() && p.distanceToSqr(Vec3.atCenterOf(center))<=capture*capture)
                .sorted(Comparator.comparingDouble(p->p.distanceToSqr(Vec3.atCenterOf(center)))).limit(BlightRules.MAX_PARTICIPANTS).forEach(p->r.participants.add(p.getUUID()));
        BlightEcology.wake(level,center);setDirty();return true;
    }
    public static BlockState restored(BlockState state,long salt){
        if(state.is(OriBlocks.BLIGHTED_SOIL))return Blocks.GRASS_BLOCK.defaultBlockState();
        if(state.is(OriBlocks.WITHERED_SPIRIT_LOG))return OriBlocks.SPIRIT_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,state.getValue(RotatedPillarBlock.AXIS));
        if(state.is(OriBlocks.GLOOM_WATER))return Blocks.WATER.defaultBlockState();
        if(state.is(OriBlocks.GLOOM_THORNS))return switch(Math.floorMod(Long.hashCode(salt),4)){
            case 0->Blocks.SHORT_GRASS.defaultBlockState();case 1->Blocks.AZURE_BLUET.defaultBlockState();case 2->Blocks.OXEYE_DAISY.defaultBlockState();default->Blocks.AIR.defaultBlockState();};
        return state;
    }
    /** Compatibility entry point for local wave tests; production uses the shared server scheduler. */
    public void advance(ServerLevel level){
        lastReads=0;lastWrites=0;var budget=new BlightEcologyRules.Budget(BlightRules.READ_BUDGET,BlightRules.WRITE_BUDGET);
        var work=new ArrayList<>(waves.stream().map(regions::get).toList());
        if(!work.isEmpty())Collections.rotate(work,Math.floorMod(rotation++,work.size()));
        for(var r:work){if(budget.remainingReads()==0||budget.remainingWrites()==0)break;restore(level,r,budget,32);}
        lastReads=budget.reads();lastWrites=budget.writes();
    }
    void restore(ServerLevel level,Region r,BlightEcologyRules.Budget budget,int allowance){
        if(!r.purifying||r.ecology!=null&&r.ecology.readOnly())return;
        boolean loaded=r.processed()==r.total()&&(r.ecology==null||r.ecology.restoredCount()==r.ecology.size());
        if(!loaded)for(var entry:r.chunks.entrySet()){
            if(!budget.index())return;
            if(entry.getValue().cursor<entry.getValue().positions.length&&loaded(level,entry.getKey())){loaded=true;break;}
        }
        if(!loaded&&r.ecology!=null)for(long key:r.ecology.changeChunks()){
            if(!budget.index())return;if(loaded(level,key)){loaded=true;break;}
        }
        if(!loaded)return;
        r.elapsed=Math.min(r.duration,r.elapsed+1);setDirty();int startWrites=budget.writes();
        for(var entry:r.chunks.entrySet()){
            if(!budget.index())return;
            if(!loaded(level,entry.getKey()))continue;
            var part=entry.getValue();
            while(part.cursor<part.positions.length){
                var pos=BlockPos.of(part.positions[part.cursor]);
                if(due(r,pos)>r.elapsed)break;
                if(budget.remainingReads()<2||budget.remainingWrites()==0||budget.writes()-startWrites>=allowance)return;
                budget.read();var state=level.getBlockState(pos);var replacement=restored(state,pos.asLong());
                if(!replacement.equals(state)){
                    if(replacement.getBlock() instanceof BushBlock){budget.read();if(!replacement.canSurvive(level,pos))replacement=Blocks.AIR.defaultBlockState();}
                    budget.write();level.setBlock(pos,replacement,Block.UPDATE_CLIENTS);
                    purifyParticle(level,pos);
                }
                part.cursor++;
            }
        }
        if(r.ecology!=null){
            var journal=r.ecology;int examined=0;
            while(examined++<journal.size()&&journal.restoredCount()<journal.size()){
                if(!budget.index()||budget.remainingReads()<2||budget.remainingWrites()==0||budget.writes()-startWrites>=allowance)return;
                int index=Math.floorMod(r.ecologyScan++,journal.size());if(journal.restored(index))continue;
                var c=journal.change(index);if(due(r,c.pos())>r.elapsed||!loaded(level,chunk(c.pos())))continue;
                budget.read();var current=level.getBlockState(c.pos());
                if(c.matches(current)){
                    boolean survives=true;
                    if(c.before().getBlock() instanceof BushBlock||c.before().is(Blocks.SNOW)){budget.read();survives=c.before().canSurvive(level,c.pos());}
                    if(survives){budget.write();level.setBlock(c.pos(),c.before(),Block.UPDATE_CLIENTS);purifyParticle(level,c.pos());}
                }
                journal.restoredAt(index);
            }
        }
        if(r.elapsed>=r.duration&&r.processed()==r.total()&&(r.ecology==null||r.ecology.restoredCount()==r.ecology.size()))finish(level,r);
    }
    private static int due(Region r,BlockPos pos){return Math.max(1,Math.min(r.duration,(int)Math.ceil(r.duration*Math.sqrt(radialSquared(pos,r.center))/Math.max(1,r.radius))));}
    private static void purifyParticle(ServerLevel level,BlockPos pos){
        level.sendParticles(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(),0xD5F4FF,.18f,24),pos.getX()+.5,pos.getY()+1.05,pos.getZ()+.5,2,.3,.05,.3,.025);
    }
    private static boolean loaded(ServerLevel level,long key){return level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key))!=null;}
    private void markComplete(long center){completed.add(center);completedChunks.computeIfAbsent(chunk(BlockPos.of(center)),k->new HashSet<>()).add(center);}
    private void finish(ServerLevel level,Region r){
        long key=r.center.asLong();unindex(r);removeCoreIndex(r);regions.remove(key);waves.remove(key);markComplete(key);
        if(r.rewardEligible&&!r.participants.isEmpty()){pending.put(key,new ArrayList<>(r.participants));if(r.reward!=60)rewardAmounts.put(key,r.reward);}
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
    /** Ecology footprint only; the existing Gloom Tide sanctuary keeps its 32-block sphere. */
    public boolean ecologyProtects(BlockPos floor){return purifiedColumns.contains(BlightRegion.column(floor))||protects(Vec3.atCenterOf(floor));}
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
                    player.setData(SpiritData.PROGRESS,progress.withRewards(rewards));SpiritProgression.grantLight(player,rewardAmounts.getOrDefault(entry.getKey().longValue(),BlightRules.REWARD));
                    CompanionLines.offer(player,CompanionLines.Line.FIRST_PURIFY);
                    cn.wwl.orimod.progression.StoryProgress.award(player,cn.wwl.orimod.progression.StoryProgress.Step.BLIGHT);
                    cn.wwl.orimod.progression.WillWisps.purified(player,level,BlockPos.of(entry.getKey()));
                }
                recipients.remove();setDirty();
            }
            if(entry.getValue().isEmpty()){rewardAmounts.remove(entry.getKey().longValue());iterator.remove();}
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
            var data=get(level);var r=data.regions.get(e.getPersistentData().getLong(DEFENDER));if(r!=null){if(r.guards.remove(e.getUUID()))data.setDirty();if(r.extraMobs.remove(e.getUUID())){data.extraMobs--;data.setDirty();}}
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
            if(r.naturalEcology)e.putBoolean("natural_ecology",true);
            if(!r.rewardEligible)e.putBoolean("no_region_reward",true);
            if(r.ecology!=null){e.put("ecology",r.ecology.save());e.putInt("duration",r.duration);e.putInt("reward",r.reward);}
            var chunks=new ListTag();r.chunks.forEach((key,p)->{var c=new CompoundTag();c.putLong("chunk",key);c.putLongArray("positions",p.positions);c.putInt("cursor",p.cursor);chunks.add(c);});e.put("chunks",chunks);
            if(!r.animals.isEmpty())e.put("animals",uuids(r.animals));if(!r.extraMobs.isEmpty())e.put("ecology_mobs",uuids(r.extraMobs));e.put("participants",uuids(r.participants));e.put("guards",uuids(r.guards));list.add(e);
        }
        tag.put("regions",list);tag.putLongArray("completed",completed.stream().mapToLong(Long::longValue).toArray());
        if(!purifiedColumns.isEmpty())tag.putLongArray("purified_columns",purifiedColumns.toLongArray());
        var rewards=new ListTag();pending.forEach((key,players)->{var e=new CompoundTag();e.putLong("center",key);e.put("players",uuids(players));if(rewardAmounts.containsKey(key.longValue()))e.putInt("amount",rewardAmounts.get(key.longValue()));rewards.add(e);});tag.put("pending",rewards);return tag;
    }
    private static ListTag uuids(Collection<UUID> ids){var list=new ListTag();for(var id:ids)list.add(NbtUtils.createUUID(id));return list;}
    private static List<UUID> readIds(ListTag list,int limit){var ids=new ArrayList<UUID>();for(var value:list){if(ids.size()>=limit)break;ids.add(NbtUtils.loadUUID(value));}return ids;}
    public static BlightData load(CompoundTag tag,HolderLookup.Provider registries){
        var data=new BlightData();for(long center:tag.getLongArray("completed"))data.markComplete(center);
        for(long column:tag.getLongArray("purified_columns"))data.purifiedColumns.add(column);
        for(var value:tag.getList("regions",Tag.TAG_COMPOUND)){
            var e=(CompoundTag)value;long center=e.getLong("center");if(data.completed.contains(center))continue;
            var chunks=e.getList("chunks",Tag.TAG_COMPOUND);var all=new ArrayList<Long>();
            for(var v:chunks)for(long p:((CompoundTag)v).getLongArray("positions"))all.add(p);
            var r=new Region(BlockPos.of(center),all.stream().mapToLong(Long::longValue).toArray());
            r.purifying=e.getBoolean("purifying");r.nextDefender=e.getLong("next_defender");
            r.naturalEcology=e.getBoolean("natural_ecology");
            r.rewardEligible=!e.getBoolean("no_region_reward");
            if(e.contains("ecology",Tag.TAG_COMPOUND)){
                r.ecology=BlightRegion.load(r.center,e.getCompound("ecology"),registries);r.radius=Math.max(r.radius,r.ecology.radius());
                r.duration=Math.clamp(e.contains("duration")?e.getInt("duration"):600,600,BlightEcologyRules.duration(64));r.reward=Math.clamp(e.contains("reward")?e.getInt("reward"):60,60,180);
                if(r.purifying)for(var p:r.ecology.columns())data.purifiedColumns.add(BlightRegion.column(p));
            }
            r.elapsed=Math.clamp(e.getInt("elapsed"),0,r.duration);
            for(var v:chunks){var c=(CompoundTag)v;var p=r.chunks.get(c.getLong("chunk"));if(p!=null)p.cursor=Math.max(0,Math.min(p.positions.length,c.getInt("cursor")));}
            r.animals.addAll(readIds(e.getList("animals",Tag.TAG_INT_ARRAY),64));
            r.extraMobs.addAll(readIds(e.getList("ecology_mobs",Tag.TAG_INT_ARRAY),3));data.extraMobs+=r.extraMobs.size();
            r.participants.addAll(readIds(e.getList("participants",Tag.TAG_INT_ARRAY),16));r.guards.addAll(readIds(e.getList("guards",Tag.TAG_INT_ARRAY),3));data.regions.put(center,r);data.indexCore(r);data.index(r);if(r.purifying)data.waves.add(center);
        }
        for(var value:tag.getList("pending",Tag.TAG_COMPOUND)){var e=(CompoundTag)value;var ids=readIds(e.getList("players",Tag.TAG_INT_ARRAY),16);if(!ids.isEmpty()){long key=e.getLong("center");data.pending.put(key,ids);if(e.contains("amount"))data.rewardAmounts.put(key,Math.clamp(e.getInt("amount"),60,180));}}
        return data;
    }
}
