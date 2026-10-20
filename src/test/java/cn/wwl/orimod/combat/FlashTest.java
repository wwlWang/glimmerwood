package cn.wwl.orimod.combat;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.progression.Ability;
import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class FlashTest {
    @Test void entryRequiresSpiritSkillFiveEnergyAndNoCooldown() {
        assertEquals("not_spirit", Flash.refusal(false, true, 100, 10, 0, MoveMode.IDLE, false, false));
        assertEquals("locked", Flash.refusal(true, false, 100, 10, 0, MoveMode.IDLE, false, false));
        assertEquals("cooldown", Flash.refusal(true, true, 100, 10, 11, MoveMode.IDLE, false, false));
        assertEquals("energy", Flash.refusal(true, true, 4.99f, 10, 0, MoveMode.IDLE, false, false));
        assertNull(Flash.refusal(true, true, 5, 10, 10, MoveMode.IDLE, false, false));
    }
    @Test void continuationCanUseLastEnergyButCannotStartBelowFive() {
        assertNull(Flash.refusal(true, true, .1f, 0, 0, MoveMode.IDLE, false, true));
        for (float energy : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            assertEquals("energy", Flash.refusal(true, true, energy, 0, 0, MoveMode.IDLE, false, true));
        assertEquals("charging", Flash.refusal(true, true, 100, 0, 0, MoveMode.IDLE, true, false));
    }
    @ParameterizedTest @EnumSource(MoveMode.class)
    void onlyExclusiveMovementBlocksFlash(MoveMode mode) {
        boolean blocked = mode == MoveMode.BASH_AIM || mode == MoveMode.DASH || mode == MoveMode.GRAPPLE || mode == MoveMode.STOMP;
        assertEquals(blocked ? "movement" : null, Flash.refusal(true, true, 100, 0, 0, mode, false, false));
    }
    @Test void leaseEndsAtEightAndRenewalExtendsIt() {
        assertTrue(Flash.leased(107, 100 + Flash.LEASE_TICKS));
        assertFalse(Flash.leased(108, 100 + Flash.LEASE_TICKS));
        assertTrue(Flash.leased(112, 105 + Flash.LEASE_TICKS));
        assertFalse(Flash.leased(113, 105 + Flash.LEASE_TICKS));
    }
    @Test void blockWorkHasBoundedCadence() {
        var lamps = new java.util.ArrayList<Long>();
        var water = new java.util.ArrayList<Long>();
        for (long age = 1; age <= 80; age++) {
            if (Flash.lanternDue(age)) lamps.add(age);
            if (Flash.cleanseDue(age)) water.add(age);
        }
        assertEquals(java.util.List.of(1L, 11L, 21L, 31L, 41L, 51L, 61L, 71L), lamps);
        assertEquals(java.util.List.of(40L, 60L, 80L), water);
        assertFalse(Flash.lanternDue(0)); assertFalse(Flash.cleanseDue(-1));
    }
    @ParameterizedTest @EnumSource(MobSpawnType.class)
    void onlyNaturalSpawnsAreSuppressed(MobSpawnType type) {
        assertEquals(type == MobSpawnType.NATURAL, Flash.suppresses(true, type, true, true, 16 * 16));
    }
    @Test void suppressionChecksSphereWorldAndActiveState() {
        assertFalse(Flash.suppresses(true, MobSpawnType.NATURAL, true, true, 256.01));
        assertFalse(Flash.suppresses(true, MobSpawnType.NATURAL, false, true, 0));
        assertFalse(Flash.suppresses(true, MobSpawnType.NATURAL, true, false, 0));
        assertFalse(Flash.suppresses(false, MobSpawnType.NATURAL, true, true, 0));
        assertFalse(Flash.suppresses(true, MobSpawnType.NATURAL, true, true, Double.NaN));
    }
    @Test void costsDamageAndSkillMatchTheDesign() {
        assertEquals(6, Flash.COST_PER_TICK * 20, 1e-6);
        assertEquals(2, Flash.GLOOM_DAMAGE * 20 / Flash.GLOOM_INTERVAL, 1e-6);
        assertEquals(1, Flash.BOSS_DAMAGE * 20 / Flash.BOSS_INTERVAL, 1e-6);
        assertEquals(.5, Flash.UNDEAD_DAMAGE * 20 / Flash.UNDEAD_INTERVAL, 1e-6);
        assertEquals(8192, Ability.FLASH.bit);
        assertEquals(40, Ability.FLASH.cost);
        assertEquals(Ability.CHARGE_FLAME, Ability.FLASH.prerequisite);
        assertEquals(14, Ability.values().length);
    }
}
