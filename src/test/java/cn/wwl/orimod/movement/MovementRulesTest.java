package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class MovementRulesTest {
    // Ages 2/3 straddle the coyote window. A buffered edge can predate its landing (age -1).
    @ParameterizedTest @CsvSource({
            "true,0,true,false,false,false,false,JUMP",
            "true,2,false,false,false,false,false,JUMP",
            "true,3,false,false,false,false,false,DOUBLE_JUMP",
            "true,3,false,false,false,true,false,IDLE",
            "true,-1,true,false,false,true,false,JUMP",
            "true,0,true,true,true,false,false,JUMP",
            "true,2,false,true,true,true,false,JUMP",
            "true,3,false,true,true,true,false,WALL_JUMP",
            "true,3,false,true,false,false,false,DOUBLE_JUMP",
            "true,3,false,true,false,true,false,IDLE",
            "false,0,true,true,true,false,false,IDLE",
            "false,0,false,true,true,true,false,WALL_JUMP",
            "false,0,false,true,false,false,false,DOUBLE_JUMP",
            "false,0,false,true,false,true,false,IDLE",
            "false,0,false,false,true,false,false,DOUBLE_JUMP",
            "false,0,false,false,true,true,false,IDLE",
            "true,0,true,false,false,false,true,IDLE",
            "false,0,false,true,true,false,true,IDLE",
            "false,0,false,false,false,false,true,IDLE",
            "true,3,true,false,false,false,false,IDLE"
    })
    void jumpPriorityAndChargeBoundaries(boolean charge, long age, boolean ground, boolean wall,
                                        boolean wallReady, boolean spent, boolean blocked, MoveMode expected) {
        assertEquals(expected, MovementRules.jumpMode(charge, 100 + age, 100, ground, wall, wallReady, spent, blocked));
    }

    @ParameterizedTest @EnumSource(MoveMode.class)
    void activeActionOwnershipAppliesToGroundWallAndAirJumps(MoveMode mode) {
        boolean ownsMotion = java.util.Set.of(MoveMode.DASH, MoveMode.BASH_AIM, MoveMode.GRAPPLE, MoveMode.STOMP).contains(mode);
        assertEquals(ownsMotion ? MoveMode.IDLE : MoveMode.JUMP,
                MovementRules.jumpMode(true, 100, 100, true, false, false, false, mode.blocksJump()));
        assertEquals(ownsMotion ? MoveMode.IDLE : MoveMode.WALL_JUMP,
                MovementRules.jumpMode(false, 100, 90, false, true, true, true, mode.blocksJump()));
        assertEquals(ownsMotion ? MoveMode.IDLE : MoveMode.DOUBLE_JUMP,
                MovementRules.jumpMode(false, 100, 90, false, false, false, false, mode.blocksJump()));
    }

    @ParameterizedTest @CsvSource({
            "1,0,0,false,0,1", "1,0,90,false,-1,0", "1,0,180,false,0,-1",
            "1,0,-90,false,1,0", "0,1,0,false,1,0", "-1,0,0,false,0,-1",
            "0,0,90,false,0,0", "0,0,90,true,-1,0", ".09,0,0,false,0,0"
    })
    void inputUsesYawAndExplicitIdleFallback(float forward, float left, float yaw, boolean fallback, double x, double z) {
        Vec3 actual = MovementRules.inputDirection(forward, left, yaw, fallback);
        assertEquals(x, actual.x, .0002); assertEquals(0, actual.y); assertEquals(z, actual.z, .0002);
    }

    @Test void diagonalInputDoesNotIncreaseSpeed() {
        assertEquals(1, MovementRules.inputDirection(1, 1, 37, false).length(), .000001);
    }

    @ParameterizedTest @CsvSource({".6,0", "0,0", "-.12,-.12", "-3,-3"})
    void dashDoesNotLiftOrEraseDownwardMomentum(double currentY, double expectedY) {
        assertEquals(new Vec3(1.12, expectedY, 0), MovementRules.dashVelocity(new Vec3(1.12, 99, 0), currentY));
    }

    @ParameterizedTest @CsvSource({".3,.3", "0,0", "-.1,-.1", "-.12,-.12", "-2,-.12"})
    void wallSlideOnlyLimitsFalling(double currentY, double expectedY) {
        assertEquals(new Vec3(.2, expectedY, -.3), MovementRules.wallSlideVelocity(new Vec3(.2, currentY, -.3)));
    }

    @ParameterizedTest @CsvSource({".42,.5", ".5,.5", ".8,.8"})
    void wallJumpPreservesHigherVanillaJumpBoost(double jumpY, double expectedY) {
        Vec3 actual = MovementRules.wallJumpVelocity(new Vec3(1, 0, 0), jumpY);
        assertEquals(-.47, actual.x, .000001); assertEquals(expectedY, actual.y, .000001); assertEquals(0, actual.z, .000001);
    }

    @Test void glideCapsHorizontalSpeedAndFallRate() {
        Vec3 result = MovementRules.glideVelocity(new Vec3(1, -3, 1), new Vec3(0, 0, 1), false);
        assertEquals(.5, result.horizontalDistance(), .000001); assertEquals(-.12, result.y);
        assertTrue(result.z > result.x);
    }

    @Test void glideRetainsUpwardMomentumAndAppliesDragWithoutInput() {
        Vec3 actual = MovementRules.glideVelocity(new Vec3(.2, .2, 0), Vec3.ZERO, false);
        assertEquals(.184, actual.x, .000001); assertEquals(.2, actual.y, .000001); assertEquals(0, actual.z, .000001);
    }

    @Test void airControlShardChangesSteeringAcceleration() {
        assertEquals(.06, MovementRules.glideVelocity(Vec3.ZERO, new Vec3(1, 0, 0), false).x);
        assertEquals(.075, MovementRules.glideVelocity(Vec3.ZERO, new Vec3(1, 0, 0), true).x);
    }

    @ParameterizedTest @CsvSource({"0,0", ".09,0", ".1,.1", ".5,.5", ".85,.85", "20,.85"})
    void grappleStopsAtCenterAndNeverOvershoots(double distance, double speed) {
        Vec3 center = new Vec3(10, 30, 20);
        Vec3 actual = MovementRules.grappleVelocity(center, center.add(0, distance, 0));
        assertEquals(0, actual.x); assertEquals(speed, actual.y, .000001); assertEquals(0, actual.z);
    }

    @ParameterizedTest @CsvSource({"0,-.55", "-1.2,-1.75", "-2.2,-2.4", "-2.4,-2.4", "-5,-2.4"})
    void stompAcceleratesToItsTerminalSpeed(double currentY, double expectedY) {
        assertEquals(expectedY, MovementRules.stompVelocity(currentY).y, .000001);
    }

    @org.junit.jupiter.api.Test void oneShotLabelsEndOnLanding() {
        var impulses = java.util.EnumSet.noneOf(MoveMode.class);
        for (var mode : MoveMode.values()) if (mode.isImpulse()) impulses.add(mode);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.EnumSet.of(MoveMode.JUMP, MoveMode.DOUBLE_JUMP, MoveMode.WALL_JUMP,
                MoveMode.BASH_LAUNCH, MoveMode.GRAPPLE_END, MoveMode.STOMP_LAND, MoveMode.RESUME, MoveMode.LAUNCH), impulses);
        for (var mode : impulses) org.junit.jupiter.api.Assertions.assertFalse(mode.blocksJump(), mode.name());
    }
}
