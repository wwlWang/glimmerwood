package cn.wwl.orimod.devtools;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Offsets only the rendered camera; the player remains the controlled camera entity. */
public final class ReviewCamera {
    private static Object level;
    private static Vec3 offset, anchor;
    private static double targetHeight;
    private static boolean follow;
    private static CameraType previousType;

    private ReviewCamera() { }

    static void start(Vec3 viewOffset, double height, boolean follows) {
        start(viewOffset, height, follows, null);
    }

    static void start(Vec3 viewOffset, double height, boolean follows, Vec3 fixedAnchor) {
        var mc = Minecraft.getInstance();
        stop();
        if (mc.player == null || mc.level == null) throw new IllegalStateException("Player required");
        previousType = mc.options.getCameraType();
        offset = viewOffset; targetHeight = height; follow = follows;
        anchor = fixedAnchor == null ? mc.player.position() : fixedAnchor;
        level = mc.level;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.gameRenderer.setRenderHand(false);
    }

    static void stop() {
        if (level == null) return;
        var mc = Minecraft.getInstance();
        mc.options.setCameraType(previousType);
        mc.gameRenderer.setRenderHand(true);
        level = null;
    }

    static boolean active() { return level != null; }

    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != null && (mc.player == null || level != mc.level)) stop();
    }

    public static View view(float partial) {
        var mc = Minecraft.getInstance();
        if (level == null || mc.player == null || level != mc.level) return null;
        Vec3 focus = follow ? mc.player.getPosition(partial) : anchor;
        Vec3 eye = focus.add(offset), toward = focus.add(0,targetHeight,0).subtract(eye);
        float yaw = (float)Math.toDegrees(Math.atan2(-toward.x, toward.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance()));
        return new View(eye, yaw, pitch);
    }

    public record View(Vec3 position, float yaw, float pitch) { }
}
