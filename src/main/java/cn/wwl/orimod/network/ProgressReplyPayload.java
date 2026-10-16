package cn.wwl.orimod.network;

import cn.wwl.orimod.OriMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProgressReplyPayload(Component message, boolean openGuide) implements CustomPacketPayload {
    public static final Type<ProgressReplyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "progress_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressReplyPayload> CODEC = StreamCodec.of(
            (buf, value) -> { ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).encode(buf, value.message); buf.writeBoolean(value.openGuide); },
            buf -> new ProgressReplyPayload(ByteBufCodecs.fromCodec(ComponentSerialization.CODEC).decode(buf), buf.readBoolean()));
    public static Component latest = Component.empty();
    public static long revision;
    public static boolean guidePending;
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void accept(ProgressReplyPayload reply) { latest = reply.message; revision++; guidePending |= reply.openGuide; }
}
