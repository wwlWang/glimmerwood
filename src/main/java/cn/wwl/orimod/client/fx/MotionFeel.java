package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.OriParticles;
import cn.wwl.orimod.fx.SpiritParticleOptions;
import cn.wwl.orimod.movement.FeelTuning;
import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * R75: continuous motion feedback for Ori - a soft trailing light while moving, footstep motes,
 * dash afterimage silhouettes and a smoothed FOV kick. Constants live in {@link FeelTuning}.
 */
public final class MotionFeel {
    private static float fovKick, fovTarget;
    private static int lastStepTick;
    private static final java.util.Map<String, Object> DEBUG = new java.util.concurrent.ConcurrentHashMap<>();
    private MotionFeel() {}

    public static java.util.Map<String, Object> debug() { return java.util.Map.copyOf(DEBUG); }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) { fovKick = fovTarget = 0; return; }
        var visuals = OriClientConfig.visuals();
        long time = mc.level.getGameTime();
        int trails = 0;
        for (Player p : mc.level.players()) {
            if (!SpiritData.isSpirit(p) || p.isSpectator() || p.isInvisible() || p.distanceToSqr(mc.player) > 64 * 64) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue; // never obscure the first-person view
            trails += emit(p, time, visuals.quality().particleScale(), visuals.advancedParticles(), p == mc.player && dashing(p));
        }
        var self = mc.player;
        double speed = Math.hypot(self.getX() - self.xo, self.getZ() - self.zo) * 20;
        fovTarget = SpiritData.isSpirit(self) ? FeelTuning.fovKickTarget(dashing(self), speed) : 0;
        fovKick += (fovTarget - fovKick) * Math.min(1, FeelTuning.FOV_RATE / 20f);
        DEBUG.put("fovKick", fovKick); DEBUG.put("fovTarget", fovTarget); DEBUG.put("speed", speed); DEBUG.put("trailMotes", trails);
    }

    private static boolean dashing(Player p) {
        var state = p.getData(SpiritData.MOVEMENT);
        return state.mode == MoveMode.DASH && state.activeTicks > 0;
    }

    private static int emit(Player p, long time, double scale, boolean advanced, boolean dash) {
        var level = p.level(); var r = p.getRandom();
        double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
        int n = FeelTuning.trailMotes(speed, scale, advanced, time + p.getId());
        try {
            // Soft trail: motes spread along last tick's path, so the light reads as continuous at 20 TPS.
            for (int i = 0; i < n; i++) {
                double t = (i + r.nextDouble()) / Math.max(1, n);
                level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), i == 0 ? 0xE8FBFF : 0x9FE8FF, .26f + r.nextFloat() * .1f, 12 + r.nextInt(6)), true,
                        p.getX() - dx * t + (r.nextDouble() - .5) * .15, p.getY() + .45 - dy * t + (r.nextDouble() - .5) * .15, p.getZ() - dz * t + (r.nextDouble() - .5) * .15,
                        -dx * .05, .01, -dz * .05);
            }
            double ground = Math.hypot(dx, dz) * 20;
            if (p.onGround() && ground > 1.5 && (time + p.getId()) % FeelTuning.FOOTSTEP_SPACING_TICKS == 0) {
                int puffs = advanced ? 3 : 1;
                for (int i = 0; i < puffs; i++)
                    level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xCFF6FF, .16f, 10), false,
                            p.getX() + (r.nextDouble() - .5) * .3, p.getY() + .05, p.getZ() + (r.nextDouble() - .5) * .3,
                            (r.nextDouble() - .5) * .04, .03 + r.nextDouble() * .03, (r.nextDouble() - .5) * .04);
            }
            if (dash) {
                // Afterimage: a short-lived silhouette column at the previous position.
                for (int i = 0; i < 6; i++)
                    level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), 0xBFF2FF, i == 2 || i == 3 ? .45f : .32f, 6), true,
                            p.xo, p.yo + .1 + i * .16, p.zo, 0, 0, 0);
            }
        } catch (Throwable ignored) { }
        return n;
    }

    public static void fov(ComputeFovModifierEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getPlayer() != mc.player || fovKick < 1e-4f) return;
        float scale = mc.options.fovEffectScale().get().floatValue();
        event.setNewFovModifier(event.getNewFovModifier() * (1 + fovKick * scale));
    }
}
