package cn.wwl.orimod.network;

import cn.wwl.orimod.fx.SpiritFxKind;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One spirit effect burst; each client chooses how many particles to spawn from its own settings. */
public record SpiritFxPayload(SpiritFxKind kind, double x, double y, double z, int sourceEntity) implements CustomPacketPayload {
    public static final int NO_SOURCE = -1;
    public static final Type<SpiritFxPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:spirit_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritFxPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.kind.wireId()); buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); buf.writeVarInt(v.sourceEntity); },
            buf -> {
                int id = buf.readVarInt();
                var kind = SpiritFxKind.byWireId(id);
                if (kind == null) throw new DecoderException("Unknown spirit effect id " + id);
                return new SpiritFxPayload(kind, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(TYPE, CODEC, (payload, context) -> {
            // Only reached on a client; the dist check keeps the client class unresolved on servers.
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) cn.wwl.orimod.client.fx.SpiritFxClient.play(payload);
        });
    }
}
