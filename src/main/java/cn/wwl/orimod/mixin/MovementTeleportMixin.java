package cn.wwl.orimod.mixin;

import cn.wwl.orimod.movement.SpiritMovement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class MovementTeleportMixin {
    @Shadow public ServerPlayer player;

    // Covers commands, items, challenge returns and vanilla position corrections, including short moves.
    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void orimod$teleported(CallbackInfo ci) { SpiritMovement.teleported(player); }
}
