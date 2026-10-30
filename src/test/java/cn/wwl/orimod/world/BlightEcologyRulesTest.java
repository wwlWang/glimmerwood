package cn.wwl.orimod.world;

import cn.wwl.orimod.config.OriServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlightEcologyRulesTest {
    @Test void oneSharedBudgetNeverExceedsCeilings(){
        var b=new BlightEcologyRules.Budget();
        for(int core=0;core<32;core++)for(int i=0;i<100;i++){b.read();b.write();b.index();}
        assertEquals(512,b.reads());assertEquals(32,b.writes());assertEquals(128,b.indices());
        assertEquals(0,b.remainingReads());assertEquals(0,b.remainingWrites());
        assertFalse(b.read());assertFalse(b.write());assertFalse(b.index());
    }
    @Test void completeArtificialNeighborhoodFitsOneTurn(){
        assertEquals(441,BlightEcologyRules.ARTIFICIAL_READS);
        var b=new BlightEcologyRules.Budget();for(int i=0;i<441;i++)assertTrue(b.read());
        assertEquals(71,b.remainingReads());
        assertThrows(IllegalArgumentException.class,()->new BlightEcologyRules.Budget(513,32));
        assertThrows(IllegalArgumentException.class,()->new BlightEcologyRules.Budget(512,33));
        assertThrows(IllegalArgumentException.class,()->new BlightEcologyRules.Budget(-1,0));
    }
    @ParameterizedTest @CsvSource({"0.1,240","0.15,360","1,2400","4,9600"})
    void fractionalRatesAccumulateWithoutLosingSubColumnCredit(double multiplier,int expected){
        double credit=0;int columns=0;
        for(int interval=0;interval<1200;interval++){
            credit=BlightEcologyRules.credit(credit,multiplier);
            while(credit>=1){credit--;columns++;}
        }
        assertTrue(columns>=expected-1&&columns<=expected,"Configured speed lost fractional credit: "+columns);
        for(int i=0;i<100000;i++)credit=BlightEcologyRules.credit(credit,multiplier);
        assertTrue(credit<=Math.max(2,2*multiplier),"Stalled/offline work accumulated a backlog");
    }
    @ParameterizedTest @CsvSource({"0,600","13,600","30,1385","48,2216","64,2954","100,2954"})
    void restorationDurationScalesWithOccupiedRadius(double radius,int duration){assertEquals(duration,BlightEcologyRules.duration(radius));}
    @ParameterizedTest @CsvSource({"-1,60","0,60","63,60","64,61","2400,97","7680,180","32768,180"})
    void rewardUsesUniqueNewColumnsAndCaps(int columns,int reward){assertEquals(reward,BlightEcologyRules.reward(columns));}
    @Test void radiusUsesLongArithmeticAndInclusiveDisk(){
        assertTrue(BlightEcologyRules.within(64,0,64));assertFalse(BlightEcologyRules.within(64,1,64));
        assertFalse(BlightEcologyRules.within(Integer.MAX_VALUE,0,64));
    }
    @Test void initialFloorOwnsOneColumnRegardlessOfHeight(){
        var r=new BlightRegion(new BlockPos(-16,64,15),true);
        r.initialize(List.of(new BlockPos(-16,63,15),new BlockPos(-16,71,15),new BlockPos(-15,63,15),new BlockPos(100,63,15)));
        assertEquals(2,r.initialColumns());assertEquals(0,r.grownColumns());
        assertEquals(new BlockPos(-16,63,15),r.floor(new BlockPos(-16,-50,15)));
        r.initialize(List.of(new BlockPos(-14,63,15)));assertEquals(2,r.columns().size());
        assertThrows(UnsupportedOperationException.class,()->r.columns().clear());
    }
    @Test void protectedColumnsExpireAfterOneLoadedDayAndStayBounded(){
        var r=new BlightRegion(BlockPos.ZERO,true);r.initialize(List.of(BlockPos.ZERO));
        assertTrue(r.protect(new BlockPos(64,90,0)));assertFalse(r.protect(new BlockPos(64,-30,0)));
        assertTrue(r.protectedAt(new BlockPos(64,0,0)));assertFalse(r.protect(new BlockPos(65,0,0)));
        assertThrows(UnsupportedOperationException.class,()->r.protectedColumns().clear());
        for(int i=0;i<23999;i++)r.loadedTick();assertNull(r.expireProtection());
        r.loadedTick();assertEquals(new BlockPos(64,0,0),r.expireProtection());assertFalse(r.protectedAt(new BlockPos(64,0,0)));
        assertNull(r.expireProtection());assertTrue(r.protect(new BlockPos(64,0,0),BlightProtection.Reason.STRUCTURE));
        assertEquals(24000,r.protectionAt(new BlockPos(64,0,0)).registeredTick());
    }
    @Test void protectionVersionOnlyInvalidatesStructureReasons(){
        var r=new BlightRegion(BlockPos.ZERO,true);r.initialize(List.of(BlockPos.ZERO));
        for(int i=0;i<123;i++)r.loadedTick();r.protect(BlockPos.ZERO.east(),BlightProtection.Reason.STRUCTURE);r.protect(BlockPos.ZERO.west(),BlightProtection.Reason.ARTIFICIAL);
        var saved=r.save();var copy=BlightRegion.load(BlockPos.ZERO,saved,null);assertEquals(saved,copy.save());
        saved.putInt("protectionVersion",0);copy=BlightRegion.load(BlockPos.ZERO,saved,null);
        assertFalse(copy.protectedAt(BlockPos.ZERO.east()));assertTrue(copy.protectedAt(BlockPos.ZERO.west()));
        assertEquals(123,copy.protectionAt(BlockPos.ZERO.west()).registeredTick());
        var legacy=r.save();legacy.remove("protections");legacy.remove("protectionVersion");legacy.putLongArray("protected",new long[]{BlockPos.ZERO.east().asLong()});
        assertTrue(BlightRegion.load(BlockPos.ZERO,legacy,null).protectedColumns().isEmpty());
    }
    @Test void newAndLegacyMigrationAreIndependent(){
        assertTrue(OriServerConfig.legacyCoresSpread());
        var d=new BlightData();var old=new BlockPos(0,64,0);var fresh=old.offset(100,0,0);
        d.register(old,new long[]{old.below().asLong()});d.register(fresh,new long[]{fresh.below().asLong()},true);
        assertFalse(d.initializeEcology(old,List.of(old.below()),false));
        assertTrue(d.initializeEcology(fresh,List.of(fresh.below()),false));assertFalse(d.ecology(fresh).legacy());
        assertTrue(d.initializeEcology(old,List.of(old.below()),true));assertTrue(d.ecology(old).legacy());
        assertFalse(d.initializeEcology(old,List.of(old),true));
    }
    @Test void oldPurificationNeverBecomesEcologyOrResetsCursor(){
        var d=new BlightData();d.register(BlockPos.ZERO,new long[]{BlockPos.ZERO.below().asLong()});
        var tag=d.save(new CompoundTag(),null);var region=tag.getList("regions",Tag.TAG_COMPOUND).getCompound(0);
        region.putBoolean("purifying",true);region.putInt("elapsed",303);region.getList("chunks",Tag.TAG_COMPOUND).getCompound(0).putInt("cursor",1);
        var copy=BlightData.load(tag,null);assertFalse(copy.initializeEcology(BlockPos.ZERO,List.of(BlockPos.ZERO),true));
        assertEquals(tag,copy.save(new CompoundTag(),null));
    }
    @Test void initialOverlapsNeverDuplicateOwnership(){
        var d=new BlightData();var a=BlockPos.ZERO;var b=new BlockPos(5,0,0);var shared=new BlockPos(2,-1,0);
        d.register(a,new long[]{shared.asLong()});d.register(b,new long[]{shared.asLong()});
        assertTrue(d.initializeEcology(a,List.of(shared),true));assertTrue(d.initializeEcology(b,List.of(shared),true));
        assertEquals(Optional.of(a),d.columnOwner(shared));assertEquals(0,d.ecology(b).initialColumns());
        d.removeCore(b);assertEquals(Optional.of(a),d.columnOwner(shared));
    }
    @Test void structurePaddingIsHorizontalAndInclusive(){
        var box=new BoundingBox(-16,50,15,20,100,42);
        assertTrue(BlightProtection.protects(box,new BlockPos(-18,50,44)));
        assertTrue(BlightProtection.protects(box,new BlockPos(22,100,13)));
        for(var p:List.of(new BlockPos(-19,60,20),new BlockPos(23,60,20),new BlockPos(0,49,20),new BlockPos(0,101,20)))assertFalse(BlightProtection.protects(box,p));
    }
}
