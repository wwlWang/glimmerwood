"""Build Kuro's Feather (R113): the glide feather held overhead, and the replacement look of the elytra item.

Run: blender -b --factory-startup --python scripts/art/build_feather.py
Writes art/source/feather/vNNN/{kuro_feather.glb, kuro_feather_{base,emission,surface}.png, manifest.json}.
Static mesh, no rig. Origin at the grip (the bottom of the bare quill), +Y toward the tip (glTF +Y up),
the vane spreads along +/-X and cups slightly toward -Z (the side facing the holder). Length FEATHER_LENGTH metres.
Original geometry: a long, gently bowed flight feather with an asymmetric vane, a few splits on the trailing edge
and a pale blue glow along its edges and tip.
"""
import bpy
import bmesh
import hashlib
import json
import math
import struct
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
NAME = 'kuro_feather'
FEATHER_LENGTH = 1.35
out_root = ROOT / 'art/source/feather'
revision = 1 + max((int(d.name[1:]) for d in out_root.glob('v[0-9][0-9][0-9]')), default=0)
OUT = out_root / f'v{revision:03}'
OUT.mkdir(parents=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
L = FEATHER_LENGTH
WHITE, BLUE, GLOW = Vector((.95, .96, .98)), Vector((.70, .86, 1.0)), Vector((.30, .58, 1.0))


def rachis(s):
    """Quill centre line: bows a little sideways and back, so the feather reads as a curve, not a plank."""
    return Vector((.07 * L * math.sin(math.pi * s) * s, L * s, .05 * L * s * s))


def width(s):
    if s < .13: return 0
    u = (s - .13) / .87
    return .21 * L * math.sin(math.pi * min(1, u * 1.05) ** .8) ** .9 * (1 - .25 * u)


def vane(sign, share, splits):
    """One side of the vane as a grid; barbs sweep toward the tip and the surface cups toward -Z."""
    bm = bmesh.new()
    lb, le = bm.verts.layers.float_color.new('base'), bm.verts.layers.float_color.new('emit')  # before any vertex
    ns, nb = 110, 12
    rows = []
    for i in range(ns + 1):
        s = .13 + .87 * i / ns
        row = []
        for j in range(nb + 1):
            t = j / nb
            w = width(s) * share
            # A notch: the trailing edge parts slightly at each split.
            gap = sum(math.exp(-((s - c) / .012) ** 2) for c in splits) * t * t
            p = rachis(s) + Vector((sign * t * w, t * w * .55 + gap * .02 * L, -.045 * L * math.sin(math.pi * t) * (w / (.21 * L) + .2)))
            if gap > .3: p.x -= sign * gap * w * .25 * t
            v = bm.verts.new(p)
            edge = t ** 3
            tip = max(0, (s - .8) / .2) ** 2
            # Faint barbs: stripes that run with the barb sweep (from the quill out and toward the tip).
            barb = .5 + .5 * math.sin((s * L - t * w * .55) / (.022 * L) * math.tau)
            shade = 1 - .07 * barb * (.3 + .7 * t)
            v[lb] = (*(WHITE.lerp(BLUE, .55 * edge + .4 * tip) * shade), 1)
            v[le] = (*(GLOW * (.10 + .55 * edge + .6 * tip)), 1)
            row.append(v)
        rows.append(row)
    for i in range(ns):
        for j in range(nb):
            a, b, c, d = rows[i][j], rows[i + 1][j], rows[i + 1][j + 1], rows[i][j + 1]
            bm.faces.new((a, b, c, d) if sign > 0 else (d, c, b, a))
    return bm


def to_object(bm, name):
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me); bpy.context.collection.objects.link(o)
    return o


parts = [to_object(vane(1, .38, (.42, .71)), 'lead'), to_object(vane(-1, .62, (.36, .58, .79)), 'trail')]
for o in parts:
    m = o.modifiers.new('solid', 'SOLIDIFY'); m.thickness = .006; m.offset = 0
# Quill: a tapered tube along the centre line, brightest core of the glow.
curve = bpy.data.curves.new('quill', 'CURVE'); curve.dimensions = '3D'; curve.bevel_depth = 1; curve.bevel_resolution = 3
spline = curve.splines.new('POLY'); n = 24; spline.points.add(n)
for i in range(n + 1):
    s = .96 * i / n; p = rachis(s); spline.points[i].co = (*p, 1); spline.points[i].radius = .016 * L * (1 - .8 * s) + .002
quill = bpy.data.objects.new('quill', curve); bpy.context.collection.objects.link(quill)
bpy.ops.object.select_all(action='DESELECT'); quill.select_set(True); bpy.context.view_layer.objects.active = quill
bpy.ops.object.convert(target='MESH'); quill = bpy.context.object
qb, qe = quill.data.color_attributes.new('base', 'FLOAT_COLOR', 'POINT'), quill.data.color_attributes.new('emit', 'FLOAT_COLOR', 'POINT')
for v in quill.data.vertices:
    s = max(0, min(1, v.co.y / L))
    qb.data[v.index].color = (*WHITE.lerp(BLUE, .3 * s), 1)
    qe.data[v.index].color = (*(GLOW * (.25 + .5 * s)), 1)
for o in parts:
    bpy.ops.object.select_all(action='DESELECT'); o.select_set(True); bpy.context.view_layer.objects.active = o
    bpy.ops.object.modifier_apply(modifier='solid')
for o in parts + [quill]:
    orm = o.data.color_attributes.new('orm', 'FLOAT_COLOR', 'POINT')
    for v in o.data.vertices: orm.data[v.index].color = (1, .55, 0, 1)
bpy.ops.object.select_all(action='DESELECT')
for o in parts + [quill]: o.select_set(True)
bpy.context.view_layer.objects.active = quill
bpy.ops.object.join(); body = bpy.context.object; body.name = NAME
bpy.ops.object.shade_smooth()
# Blender is Z-up; the glTF exporter maps Blender +Z to glTF +Y. Author +Y-up here, so turn it into Blender Z-up.
body.rotation_euler = (math.pi / 2, 0, 0)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

# Bake colour layers to the three GemRender textures on one UV set.
mat = bpy.data.materials.new(NAME); mat.use_nodes = True; body.data.materials.clear(); body.data.materials.append(mat)
for layer in list(body.data.uv_layers): body.data.uv_layers.remove(layer)
uv = body.data.uv_layers.new(name='runtime'); body.data.uv_layers.active = uv
bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=.02, area_weight=.4)
bpy.ops.object.mode_set(mode='OBJECT')
scene = bpy.context.scene
scene.render.engine = 'CYCLES'; scene.cycles.samples = 1; scene.render.bake.margin = 6
images = {}
for suffix, layer in (('base', 'base'), ('emission', 'emit'), ('surface', 'orm')):
    img = bpy.data.images.new(f'{NAME}_{suffix}', 256, 256, alpha=True)
    if suffix == 'surface': img.colorspace_settings.name = 'Non-Color'
    img.filepath_raw = str(OUT / f'{NAME}_{suffix}.png'); img.file_format = 'PNG'
    nodes = mat.node_tree.nodes; nodes.clear()
    a = nodes.new('ShaderNodeVertexColor'); a.layer_name = layer
    e = nodes.new('ShaderNodeEmission'); o = nodes.new('ShaderNodeOutputMaterial')
    t = nodes.new('ShaderNodeTexImage'); t.image = img; nodes.active = t
    mat.node_tree.links.new(a.outputs['Color'], e.inputs['Color']); mat.node_tree.links.new(e.outputs[0], o.inputs['Surface'])
    bpy.ops.object.bake(type='EMIT'); img.save(); img.pack(); images[suffix] = img
nodes = mat.node_tree.nodes; nodes.clear(); links = mat.node_tree.links
sh = nodes.new('ShaderNodeBsdfPrincipled'); out = nodes.new('ShaderNodeOutputMaterial')
tb, te, ts = (nodes.new('ShaderNodeTexImage') for _ in range(3))
tb.image, te.image, ts.image = images['base'], images['emission'], images['surface']
sep = nodes.new('ShaderNodeSeparateColor')
links.new(ts.outputs['Color'], sep.inputs['Color']); links.new(sep.outputs['Green'], sh.inputs['Roughness'])
links.new(sep.outputs['Blue'], sh.inputs['Metallic']); links.new(tb.outputs['Color'], sh.inputs['Base Color'])
links.new(te.outputs['Color'], sh.inputs['Emission Color']); sh.inputs['Emission Strength'].default_value = 1
links.new(sh.outputs[0], out.inputs['Surface'])

bpy.ops.object.select_all(action='DESELECT'); body.select_set(True); bpy.context.view_layer.objects.active = body
glb = OUT / f'{NAME}.glb'
bpy.ops.export_scene.gltf(filepath=str(glb), export_format='GLB', use_selection=True, export_extras=True,
                          export_copyright='OriMod original asset.')
data = glb.read_bytes(); size = struct.unpack_from('<I', data, 12)[0]; gltf = json.loads(data[20:20 + size])
for prim in (p for m in gltf['meshes'] for p in m['primitives']):
    for key in [k for k in prim['attributes'] if k.startswith('COLOR_')]: del prim['attributes'][key]
textures = {}
for image in gltf['images']:
    resource = f'orimod:textures/entity/{image["name"]}.png'
    image.setdefault('extras', {})['resourceLocation'] = resource
    png = OUT / f'{image["name"]}.png'
    textures[resource] = {'path': png.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(png.read_bytes()).hexdigest()}
enc = json.dumps(gltf, separators=(',', ':')).encode(); enc += b' ' * (-len(enc) % 4); rest = data[20 + size:]
glb.write_bytes(struct.pack('<III', 0x46546C67, 2, 20 + len(enc) + len(rest)) + struct.pack('<II', len(enc), 0x4E4F534A) + enc + rest)
blend = OUT / f'{NAME}.blend'; bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)
lo = [min(v.co[i] for v in body.data.vertices) for i in range(3)]; hi = [max(v.co[i] for v in body.data.vertices) for i in range(3)]
manifest = {'asset': NAME, 'revision': revision, 'length_m': L, 'grip': 'origin, bottom of the quill', 'up': 'glTF +Y',
            'bounds_blender': [lo, hi], 'vertices': len(body.data.vertices), 'export': glb.relative_to(ROOT).as_posix(),
            'export_sha256': hashlib.sha256(glb.read_bytes()).hexdigest(), 'blend': blend.relative_to(ROOT).as_posix(),
            'textures': textures}
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'revision': revision, 'output': str(OUT), 'vertices': len(body.data.vertices)}))
