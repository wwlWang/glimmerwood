package cn.wwl.orimod.network;

import cn.wwl.orimod.fx.BlightAtmosphereRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

/** Dimension + generation and at most four summaries. Packed centre is the stable core ID. */
public record BlightAtmospherePayload(ResourceLocation dimension,long generation,List<Core> cores) implements CustomPacketPayload {
    public record Core(long center,float radius,boolean purifying,float progress){
        public Core{if(!Float.isFinite(radius)||radius<0||radius>96||!Float.isFinite(progress)||progress<0||progress>1||!purifying&&progress!=0)throw new IllegalArgumentException("Invalid blight core summary");}
    }
    public BlightAtmospherePayload{
        Objects.requireNonNull(dimension);cores=List.copyOf(cores);
        if(generation<0||cores.size()>BlightAtmosphereRules.LIMIT||cores.stream().map(Core::center).distinct().count()!=cores.size())throw new IllegalArgumentException("Invalid blight summaries");
    }
    public static final Type<BlightAtmospherePayload> TYPE=new Type<>(ResourceLocation.parse("orimod:blight_atmosphere"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BlightAtmospherePayload> CODEC=StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension);b.writeVarLong(p.generation);b.writeByte(p.cores.size());
        for(var c:p.cores){b.writeLong(c.center);b.writeFloat(c.radius);b.writeByte(c.purifying?1:0);b.writeFloat(c.progress);}
    },b->{try{
        var dimension=b.readResourceLocation();long generation=b.readVarLong();int count=b.readUnsignedByte();
        if(count>4)throw new DecoderException("Too many blight cores");var cores=new ArrayList<Core>(count);
        for(int i=0;i<count;i++){long center=b.readLong();float radius=b.readFloat();int phase=b.readUnsignedByte();if(phase>1)throw new DecoderException("Invalid blight phase");cores.add(new Core(center,radius,phase==1,b.readFloat()));}
        return new BlightAtmospherePayload(dimension,generation,cores);
    }catch(IllegalArgumentException|IndexOutOfBoundsException e){throw new DecoderException("Invalid blight atmosphere",e);}});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->{
        if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())cn.wwl.orimod.client.fx.BlightAtmosphereClient.receive(p);
    });}
}
