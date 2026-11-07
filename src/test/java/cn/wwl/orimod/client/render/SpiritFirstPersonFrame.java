package cn.wwl.orimod.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared first-person projection fixture: hand FOV is fixed at 70 degrees (GameRenderer#renderItemInHand). */
final class SpiritFirstPersonFrame {
    private SpiritFirstPersonFrame() { }
    static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 10);

    static JsonObject model(String name) {
        try (var in = SpiritFirstPersonFrame.class.getResourceAsStream("/assets/orimod/models/item/" + name + ".json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(in))).getAsJsonObject();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    /** ItemTransform#apply for a first-person context, plus ItemRenderer's built-in-entity (-.5) recentre. */
    static Matrix4f display(String name, boolean left) {
        var d = model(name).getAsJsonObject("display").getAsJsonObject("firstperson_" + (left ? "left" : "right") + "hand");
        var a = d.getAsJsonArray("rotation"); var t = d.getAsJsonArray("translation"); var s = d.getAsJsonArray("scale");
        float sign = left ? -1 : 1;
        return new Matrix4f().translation(sign * t.get(0).getAsFloat() / 16, t.get(1).getAsFloat() / 16, t.get(2).getAsFloat() / 16)
                // ItemTransform#apply negates Y/Z rotation for the left hand on top of the JSON's mirrored values.
                .rotateXYZ(rad(a.get(0).getAsFloat()), sign * rad(a.get(1).getAsFloat()), sign * rad(a.get(2).getAsFloat()))
                .scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat())
                .translate(-.5f, -.5f, -.5f);
    }

    /** GameRenderer#bobView at walk phase {@code phase} (walkDist) and amplitude {@code bob}. */
    static Matrix4f bob(float bob, float phase) {
        float f1 = -phase;
        return new Matrix4f().translation((float) Math.sin(f1 * Math.PI) * bob * .5f, -Math.abs((float) Math.cos(f1 * Math.PI) * bob), 0)
                .rotateZ(rad((float) Math.sin(f1 * Math.PI) * bob * 3))
                .rotateX(rad(Math.abs((float) Math.cos(f1 * Math.PI - .2f) * bob) * 5));
    }

    /** Spirit weapons other than the bow get SpiritHands' left inward nudge at renderItem's head. */
    static Matrix4f leftNudge(boolean left, boolean bow) {
        return left && !bow ? new Matrix4f().translation(.22f, .06f, -.06f) : new Matrix4f();
    }

    static Vector3f ndc(Matrix4f handSpace, Vector3f itemPoint) {
        return new Matrix4f(PROJECTION).mul(handSpace).transformProject(new Vector3f(itemPoint));
    }

    static Vector3f p(float x, float y) { return new Vector3f(x, y, .5f); }
    static float rad(float degrees) { return (float) Math.toRadians(degrees); }
}
