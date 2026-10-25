package cn.wwl.orimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Actual blade/palm fit, shared by the renderer and projection regressions. Camera space: forward = -Z. */
public final class SpiritBladeFraming {
    private SpiritBladeFraming() { }
    public record Fit(SpiritArmRig.Frame arm, Matrix4f item, float gripError) { }

    public static Fit fit(boolean left, Matrix4f input, Matrix4f display, boolean measure) {
        var point = display.transformPosition(new Vector3f(-.125f, -.125f, 0));
        var oldPoint = input.transformPosition(new Vector3f(point));
        var canonical = new Quaternionf().rotationY((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(25));
        var rest = SpiritArmRig.basis(new Vector3f(-.35f, .9f, -.2f).normalize()).rotateY((float) Math.toRadians(25));
        if (left) rest = SpiritArmRig.mirror(rest);
        var oldRotation = input.getUnnormalizedRotation(new Quaternionf()).mul(
                display.getUnnormalizedRotation(new Quaternionf()).mul(canonical.invert()).mul(rest).normalize());
        var socket = new Vector3f(left ? .015f : -.015f, -.035f, .012f);
        var origin = new Vector3f(oldPoint).sub(new Vector3f(socket).mul(SpiritArmRig.PAW_SCALE).rotate(oldRotation));
        var frame = SpiritArmRig.surface(left, origin, oldRotation);
        var contact = new Vector3f(socket).mul(SpiritArmRig.PAW_SCALE).rotate(frame.rotation()).add(frame.palm());
        var correction = new Matrix4f().translation(contact)
                .rotate(new Quaternionf(frame.rotation()).mul(new Quaternionf(oldRotation).invert()))
                .translate(new Vector3f(oldPoint).negate());
        var item = correction.mul(input);
        return new Fit(frame, item, measure ? item.transformPosition(new Vector3f(point)).distance(contact) : Float.NaN);
    }

    /** Conservative maximum glow blade: includes the .7-wide hit flare on both crossed planes.
     * Area is the full, unclipped NDC bounding box; visible is a uniform sample of both sheets. */
    public record Projection(float area, float nearClearance, float visible, float angle, float tipX, float tipY) { }
    public static Projection project(Matrix4f blade, float aspect) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX, near = minX;
        int count = 0, visible = 0;
        var v = new Vector3f();
        for (int i = 0; i <= 40; i++) for (int plane = 0; plane < 2; plane++) for (int side = -2; side <= 2; side++) {
            float along = -.08f + .62f * i / 40, off = side * .7f / 4;
            v.set(along, along, 0);
            if (plane == 0) v.add(-off * .7071068f, off * .7071068f, 0); else v.z += off;
            blade.transformPosition(v);
            near = Math.min(near, -v.z - .05f);
            float x = v.x / (-v.z * .7002075f * aspect), y = v.y / (-v.z * .7002075f);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            count++; if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && v.z < -.05f) visible++;
        }
        var base = blade.transformPosition(new Vector3f(-.08f, -.08f, 0));
        var tip = blade.transformPosition(new Vector3f(.48f, .48f, 0));
        // Pixel axis (the aspect cancels against the framebuffer width).
        float angle = (float) Math.toDegrees(Math.atan2(-(tip.y / -tip.z - base.y / -base.z), tip.x / -tip.z - base.x / -base.z));
        return new Projection((maxX-minX)*(maxY-minY), near, visible/(float)count, angle,
                tip.x/(-tip.z*.7002075f*aspect), tip.y/(-tip.z*.7002075f));
    }
}
