// Projection matrix-vector multiplication.
// The following three functions are licensed under the MIT license
// Copyright (c) 2025-2026 Luracasmus
vec4 proj_mmul(mat4 proj_mat, vec3 view) {
	return vec4(
		vec2(proj_mat[0].x, proj_mat[1].y) * view.xy,
		fma(proj_mat[2].z, view.z, proj_mat[3].z),
		proj_mat[2].w * view.z
	);
}

vec4 proj_inv_mmul(mat4 inv_proj_mat, vec3 ndc) {
	return vec4(
		vec2(inv_proj_mat[0].x, inv_proj_mat[1].y) * ndc.xy,
		inv_proj_mat[3].z,
		fma(inv_proj_mat[2].w, ndc.z, inv_proj_mat[3].w)
	);
}

vec3 rot_trans_mmul(mat4 rot_trans_mat, vec3 vec) {
	return mat3(rot_trans_mat) * vec + rot_trans_mat[3].xyz;
}

vec3 project_and_divide(mat4 Projection_mat, vec3 x) {
    vec4 HomogeneousPos = proj_mmul(Projection_mat, x);
    return HomogeneousPos.xyz / HomogeneousPos.w;
}

vec3 screen_view(vec3 x, bool IsDH) {
    x = x * 2 - 1;

    mat4 ProjMat = IsDH ? dhProjectionInverse : gbufferProjectionInverse;
    vec4 r = proj_inv_mmul(ProjMat, x);
    return r.xyz / r.w;
}

vec3 view_player(vec3 x, bool IsDH) {
    #ifdef VOXY
        mat4 Mat = IsDH ? vxModelViewInv : gbufferModelViewInverse;
    #else
        mat4 Mat = gbufferModelViewInverse;
    #endif
    return mat3(Mat) * x;
}

vec3 player_view(vec3 x, bool IsDH) {
    #ifdef VOXY
        mat4 Mat = IsDH ? vxModelView : gbufferModelView;
    #else
        mat4 Mat = gbufferModelView;
    #endif
    return mat3(Mat) * x;
}

vec3 view_screen(vec3 x, bool IsDH) {
    mat4 ProjMat = IsDH ? dhProjection : gbufferProjection;
    vec4 r = proj_mmul(ProjMat, x);
    x = r.xyz / r.w;
    x = x * 0.5 + 0.5;
    return x;
}

vec3 player_shadow(vec3 PlayerPos) {
    vec3 ShadowViewPos = rot_trans_mmul(shadowModelView, PlayerPos + gbufferModelViewInverse[3].xyz);
    vec3 ShadowPos = proj_mmul(shadowProjection, ShadowViewPos).xyz; //convert to shadow ndc space
    return ShadowPos;
}


#define to_linear(sRGB) ( sRGB * (sRGB * (sRGB * 0.305306011 + 0.682171111) + 0.012522878) )
