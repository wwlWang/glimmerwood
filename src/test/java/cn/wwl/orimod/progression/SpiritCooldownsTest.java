package cn.wwl.orimod.progression;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritCooldownsTest {
    @Test void legacySaveWithoutFieldsIsReady() throws ReflectiveOperationException {
        var decoded=SpiritCooldowns.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{}")).getOrThrow();
        for(var field:SpiritCooldowns.class.getFields())if(field.getType()==long.class)assertEquals(0L,field.getLong(decoded),field.getName());
    }
    @Test void everyFunctionalDeadlineRoundTripsIndependently() throws ReflectiveOperationException {
        var original=new SpiritCooldowns();long value=100_000;
        for(var field:SpiritCooldowns.class.getFields())if(field.getType()==long.class)field.setLong(original,value+=37);
        var json=SpiritCooldowns.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow();
        var decoded=SpiritCooldowns.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        for(var field:SpiritCooldowns.class.getFields())if(field.getType()==long.class)assertEquals(field.getLong(original),field.getLong(decoded),field.getName());
    }
    @Test void partialOlderAttachmentKeepsKnownDeadlineAndDefaultsNewFields(){
        var decoded=SpiritCooldowns.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"wellReadyAt\":123456,\"futureField\":7}")).getOrThrow();
        assertEquals(123456,decoded.wellReadyAt);assertEquals(0,decoded.spearReadyAt);assertEquals(0,decoded.dashReadyAt);
    }
}
