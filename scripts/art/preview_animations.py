"""Re-author the clips on a saved character revision and render contact sheets, without exporting or saving.

Usage: blender -b --factory-startup --python scripts/art/preview_animations.py -- <manifest.json> <out_dir> <clip,clip,...> [frames]
Each clip gets side and front rows of evenly spaced frames, written as <out_dir>/<clip>.png.
"""
import bpy
import hashlib
import importlib
import json
import math
import sys
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
import character_animations
importlib.reload(character_animations)

args = sys.argv[sys.argv.index('--') + 1:]
manifest = json.loads((ROOT / args[0]).read_text(encoding='utf-8'))
out = Path(args[1]); out.mkdir(parents=True, exist_ok=True)
clips = args[2].split(',')
count = int(args[3]) if len(args) > 3 else 8
source = ROOT / manifest['blend']
assert hashlib.sha256(source.read_bytes()).hexdigest() == manifest['blend_sha256']
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = bpy.context.scene
rig = next(o for o in scene.objects if o.type == 'ARMATURE')
mesh = next(o for o in scene.objects if o.type == 'MESH' and o.vertex_groups)
for action in list(bpy.data.actions):
    bpy.data.actions.remove(action)
report, actions = character_animations.author(rig, mesh)
by_name = {a.name: a for a in actions}

scene.render.engine = 'BLENDER_WORKBENCH'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'MATERIAL'
scene.display.shading.show_shadows = False
scene.render.resolution_x = scene.render.resolution_y = 240
scene.render.image_settings.file_format = 'PNG'
world = bpy.data.worlds.new('preview'); world.color = (.16, .17, .2); scene.world = world
camera = bpy.data.objects.new('preview_camera', bpy.data.cameras.new('preview_camera'))
scene.collection.objects.link(camera); scene.camera = camera
camera.data.type = 'ORTHO'; camera.data.ortho_scale = 1.45
ground = bpy.data.objects.new('ground_line', None); scene.collection.objects.link(ground)
views = {'side': ((2.8, 0, .62), (math.radians(90), 0, math.radians(90))),
         'front': ((0, -2.8, .62), (math.radians(90), 0, 0))}
frames_dir = out / '_frames'; frames_dir.mkdir(exist_ok=True)
written = {}
for clip in clips:
    action = by_name.get(clip)
    if action is None:
        print('missing clip', clip); continue
    rig.animation_data.action = action
    if getattr(action, 'slots', None):
        rig.animation_data.action_slot = action.slots[0]
    first, last = map(int, action.frame_range)
    frames = [round(first + (last - first) * i / count) for i in range(count)]
    paths = []
    for view, (location, rotation) in views.items():
        camera.location = Vector(location); camera.rotation_euler = rotation
        for i, frame in enumerate(frames):
            scene.frame_set(frame)
            scene.render.filepath = str(frames_dir / f'{clip}_{view}_{i}.png')
            bpy.ops.render.render(write_still=True)
            paths.append(scene.render.filepath)
    written[clip] = {'frames': frames, 'paths': paths}
(out / 'preview.json').write_text(json.dumps({'clips': written, 'animation': report}, indent=1), encoding='utf-8')
