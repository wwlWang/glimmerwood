package cn.wwl.orimod.progression;

import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.RestorationData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.*;

public final class WillWisps {
    private WillWisps(){}
    public static void offer(ServerPlayer p,WillWisp kind,ServerLevel level,BlockPos source){
        if((p.getData(SpiritData.PROGRESS).wisps()&kind.bit)!=0)return;
        var c=RestorationData.get(p.server).offer(p.getUUID(),kind,level.dimension().location().toString(),source);
        materialize(level,c);sync(p);
    }
    public static boolean valid(WillWispEntity e){
        if(!(e.level() instanceof ServerLevel level) || e.owner()==null)return false;
        var c=RestorationData.get(level.getServer()).claim(e.owner(),e.kind());
        return c!=null && c.entity().equals(e.getUUID()) && c.source().equals(e.source()) && c.dimension().equals(level.dimension().location().toString());
    }
    public static boolean pickup(ServerPlayer p,WillWispEntity e){
        if(!p.isAlive() || p.isSpectator() || !p.getUUID().equals(e.owner()) || p.level()!=e.level() || !valid(e)
                || !p.getBoundingBox().inflate(.75).intersects(e.getBoundingBox()))return false;
        boolean fresh=(p.getData(SpiritData.PROGRESS).wisps()&e.kind().bit)==0;
        give(p,e.kind().bit);e.discard();
        if(fresh){p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.orimod.wisp.collected",net.minecraft.network.chat.Component.translatable("wisp.orimod."+e.kind().id())));
            cn.wwl.orimod.fx.SpiritFx.emit(p.serverLevel(),cn.wwl.orimod.fx.SpiritFxKind.WISP_COLLECT,p.getX(),p.getY()+1,p.getZ(),p);}
        return fresh;
    }
    public static void give(ServerPlayer p,int mask){
        var progress=p.getData(SpiritData.PROGRESS);p.setData(SpiritData.PROGRESS,progress.withWisps(progress.wisps()|mask));
        var data=RestorationData.get(p.server);for(var w:WillWisp.values())if((mask&w.bit)!=0)data.remove(p.getUUID(),w);sync(p);
    }
    public static boolean clear(ServerPlayer p){Restoration.cancel(p);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withWisps(0));for(var w:WillWisp.values())RestorationData.get(p.server).remove(p.getUUID(),w);sync(p);return true;}
    public static void purified(ServerPlayer p,ServerLevel level,BlockPos center){if(WillWisp.purifications(p.getData(SpiritData.PROGRESS))>=3)offer(p,WillWisp.EARTH,level,center);}
    private static void materialize(ServerLevel level,RestorationData.Claim c){
        if(!c.dimension().equals(level.dimension().location().toString()) || level.getChunkSource().getChunkNow(c.source().getX()>>4,c.source().getZ()>>4)==null || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(c.source().getX()>>4,c.source().getZ()>>4)) || level.getEntity(c.entity())!=null)return;
        var e=OriMobs.WILL_WISP.get().create(level);if(e!=null){e.bind(c);level.addFreshEntity(e);}
    }
    /** Missing visual entities can be repaired; fixed UUIDs and the ledger prevent duplicate claims. No chunk tickets. */
    public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level && level.getGameTime()%40==0){
        for(var c:RestorationData.get(level.getServer()).claims()){
            var p=level.getServer().getPlayerList().getPlayer(c.owner());
            if(p!=null && (p.getData(SpiritData.PROGRESS).wisps()&c.kind().bit)!=0)RestorationData.get(level.getServer()).remove(c.owner(),c.kind());
            else materialize(level,c);
        }
    }}
    public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){Restoration.cleanReceipt(p);sync(p);}}
    public static void sync(ServerPlayer p){cn.wwl.orimod.network.RestorationPayload.send(p);}
    public static java.util.List<Map<String,Object>> pending(ServerPlayer p){return RestorationData.get(p.server).claims().stream().filter(c->c.owner().equals(p.getUUID())).map(c->Map.<String,Object>of("kind",c.kind().id(),"dimension",c.dimension(),"position",new int[]{c.source().getX(),c.source().getY(),c.source().getZ()})).toList();}
}
