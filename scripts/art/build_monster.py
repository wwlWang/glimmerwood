"""Build one rigged, animated gloom creature as a versioned GLB (batch 4, smooth Ori-style models).

Run: blender -b --factory-startup --python scripts/art/build_monster.py -- <creature>
Writes art/source/monsters/<creature>/vNNN/ (blend, glb, textures, preview frames, manifest.json).
Promotion into the game is separate: python scripts/art/promote_monster.py art/source/monsters/<creature>/vNNN/manifest.json

Every creature faces Blender -Y (glTF +Z), stands on z=0, one Blender unit is one block.
Clip contract shared by all creatures (the runtime picks by name): idle and move loop; telegraph holds its last
frame; attack, hurt and death play once. Clip lengths are seconds at FPS.
"""
import bpy
import hashlib
import json
import math
import random
import struct
import sys
from pathlib import Path
from mathutils import Vector, Matrix

ROOT = Path(__file__).resolve().parents[2]
FPS = 24
NAME = sys.argv[sys.argv.index('--') + 1]
rng = random.Random(1)


# ---------- geometry: every part is tagged with the bone it rides and its colours ----------
PARTS = []


def tag(o, bone, base, emit=(0, 0, 0), rough=.5, chain=None):
    if emit == GLOW and base == GLOW: base = GLOW_BASE
    o['bone'] = bone; o['base'] = base; o['emit'] = emit; o['rough'] = rough
    if chain: o['chain'] = json.dumps(chain)  # [(bone, z)] — weights blend by height along the chain
    PARTS.append(o); return o


def blob(loc, scale, rot=(0, 0, 0), seg=20):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, location=loc, rotation=rot)
    o = bpy.context.object; o.scale = scale; bpy.ops.object.shade_smooth(); return o


def tube(points, radii, res=6, bevel=3):
    c = bpy.data.curves.new('tube', 'CURVE'); c.dimensions = '3D'
    c.bevel_depth = 1; c.bevel_resolution = bevel; c.use_fill_caps = True; c.resolution_u = res
    s = c.splines.new('BEZIER'); s.bezier_points.add(len(points) - 1)
    for bp, p, r in zip(s.bezier_points, points, radii):
        bp.co = p; bp.radius = r; bp.handle_left_type = bp.handle_right_type = 'AUTO'
    o = bpy.data.objects.new('tube', c); bpy.context.collection.objects.link(o)
    return o


def leaf(base, direction, length, width, thick, up=(0, 0, 1)):
    d = Vector(direction).normalized()
    o = blob(Vector(base) + d * length / 2, (width / 2, length / 2, thick / 2), seg=16)
    o.rotation_mode = 'QUATERNION'; o.rotation_quaternion = d.to_track_quat('Y', 'Z')
    return o


# ---------- palette (linear) ----------
# In game the emission is added on top of the lit base: glowing parts keep a dark violet base and a saturated violet
# emission so they read purple, not blown-out white (v002 in-game check); the hide is a lifted purple so the form
# still reads at night.
HIDE = (.075, .035, .11)
SHEEN = (.17, .075, .24)
GLOW = (.6, .14, 1.0)
GLOW_BASE = (.22, .05, .36)
CORE = (1.0, .85, 1.0)
EYE = (1.0, .3, .7)


def warn(t):
    """Telegraph ramp: most of the pose in the first 60% (players must read it early), then held."""
    x = min(1.0, t / .6)
    return 1 - (1 - x) ** 3


# ---------- creatures ----------
def spitter():
    """Rooted gloom bulb that walks on its roots: a five-petal bud opens on a pale core and spits gloom orbs."""
    bones = [('root', (0, 0, 0), (0, 0, .2), None),
             ('stalk_1', (0, 0, .15), (0, 0, .4), 'root'), ('stalk_2', (0, 0, .4), (0, 0, .65), 'stalk_1'),
             ('stalk_3', (0, 0, .65), (0, 0, .82), 'stalk_2'), ('bud', (0, 0, .82), (0, 0, 1.0), 'stalk_3')]
    chain = [('stalk_1', .15), ('stalk_2', .45), ('stalk_3', .7), ('bud', .85)]
    for i in range(7):
        a = i * math.tau / 7 + .3; c, s = math.cos(a), math.sin(a)
        bones.append((f'root_{i}', (c * .12, s * .12, .2), (c * .7, s * .7, 0), 'root'))
        tag(tube([Vector((c * .1, s * .1, .22)), Vector((c * .35, s * .35, .08)), Vector((c * .58, s * .58, .01)),
                  Vector((c * .78, s * .78, -.01))], [.1, .065, .04, .012]), f'root_{i}', HIDE, rough=.6)
    tag(tube([Vector((0, 0, .05)), Vector((.04, 0, .3)), Vector((-.02, 0, .55)), Vector((0, 0, .8))], [.19, .15, .115, .13], res=8),
        'stalk_1', SHEEN, rough=.35, chain=chain)
    for i in range(3):
        a = i * math.tau / 3
        tag(tube([Vector((math.cos(a) * .18, math.sin(a) * .18, .08)), Vector((math.cos(a) * .145, math.sin(a) * .145, .38)),
                  Vector((math.cos(a) * .115, math.sin(a) * .115, .62)), Vector((math.cos(a) * .125, math.sin(a) * .125, .8))],
                 [.02, .017, .014, .012]), 'stalk_1', GLOW, GLOW, .3, chain=chain)
    z = .92
    tag(blob((0, 0, z), (.16, .16, .15)), 'bud', CORE, CORE, .2)
    for i in range(5):
        a = i * math.tau / 5; tilt = .75
        d = Vector((math.sin(tilt) * math.cos(a), math.sin(tilt) * math.sin(a), math.cos(tilt)))
        base = Vector((math.cos(a) * .09, math.sin(a) * .09, z - .12))
        bones.append((f'petal_{i}', tuple(base), tuple(base + d * .55), 'bud'))
        tag(blob(base + d * .28, (.045, .16, .3), rot=(0, tilt, a)), f'petal_{i}', SHEEN, rough=.3)
        tag(tube([base + d * .06, base + d * .3, base + d * .52], [.011, .018, .006]), f'petal_{i}', GLOW, GLOW, .3)
        tag(blob(base + d * .57, (.032, .032, .032), seg=10), f'petal_{i}', GLOW, GLOW, .3)
    for i, side in enumerate(('l', 'r')):
        a = i * math.pi + .8; d = Vector((math.cos(a), math.sin(a), .25))
        b = Vector((math.cos(a) * .12, math.sin(a) * .12, .45))
        bones.append((f'leaf_{side}', tuple(b), tuple(b + d.normalized() * .5), 'stalk_2'))
        tag(leaf(b, d, .5, .17, .035), f'leaf_{side}', SHEEN, rough=.4)
    return bones


def spitter_pose(P, clip, t):
    """P(bone, rx, ry, rz, scale=1, loc=None) poses in bone space. t is 0..1 through the clip."""
    tau = math.tau
    def petals(openness, wobble=0):
        for i in range(5):
            P(f'petal_{i}', 0, 0, -openness + wobble * math.sin(tau * t * 2 + i))
    def sway(amount, speed=1):
        P('stalk_1', amount * math.sin(tau * t * speed) * .5, 0, amount * math.cos(tau * t * speed) * .35)
        P('stalk_2', amount * math.sin(tau * t * speed - .7) * .6, 0, amount * math.cos(tau * t * speed - .7) * .4)
        P('stalk_3', amount * math.sin(tau * t * speed - 1.4) * .7, 0, 0)
    if clip == 'idle':
        sway(.06); petals(.08 * math.sin(tau * t), .03)
        P('bud', 0, 0, 0, 1 + .03 * math.sin(tau * t * 2))
        for s, ph in (('l', 0), ('r', 1.5)): P(f'leaf_{s}', .12 * math.sin(tau * t + ph), 0, 0)
    elif clip == 'move':
        # Roots step in two alternating sets; the body bobs on each step.
        P('root', 0, 0, 0, 1, (0, 0, .03 * abs(math.sin(tau * t * 2))))
        for i in range(7):
            phase = tau * t + (math.pi if i % 2 else 0)
            P(f'root_{i}', -.35 * max(0, math.sin(phase)), 0, .12 * math.cos(phase))
        sway(.1, 2); petals(.05)
        for s in 'lr': P(f'leaf_{s}', .2 * math.sin(tau * t * 2), 0, 0)
    elif clip == 'telegraph':
        # Petals clamp shut while the bud swells and the stalk draws back: the orb is coming.
        k = warn(t)
        petals(-.5 * k, .06 * k)
        P('bud', 0, 0, 0, 1 + .28 * k)
        P('stalk_2', -.22 * k, 0, 0); P('stalk_3', -.18 * k, 0, 0)
        for s in 'lr': P(f'leaf_{s}', .3 * k, 0, 0)
    elif clip == 'attack':
        # Burst: petals fling wide, the bud snaps forward and recoils, then settles.
        k = math.sin(math.pi * min(1, t * 3)) if t < .33 else max(0, 1 - (t - .33) / .67)
        petals(.55 * k)
        P('bud', .45 * k, 0, 0, 1 + .1 * k)
        P('stalk_2', .25 * k, 0, 0); P('stalk_3', .2 * k, 0, 0)
    elif clip == 'hurt':
        k = math.sin(math.pi * t)
        petals(-.35 * k); P('stalk_1', -.25 * k, 0, .1 * k); P('stalk_2', -.2 * k, 0, 0); P('bud', 0, 0, 0, 1 - .12 * k)
    elif clip == 'death':
        # Wilt: petals droop open and down, the stalk folds forward, everything shrinks into the roots.
        k = t * t * (3 - 2 * t)
        petals(1.1 * k)
        P('stalk_1', .35 * k, 0, .15 * k); P('stalk_2', .55 * k, 0, 0); P('stalk_3', .6 * k, 0, 0)
        P('bud', 0, 0, 0, 1 - .45 * k)
        P('root', 0, 0, 0, 1 - .12 * k, (0, 0, -.06 * k))
        for s in 'lr': P(f'leaf_{s}', -.6 * k, 0, 0)


def beetle():
    """Armoured gloom beetle: two wing-case shells over a dark body (they flare open on the wind-up and show the glowing
    seam), a heavy head plate (its armoured front), mandibles, short glowing antennae and six braced legs."""
    bones = [('root', (0, 0, 0), (0, 0, .2), None), ('body', (0, .35, .38), (0, -.3, .38), 'root'),
             ('head', (0, -.45, .36), (0, -.78, .36), 'body')]
    tag(blob((0, .1, .36), (.4, .56, .22)), 'body', HIDE, rough=.6)
    tag(blob((0, -.36, .42), (.36, .2, .2)), 'body', SHEEN, rough=.25)  # pronotum
    tag(tube([Vector((-.3, -.3, .5)), Vector((0, -.27, .6)), Vector((.3, -.3, .5))], [.015, .025, .015]), 'body', GLOW, GLOW, .3)
    for i in range(4):  # glowing belly vents between the legs
        tag(blob(((-1) ** i * .12, -.1 + i * .14, .17), (.05, .05, .03), seg=10), 'body', GLOW, GLOW, .3)
    tag(blob((0, -.6, .34), (.21, .17, .15)), 'head', HIDE, rough=.4)
    tag(blob((0, -.58, .44), (.2, .14, .06)), 'head', SHEEN, rough=.2)  # brow plate
    for s in (-1, 1):
        side = 'l' if s < 0 else 'r'
        tag(blob((s * .11, -.73, .38), (.04, .035, .04), seg=10), 'head', EYE, EYE, .2)
        bones.append((f'mandible_{side}', (s * .1, -.7, .27), (s * .07, -1.0, .23), 'head'))
        tag(tube([Vector((s * .1, -.7, .27)), Vector((s * .21, -.9, .25)), Vector((s * .07, -1.01, .22))], [.045, .03, .006]),
            f'mandible_{side}', SHEEN, rough=.3)
        tag(tube([Vector((s * .08, -.7, .44)), Vector((s * .22, -.88, .62)), Vector((s * .3, -.9, .7))], [.012, .009, .006]), 'head', HIDE)
        tag(blob((s * .3, -.9, .7), (.025, .025, .025), seg=8), 'head', GLOW, GLOW, .3)
        hinge = Vector((s * .05, -.28, .5)); end = Vector((s * .3, .66, .42))
        bones.append((f'shell_{side}', tuple(hinge), tuple(end), 'body'))
        tag(blob((s * .21, .17, .47), (.25, .52, .2), rot=(0, s * .25, 0)), f'shell_{side}', SHEEN, rough=.22)
        tag(tube([Vector((s * .03, -.25, .62)), Vector((s * .05, .2, .66)), Vector((s * .08, .6, .52))], [.015, .022, .01]),
            f'shell_{side}', GLOW, GLOW, .3)
        tag(tube([Vector((s * .36, -.1, .5)), Vector((s * .4, .25, .5)), Vector((s * .3, .55, .45))], [.01, .016, .008]),
            f'shell_{side}', GLOW, GLOW, .3)
        for k, y in enumerate((-.3, .02, .34)):
            spread = (-.2, 0, .22)[k]
            hip = Vector((s * .3, y, .28)); knee = Vector((s * .6, y + spread, .4)); foot = Vector((s * .72, y + spread * 1.45, 0))
            bones.append((f'leg_{side}{k}_a', tuple(hip), tuple(knee), 'body'))
            bones.append((f'leg_{side}{k}_b', tuple(knee), tuple(foot), f'leg_{side}{k}_a'))
            tag(tube([hip, hip.lerp(knee, .5), knee], [.065, .06, .05]), f'leg_{side}{k}_a', HIDE, rough=.4)
            tag(tube([knee, knee.lerp(foot, .5), foot], [.05, .04, .015]), f'leg_{side}{k}_b', SHEEN, rough=.3)
    return bones


def beetle_pose(P, clip, t):
    tau = math.tau
    def legs(phase_fn, swing, lift):
        for side, s in (('l', -1), ('r', 1)):
            for k in range(3):
                ph = phase_fn(side, k)
                P(f'leg_{side}{k}_a', s * swing * math.cos(ph), 0, lift * max(0, math.sin(ph)))
                P(f'leg_{side}{k}_b', 0, 0, -lift * .6 * max(0, math.sin(ph)))
    tripod = lambda side, k: tau * t * (2 if clip == 'charge' else 1) + (math.pi if (side == 'l') == (k % 2 == 0) else 0)
    def shells(lift, spread):
        P('shell_l', -spread, 0, lift); P('shell_r', spread, 0, lift)
    def jaws(open_):
        P('mandible_l', 0, 0, 0); P('mandible_r', 0, 0, 0)
        P('mandible_l', -open_, 0, 0); P('mandible_r', open_, 0, 0)
    if clip == 'idle':
        P('body', 0, 0, .03 * math.sin(tau * t)); jaws(.15 * max(0, math.sin(tau * t * 3)))
        P('head', .1 * math.sin(tau * t * .5), 0, .05 * math.sin(tau * t))
        shells(.04 * (1 + math.sin(tau * t)), 0)
    elif clip in ('move', 'charge'):
        fast = clip == 'charge'
        legs(tripod, .45 if fast else .35, .5 if fast else .35)
        P('root', 0, 0, 0, 1, (0, 0, .025 * abs(math.sin(tau * t * (4 if fast else 2)))))
        P('body', .04 * math.sin(tau * t * 2), 0, -.12 if fast else 0)
        P('head', 0, 0, -.25 if fast else .05 * math.sin(tau * t * 2))
        jaws(.45 if fast else .1); shells(.18 if fast else 0, .12 if fast else 0)
    elif clip == 'telegraph':
        k = warn(t)
        shells(.55 * k, .35 * k)  # flare: the glowing seam is the warning
        P('body', 0, 0, -.2 * k); P('head', 0, 0, -.25 * k); jaws(.6 * k + .08 * math.sin(tau * t * 6) * k)
        legs(lambda side, kk: -math.pi / 2, .0, 0)
        for side, s in (('l', -1), ('r', 1)):  # dig in: rear legs push, front legs brace
            P(f'leg_{side}2_a', -s * .3 * k, 0, -.15 * k); P(f'leg_{side}0_a', s * .25 * k, 0, -.1 * k)
    elif clip == 'attack':
        k = math.sin(math.pi * t)
        jaws(.7 * (1 - k)); P('head', 0, 0, -.35 * k); P('body', 0, 0, -.1 * k)
    elif clip == 'stunned':
        P('head', .3 * math.sin(tau * t), 0, -.2 + .1 * math.sin(tau * t * 2)); P('body', .06 * math.sin(tau * t), 0, 0)
        shells(.2, .1); jaws(.35)
        legs(lambda side, kk: tau * t * .5 + kk, .12, .1)
    elif clip == 'flipped':
        # Upside down (the runtime rolls the body): legs flail in the air, shells pressed shut.
        for side, s in (('l', -1), ('r', 1)):
            for kk in range(3):
                ph = tau * t * 3 + kk * 1.7 + (0 if side == 'l' else 1)
                P(f'leg_{side}{kk}_a', s * .4 * math.sin(ph), 0, -.75 - .35 * math.sin(ph * 1.3))  # model-down is sky-up once rolled
                P(f'leg_{side}{kk}_b', 0, 0, .5 + .4 * math.cos(ph))
        jaws(.4 + .3 * math.sin(tau * t * 4)); P('head', .2 * math.sin(tau * t * 2), 0, .2)
    elif clip == 'hurt':
        k = math.sin(math.pi * t)
        P('body', 0, 0, .15 * k); P('head', 0, 0, .25 * k); shells(.2 * k, .05 * k); jaws(.4 * k)
    elif clip == 'death':
        # The classic dead beetle: legs curl in, shells settle shut, the body sinks.
        k = t * t * (3 - 2 * t)
        for side, s in (('l', -1), ('r', 1)):
            for kk in range(3):
                P(f'leg_{side}{kk}_a', 0, 0, .9 * k); P(f'leg_{side}{kk}_b', 0, 0, -1.2 * k)
        P('root', 0, 0, 0, 1, (0, 0, -.12 * k)); P('head', 0, 0, -.3 * k); jaws(.5 * k)


def bird(S, crest):
    """Shared gloom bird (beak toward -Y): arm and hand wing bones carrying overlapping feathers, a fanned forked tail,
    hinged beak, talons. S scales the hawk (1.0) up to the Matriarch."""
    z = .32 * S
    V = lambda x, y, zz: Vector((x * S, y * S, zz * S + z))
    bones = [('root', (0, 0, 0), (0, 0, .2 * S), None), ('body', tuple(V(0, .3, 0)), tuple(V(0, -.3, 0)), 'root'),
             ('head', tuple(V(0, -.33, .06)), tuple(V(0, -.62, .1)), 'body'), ('jaw', tuple(V(0, -.5, .02)), tuple(V(0, -.7, -.02)), 'head'),
             ('tail', tuple(V(0, .3, 0)), tuple(V(0, .9, -.02)), 'body')]
    tag(blob(V(0, 0, 0), (.17 * S, .42 * S, .17 * S)), 'body', HIDE, rough=.5)
    tag(blob(V(0, -.22, -.07), (.1 * S, .17 * S, .1 * S)), 'body', GLOW, GLOW, .3)  # glowing breast
    tag(blob(V(0, -.4, .08), (.13 * S, .15 * S, .13 * S)), 'head', HIDE, rough=.45)
    tag(tube([V(0, -.52, .1), V(0, -.66, .07), V(0, -.73, -.01)], [.045 * S, .025 * S, .004]), 'head', SHEEN, rough=.2)
    tag(tube([V(0, -.5, .03), V(0, -.62, .01), V(0, -.67, -.02)], [.03 * S, .018 * S, .004]), 'jaw', SHEEN, rough=.2)
    for side, s in (('l', -1), ('r', 1)):
        tag(blob(V(s * .075, -.5, .14), (.03 * S, .03 * S, .025 * S), seg=10), 'head', EYE, EYE, .2)
        shoulder = V(s * .12, -.1, .06); wrist = V(s * .62, -.02, .16); tip = V(s * 1.0, .08, .14)
        bones.append((f'wing_{side}_1', tuple(shoulder), tuple(wrist), 'body'))
        bones.append((f'wing_{side}_2', tuple(wrist), tuple(tip), f'wing_{side}_1'))
        tag(tube([shoulder, shoulder.lerp(wrist, .5), wrist], [.075 * S, .065 * S, .055 * S]), f'wing_{side}_1', HIDE)
        tag(tube([wrist, wrist.lerp(tip, .5), tip], [.055 * S, .045 * S, .03 * S]), f'wing_{side}_2', HIDE)
        for k in range(4):
            tag(leaf(shoulder.lerp(wrist, k / 3), (s * .15, 1, -.05), (.36 + .05 * k) * S, .2 * S, .04 * S), f'wing_{side}_1', SHEEN, rough=.3)
        tag(leaf(shoulder.lerp(wrist, .6) + Vector((0, .05 * S, -.02 * S)), (s * .2, 1, -.1), .32 * S, .09 * S, .02 * S),
            f'wing_{side}_1', GLOW, GLOW, .3)
        for k in range(5):
            a = math.radians(-8 + 22 * k)
            tag(leaf(wrist.lerp(tip, k / 4), (s * math.cos(a), math.sin(a) + .35, -.06), (.62 - .05 * k) * S, .14 * S, .03 * S),
                f'wing_{side}_2', SHEEN, rough=.3)
        hip = V(s * .08, .08, -.12); foot = V(s * .09, .02, -.4)
        bones.append((f'leg_{side}', tuple(hip), tuple(foot), 'body'))
        tag(tube([hip, V(s * .1, .1, -.32), foot], [.03 * S, .02 * S, .008 * S]), f'leg_{side}', SHEEN)
        for k in range(3):
            a = (k - 1) * .5
            tag(tube([foot, foot + Vector((math.sin(a) * .07 * S, -math.cos(a) * .07 * S, -.03 * S))], [.012 * S, .003]), f'leg_{side}', SHEEN)
    for k in range(5):
        a = (k - 2) * .22
        tag(leaf(V(0, .32, .02), (math.sin(a) * 1.2, 1, -.05), (.55 + .12 * abs(k - 2)) * S, .13 * S, .03 * S), 'tail',
            GLOW if crest and k in (1, 3) else SHEEN, GLOW if crest and k in (1, 3) else (0, 0, 0), .3)
    if crest:
        for k in range(5):
            a = (k - 2) * .3
            tag(leaf(V(0, -.45, .2), (math.sin(a) * .5, .8, .7), .32 * S, .07 * S, .02 * S), 'head',
                GLOW if k == 2 else SHEEN, GLOW if k == 2 else (0, 0, 0), .3)
        for k in range(3):  # long trailing plumes mark the Matriarch from any distance
            a = (k - 1) * .35
            tag(leaf(V(0, .45, .0), (math.sin(a), 1, -.12), 1.1 * S, .07 * S, .02 * S), 'tail', GLOW, GLOW, .3)
    return bones


def bird_pose(P, clip, t):
    """Wing bones: +rz lifts the tip; rx swings forward (mirrored per side). Body/head: +rz pitches the nose up."""
    tau = math.tau
    def wings(lift1, lift2, sweep1=0, sweep2=0):
        for side, s in (('l', -1), ('r', 1)):
            P(f'wing_{side}_1', s * sweep1, 0, lift1); P(f'wing_{side}_2', s * sweep2, 0, lift2)
    def flap(ph, amp=1.0):
        down = math.sin(ph)
        wings(.75 * amp * down, .35 * amp * math.sin(ph - .9), .1 * math.cos(ph), .15 * amp * math.cos(ph - .9))
    smooth = lambda x: max(0, min(1, x)) ** 2 * (3 - 2 * max(0, min(1, x)))
    if clip == 'idle':  # soaring: wings level, gentle dihedral sway, the odd slow beat
        wings(.12 + .05 * math.sin(tau * t), .05 * math.sin(tau * t - 1))
        P('body', .06 * math.sin(tau * t), 0, 0); P('tail', 0, 0, .05 * math.sin(tau * t * 2)); P('head', .1 * math.sin(tau * t), 0, 0)
    elif clip == 'move':  # powered flight
        flap(tau * t); P('root', 0, 0, 0, 1, (0, 0, -.04 * math.sin(tau * t)))
        P('tail', 0, 0, .1 * math.sin(tau * t + 1)); P('body', 0, 0, .05)
    elif clip in ('telegraph', 'enrage'):  # rear back, wings high, beak open: the shriek
        k = smooth(t * 2) if clip == 'enrage' else warn(t)
        shake = .06 * math.sin(tau * t * 8) * k if clip == 'enrage' else 0
        wings(1.0 * k, .5 * k, -.3 * k, -.2 * k)
        P('body', 0, 0, .45 * k); P('head', shake, 0, .4 * k); P('jaw', 0, 0, -.6 * k); P('tail', 0, 0, -.3 * k, 1 + .25 * k)
    elif clip == 'dive':  # wings swept back and folded, head down the line
        wings(.15, -.2, -.9, -.6); P('body', 0, 0, -.25); P('head', 0, 0, -.15); P('tail', 0, 0, .1, .8)
        for side in 'lr': P(f'leg_{side}', 0, 0, -.6)
    elif clip == 'attack':  # talons rake forward
        k = math.sin(math.pi * t)
        flap(tau * t * .5 + 1, .6)
        for side in 'lr': P(f'leg_{side}', 0, 0, 1.2 * k)
        P('body', 0, 0, .3 * k); P('jaw', 0, 0, -.4 * k)
    elif clip == 'stunned':  # grounded, wings splayed on the floor, head lolling
        wings(-.35, -.15, -.1, .1)
        P('root', 0, 0, 0, 1, (0, 0, -.25)); P('body', 0, 0, -.15 + .04 * math.sin(tau * t))
        P('head', .35 * math.sin(tau * t), 0, -.35); P('jaw', 0, 0, -.25); P('tail', 0, 0, -.2)
    elif clip == 'feathers':  # wings fan forward and fling the feather volley at the end
        k = smooth(t / .75); r = smooth((t - .75) / .25)
        wings(.5 * k - .3 * r, .3 * k, .55 * k - 1.0 * r, .4 * k - .6 * r)
        for side in 'lr': pass
        P('body', 0, 0, .2 * k - .3 * r); P('head', 0, 0, -.2 * r); P('tail', 0, 0, 0, 1 + .4 * k)
    elif clip == 'gust':  # wind up high, then one huge downstroke
        k = smooth(t / .7); r = smooth((t - .7) / .3)
        wings(1.1 * k - 1.8 * r, .6 * k - .8 * r, -.2 * k + .3 * r, 0)
        P('body', 0, 0, .3 * k - .4 * r); P('root', 0, 0, 0, 1, (0, 0, .15 * r)); P('tail', 0, 0, -.2 * k, 1 + .3 * k)
    elif clip == 'hurt':
        k = math.sin(math.pi * t)
        wings(.5 * k, -.4 * k, -.3 * k, .3 * k); P('body', 0, 0, .3 * k); P('head', 0, 0, .3 * k); P('jaw', 0, 0, -.5 * k)
    elif clip == 'death':  # wings crumple, the body tips over and falls
        k = smooth(t)
        wings(-.6 * k, -.8 * k, -.4 * k, .5 * k)
        P('root', 1.3 * k, 0, 0, 1, (0, 0, -.2 * k)); P('head', 0, 0, -.6 * k); P('jaw', 0, 0, -.3 * k); P('tail', 0, 0, -.4 * k)


def hawk():
    return bird(1.0, False)


def matriarch():
    return bird(2.4, True)


def heart():
    """Gloom Heart boss: a cracked dark heart leaking light, caged in five thorned two-joint ribs that open, flare and
    slam; roots trail beneath. Hovers with its centre at z=1.2 inside a 2.4-block box."""
    z = 1.2
    bones = [('root', (0, 0, 0), (0, 0, .3), None), ('core', (0, 0, z - .45), (0, 0, z + .45), 'root'),
             ('crown', (0, 0, z + .8), (0, 0, z + 1.35), 'root')]
    tag(blob((0, 0, z), (.42, .42, .47)), 'core', CORE, CORE, .2)
    for i in range(6):  # dark shell lobes; light leaks through the gaps
        a = i * math.tau / 6
        d = Vector((math.cos(a), math.sin(a), (.35 if i % 2 else -.3))).normalized()
        tag(blob(Vector((0, 0, z)) + d * .24, (.29, .29, .32), rot=(0, 0, a)), 'core', HIDE, rough=.35)
    rr = random.Random(7)
    for i in range(5):
        a = i * math.tau / 5 + rr.uniform(-.2, .2); c, s = math.cos(a), math.sin(a); w = rr.uniform(.8, .95)
        pts = [Vector((c * .13, s * .13, z + .85))]
        for k, (r, h) in enumerate(((.68, .6), (.92, .08), (.77, -.5), (.34, -.85))):
            b = a + .3 * (k + 1) / 4
            pts.append(Vector((math.cos(b) * r * w, math.sin(b) * r * w, z + h)))
        bones.append((f'rib_{i}_1', tuple(pts[0]), tuple(pts[2]), 'root'))
        bones.append((f'rib_{i}_2', tuple(pts[2]), tuple(pts[4]), f'rib_{i}_1'))
        tag(tube(pts[:3], [.05, .11, .13]), f'rib_{i}_1', SHEEN, rough=.3)
        tag(tube(pts[2:], [.13, .095, .045]), f'rib_{i}_2', SHEEN, rough=.3)
        tag(tube([pts[1], pts[1].lerp(pts[2], .9)], [.012, .012]), f'rib_{i}_1', GLOW, GLOW, .3)
        for k, bone in ((1, f'rib_{i}_1'), (3, f'rib_{i}_2')):
            o = Vector((pts[k].x, pts[k].y, 0)).normalized()
            tag(tube([pts[k], pts[k] + o * .27 + Vector((0, 0, .1))], [.06, .004]), bone, HIDE, rough=.4)
        tag(tube([pts[4], pts[4] + Vector((c * .17, s * .17, -.38)), pts[4] + Vector((c * .04, s * .04, -.64))], [.045, .03, .006]),
            f'rib_{i}_2', HIDE, rough=.5)
        tag(blob(pts[4] + Vector((c * .04, s * .04, -.64)), (.03, .03, .03), seg=8), f'rib_{i}_2', GLOW, GLOW, .3)
    tag(tube([Vector((0, 0, z + .8)), Vector((0, 0, z + 1.1)), Vector((.1, 0, z + 1.32))], [.1, .05, .008]), 'crown', HIDE, rough=.4)
    return bones


def heart_pose(P, clip, t):
    """Ribs: +rz on rib_N_1 opens the cage outward and up; rib_N_2 follows. Core scale is the heartbeat."""
    tau = math.tau
    smooth = lambda x: max(0, min(1, x)) ** 2 * (3 - 2 * max(0, min(1, x)))
    def beat(x, strength=.06):  # lub-dub
        ph = x % 1
        return strength * (math.exp(-((ph - .1) / .05) ** 2) + .6 * math.exp(-((ph - .28) / .05) ** 2))
    def ribs(open1, open2=0, wobble=0, spin=0):
        for i in range(5):
            P(f'rib_{i}_1', spin, 0, open1 + wobble * math.sin(tau * t * 3 + i * 1.3)); P(f'rib_{i}_2', 0, 0, open2)
    if clip in ('idle', 'move', 'idle_phase2'):
        p2 = clip == 'idle_phase2'
        bpm = 2 if p2 else 1
        P('core', 0, 0, 0, 1 + beat(t * bpm, .1 if p2 else .06))
        ribs(.05 + beat(t * bpm, .3 if p2 else .15), .02, .05 if p2 else .015)
        P('root', 0, 0, 0, 1, (0, 0, .06 * math.sin(tau * t)))
        P('crown', .06 * math.sin(tau * t), 0, 0)
    elif clip == 'telegraph':  # volley: the core swells, ribs part to aim
        k = warn(t)
        P('core', 0, 0, 0, 1 + .25 * k + .03 * math.sin(tau * t * 8) * k); ribs(.35 * k, -.1 * k)
    elif clip == 'ring':  # ribs flare out flat like a crown: a ring of orbs is coming
        k = warn(t)
        P('core', 0, 0, 0, 1 + .18 * k); ribs(.9 * k, -.5 * k, 0, .4 * k)
    elif clip == 'slam':  # rear up: ribs fold high over the core, the whole body lifts, then drops at the end
        k = smooth(t / .8); d = smooth((t - .8) / .2)
        P('root', 0, 0, 0, 1, (0, 0, .5 * k - .9 * d)); ribs(-.35 * k + 1.0 * d, .6 * k - .4 * d)
        P('core', 0, 0, 0, 1 + .1 * k)
    elif clip == 'attack':  # release: ribs snap open and back, the core pulses
        k = math.sin(math.pi * t)
        P('core', 0, 0, 0, 1 + .2 * (1 - t)); ribs(.6 * k, -.2 * k)
    elif clip == 'enrage':  # half health: the cage splays wide and shudders, the core flares
        k = smooth(t * 2); r = smooth((t - .75) / .25)
        P('core', 0, 0, 0, 1 + .3 * k * (1 - r) + .05 * math.sin(tau * t * 10)); ribs(1.0 * k * (1 - r) + .15 * r, -.3 * k * (1 - r), .12 * k)
        P('crown', .2 * math.sin(tau * t * 6) * k, 0, 0)
    elif clip == 'hurt':
        k = math.sin(math.pi * t)
        P('core', 0, 0, 0, 1 - .1 * k); ribs(-.15 * k, .1 * k)
    elif clip == 'death':  # the cage collapses inward, the heart dims to a husk and sinks
        k = smooth(t)
        P('core', 0, 0, 0, 1 - .45 * k); ribs(-.5 * k, .8 * k, .08 * (1 - k))
        P('root', .3 * k, 0, 0, 1, (0, 0, -.6 * k)); P('crown', .8 * k, 0, 0)


CREATURES = {
    'gloom_spitter': (spitter, spitter_pose,
                      {'idle': (3.0, True), 'move': (1.0, True), 'telegraph': (.7, False), 'attack': (.5, False),
                       'hurt': (.4, False), 'death': (1.0, False)}),
    'gloom_beetle': (beetle, beetle_pose,
                     {'idle': (3.0, True), 'move': (.75, True), 'telegraph': (.75, False), 'attack': (.35, False),
                      'charge': (.4, True), 'stunned': (1.5, True), 'flipped': (.8, True), 'hurt': (.35, False), 'death': (1.0, False)}),
    'gloom_hawk': (hawk, bird_pose,
                   {'idle': (3.0, True), 'move': (.6, True), 'telegraph': (1.0, False), 'dive': (.5, True), 'attack': (.4, False),
                    'hurt': (.35, False), 'death': (1.0, False)}),
    'gloom_matriarch': (matriarch, bird_pose,
                        {'idle': (4.0, True), 'move': (1.1, True), 'telegraph': (1.5, False), 'dive': (.6, True), 'attack': (.6, False),
                         'stunned': (2.0, True), 'feathers': (1.0, False), 'gust': (1.25, False), 'enrage': (2.0, False),
                         'hurt': (.5, False), 'death': (1.0, False)}),
    'gloom_heart': (heart, heart_pose,
                    {'idle': (1.5, True), 'idle_phase2': (1.5, True), 'move': (1.5, True), 'telegraph': (1.0, False),
                     'ring': (1.5, False), 'slam': (1.25, False), 'attack': (.4, False), 'enrage': (2.0, False),
                     'hurt': (.4, False), 'death': (1.0, False)}),
}


# ---------- build ----------
build, pose_fn, CLIPS = CREATURES[NAME]
bpy.ops.wm.read_factory_settings(use_empty=True)
out_root = ROOT / 'art/source/monsters' / NAME
revision = 1 + max((int(d.name[1:]) for d in out_root.glob('v[0-9][0-9][0-9]')), default=0)
OUT = out_root / f'v{revision:03}'; OUT.mkdir(parents=True)
bone_specs = build()

# Armature: each bone's Z axis points along the creature's forward-ish tangent so X/Z rotations read predictably.
bpy.ops.object.armature_add(location=(0, 0, 0)); rig = bpy.context.object; rig.name = NAME + '_rig'
bpy.ops.object.mode_set(mode='EDIT')
eb = rig.data.edit_bones; eb.remove(eb[0])
for name, head, tail, parent in bone_specs:
    b = eb.new(name); b.head = head; b.tail = tail
    d = (Vector(tail) - Vector(head)).normalized()
    radial = Vector((d.x, d.y, 0))
    b.align_roll(radial.cross(Vector((0, 0, 1))).normalized() if radial.length > .2 else Vector((0, -1, 0)))
    if parent: b.parent = eb[parent]
bpy.ops.object.mode_set(mode='OBJECT')

# Mesh: convert, bake transforms, write per-part weights and colours, then join.
meshes = []
for o in PARTS:
    bpy.ops.object.select_all(action='DESELECT'); o.select_set(True); bpy.context.view_layer.objects.active = o
    if o.type == 'CURVE':
        info = {k: o[k] for k in o.keys()}
        bpy.ops.object.convert(target='MESH'); o = bpy.context.object
        for k, v in info.items(): o[k] = v
        bpy.ops.object.shade_smooth()
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    me = o.data
    base, emit, rough = Vector(o['base']), Vector(o['emit']), o['rough']
    col = me.color_attributes.new('base', 'FLOAT_COLOR', 'POINT')
    glo = me.color_attributes.new('emit', 'FLOAT_COLOR', 'POINT')
    orm = me.color_attributes.new('orm', 'FLOAT_COLOR', 'POINT')
    for v in me.vertices:
        # Darker toward the ground, faint mottling: reads as hide, not plastic.
        p = v.co
        n = .85 + .3 * (math.sin(p.x * 23 + p.y * 17) * math.sin(p.z * 19 - p.x * 11) * .5 + .5)
        lift = .55 + .45 * min(1, max(0, p.z / 1.0))
        c = base * (n * lift if emit.length == 0 else 1)
        col.data[v.index].color = (*c, 1)
        glo.data[v.index].color = (*emit, 1)
        orm.data[v.index].color = (1, rough, 0, 1)
    if 'chain' in o:
        chain = json.loads(o['chain'])
        for bone, _ in chain:
            if bone not in o.vertex_groups: o.vertex_groups.new(name=bone)
        for v in me.vertices:
            z = v.co.z
            for k, (bone, z0) in enumerate(chain):
                z1 = chain[k + 1][1] if k + 1 < len(chain) else z0 + 10
                zp = chain[k - 1][1] if k > 0 else z0 - 10
                w = 0
                if zp <= z < z0: w = (z - zp) / (z0 - zp) if k > 0 else 1
                elif z0 <= z < z1: w = 1 - (z - z0) / (z1 - z0) if k + 1 < len(chain) else 1
                if w > 0: o.vertex_groups[bone].add([v.index], w, 'REPLACE')
    else:
        g = o.vertex_groups.new(name=o['bone']); g.add([v.index for v in me.vertices], 1.0, 'REPLACE')
    meshes.append(o)
bpy.ops.object.select_all(action='DESELECT')
for o in meshes: o.select_set(True)
bpy.context.view_layer.objects.active = meshes[0]
bpy.ops.object.join(); body = bpy.context.object; body.name = NAME + '_body'
for k in ('bone', 'base', 'emit', 'rough', 'chain'):
    if k in body: del body[k]
body.parent = rig
mod = body.modifiers.new('rig', 'ARMATURE'); mod.object = rig

# Bake the colour layers to three textures on one runtime UV set (the GemRender contract used by ori.glb).
body.data.materials.clear()
mat = bpy.data.materials.new(NAME); mat.use_nodes = True; body.data.materials.append(mat)
for layer in list(body.data.uv_layers): body.data.uv_layers.remove(layer)
uv = body.data.uv_layers.new(name='runtime'); body.data.uv_layers.active = uv
bpy.ops.object.select_all(action='DESELECT'); body.select_set(True); bpy.context.view_layer.objects.active = body
bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=.02, area_weight=.4)
bpy.ops.object.mode_set(mode='OBJECT')
scene = bpy.context.scene
scene.render.engine = 'CYCLES'; scene.cycles.samples = 1; scene.render.bake.margin = 6
images = {}
for suffix, layer in (('base', 'base'), ('emission', 'emit'), ('surface', 'orm')):
    img = bpy.data.images.new(f'{NAME}_{suffix}', 512, 512, alpha=True)
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

# Animations.
scene.render.fps = FPS
rig.animation_data_create()
pb = rig.pose.bones
for b in pb: b.rotation_mode = 'XYZ'
meta = {}
for clip, (seconds, loop) in CLIPS.items():
    act = bpy.data.actions.new(clip); act.use_fake_user = True; act['loop'] = loop
    rig.animation_data.action = act
    frames = round(seconds * FPS)
    for f in range(frames + 1):
        for b in pb: b.rotation_euler = (0, 0, 0); b.scale = (1, 1, 1); b.location = (0, 0, 0)
        def P(bone, rx, ry, rz, s=1, loc=None):
            b = pb[bone]; b.rotation_euler = (rx, ry, rz); b.scale = (s, s, s)
            if loc: b.location = loc
        pose_fn(P, clip, f / frames)
        for b in pb:
            for prop in ('rotation_euler', 'location', 'scale'): b.keyframe_insert(prop, frame=f, group=b.name)
    meta[clip] = {'seconds': seconds, 'loop': loop, 'frames': frames}
rig.animation_data.action = bpy.data.actions['idle']

# Export.
bpy.ops.object.select_all(action='DESELECT'); rig.select_set(True); body.select_set(True); bpy.context.view_layer.objects.active = rig
glb = OUT / f'{NAME}.glb'
bpy.ops.export_scene.gltf(filepath=str(glb), export_format='GLB', use_selection=True, export_animations=True,
                          export_animation_mode='ACTIONS', export_force_sampling=True, export_frame_range=False,
                          export_extras=True, export_copyright='OriMod original creature.')
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
manifest = {'creature': NAME, 'revision': revision, 'fps': FPS, 'clips': meta, 'vertices': len(body.data.vertices),
            'bones': [b[0] for b in bone_specs], 'export': glb.relative_to(ROOT).as_posix(),
            'export_sha256': hashlib.sha256(glb.read_bytes()).hexdigest(), 'blend': blend.relative_to(ROOT).as_posix(),
            'textures': textures}
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps({'revision': revision, 'output': str(OUT), 'vertices': len(body.data.vertices)}))
