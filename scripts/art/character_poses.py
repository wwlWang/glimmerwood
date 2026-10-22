"""R112 key-pose clips: each clip is a few named poses on a timeline, eased between, plus cyclic layers.

A pose is a flat dict of named joint parameters (radians / metres), so poses blend by plain interpolation.
Signs: forward (Blender -Y) and flexion are positive. Bone suffix _l is the anatomical right (-X).
  drop      root lowered (m)            pitch/roll/yaw   whole body (+pitch tips forward, +yaw turns left)
  hip       pelvis pitch                spine            abdomen+spine pitch (+ curls forward)
  twist     spine yaw (+ left)          lean             spine roll (+ toward anatomical left)
  head      neck+head pitch (+ down)    head_yaw         (+ left)            jaw  open
  out_s     arm abduction from hanging  fwd_s            arm raise forward (pi = straight up, negative = back)
  bend_s    elbow flex                  wrist_s          wrist flex          grip_s  finger curl 0..1
  thigh_s   hip flexion (+ knee forward)    knee_s  knee flex    hock_s  hock flex
  foot_s    (forward, up) metres: when set, the leg is solved so the ankle sits there, relative to its rest
  tail      tail curl (+ tip up, spread along the chain)   tail_base  root joint only   tail_side   (+ left)
  ears      ears back (+ swept back)   ear_flop  extra droop on the tips
"""
import math

BASE = dict(drop=0, pitch=0, roll=0, yaw=0, hip=0, spine=0, twist=0, lean=0, head=0, head_yaw=0, jaw=0,
            out_l=.2, fwd_l=0, bend_l=.2, wrist_l=0, grip_l=0, out_r=.2, fwd_r=0, bend_r=.2, wrist_r=0, grip_r=0,
            thigh_l=0, knee_l=0, hock_l=0, thigh_r=0, knee_r=0, hock_r=0, foot_l=None, foot_r=None,
            tail=0, tail_base=0, tail_side=0, ears=0, ear_flop=0)

# Ori's neutral: digitigrade and loaded, never standing straight (docs/ANIMATION_REFERENCE.md).
STANCE = dict(drop=.075, hip=.10, spine=.16, head=-.20, out_l=.30, out_r=.30, fwd_l=.30, fwd_r=.30, bend_l=.55, bend_r=.55,
              foot_l=(0, 0), foot_r=(0, 0), tail=.10, ears=.10)


def smooth(t):
    t = max(0, min(1, t))
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = max(0, min(1, t))
    return 1 - (1 - t) ** 3


def ease_in(t):
    t = max(0, min(1, t))
    return t * t * t


def pose(*layers, **values):
    """BASE overlaid by each layer in turn, then by the keyword values."""
    out = dict(BASE)
    for layer in layers: out.update(layer)
    out.update(values)
    return out


def mix(a, b, f):
    out = {}
    for k in a:
        x, y = a[k], b[k]
        if x is None or y is None:
            out[k] = y if f >= .5 else x
        elif isinstance(x, tuple):
            out[k] = tuple(p + (q - p) * f for p, q in zip(x, y))
        else:
            out[k] = x + (y - x) * f
    return out


def keys(t, frames, ease=smooth):
    """frames: [(time, pose), ...] ascending in 0..1; eased between neighbours, held outside."""
    if t <= frames[0][0]: return dict(frames[0][1])
    for (t0, a), (t1, b) in zip(frames, frames[1:]):
        if t <= t1: return mix(a, b, ease((t - t0) / max(1e-6, t1 - t0)))
    return dict(frames[-1][1])


def add(p, **delta):
    """Offset numeric parameters (cyclic layers on top of a keyed pose)."""
    for k, v in delta.items():
        if isinstance(p[k], tuple): p[k] = tuple(a + b for a, b in zip(p[k], v))
        else: p[k] = (p[k] or 0) + v
    return p


def apply(ctx, p):
    """Pose the rig from a parameter dict. ctx exposes rotate/shift/leg/elbow/gripping/ankle_rest."""
    rotate, shift = ctx.rotate, ctx.shift
    X, Y, Z = (1, 0, 0), (0, 1, 0), (0, 0, 1)
    shift('root', (0, 0, -p['drop']))
    rotate('root', p['pitch'], X); rotate('root', p['roll'], Y); rotate('root', p['yaw'], Z)
    rotate('pelvis', p['hip'], X)
    for bone, share in (('abdomen', .45), ('spine', .55)):
        rotate(bone, p['spine'] * share, X); rotate(bone, p['twist'] * share, Z); rotate(bone, p['lean'] * share, Y)
    for bone, share in (('neck', .4), ('head', .6)):
        rotate(bone, p['head'] * share, X); rotate(bone, p['head_yaw'] * share, Z)
    rotate('jaw', p['jaw'], X)
    for s, sign in (('l', -1), ('r', 1)):
        rotate('upper_arm_' + s, sign * (math.pi / 2 - p['out_' + s]), Y)
        rotate('upper_arm_' + s, -p['fwd_' + s], X)
        ctx.elbow(s, -p['bend_' + s])
        rotate('hand_' + s, p['wrist_' + s], X)
        if p['grip_' + s]: ctx.gripping(s, .45 * p['grip_' + s])
    for s in ('l', 'r'):
        rotate('hock_' + s, -p['hock_' + s], X)
        if p['foot_' + s] is not None:
            target = ctx.ankle_rest[s].copy()
            target.y -= p['foot_' + s][0]; target.z += p['foot_' + s][1]
            ctx.leg(s, target)
        else:
            rotate('thigh_' + s, -p['thigh_' + s], X); rotate('knee_' + s, p['knee_' + s], X)
    rotate('tail_1', p['tail_base'], X)
    for i in range(1, 6):
        rotate('tail_' + str(i), p['tail'] * (.12 + .05 * i), X)
        rotate('tail_' + str(i), p['tail_side'] * .2, Z)
    for s in ('l', 'r'):
        for i in range(1, 4):
            rotate(f'ear_{s}_{i}', -p['ears'] * (.5 if i == 1 else .25) + p['ear_flop'] * (i - 1) * .3, X)
