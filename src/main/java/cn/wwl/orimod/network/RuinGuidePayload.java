package cn.wwl.orimod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RuinGuidePayload(String dimension, BlockPos entry, String kind) implements CustomPacketPayload {
    public static final Type<RuinGuidePayload> TYPE = new Type<>(ResourceLocation.parse("orimod:ruin_guide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RuinGuidePayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeUtf(v.dimension, 128); buf.writeBlockPos(v.entry); buf.writeUtf(v.kind, 32); },
            buf -> new RuinGuidePayload(buf.readUtf(128), buf.readBlockPos(), buf.readUtf(32)));
    public static RuinGuidePayload current;
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
