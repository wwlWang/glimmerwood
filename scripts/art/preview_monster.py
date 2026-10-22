"""Render a frame strip per clip from an exported creature GLB (round-trips the runtime file, not the .blend).

Run: blender -b --factory-startup --python scripts/art/preview_monster.py -- <creature.glb> <out_dir> [frames_per_clip] [extent] [front|three_quarter]
Writes <out_dir>/<clip>.png strips (left to right through the clip) and textured.png (a still with the baked textures).
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
glb, out = args[0], Path(args[1]); out.mkdir(parents=True, exist_ok=True)
per = int(args[2]) if len(args) > 2 else 6
extent = float(args[3]) if len(args) > 3 else 1.3
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
rig = next(o for o in bpy.context.scene.objects if o.type == 'ARMATURE')
scene = bpy.context.scene
scene.render.engine = 'CYCLES'; scene.cycles.samples = 16; scene.cycles.use_denoising = True
scene.render.resolution_x = scene.render.resolution_y = 360
scene.view_settings.view_transform = 'AgX'
w = bpy.data.worlds.new('w'); scene.world = w; w.use_nodes = True
w.node_tree.nodes['Background'].inputs['Color'].default_value = (.05, .07, .09, 1)
bpy.ops.mesh.primitive_plane_add(size=40); g = bpy.context.object
m = bpy.data.materials.new('g'); m.use_nodes = True; m.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (.12, .16, .1, 1)
g.data.materials.append(m)
for loc, e in (((-3, -4, 5), 500), ((4, 2, 3), 200)):
    bpy.ops.object.light_add(type='AREA', location=loc); L = bpy.context.object; L.data.energy = e * extent; L.data.size = 3
    L.rotation_euler = (Vector((0, 0, extent * .4)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); bpy.context.collection.objects.link(cam); scene.camera = cam
target = Vector((0, 0, extent * .42))
view = Vector((0, -1, .35)) if len(args) > 4 and args[4] == 'front' else Vector((1.1, -1.5, .7))
cam.location = target + view.normalized() * extent * 2.6
cam.rotation_euler = (target - cam.location).to_track_quat('-Z', 'Y').to_euler()
fps = scene.render.fps
for action in bpy.data.actions:
    rig.animation_data.action = action
    if hasattr(rig.animation_data, 'action_slot') and action.slots: rig.animation_data.action_slot = action.slots[0]
    start, end = action.frame_range
    frames = []
    for i in range(per):
        f = start + (end - start) * i / max(1, per - 1)
        scene.frame_set(int(f), subframe=f - int(f))
        p = out / f'_{action.name}_{i}.png'; scene.render.filepath = str(p); bpy.ops.render.render(write_still=True); frames.append(p)
    print('CLIP', action.name, start, end)
