package cn.wwl.orimod.network;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ToggleSpiritPayload() implements CustomPacketPayload {
    public static final Type<ToggleSpiritPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "toggle_spirit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSpiritPayload> CODEC = StreamCodec.unit(new ToggleSpiritPayload());
    @Override public Type<ToggleSpiritPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) SpiritForm.toggle(player);
        });
    }
}
