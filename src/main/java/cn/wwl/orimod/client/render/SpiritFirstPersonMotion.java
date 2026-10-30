package cn.wwl.orimod.client.render;

/** The airborne hammer plunge is the only additional first-person pose. */
public final class SpiritFirstPersonMotion {
    private SpiritFirstPersonMotion() { }
    static float plungeWeight(boolean falling, float sinceStart, float sinceEnd) {
        return falling ? Math.clamp(sinceStart / 4, 0, 1) : 1 - Math.clamp(sinceEnd / 8, 0, 1);
    }
    static org.joml.Matrix4f plunge(float weight) {
        weight=Math.clamp(weight,0,1);
        return new org.joml.Matrix4f().translate(0,-.05f*weight,-.08f*weight)
                .rotateX((float)Math.toRadians(-30*weight));
    }
}
