package cn.wwl.orimod.progression;

import cn.wwl.orimod.config.OriServerConfig;
import cn.wwl.orimod.entity.CorruptionRules;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class BlightExposure {
    public static boolean affected(Player player){
        if(!(player instanceof ServerPlayer p)||p.isCreative()||p.isSpectator()||!OriServerConfig.blightEnabled()||Restoration.active(p))return false;
        return BlightData.get(p.serverLevel()).activeAt(p.position()).isPresent();
    }
    public static boolean suppress(Player p){return OriServerConfig.blightRecovery()&&affected(p);}
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var s=p.getData(SpiritData.ENERGY_STATE);boolean inside=affected(p);
        if(inside&&!s.blightInside)p.displayClientMessage(Component.translatable(SpiritData.isSpirit(p)&&OriServerConfig.blightDrain()>0?"message.orimod.blight_exposure":"message.orimod.blight_exposure_human"),true);
        s.blightInside=inside;
        if(inside&&SpiritData.isSpirit(p)&&p.level().getGameTime()%20==0)
            p.setData(SpiritData.ENERGY,CorruptionRules.drain(SpiritProgression.energy(p),OriServerConfig.blightDrain()));
    }
    private BlightExposure(){}
}
