package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.GinsoEscapePayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Owner-only virtual flood. No block/fluid writes, global weather, Soul Link edits or bystander effects. */
public final class GinsoEscape {
    public static final String RECEIPT="escape/ginso";
    // Runtime tolerance only: frozen route/flood geometry and the grounded exit test stay unchanged.
    // Route boxes end 2.5 above the tread; four extra blocks cover triple jump plus headroom.
    private static final double JUMP_HEADROOM=4, DROP_TOLERANCE=1;
    public enum Phase { READY, PREPARE, RUNNING, SUCCESS, FAILED, COOLDOWN }
    public enum Outcome { NONE, SUCCESS, SUBMERGED, TIMEOUT, OUTSIDE, DEAD, DISCONNECTED, DIMENSION, CHUNK, RECOVERED }
    private static final ResourceLocation DRAG=ResourceLocation.parse("orimod:ginso_water_drag");
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    public static final class Session {
        public final UUID id=UUID.randomUUID();
        public final ServerLevel level;public final GinsoPiece piece;public final GinsoEscapeLayout.Layout layout;public final GinsoEscapeRules.Plan plan;
        public final GinsoFloodVolume flood;
        public Phase phase=Phase.PREPARE;public Outcome outcome=Outcome.NONE;
        public int elapsed, submerged, maximumSubmerged, cooldown, rewardedLight;public String rewardedCell="";
        public boolean eligible, invalidated;public float startingHealth;
        private Session(ServerPlayer p,GinsoPiece piece){this.level=p.serverLevel();this.piece=piece;layout=piece.escape();plan=GinsoEscapeRules.plan(layout,piece.escapeVersion(),cn.wwl.orimod.config.OriServerConfig.ginsoWaterMultiplier());
            flood=GinsoFloodVolume.forPiece(level,piece);
            eligible=SpiritData.isAwakened(p)&&!p.isCreative()&&!p.isSpectator();startingHealth=p.getHealth();}
        public boolean active(){return phase==Phase.PREPARE||phase==Phase.RUNNING;}
        public double water(){return piece.origin().getY()+GinsoEscapeRules.water(plan,elapsed);}
        public Vec3 local(Vec3 world){return localPosition(piece.origin(),piece.entrance(),world);}
    }
    public static Session session(ServerPlayer p){return SESSIONS.get(p.getUUID());}
    public static boolean active(ServerPlayer p){var s=session(p);return s!=null&&s.active();}
    public static Vec3 localPosition(BlockPos origin,int turn,Vec3 world){
        double x=world.x-origin.getX()-.5,z=world.z-origin.getZ()-.5;
        for(int i=0;i<Math.floorMod(-turn,4);i++){double t=x;x=-z;z=t;}
        return new Vec3(x+.5,world.y-origin.getY(),z+.5);
    }
    public static boolean inPlayableRoute(List<GinsoEscapeLayout.Box> route,double x,double y,double z){
        for(var b:route)if(x>=b.x0()&&x<b.x1()&&z>=b.z0()&&z<b.z1()
                &&y>=b.y0()-DROP_TOLERANCE&&y<b.y1()+JUMP_HEADROOM)return true;
        return false;
    }
    public static boolean start(ServerPlayer p,BlockPos heart){
        var piece=GinsoCourse.at(p.serverLevel(),heart);
        if(piece==null||piece.escapeVersion()==0||!piece.point(piece.escapeHeart()).equals(heart))return reject(p,"unavailable");
        return start(p,piece);
    }
    /** Same validation for placed structure fixtures and the right-click path. */
    public static boolean start(ServerPlayer p,GinsoPiece piece){
        var old=session(p);if(old!=null&&(old.active()||old.cooldown>0))return reject(p,old.active()?"running":"cooldown");
        var recovery=p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);
        if(recovery.resumable())return reject(p,"running");
        if(recovery.pending()||recovery.retryAt()>clock(p))return reject(p,"cooldown");
        var heart=piece.point(piece.escapeHeart());
        if(piece.escapeVersion()==0||!p.serverLevel().hasChunkAt(heart)||!p.serverLevel().getBlockState(heart).is(OriBlocks.GINSO_WATER_HEART)
                ||p.distanceToSqr(Vec3.atCenterOf(heart))>36||!p.isAlive()||Restoration.active(p)
                ||(!SpiritData.isAwakened(p)&&!p.isCreative()&&!p.isSpectator()))return reject(p,"unavailable");
        if(piece.escapeVersion()==2){
            var missing=GinsoClimbEscape.missing(p.getData(SpiritData.ABILITIES));
            if(!missing.isEmpty()){
                var names=Component.empty();
                for(int i=0;i<missing.size();i++){if(i>0)names.append(", ");names.append(missing.get(i).title());}
                p.displayClientMessage(Component.translatable("message.orimod.ginso.escape.requires",names),true);return false;
            }
            if(!SpiritData.isSpirit(p))return reject(p,"spirit_required");
        }
        var s=new Session(p,piece);var at=s.local(p.position());
        if(!inPlayableRoute(s.layout.route(),at.x,at.y,at.z))return reject(p,"approach");
        dismount(p);
        SESSIONS.put(p.getUUID(),s);
        cn.wwl.orimod.progression.StoryProgress.award(p,cn.wwl.orimod.progression.StoryProgress.Step.GINSO);
        saveProgress(p,s);
        GinsoEscapePayload.send(p,s,true);
        EscapeSupport.title(p,"title.orimod.ginso.prepare","subtitle.orimod.ginso.prepare",5,40,15);
        return true;
    }
    private static void dismount(ServerPlayer p) {
        if (p.isPassenger()) { p.stopRiding(); reject(p, "no_vehicle"); }
    }
    public static void mount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer p
                && session(p) != null && session(p).active()) {
            event.setCanceled(true);
            reject(p, "no_vehicle");
        }
    }
    private static boolean reject(ServerPlayer p,String reason){p.displayClientMessage(Component.translatable("message.orimod.ginso.escape."+reason),true);return false;}
    private static long clock(ServerPlayer p){return p.server.overworld().getGameTime();}
    private static void saveProgress(ServerPlayer p,Session s){
        var snap=GinsoEscapeRecovery.progress(s.piece,s.elapsed);
        p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,new GinsoEscapeRecovery(
                s.level.dimension().location().toString(),snap.safe(),false,0,
                snap.origin(),snap.seed(),snap.entrance(),snap.approach(),
                snap.shapeVersion(),snap.courseVersion(),snap.escapeVersion(),snap.elapsed()));
    }
    public static void tick(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)tick(p);}
    public static void tick(ServerPlayer p){
        var s=session(p);
        if(s==null){
            if(p.isAlive()&&p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).resumable())resume(p);
            else if(p.isAlive()&&p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending())recover(p);
            return;
        }
        if(!s.active()){
            if(s.phase==Phase.SUCCESS||s.phase==Phase.FAILED){s.phase=Phase.COOLDOWN;GinsoEscapePayload.send(p,s,false);}
            if(s.cooldown>0)s.cooldown--;
            if(s.cooldown==0&&s.phase!=Phase.READY){s.phase=Phase.READY;p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);GinsoEscapePayload.send(p,s,false);}
            return;
        }
        if(!p.isAlive()){end(p,s,Outcome.DEAD,false);return;}
        if(p.serverLevel()!=s.level){end(p,s,Outcome.DIMENSION,false);return;}
        var heart=s.piece.point(s.piece.escapeHeart());
        if(s.invalidated||s.level.getChunkSource().getChunkNow(heart.getX()>>4,heart.getZ()>>4)==null
                ||!s.level.getBlockState(heart).is(OriBlocks.GINSO_WATER_HEART)) {end(p,s,Outcome.CHUNK,true);return;}
        dismount(p);
        s.eligible &= !p.isCreative()&&!p.isSpectator();
        s.elapsed++;if(s.elapsed>=GinsoEscapeRules.PREPARE_TICKS)s.phase=Phase.RUNNING;
        saveProgress(p,s);
        var at=s.local(p.position());
        if(s.phase==Phase.RUNNING&&s.layout.exit().contains(at.x,at.y,at.z)&&p.onGround()){end(p,s,Outcome.SUCCESS,false);return;}
        if(!inPlayableRoute(s.layout.route(),at.x,at.y,at.z)){end(p,s,Outcome.OUTSIDE,true);return;}
        boolean wet=s.phase==Phase.RUNNING&&s.flood.contains(at.x,at.y,at.z)&&p.getY()<s.water()
                &&GinsoFloodVolume.passable(s.level,s.piece.origin(),s.piece.entrance(),GinsoFloodVolume.cell(at.x,at.y,at.z));
        drag(p,wet);
        var eye=s.local(p.getEyePosition());
        boolean headWet=wet&&p.getEyeY()<s.water()&&s.flood.contains(eye.x,eye.y,eye.z)
                &&GinsoFloodVolume.passable(s.level,s.piece.origin(),s.piece.entrance(),GinsoFloodVolume.cell(eye.x,eye.y,eye.z));
        s.submerged=headWet?s.submerged+1:0;
        s.maximumSubmerged=Math.max(s.maximumSubmerged,s.submerged);
        if(s.submerged>0&&s.submerged%20==0&&p.getHealth()>4)
            EscapeSupport.hurt(p,p.damageSources().drown(),Math.min(1,p.getHealth()-4));
        if(s.submerged>=GinsoEscapeRules.SUBMERGED_TICKS){end(p,s,Outcome.SUBMERGED,true);return;}
        if(s.elapsed>=GinsoEscapeRules.PREPARE_TICKS+s.plan.limitTicks()){end(p,s,Outcome.TIMEOUT,true);return;}
        if(s.elapsed%5==0)GinsoEscapePayload.send(p,s,false);
    }
    private static void drag(ServerPlayer p,boolean wet){
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed==null)return;
        if(wet){if(!speed.hasModifier(DRAG))speed.addTransientModifier(new AttributeModifier(DRAG,-.15,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
        else speed.removeModifier(DRAG);
    }
    private static void end(ServerPlayer p,Session s,Outcome outcome,boolean returnSafe){
        if(!s.active())return;
        s.outcome=outcome;s.phase=outcome==Outcome.SUCCESS?Phase.SUCCESS:Phase.FAILED;s.cooldown=GinsoEscapeRules.RETRY_TICKS;drag(p,false);
        if(outcome==Outcome.SUCCESS){cn.wwl.orimod.progression.StoryProgress.award(p,cn.wwl.orimod.progression.StoryProgress.Step.GINSO_ESCAPE);reward(p,s);EscapeSupport.title(p,"title.orimod.ginso.success","subtitle.orimod.ginso.success",5,50,20);}
        else if(outcome!=Outcome.DEAD&&p.isAlive())p.displayClientMessage(Component.translatable("message.orimod.ginso.escape.failed",Component.translatable("message.orimod.ginso.reason."+outcome.name().toLowerCase(Locale.ROOT))),false);
        if(outcome==Outcome.DEAD)p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);
        else if(returnSafe)p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.recover(s.level.dimension().location().toString(),s.piece.point(s.piece.escapeSafe()),clock(p)+s.cooldown));
        else p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.cooldown(s.level.dimension().location().toString(),s.piece.point(s.piece.escapeSafe()),clock(p)+s.cooldown));
        GinsoEscapePayload.send(p,s,false);
        if(returnSafe&&p.isAlive())recover(p);
    }
    private static void reward(ServerPlayer p,Session s){
        if(!s.eligible||p.isCreative()||p.isSpectator()||p.getData(SpiritData.PROGRESS).rewards().getOrDefault(RECEIPT,0)>0)return;
        var progress=p.getData(SpiritData.PROGRESS);var rewards=new HashMap<>(progress.rewards());rewards.put(RECEIPT,1);p.setData(SpiritData.PROGRESS,progress.withRewards(rewards));
        int light=100;
        if(SpiritCells.absorb(p,SpiritCells.Kind.ENERGY))s.rewardedCell="energy";
        else if(SpiritCells.absorb(p,SpiritCells.Kind.LIFE))s.rewardedCell="life";
        else light+=100;
        s.rewardedLight=light;SpiritProgression.grantLight(p,light);
        p.displayClientMessage(Component.translatable("message.orimod.ginso.escape.reward",light,Component.translatable("message.orimod.ginso.cell."+(s.rewardedCell.isEmpty()?"none":s.rewardedCell))),false);
    }
    /** Explicit recovery may load the saved safe chunk once; the active hot path never loads chunks. */
    public static void recover(ServerPlayer p){
        var r=p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);if(!r.pending()||!p.isAlive())return;
        ServerLevel level=null;var id=ResourceLocation.tryParse(r.dimension());if(id!=null)level=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
        var safe=r.safe();if(level==null){level=p.server.overworld();safe=level.getSharedSpawnPos();}
        level.getChunk(safe);Vec3 destination=null;
        // Do not overwrite player builds. Find a supported, collision-free landing nearby if the platform was edited.
        search:for(int dy:new int[]{0,1,2,3,-1,-2,-3,4,5,6})for(int radius=0;radius<=4;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;var q=safe.offset(x,dy,z);var feet=Vec3.atBottomCenterOf(q);
            if(!level.hasChunkAt(q)||!level.getFluidState(q).isEmpty()||!level.getBlockState(q.below()).isFaceSturdy(level,q.below(),net.minecraft.core.Direction.UP))continue;
            if(level.noCollision(p,p.getBoundingBox().move(feet.subtract(p.position())))){destination=feet;break search;}
        }
        if(destination==null){var fallback=level.getSharedSpawnPos();level.getChunk(fallback);destination=Vec3.atBottomCenterOf(fallback.above());}
        p.stopRiding();p.stopUsingItem();SpiritCombat.cancel(p,"ginso_recovery");SpiritMovement.teleported(p);drag(p,false);
        p.teleportTo(level,destination.x,destination.y,destination.z,p.getYRot(),p.getXRot());p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;p.fallDistance=0;
        p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.cooldown(level.dimension().location().toString(),safe,clock(p)+GinsoEscapeRules.RETRY_TICKS));
    }
    /** Rebuild an interrupted active escape. Offline time does not advance the flood clock. */
    public static boolean resume(ServerPlayer p){
        var r=p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);if(!r.resumable()||!p.isAlive()||session(p)!=null)return false;
        if(!r.dimension().isEmpty()&&!r.dimension().equals(p.level().dimension().location().toString())){
            p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);return false;
        }
        var piece=r.piece();
        var heart=piece.point(piece.escapeHeart());
        var level=p.serverLevel();
        // Login can fire before the player's chunk ticket lands; force-load like recover() before treating the heart as gone.
        level.getChunk(heart);
        if(!level.getBlockState(heart).is(OriBlocks.GINSO_WATER_HEART)){
            // Heart truly missing: fall back to safe recovery rather than soft-locking progress.
            p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.recover(
                    level.dimension().location().toString(),piece.point(piece.escapeSafe()),clock(p)+GinsoEscapeRules.RETRY_TICKS));
            recover(p);return false;
        }
        var s=new Session(p,piece);s.elapsed=r.elapsed();
        if(s.elapsed>=GinsoEscapeRules.PREPARE_TICKS)s.phase=Phase.RUNNING;
        SESSIONS.put(p.getUUID(),s);
        saveProgress(p,s);
        GinsoEscapePayload.send(p,s,true);
        p.displayClientMessage(Component.translatable("message.orimod.ginso.escape.resumed"),true);
        return true;
    }
    public static void unload(net.neoforged.neoforge.event.level.ChunkEvent.Unload e){
        // Mark only: never reload a chunk from its unload callback. Recovery runs on the next player tick.
        if(e.getLevel() instanceof ServerLevel level)for(var s:SESSIONS.values())
            if(s.active()&&s.level==level&&new net.minecraft.world.level.ChunkPos(s.piece.point(s.piece.escapeHeart())).equals(e.getChunk().getPos()))s.invalidated=true;
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        SESSIONS.remove(p.getUUID());
        if(resume(p))return;
        // Still resumable means resume deferred (e.g. transient load); player tick retries. Do not clear.
        if(p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).resumable())return;
        if(p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending())recover(p);
        GinsoEscapePayload.clear(p);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)disconnect(p);}
    public static void disconnect(ServerPlayer p){
        var s=SESSIONS.remove(p.getUUID());drag(p,false);
        if(s!=null&&s.active()){s.outcome=Outcome.DISCONNECTED;saveProgress(p,s);}
        /* resumable attachment survives player save; login rebuilds the session */
    }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){
        var s=session(p);
        // Cancelling clears drag and persists the retry deadline, but must never arm a return teleport.
        if(s!=null&&s.active())end(p,s,Outcome.DIMENSION,false);
    }}
    public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=session(p);if(s!=null&&s.active())end(p,s,Outcome.DEAD,false);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);}}
    public static void stop(MinecraftServer server){for(var p:server.getPlayerList().getPlayers())disconnect(p);SESSIONS.clear();}
    public static void clear(){SESSIONS.clear();}
    public static Map<String,Object> diagnostics(ServerPlayer p){
        var s=session(p);var out=new LinkedHashMap<String,Object>();var r=p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);
        out.put("phase",s==null?(r.resumable()?"RESUME":r.retryAt()>clock(p)?"COOLDOWN":"READY"):s.phase.name());
        out.put("pendingRecovery",r.pending());out.put("resumable",r.resumable());out.put("savedElapsed",r.elapsed());
        out.put("receipt",p.getData(SpiritData.PROGRESS).rewards().getOrDefault(RECEIPT,0));
        if(s!=null){out.put("session",s.id.toString());out.put("elapsedTicks",s.elapsed);out.put("waterY",s.water());out.put("submergedTicks",s.submerged);out.put("maximumSubmergedTicks",s.maximumSubmerged);out.put("outcome",s.outcome.name());out.put("cooldownTicks",s.cooldown);out.put("referenceSeconds",s.plan.referenceSeconds());out.put("limitTicks",s.plan.limitTicks());out.put("rewardLight",s.rewardedLight);out.put("rewardCell",s.rewardedCell);out.put("initialHealth",s.startingHealth);out.put("health",p.getHealth());out.put("safe",List.of(s.piece.point(s.piece.escapeSafe()).getX(),s.piece.point(s.piece.escapeSafe()).getY(),s.piece.point(s.piece.escapeSafe()).getZ()));}
        return out;
    }
    private GinsoEscape(){}
}
