package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;

/** Transient per-player state. Air charges start spent so loading in mid-air is not a refill. */
public final class MovementState {
    public long lastInput = -100, jumpUntil = -100, jumpPressedAt = -100, groundedAt = -100, groundLockUntil, dashReadyAt, wallReadyAt;
    public int sequence = -1, actionId, activeTicks, wallDirection = -1;
    public boolean jumpHeld, dashHeld, crouching, active, airJumpSpent = true, airDashSpent = true, groundJumpAvailable;
    public float forward, left, yaw;
    public String mode = "idle", lastAction = "none", lastWallRefill = "", dimension = "";
    public Vec3 velocity = Vec3.ZERO, previousPosition;
    public int acceptedJumps, acceptedWallJumps, acceptedDashes, rejectedDashes;
    public int lastHurtTime;
    public boolean bashHeld;
    public float pitch;
    public BashSession bash;
    public int targetId = -1, acceptedBashes, cancelledBashes;
    public Vec3 targetPosition = Vec3.ZERO, suspendedVelocity = Vec3.ZERO;
    public float suspendedFallDistance;
    public double observedDy, grappleDistance = Double.MAX_VALUE;
    public SpiritTargets.Target grapple;
    public long grappleUntil, stompUntil;
    public int grappleStalls, acceptedGrapples, completedGrapples, cancelledGrapples, acceptedStomps, stompImpacts, startedGlides;
    public final java.util.Map<String, Long> targetCooldowns = new java.util.HashMap<>();
}
