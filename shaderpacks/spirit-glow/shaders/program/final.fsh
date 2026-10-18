#include "/lib/all_the_libs.glsl"

noperspective in vec2 texcoord;

#include "/global/post/taa.glsl"
#include "/global/post/cas.glsl"

// CAS, vignette, color grading

vec3 film_grain(vec3 Color, vec2 Pos) {
    Pos.x += fract(float(frameCounter) / 4.14159) * 234;
    Pos.y -= fract(float(frameCounter) / 5.49382) * 567;
    Color += (texture(noisetex, Pos.xy / 255).rgb - 0.5) * FILM_GRAIN_STRENGTH;
    return Color;
}

vec3 apply_vignette(vec3 Color, vec2 Pos) {
    Pos = Pos - 0.5;
    Pos *= VIGNETTE_OPACITY;
    float Strength = len2(Pos);
    Strength = pow(Strength, 2 - VIGNETTE_FALLOFF);
    Color *= 1 - min(Strength, 1);
    return Color;
}

vec3 channel_mixer(vec3 Color) {
    vec3 NewColor = vec3(0);
    NewColor += Color.r * vec3(CM_R_IN_R, CM_G_IN_R, CM_B_IN_R);
    NewColor += Color.g * vec3(CM_R_IN_G, CM_G_IN_G, CM_B_IN_G);
    NewColor += Color.b * vec3(CM_R_IN_B, CM_G_IN_B, CM_B_IN_B);
    return clamp(NewColor, 0, 1);
}

vec3 color_balance(vec3 Color) {
    vec3 Shadows = clamp(Color + vec3(SHADOWS_CYAN_TO_RED, SHADOWS_MAGENTA_TO_GREEN, SHADOWS_YELLOW_TO_BLUE), 0, 1);
    vec3 Mids = clamp(Color + vec3(MIDS_CYAN_TO_RED, MIDS_MAGENTA_TO_GREEN, MIDS_YELLOW_TO_BLUE), 0, 1);
    vec3 Highs = clamp(Color + vec3(HIGHS_CYAN_TO_RED, HIGHS_MAGENTA_TO_GREEN, HIGHS_YELLOW_TO_BLUE), 0, 1);

    float OldL = get_luminance(Color);
    vec3 NewColor = OldL < 0.5 ? mix(Shadows, Mids, OldL * 2) : mix(Mids, Highs, OldL * 2 - 1);
    float NewL = get_luminance(NewColor);

    NewColor *= OldL / NewL;
    return NewColor;
}

#ifdef SPIRIT_PAINTERLY
// Small 4-sector Kuwahara filter: flattens texture noise into soft painted patches while keeping edges.
vec3 spirit_painterly(vec2 Coord) {
    const int R = SPIRIT_PAINTERLY_RADIUS;
    vec2 Texel = 1.0 / vec2(viewWidth, viewHeight);
    vec3 Mean[4]; vec3 Sq[4];
    for(int k = 0; k < 4; k++) { Mean[k] = vec3(0); Sq[k] = vec3(0); }
    for(int y = -R; y <= R; y++) for(int x = -R; x <= R; x++) {
        vec3 C = texture(colortex0, Coord + vec2(x, y) * Texel).rgb;
        vec3 C2 = C * C;
        if(x <= 0 && y <= 0) { Mean[0] += C; Sq[0] += C2; }
        if(x >= 0 && y <= 0) { Mean[1] += C; Sq[1] += C2; }
        if(x <= 0 && y >= 0) { Mean[2] += C; Sq[2] += C2; }
        if(x >= 0 && y >= 0) { Mean[3] += C; Sq[3] += C2; }
    }
    float N = float((R + 1) * (R + 1));
    vec3 Best = Mean[0] / N; float BestVar = 1e9;
    for(int k = 0; k < 4; k++) {
        vec3 M = Mean[k] / N;
        vec3 V = abs(Sq[k] / N - M * M);
        float Var = V.r + V.g + V.b;
        if(Var < BestVar) { BestVar = Var; Best = M; }
    }
    return Best;
}
#endif

#ifdef SPIRIT_STYLE
// Spirit Glow grade (display space): tinted darks, teal foliage, saturation shaped by luminance, soft highlights.
vec3 spirit_grade(vec3 C) {
    float L = get_luminance(C);
    #if SPIRIT_SHADOW_HUE == 0
        const vec3 DarkMul = vec3(0.55, 0.88, 1.08); const vec3 DarkLift = vec3(0.000, 0.030, 0.045); // teal
    #elif SPIRIT_SHADOW_HUE == 1
        const vec3 DarkMul = vec3(0.58, 0.74, 1.28); const vec3 DarkLift = vec3(0.010, 0.022, 0.070); // blue
    #else
        const vec3 DarkMul = vec3(0.80, 0.62, 1.25); const vec3 DarkLift = vec3(0.035, 0.010, 0.065); // purple
    #endif
    // warm, saturated pixels (torches, lava, sunset) are the focal accents: keep them out of the cool grade
    float Warm = clamp((C.r - max(C.g * 0.8, C.b)) * 4, 0, 1) * smoothstep(0.15, 0.45, L);

    // darks hold the frame in deep blue/teal/purple
    float Dark = (1 - smoothstep(0.0, 0.55, L)) * (1 - Warm);
    C = mix(C, C * DarkMul + DarkLift, Dark * SPIRIT_SHADOW_TINT);

    // yellow-green foliage leans towards emerald/teal, the way painted forests read
    float Green = clamp((C.g - max(C.r, C.b)) * 5, 0, 1) * (1 - Warm);
    C = mix(C, vec3(C.r * 0.82, C.g * 0.96, max(C.b, C.g * 0.78)), Green * SPIRIT_FOLIAGE_TEAL);

    // more colour in the mid tones, less in deep darks and highlights, so glows stay near white
    L = get_luminance(C);
    float Mid = smoothstep(0.06, 0.3, L) * (1 - smoothstep(0.55, 0.95, L));
    float High = smoothstep(0.7, 1.0, L) * (1 - Warm);
    float Sat = 1 + SPIRIT_MID_SATURATION * Mid - 0.45 * SPIRIT_HIGHLIGHT_SOFTNESS * High;
    C = max(vec3(0), mix(vec3(L), C, Sat));

    // soft shoulder: ease the top end towards white instead of clipping
    vec3 Shoulder = 1 - (1 - C) * (1 - C);
    C = mix(C, mix(C, Shoulder, 0.5), SPIRIT_HIGHLIGHT_SOFTNESS * High);

    #if SPIRIT_POSTERIZE > 0
        C = mix(C, floor(C * SPIRIT_POSTERIZE + 0.5) / SPIRIT_POSTERIZE, 0.5);
    #endif
    return clamp(C, 0, 1);
}
#endif

layout(location = 0) out vec4 Color;

void main() {
    // Plain #ifdef on each option: Iris only offers a toggle for defines it finds in #ifdef / #ifndef.
    #ifdef SPIRIT_PAINTERLY
        Color = vec4(spirit_painterly(texcoord), 1);
    #else
    #ifdef IMAGE_SHARPENING
        Color = vec4(CAS(colortex0), 1);
    #else
        Color = texture(colortex0, texcoord);
    #endif
    #endif

    Color.rgb = pow(Color.rgb, vec3(1/2.2));

    #ifdef FILM_GRAIN
    Color.rgb = film_grain(Color.rgb, gl_FragCoord.xy);
    #endif

    Color.rgb = apply_vignette(Color.rgb, texcoord);

    #ifdef DEBUG_DISPLAY_NEUTRAL_LUT
        Color.rgb = display_lut(Color.rgb, gl_FragCoord.xy);
    #endif

    Color.rgb = apply_vibrance(Color.rgb, VIBRANCE);
    Color.rgb = apply_saturation(Color.rgb, SATURATION);
    Color.rgb = apply_contrast(Color.rgb, CONTRAST);

    #ifdef COLOR_BALANCING
        Color.rgb = color_balance(Color.rgb);
    #endif

    #ifdef CHANNEL_MIXER
        Color.rgb = channel_mixer(Color.rgb);
    #endif

    #ifdef SPIRIT_STYLE
        Color.rgb = spirit_grade(Color.rgb);
    #endif

    #ifndef DEBUG_DISPLAY_NEUTRAL_LUT
        Color.xyz += (ign(gl_FragCoord.xy, false) - 0.5) / 128; 
    #endif
}
