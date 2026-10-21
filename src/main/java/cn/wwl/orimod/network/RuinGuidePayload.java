package cn.wwl.orimod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RuinGuidePayload(String dimension, BlockPos entry, String kind) implements CustomPacketPayload {
    // v1 began with a UTF dimension capped at 128 chars (384 encoded bytes).
    // This length sentinel rejects v2 in that exact old decoder before reading a body.
    public static final int VERSION=2;
    public static net.minecraft.network.chat.Component title(String kind){
        return switch(kind){case "glades_outpost","spirit_tree","blighted_grounds"->net.minecraft.network.chat.Component.translatable("guide.orimod.target."+kind);default->cn.wwl.orimod.world.RuinKind.byId(kind).title();};
    }
    public RuinGuidePayload {
        if(!java.util.Set.of("forest_spring","creek_ruins","cave_altar","glades_outpost","spirit_tree","blighted_grounds").contains(kind)
                || ResourceLocation.tryParse(dimension)==null || Math.abs((long)entry.getX())>30000000 || Math.abs((long)entry.getZ())>30000000)throw new IllegalArgumentException("Invalid guide target");
    }
    public static final Type<RuinGuidePayload> TYPE = new Type<>(ResourceLocation.parse("orimod:ruin_guide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RuinGuidePayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(385); buf.writeByte(VERSION); buf.writeUtf(v.dimension, 128); buf.writeBlockPos(v.entry); buf.writeUtf(v.kind, 32); },
            buf -> {if(buf.readVarInt()!=385 || buf.readUnsignedByte()!=VERSION)throw new io.netty.handler.codec.DecoderException("Incompatible guide protocol: expected 2");try{return new RuinGuidePayload(buf.readUtf(128),buf.readBlockPos(),buf.readUtf(32));}catch(IllegalArgumentException e){throw new io.netty.handler.codec.DecoderException("Invalid guide target",e);}});
    public static RuinGuidePayload current;
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
