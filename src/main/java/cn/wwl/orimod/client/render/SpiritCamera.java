package cn.wwl.orimod.client.render;

import cn.wwl.orimod.movement.FeelTuning;
import cn.wwl.orimod.movement.CameraFollow;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Third-person spirit camera: a single critically damped follow, with bounded lag and no velocity lead.
 * Independent lag/lead filters previously left a forward offset that slid backwards after the player stopped.
 * Offsets are applied after vanilla's own wall clamp and dropped when they would put the camera inside a block.
 */
public final class SpiritCamera {
    private static CameraFollow state;
    private static Vec3 last;
    private static Entity lastEntity;
    private static BlockGetter lastLevel;
    private static long lastNanos;
    private static final java.util.Map<String, Object> DEBUG = new java.util.concurrent.ConcurrentHashMap<>();
    private SpiritCamera() {}

    public static java.util.Map<String, Object> debug() { return java.util.Map.copyOf(DEBUG); }

    public static void reset() { state = null; last = null; lastEntity = null; lastLevel = null; lastNanos = 0; DEBUG.clear(); }

    public static Vec3 follow(BlockGetter level, Entity entity, Vec3 vanilla, float partial) {
        if (!(entity instanceof Player player) || !SpiritData.isSpirit(player) || player.isSpectator()) { reset(); return null; }
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(.5, (now - lastNanos) / 1e9); lastNanos = now;
        Vec3 body = player.getPosition(partial).add(0, player.getEyeHeight(), 0);
        if (state == null || lastEntity != entity || lastLevel != level || last == null || body.distanceToSqr(last) > 16)
            state = CameraFollow.at(body);
        last = body; lastEntity = entity; lastLevel = level;
        if (!Minecraft.getInstance().isPaused()) state = state.step(body, dt);
        Vec3 lag = state.position().subtract(body);
        lag = new Vec3(lag.x, lag.y * FeelTuning.CAMERA_LAG_VERTICAL, lag.z);
        Vec3 target = vanilla.add(lag);
        DEBUG.put("lag", lag.length()); DEBUG.put("offset", java.util.List.of(lag.x, lag.y, lag.z));
        var hit = level.clip(new ClipContext(body, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) { DEBUG.put("blocked", true); return null; }
        DEBUG.put("blocked", false);
        return target;
    }
}
