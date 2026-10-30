package cn.wwl.orimod.client;

import cn.wwl.orimod.progression.ExplorationShardRules;
import cn.wwl.orimod.progression.Shard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Gentle local lightmap lift; never creates, replaces or removes a potion effect. */
public final class LumenSight {
    private static ClientLevel world;
    private static boolean dark;
    private static float strength;
    private static long lightmapSamples;
    private LumenSight() {}
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level || mc.player == null) { world = mc.level; strength = 0; dark = false; }
        if (mc.player == null || mc.level == null) return;
        if (!Shard.LUMEN_SIGHT.equipped(mc.player) || mc.player.isSpectator()) { strength = 0; dark = false; return; }
        int light = mc.level.getMaxLocalRawBrightness(mc.player.blockPosition());
        dark = ExplorationShardRules.dark(dark, light);
        strength = ExplorationShardRules.fade(strength, dark);
    }
    public static float strength() { return strength; }
    public static java.util.Map<String,Object> diagnostics() {
        var mc=Minecraft.getInstance();
        return java.util.Map.of("dark",dark,"strength",strength,"lightmapSamples",lightmapSamples,
                "rawLight",mc.player==null||mc.level==null?-1:mc.level.getMaxLocalRawBrightness(mc.player.blockPosition()));
    }
    public static void brighten(Vector3f color) {
        lightmapSamples++;
        var player = Minecraft.getInstance().player;
        if (strength <= 0 || player == null || player.hasEffect(MobEffects.NIGHT_VISION)) return;
        float maximum = Math.max(color.x, Math.max(color.y, color.z));
        if (maximum > 0 && maximum < 1) color.lerp(new Vector3f(color).div(maximum), strength);
    }
}
