// SpiritHands and SpiritPlayerVisual tag their materials only with this pack. Do not guess
// from atlas dimensions, heldItemId or the player's entityId: these also match
// ordinary hands/items. The marker is restored to opaque before the alpha test.
const float ORIMOD_HAND_ALPHA = 254.0 / 255.0;
const float ORIMOD_EMISSION_OFFSET = 2.0 / 3.0;
const float ORIMOD_BODY_LIGHT = 241.0;

bool orimod_hand_material(float alpha) {
    return abs(alpha - ORIMOD_HAND_ALPHA) < 0.25 / 255.0;
}

vec3 orimod_neutral_light(vec3 base, vec3 lit, vec3 emission) {
    // SpiritSelfLight supplies block-light 15, which this pack normally colours
    // torch-orange. An emissive spirit surface keeps that brightness and normal
    // shading, but its self-light is neutral. Non-emissive pixels keep world light.
    float emitting = step(0.5 / 255.0, max(emission.r, max(emission.g, emission.b)));
    float brightness = get_luminance(lit) / max(get_luminance(base), 1e-5);
    return mix(lit, base * brightness, emitting);
}
