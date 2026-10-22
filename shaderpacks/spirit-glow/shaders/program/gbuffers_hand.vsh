#define GBUFFERS_BASIC

#include "/lib/all_the_libs.glsl"

#include "/global/lighting/gbuffers.vsh"
#include "/global/lighting/spirit_self_light.glsl"

flat out int orimodHandMaterial;

void main() {
	
	init_generic();
    orimodHandMaterial = int(orimod_hand_material(glcolor.a));
    if (orimodHandMaterial != 0) glcolor.a = 1.0;
	#if TAA_MODE >= 2
    gl_Position.xy += taaJitter * gl_Position.w;
    #endif
}
