package cn.wwl.orimod.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** R40: an updraft lift is server-sanctioned; reset the "floating too long" counter while it lasts. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerFloatingAccessor {
    @Accessor("aboveGroundTickCount")
    void orimod$setAboveGroundTickCount(int ticks);
}
