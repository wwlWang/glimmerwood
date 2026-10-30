package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.BlightAtmosphereRules;
import cn.wwl.orimod.network.BlightAtmospherePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Client computes distance from summaries only. Never used for gameplay occupancy. */
public final class BlightAtmosphereClient {
    private static ClientLevel world;
    private static BlightAtmospherePayload received;
    private static final BlightAtmosphereRules.Fade FADE=new BlightAtmosphereRules.Fade();
    private static int silence;
    public static void receive(BlightAtmospherePayload p){if(received!=null&&p.generation()<received.generation())return;received=p;silence=0;}
    public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;FADE.clear();}
        if(world==null||mc.isPaused())return;
        if(++silence>200)received=null;
        var camera=mc.gameRenderer.getMainCamera().getPosition();FADE.tick(target(camera.x,camera.z));
    }
    public static float target(double x,double z){
        if(world==null||received==null||!world.dimension().location().equals(received.dimension()))return 0;
        float value=0;for(var c:received.cores()){var p=BlockPos.of(c.center());value=Math.max(value,BlightAtmosphereRules.strength(Math.hypot(x-p.getX(),z-p.getZ()),c.radius(),c.purifying(),c.progress()));}return value;
    }
    public static float intensity(float partial){return world==null||world!=Minecraft.getInstance().level?0:FADE.value(partial);}
    public static boolean vanillaPriority(){var mc=Minecraft.getInstance();return mc.gameRenderer.getMainCamera().getFluidInCamera()!=FogType.NONE
            ||mc.getCameraEntity() instanceof LivingEntity e&&(e.hasEffect(MobEffects.BLINDNESS)||e.hasEffect(MobEffects.DARKNESS));}
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e){world=null;received=null;FADE.clear();silence=0;}
    public static Map<String,Object> diagnostics(){return Map.of("cores",received==null?List.of():received.cores(),"dimension",received==null?"none":received.dimension().toString(),"generation",received==null?-1:received.generation(),"target",FADE.target(),"intensity",intensity(1),"vanillaPriority",vanillaPriority());}
    private BlightAtmosphereClient(){}
}
