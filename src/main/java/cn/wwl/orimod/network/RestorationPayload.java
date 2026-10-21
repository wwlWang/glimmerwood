package cn.wwl.orimod.network;

import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.world.RestorationData;
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

/** Explicit wire header: a future/old incompatible packet is rejected before reading session fields. */
public record RestorationPayload(boolean restored,boolean completed,Optional<UUID> session,BlockPos tree,net.minecraft.world.phys.Vec3 origin,int remaining,List<Pending> pending) implements CustomPacketPayload {
    public record Pending(WillWisp kind,String dimension,BlockPos pos){public Pending{
        if(kind==null || dimension==null || ResourceLocation.tryParse(dimension)==null || dimension.length()>128)throw new IllegalArgumentException("Invalid wisp claim");
        check(pos);pos=pos.immutable();
    }}
    public static final int WIRE_VERSION=1;
    public static final Type<RestorationPayload> TYPE=new Type<>(ResourceLocation.parse("orimod:restoration"));
    public RestorationPayload{
        if(session==null || pending==null || pending.size()>3 || remaining<0 || remaining>RestorationRules.DURATION || session.isEmpty() && remaining!=0)throw new IllegalArgumentException("Invalid restoration session");
        check(tree);tree=tree.immutable();pending=List.copyOf(pending);
        if(origin==null || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z) || Math.abs(origin.x)>30_000_000 || Math.abs(origin.z)>30_000_000 || origin.y < -2048 || origin.y>2047)throw new IllegalArgumentException("Invalid ritual origin");
        if(pending.stream().map(Pending::kind).distinct().count()!=pending.size())throw new IllegalArgumentException("Duplicate wisp claim");
    }
    private static void check(BlockPos p){if(p==null || Math.abs((long)p.getX())>30_000_000 || Math.abs((long)p.getZ())>30_000_000 || p.getY() < -2048 || p.getY()>2047)throw new IllegalArgumentException("Invalid restoration position");}
    public static final StreamCodec<RegistryFriendlyByteBuf,RestorationPayload> CODEC=StreamCodec.of((b,v)->{
        b.writeByte(WIRE_VERSION);b.writeBoolean(v.restored);b.writeBoolean(v.completed);b.writeBoolean(v.session.isPresent());v.session.ifPresent(b::writeUUID);
        b.writeBlockPos(v.tree);b.writeDouble(v.origin.x);b.writeDouble(v.origin.y);b.writeDouble(v.origin.z);b.writeVarInt(v.remaining);b.writeVarInt(v.pending.size());for(var p:v.pending){b.writeByte(p.kind.ordinal());b.writeUtf(p.dimension,128);b.writeBlockPos(p.pos);}
    },b->{try{
        if(b.readUnsignedByte()!=WIRE_VERSION)throw new DecoderException("Incompatible restoration payload: expected protocol 1");
        boolean restored=flag(b),completed=flag(b);var session=flag(b)?Optional.of(b.readUUID()):Optional.<UUID>empty();var tree=b.readBlockPos();var origin=new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble());int remaining=b.readVarInt(),n=b.readVarInt();
        if(n<0 || n>3)throw new DecoderException("Invalid pending wisp count");var pending=new ArrayList<Pending>();
        for(int i=0;i<n;i++){int kind=b.readUnsignedByte();if(kind>=WillWisp.values().length)throw new DecoderException("Unknown wisp");pending.add(new Pending(WillWisp.values()[kind],b.readUtf(128),b.readBlockPos()));}
        return new RestorationPayload(restored,completed,session,tree,origin,remaining,pending);
    }catch(IllegalArgumentException|IndexOutOfBoundsException e){throw new DecoderException("Invalid restoration payload",e);}});
    private static boolean flag(RegistryFriendlyByteBuf b){int v=b.readUnsignedByte();if(v>1)throw new DecoderException("Invalid restoration flag");return v==1;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(ServerPlayer p){var data=RestorationData.get(p.server);var s=Restoration.session(p);
        var pending=data.claims().stream().filter(c->c.owner().equals(p.getUUID())).map(c->new Pending(c.kind(),c.dimension(),c.source())).toList();
        PacketDistributor.sendToPlayer(p,new RestorationPayload(data.restored(),Restoration.completed(p),Optional.ofNullable(s).map(Restoration.Session::id),s==null?BlockPos.ZERO:s.tree(),s==null?net.minecraft.world.phys.Vec3.ZERO:s.origin(),Restoration.remaining(p),pending));
    }
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->{if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())cn.wwl.orimod.client.RestorationClient.receive(p);});}
}
