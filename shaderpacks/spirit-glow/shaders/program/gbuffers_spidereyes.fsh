#include "/lib/all_the_libs.glsl"


#include "/global/lighting/gbuffers.fsh"

flat in int orimodHandMaterial;

/* DRAWBUFFERS:0 */
layout(location = 0) out vec4 Color;

void main() {
    // Ori's hand pass already combined both atlas bands. Other eyes are unchanged.
    if (orimodHandMaterial != 0) discard;
	Color = texture(gtexture, texcoord) * glcolor;
	if (Color.a < alphaTestRef) {
		discard; 
	}
}
