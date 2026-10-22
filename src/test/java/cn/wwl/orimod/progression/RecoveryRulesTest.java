package cn.wwl.orimod.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryRulesTest {
    @Test void passiveLifeIsSpiritOnlyAndWaitsEightSeconds() {
        assertEquals(0, RecoveryRules.life(1, 20, false, 1000, 1));
        assertEquals(0, RecoveryRules.life(1, 20, true, 159, 1));
        assertEquals(.5f, RecoveryRules.life(1, 20, true, 160, 1));
    }
    @Test void lifeCapsAtEightyPercentWithoutLoweringExistingHealth() {
        assertEquals(.25f, RecoveryRules.life(15.75f, 20, true, 200, 1));
        assertEquals(0, RecoveryRules.life(18, 20, true, 200, 1));
        assertEquals(.5f, RecoveryRules.life(31, 40, true, 200, 1));
        assertEquals(0, RecoveryRules.life(0, 20, true, 200, 1));
    }
    @Test void energyWaitsAndNeverReducesEnergyAboveFloor() {
        assertEquals(0, RecoveryRules.energy(0, 100, 79, false, false, 1));
        assertEquals(2, RecoveryRules.energy(0, 100, 80, false, false, 1));
        assertEquals(0, RecoveryRules.energy(80, 100, 80, false, false, 1));
        assertEquals(.25f, RecoveryRules.energy(19.75f, 100, 80, false, false, 1));
    }
    @Test void recoveryShardChangesRateNotCapAndChannelPausesIt() {
        assertEquals(3, RecoveryRules.energy(0, 100, 80, false, true, 1));
        assertEquals(1, RecoveryRules.energy(19, 100, 80, false, true, 1));
        assertEquals(0, RecoveryRules.energy(0, 100, 80, true, true, 1));
    }
    @Test void zeroEnergyAlwaysRecoversACastAndPuzzleBudget() {
        float e=0; for(int i=0;i<10;i++)e+=RecoveryRules.energy(e,100,80,false,false,1);
        assertEquals(20,e); assertTrue(e>=RecoveryTuning.CAST_COST);
        assertTrue(e>=15); // Light Burst.
        assertTrue(e>.3f*41); // Flash's first purification tick.
    }
    @Test void regenerateRequiresAwakeningFormHealthAndEnergy() {
        assertEquals("unavailable",RecoveryRules.refusal(false,10,20,100,false,false));
        assertEquals("health",RecoveryRules.refusal(true,20,20,100,false,false));
        assertEquals("energy",RecoveryRules.refusal(true,10,20,9,false,false));
        assertEquals("busy",RecoveryRules.refusal(true,10,20,10,true,false));
        assertEquals("release",RecoveryRules.refusal(true,10,20,10,false,true));
        assertNull(RecoveryRules.refusal(true,18,20,10,false,false));
    }
    @Test void malformedInputsDoNotCreateHealingOrEnergy() {
        assertEquals(0,RecoveryRules.life(Float.NaN,20,true,200,1));
        assertEquals(0,RecoveryRules.energy(Float.NaN,100,200,false,false,1));
        assertEquals("energy",RecoveryRules.refusal(true,10,20,Float.NaN,false,false));
    }
    @Test void dropBudgetIsBoundedForBosses() {
        assertEquals(1,RecoveryRules.orbGroups(20)); assertEquals(2,RecoveryRules.orbGroups(21));
        assertEquals(4,RecoveryRules.orbGroups(100000)); assertEquals(1,RecoveryRules.orbGroups(Float.NaN));
    }
}
