package cn.wwl.orimod.network;

import cn.wwl.orimod.progression.CompanionHints.Hint;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Optional;

/** R97: server-selected Glimmer guidance. Version 1 uses a bounded, validated id and a strict optional position. */
public record CompanionHintPayload(String id, Optional<BlockPos> pos) implements CustomPacketPayload {
    public static final String VERSION = "1";
    public static final int MAX_ID_LENGTH = 32, WORLD_LIMIT = 30_000_000;
    public static final Type<CompanionHintPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:companion_hint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionHintPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeUtf(v.id, MAX_ID_LENGTH); buf.writeByte(v.pos.isPresent() ? 1 : 0); v.pos.ifPresent(buf::writeBlockPos); },
            buf -> {
                try {
                    String id = buf.readUtf(MAX_ID_LENGTH);
                    int flag = buf.readUnsignedByte();
                    if (flag > 1) throw new DecoderException("Invalid companion hint position flag " + flag);
                    return new CompanionHintPayload(id, flag == 1 ? Optional.of(buf.readBlockPos()) : Optional.empty());
                } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                    throw new DecoderException("Invalid companion hint", e);
                }
            });

    public CompanionHintPayload {
        var hint = Hint.byWireId(id);
        if (hint == null) throw new IllegalArgumentException("Unknown companion hint " + id);
        if (pos == null || pos.isPresent() != (hint.radius > 0)) throw new IllegalArgumentException("Wrong companion hint position");
        if (pos.isPresent()) {
            var p = pos.get();
            if (Math.abs((long) p.getX()) > WORLD_LIMIT || Math.abs((long) p.getZ()) > WORLD_LIMIT || p.getY() < -2048 || p.getY() > 2047)
                throw new IllegalArgumentException("Companion hint position outside world");
            pos = Optional.of(p.immutable());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToClient(TYPE, CODEC, (payload, context) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) cn.wwl.orimod.client.fx.CompanionMotion.hint(payload);
        });
    }
}
