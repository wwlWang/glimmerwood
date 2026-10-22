package cn.wwl.orimod.client.render;

/** Same light-source floor as the world body. Keep sky light, including with Iris enabled. */
final class SpiritSelfLight {
    private SpiritSelfLight() { }
    static int packed(int ambient) { return ambient & 0xFFFF0000 | 15 << 4; }
    // GemRender's PBR atlas packs base/surface/emission into three equal vertical bands.
    static final float EMISSION_UV_OFFSET = 2f / 3f;
    // Explicit vertex material tag understood only by our pack's hand/eyes programs.
    // The ordinary (and other shader pack) paths retain alpha 255 exactly.
    static final int HAND_MATERIAL_ALPHA = 254;
    static int handAlpha(boolean spiritGlow) { return spiritGlow ? HAND_MATERIAL_ALPHA : 255; }
    // GemRender exposes packed light, not instance colour, from its entity visual.
    // Vanilla block-light coordinates are multiples of 16; 241 is an explicit
    // material tag, decoded back to 240 before Spirit Glow shades the body.
    static final int BODY_MATERIAL_LIGHT = 241;
    static int bodyLight(int ambient, boolean spiritGlow) {
        return ambient & 0xFFFF0000 | (spiritGlow ? BODY_MATERIAL_LIGHT : 240);
    }
}
