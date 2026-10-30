package cn.wwl.orimod.world;

import cn.wwl.orimod.config.OriServerConfig;
import cn.wwl.orimod.fx.BlightAtmosphereRules;
import cn.wwl.orimod.network.BlightAtmospherePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Bounded nearby index query, one coalesced send/player/tick; no block or chunk acquisition. */
public final class BlightAtmosphereSync {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static long generation,packets,bytes;
    private static final class Session {
        final ServerPlayer player;final net.minecraft.server.level.ServerLevel level;final long generation;
        List<BlightAtmospherePayload.Core> sent=List.of();long sentAt=-100,lastQuery=-100,revision=-1,chunk=Long.MIN_VALUE;int border;
        Optional<BlockPos> occupied=Optional.empty();
        Session(ServerPlayer p){player=p;level=p.serverLevel();generation=++BlightAtmosphereSync.generation;}
    }
    public static void tick(ServerTickEvent.Post e){for(var p:e.getServer().getPlayerList().getPlayers())sync(p,e.getServer().getTickCount());}
    public static void sync(ServerPlayer p,long now){
        var s=SESSIONS.get(p.getUUID());boolean fresh=s==null||s.player!=p||s.level!=p.serverLevel();
        if(fresh){s=new Session(p);SESSIONS.put(p.getUUID(),s);}
        var data=BlightData.get(p.serverLevel());var occupied=data.activeAt(p.position());int border=border(s.sent,p.getX(),p.getZ());
        boolean revised=s.revision!=data.atmosphereRevision(),moved=s.chunk!=p.chunkPosition().toLong();boolean urgent=fresh||!occupied.equals(s.occupied)||border!=s.border;
        if(!urgent&&!revised&&!moved&&now-s.lastQuery<20&&now-s.sentAt<100)return;
        s.lastQuery=now;s.revision=data.atmosphereRevision();s.occupied=occupied;s.chunk=p.chunkPosition().toLong();
        var cores=OriServerConfig.blightEnabled()?data.nearbySummaries(p.position()):List.<BlightAtmospherePayload.Core>of();
        urgent|=(revised||moved)&&!cores.equals(s.sent);
        if(BlightAtmosphereRules.send(now-s.sentAt,!cores.equals(s.sent),urgent)){
            var packet=new BlightAtmospherePayload(p.level().dimension().location(),s.generation,cores);
            PacketDistributor.sendToPlayer(p,packet);s.sent=cores;s.sentAt=now;packets++;
            // Same UTF-8/VarInt framing as the codec (payload body, excluding channel/envelope).
            int text=packet.dimension().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            bytes+=varBytes(text)+text+varBytes(s.generation)+1+17L*cores.size();
        }
        s.border=border(s.sent,p.getX(),p.getZ());
    }
    private static int varBytes(long n){int count=1;while((n>>>=7)!=0)count++;return count;}
    public static int border(List<BlightAtmospherePayload.Core> cores,double x,double z){int mask=0;for(int i=0;i<cores.size();i++){var c=cores.get(i);var p=BlockPos.of(c.center());if(Math.hypot(x-p.getX(),z-p.getZ())<=c.radius())mask|=1<<i;}return mask;}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    public static Map<String,Object> metrics(){return Map.of("players",SESSIONS.size(),"packets",packets,"bodyBytes",bytes);}
    public static void clear(){SESSIONS.clear();generation=packets=bytes=0;}
    private BlightAtmosphereSync(){}
}
