package cn.wwl.orimod.world;

import cn.wwl.orimod.config.OriServerConfig;
import cn.wwl.orimod.entity.OriMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Extra ecology creatures share the existing defender reservation, including unloaded ones. */
public final class BlightMobs {
    public static final String EXTRA="orimod_blight_ecology_mob";
    private static long attempted=Long.MIN_VALUE,spawned=Long.MIN_VALUE;
    public static boolean room(int core,int global,long now,long lastAttempt,long lastSpawn){return core<3&&global<32&&now!=lastAttempt&&(lastSpawn==Long.MIN_VALUE||now-lastSpawn>=20);}
    static void tick(ServerLevel level,BlightData.Region r,BlightEcologyRules.Budget b){
        if(r.ecology.loadedTicks()%200!=0||level.random.nextInt(4)!=0)return;
        var player=level.players().stream().filter(p->!p.isSpectator()&&!p.isCreative()&&p.isAlive()&&p.distanceToSqr(Vec3.atCenterOf(r.center))<=80*80).findFirst();
        player.ifPresent(p->attempt(p,r.center,b,level.getServer().getTickCount()));
    }
    public static boolean attempt(ServerPlayer player,BlockPos center,BlightEcologyRules.Budget b,long now){
        var level=player.serverLevel();var data=BlightData.get(level);var r=data.region(center);
        if(!OriServerConfig.blightEnabled()||!OriServerConfig.blightExtraMobs()||r==null||!data.activeCore(center)||r.ecology.columns().isEmpty()
                ||level.getDifficulty()==Difficulty.PEACEFUL||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)||player.isCreative()||player.isSpectator()||!player.isAlive())return false;
        int global=0;for(var l:level.getServer().getAllLevels())global+=BlightData.get(l).extraMobCount();
        if(!room(r.guards.size(),global,now,attempted,spawned)||b.remainingReads()<16||!b.index())return false;
        attempted=now;var floor=r.ecology.columnAt(level.random.nextInt(r.ecology.columns().size()));var pos=floor.above();var point=Vec3.atBottomCenterOf(pos);
        double distance=player.distanceToSqr(point);if(distance<64||distance>48*48||level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return false;
        if(!data.activeAt(point).filter(center::equals).isPresent()||BlightProtection.inspect(level,floor,center,b).result()!=BlightProtection.Result.ALLOW||b.remainingReads()<3)return false;
        // The checked chunk already exists; three ground/clearance reads are reserved in the same terrain budget.
        b.read();b.read();b.read();if(!GloomTide.validGround(level,pos))return false;
        Mob mob=switch(level.random.nextInt(3)){case 0->OriMobs.GLOOM_BEETLE.get().create(level);case 1->OriMobs.GLOOM_HAWK.get().create(level);default->OriMobs.GLOOM_SPITTER.get().create(level);};
        if(mob==null)return false;mob.moveTo(point.x,point.y,point.z,level.random.nextFloat()*360,0);
        if(!level.noCollision(mob)){mob.discard();return false;}
        mob.getPersistentData().putLong(BlightData.DEFENDER,center.asLong());mob.getPersistentData().putBoolean(EXTRA,true);
        EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);
        if(!level.addFreshEntity(mob)){mob.discard();return false;}
        r.guards.add(mob.getUUID());r.extraMobs.add(mob.getUUID());data.extraAdded();spawned=now;mob.setTarget(player);return true;
    }
    public static void join(EntityJoinLevelEvent event){if(event.getLevel() instanceof ServerLevel l&&event.getEntity().getPersistentData().getBoolean(EXTRA)){
        var center=BlockPos.of(event.getEntity().getPersistentData().getLong(BlightData.DEFENDER));
        if(!BlightData.get(l).activeCore(center)){event.setCanceled(true);event.getEntity().discard();}
    }}
    static void purify(ServerLevel level,BlightData.Region r){for(var id:java.util.List.copyOf(r.extraMobs)){var e=level.getEntity(id);if(e!=null)e.discard();}}
    public static void clear(){attempted=spawned=Long.MIN_VALUE;}
    private BlightMobs(){}
}
