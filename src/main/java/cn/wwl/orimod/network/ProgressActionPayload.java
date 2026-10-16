package cn.wwl.orimod.network;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.progression.SpiritProgression;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Requests carry an action and catalogue ID; the server owns prices and prerequisites. */
public record ProgressActionPayload(String action, String target) implements CustomPacketPayload {
    public static final Type<ProgressActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "progress_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressActionPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.action, 24); buf.writeUtf(value.target, 32); },
            buf -> new ProgressActionPayload(buf.readUtf(24), buf.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TYPE, CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player) SpiritProgression.request(player, request.action, request.target);
        });
        registrar.playToClient(ProgressReplyPayload.TYPE, ProgressReplyPayload.CODEC, (reply, context) -> ProgressReplyPayload.accept(reply));
    }
}
