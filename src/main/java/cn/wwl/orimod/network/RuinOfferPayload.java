package cn.wwl.orimod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RuinOfferPayload(BlockPos shrine, String kind, String problem, int clears, int bestTicks, boolean active) implements CustomPacketPayload {
    public static final Type<RuinOfferPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:ruin_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RuinOfferPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeBlockPos(v.shrine); buf.writeUtf(v.kind, 32); buf.writeUtf(v.problem, 32); buf.writeVarInt(v.clears); buf.writeVarInt(v.bestTicks); buf.writeBoolean(v.active); },
            buf -> new RuinOfferPayload(buf.readBlockPos(), buf.readUtf(32), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    public static RuinOfferPayload pending;
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
