vec3 apply_tonemap(vec3 X) {
    #if TONEMAP_OPERATOR == 1
    return ACESFilm(X);
    #elif TONEMAP_OPERATOR == 2
    return reinhard_jodie(X);
    #elif TONEMAP_OPERATOR == 3
    return ACES_slow(X);
    #elif TONEMAP_OPERATOR == 4
    return Hejl2015(X);
    #elif TONEMAP_OPERATOR == 5
    return Lottes(X);
    #elif TONEMAP_OPERATOR == 6
    return Uchimura(X);
    #elif TONEMAP_OPERATOR == 7
    return agxPunchy(X);
    #elif TONEMAP_OPERATOR == 8
    return PBRNeutralToneMapping(X);
    #endif
}

vec3 apply_saturation(vec3 Color, float Sat) {
    float luminance = get_luminance(Color);
    return mix(vec3(luminance), Color, Sat);
}

vec3 apply_vibrance(vec3 color, float intensity) {
    float mn = min(color.r, min(color.g, color.b));
    float mx = max(color.r, max(color.g, color.b));
    float sat = (1.0 - clamp(mx - mn, 0, 1)) * clamp(1.0 - mx, 0, 1) * get_luminance(color) * 5.0;
    vec3 lightness = vec3((mn + mx) * 0.5);

    return mix(color, mix(lightness, color, intensity), sat);
}

vec3 apply_contrast(vec3 color, float contrast) {
    return (color - 0.5) * contrast + 0.5;
}

// Mix colors, preserving the luminance of c1
vec3 mix_preserve_c1lum(vec3 C1, vec3 C2, float Weight) {
    float L1 = get_luminance(C1);

    vec3 CMixed = mix(C1, C2, Weight);
    float L = get_luminance(CMixed) + 1e-6;

    float Scale = L1 / L;

    return CMixed * Scale;
}

vec3 tint_underwater(vec3 FinalColor) {
    if (isEyeInWater == 1) {
        vec3 WaterColor = to_linear(vec3(f_WATER_RED, f_WATER_GREEN, f_WATER_BLUE));
        WaterColor = mix_preserve_c1lum(WaterColor, fogColor, f_BIOME_WATER_CONTRIBUTION);
        return mix_preserve_c1lum(FinalColor, WaterColor, 1) * 0.25;
    }
    return FinalColor;
}

vec3 rgb_to_xyz(vec3 rgb) {
    const mat3 XYZ_MATRIX = mat3(
            0.5149, 0.3654, 0.0248,
            0.3244, 0.6704, 0.1248,
            0.1607, 0.0642, 0.8504
        );
    return XYZ_MATRIX * rgb;
}

vec3 purkinje_effect(vec3 Color) {
    vec3 ColorXYZ = rgb_to_xyz(Color);
    float ScotopicLuminance = ColorXYZ.y * (1.33 * (1.0 + (ColorXYZ.y + ColorXYZ.z) / (ColorXYZ.x + 0.0001)) - 1.68);
    vec3 NightColor = ScotopicLuminance * vec3(0.6, 0.7, 1.1);

    float MaxLum = 0.05 + 0.05 * nightStrength * isOutdoorsSmooth + 0.05 * (1 - isOutdoorsSmooth);
    float BlendFactor = 1 - smoothstep(0.0, MaxLum, ScotopicLuminance);
    // if(BlendFactor > 0.001) return vec3(1,0,0);
    vec3 FinalColor = mix(Color, NightColor, PURKINJE_STRENGTH * BlendFactor);
    return FinalColor;
}

vec3 rgb_to_hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz),
                 vec4(c.gb, K.xy),
                 step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r),
                 vec4(c.r, p.yzx),
                 step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(
        abs(q.z + (q.w - q.y) / (6.0 * d + e)), // Hue
        d / (q.x + e),                          // Saturation
        q.x                                     // Value
    );
}

vec3 hsv_to_rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 get_min_light() {
    float MinLight = clamp(MIN_LIGHT_AMOUNT + screenBrightness * 0.1 - 0.1, 0.0, 0.5);
    MinLight = to_linear(MinLight);
    MinLight += nightVision / 7;
    return vec3(MinLight);
}

vec3 decode_lut(vec3 Color, vec2 FragCoord) {
    float TexSize = textureSize(colortex7, 0).x;
    float CellNr = pow(TexSize, 1/3.0);

    Color.rgb = pow(Color.rgb, vec3(1/2.2));
    Color += (ign(FragCoord.xy, false) - 0.5) / pow2(CellNr); 
    Color = clamp(Color, 0, 0.999);

    float OffsetInCell = fract(Color.b * CellNr);
    vec2 CellOffset = vec2(floor(OffsetInCell * CellNr), floor(Color.b * CellNr));
    vec2 Offset = Color.rg / CellNr;

    vec3 LutColor = to_linear(texture(colortex7, clamp(Offset + CellOffset / CellNr, 0.5 / TexSize, 1 - 0.5 / TexSize)).rgb);

    return LutColor;
}

vec3 display_lut(vec3 OldColor, vec2 FragCoord) {
    const float SIZE = 512.0;
    float CELL_NR = pow(SIZE, 1/3.0);
    if(any(greaterThan(FragCoord, vec2(SIZE)))) { return OldColor; }
    
    vec3 Color;
    vec2 Texcoord = FragCoord / SIZE;
    Texcoord.y = 1 - Texcoord.y;

    Color.rg = fract(Texcoord * CELL_NR);
    Color.b = (floor(Texcoord.x * CELL_NR) / CELL_NR + floor(Texcoord.y * CELL_NR)) / CELL_NR;
    return Color;
}