#include "/lib/all_the_libs.glsl"

#include "/global/lighting/gbuffers.fsh"
#include "/global/lighting/spirit_self_light.glsl"

flat in int orimodHandMaterial;

/* DRAWBUFFERS:0 */
layout(location = 0) out vec4 Color;

void main() {
    mat3 TBN = tbn_decode(Normal, Tangent);
    float _PomShadow = 1;
    #if (defined PBR_POM) && (defined PBR_NORMAL)
        vec2 Texcoord = pom(TBN, _PomShadow);
        Color = textureGrad(gtexture, Texcoord, dCoordx, dCoordy);   
    #else
        vec2 Texcoord = texcoord;
        Color = texture(gtexture, Texcoord);   
    #endif

    Color *= glcolor;

    if(Color.a < alphaTestRef) {
        discard;
    }

    Color.rgb = to_linear(Color.rgb);
    vec3 ScreenPos = vec3(gl_FragCoord.xy*resolutionInv, gl_FragCoord.z);
    vec3 PlayerPos = view_player(ViewPos, false);
    
    float Dither = dither(gl_FragCoord.xy);

    vec3 TweakedLM = tweak_lightmap(Color.rgb, PlayerPos, LightmapCoords, texcoord, ScreenPos, TBN, Dither, _PomShadow);
    Color.xyz = TweakedLM;
    if (orimodHandMaterial != 0) {
        // GemRender's third atlas band contains emission RGB + paint coverage A,
        // not opacity. Read RGB here, with the base band's cutout/depth exactly once.
        vec3 emission = texture(gtexture, Texcoord + vec2(0, ORIMOD_EMISSION_OFFSET)).rgb;
        vec3 base = to_linear(texture(gtexture, Texcoord).rgb * glcolor.rgb);
        Color.rgb = orimod_neutral_light(base, TweakedLM, emission) + to_linear(emission);
    }
}
