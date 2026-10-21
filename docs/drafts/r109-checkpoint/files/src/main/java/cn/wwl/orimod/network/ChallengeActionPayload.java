package cn.wwl.orimod.network;

import cn.wwl.orimod.world.SpiritChallenge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ChallengeActionPayload(BlockPos shrine, boolean start) implements CustomPacketPayload {
    public static final Type<ChallengeActionPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:challenge_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeActionPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeBlockPos(v.shrine); buf.writeBoolean(v.start); }, buf -> new ChallengeActionPayload(buf.readBlockPos(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TYPE, CODEC, (request, context) -> { if (context.player() instanceof ServerPlayer player) SpiritChallenge.request(player, request.shrine, request.start); });
        registrar.playToClient(RuinOfferPayload.TYPE, RuinOfferPayload.CODEC, (reply, context) -> RuinOfferPayload.pending = reply);
        event.registrar("3").playToClient(RuinGuidePayload.TYPE, RuinGuidePayload.CODEC, (reply, context) -> RuinGuidePayload.current = reply);
    }
}
