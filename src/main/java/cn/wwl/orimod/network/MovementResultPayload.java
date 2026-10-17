package cn.wwl.orimod.network;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server-authorized motion. The owning client still moves through vanilla collision resolution. */
public record MovementResultPayload(int entityId, String dimension, int actionId, int jumpSequence, String mode, int ticks,
                                    Vec3 velocity, boolean airJumpSpent, boolean airDashSpent, int targetId, Vec3 targetPosition) implements CustomPacketPayload {
    public static final Type<MovementResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "movement_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementResultPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.entityId); buf.writeUtf(v.dimension, 128); buf.writeVarInt(v.actionId);
                buf.writeVarInt(v.jumpSequence); buf.writeUtf(v.mode, 24); buf.writeVarInt(v.ticks); buf.writeDouble(v.velocity.x); buf.writeDouble(v.velocity.y); buf.writeDouble(v.velocity.z);
                buf.writeBoolean(v.airJumpSpent); buf.writeBoolean(v.airDashSpent); buf.writeInt(v.targetId);
                buf.writeDouble(v.targetPosition.x); buf.writeDouble(v.targetPosition.y); buf.writeDouble(v.targetPosition.z); },
            buf -> new MovementResultPayload(buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readUtf(24), buf.readVarInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readBoolean(), buf.readBoolean(), buf.readInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    @Override public Type<MovementResultPayload> type() { return TYPE; }

    public void apply(Player player) {
        if (player.getId() != entityId || !player.level().dimension().location().toString().equals(dimension)) return;
        var state = player.getData(SpiritData.MOVEMENT);
        if (actionId <= state.actionId) return;
        state.actionId = actionId;
        String predicted = state.predictedJumps.remove(jumpSequence);
        boolean alreadyApplied = mode.equals(predicted);
        if (mode.equals("jump_reject")) { state.predictedJumps.clear(); state.jumpPredictionRejections++; }
        else if (predicted != null && !alreadyApplied) state.jumpPredictionMismatches++;
        // Earlier acknowledgements must not refill a charge already used by a later prediction.
        if (state.predictedJumps.isEmpty() || airJumpSpent) state.airJumpSpent = airJumpSpent;
        if (state.predictedJumps.isEmpty() || airDashSpent) state.airDashSpent = airDashSpent;
        if (!SpiritData.isSpirit(player) || !state.active) return;
        if (mode.equals("bash_aim") && !state.mode.equals("bash_aim")) {
            state.suspendedVelocity = player.getDeltaMovement();
            state.suspendedFallDistance = player.fallDistance;
        }
        state.mode = mode;
        state.activeTicks = ticks;
        state.velocity = velocity;
        state.targetId = targetId;
        state.targetPosition = targetPosition;
        if (!mode.equals("idle") && !mode.equals("jump_reject")) state.lastAction = mode;
        if (!alreadyApplied && (mode.equals("double_jump") || mode.equals("wall_jump") || mode.equals("bash_launch") || mode.equals("grapple_end"))) player.fallDistance = 0;
        if (!alreadyApplied && (mode.equals("jump") || mode.equals("double_jump"))) {
            player.setDeltaMovement(player.getDeltaMovement().x, velocity.y, player.getDeltaMovement().z);
            player.setOnGround(false);
        } else if (!alreadyApplied && (mode.equals("wall_jump") || mode.equals("bash_launch") || mode.equals("grapple_end") || mode.equals("resume"))) {
            if (mode.equals("resume")) player.fallDistance = state.suspendedFallDistance;
            player.setDeltaMovement(velocity);
            player.setOnGround(false);
        } else if (mode.equals("jump_reject")) {
            player.setDeltaMovement(velocity);
        }
    }
}
