package cn.wwl.orimod.network;

import cn.wwl.orimod.OriMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Inputs, never client-authored speed, charge counts or destination positions. */
public record MovementInputPayload(int sequence, long gameTick, int flags, float forward, float left, float yaw, float pitch) implements CustomPacketPayload {
    public static final int ACTIVE = 1, JUMP = 2, DASH = 4, CROUCH = 8, BASH = 16;
    public static final Type<MovementInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "movement_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementInputPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.sequence); buf.writeLong(value.gameTick); buf.writeByte(value.flags); buf.writeFloat(value.forward); buf.writeFloat(value.left); buf.writeFloat(value.yaw); buf.writeFloat(value.pitch); },
            buf -> new MovementInputPayload(buf.readVarInt(), buf.readLong(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
    @Override public Type<MovementInputPayload> type() { return TYPE; }
    public boolean has(int flag) { return (flags & flag) != 0; }
}
