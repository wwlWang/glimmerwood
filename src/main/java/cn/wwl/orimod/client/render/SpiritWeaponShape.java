package cn.wwl.orimod.client.render;

import org.joml.Vector3f;

/** Item-space geometry shared by the renderer and grip/charge regression tests. */
final class SpiritWeaponShape {
    private SpiritWeaponShape() { }
    static final float BOW_X = .28125f, BOW_Y = .71875f;
    static float pullStage(float pull) { return pull >= .9f ? 1 : pull >= .65f ? .65f : 0; }
    /** Curved limbs extend away from the palm, string lies behind it. Forward is (-X,+Y). */
    static Vector3f bowLimb(float t) {
        float across = .61f * t, back = .30f * t * t;
        return new Vector3f(BOW_X + (across + back) * .7071068f,
                BOW_Y + (across - back) * .7071068f, .5f);
    }
    static Vector3f bowString(float pull) {
        float back = .30f + .30f * pullStage(pull);
        return new Vector3f(BOW_X + back * .7071068f, BOW_Y - back * .7071068f, .5f);
    }

    static Vector3f bowSide(float t) {
        return new Vector3f(.61f - .60f * t, -.61f - .60f * t, 0).normalize();
    }
}
