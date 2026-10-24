package cn.wwl.orimod.network;

import cn.wwl.orimod.world.*;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

/** Geometry once on start; small owner-only clock updates thereafter. No world block packets. */
public record GinsoEscapePayload(UUID session,GinsoEscape.Phase phase,BlockPos origin,int turn,double initialWater,
                                 int elapsed,int limit,int submerged,int cooldown,List<GinsoEscapeLayout.Box> flood) implements CustomPacketPayload {
    public static final int VERSION=1,MAX_BOXES=1024;
    public static final UUID NONE=new UUID(0,0);
    public static final Type<GinsoEscapePayload> TYPE=new Type<>(ResourceLocation.parse("orimod:ginso_escape"));
    public GinsoEscapePayload {
        if(session==null||phase==null||origin==null||turn<0||turn>3||!Double.isFinite(initialWater)||initialWater<64||initialWater>128
                ||elapsed<0||elapsed>12000||limit<0||limit>12000||submerged<0||submerged>60||cooldown<0||cooldown>100
                ||flood==null||flood.size()>MAX_BOXES||Math.abs((long)origin.getX())>30_000_000||Math.abs((long)origin.getZ())>30_000_000||origin.getY() < -2048||origin.getY()>2047)
            throw new IllegalArgumentException("Invalid Ginso escape payload");
        for(var b:flood)if(!valid(b))throw new IllegalArgumentException("Invalid Ginso flood box");
        origin=origin.immutable();flood=List.copyOf(flood);
    }
    private static boolean valid(GinsoEscapeLayout.Box b){return b!=null&&Double.isFinite(b.x0()+b.x1()+b.y0()+b.y1()+b.z0()+b.z1())
            &&b.x0()>=-64&&b.x1()<=64&&b.z0()>=-64&&b.z1()<=64&&b.y0()>=64&&b.y1()<=160&&b.x0()<b.x1()&&b.y0()<b.y1()&&b.z0()<b.z1();}
    public static final StreamCodec<RegistryFriendlyByteBuf,GinsoEscapePayload> CODEC=StreamCodec.of((b,v)->{
        b.writeByte(VERSION);b.writeUUID(v.session);b.writeByte(v.phase.ordinal());b.writeBlockPos(v.origin);b.writeByte(v.turn);b.writeDouble(v.initialWater);
        b.writeVarInt(v.elapsed);b.writeVarInt(v.limit);b.writeVarInt(v.submerged);b.writeVarInt(v.cooldown);b.writeVarInt(v.flood.size());
        for(var x:v.flood){b.writeDouble(x.x0());b.writeDouble(x.y0());b.writeDouble(x.z0());b.writeDouble(x.x1());b.writeDouble(x.y1());b.writeDouble(x.z1());}
    },b->{try{
        if(b.readUnsignedByte()!=VERSION)throw new DecoderException("Incompatible Ginso escape protocol; expected 1");
        var id=b.readUUID();int phase=b.readUnsignedByte();if(phase>=GinsoEscape.Phase.values().length)throw new DecoderException("Unknown Ginso phase");
        var origin=b.readBlockPos();int turn=b.readUnsignedByte();double water=b.readDouble();int age=b.readVarInt(),limit=b.readVarInt(),sub=b.readVarInt(),cool=b.readVarInt(),n=b.readVarInt();
        if(n<0||n>MAX_BOXES)throw new DecoderException("Invalid Ginso box count");var boxes=new ArrayList<GinsoEscapeLayout.Box>(n);
        for(int i=0;i<n;i++)boxes.add(new GinsoEscapeLayout.Box(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
        return new GinsoEscapePayload(id,GinsoEscape.Phase.values()[phase],origin,turn,water,age,limit,sub,cool,boxes);
    }catch(IllegalArgumentException|IndexOutOfBoundsException e){throw new DecoderException("Invalid Ginso escape payload",e);}});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(ServerPlayer p,GinsoEscape.Session s,boolean geometry){PacketDistributor.sendToPlayer(p,new GinsoEscapePayload(s.id,s.phase,s.piece.origin(),s.piece.entrance(),s.plan.startWater(),s.elapsed,s.plan.limitTicks(),s.submerged,s.cooldown,geometry?s.layout.flood():List.of()));}
    public static void clear(ServerPlayer p){PacketDistributor.sendToPlayer(p,new GinsoEscapePayload(NONE,GinsoEscape.Phase.READY,BlockPos.ZERO,0,90,0,0,0,0,List.of()));}
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->{
        if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())cn.wwl.orimod.client.GinsoEscapeClient.receive(p);
    });}
}
