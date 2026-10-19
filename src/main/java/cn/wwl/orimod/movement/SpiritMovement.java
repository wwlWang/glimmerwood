package cn.wwl.orimod.movement;

import static cn.wwl.orimod.movement.MoveMode.*;
import static cn.wwl.orimod.movement.MovementRules.*;

import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.network.MovementResultPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SpiritMovement {
    private static final Direction[] WALLS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private SpiritMovement() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4"); // R38: LAUNCH mode
        registrar.playToServer(MovementInputPayload.TYPE, MovementInputPayload.CODEC, (input, context) -> {
            if (context.player() instanceof ServerPlayer player) accept(player, input);
        });
        registrar.playToClient(MovementResultPayload.TYPE, MovementResultPayload.CODEC, (result, context) -> result.apply(context.player()));
    }

    public static boolean canMove(Player player) {
        return SpiritData.isSpirit(player) && player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !player.isInWaterOrBubble() && !player.isInLava()
                && !player.onClimbable() && !player.isFallFlying() && !player.getAbilities().flying;
    }

    public static void accept(ServerPlayer player, MovementInputPayload input) {
        var s = player.getData(SpiritData.MOVEMENT);
        if (input.sequence() <= s.sequence || !Float.isFinite(input.forward()) || !Float.isFinite(input.left()) || !Float.isFinite(input.yaw()) || !Float.isFinite(input.pitch())) return;
        s.sequence = input.sequence();
        long now = player.level().getGameTime();
        s.lastInput = now;
        boolean wasActive = s.active;
        s.active = input.has(MovementInputPayload.ACTIVE) && canMove(player) && !player.hasContainerOpen();
        s.forward = Mth.clamp(input.forward(), -1, 1);
        s.left = Mth.clamp(input.left(), -1, 1);
        s.yaw = Mth.wrapDegrees(input.yaw());
        s.pitch = Mth.clamp(input.pitch(), -90, 90);
        s.crouching = input.has(MovementInputPayload.CROUCH);
        boolean jump = input.has(MovementInputPayload.JUMP), dash = input.has(MovementInputPayload.DASH), bash = input.has(MovementInputPayload.BASH);
        if (s.active) {
            if (jump && !s.jumpHeld && s.crouching && tryLaunch(player, s, now)) {
                // R38: crouch + jump is Launch (charged on the ground), never an ordinary jump.
            } else if (jump && !s.jumpHeld) {
                // Bound compensation to two ticks; packet delivery cannot consume the intended 100 ms window.
                s.jumpPressedAt = Math.clamp(input.gameTick(), now - 2, now);
                // A queued packet must survive until the next player tick. Coyote time
                // uses the bounded press timestamp; buffering starts at server receipt.
                s.jumpUntil = now + INPUT_BUFFER_TICKS;
                if (grounded(player) && now >= s.groundLockUntil) {
                    s.groundJumpAvailable = true; s.groundedAt = now;
                    s.airJumpSpent = s.airDashSpent = false;
                }
                var request = new MovementState.JumpRequest(input.sequence(), s.jumpPressedAt, s.jumpUntil,
                        player.position(), player.getDeltaMovement(), player.fallDistance,
                        touchingWall(player, inputDirection(s, false), s.wallDirection), input.has(MovementInputPayload.PREDICTED_JUMP));
                // Handle independent edges in packet order, including several arriving after a
                // server hitch. The short queue is only for inputs waiting for a valid landing.
                if (tryJump(player, s, request, now)) s.jumpUntil = -100;
                else if (s.jumps.size() < 4) s.jumps.addLast(request);
                else rejectJump(player, s, request);
            }
            if (dash && !s.dashHeld) {
                if (s.bash != null || s.grapple != null) cancel(player, s);
                if (s.crouching) AirMovement.beginStomp(player, s, now);
                else if (s.mode != STOMP) tryDash(player, s, now);
            }
            if (bash && !s.bashHeld) {
                if (s.grapple != null) cancel(player, s);
                else if (s.mode != STOMP) beginBash(player, s, now);
            }
            else if (!bash && s.bashHeld && s.bash != null) releaseBash(player, s, now);
        } else {
            s.jumpUntil = -100;
            s.jumps.clear();
            if (wasActive || s.mode != IDLE) cancel(player, s);
        }
        s.jumpHeld = jump;
        s.dashHeld = dash;
        s.bashHeld = bash;
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = player.getData(SpiritData.MOVEMENT);
        long now = player.level().getGameTime();
        String dimension = player.level().dimension().location().toString();
        boolean relocated = !s.dimension.equals(dimension);
        s.observedDy = s.previousPosition == null || relocated ? 0 : player.getY() - s.previousPosition.y;
        s.dimension = dimension;
        s.previousPosition = player.position();
        if (relocated) teleported(player);
        boolean grounded = grounded(player) && now >= s.groundLockUntil;
        if (grounded) {
            boolean refilled = s.airJumpSpent || s.airDashSpent;
            s.airJumpSpent = s.airDashSpent = false;
            s.groundJumpAvailable = true;
            s.groundedAt = now;
            s.lastWallRefill = "";
            s.airLaunchSpent = false;
            // MOVE12 (R27): a ground jump, a wall jump refilled on a new wall, a bash launch, grapple end or stomp
            // landing can reach the ground without a refill; the one-shot label must still end, or the server keeps
            // reporting e.g. wall_jump / grapple_end on the ground.
            if ((refilled || s.mode.isImpulse()) && s.mode != DASH && s.mode != STOMP && s.bash == null && s.grapple == null)
                signal(player, s, IDLE, 0, Vec3.ZERO);
        }
        if (!s.active || now - s.lastInput > 4 || !canMove(player)) {
            s.active = false;
            s.jumpUntil = -100;
            s.jumps.clear();
            if (s.mode != IDLE) cancel(player, s);
            return;
        }
        if (s.bash != null) {
            if (now >= s.bash.expiresAt || s.crouching || !s.bash.target.valid(player)) cancel(player, s);
            else s.bash.hold(player);
            return;
        }
        if (AirMovement.tickOwned(player, s, now, grounded)) return;
        // R38: charge a ground Launch while crouching still on the ground; a short chime marks the full charge.
        if (grounded && s.crouching && s.mode == IDLE && cn.wwl.orimod.progression.Ability.LAUNCH.unlocked(player)) {
            if (++s.launchCharge == LAUNCH_CHARGE_TICKS)
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, .8f, 1.6f);
        } else s.launchCharge = 0;
        Direction wall = touchingWall(player, inputDirection(s, false), s.wallDirection);
        s.wallDirection = wall == null ? -1 : wall.ordinal();
        while (!s.jumps.isEmpty()) {
            var request = s.jumps.getFirst();
            if (request.expiresAt() < now) { s.jumps.removeFirst(); rejectJump(player, s, request); }
            else if (tryJump(player, s, request, now)) s.jumps.removeFirst();
            else break;
        }
        s.jumpUntil = s.jumps.isEmpty() ? -100 : s.jumps.getFirst().expiresAt();
        if (s.mode == DASH) {
            if (s.activeTicks-- <= 0 || player.horizontalCollision || s.crouching) cancel(player, s);
            return;
        }
        if (!grounded && wall != null && !s.crouching && now >= s.wallReadyAt && player.getDeltaMovement().y < 0) {
            // A short renewable lease prevents a missed release from leaving the client attached to a wall.
            signal(player, s, WALL_SLIDE, 3, Vec3.ZERO);
            player.fallDistance = 0;
        } else {
            if (s.mode == WALL_SLIDE) cancel(player, s);
            AirMovement.glide(player, s, now, grounded, wall != null || player.horizontalCollision);
        }
    }

    private static boolean tryJump(ServerPlayer player, MovementState s, MovementState.JumpRequest request, long now) {
        if (s.crouching || s.bash != null || s.grapple != null || s.mode.blocksJump()) return false;
        boolean onGround = grounded(player) && now >= s.groundLockUntil;
        Direction wall = touchingWall(player, inputDirection(s, false), s.wallDirection);
        if (wall == null) wall = request.wall();
        // R40: any refill clears the triple jump too (it is only consulted once the double jump is spent).
        if (!s.airJumpSpent) s.tripleJumpSpent = false;
        boolean triple = cn.wwl.orimod.progression.Shard.TRIPLE_JUMP.equipped(player);
        MoveMode mode = MovementRules.jumpMode(s.groundJumpAvailable, request.pressedAt(), s.groundedAt,
                onGround, wall != null, now >= s.wallReadyAt, MovementRules.airJumpsSpent(s.airJumpSpent, triple, s.tripleJumpSpent), false);
        if (mode == IDLE || !cn.wwl.orimod.combat.SpiritCombat.allowMovement(player)) return false;
        player.jumpFromGround();
        s.groundJumpAvailable = false; s.groundLockUntil = now + 4; s.jumpPressedAt = request.pressedAt();
        if (mode == WALL_JUMP) {
            player.setDeltaMovement(MovementRules.wallJumpVelocity(Vec3.atLowerCornerOf(wall.getNormal()), player.getDeltaMovement().y));
            player.fallDistance = 0; s.wallReadyAt = now + 8;
            String surface = wallSurface(player, wall);
            if (!surface.equals(s.lastWallRefill)) { s.airJumpSpent = s.airDashSpent = false; s.lastWallRefill = surface; }
            s.acceptedWallJumps++;
        } else {
            if (mode == DOUBLE_JUMP) { player.fallDistance = 0; if (s.airJumpSpent) s.tripleJumpSpent = true; s.airJumpSpent = true; }
            s.acceptedJumps++;
        }
        player.setOnGround(false);
        signal(player, s, mode, 0, player.getDeltaMovement(), request.sequence());
        return true;
    }

    public static String wallSurface(Player player, Direction wall) {
        return wall.getAxis() + ":" + (wall.getAxis() == Direction.Axis.X
                ? Mth.floor(player.getX() + wall.getStepX() * (player.getBbWidth() / 2 + .15))
                : Mth.floor(player.getZ() + wall.getStepZ() * (player.getBbWidth() / 2 + .15)));
    }

    private static void rejectJump(ServerPlayer player, MovementState s, MovementState.JumpRequest request) {
        if (!request.predicted() || !player.isAlive()) return;
        Vec3 correction = request.position();
        if (player.level().noCollision(player, player.getBoundingBox().move(correction.subtract(player.position()))))
            player.connection.teleport(correction.x, correction.y, correction.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(request.velocity());
        player.fallDistance = Math.max(player.fallDistance, request.fallDistance());
        PacketDistributor.sendToPlayer(player, new MovementResultPayload(player.getId(), player.level().dimension().location().toString(),
                ++s.actionId, request.sequence(), JUMP_REJECT, 0, request.velocity(), s.airJumpSpent, s.airDashSpent, -1, Vec3.ZERO));
    }

    static boolean tryLaunch(ServerPlayer player, MovementState s, long now) {
        boolean onGround = grounded(player) && now >= s.groundLockUntil;
        if (s.bash != null || s.grapple != null || s.mode.blocksJump() || !cn.wwl.orimod.combat.SpiritCombat.allowMovement(player)) return false;
        if (!launchAllowed(cn.wwl.orimod.progression.Ability.LAUNCH.unlocked(player), onGround, s.launchCharge, s.airLaunchSpent)) return false;
        Vec3 velocity = launchVelocity(s.yaw, s.pitch, onGround);
        if (!onGround) s.airLaunchSpent = true;
        // Like Bash, a Launch refreshes the other air moves (WotW).
        s.airJumpSpent = s.airDashSpent = false;
        s.launchCharge = 0;
        s.groundJumpAvailable = false;
        s.groundLockUntil = now + 4;
        s.acceptedLaunches++;
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.fallDistance = 0;
        signal(player, s, LAUNCH, 0, velocity);
        return true;
    }

    private static void tryDash(ServerPlayer player, MovementState s, long now) {
        if (!SpiritData.hasDash(player)) {
            s.rejectedDashes++;
            player.displayClientMessage(Component.translatable("message.orimod.dash_locked"), true);
            return;
        }
        if (now < s.dashReadyAt || s.crouching || (!grounded(player) && s.airDashSpent)) {
            s.rejectedDashes++;
            return;
        }
        if (!cn.wwl.orimod.combat.SpiritCombat.allowMovement(player)) return;
        s.airDashSpent = true;
        s.dashReadyAt = now + DASH_COOLDOWN_TICKS;
        s.groundLockUntil = Math.max(s.groundLockUntil, now + DASH_TICKS + 1);
        s.acceptedDashes++;
        Vec3 direction = inputDirection(s, true).scale(DASH_SPEED);
        // Horizontal dash preserves falling history. It never sets noGravity, noPhysics or fallDistance.
        Vec3 velocity = MovementRules.dashVelocity(direction, player.getDeltaMovement().y);
        player.setDeltaMovement(velocity);
        signal(player, s, DASH, DASH_TICKS, velocity);
    }

    public static boolean grounded(Player player) {
        return player.onGround() && !player.level().noCollision(player, player.getBoundingBox().deflate(0.01).move(0, -0.06, 0));
    }

    public static Vec3 inputDirection(MovementState s, boolean fallback) {
        return MovementRules.inputDirection(s.forward, s.left, s.yaw, fallback);
    }

    public static Direction touchingWall(Player player, Vec3 toward, int previous) {
        Direction best = null;
        double bestDot = 0.2;
        for (Direction side : WALLS) {
            double dot = toward.dot(Vec3.atLowerCornerOf(side.getNormal())) + (previous == side.ordinal() ? 0.15 : 0);
            if (dot <= bestDot) continue;
            var box = player.getBoundingBox().deflate(0.01, 0.08, 0.01).move(side.getStepX() * 0.12, 0, side.getStepZ() * 0.12);
            if (!player.level().noCollision(player, box)) { best = side; bestDot = dot; }
        }
        return best;
    }

    public static void cancel(ServerPlayer player, MovementState s) {
        cancel(player, s, false);
    }

    /** Called after an actual server position synchronization, never inferred from travel distance. */
    public static void teleported(ServerPlayer player) {
        var s = player.getData(SpiritData.MOVEMENT);
        if (s.mode != IDLE || s.bash != null || s.grapple != null) cancel(player, s);
        s.active = false; s.activeTicks = 0; s.jumpUntil = -100; s.jumps.clear();
        s.airJumpSpent = s.airDashSpent = true; s.groundJumpAvailable = false;
        s.groundedAt = -100; s.groundLockUntil = 0; s.lastWallRefill = "";
        s.dimension = player.level().dimension().location().toString();
        s.previousPosition = player.position(); s.observedDy = 0; s.relocations++;
        if (SpiritData.isSpirit(player)) signal(player, s, RELOCATE, 0, Vec3.ZERO);
        s.mode = IDLE;
    }

    public static void interrupt(ServerPlayer player) {
        var s = player.getData(SpiritData.MOVEMENT);
        if (!s.active && s.mode == IDLE && s.bash == null && s.grapple == null) return;
        s.active = false;
        cancel(player, s, true);
    }

    private static void cancel(ServerPlayer player, MovementState s, boolean interrupted) {
        s.jumpUntil = -100;
        s.jumps.clear();
        if (s.grapple != null) { s.grapple = null; s.cancelledGrapples++; }
        if (s.bash != null) {
            // LivingDamageEvent.Post precedes vanilla knockback and the hurtTime assignment.
            // Preserve current motion for an attack; a manual/timeout release restores suspended motion.
            Vec3 velocity = interrupted ? player.getDeltaMovement() : s.bash.playerVelocity;
            player.fallDistance = s.bash.playerFallDistance;
            s.bash.release(null);
            s.bash = null;
            s.cancelledBashes++;
            player.setDeltaMovement(velocity);
            signal(player, s, RESUME, 0, velocity);
            s.mode = IDLE;
            return;
        }
        signal(player, s, IDLE, 0, Vec3.ZERO);
    }

    private static void beginBash(ServerPlayer player, MovementState s, long now) {
        var target = SpiritTargets.find(player, s.yaw, s.pitch);
        if (target == null || !target.valid(player)) return;
        s.targetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (s.targetCooldowns.containsKey(target.key())) return;
        if (target.grapple()) { AirMovement.beginGrapple(player, s, target, now); return; }
        if (!SpiritData.hasBash(player)) {
            player.displayClientMessage(Component.translatable("message.orimod.bash_locked"), true);
            return;
        }
        if (!cn.wwl.orimod.combat.SpiritCombat.allowMovement(player)) return;
        s.targetCooldowns.put(target.key(), now + BashSession.TARGET_COOLDOWN_TICKS);
        s.bash = new BashSession(player, target);
        s.jumpUntil = -100;
        s.bash.hold(player);
        signal(player, s, BASH_AIM, BashSession.AIM_TICKS, Vec3.ZERO);
    }

    private static void releaseBash(ServerPlayer player, MovementState s, long now) {
        var session = s.bash;
        if (session == null) return;
        if (!session.target.valid(player) || now >= session.expiresAt) { cancel(player, s); return; }
        Vec3 direction = Vec3.directionFromRotation(s.pitch, s.yaw);
        session.release(direction, player);
        s.targetCooldowns.put(session.target.key(), now + BashSession.TARGET_COOLDOWN_TICKS);
        s.bash = null;
        s.airJumpSpent = s.airDashSpent = false;
        s.groundJumpAvailable = false;
        s.groundLockUntil = now + 4;
        s.acceptedBashes++;
        Vec3 velocity = direction.scale(1.25);
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;
        signal(player, s, BASH_LAUNCH, 0, velocity);
    }

    static void signal(ServerPlayer player, MovementState s, MoveMode mode, int ticks, Vec3 velocity) {
        signal(player, s, mode, ticks, velocity, -1);
    }

    private static void signal(ServerPlayer player, MovementState s, MoveMode mode, int ticks, Vec3 velocity, int jumpSequence) {
        // Grapple re-signals every pull tick; only its first signal is a new action.
        if (mode != GRAPPLE || s.mode != GRAPPLE) cn.wwl.orimod.fx.SpiritFxLifecycle.moved(player, mode);
        s.mode = mode;
        if (mode != IDLE && mode != WALL_SLIDE && mode != RELOCATE) s.lastAction = mode;
        s.activeTicks = ticks;
        s.velocity = velocity;
        PacketDistributor.sendToPlayer(player, new MovementResultPayload(player.getId(), player.level().dimension().location().toString(),
                ++s.actionId, jumpSequence, mode, ticks, velocity, s.airJumpSpent, s.airDashSpent,
                s.bash == null || s.bash.target.entity() == null ? -1 : s.bash.target.entity().getId(),
                s.bash != null ? s.bash.targetPosition : s.grapple != null ? s.grapple.point() : Vec3.ZERO));
    }
}
