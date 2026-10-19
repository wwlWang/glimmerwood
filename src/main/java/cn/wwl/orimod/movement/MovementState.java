package cn.wwl.orimod.movement;

import net.minecraft.world.phys.Vec3;

/** Transient per-player state. Air charges start spent so loading in mid-air is not a refill. */
public final class MovementState {
    public long lastInput = -100, jumpUntil = -100, jumpPressedAt = -100, groundedAt = -100, groundLockUntil, dashReadyAt, wallReadyAt;
    public int sequence = -1, actionId, activeTicks, wallDirection = -1;
    public boolean jumpHeld, dashHeld, crouching, active, airJumpSpent = true, airDashSpent = true, groundJumpAvailable;
    public float forward, left, yaw;
    public MoveMode mode = MoveMode.IDLE, lastAction = MoveMode.IDLE;
    public String lastWallRefill = "", dimension = "";
    public Vec3 velocity = Vec3.ZERO, previousPosition;
    public int acceptedJumps, acceptedWallJumps, acceptedDashes, rejectedDashes, relocations;
    public boolean bashHeld;
    /** R88: last jump-press edge and last ground/wall jump; glide only arms on a press made after that jump (an air jump's own press may glide). */
    public long jumpEdgeAt = -100, jumpPerformedAt = -100;
    /** R38: crouch-held ground ticks toward a charged Launch; the one air Launch. */
    public int launchCharge, acceptedLaunches, launchHitCount;
    public long launchHitUntil;
    public final java.util.Set<Integer> launchHits = new java.util.HashSet<>();
    public boolean airLaunchSpent = true;
    /** R40: the Triple Jump shard's extra air jump. */
    public boolean tripleJumpSpent;
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
    public final java.util.ArrayDeque<JumpRequest> jumps = new java.util.ArrayDeque<>();
    public final java.util.Map<Integer, MoveMode> predictedJumps = new java.util.LinkedHashMap<>();
    public int predictedJumpCount, jumpPredictionMismatches, jumpPredictionRejections;
    /** R78 (client): a locally started Dash / Glide / Bash aim awaiting the server's confirmation. */
    public MoveMode predictedMode = MoveMode.IDLE;
    public long predictedUntil;
    public int predictedActions, confirmedPredictions, unconfirmedPredictions;
    public record JumpRequest(int sequence, long pressedAt, long expiresAt, Vec3 position,
                              Vec3 velocity, float fallDistance, net.minecraft.core.Direction wall, boolean predicted) {}
}
