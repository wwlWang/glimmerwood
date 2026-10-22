"""Batch 4 concept blockouts: the five gloom creatures as smooth Ori-style shapes, rendered beside Ori for scale.

Run: blender -b --factory-startup --python scripts/art/monster_concepts.py -- <out_dir> [creature,...]
Writes <out_dir>/<creature>_<view>.png. Concept only; nothing here is promoted into the game.
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ORI = ROOT / 'src/main/resources/assets/orimod/models/entity/ori.glb'
args = sys.argv[sys.argv.index('--') + 1:]
OUT = Path(args[0]); OUT.mkdir(parents=True, exist_ok=True)
ONLY = set(args[1].split(',')) if len(args) > 1 else None


def material(name, base, emit=None, strength=0.0, rough=.55):
    m = bpy.data.materials.new(name); m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*base, 1); p.inputs['Roughness'].default_value = rough
    if emit:
        p.inputs['Emission Color'].default_value = (*emit, 1); p.inputs['Emission Strength'].default_value = strength
    return m


def blob(loc, scale, mat, rot=(0, 0, 0), seg=32):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, location=loc, rotation=rot)
    o = bpy.context.object; o.scale = scale; o.data.materials.append(mat)
    bpy.ops.object.shade_smooth(); return o


def tube(points, radii, mat, res=12):
    """A tapered organic limb: a smooth curve through points with a per-point radius."""
    c = bpy.data.curves.new('tube', 'CURVE'); c.dimensions = '3D'
    c.bevel_depth = 1; c.bevel_resolution = 4; c.use_fill_caps = True
    s = c.splines.new('BEZIER'); s.bezier_points.add(len(points) - 1)
    for bp, p, r in zip(s.bezier_points, points, radii):
        bp.co = p; bp.radius = r; bp.handle_left_type = bp.handle_right_type = 'AUTO'
    c.resolution_u = res
    o = bpy.data.objects.new('tube', c); bpy.context.collection.objects.link(o); o.data.materials.append(mat)
    return o


def petal(base, tip_dir, length, width, mat, curl=.25):
    """A thick leaf/feather: a flattened tapered tube that curls toward its tip."""
    d = Vector(tip_dir).normalized(); up = Vector((0, 0, 1))
    side = d.cross(up) if abs(d.dot(up)) < .95 else Vector((1, 0, 0))
    bend = d.cross(side).normalized()
    pts = [Vector(base) + d * length * t + bend * curl * length * t * t for t in (0, .35, .7, 1)]
    o = tube(pts, [width * .45, width, width * .7, .02], mat)
    return o


# --- shared palette: Ori decay — near-black violet hide, hot violet-pink gloom light, pale cores ---
HIDE = material('hide', (.035, .02, .055), rough=.45)
HIDE2 = material('hide_sheen', (.09, .04, .12), rough=.3)
GLOW = material('gloom_glow', (.5, .15, .7), (.85, .3, 1.0), 6)
CORE = material('core', (.9, .8, 1.0), (1.0, .85, 1.0), 12)
EYE = material('eye', (1, .4, .8), (1.0, .35, .75), 14)
WARN = material('warn', (1, .3, .2), (1.0, .35, .2), 10)


def spitter():
    """Rooted gloom bulb: roots grip the ground, a swollen stalk, a five-petal bud open on a pale core."""
    for i in range(7):
        a = i * math.tau / 7 + .3
        c, s = math.cos(a), math.sin(a)
        tube([Vector((c * .12, s * .12, .2)), Vector((c * .35, s * .35, .06)), Vector((c * .6, s * .6, .0)),
              Vector((c * .8, s * .8, -.02))], [.11, .07, .04, .012], HIDE)
    tube([Vector((0, 0, 0)), Vector((.05, 0, .3)), Vector((-.03, 0, .55)), Vector((0, 0, .78))], [.2, .16, .12, .14], HIDE2)
    for i in range(3):  # glowing veins up the stalk
        a = i * math.tau / 3
        tube([Vector((math.cos(a) * .19, math.sin(a) * .19, .05)), Vector((math.cos(a) * .15, math.sin(a) * .15, .35)),
              Vector((math.cos(a) * .12, math.sin(a) * .12, .75))], [.022, .018, .014], GLOW)
    z = .92
    blob((0, 0, z), (.17, .17, .16), CORE)
    for i in range(5):  # cupped petals tilted open
        a = i * math.tau / 5
        tilt = .75
        d = Vector((math.sin(tilt) * math.cos(a), math.sin(tilt) * math.sin(a), math.cos(tilt)))
        base = Vector((math.cos(a) * .1, math.sin(a) * .1, z - .12))
        o = blob(base + d * .28, (.045, .16, .3), HIDE2, rot=(0, tilt, a))
        tube([base + d * .06, base + d * .3, base + d * .55], [.012, .02, .006], GLOW)
        blob(base + d * .58, (.035, .035, .035), GLOW)
    for i in range(2):  # two drooping leaves
        a = i * math.pi + .8
        petal((math.cos(a) * .12, math.sin(a) * .12, .45), (math.cos(a), math.sin(a), .25), .55, .09, HIDE2, curl=-.4)


def leaf(base, direction, length, width, thick, mat):
    """A flat smooth feather / plate: an ellipsoid whose long axis runs along direction, flat side up."""
    d = Vector(direction).normalized()
    o = blob(Vector(base) + d * length / 2, (width / 2, length / 2, thick / 2), mat, seg=24)
    o.rotation_mode = 'QUATERNION'; o.rotation_quaternion = d.to_track_quat('Y', 'Z')
    return o


def bird(S, crest):
    """Shared hawk silhouette (beak toward -Y): broad fanned wings, forked tail, glowing breast and eyes."""
    z = .9 * S
    blob((0, 0, z), (.17 * S, .42 * S, .17 * S), HIDE)
    blob((0, -.4 * S, z + .08 * S), (.13 * S, .15 * S, .13 * S), HIDE)
    tube([Vector((0, -.52 * S, z + .1 * S)), Vector((0, -.66 * S, z + .06 * S)), Vector((0, -.72 * S, z - .03 * S))],
         [.045 * S, .025 * S, .004], HIDE2)
    blob((0, -.22 * S, z - .06 * S), (.1 * S, .16 * S, .1 * S), GLOW)
    for s in (-1, 1):
        blob((s * .075 * S, -.5 * S, z + .14 * S), (.03 * S, .03 * S, .025 * S), EYE)
        shoulder = Vector((s * .12 * S, -.1 * S, z + .06 * S)); wrist = Vector((s * .62 * S, -.02 * S, z + .16 * S))
        tip = Vector((s * 1.0 * S, .08 * S, z + .14 * S))
        tube([shoulder, wrist, tip], [.075 * S, .055 * S, .035 * S], HIDE)
        for k in range(4):  # coverts along the arm, overlapping
            t = k / 3
            leaf(shoulder.lerp(wrist, t), (s * .15, 1, -.05), (.36 + .05 * k) * S, .2 * S, .04 * S, HIDE2)
        for k in range(5):  # primaries fanning from the hand
            a = math.radians(-8 + 22 * k)
            leaf(wrist.lerp(tip, k / 4), (s * math.cos(a), math.sin(a) + .35, -.06), (.62 - .05 * k) * S, .14 * S, .03 * S, HIDE2)
        leaf(wrist.lerp(tip, .5) + Vector((0, .05 * S, -.02 * S)), (s * .3, 1, -.1), .35 * S, .1 * S, .02 * S, GLOW)
        tube([Vector((s * .08 * S, .08 * S, z - .12 * S)), Vector((s * .1 * S, .1 * S, z - .32 * S)),
              Vector((s * .09 * S, .02 * S, z - .4 * S))], [.03 * S, .02 * S, .008], HIDE2)
    for k in range(5):  # forked tail
        a = (k - 2) * .22
        leaf((0, .32 * S, z + .02 * S), (math.sin(a) * 1.2, 1, -.05), (.55 + .12 * abs(k - 2)) * S, .13 * S, .03 * S, HIDE2)
    if crest:
        for k in range(5):
            a = (k - 2) * .3
            leaf((0, -.45 * S, z + .2 * S), (math.sin(a) * .5, .8, .7), .32 * S, .07 * S, .02 * S, GLOW if k == 2 else HIDE2)


def hawk():
    """Lean gloom hawk: wingspan ~2 blocks."""
    bird(1.0, False)


def beetle():
    """Armoured gloom beetle: domed shell in plates with glowing seams, six short braced legs, mandibles."""
    blob((0, .1, .42), (.5, .58, .3), HIDE2)
    blob((0, -.38, .4), (.38, .2, .2), HIDE2)  # pronotum
    for y in (-.05, .32):
        tube([Vector((-.47, y, .45)), Vector((0, y, .73)), Vector((.47, y, .45))], [.02, .035, .02], GLOW)
    tube([Vector((0, -.2, .7)), Vector((0, .2, .74)), Vector((0, .66, .48))], [.02, .03, .015], GLOW)
    blob((0, -.62, .32), (.22, .17, .15), HIDE)
    for s in (-1, 1):
        blob((s * .12, -.74, .37), (.04, .04, .04), EYE)
        tube([Vector((s * .1, -.72, .27)), Vector((s * .22, -.92, .25)), Vector((s * .07, -1.02, .22))], [.045, .03, .008], HIDE2)
        for k, y in enumerate((-.32, .02, .36)):
            spread = (-.18, 0, .2)[k]
            hip = Vector((s * .36, y, .28)); knee = Vector((s * .62, y + spread, .38)); foot = Vector((s * .72, y + spread * 1.4, 0))
            tube([hip, knee], [.075, .06], HIDE)
            tube([knee, knee.lerp(foot, .5), foot], [.06, .045, .02], HIDE)


def heart():
    """Gloom Heart boss: a cracked dark heart leaking light, held in a cage of thorned ribs, roots trailing below."""
    z = 1.6
    blob((0, 0, z), (.5, .5, .56), CORE)
    for i in range(6):  # dark shell lobes with light leaking between them
        a = i * math.tau / 6
        d = Vector((math.cos(a), math.sin(a), (.35 if i % 2 else -.3)))
        blob(Vector((0, 0, z)) + d.normalized() * .28, (.34, .34, .38), HIDE, rot=(0, 0, a))
    import random
    rng = random.Random(7)
    for i in range(5):
        a = i * math.tau / 5 + rng.uniform(-.2, .2)
        c, s = math.cos(a), math.sin(a)
        w = rng.uniform(.9, 1.2)
        top = Vector((c * .15, s * .15, z + 1.0)); twist = .35
        pts = [top]
        for k, (r, h) in enumerate(((.8, .7), (1.08, .1), (.9, -.6), (.4, -1.0))):
            b = a + twist * (k + 1) / 4
            pts.append(Vector((math.cos(b) * r * w, math.sin(b) * r * w, z + h)))
        tube(pts, [.06, .14, .16, .11, .05], HIDE2)
        for k in (1, 2):
            p = pts[k]; o = Vector((p.x, p.y, 0)).normalized()
            tube([p, p + o * .32 + Vector((0, 0, .12))], [.07, .005], HIDE)
        tube([pts[4], pts[4] + Vector((c * .2, s * .2, -.45)), pts[4] + Vector((c * .05, s * .05, -.75))], [.05, .035, .008], HIDE)
    tube([Vector((0, 0, z + .95)), Vector((0, 0, z + 1.3)), Vector((.12, 0, z + 1.55))], [.12, .06, .01], HIDE)


def matriarch():
    """Gloom Matriarch boss: the hawk silhouette grown vast (~5 blocks across) with a glowing crown crest."""
    bird(2.4, True)


CREATURES = {'spitter': (spitter, 1.6), 'hawk': (hawk, 2.0), 'beetle': (beetle, 1.8), 'heart': (heart, 3.6), 'matriarch': (matriarch, 6.0)}


def stage(extent):
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'; scene.cycles.samples = 32; scene.cycles.use_denoising = True
    scene.cycles.device = 'CPU'
    scene.render.resolution_x, scene.render.resolution_y = 900, 900
    scene.view_settings.view_transform = 'AgX'
    w = scene.world or bpy.data.worlds.new('w'); scene.world = w; w.use_nodes = True
    w.node_tree.nodes['Background'].inputs['Color'].default_value = (.035, .05, .07, 1)
    w.node_tree.nodes['Background'].inputs['Strength'].default_value = 1.0
    bpy.ops.mesh.primitive_plane_add(size=60, location=(0, 0, 0))
    bpy.context.object.data.materials.append(material('ground', (.06, .08, .06), rough=.9))
    for loc, energy, size in (((-4, -5, 6), 900, 4), ((5, 3, 4), 350, 3), ((0, 6, 2), 250, 3)):
        bpy.ops.object.light_add(type='AREA', location=loc); L = bpy.context.object
        L.data.energy = energy * extent / 2; L.data.size = size
        L.rotation_euler = (Vector((0, 0, extent * .3)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()


def ori_for_scale(x):
    bpy.ops.import_scene.gltf(filepath=str(ORI))
    for o in bpy.context.selected_objects:
        if o.parent is None: o.location.x += x; o.rotation_euler.z = math.pi / 2 * -0  # faces -Y like the creatures


def render(name, extent):
    cam_data = bpy.data.cameras.new('cam'); cam = bpy.data.objects.new('cam', cam_data)
    bpy.context.collection.objects.link(cam); bpy.context.scene.camera = cam
    cam_data.lens = 50
    target = Vector((-extent * .2, 0, extent * .35))
    for view, direction in (('front', (0, -1, .25)), ('side', (1, 0, .2)), ('three_quarter', (.8, -.9, .45))):
        d = Vector(direction).normalized()
        cam.location = target + d * extent * 2.6
        cam.rotation_euler = (target - cam.location).to_track_quat('-Z', 'Y').to_euler()
        bpy.context.scene.render.filepath = str(OUT / f'{name}_{view}.png')
        bpy.ops.render.render(write_still=True)


for name, (build, extent) in CREATURES.items():
    if ONLY and name not in ONLY: continue
    bpy.ops.wm.read_factory_settings(use_empty=True)
    globals().update(HIDE=material('hide', (.035, .02, .055), rough=.45), HIDE2=material('hide_sheen', (.09, .04, .12), rough=.3),
                     GLOW=material('gloom_glow', (.5, .15, .7), (.85, .3, 1.0), 14), CORE=material('core', (.9, .8, 1.0), (1.0, .85, 1.0), 12),
                     EYE=material('eye', (1, .4, .8), (1.0, .35, .75), 14))
    stage(extent)
    build()
    ori_for_scale(-extent * .75)
    render(name, extent)
    print('RENDERED', name)
