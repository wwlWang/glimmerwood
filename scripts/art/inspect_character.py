"""Read a versioned Blender character, report rig geometry and render review angles."""
import bpy
import json
import sys
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
source = ROOT / (args[0] if args else 'art/source/deformation-sample/ori-deformation-v008.blend')
output = ROOT / (args[1] if len(args) > 1 else 'evidence/character-source-inspection-2026-10-18')
output.mkdir(parents=True, exist_ok=True)
assert bpy.app.background
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = next(s for s in bpy.data.scenes if any(o.type == 'ARMATURE' for o in s.objects))
bpy.context.window.scene = scene
rig = next(o for o in scene.objects if o.type == 'ARMATURE')
mesh = max((o for o in scene.objects if o.type == 'MESH'), key=lambda o: len(o.data.vertices))
rig.animation_data_create()
catalog = len(args)>2 and args[2]=='catalog'
if len(args) > 2 and not catalog:
    rig.animation_data.action = bpy.data.actions[args[2]]
    scene.frame_set(int(args[3]) if len(args) > 3 else 0)
else:
    rig.animation_data.action = None
    for bone in rig.pose.bones:
        bone.matrix_basis.identity()
bpy.context.view_layer.update()
world = [mesh.matrix_world @ v.co for v in mesh.data.vertices]
parents = list(range(len(world)))
def root(i):
    while parents[i] != i:
        parents[i] = parents[parents[i]]
        i = parents[i]
    return i
for edge in mesh.data.edges:
    a, b = edge.vertices
    parents[root(a)] = root(b)
parts = {}
for i, point in enumerate(world):
    parts.setdefault(root(i), []).append(i)
def bounds(indices):
    return [[round(f(world[i][a] for i in indices), 5) for a in range(3)] for f in (min, max)]
groups = {g.index: g.name for g in mesh.vertex_groups}
weights = {b.name: 0 for b in rig.data.bones}
for vertex in mesh.data.vertices:
    for g in vertex.groups:
        if groups[g.group] in weights:
            weights[groups[g.group]] += g.weight
report = {'source': str(source), 'bounds': bounds(range(len(world))),
          'uv_layers': [uv.name for uv in mesh.data.uv_layers],
          'components': [{'vertices': len(ids), 'bounds': bounds(ids)} for ids in sorted(parts.values(), key=len, reverse=True)],
          'bones': [{'name': b.name, 'parent': b.parent.name if b.parent else None,
                     'head': list(rig.matrix_world @ b.head_local), 'tail': list(rig.matrix_world @ b.tail_local),
                     'weight': weights[b.name]} for b in rig.data.bones]}
(output / 'geometry.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
for obj in scene.objects:
    obj.hide_render = obj.type != 'MESH' or obj != mesh
scene.render.engine = 'CYCLES'
scene.cycles.samples = 24
scene.render.resolution_x = 720
scene.render.resolution_y = 900
scene.render.resolution_percentage = 100
scene.world = bpy.data.worlds.new('Review world')
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (0.06, 0.09, 0.13, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0.4
scene.view_settings.view_transform = 'AgX'
for loc, power, size in [((2,-3,3), 150, 3), ((-2,1,2), 110, 2), ((0,2,0.6), 70, 1.5)]:
    light = bpy.data.lights.new('Review softbox', 'AREA')
    light.energy, light.shape, light.size = power, 'DISK', size
    obj = bpy.data.objects.new(light.name, light)
    scene.collection.objects.link(obj)
    obj.location = loc
    obj.rotation_euler = (Vector((0,0,0.65))-obj.location).to_track_quat('-Z','Y').to_euler()
camera = bpy.data.cameras.new('Review camera')
camera.type, camera.ortho_scale = 'ORTHO', 1.42
obj = bpy.data.objects.new(camera.name, camera)
scene.collection.objects.link(obj)
scene.camera = obj
if catalog:
    scene.render.resolution_x=480
    scene.render.resolution_y=600
    scene.cycles.samples=12
    for action in sorted(bpy.data.actions,key=lambda a:a.name):
        if not action.layers:
            continue
        rig.animation_data.action=action
        frame=round(action.frame_range[1]*.4)
        if action.name in {'blade_1','blade_2','blade_3'}:
            frame=round(action.frame_range[1]*.25)
        scene.frame_set(frame)
        for angle,loc in [('three-quarter',(2,-3,1.15)),('side',(3,0,.68))]:
            obj.location=loc
            obj.rotation_euler=(Vector((0,0,.60))-obj.location).to_track_quat('-Z','Y').to_euler()
            scene.render.filepath=str(output/f'{action.name}-{frame:02}-{angle}.png')
            bpy.ops.render.render(write_still=True)
    print(json.dumps({'catalog':str(output)}))
    sys.exit(0)
for name, loc in [('front', (0,-3,0.68)), ('side', (3,0,0.68)), ('back', (0,3,0.68)), ('three-quarter', (2,-3,1.15))]:
    if len(args)>4 and name!=args[4]:
        continue
    obj.location = loc
    obj.rotation_euler = (Vector((0,0,0.60))-obj.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath = str(output / f'{name}.png')
    bpy.ops.render.render(write_still=True)
print(json.dumps({'output': str(output), 'bones': len(rig.data.bones), 'components': len(parts)}))
