"""Build a versioned, editable character from the unchanged licensed source.

Run in background Blender. Each execution writes a new revision; promotion into the
game resources is separate from authoring and visual review.
"""
import bpy
import bmesh
import hashlib
import json
import math
import sys
import struct
from pathlib import Path
from mathutils import Matrix, Quaternion, Vector

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
import character_rig
import character_materials
import character_animations
import character_export
SOURCE = ROOT / 'models/ori/3/ori_rigged.glb'
SOURCE_HASH = '6ed54f4909344667651cf4055878fa6a05c3a667e10cc81dbe24e93ba396b918'
assert bpy.app.background and hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_HASH
output_root = ROOT / 'art/source/character'
revision = 1
while (output_root / f'v{revision:03}').exists():
    revision += 1
OUTPUT = output_root / f'v{revision:03}'
OUTPUT.mkdir(parents=True)

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(SOURCE))
scene = bpy.context.scene
scene.name = 'Ori_Character'
rig = next(o for o in scene.objects if o.type == 'ARMATURE')
mesh = max((o for o in scene.objects if o.type == 'MESH' and o.vertex_groups), key=lambda o: len(o.data.vertices))
rig.name, mesh.name = 'Ori_Rig', 'Ori_Body'
for obj in list(scene.objects):
    if obj.type == 'MESH' and obj != mesh:
        bpy.data.objects.remove(obj, do_unlink=True)
bpy.ops.object.select_all(action='DESELECT')
mesh.select_set(True)
bpy.context.view_layer.objects.active = mesh
skin = next(m for m in mesh.modifiers if m.type == 'ARMATURE')
bpy.ops.object.modifier_apply(modifier=skin.name)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='POSE')
bpy.ops.pose.armature_apply(selected=False)
bpy.ops.object.mode_set(mode='OBJECT')
points = [mesh.matrix_world @ v.co for v in mesh.data.vertices]
minimum = Vector(tuple(min(v[i] for v in points) for i in range(3)))
maximum = Vector(tuple(max(v[i] for v in points) for i in range(3)))
scale = 1.2 / (maximum.z-minimum.z)
normalization = Matrix.Scale(scale, 4) @ Matrix.Translation(Vector((-(maximum.x+minimum.x)/2, 0, -minimum.z)))
for obj in list(scene.objects):
    if obj.parent is None:
        obj.matrix_world = normalization @ obj.matrix_world
bpy.context.view_layer.update()

# The source has duplicate vertices along UV seams. Decimating these independently
# created cracks in the technical sample. Weld first; keep the UV loop data.
bpy.context.view_layer.objects.active = mesh
bm = bmesh.new()
bm.from_mesh(mesh.data)
before = len(bm.verts)
bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=0.00003 / mesh.matrix_world.to_scale().length * math.sqrt(3))
bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
bm.to_mesh(mesh.data)
bm.free()
mesh.data.update()
mesh.data.calc_loop_triangles()
pre_triangles = len(mesh.data.loop_triangles)
reduction = mesh.modifiers.new('Runtime triangle budget', 'DECIMATE')
reduction.ratio = min(1, 12400/pre_triangles)
bpy.ops.object.modifier_apply(modifier=reduction.name)
mesh.data.normals_split_custom_set([(0, 0, 0)] * len(mesh.data.loops))
for p in mesh.data.polygons:
    p.use_smooth = True
skin = mesh.modifiers.new('Ori weighted skin', 'ARMATURE')
skin.object = rig
weights = {b.name: 0 for b in rig.data.bones}
group_names = {g.index: g.name for g in mesh.vertex_groups}
for v in mesh.data.vertices:
    for entry in v.groups:
        name = group_names[entry.group]
        if name in weights:
            weights[name] += entry.weight
report = {'source': str(SOURCE.relative_to(ROOT).as_posix()), 'source_sha256': SOURCE_HASH,
          'vertices_before_weld': before, 'vertices': len(mesh.data.vertices), 'triangles_before_reduce': pre_triangles,
          'bones': [{'name': b.name, 'parent': b.parent.name if b.parent else None,
                     'head': list(rig.matrix_world @ b.head_local), 'tail': list(rig.matrix_world @ b.tail_local),
                     'weight': weights[b.name]} for b in rig.data.bones]}
(OUTPUT / 'source-rig.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
mat = bpy.data.materials.new('Ori Pearl')
mat.use_nodes = True
shader = mat.node_tree.nodes.get('Principled BSDF')
shader.inputs['Base Color'].default_value = (0.64,0.85,0.92,1)
shader.inputs['Roughness'].default_value = 0.62
mesh.data.materials.clear()
mesh.data.materials.append(mat)
report['runtime_rig'] = character_rig.simplify(rig, mesh)
report['materials'] = character_materials.build(mesh, OUTPUT)
report['animation'], actions = character_animations.author(rig, mesh)
(OUTPUT / 'authoring.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
bpy.ops.wm.save_as_mainfile(filepath=str(OUTPUT / 'ori.blend'), compress=True)
export,resources=character_export.export_character(scene,rig,mesh,OUTPUT)
mesh.data.calc_loop_triangles()
manifest={'stage':'Character candidate; visual and runtime acceptance pending','revision':revision,
          'source':str(SOURCE.relative_to(ROOT).as_posix()),'source_sha256':SOURCE_HASH,
          'source_url':'https://sketchfab.com/3d-models/ori-rigged-d217801abaab4687b3aba56d5865e402',
          'license':'CC-BY-4.0','author':'Random modeler (Seea_Rooster)',
          'blend':str((OUTPUT/'ori.blend').relative_to(ROOT).as_posix()),
          'blend_sha256':hashlib.sha256((OUTPUT/'ori.blend').read_bytes()).hexdigest(),
          'export':str(export.relative_to(ROOT).as_posix()),'export_sha256':hashlib.sha256(export.read_bytes()).hexdigest(),
          'triangles':len(mesh.data.loop_triangles),'bones':len(rig.data.bones),'height':1.2,
          'blender':bpy.app.version_string,'textures':resources,'animation':report['animation'],
          'attachments':report['runtime_rig']['attachments']}
(OUTPUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps({'revision': revision, 'output': str(OUTPUT), 'vertices': len(mesh.data.vertices)}))
