package cn.wwl.orimod.world;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;

/** Shared escape presentation/time arithmetic; each encounter retains its own damage and reward rules. */
public final class EscapeSupport {
    public static float remaining(long elapsed,int limit){return Math.clamp(1-elapsed/(float)limit,0,1);}
    public static void hurt(ServerPlayer p,net.minecraft.world.damagesource.DamageSource source,float amount){p.hurt(source,amount);}
    public static void title(ServerPlayer p,String title,String subtitle,int in,int stay,int out){
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in,stay,out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(subtitle)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title)));
    }
    private EscapeSupport(){}
}
