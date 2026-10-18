#include "/lib/all_the_libs.glsl"
#include "/global/lighting/light_colors.vsh"

out vec2 texcoord;
out vec4 glcolor;

void main() {
	gl_Position = ftransform();
	init_colors();
	texcoord = get_texcoord(gl_TextureMatrix[0], gl_MultiTexCoord0);
	glcolor = gl_Color;
	#if TAA_MODE >= 2
		gl_Position.xy += taaJitter * gl_Position.w;
	#endif
}
