#include "/lib/all_the_libs.glsl"

#include "/global/lighting/gbuffers.fsh"
#include "/global/lighting/spirit_self_light.glsl"
#include "/global/seasons.glsl"

/* DRAWBUFFERS:0 */
layout(location = 0) out vec4 Color;

void main() {
    Color = texture(gtexture, texcoord);
    vec2 lmcoord;
    float ao;
    vec4 overlayColor;
    vec4 materialColor;
    bool orimodBodyMaterial = abs(clrwl_vertexLight.x * 256.0 - ORIMOD_BODY_LIGHT) < 0.25;

    clrwl_computeFragment(Color, materialColor, lmcoord, ao, overlayColor);
    if (orimodBodyMaterial) {
        // This output contains GemRender's PBR shade, highlights AND emission.
        // Discarding it left Ori with only its raw base texture and warm lightmap.
        Color = materialColor;
        lmcoord.x = 240.0 / 256.0 + 1.0 / 32.0;
    }
    lmcoord = max(lmcoord * 1.06667 - 0.0625, 0);
    lmcoord.x = pow(lmcoord.x, 4.0001 - LM_FALLOFF_CURVE);

    #ifdef HANDHELD_LIGHTS
        float Dist = length(ViewPos);

        float HandheldLight = heldBlockLightValue;
        lmcoord.x = max(lmcoord.x, pow(max((HandheldLight - Dist) / 15.0, 0), 4.0 - HANDHELD_FALLOFF_CURVE));
    #endif

    #ifdef LM_FLICKER
        lmcoord.x *= (1 - LM_FLICKER_STRENGTH) + texture(noisetex, vec2(frameTimeCounter / 8, 0)).r * LM_FLICKER_STRENGTH;
    #endif

    vec4 glcolor = clrwl_vertexColor;
    #ifdef SEASONS
        glcolor = get_seasons_color(glcolor); 
    #endif
    if (!orimodBodyMaterial) Color.rgb *= glcolor.rgb;

    Color.rgb = mix(Color.rgb, overlayColor.rgb, overlayColor.a);

    if (!orimodBodyMaterial) Color.rgb *= ao;

    vec3 ScreenPos = vec3(gl_FragCoord.xy*resolutionInv, gl_FragCoord.z);
    vec3 PlayerPos = view_player(ViewPos, false);

    float Dither = dither(gl_FragCoord.xy);
    #if (defined DISTANT_HORIZONS) && (!defined VOXY)
        if (transition_to_dh(PlayerPos, false, Dither)) {
            discard;
        }
    #endif

    Color.rgb = to_linear(Color.rgb);

        mat3 TBN = tbn_decode(Normal, Tangent);
    vec3 TweakedLM = tweak_lightmap(Color.rgb, PlayerPos, lmcoord, texcoord, ScreenPos, TBN, Dither, 1);
    if (orimodBodyMaterial) {
        vec3 emission = texture(gtexture, texcoord + vec2(0, ORIMOD_EMISSION_OFFSET)).rgb;
        // The material output above already includes emission. Do not add it twice.
        Color.rgb = orimod_neutral_light(Color.rgb, TweakedLM, emission);
    } else {
        Color.xyz = TweakedLM;
    }
}
