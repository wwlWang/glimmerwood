package cn.wwl.orimod.world;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.OriMobs;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static cn.wwl.orimod.world.ShrineRules.*;

/** Server-owned trials. Generation journals are durable; no session survives a world restart. */
public final class ShrineTrials extends SavedData {
    public static final String TAG="orimod:shrine_session";
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,"orimod");
    private static final Supplier<AttachmentType<List<CompoundTag>>> JOURNAL=TYPES.register("shrine_contracts",()->AttachmentType.<List<CompoundTag>>builder(() -> List.of()).serialize(CompoundTag.CODEC.listOf()).build());
    private static final Map<ServerLevel,ConcurrentLinkedQueue<ShrineContract>> PENDING=new ConcurrentHashMap<>();
    public static void register(net.neoforged.bus.api.IEventBus bus){TYPES.register(bus);}
    public static final class Session {
        public final UUID id=UUID.randomUUID();
        public final Map<UUID,Participant> participants=new LinkedHashMap<>();
        public final Set<UUID> mobs=new HashSet<>();
        public final long began;
        public long elapsed;
        public Phase phase=Phase.SEALING;
        public int wave=1,cursor,wait;
        public List<BlockPos> spawnPoints=List.of();
        public boolean won;
        Session(long now){began=now;}
    }
    public static final class Site {
        public final ShrineContract contract;
        public Session session;
        public String reason="";
        public long lastElapsed;
        public double maxSealMillis,maxClearMillis,startMillis;
        Site(ShrineContract contract){this.contract=contract;}
    }
    private final Map<Long,Site> sites=new HashMap<>();
    private final Set<Long> owned=new HashSet<>(),cleanup=new LinkedHashSet<>();
    public static ShrineTrials get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(ShrineTrials::new,ShrineTrials::load,null),"orimod_shrine_trials");}
    public static ShrineTrials load(CompoundTag tag,HolderLookup.Provider registry){
        var data=new ShrineTrials();
        for(var value:tag.getList("sites",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)value;var site=new Site(ShrineContract.load(t.getCompound("contract")));data.sites.put(site.contract.altar().asLong(),site);
            site.reason=t.getString("reason");site.lastElapsed=t.getLong("elapsed");
            if(t.getBoolean("active")){site.reason="restart";site.contract.wards().forEach(p->data.cleanup.add(p.asLong()));OriMod.LOGGER.info("Shrine {} failed: restart",site.contract.altar());}
        }
        for(long pos:tag.getLongArray("cleanup"))data.cleanup.add(pos);
        if(!data.cleanup.isEmpty())data.setDirty();return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry){
        var list=new ListTag();for(var site:sites.values()){
            var t=new CompoundTag();t.put("contract",site.contract.save());t.putBoolean("active",site.session!=null);t.putString("reason",site.reason);t.putLong("elapsed",site.lastElapsed);
            // Persist the actual state for diagnostics; loading always aborts rather than resuming it.
            if(site.session!=null){var s=site.session;t.putUUID("session",s.id);t.putString("phase",s.phase.name());t.putInt("wave",s.wave);t.putLong("began",s.began);
                var participants=new ListTag();s.participants.forEach((id,state)->{var p=new CompoundTag();p.putUUID("id",id);p.putString("state",state.name());participants.add(p);});t.put("participants",participants);
                t.putInt("remaining",s.mobs.size());}
            list.add(t);
        }
        tag.put("sites",list);tag.putLongArray("cleanup",cleanup.stream().mapToLong(Long::longValue).toArray());return tag;
    }
    public void register(ShrineContract contract){if(!sites.containsKey(contract.altar().asLong())){sites.put(contract.altar().asLong(),new Site(contract));setDirty();}}
    public Site site(BlockPos altar){return sites.get(altar.asLong());}
    public Collection<Site> sites(){return Collections.unmodifiableCollection(sites.values());}
    public static void generated(WorldGenLevel level,ShrineContract contract){
        var chunk=level.getChunk(contract.altar());synchronized(chunk){var values=new ArrayList<>(chunk.getData(JOURNAL));if(values.stream().noneMatch(t->t.getLong("altar")==contract.altar().asLong())){values.add(contract.save());chunk.setData(JOURNAL,List.copyOf(values));chunk.setUnsaved(true);}}
        if(level instanceof ServerLevel server)get(server).register(contract);else PENDING.computeIfAbsent(level.getLevel(),k->new ConcurrentLinkedQueue<>()).add(contract);
        FeatureSites.generated(level,contract.altar(),FeatureSites.Kind.SHRINE);
    }
    public static void loadChunk(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level))return;var chunk=event.getChunk();
        if(chunk.hasData(JOURNAL))for(var tag:chunk.getData(JOURNAL))PENDING.computeIfAbsent(level,k->new ConcurrentLinkedQueue<>()).add(ShrineContract.load(tag));
        // Only ward-bearing sections are inspected. No neighbouring chunks are accessed.
        var data=get(level);var cp=chunk.getPos();
        for(int i=0;i<chunk.getSectionsCount();i++){
            var section=chunk.getSection(i);if(!section.maybeHas(s->s.is(OriBlocks.SHRINE_WARD)))continue;
            int base=chunk.getSectionYFromSectionIndex(i)<<4;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(section.getBlockState(x,y,z).is(OriBlocks.SHRINE_WARD)){
                long pos=new BlockPos(cp.getMinBlockX()+x,base+y,cp.getMinBlockZ()+z).asLong();if(!data.owned.contains(pos))data.cleanup.add(pos);
            }
        }
        if(!data.cleanup.isEmpty())data.setDirty();
    }
    public static void merge(ServerLevel level){var queue=PENDING.get(level);if(queue!=null)for(int n=0;n<64;n++){var c=queue.poll();if(c==null)break;get(level).register(c);}}
    private static boolean loaded(ServerLevel level,BlockPos p){return level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;}
    public boolean ownsWard(BlockPos p){return owned.contains(p.asLong());}
    public static String receipt(Level level,BlockPos altar){return "shrine/"+level.dimension().location()+"/"+altar.getX()+","+altar.getY()+","+altar.getZ();}
    public String start(ServerPlayer player,BlockPos altar){
        var level=player.serverLevel();merge(level);var site=site(altar);String reason=refusal(player,site);
        if(!reason.isEmpty()){message(player,reason);return reason;}
        long begin=System.nanoTime();var s=new Session(level.getGameTime());
        for(var p:level.players())if(p.isAlive() && !p.isSpectator() && site.contract.inside(p.position(),0))s.participants.put(p.getUUID(),Participant.ACTIVE);
        // The initiator can be a server test player not enrolled in PlayerList.
        s.participants.putIfAbsent(player.getUUID(),Participant.ACTIVE);
        if(s.participants.size()>MAX_PLAYERS){message(player,"too_many");return "too_many";}
        for(var p:site.contract.wards()){
            if(!loaded(level,p)){message(player,"unloaded");return "unloaded";}
            if(!level.getBlockState(p).isAir()){message(player,"blocked");return "blocked";}
            if(!level.getEntities((Entity)null,new AABB(p),e->!e.isSpectator() && e.isAlive()).isEmpty()){message(player,"occupied");return "occupied";}
        }
        site.session=s;site.reason="";site.maxSealMillis=site.maxClearMillis=0;site.startMillis=(System.nanoTime()-begin)/1e6;setDirty();
        for(var p:players(level,s)){message(p,"begin");CompanionLines.offer(p,CompanionLines.Line.SHRINE_START);}
        return "";
    }
    private String refusal(ServerPlayer p,Site site){
        if(site==null || !p.level().getBlockState(site.contract.altar()).is(OriBlocks.SPIRIT_SHRINE_ALTAR))return "missing";
        if(!SpiritData.isAwakened(p) || !SpiritData.isSpirit(p) || !p.isAlive() || p.isSpectator())return "form";
        if(p.level().getDifficulty()==Difficulty.PEACEFUL)return "peaceful";
        if(site.session!=null || site.contract.wards().stream().anyMatch(v->cleanup.contains(v.asLong())))return "busy";
        return site.contract.inside(p.position(),0)?"":"outside";
    }
    private static List<ServerPlayer> players(ServerLevel level,Session s){return s.participants.keySet().stream().map(id->level.getServer().getPlayerList().getPlayer(id)).filter(Objects::nonNull).toList();}
    public static void message(ServerPlayer p,String id,Object...args){p.displayClientMessage(Component.translatable("message.orimod.shrine."+id,args),false);}
    public void stop(ServerLevel level,Site site,String why){finish(level,site,false,why);}
    private void finish(ServerLevel level,Site site,boolean won,String reason){
        var s=site.session;if(s==null || s.phase==Phase.CLEANUP)return;
        s.phase=Phase.CLEANUP;s.cursor=0;s.won=won;site.reason=reason;site.lastElapsed=s.elapsed;
        for(var id:List.copyOf(s.mobs)){var e=level.getEntity(id);if(e!=null)e.discard();}s.mobs.clear();
        for(var p:players(level,s)){
            if(won && rewardEligible(s.participants.get(p.getUUID())))reward(p,site.contract.altar());
            else if(!won)message(p,"failed",Component.translatable("message.orimod.shrine.reason."+reason));
        }
        OriMod.LOGGER.info("Shrine {} {}: {} after {} ticks; seal max={} ms, preflight={} ms",site.contract.altar(),won?"completed":"failed",reason,site.lastElapsed,site.maxSealMillis,site.startMillis);setDirty();
    }
    private static void reward(ServerPlayer p,BlockPos altar){
        var progress=p.getData(SpiritData.PROGRESS);String key=receipt(p.level(),altar);boolean seen=progress.rewards().containsKey(key);
        var reward=ShrineRules.reward(progress.ownedShards(),seen,p.getRandom().nextInt());var records=new HashMap<>(progress.rewards());records.put(key,1);
        p.setData(SpiritData.PROGRESS,progress.withOwned(progress.ownedShards()|reward.shard()).withRewards(records));SpiritProgression.grantLight(p,reward.light());
        Component item=Arrays.stream(Shard.values()).filter(shard->shard.bit==reward.shard()).findFirst().map(Shard::title).orElse(Component.translatable("message.orimod.shrine.no_shard"));
        message(p,"reward",item,reward.light());CompanionLines.offer(p,CompanionLines.Line.SHRINE_COMPLETE);
    }
    public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)get(level).tickLevel(level);}
    public void tickLevel(ServerLevel level){tickLevel(level,level.getGameTime());}
    /** Explicit clock permits boundary tests without changing the shared world clock. */
    public void tickLevel(ServerLevel level,long now){
        merge(level);
        for(var site:List.copyOf(sites.values())){
            if(site.session==null)continue;var s=site.session;s.elapsed=Math.max(0,now-s.began);
            if(s.phase!=Phase.CLEANUP){
                if(site.contract.chunks().stream().anyMatch(c->{var p=new ChunkPos(c);return level.getChunkSource().getChunkNow(p.x,p.z)==null;}))finish(level,site,false,"unloaded");
                else if(!level.getBlockState(site.contract.altar()).is(OriBlocks.SPIRIT_SHRINE_ALTAR))finish(level,site,false,"altar_removed");
                else if(level.getDifficulty()==Difficulty.PEACEFUL)finish(level,site,false,"peaceful");
                else if(timedOut(s.elapsed))finish(level,site,false,"timeout");
                else {
                    for(var entry:s.participants.entrySet()){
                        var p=level.getServer().getPlayerList().getPlayer(entry.getKey());
                        if(p==null){entry.setValue(Participant.LEFT);continue;}
                        if(entry.getValue()!=Participant.ACTIVE)continue;
                        if(!p.isAlive())entry.setValue(Participant.DEAD);
                        else if(p.serverLevel()!=level || p.isSpectator() || !site.contract.inside(p.position(),3))entry.setValue(Participant.LEFT);
                    }
                    if(allOut(s.participants.values()))finish(level,site,false,"all_out");
                }
            }
            if(s.phase==Phase.SEALING || s.phase==Phase.CLEANUP){writeShell(level,site);continue;}
            if(s.phase==Phase.WARNING){
                if(s.wait%4==0)warning(level,s);
                if(--s.wait<=0)spawn(level,site);
            }else if(s.phase==Phase.INTERMISSION){if(--s.wait<=0)prepare(level,site);}
            else if(s.phase==Phase.FIGHT){
                if(s.mobs.isEmpty()){
                    if(s.wave==3)finish(level,site,true,"victory");else{s.wave++;s.phase=Phase.INTERMISSION;s.wait=INTERMISSION;setDirty();}
                }else for(var id:List.copyOf(s.mobs)){
                    var e=level.getEntity(id);if(!(e instanceof Mob mob) || !e.isAlive()){finish(level,site,false,"enemy_lost");break;}
                    if(!site.contract.inside(e.position(),1)){finish(level,site,false,"enemy_lost");break;}
                    if(level.getGameTime()%20==0)players(level,s).stream().filter(p->s.participants.get(p.getUUID())==Participant.ACTIVE).min(Comparator.comparingDouble(mob::distanceToSqr)).ifPresent(mob::setTarget);
                }
            }
        }
        // Deferred cleanup never loads a chunk, and gets a fixed global write budget.
        int count=0;var it=cleanup.iterator();while(it.hasNext() && count<512){var pos=BlockPos.of(it.next());if(!loaded(level,pos))continue;
            if(!owned.contains(pos.asLong()) && level.getBlockState(pos).is(OriBlocks.SHRINE_WARD))level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
            it.remove();count++;setDirty();
        }
    }
    private void writeShell(ServerLevel level,Site site){
        var s=site.session;boolean seal=s.phase==Phase.SEALING;var cells=site.contract.wards();int budget=(cells.size()+3)/4;long began=System.nanoTime();
        int end=Math.min(cells.size(),s.cursor+budget);
        while(s.cursor<end){var p=cells.get(s.cursor++);
            if(seal){
                if(!loaded(level,p) || (!level.getBlockState(p).isAir() || !level.getEntities((Entity)null,new AABB(p),e->e.isAlive() && !e.isSpectator()).isEmpty())){finish(level,site,false,"blocked");return;}
                owned.add(p.asLong());level.setBlock(p,OriBlocks.SHRINE_WARD.get().defaultBlockState(),Block.UPDATE_CLIENTS);
            }else{
                owned.remove(p.asLong());
                if(!loaded(level,p)){cleanup.add(p.asLong());continue;}
                if(level.getBlockState(p).is(OriBlocks.SHRINE_WARD))level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        }
        double ms=(System.nanoTime()-began)/1e6;if(seal)site.maxSealMillis=Math.max(site.maxSealMillis,ms);else site.maxClearMillis=Math.max(site.maxClearMillis,ms);setDirty();
        if(s.cursor==cells.size()){
            if(seal)prepare(level,site);
            else {
                if(s.won && loaded(level,site.contract.altar()) && level.getBlockState(site.contract.altar()).is(OriBlocks.SPIRIT_SHRINE_ALTAR))level.setBlock(site.contract.altar(),OriBlocks.SPIRIT_SHRINE_ALTAR.get().defaultBlockState().setValue(SpiritShrineAltarBlock.COMPLETED,true),Block.UPDATE_CLIENTS);
                site.session=null;OriMod.LOGGER.info("Shrine {} cleanup max={} ms ({} cells / 4 ticks)",site.contract.altar(),site.maxClearMillis,cells.size());
            }
        }
    }
    private Mob create(ServerLevel level,MobKind kind){return switch(kind){case SPITTER->OriMobs.GLOOM_SPITTER.get().create(level);case BEETLE->OriMobs.GLOOM_BEETLE.get().create(level);case HAWK->OriMobs.GLOOM_HAWK.get().create(level);};}
    private boolean validSpawn(ServerLevel level,Site site,Mob mob,BlockPos p){
        if(!site.contract.inside(Vec3.atBottomCenterOf(p),0) || !GloomTide.validGround(level,p))return false;
        var point=Vec3.atBottomCenterOf(p);mob.moveTo(point.x,point.y,point.z,0,0);
        return level.noCollision(mob) && players(level,site.session).stream().filter(v->site.session.participants.get(v.getUUID())==Participant.ACTIVE).noneMatch(v->v.distanceToSqr(point)<9);
    }
    private void prepare(ServerLevel level,Site site){
        var s=site.session;var chosen=new ArrayList<BlockPos>();var choices=new ArrayList<>(site.contract.spawns());Collections.shuffle(choices,new Random(level.random.nextLong()));
        for(var kind:wave(s.wave,s.participants.size())){
            var mob=create(level,kind);if(mob==null){finish(level,site,false,"spawn_blocked");return;}
            var pos=choices.stream().filter(p->chosen.stream().noneMatch(q->q.distSqr(p)<9) && validSpawn(level,site,mob,p)).findFirst();mob.discard();
            if(pos.isEmpty()){finish(level,site,false,"spawn_blocked");return;}chosen.add(pos.get());
        }
        s.spawnPoints=List.copyOf(chosen);s.wait=WARNING;s.phase=Phase.WARNING;setDirty();
        for(var p:players(level,s))message(p,"wave",s.wave);
        level.playSound(null,site.contract.altar(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,.8f,.7f);
    }
    private void warning(ServerLevel level,Session s){
        var particle=new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(),0xAA66F0,.55f,16);
        double angle=(WARNING-s.wait)*.6;
        for(var p:s.spawnPoints)for(int i=0;i<3;i++){double a=angle+i*Math.PI*2/3;level.sendParticles(particle,p.getX()+.5+.6*Math.cos(a),p.getY()+.15+(WARNING-s.wait)*.02,p.getZ()+.5+.6*Math.sin(a),1,0,.03,0,0);}
    }
    private void spawn(ServerLevel level,Site site){
        var s=site.session;var kinds=wave(s.wave,s.participants.size());var pending=new ArrayList<Mob>();
        for(int i=0;i<kinds.size();i++){
            var mob=create(level,kinds.get(i));var p=s.spawnPoints.get(i);
            if(mob==null || !validSpawn(level,site,mob,p)){if(mob!=null)mob.discard();pending.forEach(Entity::discard);finish(level,site,false,"spawn_blocked");return;}
            mob.getPersistentData().putUUID(TAG,s.id);mob.setPersistenceRequired();EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(p),MobSpawnType.EVENT,null);pending.add(mob);
        }
        s.phase=Phase.FIGHT;
        for(var mob:pending){s.mobs.add(mob.getUUID());if(!level.addFreshEntity(mob)){s.mobs.remove(mob.getUUID());pending.forEach(Entity::discard);finish(level,site,false,"spawn_blocked");return;}
            players(level,s).stream().filter(p->s.participants.get(p.getUUID())==Participant.ACTIVE).min(Comparator.comparingDouble(mob::distanceToSqr)).ifPresent(mob::setTarget);}
        setDirty();
    }
    public static boolean contains(ServerLevel level,Vec3 pos){return get(level).sites.values().stream().anyMatch(s->s.session!=null && s.contract.inside(pos,0));}
    public static void natural(MobSpawnEvent.PositionCheck event){if(event.getSpawnType()==MobSpawnType.NATURAL && contains(event.getLevel().getLevel(),event.getEntity().position()))event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);}
    public static void death(LivingDeathEvent event){
        if(event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level))return;var data=get(level);
        for(var site:data.sites.values())if(site.session!=null){var s=site.session;var id=event.getEntity().getUUID();
            if(event.getEntity() instanceof ServerPlayer && s.participants.get(id)==Participant.ACTIVE)s.participants.put(id,Participant.DEAD);
            if(s.mobs.remove(id))data.setDirty();}
    }
    public static void leave(EntityLeaveLevelEvent event){if(!(event.getLevel() instanceof ServerLevel level) || !event.getEntity().getPersistentData().hasUUID(TAG))return;var data=get(level);
        for(var site:data.sites.values())if(site.session!=null && site.session.phase!=Phase.CLEANUP && site.session.mobs.contains(event.getEntity().getUUID()))data.finish(level,site,false,"enemy_lost");}
    public static void join(EntityJoinLevelEvent event){if(!(event.getLevel() instanceof ServerLevel level) || !event.getEntity().getPersistentData().hasUUID(TAG))return;
        var id=event.getEntity().getPersistentData().getUUID(TAG);boolean valid=get(level).sites.values().stream().anyMatch(s->s.session!=null && s.session.phase!=Phase.CLEANUP && s.session.id.equals(id));
        if(!valid){event.setCanceled(true);event.getEntity().discard();}}
    private static void exit(ServerPlayer player){for(var level:player.server.getAllLevels()){var data=get(level);for(var site:data.sites.values())if(site.session!=null && site.session.participants.containsKey(player.getUUID())){site.session.participants.put(player.getUUID(),Participant.LEFT);data.setDirty();if(allOut(site.session.participants.values()))data.finish(level,site,false,"all_out");}}}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);}
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);}
    public static void unloadChunk(ChunkEvent.Unload e){if(e.getLevel() instanceof ServerLevel level){var data=get(level);for(var site:data.sites.values())if(site.session!=null && site.contract.chunks().contains(e.getChunk().getPos().toLong()))data.finish(level,site,false,"unloaded");}}
    public static void stopping(net.minecraft.server.MinecraftServer server){for(var level:server.getAllLevels()){var data=get(level);for(var site:data.sites.values())if(site.session!=null){data.finish(level,site,false,"shutdown");while(site.session!=null)data.writeShell(level,site);}}}
    public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level)PENDING.remove(level);}
    public static void clear(){PENDING.clear();}
    public Site nearest(Vec3 pos,double distance){return sites.values().stream().filter(s->s.contract.altar().distToCenterSqr(pos)<=distance*distance).min(Comparator.comparingDouble(s->s.contract.altar().distToCenterSqr(pos))).orElse(null);}
    public static Map<String,Object> status(ServerPlayer p){
        var data=get(p.serverLevel());merge(p.serverLevel());var site=data.nearest(p.position(),32);var s=site==null?null:site.session;var out=new LinkedHashMap<String,Object>();
        out.put("state",s==null?"IDLE":s.phase.name());out.put("wave",s==null?0:s.wave);out.put("remaining",s==null?0:s.mobs.size());out.put("participants",s==null?Map.of():s.participants.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),e->e.getValue().name())));
        out.put("elapsed",s==null?(site==null?0:site.lastElapsed):s.elapsed);out.put("ward",site!=null && site.contract.wards().stream().anyMatch(data::ownsWard));out.put("reason",site==null?"":site.reason);
        if(site!=null){out.put("altar",List.of(site.contract.altar().getX(),site.contract.altar().getY(),site.contract.altar().getZ()));out.put("sealMillis",site.maxSealMillis);out.put("clearMillis",site.maxClearMillis);out.put("preflightMillis",site.startMillis);out.put("wardCount",site.contract.wards().size());}return out;
    }
}
