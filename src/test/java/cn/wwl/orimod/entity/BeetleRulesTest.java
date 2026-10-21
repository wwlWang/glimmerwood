package cn.wwl.orimod.entity;

import cn.wwl.orimod.world.GloomTideRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static cn.wwl.orimod.entity.BeetleRules.*;
import static org.junit.jupiter.api.Assertions.*;

class BeetleRulesTest {
    @ParameterizedTest @CsvSource({"0,true","59,true","-59,true","60,true","-60,true","61,false","-61,false","180,false"})
    void bodySector(double angle,boolean armored){
        var incoming=new Vec3(Math.sin(Math.toRadians(angle)),0,Math.cos(Math.toRadians(angle)));
        assertEquals(armored,front(0,incoming));
        assertEquals(armored,front(90,incoming.yRot((float)-Math.PI/2)));
    }
    @Test void overheadUnknownAndNonFiniteSourcesAreNotArmored(){
        for(var direction:new Vec3[]{null,Vec3.ZERO,new Vec3(0,2,0),new Vec3(0,1,1),new Vec3(Double.NaN,0,1)})assertFalse(front(0,direction));
        assertTrue(front(0,new Vec3(0,.99,1)));
    }
    @ParameterizedTest @EnumSource(State.class)
    void damageTable(State state){assertEquals(state==State.FLIPPED?2:.2f,damageScale(state,true));assertEquals(state==State.FLIPPED?2:1,damageScale(state,false));}
    @Test void windupHasFifteenFullTicks(){
        var c=Clock.idle().with(State.WINDUP,WINDUP);
        for(int i=1;i<15;i++){c=c.step();assertEquals(State.WINDUP,c.state());assertEquals(15-i,c.left());}
        assertEquals(State.CHARGING,c.step().state());
    }
    @Test void wallStunAndCooldownHaveIndependentClocks(){
        var c=Clock.idle().stopped(true);
        for(int i=1;i<30;i++){c=c.step();assertEquals(State.STUNNED,c.state());}
        c=c.step();assertEquals(State.IDLE,c.state());assertEquals(30,c.cooldown());
        for(int i=0;i<30;i++)c=c.step();assertEquals(0,c.cooldown());
        assertEquals(State.IDLE,Clock.idle().stopped(false).state());
        assertEquals(60,Clock.idle().stopped(false).cooldown());
    }
    @Test void flipCannotRefreshAndRecoveryProtectsFortyTicks(){
        var c=Clock.idle().flipped();
        for(int i=0;i<60;i++){assertEquals(State.FLIPPED,c.state());assertSame(c,c.flipped());c=c.step();}
        assertEquals(State.IDLE,c.state());assertEquals(40,c.immunity());
        for(int i=0;i<40;i++){assertFalse(c.canFlip());assertSame(c,c.flipped());c=c.step();}
        assertTrue(c.canFlip());assertEquals(60,c.flipped().left());
    }
    @ParameterizedTest @CsvSource({"8.999,false","9,true","64,true","64.001,false"})
    void chargeRangeInclusive(double squared,boolean allowed){assertEquals(allowed,chargeRange(squared));}
    @Test void waveThresholdsAreIndependent(){
        assertEquals(java.util.List.of(GloomTideRules.WaveMob.SPITTER),GloomTideRules.wave(0,.35f,.3f));
        assertEquals(java.util.List.of(GloomTideRules.WaveMob.SPITTER,GloomTideRules.WaveMob.SPITTER,GloomTideRules.WaveMob.BEETLE,GloomTideRules.WaveMob.HAWK),GloomTideRules.wave(1,.349f,.299f));
    }
    @Test void fixedSeedWavesStayWithinCompositionAndExpectedFrequency(){
        var random=new java.util.Random(105);int beetles=0,hawks=0,spitters=0;
        for(int i=0;i<10000;i++){
            var wave=GloomTideRules.wave(random.nextInt(2),random.nextFloat(),random.nextFloat());
            assertTrue(wave.size()>=1 && wave.size()<=4);
            beetles+=java.util.Collections.frequency(wave,GloomTideRules.WaveMob.BEETLE);
            hawks+=java.util.Collections.frequency(wave,GloomTideRules.WaveMob.HAWK);
            spitters+=java.util.Collections.frequency(wave,GloomTideRules.WaveMob.SPITTER);
        }
        assertTrue(beetles>=3300 && beetles<=3700,"beetles="+beetles);
        assertTrue(hawks>=2800 && hawks<=3200,"hawks="+hawks);
        assertTrue(spitters>=14800 && spitters<=15200,"spitters="+spitters);
    }
}
