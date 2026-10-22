package cn.wwl.orimod.network;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record WellOfferPayload(BlockPos from,String dimension) implements CustomPacketPayload {
    public static final Type<WellOfferPayload> TYPE=new Type<>(ResourceLocation.parse("orimod:well_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WellOfferPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeByte(1);b.writeBlockPos(p.from);b.writeResourceLocation(ResourceLocation.parse(p.dimension));
    },b->{if(b.readUnsignedByte()!=1)throw new DecoderException("Incompatible well protocol: expected 1");return new WellOfferPayload(b.readBlockPos(),b.readResourceLocation().toString());});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
