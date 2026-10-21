package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PlatformRulesTest {
    @ParameterizedTest @EnumSource(PlatformRules.Source.class) void radiusIncludesBoundaryOnly(PlatformRules.Source source) {
        double r=source.radius;
        assertTrue(PlatformRules.within(source,r*r));assertTrue(PlatformRules.within(source,0));
        assertFalse(PlatformRules.within(source,r*r+.001));assertFalse(PlatformRules.within(source,-1));
        assertFalse(PlatformRules.within(source,Double.NaN));assertFalse(PlatformRules.within(source,Double.POSITIVE_INFINITY));
    }
    @Test void renewalNeverShortensAndRetainsHundredTicks() {
        assertEquals(110,PlatformRules.renew(Long.MIN_VALUE,10));assertEquals(120,PlatformRules.renew(110,20));assertEquals(300,PlatformRules.renew(300,20));
    }
    @ParameterizedTest @ValueSource(ints={1,5,10,20,30}) void warningKeepsLastThirtyTicks(int remaining) {
        assertTrue(PlatformRules.fading(100,100+remaining));assertTrue(PlatformRules.next(false,100,100+remaining)<=5);
    }
    @Test void warningBoundaryAndFiveTickModelPhases() {
        assertFalse(PlatformRules.fading(69,100));assertTrue(PlatformRules.fading(70,100));assertFalse(PlatformRules.fading(100,100));
        for(int now=70;now<95;now+=5)assertNotEquals(PlatformRules.blink(now,100),PlatformRules.blink(now+5,100));
        assertEquals(70,PlatformRules.next(false,0,100));assertEquals(5,PlatformRules.next(true,0,100));assertEquals(1,PlatformRules.next(true,99,100));
    }
    @Test void OccupancyNeverForcesMaterializationEvenAfterManyRetries() {
        for(int now=0;now<1000;now+=5)assertFalse(PlatformRules.solidify(true,now,now+100));
        assertTrue(PlatformRules.solidify(false,99,100));assertFalse(PlatformRules.solidify(false,100,100));
    }
    @Test void savedDeadlinesAndChunkIndexRoundTripThenRemoveToZero() {
        var data=new PlatformTimers();long a=new BlockPos(1,2,3).asLong(),b=new BlockPos(-33,70,2).asLong();
        data.put(a,new PlatformTimers.Lease(100,230));data.put(b,new PlatformTimers.Lease(Long.MIN_VALUE,400));
        var saved=PlatformTimers.load(data.save(new CompoundTag(),null),null);
        assertEquals(2,saved.size());assertEquals(data.get(a),saved.get(a));assertEquals(data.get(b),saved.get(b));
        assertEquals(java.util.Set.of(a),saved.positions(net.minecraft.world.level.ChunkPos.asLong(0,0)));
        saved.remove(a);saved.remove(a);saved.remove(b);assertEquals(0,saved.size());assertTrue(saved.positions(0).isEmpty());
    }
    @Test void expiredUnloadedEntriesAreRetiredAndRenewalDoesNotLeaveExpiryIndexGarbage() {
        var data=new PlatformTimers();long a=new BlockPos(1,2,3).asLong(),b=new BlockPos(1000,70,2).asLong();
        data.put(a,new PlatformTimers.Lease(100,100));data.put(b,new PlatformTimers.Lease(150,150));
        data.put(a,new PlatformTimers.Lease(200,200));data.pruneExpired(100);assertEquals(2,data.size());
        data.pruneExpired(150);assertNull(data.get(b));assertNotNull(data.get(a));
        data.pruneExpired(200);assertEquals(0,data.size());assertTrue(data.positions(0).isEmpty());
    }
    @Test void ghostAndBlinkModelsUseDifferentLayersButNotDifferentLightLevels() throws Exception {
        var ghost=model("ghost");
        assertEquals("minecraft:translucent",ghost.get("render_type").getAsString());
        for(String variant:java.util.List.of("lit","blink")) {
            var solid=model(variant);
            assertEquals("minecraft:solid",solid.get("render_type").getAsString());
        }
    }
    private static com.google.gson.JsonObject model(String mode) throws Exception {
        try(var in=PlatformRulesTest.class.getResourceAsStream("/assets/orimod/models/block/spirit_platform_"+mode+".json")) {
            assertNotNull(in);return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
