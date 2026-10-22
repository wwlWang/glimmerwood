package cn.wwl.orimod.network;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public record WellSelectPayload(BlockPos from,BlockPos to,String dimension) implements CustomPacketPayload {
    public static final Type<WellSelectPayload> TYPE=new Type<>(ResourceLocation.parse("orimod:well_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WellSelectPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeByte(1);b.writeBlockPos(p.from);b.writeBlockPos(p.to);b.writeResourceLocation(ResourceLocation.parse(p.dimension));
    },b->{if(b.readUnsignedByte()!=1)throw new DecoderException("Incompatible well protocol: expected 1");return new WellSelectPayload(b.readBlockPos(),b.readBlockPos(),b.readResourceLocation().toString());});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("1");
        r.playToServer(TYPE,CODEC,(p,c)->{if(c.player() instanceof ServerPlayer s)cn.wwl.orimod.world.SpiritWell.travel(s,p.from,p.to,p.dimension);});
        r.playToClient(WellOfferPayload.TYPE,WellOfferPayload.CODEC,(p,c)->{
            if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())cn.wwl.orimod.client.WellScreen.receive(p);
        });
    }
}
