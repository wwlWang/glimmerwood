package cn.wwl.orimod.client.render;

import cn.wwl.orimod.movement.FeelTuning;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R79: third-person spirit camera. The camera trails the body slightly (frame-rate independent exponential follow,
 * clamped) and leads along the horizontal velocity, so fast moves read as motion instead of a rigid tether.
 * Offsets are applied after vanilla's own wall clamp and dropped when they would put the camera inside a block.
 */
public final class SpiritCamera {
    private static Vec3 smoothed, lead = Vec3.ZERO, last;
    private static long lastNanos;
    private static final java.util.Map<String, Object> DEBUG = new java.util.concurrent.ConcurrentHashMap<>();
    private SpiritCamera() {}

    public static java.util.Map<String, Object> debug() { return java.util.Map.copyOf(DEBUG); }

    public static void reset() { smoothed = null; lead = Vec3.ZERO; last = null; }

    public static Vec3 follow(BlockGetter level, Entity entity, Vec3 vanilla, float partial) {
        if (!(entity instanceof Player player) || !SpiritData.isSpirit(player) || player.isSpectator()) { reset(); return null; }
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(.1, (now - lastNanos) / 1e9); lastNanos = now;
        Vec3 body = player.getPosition(partial).add(0, player.getEyeHeight(), 0);
        if (smoothed == null || last == null || body.distanceToSqr(last) > 16) { smoothed = body; lead = Vec3.ZERO; }
        double velocityDt = dt > 0 && last != null ? dt : 1;
        Vec3 velocity = last == null ? Vec3.ZERO : body.subtract(last).scale(1 / velocityDt);
        last = body;
        smoothed = smoothed.add(body.subtract(smoothed).scale(FeelTuning.follow(FeelTuning.CAMERA_LAG_RATE, dt)));
        Vec3 lag = smoothed.subtract(body);
        lag = new Vec3(lag.x, lag.y * FeelTuning.CAMERA_LAG_VERTICAL, lag.z);
        if (lag.length() > FeelTuning.CAMERA_LAG_MAX) { lag = lag.normalize().scale(FeelTuning.CAMERA_LAG_MAX); smoothed = body.add(lag); }
        if (dt > 0) lead = lead.add(FeelTuning.leadTarget(velocity).subtract(lead).scale(FeelTuning.follow(FeelTuning.CAMERA_LEAD_RATE, dt)));
        Vec3 target = vanilla.add(lag).add(lead);
        DEBUG.put("lag", lag.length()); DEBUG.put("lead", lead.length());
        var hit = level.clip(new ClipContext(body, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) { DEBUG.put("blocked", true); return null; }
        DEBUG.put("blocked", false);
        return target;
    }
}
