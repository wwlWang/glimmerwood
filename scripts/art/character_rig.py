"""Runtime skeleton. Historical l/r labels mean -X/+X, not anatomical sides.

The character faces Blender -Y (glTF +Z): its anatomical right is -X, named l.
The runtime maps these labels explicitly rather than mirroring the mesh.
"""
import bpy
from mathutils import Vector

BONES = {
    'root': '_rootJoint', 'pelvis': 'Bone_02', 'abdomen': 'Bone.001_03', 'spine': 'Bone.002_04',
    'neck': 'Bone.005_07', 'head': 'Bone.006_08', 'jaw': 'Bone.159_0102', 'muzzle': 'Bone.161_0104',
    'antenna_l_1': 'Bone.043_011', 'antenna_l_2': 'Bone.049_017', 'antenna_l_3': 'Bone.053_021',
    'antenna_r_1': 'Bone.090_039', 'antenna_r_2': 'Bone.096_045', 'antenna_r_3': 'Bone.100_049',
    'ear_l_1': 'Bone.072_00', 'ear_l_2': 'Bone.079_073', 'ear_l_3': 'Bone.085_079',
    'ear_r_1': 'Bone.119_085', 'ear_r_2': 'Bone.126_092', 'ear_r_3': 'Bone.132_098',
    'shoulder_l': 'Bone.007_0109', 'upper_arm_l': 'Bone.008_0110', 'forearm_l': 'Bone.010_0112', 'hand_l': 'Bone.011_0113',
    'finger_l_1': 'Bone.029_0114', 'finger_l_2': 'Bone.031_0116',
    'finger_l_3': 'Bone.033_0118', 'finger_l_4': 'Bone.035_0120',
    'thumb_l_1': 'Bone.037_0122', 'thumb_l_2': 'Bone.039_0124',
    'shoulder_r': 'Bone.136_0126', 'upper_arm_r': 'Bone.137_0127', 'forearm_r': 'Bone.139_0129', 'hand_r': 'Bone.140_0130',
    'finger_r_1': 'Bone.141_0131', 'finger_r_2': 'Bone.143_0133',
    'finger_r_3': 'Bone.145_0135', 'finger_r_4': 'Bone.147_0137',
    'thumb_r_1': 'Bone.149_0139', 'thumb_r_2': 'Bone.151_0141',
    'tail_1': 'Bone.018_0143', 'tail_2': 'Bone.020_0145', 'tail_3': 'Bone.023_0148',
    'tail_4': 'Bone.026_0151', 'tail_5': 'Bone.028_0153',
    'thigh_l': 'Bone.013_0155', 'knee_l': 'Bone.014_0156', 'hock_l': 'Bone.015_0157', 'ankle_l': 'Bone.016_0158', 'toe_l': 'Bone.017_0160',
    'thigh_r': 'Bone.154_0162', 'knee_r': 'Bone.155_0163', 'hock_r': 'Bone.156_0164', 'ankle_r': 'Bone.157_0165', 'toe_r': 'Bone.158_0166',
}

def simplify(rig, mesh):
    keep = set(BONES.values())
    fallback = {b.name: b.name if b.name in keep else next((p.name for p in b.parent_recursive if p.name in keep), BONES['root'])
                for b in rig.data.bones}
    group_names = {g.index: g.name for g in mesh.vertex_groups}
    merged = []
    for vertex in mesh.data.vertices:
        weights = {}
        for g in vertex.groups:
            target = fallback.get(group_names[g.group], BONES['root'])
            weights[target] = weights.get(target, 0) + g.weight
        merged.append(weights)
    mesh.vertex_groups.clear()
    for name in sorted(keep):
        mesh.vertex_groups.new(name=name)
    for i, weights in enumerate(merged):
        for name, weight in weights.items():
            mesh.vertex_groups[name].add([i], weight, 'REPLACE')
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    bones = rig.data.edit_bones
    for name in keep:
        b = bones[name]
        b.use_connect = False
        if b.parent:
            b.parent = bones[fallback[b.parent.name]]
    for name in ['jaw', 'muzzle', 'ear_l_1', 'ear_r_1', 'antenna_l_1', 'antenna_r_1']:
        bones[BONES[name]].parent = bones[BONES['head']]
    for b in list(bones):
        if b.name not in keep:
            bones.remove(b)
    bpy.ops.object.mode_set(mode='OBJECT')
    for semantic, original in BONES.items():
        rig.data.bones[original].name = semantic
    # Bone renaming updates groups only when the armature is the active modifier target.
    for semantic, original in BONES.items():
        if original in mesh.vertex_groups:
            mesh.vertex_groups[original].name = semantic
    bpy.context.view_layer.objects.active = mesh
    bpy.ops.object.vertex_group_limit_total(limit=4)
    bpy.ops.object.vertex_group_normalize_all(lock_active=False)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    attachments = {'grip_l': ('hand_l', (-0.475,0.015,0.648)), 'grip_r': ('hand_r', (0.478,0.015,0.648)),
                   'sole_l': ('toe_l', (-0.07,0.05,0.0)), 'sole_r': ('toe_r', (0.07,0.05,0.0)),
                   'body_center': ('spine', (0,0.015,0.61))}
    for name, (parent, position) in attachments.items():
        bone = rig.data.edit_bones.new(name)
        bone.head = rig.matrix_world.inverted() @ Vector(position)
        direction = Vector(((-1 if name.endswith('_l') else 1)*.025,0,0)) if name.startswith('grip_') else Vector((0,-.025,0))
        bone.tail = rig.matrix_world.inverted() @ (Vector(position)+direction)
        if name.startswith('grip_'):
            # Match vanilla arm coordinates: +Y runs shoulder -> fingers,
            # +Z points backwards. The runtime can then use ItemInHandLayer's
            # normal X=-90 / Y=180 transform for every item display context.
            bone.align_roll(rig.matrix_world.inverted().to_3x3() @ Vector((0,1,0)))
        bone.parent = rig.data.edit_bones[parent]
        bone.use_deform = False
    bpy.ops.object.mode_set(mode='OBJECT')
    assert len(rig.data.bones) <= 64
    return {'bones': len(rig.data.bones), 'attachments': list(attachments), 'source_bones': BONES}
