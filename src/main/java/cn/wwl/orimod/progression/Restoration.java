package cn.wwl.orimod.progression;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Unsaved sessions: shutdown/disconnect never leave invulnerability or a movement lock in player data. */
public final class Restoration {
    public static final String RECEIPT="restoration/epoch";
    public record Session(UUID id,ServerLevel level,BlockPos tree,Vec3 origin,long start){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    public static Session session(ServerPlayer p){return SESSIONS.get(p.getUUID());}
    public static boolean active(ServerPlayer p){return session(p)!=null;}
    public static int remaining(ServerPlayer p){var s=session(p);return s==null?0:(int)Math.clamp(RestorationRules.DURATION-(p.serverLevel().getGameTime()-s.start),0,RestorationRules.DURATION);}
    public static boolean completed(ServerPlayer p){return p.getData(SpiritData.PROGRESS).rewards().getOrDefault(RECEIPT,0)==RestorationData.get(p.server).epoch();}
    public static boolean start(ServerPlayer p,BlockPos tree){
        var progress=p.getData(SpiritData.PROGRESS);
        String refusal=RestorationRules.refusal(p.isAlive() && !p.isSpectator(),SpiritData.isAwakened(p)&&SpiritData.isSpirit(p),progress.wisps(),
                completed(p),active(p));
        if(p.serverLevel().getChunkSource().getChunkNow(tree.getX()>>4,tree.getZ()>>4)==null || p.position().distanceToSqr(Vec3.atCenterOf(tree))>36)return false;
        if(!p.serverLevel().getBlockState(tree).is(OriBlocks.GREAT_TREE_HEART)){
            p.displayClientMessage(Component.translatable("message.orimod.restoration.great_tree"),true);return false;
        }
        if(refusal!=null){p.displayClientMessage(Component.translatable("message.orimod.restoration."+refusal),true);return false;}
        p.stopRiding();p.stopUsingItem();cn.wwl.orimod.combat.Flash.stop(p);cn.wwl.orimod.combat.SpiritCombat.cancel(p,"restoration");
        cn.wwl.orimod.movement.SpiritMovement.teleported(p);
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        var origin=RestorationViewpoint.choose(p,tree);
        SESSIONS.put(p.getUUID(),new Session(UUID.randomUUID(),p.serverLevel(),tree.immutable(),origin,p.level().getGameTime()));
        if(!origin.equals(p.position()))p.connection.teleport(origin.x,origin.y,origin.z,p.getYRot(),p.getXRot());
        p.getData(SpiritData.ENERGY_STATE).lines.pending=null;
        WillWisps.sync(p);return true;
    }
    public static void cancel(ServerPlayer p){var old=SESSIONS.remove(p.getUUID());if(old!=null){RestorationBloom.canopy(old.level,old.tree);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;WillWisps.sync(p);}}
    public static boolean skip(ServerPlayer p,String id){var s=session(p);if(s==null || !s.id.toString().replace("-", "").equals(id))return false;return finish(p);}
    private static boolean valid(ServerPlayer p,Session s){return p.isAlive() && !p.isSpectator() && SpiritData.isSpirit(p) && p.serverLevel()==s.level
            && p.getData(SpiritData.PROGRESS).wisps()==7 && s.level.getChunkSource().getChunkNow(s.tree.getX()>>4,s.tree.getZ()>>4)!=null
            && s.level.getBlockState(s.tree).is(OriBlocks.GREAT_TREE_HEART);}
    public static boolean finish(ServerPlayer p){
        var s=session(p);if(s==null)return false;
        if(!valid(p,s) || completed(p)){cancel(p);return false;}
        var data=RestorationData.get(p.server);var progress=p.getData(SpiritData.PROGRESS);
        var rewards=new HashMap<>(progress.rewards());rewards.put(RECEIPT,data.epoch());p.setData(SpiritData.PROGRESS,progress.withRewards(rewards));
        boolean first=!data.restored();data.complete();if(first)RestorationBloom.refresh(p.server);
        cancel(p);SpiritProgression.grantLight(p,RestorationRules.REWARD);
        var advancement=p.server.getAdvancements().get(ResourceLocation.parse("orimod:restoration"));if(advancement!=null)p.getAdvancements().award(advancement,"restored");
        CompanionLines.offer(p,CompanionLines.Line.RESTORATION);
        cn.wwl.orimod.fx.SpiritFx.emit(s.level,cn.wwl.orimod.fx.SpiritFxKind.RESTORATION,s.tree.getX()+.5,s.tree.getY()+1,s.tree.getZ()+.5,p);
        p.sendSystemMessage(Component.translatable("message.orimod.restoration.reward",RestorationRules.REWARD));
        for(var player:p.server.getPlayerList().getPlayers())WillWisps.sync(player);
        return true;
    }
    public static void tick(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p){var s=session(p);if(s==null)return;
        if(!valid(p,s)){cancel(p);return;}
        // Position only: rotation remains entirely under player control.
        if(p.position().distanceToSqr(s.origin)>1.0e-6)p.connection.teleport(s.origin.x,s.origin.y,s.origin.z,p.getYRot(),p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        long age=p.level().getGameTime()-s.start;
        if(age>=140 && age<=220 && age%20==0)RestorationBloom.canopy(s.level,s.tree);
        if(remaining(p)==0)finish(p);else if(p.level().getGameTime()%20==0)WillWisps.sync(p);
    }}
    public static void hurt(LivingIncomingDamageEvent e){if(e.getEntity() instanceof ServerPlayer p && active(p))e.setCanceled(true);}
    public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){cancel(p);WillWisps.sync(p);}}
    public static void reset(MinecraftServer server){
        for(var p:server.getPlayerList().getPlayers())cancel(p);
        RestorationData.get(server).reset();RestorationBloom.refresh(server);
        for(var p:server.getPlayerList().getPlayers()){cleanReceipt(p);WillWisps.sync(p);}
    }
    public static void cleanReceipt(ServerPlayer p){
        var progress=p.getData(SpiritData.PROGRESS);if(!progress.rewards().containsKey(RECEIPT) || completed(p))return;
        var r=new HashMap<>(progress.rewards());r.remove(RECEIPT);r.remove("line/restoration");p.setData(SpiritData.PROGRESS,progress.withRewards(r));
        var a=p.server.getAdvancements().get(ResourceLocation.parse("orimod:restoration"));if(a!=null)for(var c:a.value().criteria().keySet())p.getAdvancements().revoke(a,c);
    }
    /** Temporary crown bands during seconds 7–11. Cancellation schedules the same leaves back to normal. */
    public static boolean canopyChunk(ServerLevel level,int x,int z){
        for(var s:SESSIONS.values())if(s.level==level && level.getGameTime()-s.start>=140
                && x*16<=s.tree.getX()+40 && x*16+15>=s.tree.getX()-40
                && z*16<=s.tree.getZ()+40 && z*16+15>=s.tree.getZ()-40)return true;
        return false;
    }
    public static boolean bloomingAt(ServerLevel level,BlockPos pos){
        for(var s:SESSIONS.values())if(s.level==level){long age=level.getGameTime()-s.start;
            if(age<140 || Math.abs(pos.getX()-s.tree.getX())>40 || Math.abs(pos.getZ()-s.tree.getZ())>40)continue;
            int height=(int)Math.min(46,12+(age-140)/20*9);
            if(pos.getY()>=s.tree.getY() && pos.getY()<=s.tree.getY()+height)return true;
        }return false;
    }
    public static void clear(){SESSIONS.clear();}
    private Restoration(){}
}
