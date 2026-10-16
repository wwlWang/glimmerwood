package cn.wwl.orimod.network;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.combat.SpiritCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only an input edge crosses the wire; targets, charge, damage and timing are server-owned. */
public record CombatInputPayload(boolean cancel) implements CustomPacketPayload {
    public static final Type<CombatInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "combat_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatInputPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBoolean(value.cancel), buf -> new CombatInputPayload(buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (request.cancel) SpiritCombat.cancel(player, "input");
                else SpiritCombat.attack(player);
            }
        });
    }
}
