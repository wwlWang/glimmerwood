package cn.wwl.orimod.mixin;

import cn.wwl.orimod.progression.Restoration;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class RestorationMoveMixin {
    @Shadow public ServerPlayer player;
    // Main-thread handoff is mandatory; intercept before vanilla applies positional movement.
    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true)
    private void orimod$hold(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        if(!cn.wwl.orimod.spirit.PlayerLifecycle.current(player))return;
        var session=Restoration.session(player);if(session==null)return;
        float yaw=packet.getYRot(player.getYRot()),pitch=packet.getXRot(player.getXRot());
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch))return; // Vanilla disconnects invalid packets.
        player.setYRot(net.minecraft.util.Mth.wrapDegrees(yaw));player.setXRot(Math.clamp(pitch,-90,90));
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.fallDistance=0;
        var o=session.origin();double x=packet.getX(o.x),y=packet.getY(o.y),z=packet.getZ(o.z);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;
        if(o.distanceToSqr(x,y,z)>1e-5)player.connection.teleport(o.x,o.y,o.z,player.getYRot(),player.getXRot());
        ci.cancel();
    }
}
