package cn.wwl.orimod.client.render;

/** Same light-source floor as the world body. Keep sky light, including with Iris enabled. */
final class SpiritSelfLight {
    private SpiritSelfLight() { }
    static int packed(int ambient) { return ambient & 0xFFFF0000 | 15 << 4; }
    // GemRender's PBR atlas packs base/surface/emission into three equal vertical bands.
    static final float EMISSION_UV_OFFSET = 2f / 3f;
}
