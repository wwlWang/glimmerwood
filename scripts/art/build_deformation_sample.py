"""Run with Blender --background --factory-startup --python. Builds the deformation sample.

Original downloads and review scenes are never edited. This is not the final animation set.
"""
import bpy
import hashlib
import json
import math
import struct
from pathlib import Path
from mathutils import Matrix, Quaternion, Vector

if not bpy.app.background:
    raise RuntimeError("Use a fresh background Blender process so unrelated actions cannot enter the export")

ROOT = Path(__file__).resolve().parents[2]
ALIASES = {
    "root": "_rootJoint", "pelvis": "Bone_02", "spine": "Bone.002_04", "neck": "Bone.005_07", "head": "Bone.006_08",
    "upper_arm_l": "Bone.008_0110", "forearm_l": "Bone.010_0112",
    "upper_arm_r": "Bone.137_0127", "forearm_r": "Bone.139_0129",
    "thigh_l": "Bone.013_0155", "knee_l": "Bone.014_0156", "ankle_l": "Bone.016_0158",
    "thigh_r": "Bone.154_0162", "knee_r": "Bone.155_0163", "ankle_r": "Bone.157_0165",
    "tail_a": "Bone.018_0143", "tail_b": "Bone.021_0146", "tail_c": "Bone.024_0149",
}
SOURCE = ROOT / "models/ori/3/ori_rigged.glb"
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == "6ed54f4909344667651cf4055878fa6a05c3a667e10cc81dbe24e93ba396b918"

scene = bpy.data.scenes.new("OriMod_DeformationSample")
scene["orimod_generated"] = True
bpy.context.window.scene = scene
bpy.ops.import_scene.gltf(filepath=str(SOURCE))
armature = next(o for o in scene.objects if o.type == "ARMATURE")
mesh = max((o for o in scene.objects if o.type == "MESH" and o.vertex_groups), key=lambda o: len(o.data.vertices))
armature.name = "Ori_DeformationRig"
mesh.name = "Ori_DeformationBody"

# Importer-created bone-shape helpers are not render assets.
for obj in list(scene.objects):
    if obj.type == "MESH" and obj != mesh:
        obj.hide_render = True
        obj.hide_set(True)

bpy.ops.object.select_all(action="DESELECT")
mesh.select_set(True)
bpy.context.view_layer.objects.active = mesh
reduction = mesh.modifiers.new("Preview triangle budget", "DECIMATE")
reduction.ratio = 0.20
while list(mesh.modifiers).index(reduction) > 0:
    bpy.ops.object.modifier_move_up(modifier=reduction.name)
bpy.ops.object.modifier_apply(modifier=reduction.name)
bpy.ops.object.vertex_group_limit_total(limit=4)
bpy.ops.object.vertex_group_normalize_all(lock_active=False)
for polygon in mesh.data.polygons:
    polygon.use_smooth = True

# Bake the imported corrective pose before reducing the rig. Keep the visible geometry fixed.
skin_modifier = next(m for m in mesh.modifiers if m.type == 'ARMATURE')
bpy.ops.object.modifier_apply(modifier=skin_modifier.name)
bpy.context.view_layer.objects.active = armature
bpy.ops.object.mode_set(mode='POSE')
bpy.ops.pose.armature_apply(selected=False)
bpy.ops.object.mode_set(mode='OBJECT')
original_bones = len(armature.data.bones)
keep = set(ALIASES.values())
for name in list(keep):
    keep.update(b.name for b in armature.data.bones[name].parent_recursive)
importance = {b.name: 0.0 for b in armature.data.bones}
group_names = {g.index: g.name for g in mesh.vertex_groups}
for vertex in mesh.data.vertices:
    for weight in vertex.groups:
        name = group_names[weight.group]
        if name in importance:
            importance[name] += weight.weight
for name in sorted(importance, key=importance.get, reverse=True):
    chain = {name, *(b.name for b in armature.data.bones[name].parent_recursive)}
    if len(keep | chain) <= 64:
        keep.update(chain)
fallback = {}
for bone in armature.data.bones:
    fallback[bone.name] = bone.name if bone.name in keep else next(
        (parent.name for parent in bone.parent_recursive if parent.name in keep), ALIASES['root'])
merged_weights = []
for vertex in mesh.data.vertices:
    merged = {}
    for entry in vertex.groups:
        name = fallback.get(group_names[entry.group])
        if name:
            merged[name] = merged.get(name, 0.0) + entry.weight
    merged_weights.append(merged)
mesh.vertex_groups.clear()
for name in sorted(keep):
    mesh.vertex_groups.new(name=name)
for index, weights in enumerate(merged_weights):
    for name, weight in weights.items():
        mesh.vertex_groups[name].add([index], weight, 'REPLACE')
bpy.ops.object.mode_set(mode='EDIT')
for bone in list(armature.data.edit_bones):
    if bone.name not in keep:
        armature.data.edit_bones.remove(bone)
bpy.ops.object.mode_set(mode='OBJECT')
skin_modifier = mesh.modifiers.new('Ori weighted skin', 'ARMATURE')
skin_modifier.object = armature
bpy.context.view_layer.objects.active = mesh
bpy.ops.object.vertex_group_limit_total(limit=4)
bpy.ops.object.vertex_group_normalize_all(lock_active=False)

material = bpy.data.materials.new("Ori_Pearl")
material.use_nodes = True
principled = next(n for n in material.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
principled.inputs["Base Color"].default_value = (0.64, 0.85, 0.92, 1.0)
principled.inputs["Roughness"].default_value = 0.72
principled.inputs["Metallic"].default_value = 0
# GemRender resolves images through Minecraft resources. An absent texture falls back
# to Flywheel's default atlas, so even a solid-colour material needs an explicit image.
texture_id = "orimod_devtools:textures/entity/ori_pearl.png"
texture_path = ROOT / "src/devtools/resources/assets/orimod_devtools/textures/entity/ori_pearl.png"
texture_path.parent.mkdir(parents=True, exist_ok=True)
pearl = bpy.data.images.new("Ori_Pearl", width=2, height=2, alpha=True)
pearl.generated_color = (0.64, 0.85, 0.92, 1.0)
pearl.filepath_raw = str(texture_path)
pearl.file_format = "PNG"
pearl.save()
pearl.pack()
texture_node = material.node_tree.nodes.new("ShaderNodeTexImage")
texture_node.image = pearl
material.node_tree.links.new(texture_node.outputs["Color"], principled.inputs["Base Color"])
principled.inputs["Base Color"].default_value = (1, 1, 1, 1)
mesh.data.materials.clear()
mesh.data.materials.append(material)

bpy.context.view_layer.update()
evaluated = mesh.evaluated_get(bpy.context.evaluated_depsgraph_get())
points = [evaluated.matrix_world @ v.co for v in evaluated.data.vertices]
minimum = Vector(tuple(min(v[i] for v in points) for i in range(3)))
maximum = Vector(tuple(max(v[i] for v in points) for i in range(3)))
scale = 1.2 / (maximum.z - minimum.z)
origin = Vector(((maximum.x + minimum.x) / 2, 0, minimum.z))
normalization = Matrix.Scale(scale, 4) @ Matrix.Translation(-origin)
for obj in list(scene.objects):
    if obj.parent is None:
        obj.matrix_world = normalization @ obj.matrix_world
bpy.context.view_layer.update()

# Keep the imported bind-pose correction and animate offsets from it.
baseline = {}
world_rotation = {}
for bone in armature.pose.bones:
    bone.rotation_mode = "QUATERNION"
    baseline[bone.name] = (bone.rotation_quaternion.copy(), bone.location.copy(), bone.scale.copy())
    world_rotation[bone.name] = (armature.matrix_world @ bone.matrix).to_quaternion()

def offset(alias, angle, axis=(1, 0, 0)):
    bone = armature.pose.bones[ALIASES[alias]]
    # Imported FBX bones have differently oriented local axes, including mirrored limbs.
    # Conjugate an anatomical world-axis offset into each bone's own baseline frame.
    world = world_rotation[bone.name]
    base = baseline[bone.name][0]
    delta = world.inverted() @ Quaternion(axis, angle) @ world
    bone.rotation_quaternion = base @ delta @ base.inverted() @ bone.rotation_quaternion

def reset():
    for bone in armature.pose.bones:
        rotation, location, scaling = baseline[bone.name]
        bone.rotation_quaternion = rotation
        bone.location = location
        bone.scale = scaling

scene.render.fps = 30
ankle_names = [ALIASES['ankle_l'], ALIASES['ankle_r']]
ankle_height = min((armature.matrix_world @ armature.pose.bones[n].head).z for n in ankle_names)
clips = [("idle", 60), ("run", 24), ("crouch", 60), ("bend", 60), ("tail_sway", 60)]
armature.animation_data_create()
created_actions = []
for name, length in clips:
    action = bpy.data.actions.new(name)
    action.use_fake_user = True
    armature.animation_data.action = action
    for frame in range(length + 1):
        scene.frame_set(frame)
        reset()
        offset("upper_arm_l", -1.15, (0, 1, 0))
        offset("upper_arm_r", 1.15, (0, 1, 0))
        phase = frame / length * math.tau
        envelope = (1 - math.cos(phase)) * 0.5
        if name == "idle":
            offset("spine", math.sin(phase) * 0.025)
            offset("head", math.sin(phase + 0.5) * 0.035)
            offset("tail_a", math.sin(phase - 0.6) * 0.10, (0, 0, 1))
        elif name == "run":
            stride = math.sin(phase)
            offset("thigh_l", stride * 0.48)
            offset("thigh_r", -stride * 0.48)
            offset("knee_l", max(0, -stride) * 0.55)
            offset("knee_r", max(0, stride) * 0.55)
            offset("spine", 0.10)
            offset("upper_arm_l", -stride * 0.32)
            offset("upper_arm_r", stride * 0.32)
            offset("tail_a", math.sin(phase - 0.5) * 0.13, (0, 0, 1))
        elif name == "crouch":
            for side in ("l", "r"):
                offset("thigh_" + side, -0.65 * envelope)
                offset("knee_" + side, 0.95 * envelope)
                offset("ankle_" + side, -0.30 * envelope)
            offset("spine", 0.35 * envelope)
        elif name == "bend":
            offset("spine", 0.65 * envelope)
            offset("neck", -0.25 * envelope)
        elif name == "tail_sway":
            for index, alias in enumerate(("tail_a", "tail_b", "tail_c")):
                offset(alias, math.sin(phase - index * 0.55) * 0.25, (0, 0, 1))

        if name == "crouch":
            bpy.context.view_layer.update()
            current_height = min((armature.matrix_world @ armature.pose.bones[n].head).z for n in ankle_names)
            pelvis = armature.pose.bones[ALIASES['root']]
            basis = pelvis.bone.matrix_local
            if pelvis.parent:
                basis = pelvis.parent.matrix @ pelvis.parent.bone.matrix_local.inverted() @ basis
            pelvis.location += (armature.matrix_world @ basis).to_3x3().inverted() @ Vector((0, 0, ankle_height - current_height))

        for bone in armature.pose.bones:
            bone.keyframe_insert("rotation_quaternion", frame=frame, group=bone.name)
            bone.keyframe_insert("location", frame=frame, group=bone.name)
            bone.keyframe_insert("scale", frame=frame, group=bone.name)
    created_actions.append(action)

armature.animation_data.action = created_actions[0]
scene.frame_start = 0
scene.frame_end = 60
scene.frame_set(0)
bpy.ops.object.select_all(action="DESELECT")
for obj in scene.objects:
    if obj.type in {"ARMATURE", "EMPTY"} or obj == mesh:
        obj.hide_set(False)
        obj.select_set(True)
bpy.context.view_layer.objects.active = armature

source_dir = ROOT / "art/source/deformation-sample"
source_dir.mkdir(parents=True, exist_ok=True)
revision = 1
while (source_dir / f"ori-deformation-v{revision:03}.blend").exists():
    revision += 1
blend_path = source_dir / f"ori-deformation-v{revision:03}.blend"
bpy.data.libraries.write(str(blend_path), {scene, *created_actions}, fake_user=True, compress=True)

export = ROOT / "src/devtools/resources/assets/orimod_devtools/models/ori_deformation_sample.glb"
export.parent.mkdir(parents=True, exist_ok=True)
# export_format is a dynamic Blender enum. Setting it gives Blender's supported-value validation.
bpy.ops.export_scene.gltf(filepath=str(export), export_format="GLB", use_selection=True, use_active_scene=True,
                          export_animations=True, export_animation_mode="ACTIONS",
                          export_force_sampling=True, export_frame_range=False,
                          export_copyright="Ori Rigged by Random modeler / Seea_Rooster, CC BY 4.0. Rig reduction and deformation animation by OriMod.")
# Preserve the embedded image for Blender round trips and add the renderer's resource
# binding to the GLB JSON. Blender does not export arbitrary image extras.
glb = export.read_bytes()
json_length, json_type = struct.unpack_from("<II", glb, 12)
assert json_type == 0x4E4F534A
gltf = json.loads(glb[20:20 + json_length])
assert len(gltf["images"]) == 1
gltf["images"][0].setdefault("extras", {})["resourceLocation"] = texture_id
encoded = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
encoded += b" " * (-len(encoded) % 4)
remaining_chunks = glb[20 + json_length:]
export.write_bytes(struct.pack("<III", 0x46546C67, 2, 20 + len(encoded) + len(remaining_chunks))
                   + struct.pack("<II", len(encoded), 0x4E4F534A) + encoded + remaining_chunks)
mesh.data.calc_loop_triangles()
report = {"stage": "Deformation sample; not the final character", "source": str(SOURCE.relative_to(ROOT).as_posix()),
          "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
          "blend": str(blend_path.relative_to(ROOT).as_posix()), "export": str(export.relative_to(ROOT).as_posix()),
          "triangles": len(mesh.data.loop_triangles), "bones": len(armature.data.bones), "original_bones": original_bones,
          "aliases": ALIASES, "clips": dict(clips), "fps": 30, "height": 1.2,
          "blender": bpy.app.version_string, "source_url": "https://sketchfab.com/3d-models/ori-rigged-d217801abaab4687b3aba56d5865e402",
          "license": "CC-BY-4.0", "author": "Random modeler (Seea_Rooster)",
          "texture": str(texture_path.relative_to(ROOT).as_posix()), "texture_resource": texture_id,
          "texture_sha256": hashlib.sha256(texture_path.read_bytes()).hexdigest(),
          "modifications": "Decimated to 20%, baked corrective pose, 64-bone hierarchy with merged weights, normalized height/origin, grey test material, five sampled deformation clips",
          "export_sha256": hashlib.sha256(export.read_bytes()).hexdigest()}
(source_dir / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False))
