"""R112 clip library on the key-pose framework (character_poses). name -> (frames, loop, fn(t) -> pose)."""
import math
from character_poses import BASE, STANCE, pose, keys, add, mix, smooth, ease_out, ease_in

CLIPS = {}
STANCE = pose(STANCE)  # a full pose, so it can be a key frame as well as a layer


def clip(name, frames, loop):
    def register(fn):
        CLIPS[name] = (frames, loop, fn)
        return fn
    return register


TAU = math.tau


def wave(t, cycles=1, offset=0): return math.sin((t * cycles + offset) * TAU)


def pulse(t): return math.sin(math.pi * max(0, min(1, t)))


def gait(t, stride, duty, lift, offset):
    """In-place foot cycle: (forward, up) for a foot whose stance starts at phase `offset`."""
    p = (t + offset) % 1
    if p < duty: return (stride * duty / 2 - stride * p, 0)
    f = (p - duty) / (1 - duty)
    return (-stride * duty / 2 + stride * duty * smooth(f), math.sin(math.pi * f) * lift)


# ---- standing -------------------------------------------------------------------------------------------------
@clip('idle', 90, True)
def _(t):
    p = pose(STANCE)
    flick = pulse((t - .55) / .07) if .55 < t < .62 else 0  # one ear twitch per breath cycle
    return add(p, drop=.006 * wave(t), spine=.035 * wave(t), head=-.03 * wave(t, 1, .15), roll=.02 * wave(t, 1, .25),
               head_yaw=.10 * wave(t, 1, .4), tail_side=.18 * wave(t, 1, .3), ears=-.3 * flick, fwd_l=.04 * wave(t), fwd_r=.04 * wave(t, 1, .5))


@clip('alert', 60, True)
def _(t):
    look = keys(t, [(0, dict(y=0)), (.15, dict(y=.45)), (.45, dict(y=.45)), (.6, dict(y=-.35)), (.85, dict(y=-.35)), (1, dict(y=0))])['y']
    p = pose(STANCE, drop=.05, ears=-.15, tail=.3, out_l=.35, out_r=.35, fwd_l=.4, fwd_r=.4)
    return add(p, head_yaw=look, twist=look * .25, spine=.02 * wave(t))


CROUCH = dict(STANCE, drop=.20, hip=.25, spine=.45, head=-.6, out_l=.38, out_r=.38, fwd_l=.6, fwd_r=.6, bend_l=1.0, bend_r=1.0,
              ears=.6, tail=-.2)


@clip('crouch', 60, True)
def _(t): return add(pose(CROUCH), drop=.005 * wave(t), spine=.025 * wave(t), tail_side=.12 * wave(t, 1, .3))


@clip('crouch_walk', 30, True)
def _(t):
    p = pose(CROUCH)
    p['foot_l'], p['foot_r'] = gait(t, .55, .65, .07, 0), gait(t, .55, .65, .07, .5)
    return add(p, drop=.008 * abs(wave(t, 2)), twist=.08 * wave(t), fwd_l=.18 * wave(t, 1, .5), fwd_r=.18 * wave(t), tail_side=.2 * wave(t, 1, .2))


@clip('crawl', 30, True)
def _(t):
    # On all fours under a one-block gap: body flat, head low, hands and knees alternating.
    w = wave(t)
    p = pose(drop=.16, pitch=1.30, tail_base=-1.1, hip=.15, spine=.10, head=-1.05, out_l=.30, out_r=.30, fwd_l=.55, fwd_r=.55, bend_l=.45, bend_r=.45,
             thigh_l=1.35, thigh_r=1.35, knee_l=1.55, knee_r=1.55, ears=.85, tail=-.2)
    return add(p, fwd_l=.35 * w, fwd_r=-.35 * w, bend_l=.25 * max(0, w), bend_r=.25 * max(0, -w), thigh_l=-.30 * w, thigh_r=.30 * w,
               twist=.10 * w, tail_side=.25 * wave(t, 1, .25), drop=.01 * abs(wave(t, 2)))


# ---- ground locomotion ----------------------------------------------------------------------------------------
@clip('walk', 30, True)
def _(t):
    p = pose(STANCE, drop=.09, pitch=.04, spine=.12, head=-.18, fwd_l=.10, fwd_r=.10, bend_l=.45, bend_r=.45, ears=.15)
    p['foot_l'], p['foot_r'] = gait(t, .80, .50, .09, 0), gait(t, .80, .50, .09, .5)
    w = wave(t)
    return add(p, drop=-.012 * (1 - math.cos(t * 2 * TAU)) / 2, twist=.07 * w, head_yaw=-.04 * w, fwd_l=.38 * w, fwd_r=-.38 * w,
               tail_side=.15 * wave(t, 1, .2))


@clip('run', 24, True)
def _(t):
    # Ori runs low and hard into the stride with the arms swept back, not pumping forward.
    p = pose(STANCE, drop=.125, pitch=.30, hip=.05, spine=.24, head=-.62, out_l=.38, out_r=.38, fwd_l=-.55, fwd_r=-.55,
             bend_l=.95, bend_r=.95, wrist_l=-.3, wrist_r=-.3, ears=.75, tail=-.05, tail_base=-.30)
    # The 0.3 rad lean carries the hips forward; centre the stride under them.
    p['foot_l'], p['foot_r'] = [(f + .10, u) for f, u in (gait(t, 2.0, .21, .26, 0), gait(t, 2.0, .21, .26, .5))]
    w = wave(t)
    return add(p, drop=-.05 * abs(math.sin(t * 2 * math.pi)) + .025, twist=.16 * w, head_yaw=-.10 * w, fwd_l=.40 * w, fwd_r=-.40 * w,
               lean=.04 * w, tail_side=.18 * wave(t, 1, .3), spine=.03 * wave(t, 2))


@clip('start', 9, False)
def _(t): return keys(t, [(0, pose(STANCE)), (.4, pose(STANCE, drop=.11, pitch=.18, spine=.30, head=-.45, fwd_l=-.3, fwd_r=.5, ears=.4)),
                          (1, pose(STANCE, drop=.095, pitch=.30, spine=.24, head=-.62, fwd_l=-.55, fwd_r=-.55, bend_l=.95, bend_r=.95, ears=.75))])


@clip('stop', 12, False)
def _(t):
    skid = pose(STANCE, drop=.13, pitch=-.12, spine=.05, head=-.1, out_l=.7, out_r=.7, fwd_l=.9, fwd_r=.9, ears=-.2, ear_flop=.4, tail=.45,
                foot_l=(.22, 0), foot_r=(-.10, 0))
    return keys(t, [(0, skid), (.35, add(dict(skid), drop=.02)), (1, pose(STANCE))], ease=smooth)


# ---- air ------------------------------------------------------------------------------------------------------
LOAD = pose(STANCE, drop=.15, pitch=.10, spine=.35, head=-.40, fwd_l=-.45, fwd_r=-.45, out_l=.4, out_r=.4, ears=.3)
RISE = pose(pitch=.06, spine=-.10, head=-.30, out_l=.45, out_r=.45, fwd_l=-.55, fwd_r=-.40, bend_l=.45, bend_r=.55,
            thigh_l=1.15, knee_l=1.55, thigh_r=-.15, knee_r=.40, ears=.45, tail=-.30)
APEX = pose(pitch=.04, spine=.22, head=-.15, out_l=.75, out_r=.75, fwd_l=.35, fwd_r=.35, bend_l=.9, bend_r=.9,
            thigh_l=.85, knee_l=1.35, thigh_r=.65, knee_r=1.15, ears=0, ear_flop=.3, tail=.15)
FALL = pose(pitch=-.04, spine=-.05, head=.12, out_l=1.3, out_r=1.3, fwd_l=1.9, fwd_r=2.0, bend_l=.35, bend_r=.3,
            thigh_l=.28, knee_l=.40, thigh_r=-.05, knee_r=.55, ears=-.95, ear_flop=-.3, tail=.5)


@clip('jump_start', 9, False)
def _(t): return keys(t, [(0, LOAD), (1, RISE)], ease=ease_out)


@clip('rise', 24, True)
def _(t): return add(dict(RISE), fwd_l=.06 * wave(t), fwd_r=.06 * wave(t, 1, .3), thigh_r=.06 * wave(t, 1, .2), tail_side=.1 * wave(t))


@clip('apex', 18, True)
def _(t): return add(dict(APEX), out_l=.06 * wave(t), out_r=.06 * wave(t), knee_l=.05 * wave(t, 1, .25))


@clip('fall', 24, True)
def _(t):
    w = wave(t)
    return add(dict(FALL), out_l=.10 * w, out_r=-.10 * w, fwd_l=.06 * wave(t, 2), thigh_l=.10 * w, thigh_r=-.10 * w, ear_flop=-.1 * wave(t, 2),
               tail_side=.15 * wave(t, 1, .2))


def landing(t, depth):
    hit = pose(STANCE, drop=.10 + .12 * depth, pitch=.08 + .1 * depth, spine=.30 + .25 * depth, head=-.35 - .1 * depth,
               out_l=.55 + .4 * depth, out_r=.55 + .4 * depth, fwd_l=.55 + .45 * depth, fwd_r=.55 + .45 * depth, bend_l=.5, bend_r=.5,
               ears=-.2, ear_flop=.5 + .4 * depth, tail=-.3, foot_l=(.06, 0), foot_r=(-.06, 0))
    return keys(t, [(0, hit), (.22 + .1 * depth, add(dict(hit), drop=.03 * depth, spine=.08)), (1, pose(STANCE))])


@clip('land_light', 9, False)
def _(t): return landing(t, 0)


@clip('land_heavy', 15, False)
def _(t): return landing(t, 1)


# ---- walls and climbing (Ori faces the wall: facing follows the body yaw while clinging) ------------------------
CLING = pose(drop=.04, pitch=-.06, spine=.05, head=-.35, out_l=.65, out_r=.65, fwd_l=2.15, fwd_r=2.45, bend_l=.55, bend_r=.45,
             wrist_l=-.5, wrist_r=-.5, grip_l=.6, grip_r=.6, thigh_l=.95, knee_l=1.35, thigh_r=.55, knee_r=1.0, ears=.3, tail=-.35)


@clip('wall_hold', 30, True)
def _(t): return add(dict(CLING), spine=.03 * wave(t), drop=.004 * wave(t), tail_side=.15 * wave(t, 1, .3), head_yaw=.15 * wave(t, 1, .2))


@clip('wall_slide', 30, True)
def _(t):
    # Arms drag high on the wall, claws catching (a fast shiver), ears blown up by the slide.
    p = pose(CLING, fwd_l=2.55, fwd_r=2.6, bend_l=.3, bend_r=.3, thigh_l=.7, knee_l=1.0, thigh_r=.45, knee_r=.8, ears=-.45, ear_flop=-.3, tail=.2)
    return add(p, fwd_l=.04 * wave(t, 6), fwd_r=-.04 * wave(t, 6, .3), spine=.02 * wave(t, 6), tail_side=.12 * wave(t))


@clip('wall_climb', 24, True)
def _(t):
    # A cat-like scramble up the face: hand over hand, feet pushing in turn.
    w, c = wave(t), wave(t, 1, .25)
    p = pose(CLING, pitch=-.02, ears=.2)
    return add(p, fwd_l=.5 * w, fwd_r=-.5 * w, bend_l=.35 * max(0, -w), bend_r=.35 * max(0, w), thigh_l=-.4 * w, thigh_r=.4 * w,
               knee_l=.3 * c, knee_r=-.3 * c, twist=.10 * w, lean=.06 * w, drop=.015 * abs(wave(t, 2)), tail_side=.25 * c)


def wall_jump(t, sign):
    coil = pose(CLING, drop=.10, thigh_l=1.35, knee_l=1.85, thigh_r=1.2, knee_r=1.75, bend_l=1.0, bend_r=1.0, fwd_l=1.9, fwd_r=1.9, ears=.5)
    kick = pose(pitch=-.45, spine=-.35, head=-.45, out_l=.9, out_r=.9, fwd_l=2.6, fwd_r=2.4, bend_l=.25, bend_r=.25,
                thigh_l=-.35, knee_l=.25, thigh_r=-.25, knee_r=.35, twist=.55 * sign, lean=.25 * sign, ears=.8, tail=-.5)
    return keys(t, [(0, coil), (.3, kick), (1, mix(kick, RISE, .7))], ease=ease_out)


@clip('wall_jump_l', 12, False)
def _(t): return wall_jump(t, -1)


@clip('wall_jump_r', 12, False)
def _(t): return wall_jump(t, 1)


@clip('climb', 30, True)
def _(t):
    # Ladders and vines: facing the rungs, hand over hand with the feet stepping up in turn.
    w = wave(t)
    p = pose(drop=.02, pitch=.06, spine=.08, head=-.40, out_l=.45, out_r=.45, fwd_l=2.2, fwd_r=2.2, bend_l=.75, bend_r=.75,
             wrist_l=-.3, wrist_r=-.3, grip_l=1, grip_r=1, thigh_l=.75, thigh_r=.75, knee_l=1.2, knee_r=1.2, ears=.25, tail=-.45)
    return add(p, fwd_l=.45 * w, fwd_r=-.45 * w, bend_l=.35 * max(0, -w), bend_r=.35 * max(0, w), thigh_l=-.4 * w, thigh_r=.4 * w,
               knee_l=-.25 * w, knee_r=.25 * w, twist=.06 * w, tail_side=.15 * wave(t, 1, .25))


@clip('mantle', 12, False)
def _(t):
    # Pull up over a lip: hands planted, then press down, knee up onto the edge, settle.
    hang = pose(drop=.10, pitch=.15, spine=.2, head=-.4, out_l=.5, out_r=.5, fwd_l=2.0, fwd_r=2.0, bend_l=1.1, bend_r=1.1, grip_l=.7, grip_r=.7,
                thigh_l=.6, knee_l=1.1, thigh_r=.3, knee_r=.8, ears=.4, tail=-.4)
    press = pose(drop=.15, pitch=.55, spine=.35, head=-.7, out_l=.55, out_r=.55, fwd_l=.6, fwd_r=.6, bend_l=.3, bend_r=.3,
                 thigh_l=1.5, knee_l=1.9, thigh_r=.2, knee_r=.9, ears=.5, tail=-.2)
    return keys(t, [(0, hang), (.55, press), (1, pose(STANCE, drop=.12, spine=.3))])


# ---- water ----------------------------------------------------------------------------------------------------
@clip('swim', 30, True)
def _(t):
    # Body frame (the runtime lays the body along the swim velocity, ~90 deg). Underwater: streamlined, a dolphin kick with the legs together and a wave through the body.
    w, lag = wave(t), wave(t, 1, -.2)
    p = pose(hip=-.05, spine=.0, head=-1.15, out_l=.22, out_r=.22, fwd_l=-.35, fwd_r=-.35, bend_l=.15, bend_r=.15,
             thigh_l=.25, thigh_r=.25, knee_l=.35, knee_r=.35, ears=.95, tail=-.15, tail_base=-1.2)
    return add(p, spine=.10 * w, hip=-.12 * lag, head=-.08 * w, thigh_l=.35 * lag, thigh_r=.35 * lag, knee_l=.30 * max(0, -lag),
               knee_r=.30 * max(0, -lag), tail=.25 * wave(t, 1, -.4), fwd_l=.1 * w, fwd_r=.1 * w, drop=-.02 * w)


@clip('swim_surface', 24, True)
def _(t):
    # At the surface: head and shoulders up, quick paddling forearms, legs kicking below.
    w, c = wave(t), wave(t, 1, .25)
    p = pose(pitch=.80, spine=.05, head=-.95, out_l=.35, out_r=.35, fwd_l=1.1, fwd_r=1.1, bend_l=1.0, bend_r=1.0,
             thigh_l=.25, thigh_r=.25, knee_l=.7, knee_r=.7, ears=.35, tail=-.1, tail_base=-.7)
    return add(p, fwd_l=.55 * w, fwd_r=-.55 * w, bend_l=.4 * c, bend_r=-.4 * c, thigh_l=.45 * w, thigh_r=-.45 * w,
               twist=.06 * w, tail_side=.3 * wave(t, 1, .3), head=.04 * wave(t, 2))


@clip('swim_idle', 40, True)
def _(t):
    # Treading water: upright, arms sculling wide, legs cycling slowly.
    w, c = wave(t), wave(t, 1, .25)
    p = pose(pitch=.12, spine=.1, head=-.25, out_l=.95, out_r=.95, fwd_l=.55, fwd_r=.55, bend_l=.5, bend_r=.5,
             thigh_l=.45, thigh_r=.45, knee_l=.9, knee_r=.9, ears=.25, ear_flop=.2, tail=-.3)
    return add(p, out_l=.22 * w, out_r=.22 * w, fwd_l=.25 * c, fwd_r=.25 * c, thigh_l=.3 * w, thigh_r=-.3 * w, knee_l=.2 * c, knee_r=-.2 * c,
               drop=.012 * wave(t, 2), tail_side=.3 * c)


@clip('swim_dash', 12, True)
def _(t):
    # Body frame, like swim. Water dash: a corkscrew torpedo, arms ahead, body stretched, one full roll per cycle.
    return pose(spine=-.05, head=-1.25, out_l=.12, out_r=.12, fwd_l=3.0, fwd_r=3.0, bend_l=.05, bend_r=.05,
                thigh_l=-.08, thigh_r=-.08, knee_l=.1, knee_r=.1, ears=1.0, tail=-.1, tail_base=-1.3, roll=TAU * t)


# ---- abilities ------------------------------------------------------------------------------------------------
# The authoring pass places both paws on the fixed-size feather's shaft.
GLIDE = pose(pitch=-.04, spine=-.06, head=-.22, out_l=.55, out_r=.55, fwd_l=2.95, fwd_r=2.95, bend_l=.15, bend_r=.15, grip_l=1, grip_r=1,
             thigh_l=.28, knee_l=.45, thigh_r=.12, knee_r=.6, ears=.35, tail=-.25)


@clip('glide', 36, True)
def _(t):
    # Hanging from Kuro's feather held straight overhead, legs dangling and swaying.
    w = wave(t)
    return add(dict(GLIDE), roll=.05 * w, thigh_l=.12 * w, thigh_r=-.12 * w, knee_l=-.1 * wave(t, 1, .2), knee_r=.1 * wave(t, 1, .2),
               spine=.03 * wave(t, 2), tail_side=.3 * wave(t, 1, .25), head_yaw=.08 * w)


@clip('glide_open', 9, False)
def _(t): return keys(t, [(0, FALL), (1, GLIDE)], ease=ease_out)


@clip('glide_close', 9, False)
def _(t): return keys(t, [(0, GLIDE), (1, FALL)])


BALL = pose(drop=.12, spine=.85, head=.35, out_l=.3, out_r=.3, fwd_l=1.0, fwd_r=1.0, bend_l=1.9, bend_r=1.9,
            thigh_l=1.75, thigh_r=1.75, knee_l=2.1, knee_r=2.1, ears=.95, tail=.9)


@clip('bash_aim', 30, True)
def _(t): return add(dict(BALL), spine=.02 * wave(t, 3), head_yaw=.05 * wave(t), tail_side=.1 * wave(t, 1, .3))


@clip('grapple', 24, True)
def _(t):
    # Thrown arm locked on the target, pulled along the line, body and legs trailing.
    w = wave(t)
    p = pose(pitch=.55, spine=-.12, head=-.75, out_r=.15, fwd_r=1.75, bend_r=.05, grip_r=1, out_l=.4, fwd_l=-.65, bend_l=.6,
             thigh_l=-.30, knee_l=.35, thigh_r=-.05, knee_r=.8, ears=.95, tail=-.7)
    return add(p, thigh_l=.08 * w, thigh_r=-.08 * w, tail_side=.15 * wave(t, 2))


STOMP = pose(spine=-.12, head=.40, out_l=.75, out_r=.75, fwd_l=2.55, fwd_r=2.55, bend_l=.3, bend_r=.3,
             thigh_l=.06, knee_l=.12, thigh_r=.0, knee_r=.18, ears=-1.0, ear_flop=-.3, tail=.85)


@clip('stomp_start', 12, False)
def _(t):
    # A tucked front flip that opens into the feet-first plummet.
    p = keys(t, [(0, APEX), (.2, BALL), (.75, BALL), (1, STOMP)])
    p['pitch'] += TAU * smooth(min(1, t / .8))
    return p


@clip('stomp', 18, True)
def _(t): return add(dict(STOMP), out_l=.05 * wave(t, 3), out_r=-.05 * wave(t, 3), ear_flop=-.15 * wave(t, 3), tail_side=.08 * wave(t, 2))


@clip('launch', 12, False)
def _(t):
    rocket = pose(pitch=.15, spine=-.15, head=-.3, out_l=.1, out_r=.1, fwd_l=3.0, fwd_r=3.0, bend_l=.05, bend_r=.05,
                  thigh_l=-.12, thigh_r=-.08, knee_l=.08, knee_r=.12, ears=1.0, tail=-.45)
    return keys(t, [(0, BALL), (.3, rocket), (1, rocket)], ease=ease_out)


# ---- combat (weapon in the _r hand, the anatomical left; character_animations mirrors these for the other hand) --
# Swing clips follow the runtime curve (FeelTuning.swingCurve): raised by RAISE, strike finished by STRIKE.
RAISE, STRIKE = .24, .45
READY = pose(STANCE, out_r=.35, fwd_r=.55, bend_r=.6, grip_r=1)


def swing(t, wound, struck, settle=None):
    return keys(t, [(0, READY), (RAISE, wound), (STRIKE, struck), (min(.8, STRIKE + .25), settle or struck), (1, READY)])


@clip('blade_1', 18, False)
def _(t):
    # Forehand: coil to the weapon side, step in and sweep flat across the front.
    wound = pose(READY, drop=.10, twist=.55, spine=.12, head_yaw=-.3, out_r=1.35, fwd_r=-.35, bend_r=.85, wrist_r=.4,
                 out_l=.5, fwd_l=.7, bend_l=.9, foot_l=(.05, 0), foot_r=(-.08, 0), ears=.3)
    struck = pose(READY, drop=.13, twist=-.65, spine=.30, head_yaw=.25, out_r=.45, fwd_r=1.55, bend_r=.05, wrist_r=-.3,
                  out_l=.6, fwd_l=-.5, bend_l=.7, foot_l=(-.10, 0), foot_r=(.20, 0), ears=.6, tail_side=.5)
    return swing(t, wound, struck)


@clip('blade_2', 20, False)
def _(t):
    # Backhand: blade carried across the chest, then whipped out to the weapon side.
    wound = pose(READY, drop=.11, twist=-.55, spine=.18, head_yaw=.2, out_r=.15, fwd_r=1.15, bend_r=1.5, wrist_r=.5,
                 out_l=.6, fwd_l=-.3, bend_l=.8, foot_l=(.12, 0), foot_r=(-.05, 0), ears=.35)
    struck = pose(READY, drop=.12, twist=.60, spine=.25, head_yaw=-.25, out_r=1.45, fwd_r=.55, bend_r=.05, wrist_r=-.2,
                  out_l=.5, fwd_l=.8, bend_l=.9, foot_l=(-.06, 0), foot_r=(.16, 0), ears=.6, tail_side=-.5)
    return swing(t, wound, struck)


@clip('blade_3', 27, False)
def _(t):
    # Finisher: rise onto the toes with the blade high behind the head, then a full overhead chop into a crouch.
    wound = pose(READY, drop=-.02, spine=-.30, head=-.35, twist=.2, out_r=.45, fwd_r=3.25, bend_r=.95, wrist_r=.6,
                 out_l=.7, fwd_l=1.2, bend_l=.4, foot_l=(.05, .02), foot_r=(-.05, .02), ears=-.2, tail=.4)
    struck = pose(READY, drop=.20, spine=.70, head=-.55, twist=-.15, out_r=.3, fwd_r=.75, bend_r=.05, wrist_r=-.4,
                  out_l=.6, fwd_l=-.4, bend_l=.6, foot_l=(-.12, 0), foot_r=(.22, 0), ears=.7, ear_flop=.4, tail=-.4)
    return swing(t, wound, struck)


@clip('blade_air', 15, False)
def _(t):
    # Air slash: a tucked horizontal spin with the blade held out, one full turn.
    spin = pose(drop=-.02, spine=.25, head=-.25, out_r=1.45, fwd_r=.85, bend_r=.05, grip_r=1, out_l=.8, fwd_l=.4, bend_l=1.0,
                thigh_l=1.1, knee_l=1.6, thigh_r=.8, knee_r=1.4, ears=.8, tail=.3)
    p = keys(t, [(0, APEX), (.2, spin), (.8, spin), (1, APEX)])
    p['yaw'] += TAU * smooth((t - .1) / .75)
    return p


SPEAR_COCK = pose(READY, drop=.08, spine=-.18, head=-.25, twist=.70, head_yaw=-.55, out_r=.55, fwd_r=2.85, bend_r=1.05, wrist_r=.3,
                  out_l=.35, fwd_l=1.45, bend_l=.1, foot_l=(.16, 0), foot_r=(-.16, 0), ears=.25)


@clip('throw_ready', 30, True)
def _(t):
    # Spear / trident held cocked over the shoulder, the free hand sighting the target.
    return add(dict(SPEAR_COCK), twist=.05 * wave(t), fwd_r=.06 * wave(t, 1, .2), spine=.02 * wave(t))


@clip('spear_throw', 18, False)
def _(t):
    struck = pose(READY, drop=.14, spine=.50, head=-.5, twist=-.65, head_yaw=.3, out_r=.4, fwd_r=1.0, bend_r=.0, wrist_r=-.3,
                  out_l=.6, fwd_l=-.55, bend_l=.6, foot_l=(-.08, 0), foot_r=(.20, 0), ears=.65, tail_side=.4)
    return swing(t, SPEAR_COCK, struck)


HAMMER_UP = pose(STANCE, drop=.12, spine=-.38, head=-.4, twist=.25, out_r=.5, fwd_r=2.75, bend_r=1.25, grip_r=1, out_l=.35, fwd_l=2.55, bend_l=1.4,
                 grip_l=.8, foot_l=(.10, 0), foot_r=(-.12, 0), ears=.4, tail=.3)


@clip('hammer_charge', 30, True)
def _(t):
    # Hammer heaved back over the shoulder in both hands, breathing with the gathering light.
    return add(dict(HAMMER_UP), spine=-.04 * wave(t), fwd_r=.05 * wave(t), fwd_l=.05 * wave(t), drop=.008 * wave(t))


@clip('hammer_smash', 15, False)
def _(t):
    slam = pose(STANCE, drop=.24, spine=.75, head=-.25, twist=-.1, out_r=.25, fwd_r=.75, bend_r=.05, grip_r=1, out_l=.2, fwd_l=.8, bend_l=.1,
                grip_l=.8, foot_l=(-.14, 0), foot_r=(.20, 0), ears=-.3, ear_flop=.7, tail=-.5)
    return keys(t, [(0, HAMMER_UP), (.28, slam), (.6, add(dict(slam), drop=.03)), (1, pose(READY))])


@clip('mine', 10, False)
def _(t):
    # Mining and tool swings: a short overhand chop at the block in front.
    up = pose(READY, out_r=.4, fwd_r=2.35, bend_r=1.1, wrist_r=.5, spine=.05)
    hit = pose(READY, out_r=.3, fwd_r=.85, bend_r=.2, wrist_r=-.3, spine=.22, head=.05)
    return keys(t, [(0, READY), (.3, up), (.55, hit), (1, READY)])


@clip('throw', 12, False)
def _(t):
    # Snowballs, pearls, potions: cock behind the ear and snap forward.
    cock = pose(STANCE, twist=.35, out_r=.55, fwd_r=2.6, bend_r=1.4, grip_r=.6, fwd_l=.9, bend_l=.3)
    snap = pose(STANCE, twist=-.35, spine=.25, out_r=.35, fwd_r=1.1, bend_r=.1, fwd_l=-.3)
    return keys(t, [(0, STANCE), (.35, cock), (.55, snap), (1, STANCE)])


@clip('block', 30, True)
def _(t):
    # Shield up: forearm across the front, crouched behind it.
    p = pose(STANCE, drop=.12, spine=.28, head=.05, twist=.25, out_r=.55, fwd_r=1.25, bend_r=1.45, wrist_r=.2, grip_r=1,
             fwd_l=.4, bend_l=.8, foot_l=(-.06, 0), foot_r=(.10, 0), ears=.6)
    return add(p, spine=.015 * wave(t), drop=.004 * wave(t))


# ---- getting hurt ---------------------------------------------------------------------------------------------
RECOIL = pose(STANCE, drop=.05, pitch=-.25, spine=-.45, head=-.35, out_l=1.1, out_r=1.0, fwd_l=.95, fwd_r=.8, bend_l=.2, bend_r=.3,
              foot_l=(.12, .05), foot_r=(-.04, 0), ears=-.8, ear_flop=-.3, tail=.6)


@clip('hurt', 9, False)
def _(t): return keys(t, [(0, STANCE), (.25, RECOIL), (1, mix(RECOIL, STANCE, .45))], ease=ease_out)


@clip('hurt_back', 9, False)
def _(t):
    jolt = pose(STANCE, drop=.07, pitch=.30, spine=.50, head=.25, out_l=.9, out_r=.9, fwd_l=-1.0, fwd_r=-.9, bend_l=.2, bend_r=.2,
                foot_l=(-.05, 0), foot_r=(.08, .03), ears=1.0, tail=.5)
    return keys(t, [(0, STANCE), (.25, jolt), (1, mix(jolt, STANCE, .45))], ease=ease_out)


@clip('hurt_heavy', 24, False)
def _(t):
    # Knocked off the feet: flung back, tumbling tucked, landing in a crouch.
    flung = pose(pitch=-.85, spine=-.55, head=-.45, out_l=1.3, out_r=1.3, fwd_l=1.4, fwd_r=1.2, bend_l=.2, bend_r=.2,
                 thigh_l=.9, knee_l=.6, thigh_r=.5, knee_r=.9, ears=-.9, tail=.8)
    tuck = dict(BALL, pitch=-1.6)
    return keys(t, [(0, STANCE), (.15, flung), (.5, tuck), (.8, pose(CROUCH)), (1, pose(CROUCH))])


@clip('recover', 12, False)
def _(t):
    # Shake it off: head and ears shiver out of the recoil back into the stance.
    p = keys(t, [(0, mix(RECOIL, STANCE, .45)), (1, pose(STANCE))])
    return add(p, head_yaw=.30 * math.sin(t * 3 * TAU) * (1 - t), ear_flop=.4 * math.sin(t * 3 * TAU + 1) * (1 - t))


CURLED = pose(drop=.36, roll=1.45, spine=.65, head=.45, out_l=.25, out_r=.25, fwd_l=1.1, fwd_r=1.1, bend_l=1.8, bend_r=1.8,
              thigh_l=1.4, thigh_r=1.3, knee_l=2.0, knee_r=1.95, ears=.9, ear_flop=.5, tail=.9)


@clip('death', 27, False)
def _(t):
    # Buckle at the knees, fold forward and settle curled on the side, the light about to leave.
    kneel = pose(STANCE, drop=.36, spine=.65, head=.35, out_l=.4, out_r=.4, fwd_l=.4, fwd_r=.4, bend_l=.3, bend_r=.3,
                 foot_l=(-.05, 0), foot_r=(.05, 0), ears=.6, ear_flop=.6, tail=-.4)
    return keys(t, [(0, RECOIL), (.35, kneel), (1, CURLED)])


@clip('sleep', 90, True)
def _(t): return add(dict(CURLED), spine=.03 * wave(t), tail_side=.08 * wave(t, 1, .3), ear_flop=.05 * wave(t, 1, .5))


# ---- minecraft actions ----------------------------------------------------------------------------------------
@clip('eat', 16, True)
def _(t):
    p = pose(STANCE, spine=.22, head=.25, out_l=.15, out_r=.15, fwd_l=1.2, fwd_r=1.25, bend_l=1.95, bend_r=1.95, wrist_l=.5, wrist_r=.5,
             grip_r=.6, ears=-.1)
    chew = abs(wave(t, 2))
    return add(p, jaw=.22 * chew, head=.05 * chew, ear_flop=.15 * chew)


@clip('drink', 20, True)
def _(t):
    p = pose(STANCE, spine=-.12, head=-.45, out_r=.2, fwd_r=1.65, bend_r=1.9, wrist_r=.7, grip_r=.8, fwd_l=.3, bend_l=.6, ears=.3, tail=.2)
    return add(p, jaw=.08 * abs(wave(t, 2)), head=-.04 * wave(t, 2))


@clip('horn', 40, True)
def _(t):
    # Goat horn: lifted to the mouth, head thrown back, chest out.
    p = pose(STANCE, spine=-.25, head=-.55, out_r=.3, fwd_r=1.7, bend_r=1.75, wrist_r=.5, grip_r=.8, out_l=.9, fwd_l=.3, bend_l=.4,
             ears=-.3, ear_flop=-.2, tail=.5)
    return add(p, spine=-.04 * wave(t), ears=-.1 * wave(t, 2))


@clip('brush', 20, True)
def _(t):
    # Brushing a suspicious block: crouched over it, wrist sweeping side to side.
    p = pose(CROUCH, out_r=.45, fwd_r=1.0, bend_r=.6, grip_r=.8, fwd_l=.5, bend_l=.7)
    return add(p, twist=.10 * wave(t, 2), wrist_r=.35 * wave(t, 2), out_r=.15 * wave(t, 2), head_yaw=.08 * wave(t, 2))


@clip('boat_row', 30, True)
def _(t):
    w, c = wave(t), wave(t, 1, .25)
    p = pose(drop=.12, spine=.15, head=-.15, out_l=.35, out_r=.35, fwd_l=1.0, fwd_r=1.0, bend_l=.8, bend_r=.8, grip_l=1, grip_r=1,
             thigh_l=1.35, thigh_r=1.35, knee_l=1.4, knee_r=1.4, ears=.15, tail=-.2)
    return add(p, fwd_l=.45 * w, fwd_r=.45 * w, bend_l=.35 * c, bend_r=.35 * c, spine=.12 * w)


# ---- more abilities and actions ------------------------------------------------------------------------------
@clip('elytra', 30, True)
def _(t):
    # Body frame (the runtime pitches along the view): arms swept back like wings, legs together, tail trailing.
    w = wave(t)
    p = pose(spine=-.1, head=-1.0, out_l=.75, out_r=.75, fwd_l=-.55, fwd_r=-.55, bend_l=.15, bend_r=.15, wrist_l=-.3, wrist_r=-.3,
             thigh_l=-.1, thigh_r=-.1, knee_l=.2, knee_r=.2, ears=1.0, tail=-.1, tail_base=-1.0)
    return add(p, out_l=.06 * w, out_r=.06 * w, roll=.04 * wave(t, 1, .25), tail_side=.12 * wave(t, 1, .3))


@clip('burrow', 18, True)
def _(t):
    # Swimming through spirit sand: a tight streamlined dive, arms ahead, a dolphin wave down the body.
    w = wave(t)
    p = pose(spine=.1, head=-1.0, out_l=.1, out_r=.1, fwd_l=2.9, fwd_r=2.9, bend_l=.25, bend_r=.25, thigh_l=-.1, thigh_r=-.1, knee_l=.3, knee_r=.3,
             ears=1.0, tail=-.1, tail_base=-1.1)
    return add(p, spine=.15 * w, thigh_l=.3 * wave(t, 1, -.2), thigh_r=.3 * wave(t, 1, -.2), tail=.3 * wave(t, 1, -.4))


@clip('launch_charge', 30, True)
def _(t):
    # Coiled low before a Launch, energy gathering: trembling, arms pulled in, ears flat.
    p = pose(CROUCH, drop=.26, spine=.6, head=-.75, fwd_l=.2, fwd_r=.2, out_l=.55, out_r=.55, bend_l=1.3, bend_r=1.3, ears=.9, tail=-.4)
    return add(p, drop=.006 * wave(t, 6), spine=.015 * wave(t, 6), tail_side=.06 * wave(t, 3))


@clip('cast_charge', 30, True)
def _(t):
    # Charge Flame: hands cupped together before the chest around the gathering light.
    p = pose(STANCE, drop=.10, spine=.22, head=.1, out_l=.05, out_r=.05, fwd_l=1.05, fwd_r=1.05, bend_l=1.35, bend_r=1.35,
             wrist_l=.4, wrist_r=.4, foot_l=(.08, 0), foot_r=(-.08, 0), ears=.4)
    return add(p, bend_l=.06 * wave(t, 2), bend_r=.06 * wave(t, 2), spine=.02 * wave(t), ear_flop=.08 * wave(t, 2))


@clip('cast_release', 12, False)
def _(t):
    # Charge Flame burst / Light Burst: both palms thrust out, then settle.
    cupped = pose(STANCE, drop=.10, spine=.22, out_l=.05, out_r=.05, fwd_l=1.05, fwd_r=1.05, bend_l=1.35, bend_r=1.35, ears=.4)
    thrust = pose(STANCE, drop=.12, spine=.35, head=-.25, out_l=.3, out_r=.3, fwd_l=1.55, fwd_r=1.55, bend_l=.05, bend_r=.05,
                  wrist_l=-.7, wrist_r=-.7, foot_l=(-.06, 0), foot_r=(.16, 0), ears=.8, tail=.3)
    return keys(t, [(0, cupped), (.2, thrust), (.55, thrust), (1, STANCE)], ease=ease_out)


@clip('flash', 30, True)
def _(t):
    # Flash: light held high over the head in both hands, body stretched up.
    p = pose(STANCE, drop=.03, spine=-.15, head=-.45, out_l=.35, out_r=.35, fwd_l=2.8, fwd_r=2.8, bend_l=.4, bend_r=.4,
             wrist_l=-.4, wrist_r=-.4, ears=-.3, tail=.4)
    return add(p, fwd_l=.04 * wave(t), fwd_r=.04 * wave(t), spine=-.02 * wave(t))


@clip('place', 8, False)
def _(t):
    # Placing a block or using an item on something: a short forward reach.
    reach = pose(READY, out_r=.3, fwd_r=1.35, bend_r=.25, wrist_r=-.2, spine=.12)
    return keys(t, [(0, READY), (.35, reach), (1, READY)])


@clip('hurt_side', 9, False)
def _(t):
    # Hit from the anatomical left (mirrored for the right): knocked sideways, arms flung up.
    jolt = pose(STANCE, drop=.06, lean=-.45, twist=-.3, head=-.2, head_yaw=-.3, out_l=1.2, out_r=1.0, fwd_l=.6, fwd_r=.9,
                foot_l=(0, 0), foot_r=(0, .05), ears=.5, ear_flop=.4, tail_side=.6)
    return keys(t, [(0, STANCE), (.25, jolt), (1, mix(jolt, STANCE, .45))], ease=ease_out)
