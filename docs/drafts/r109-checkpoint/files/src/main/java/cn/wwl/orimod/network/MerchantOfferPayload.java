package cn.wwl.orimod.network;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
public record MerchantOfferPayload(int entityId,UUID merchant,long day,UUID token,int job,int balance,List<Offer> offers,String message,boolean busy) implements CustomPacketPayload {
    public record Offer(String id,int price,boolean available){public Offer{if(price<0 || cn.wwl.orimod.progression.MerchantRules.price(id)!=price)throw new IllegalArgumentException("Invalid offer price");}}
    public static final Type<MerchantOfferPayload> TYPE=new Type<>(ResourceLocation.parse("orimod:merchant_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MerchantOfferPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeByte(2);b.writeVarInt(p.entityId);b.writeUUID(p.merchant);b.writeLong(p.day);b.writeUUID(p.token);b.writeByte(p.job);b.writeVarInt(p.balance);b.writeByte(p.offers.size());
        for(var o:p.offers){b.writeUtf(o.id,32);b.writeVarInt(o.price);b.writeBoolean(o.available);}b.writeUtf(p.message,32);b.writeBoolean(p.busy);
    },b->{
        if(b.readUnsignedByte()!=2)throw new DecoderException("Incompatible merchant protocol: expected 2");
        try{int id=b.readVarInt();var merchant=b.readUUID();long day=b.readLong();var token=b.readUUID();int job=b.readUnsignedByte(),balance=b.readVarInt(),n=b.readUnsignedByte();if(n>4)throw new DecoderException("Too many merchant offers");var offers=new ArrayList<Offer>();for(int i=0;i<n;i++)offers.add(new Offer(b.readUtf(32),b.readVarInt(),b.readBoolean()));return new MerchantOfferPayload(id,merchant,day,token,job,balance,offers,b.readUtf(32),b.readBoolean());}catch(IllegalArgumentException e){throw new DecoderException("Invalid merchant offer",e);}
    });
    public MerchantOfferPayload{if(entityId<0 || job<0 || job>1 || balance<0 || offers.size()>(job==1?4:3) || offers.stream().map(Offer::id).distinct().count()!=offers.size() || message.length()>32)throw new IllegalArgumentException("Invalid offer");offers=List.copyOf(offers);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("2");r.playToServer(MerchantBuyPayload.TYPE,MerchantBuyPayload.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)cn.wwl.orimod.progression.MerchantTrading.buy(player,p);});
        r.playToClient(TYPE,CODEC,(p,c)->{if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())cn.wwl.orimod.client.MerchantScreen.receive(p);});
    }
}
