package cn.wwl.orimod.progression;

import cn.wwl.orimod.fx.CompanionLineDisplay;
import cn.wwl.orimod.progression.CompanionLines.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CompanionLinesTest {
    @ParameterizedTest @EnumSource(Line.class) void onceAndRepeatRules(Line line) {
        assertTrue(CompanionLines.available(line,false,100,Long.MIN_VALUE));
        assertEquals(!line.once,CompanionLines.available(line,true,100,Long.MIN_VALUE));
    }
    @ParameterizedTest @CsvSource({"BOSS_START,6000", "LOW_HEALTH,1200"})
    void cooldownIsMeasuredFromActualSpeech(Line line,int cooldown) {
        assertFalse(CompanionLines.available(line,false,100+cooldown-1,100));
        assertTrue(CompanionLines.available(line,false,100+cooldown,100));
        assertFalse(CompanionLines.available(line,false,99,100));
    }
    @ParameterizedTest @EnumSource(Line.class) void queueExpiryUsesEachLinesMaximumWait(Line line) {
        int wait=line==Line.LOW_HEALTH?60:240;
        assertEquals(wait,line.maxWaitTicks);
        var pending=new Pending(line,10);
        assertFalse(CompanionLines.expired(pending,10+wait));assertTrue(CompanionLines.expired(pending,11+wait));
        assertTrue(CompanionLines.expired(pending,9));
        if(line!=Line.LOW_HEALTH)assertFalse(CompanionLines.expired(pending,10+CompanionHints.GAP_TICKS+CompanionHints.SCAN_TICKS));
    }
    @ParameterizedTest @CsvSource({"0.25,0.249,true", "0.3,0.25,false", "0.2,0.1,false", "0.1,0.5,false", "0.5,0.1,true"})
    void lowHealthOnlyOnDescendingThreshold(float previous,float current,boolean expected) { assertEquals(expected,CompanionLines.healthEdge(previous,current)); }
    @Test void initiallyLowHealthIsNotAnEdge() { assertFalse(CompanionLines.healthEdge(Float.NaN,.1f)); }
    @ParameterizedTest @CsvSource({"12999,false", "13000,true", "23999,true", "24000,false", "37000,true"})
    void nightUsesTimeOfDay(long time,boolean expected) {
        assertEquals(expected,CompanionLines.nightCondition(true,true,time));
        assertFalse(CompanionLines.nightCondition(false,true,time));assertFalse(CompanionLines.nightCondition(true,false,time));
    }
    @Test void allRuinRecordsAreRequiredForScanShortCircuit() {
        var records=new java.util.HashMap<String,Integer>();
        for(var kind:cn.wwl.orimod.world.RuinKind.values()) {
            assertFalse(CompanionLines.allRuinsSeen(SpiritProgress.EMPTY.withRewards(records)));
            records.put(CompanionLines.PREFIX+Line.ruin(kind).id,1);
        }
        assertTrue(CompanionLines.allRuinsSeen(SpiritProgress.EMPTY.withRewards(records)));
    }
    @Test void clearingQueuedSpeechKeepsCooldownButResetsObservedEdges() {
        var s=new Session();s.pending=new Pending(Line.TREE_FOUND,10);s.spoken.put(Line.BOSS_START,10L);
        s.previousHealth=.2f;s.wasNight=true;s.clearPending();
        assertNull(s.pending);assertTrue(Float.isNaN(s.previousHealth));assertFalse(s.wasNight);assertEquals(10,s.spoken.get(Line.BOSS_START));
    }
    @Test void independentLineMatchesActionbarLifetimeAndLeavesVerticalSpace() {
        var d=new CompanionLineDisplay();d.show("tree_found");
        for(int i=0;i<40;i++){assertEquals(255,d.alpha(0));d.tick();}
        d.tick();assertTrue(d.alpha(0)<255 && d.alpha(0)>0);
        for(int i=0;i<19;i++)d.tick();assertEquals(0,d.remaining());assertEquals("",d.id());
        assertTrue(CompanionLineDisplay.BOTTOM_OFFSET>=68+9+4,"Line would overlap vanilla actionbar at height-68");
    }
    @ParameterizedTest @EnumSource(Line.class) void suppliedTranslationsExistAndChineseFitsThirtyCharacters(Line line) throws Exception {
        for(String locale:new String[]{"zh_cn","en_us"}) {
            try(var stream=getClass().getResourceAsStream("/assets/orimod/lang/"+locale+".json")) {
                var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                String text=json.get(line.languageKey()).getAsString();
                assertTrue(text.startsWith(locale.equals("zh_cn")?"萤：":"Glimmer: "));
                if(locale.equals("zh_cn"))assertTrue(text.codePointCount(0,text.length())<=30,text);
            }
        }
    }
}
