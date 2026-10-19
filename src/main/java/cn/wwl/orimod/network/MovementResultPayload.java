package cn.wwl.orimod.network;

import cn.wwl.orimod.movement.MoveMode;
import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server-authorized motion. The owning client still moves through vanilla collision resolution. */
public record MovementResultPayload(int entityId, String dimension, int actionId, int jumpSequence, MoveMode mode, int ticks,
                                    Vec3 velocity, boolean airJumpSpent, boolean airDashSpent, int targetId, Vec3 targetPosition) implements CustomPacketPayload {
    public static final Type<MovementResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "movement_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementResultPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.entityId); buf.writeUtf(v.dimension, 128); buf.writeVarInt(v.actionId);
                buf.writeVarInt(v.jumpSequence); buf.writeVarInt(v.mode.wireId()); buf.writeVarInt(v.ticks); buf.writeDouble(v.velocity.x); buf.writeDouble(v.velocity.y); buf.writeDouble(v.velocity.z);
                buf.writeBoolean(v.airJumpSpent); buf.writeBoolean(v.airDashSpent); buf.writeInt(v.targetId);
                buf.writeDouble(v.targetPosition.x); buf.writeDouble(v.targetPosition.y); buf.writeDouble(v.targetPosition.z); },
            buf -> new MovementResultPayload(buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), MoveMode.fromWireId(buf.readVarInt()), buf.readVarInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readBoolean(), buf.readBoolean(), buf.readInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    @Override public Type<MovementResultPayload> type() { return TYPE; }

    public void apply(Player player) {
        if (player.getId() != entityId || !player.level().dimension().location().toString().equals(dimension)) return;
        var state = player.getData(SpiritData.MOVEMENT);
        if (actionId <= state.actionId) return;
        state.actionId = actionId;
        if (mode == RELOCATE) {
            state.predictedJumps.clear(); state.groundJumpAvailable = false;
            state.groundedAt = -100; state.groundLockUntil = 0; state.lastWallRefill = "";
            state.mode = IDLE; state.activeTicks = 0; state.relocations++;
        }
        MoveMode predicted = state.predictedJumps.remove(jumpSequence);
        boolean alreadyApplied = mode == predicted;
        if (mode == JUMP_REJECT) { state.predictedJumps.clear(); state.jumpPredictionRejections++; }
        else if (predicted != null && !alreadyApplied) state.jumpPredictionMismatches++;
        // Earlier acknowledgements must not refill a charge already used by a later prediction.
        if (state.predictedJumps.isEmpty() || airJumpSpent) state.airJumpSpent = airJumpSpent;
        if (state.predictedJumps.isEmpty() || airDashSpent) state.airDashSpent = airDashSpent;
        if (!SpiritData.isSpirit(player) || !state.active) return;
        if (mode == BASH_AIM && state.mode != BASH_AIM) {
            state.suspendedVelocity = player.getDeltaMovement();
            state.suspendedFallDistance = player.fallDistance;
        }
        state.mode = mode == RELOCATE ? IDLE : mode;
        state.activeTicks = ticks;
        state.velocity = velocity;
        state.targetId = targetId;
        state.targetPosition = targetPosition;
        if (mode != IDLE && mode != JUMP_REJECT && mode != RELOCATE) state.lastAction = mode;
        if (!alreadyApplied && (mode == DOUBLE_JUMP || mode == WALL_JUMP || mode == BASH_LAUNCH || mode == GRAPPLE_END || mode == LAUNCH)) player.fallDistance = 0;
        if (!alreadyApplied && (mode == JUMP || mode == DOUBLE_JUMP)) {
            player.setDeltaMovement(player.getDeltaMovement().x, velocity.y, player.getDeltaMovement().z);
            player.setOnGround(false);
        } else if (!alreadyApplied && (mode == WALL_JUMP || mode == BASH_LAUNCH || mode == GRAPPLE_END || mode == RESUME || mode == LAUNCH)) {
            if (mode == RESUME) player.fallDistance = state.suspendedFallDistance;
            player.setDeltaMovement(velocity);
            player.setOnGround(false);
        } else if (mode == JUMP_REJECT) {
            player.setDeltaMovement(velocity);
        }
    }
}
