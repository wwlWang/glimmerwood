package cn.wwl.orimod.mixin;

import cn.wwl.orimod.client.render.SpiritAnimationEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class SpiritAnimationDamageMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void orimod$hurt(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(packet.entityId()) instanceof Player p)
            SpiritAnimationEvents.hurt(p, packet.getSource(level));
    }
}
