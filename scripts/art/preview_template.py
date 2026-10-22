"""Quick voxel preview of a structure template (flat colour per block), for iterating without a game client.

First flatten the template with the art venv (Blender's Python cannot load its numpy):
  python scripts/art/preview_template.py <template.nbt> > t.json
Then: blender -b --factory-startup --python scripts/art/preview_template.py -- t.json <out.png> [yaw_degrees] [eye_x,eye_y,eye_z,look_x,look_y,look_z]
(the optional eye/look pair is in template block coordinates, for a player's-eye view)
"""
import gzip
import json
import math
import sys

if __name__ == '__main__' and '--' not in sys.argv:
    import nbtlib
    data = nbtlib.File.parse(gzip.open(sys.argv[1]))
    names = [str(p['Name']).split(':')[1] for p in data['palette']]
    json.dump({'size': [int(v) for v in data['size']],
               'blocks': [[*(int(v) for v in b['pos']), names[int(b['state'])]] for b in data['blocks']]}, sys.stdout)
    sys.exit()

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
src, out = args[0], args[1]
yaw = math.radians(float(args[2])) if len(args) > 2 else math.radians(35)
data = json.load(open(src))

COLORS = [  # first substring match wins
    ('froglight', (1, .92, 1)), ('shroomlight', (1, .6, .3)), ('glow_lichen', (.55, .8, .6)), ('spirit_vine', (.4, .85, .8)), ('dripstone', (.55, .45, .4)), ('rooted', (.5, .38, .28)), ('chiseled', (.6, .6, .58)), ('deepslate', (.28, .28, .3)), ('calcite', (.9, .9, .88)), ('sea_lantern', (.8, .95, .95)), ('lantern', (1, .8, .4)), ('water', (.15, .35, .6)), ('lily', (.2, .5, .15)),
    ('spirit_leaves', (.35, .75, .7)), ('flowering', (.45, .6, .3)), ('dark_oak_leaves', (.12, .1, .14)), ('leaves', (.25, .5, .2)),
    ('withered', (.18, .14, .2)), ('spirit_log', (.75, .72, .65)), ('birch', (.85, .83, .78)), ('log', (.3, .22, .15)), ('wood', (.3, .25, .2)),
    ('blighted', (.17, .08, .22)), ('thorns', (.45, .12, .55)), ('mossy', (.42, .5, .38)), ('cracked', (.5, .5, .5)), ('brick', (.56, .56, .55)),
    ('cobble', (.45, .47, .44)), ('moss', (.3, .45, .18)), ('grass_block', (.33, .55, .22)), ('coarse', (.45, .33, .22)), ('dirt', (.45, .32, .2)),
    ('mud', (.25, .22, .2)), ('gravel', (.45, .43, .42)), ('tuff', (.42, .43, .4)), ('andesite', (.55, .55, .53)), ('stone', (.5, .5, .5)), ('planks', (.5, .36, .22)), ('slab', (.5, .38, .25)),
    ('fence', (.4, .3, .2)), ('chain', (.3, .3, .32)), ('vine', (.2, .45, .15)), ('fern', (.3, .55, .2)), ('grass', (.35, .6, .25)),
    ('carpet', (.33, .48, .2)), ('orchid', (.3, .6, 1)), ('lily_of', (.95, .95, .9)),
]
EMIT = ('froglight', 'lantern', 'thorns', 'shroomlight', 'glow_lichen', 'sea_lantern')


def colour(name):
    return next((c for k, c in COLORS if k in name), (1, 0, 1))


grid = {}
for x, y, z, name in data['blocks']:
    if name in ('air', 'cave_air', 'structure_void'): continue
    grid[x, y, z] = name
small = ('fern', 'grass', 'carpet', 'vine', 'lily', 'orchid', 'lily_of', 'chain', 'fence', 'thorns')
verts, faces, cols = [], [], []
dirs = [((1, 0, 0), [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)]), ((-1, 0, 0), [(0, 0, 1), (0, 1, 1), (0, 1, 0), (0, 0, 0)]),
        ((0, 1, 0), [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]), ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
        ((0, 0, 1), [(1, 0, 1), (1, 1, 1), (0, 1, 1), (0, 0, 1)]), ((0, 0, -1), [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)])]
for (x, y, z), name in grid.items():
    thin = any(k in name for k in small) and 'leaves' not in name
    h = .15 if ('carpet' in name or 'lily' in name) else .5 if 'slab' in name else 1
    for (dx, dy, dz), quad in dirs:
        nb = grid.get((x + dx, y + dy, z + dz))
        if nb and not thin and not any(k in nb for k in small) and not ('water' in nb and 'water' not in name): continue
        base = len(verts)
        for qx, qy, qz in quad:
            s = .5 if thin else 1
            o = (1 - s) / 2 if thin else 0
            verts.append((x + o + qx * s, z + o + qz * s, y + qy * (h if not thin else .8)))  # MC y-up -> Blender z-up
        faces.append((base, base + 1, base + 2, base + 3)); cols.append(name)
mesh = bpy.data.meshes.new('t'); mesh.from_pydata(verts, [], faces)
mats = {}
for i, name in enumerate(cols):
    key = next((k for k, _ in COLORS if k in name), 'x')
    if key not in mats:
        m = bpy.data.materials.new(key); m.use_nodes = True; p = m.node_tree.nodes['Principled BSDF']
        p.inputs['Base Color'].default_value = (*colour(name), 1); p.inputs['Roughness'].default_value = .9
        if key in EMIT: p.inputs['Emission Color'].default_value = (*colour(name), 1); p.inputs['Emission Strength'].default_value = 4
        if key == 'water': p.inputs['Alpha'].default_value = .75
        mesh.materials.append(m); mats[key] = len(mats)
    mesh.polygons[i].material_index = mats[key]
# MC z grows "south"; mirror so the view is not flipped.
obj = bpy.data.objects.new('t', mesh); bpy.context.collection.objects.link(obj); obj.scale.y = -1
size = data['size']
centre = Vector((size[0] / 2, -size[2] / 2, size[1] * .3))
scene = bpy.context.scene
scene.render.engine = 'CYCLES'; scene.cycles.samples = 24; scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1280, 860
scene.view_settings.view_transform = 'AgX'
w = bpy.data.worlds.new('w'); scene.world = w; w.use_nodes = True
w.node_tree.nodes['Background'].inputs['Color'].default_value = (.5, .62, .75, 1); w.node_tree.nodes['Background'].inputs['Strength'].default_value = .9
bpy.ops.object.light_add(type='SUN'); sun = bpy.context.object; sun.data.energy = 3.5; sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(-40))
cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); bpy.context.collection.objects.link(cam); scene.camera = cam
cam.data.lens = 32
dist = max(size) * 1.25
cam.location = centre + Vector((math.sin(yaw) * dist, math.cos(yaw) * dist, dist * .55))  # in front of the z=0 entrance
if len(args) > 3:  # player's-eye view, template coordinates (x, y up, z) -> Blender (x, -z, y)
    v = [float(c) for c in args[3].split(',')]
    cam.location = Vector((v[0], -v[2], v[1])); centre = Vector((v[3], -v[5], v[4])); cam.data.lens = 18
cam.rotation_euler = (centre - cam.location).to_track_quat('-Z', 'Y').to_euler()
scene.render.filepath = out
bpy.ops.render.render(write_still=True)
