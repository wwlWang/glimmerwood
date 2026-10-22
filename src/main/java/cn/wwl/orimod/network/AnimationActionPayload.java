package cn.wwl.orimod.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** R112 cosmetic action identity. No velocity, target, damage, item or gameplay timer is accepted here. */
public record AnimationActionPayload(int entity, Action action, boolean offhand) implements CustomPacketPayload {
    public enum Action {
        MINE, PLACE, THROW, SPEAR_THROW, CAST_HOLD, CAST_STOP, CAST_RELEASE;
        public boolean clientRequest() { return ordinal() <= SPEAR_THROW.ordinal(); }
        public String clip() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final String VERSION = "1";
    public static final Type<AnimationActionPayload> TYPE = new Type<>(ResourceLocation.parse("orimod:animation_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnimationActionPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.entity); b.writeByte(p.action.ordinal()); b.writeBoolean(p.offhand); },
            b -> {
                int id = b.readVarInt(), action = b.readUnsignedByte();
                if (id < -1 || action >= Action.values().length) throw new DecoderException("Invalid Ori animation action " + id + ":" + action);
                int hand = b.readUnsignedByte();
                if (hand > 1) throw new DecoderException("Invalid Ori animation hand " + hand);
                return new AnimationActionPayload(id, Action.values()[action], hand == 1);
            });
    private static final java.util.Map<ServerPlayer, Long> LAST = new java.util.WeakHashMap<>();
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playBidirectional(TYPE, CODEC, (p, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                long now = player.level().getGameTime();
                if (p.entity != -1 || !p.action.clientRequest() || !player.isAlive() || player.isSpectator()
                        || !cn.wwl.orimod.spirit.SpiritData.isSpirit(player) || LAST.getOrDefault(player, Long.MIN_VALUE) == now) return;
                LAST.put(player, now);
                PacketDistributor.sendToPlayersTrackingEntity(player, new AnimationActionPayload(player.getId(), p.action, p.offhand));
            } else if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                cn.wwl.orimod.client.render.SpiritAnimationEvents.receive(p);
            }
        });
    }
    /** Called only after the spell's server validation; also reaches its owner. */
    public static void spell(ServerPlayer player, Action action) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new AnimationActionPayload(player.getId(), action, false));
    }
}
