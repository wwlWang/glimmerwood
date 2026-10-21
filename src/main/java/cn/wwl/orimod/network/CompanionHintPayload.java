package cn.wwl.orimod.network;

import cn.wwl.orimod.progression.CompanionHints.Hint;
import cn.wwl.orimod.progression.CompanionLines.Line;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Optional;

/** R108 protocol 9: tagged hints/lines. A zero-length sentinel also makes every v9 packet fail v2 decoding. */
public record CompanionHintPayload(Kind kind, String id, Optional<BlockPos> pos) implements CustomPacketPayload {
    public static final String VERSION = "9";
    public enum Kind { HINT, LINE }
    public static final int WIRE_VERSION = 9;
    public CompanionHintPayload(String id, Optional<BlockPos> pos) { this(Kind.HINT, id, pos); }
    public static CompanionHintPayload line(Line line) { return new CompanionHintPayload(Kind.LINE, line.id, Optional.empty()); }
    public static final int MAX_ID_LENGTH = 32, WORLD_LIMIT = 30_000_000;
    public static final Type<CompanionHintPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:companion_hint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionHintPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeByte(0); buf.writeByte(WIRE_VERSION); buf.writeByte(v.kind.ordinal()); buf.writeUtf(v.id, MAX_ID_LENGTH); buf.writeByte(v.pos.isPresent() ? 1 : 0); v.pos.ifPresent(buf::writeBlockPos); },
            buf -> {
                try {
                    if (buf.readUnsignedByte() != 0 || buf.readUnsignedByte() != WIRE_VERSION)
                        throw new DecoderException("Incompatible companion payload: expected protocol 9");
                    int kind = buf.readUnsignedByte();
                    if (kind >= Kind.values().length) throw new DecoderException("Unknown companion payload kind " + kind);
                    String id = buf.readUtf(MAX_ID_LENGTH);
                    int flag = buf.readUnsignedByte();
                    if (flag > 1) throw new DecoderException("Invalid companion hint position flag " + flag);
                    return new CompanionHintPayload(Kind.values()[kind], id, flag == 1 ? Optional.of(buf.readBlockPos()) : Optional.empty());
                } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                    throw new DecoderException("Invalid companion hint", e);
                }
            });

    public CompanionHintPayload {
        if (kind == null || pos == null) throw new IllegalArgumentException("Missing companion payload kind/position");
        if (kind == Kind.HINT) {
            var hint = Hint.byWireId(id);
            if (hint == null) throw new IllegalArgumentException("Unknown companion hint " + id);
            if (pos.isPresent() != (hint.radius > 0)) throw new IllegalArgumentException("Wrong companion hint position");
        } else if (Line.byId(id) == null || pos.isPresent()) {
            throw new IllegalArgumentException("Unknown companion line or unexpected line position: " + id);
        }
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
