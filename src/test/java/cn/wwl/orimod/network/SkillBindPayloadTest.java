package cn.wwl.orimod.network;
import cn.wwl.orimod.progression.WheelSkill;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class SkillBindPayloadTest {
    @Test void allSkillsRoundTrip(){for(var s:WheelSkill.values())for(int i=0;i<3;i++)MerchantPayloadTest.roundTrip(SkillBindPayload.CODEC,new SkillBindPayload(i,s));}
    @ParameterizedTest @ValueSource(ints={0,2,255}) void incompatibleVersionRejected(int version){var b=MerchantPayloadTest.buffer();try{b.writeByte(version);assertTrue(assertThrows(DecoderException.class,()->SkillBindPayload.CODEC.decode(b)).getMessage().contains("expected 1"));}finally{b.release();}}
    @ParameterizedTest @ValueSource(ints={3,255}) void invalidSlotRejected(int slot){var b=MerchantPayloadTest.buffer();try{b.writeByte(1);b.writeByte(slot);b.writeUtf("flash");assertThrows(DecoderException.class,()->SkillBindPayload.CODEC.decode(b));}finally{b.release();}}
    @ParameterizedTest @ValueSource(strings={"","dash","../flash"}) void invalidSkillRejected(String id){var b=MerchantPayloadTest.buffer();try{b.writeByte(1);b.writeByte(0);b.writeUtf(id);assertThrows(DecoderException.class,()->SkillBindPayload.CODEC.decode(b));}finally{b.release();}}
}
