package cn.wwl.orimod.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** R101 owns protocol 1, independently of the unchanged Spirit FX channel. */
public record GloomTidePayload(boolean active, float intensity) implements CustomPacketPayload {
    public static final String VERSION = "1";
    public static final Type<GloomTidePayload> TYPE = new Type<>(ResourceLocation.parse("orimod:gloom_tide"));
    public GloomTidePayload {
        if (!Float.isFinite(intensity) || intensity < 0 || intensity > 1 || (!active && intensity != 0))
            throw new IllegalArgumentException("Invalid tide intensity");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, GloomTidePayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeByte(value.active ? 1 : 0); buf.writeFloat(value.intensity); },
            buf -> {
                try {
                    int flag = buf.readUnsignedByte();
                    if (flag > 1) throw new DecoderException("Invalid tide active flag");
                    return new GloomTidePayload(flag == 1, buf.readFloat());
                } catch (IllegalArgumentException | IndexOutOfBoundsException e) { throw new DecoderException("Invalid tide payload", e); }
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        // NeoForge 21.1 PayloadRegistrar defaults to MAIN and wraps this in MainThreadPayloadHandler.
        // Dimension respawn is sent before PlayerChangedDimensionEvent. Fade also tolerates reversed delivery.
        event.registrar(VERSION).playToClient(TYPE, CODEC, (payload, context) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) cn.wwl.orimod.client.fx.GloomTideClient.receive(payload);
        });
    }
}
