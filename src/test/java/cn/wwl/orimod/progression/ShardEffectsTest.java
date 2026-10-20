package cn.wwl.orimod.progression;

import cn.wwl.orimod.movement.BashSession;
import cn.wwl.orimod.movement.MovementRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ShardEffectsTest {
    @Test void fourthSlotStartsAtTenAndPreservesEarlierUnlocks() {
        assertEquals(1, SpiritProgression.slots(0));
        assertEquals(2, SpiritProgression.slots(Ability.BASH.bit));
        assertEquals(3, SpiritProgression.slots(Ability.GRAPPLE.bit));
        assertEquals(3, SpiritProgression.slots((1 << 9) - 1));
        assertEquals(4, SpiritProgression.slots((1 << 10) - 1));
        assertEquals(4, SpiritProgression.slots((1 << 14) - 1));
    }
    @Test void healingUsesRollingWindowInsteadOfResetAtWholeSeconds() {
        var window = new ShardEffects.HarvestWindow();
        assertEquals(.5, window.claim(19, 1, 10));
        assertEquals(.5, window.claim(20, 1, 10));
        assertEquals(0, window.claim(21, 64, 10));
        assertEquals(0, window.claim(38, 64, 10));
        assertEquals(.5, window.claim(39, 64, 10));
        assertEquals(.5, window.claim(40, 64, 10));
        assertEquals(0, window.claim(40, 64, 10));
    }
    @Test void harvestingStackNeverOverhealsOrConsumesBudgetWhenAlreadyFull() {
        var window = new ShardEffects.HarvestWindow();
        assertEquals(0, window.claim(0, 2, 0));
        assertEquals(0, window.claim(0, 0, 10));
        assertEquals(0, window.claim(0, 2, Float.NaN));
        assertEquals(.25, window.claim(0, 64, .25f));
        assertEquals(.75, window.claim(0, 64, 10));
        assertEquals(0, window.claim(19, 1, 10));
        assertEquals(1, window.claim(20, Integer.MAX_VALUE, 10));
        assertEquals(1, window.claim(0, 2, 10)); // Dimension clock moving backwards.
    }
    @ParameterizedTest @CsvSource({"0,-9223372036854775808,true", "109,100,false", "110,100,true", "99,100,true"})
    void thornCooldownBoundary(long now, long last, boolean expected) {
        assertEquals(expected, ShardEffects.thornReady(now, last));
    }
    @Test void thornOnlyAcceptsRealCloseDirectMeleeDamage() {
        assertTrue(ShardEffects.thornMelee(true, true, false, false, 1, 9));
        assertFalse(ShardEffects.thornMelee(true, true, false, false, 1, 9.01));
        assertFalse(ShardEffects.thornMelee(false, true, false, false, 1, 1));
        assertFalse(ShardEffects.thornMelee(true, false, false, false, 1, 1));
        assertFalse(ShardEffects.thornMelee(true, true, true, false, 1, 1));
        assertFalse(ShardEffects.thornMelee(true, true, false, true, 1, 1));
        for (float damage : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            assertFalse(ShardEffects.thornMelee(true, true, false, false, damage, 1));
    }
    @Test void ultraBashScalesLaunchAndFinalRedirectSpeed() {
        assertEquals(1.25, BashSession.launchSpeed(false));
        assertEquals(1.5, BashSession.launchSpeed(true), 1e-6);
        for (double original : new double[]{.11, 1, 2, 10})
            assertEquals(BashSession.redirectSpeed(original) * 1.3, BashSession.redirectSpeed(original, true), 1e-6);
    }
    @ParameterizedTest @CsvSource({"false,false,false,-0.12", "false,true,false,-0.042", "false,true,true,0",
            "true,true,true,0.14", "true,false,true,0.14"})
    void stickyAndClimbShareOneVerticalRule(boolean climb, boolean sticky, boolean pressing, double y) {
        var velocity = MovementRules.wallVelocity(new Vec3(.2, -.5, -.3), climb, sticky, pressing);
        assertEquals(.2, velocity.x); assertEquals(-.3, velocity.z); assertEquals(y, velocity.y, 1e-9);
    }
    @Test void stickyDoesNotCancelUpwardJumpMomentumAndNeedsInputIntoWall() {
        var rising = new Vec3(.2, .4, 0);
        assertEquals(rising, MovementRules.wallVelocity(rising, false, true, true));
        assertTrue(MovementRules.pressingWall(new Vec3(1, 0, 0), new Vec3(1, 0, 0)));
        assertFalse(MovementRules.pressingWall(Vec3.ZERO, new Vec3(1, 0, 0)));
        assertFalse(MovementRules.pressingWall(new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
    }
    @Test void stickyAcceptsNeutralAndAlongWallInputButReleasesAtAwayThreshold() {
        assertEquals(-.2, MovementRules.STICKY_WALL_DOT);
        var wall = new Vec3(1, 0, 0);
        assertTrue(MovementRules.stickyWallInput(Vec3.ZERO.dot(wall)));
        assertTrue(MovementRules.stickyWallInput(new Vec3(0, 0, 1).dot(wall)));
        assertTrue(MovementRules.stickyWallInput(1));
        assertTrue(MovementRules.stickyWallInput(-.199999));
        assertFalse(MovementRules.stickyWallInput(-.2));
        assertFalse(MovementRules.stickyWallInput(-.200001));
        assertFalse(MovementRules.stickyWallInput(-1));
    }
    @Test void bothLanguagesDescribeEveryNewShardAndSlot() throws Exception {
        for (var lang : new String[]{"zh_cn", "en_us"}) {
            try (var stream = getClass().getResourceAsStream("/assets/orimod/lang/" + lang + ".json")) {
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var id : new String[]{"life_harvest", "energy_harvest", "ultra_bash", "thorn", "sticky"})
                    for (var suffix : new String[]{"", ".description"}) assertTrue(json.has("shard.orimod." + id + suffix), lang + id);
                assertTrue(json.has("screen.orimod.fourth_slot"));
                assertTrue(json.has("screen.orimod.fourth_slot_unlocked"));
            }
        }
    }
}
