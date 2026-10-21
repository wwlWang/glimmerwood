package cn.wwl.orimod.network;

import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.fx.SpiritFxPhase;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * One spirit effect lifecycle step; each client chooses how many particles to spawn from its own settings.
 * Channel version 2 (R18) added the phase; version 1 clients are refused at login rather than misreading it.
 */
public record SpiritFxPayload(SpiritFxKind kind, SpiritFxPhase phase, double x, double y, double z, int sourceEntity) implements CustomPacketPayload {
    public static final int NO_SOURCE = -1;
    /** R100: version 7 adds Tree Blessing; older clients cannot decode the appended kind. */
    public static final String VERSION = "8";
    public static final Type<SpiritFxPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:spirit_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritFxPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.kind.wireId()); buf.writeByte(v.phase.wireId()); buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); buf.writeVarInt(v.sourceEntity); },
            buf -> {
                int id = buf.readVarInt();
                var kind = SpiritFxKind.byWireId(id);
                if (kind == null) throw new DecoderException("Unknown spirit effect id " + id);
                int phaseId = buf.readByte();
                var phase = SpiritFxPhase.byWireId(phaseId);
                if (phase == null) throw new DecoderException("Unknown spirit effect phase " + phaseId);
                if (phase != SpiritFxPhase.BURST && phase != SpiritFxPhase.STOP_ALL && !kind.persistent())
                    throw new DecoderException("Phase " + phase + " on one-shot effect " + kind);
                return new SpiritFxPayload(kind, phase, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
            });

    /** A one-shot burst. */
    public SpiritFxPayload(SpiritFxKind kind, double x, double y, double z, int sourceEntity) {
        this(kind, SpiritFxPhase.BURST, x, y, z, sourceEntity);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(TYPE, CODEC, (payload, context) -> {
            // Only reached on a client; the dist check keeps the client class unresolved on servers.
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) cn.wwl.orimod.client.fx.SpiritFxClient.play(payload);
        });
    }
}
