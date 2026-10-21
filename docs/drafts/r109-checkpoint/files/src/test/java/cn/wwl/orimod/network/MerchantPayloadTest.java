package cn.wwl.orimod.network;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MerchantPayloadTest {
    static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){var b=buffer();try{codec.encode(b,value);assertEquals(value,codec.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}}
    @Test void requestHasNoPriceAndRoundTrips(){roundTrip(MerchantBuyPayload.CODEC,new MerchantBuyPayload(42,UUID.randomUUID(),12,UUID.randomUUID(),"tree"));assertEquals(List.of("entityId","merchant","day","token","product"),Arrays.stream(MerchantBuyPayload.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());}
    @Test void offerRoundTrip(){roundTrip(MerchantOfferPayload.CODEC,new MerchantOfferPayload(42,UUID.randomUUID(),12,UUID.randomUUID(),1,200,List.of(new MerchantOfferPayload.Offer("ruin",40,true),new MerchantOfferPayload.Offer("tree",60,true),new MerchantOfferPayload.Offer("blight",50,true)),"",false));}
    @ParameterizedTest @ValueSource(ints={0,3,255}) void rejectsOtherMerchantVersions(int version){for(var codec:List.of(MerchantBuyPayload.CODEC,MerchantOfferPayload.CODEC)){var b=buffer();try{b.writeByte(version);assertTrue(assertThrows(DecoderException.class,()->codec.decode(b)).getMessage().contains("expected "));}finally{b.release();}}}
    @ParameterizedTest @ValueSource(strings={"","unknown","../tree"}) void badProductRejectedOnWire(String product){var b=buffer();try{b.writeByte(1);b.writeVarInt(1);b.writeUUID(UUID.randomUUID());b.writeLong(0);b.writeUUID(UUID.randomUUID());b.writeUtf(product);assertThrows(DecoderException.class,()->MerchantBuyPayload.CODEC.decode(b));}finally{b.release();}}
    @ParameterizedTest @ValueSource(ints={-1,0,59,61}) void offerRejectsForgedPrice(int price){var b=offerHeader(1);try{b.writeUtf("tree");b.writeVarInt(price);b.writeBoolean(true);b.writeUtf("");b.writeBoolean(false);assertThrows(DecoderException.class,()->MerchantOfferPayload.CODEC.decode(b));}finally{b.release();}}
    @Test void tooManyOffersRejected(){var b=offerHeader(5);try{assertThrows(DecoderException.class,()->MerchantOfferPayload.CODEC.decode(b));}finally{b.release();}}
    @Test void unknownNegativeOfferAndDuplicateIdsRejected(){assertThrows(IllegalArgumentException.class,()->new MerchantOfferPayload.Offer("oops",-1,true));var o=new MerchantOfferPayload.Offer("tree",60,true);assertThrows(IllegalArgumentException.class,()->new MerchantOfferPayload(1,UUID.randomUUID(),0,UUID.randomUUID(),1,0,List.of(o,o),"",false));}
    private static RegistryFriendlyByteBuf offerHeader(int count){var b=buffer();b.writeByte(2);b.writeVarInt(1);b.writeUUID(UUID.randomUUID());b.writeLong(0);b.writeUUID(UUID.randomUUID());b.writeByte(1);b.writeVarInt(100);b.writeByte(count);return b;}
    @ParameterizedTest @ValueSource(strings={"forest_spring","creek_ruins","cave_altar","glades_outpost","spirit_tree","blighted_grounds","spirit_shrine"}) void guideRoundTrip(String kind){roundTrip(RuinGuidePayload.CODEC,new RuinGuidePayload("minecraft:overworld",new BlockPos(-100,64,50),kind));}
    @Test void guideRejectsOldProtocolAndOldDecoderRejectsNew(){var b=buffer();try{b.writeUtf("minecraft:overworld");b.writeBlockPos(BlockPos.ZERO);b.writeUtf("forest_spring");assertTrue(assertThrows(DecoderException.class,()->RuinGuidePayload.CODEC.decode(b)).getMessage().contains("expected 3"));b.clear();RuinGuidePayload.CODEC.encode(b,new RuinGuidePayload("minecraft:overworld",BlockPos.ZERO,"glades_outpost"));assertThrows(RuntimeException.class,()->{b.readUtf(128);b.readBlockPos();b.readUtf(32);});}finally{b.release();}}
}
