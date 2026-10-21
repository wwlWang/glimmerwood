package cn.wwl.orimod.network;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record MerchantBuyPayload(int entityId,UUID merchant,long day,UUID token,String product) implements CustomPacketPayload {
    public static final int VERSION=1;
    public static final Type<MerchantBuyPayload> TYPE=new Type<>(ResourceLocation.parse("orimod:merchant_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MerchantBuyPayload> CODEC=StreamCodec.of((b,p)->{b.writeByte(VERSION);b.writeVarInt(p.entityId);b.writeUUID(p.merchant);b.writeLong(p.day);b.writeUUID(p.token);b.writeUtf(p.product,32);},b->{
        if(b.readUnsignedByte()!=VERSION)throw new DecoderException("Incompatible merchant protocol: expected 1");
        try{return new MerchantBuyPayload(b.readVarInt(),b.readUUID(),b.readLong(),b.readUUID(),b.readUtf(32));}catch(IllegalArgumentException e){throw new DecoderException("Invalid merchant request",e);}
    });
    public MerchantBuyPayload{if(entityId<0 || merchant==null || token==null || product==null || !(product.equals("close") || cn.wwl.orimod.progression.MerchantRules.price(product)>0))throw new IllegalArgumentException("Invalid merchant/product");}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
