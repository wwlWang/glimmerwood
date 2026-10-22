package cn.wwl.orimod.network;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FeatherWellPayloadTest {
    @Test void roundTrips(){MerchantPayloadTest.roundTrip(WellOfferPayload.CODEC,new WellOfferPayload(BlockPos.ZERO,"minecraft:overworld"));MerchantPayloadTest.roundTrip(WellSelectPayload.CODEC,new WellSelectPayload(BlockPos.ZERO,new BlockPos(30000000,200,-4),"minecraft:the_nether"));}
    @ParameterizedTest @ValueSource(ints={0,2,255}) void wrongVersionsRejected(int version){for(var codec:List.of(WellOfferPayload.CODEC,WellSelectPayload.CODEC)){var b=MerchantPayloadTest.buffer();try{b.writeByte(version);assertTrue(assertThrows(DecoderException.class,()->codec.decode(b)).getMessage().contains("expected 1"));}finally{b.release();}}}
}
