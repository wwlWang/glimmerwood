package cn.wwl.orimod.world;

import cn.wwl.orimod.config.VisualQuality;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.progression.SpiritProgression;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GloomTideRulesTest {
    @ParameterizedTest @CsvSource({"12999,false", "13000,true", "22999,true", "23000,false", "37000,true", "0,false"})
    void nightBoundaries(long t, boolean expected) { assertEquals(expected, GloomTideRules.isTideNight(t, 4, Difficulty.NORMAL, true, true)); }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,5,6,7})
    void onlyNewMoon(int moon) { assertFalse(GloomTideRules.isTideNight(18000, moon, Difficulty.NORMAL, true, true)); }
    @Test void allServerSwitchesAreRequired() {
        assertFalse(GloomTideRules.isTideNight(18000, 4, Difficulty.PEACEFUL, true, true));
        assertFalse(GloomTideRules.isTideNight(18000, 4, Difficulty.HARD, false, true));
        assertFalse(GloomTideRules.isTideNight(18000, 4, Difficulty.HARD, true, false));
        assertTrue(GloomTideRules.allowed(18000, Difficulty.EASY, true, true));
    }
    @ParameterizedTest @CsvSource({"255.999,false", "256,true", "784,true", "784.001,false", "0,false"})
    void horizontalRing(double squared, boolean expected) { assertEquals(expected, GloomTideRules.spawnDistance(squared)); }
    @Test void nearbyCapIsStrict() { assertTrue(GloomTideRules.roomForMob(7,8)); assertFalse(GloomTideRules.roomForMob(8,8)); assertFalse(GloomTideRules.roomForMob(9,8)); }
    @Test void defensesUseCorrectInclusiveRadii() {
        double absent = Double.POSITIVE_INFINITY;
        assertTrue(GloomTideRules.defended(100,absent,absent)); assertFalse(GloomTideRules.defended(100.01,absent,absent));
        assertTrue(GloomTideRules.defended(absent,1024,absent)); assertFalse(GloomTideRules.defended(absent,1024.01,absent));
        assertTrue(GloomTideRules.defended(absent,absent,1024)); assertFalse(GloomTideRules.defended(absent,absent,1024.01));
        assertFalse(GloomTideRules.defended(-1,Double.NaN,absent));
    }
    @Test void sameDayReplayCannotSettleTwice() {
        var n = new GloomTideRules.NightLedger(); n.begin(4,10); n.kills=5;
        assertEquals(50,n.finish()); n.begin(4,30); n.kills=9; assertEquals(0,n.finish());
    }
    @Test void earlierTimelineStartsNewNight() {
        var n = new GloomTideRules.NightLedger(); n.begin(4,10); n.kills=5; assertEquals(50,n.finish());
        n.begin(3,30); assertEquals(0,n.kills); n.kills=5; assertEquals(50,n.finish());
    }
    @Test void fewerThanFiveHasNoRewardAndNextDayResetsCount() {
        var n = new GloomTideRules.NightLedger(); n.begin(4,10); n.kills=4; assertEquals(0,n.finish());
        n.begin(5,30); assertEquals(0,n.kills); assertEquals(30,n.nextWaveAt);
    }
    @Test void perTickChunkOwnershipAllowsOnlyOnePlayersWave() {
        var claims = new GloomTideRules.WaveClaims(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        assertTrue(claims.available(1,50,a)); claims.claim(1,50,a);
        assertTrue(claims.available(1,50,a)); assertFalse(claims.available(1,50,b));
        assertTrue(claims.available(1,51,b)); assertTrue(claims.available(2,50,b));
    }
    @ParameterizedTest @CsvSource({"10,2", "20,3", "30,5", "80,12"})
    void tideOrbsRoundHalfUp(float health, int expected) { assertEquals(expected,SpiritProgression.lightFor(health,true)); }
    @Test void visualFadeTakesFiveSecondsInBothDirections() {
        float f=0; for(int i=0;i<99;i++) f=GloomTideVisuals.approach(f,.35f);
        assertTrue(f<.35f); f=GloomTideVisuals.approach(f,.35f); assertEquals(.35f,f,1e-6);
        for(int i=0;i<100;i++) f=GloomTideVisuals.approach(f,0); assertEquals(0,f,1e-6);
    }
    @Test void activePacketBeforeReturningToOverworldSurvivesBind() {
        var fade=new GloomTideVisuals.Fade();fade.bind(false);fade.receive(true,.35f);
        assertEquals(0,fade.target());fade.bind(true);assertEquals(.35f,fade.target());
        for(int i=0;i<100;i++)fade.tick();assertEquals(.35f,fade.value(1),1e-6);
    }
    @Test void activePacketAfterReturningToOverworldAlsoFades() {
        var fade=new GloomTideVisuals.Fade();fade.receive(false,0);fade.bind(true);fade.receive(true,.35f);
        fade.tick();assertTrue(fade.value(1)>0 && fade.value(1)<.35f);
        fade.bind(false);assertEquals(0,fade.target());assertEquals(0,fade.value(1));
    }
    @Test void disconnectClearsServerStateAndSameDimensionReplacementKeepsIt() {
        var fade=new GloomTideVisuals.Fade();fade.bind(true);fade.receive(true,.35f);fade.tick();
        fade.bind(true);assertEquals(.35f,fade.target());assertEquals(0,fade.value(1));
        fade.disconnect();fade.bind(true);assertEquals(0,fade.target());
    }
    @ParameterizedTest @EnumSource(VisualQuality.class)
    void particlesAreBoundedAndOffLeavesOnlyFog(VisualQuality quality) {
        assertEquals(0,FxBudget.tideParticles(quality,false));
        assertTrue(FxBudget.tideParticles(quality,true)>0 && FxBudget.tideParticles(quality,true)<=4);
    }
}
