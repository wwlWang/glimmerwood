"""Before/after movement curves for docs/FEEL_AUDIT.md. Models vanilla 1.21.1 player travel (friction 0.6*0.91 on
ground, 0.91 in air, gravity 0.08 then 0.98 drag) and the R73 FeelTuning rules (constants parsed from the Java file).
Usage: python3 scripts/feel_curves.py OUT_DIR   -> feel-*.png and feel-metrics.json"""
import json, math, pathlib, re, sys
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
root = pathlib.Path(__file__).resolve().parent.parent
src = (root / 'src/main/java/cn/wwl/orimod/movement/FeelTuning.java').read_text()
C = {k: float(v) for k, v in re.findall(r'\b([A-Z_]+) = (-?[0-9.]+)', src)}
C['DASH_PROFILE'] = [float(x) for x in re.search(r'DASH_PROFILE = \{([^}]*)\}', src).group(1).split(',')]
out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '.'); out.mkdir(parents=True, exist_ok=True)
G = .08

def vanilla_ground(ticks, speed_attr, inp):            # 1D, input 1 or 0
    v, xs = 0.0, []
    for t in range(ticks):
        v += speed_attr * inp(t); xs.append(v); v *= .546   # moveRelative, move (displacement = v), friction
    return xs                                             # displacement per tick

def approach(c, t, acc, dec):
    braking = abs(t) < abs(c) or t * c < 0
    s = dec if braking else acc
    return t if abs(t - c) <= s else c + math.copysign(s, t - c)

def new_ground(ticks, inp, start=0.0, ground=True):
    v, xs = start, []
    for t in range(ticks):
        w = inp(t); top = C['TOP_SPEED']
        if abs(v) > top + 1e-9 and (w == 0 or w * v > 0):
            v = math.copysign(max(top, abs(v) - (C['OVERSPEED_DECAY_GROUND'] if ground else C['OVERSPEED_DECAY_AIR']) - (abs(v) - top) * (C['OVERSPEED_DAMP_GROUND'] if ground else C['OVERSPEED_DAMP_AIR'])), v)
        elif ground: v = approach(v, w * top, C['GROUND_ACCEL'], C['GROUND_DECEL'])
        elif w == 0: v = math.copysign(max(0, abs(v) - C['AIR_DRAG']), v) if v else 0
        else: v = approach(v, w * top, C['AIR_ACCEL'], C['AIR_ACCEL'])
        xs.append(v)
    return xs

def vertical_new(vy, held, released):
    if vy < C['MAX_FALL'] and 'FALL_RETURN_RATE' in C:
        nxt = C['MAX_FALL'] + (vy - C['MAX_FALL']) * (1 - C['FALL_RETURN_RATE'])
        if C['MAX_FALL'] - nxt <= C['FALL_RETURN_EPSILON']: nxt = C['MAX_FALL']
        return nxt / .98 + G
    if released and vy > C['JUMP_CUT_MIN']: return vy * C['JUMP_CUT']
    if abs(vy) < C['APEX_SPEED'] and held: s = C['APEX_GRAVITY_SCALE']
    elif vy > 0: s = C['RISE_GRAVITY_SCALE'] if held else 1
    else:
        k = max(0, min(1, -vy / C.get('FALL_RAMP_SPEED', 1e-9)))
        s = C.get('FALL_GRAVITY_START', C['FALL_GRAVITY_SCALE']) + (C['FALL_GRAVITY_SCALE'] - C.get('FALL_GRAVITY_START', C['FALL_GRAVITY_SCALE'])) * k
    adj = vy + G * (1 - s)
    return adj if vy < C['MAX_FALL'] - .05 else max(adj, C['MAX_FALL'] / .98 + G)

def jump(new, hold_ticks, ticks=40):
    y, vy, ys = 0.0, .42, [0.0]
    for t in range(ticks):
        held = t < hold_ticks
        if new: vy = vertical_new(vy, held, t == hold_ticks)
        y += vy; ys.append(y); vy = (vy - G) * .98
        if y < -1.5: break
    return ys

def first(xs, pred):
    return next((i + 1 for i, x in enumerate(xs) if pred(x)), None)

m = {}
walk, sprint = vanilla_ground(30, .1, lambda t: 1), vanilla_ground(30, .13, lambda t: 1)
newg = new_ground(30, lambda t: 1)
m['vanilla_walk_top_bps'] = round(walk[-1] * 20, 2); m['vanilla_sprint_top_bps'] = round(sprint[-1] * 20, 2)
m['vanilla_sprint_ticks_to_90pct'] = first(sprint, lambda x: x >= .9 * sprint[-1])
m['new_top_bps'] = round(C['TOP_SPEED'] * 20, 2); m['new_ticks_to_90pct'] = first(newg, lambda x: x >= .9 * C['TOP_SPEED'])
stop_v = vanilla_ground(30, .13, lambda t: 1 if t < 15 else 0)[15:]; stop_n = new_ground(30, lambda t: 1 if t < 15 else 0)[15:]
m['vanilla_sprint_stop_ticks_to_10pct'] = first(stop_v, lambda x: x <= .1 * sprint[-1]); m['new_stop_ticks'] = first(stop_n, lambda x: x <= 1e-6)
m['vanilla_stop_slide_blocks'] = round(sum(stop_v), 3); m['new_stop_slide_blocks'] = round(sum(stop_n), 3)
for name, new in (('vanilla', False), ('new', True)):
    full, tap = jump(new, 99), jump(new, 2)
    m[f'{name}_jump_height_held'] = round(max(full), 3); m[f'{name}_jump_height_tap'] = round(max(tap), 3)
    m[f'{name}_jump_ticks_to_apex'] = full.index(max(full))
    m[f'{name}_jump_airtime_ticks'] = next(i for i, y in enumerate(full) if i > 0 and y <= 0)
    near = [i for i in range(1, len(full) - 1) if max(full) - full[i] < .1]
    m[f'{name}_apex_hang_ticks(<0.1 below top)'] = len(near)
# Dash: vanilla build = 5 ticks at 1.12 then air drag 0.91; new = profile then exit speed carry.
# Ground dash (input held): vanilla friction 0.546 + sprint input .13 after the 5 flat ticks.
dv = [1.12] * 5; v = 1.12 * .546
for t in range(15): v += .13; dv.append(v); v *= .546
dn = [1.12 * s for s in C['DASH_PROFILE']] + new_ground(15, lambda t: 1, start=C['DASH_EXIT_SPEED'], ground=True)
m['vanilla_dash_speed_drop_at_end_bpt'] = round(dv[4] - dv[5], 3); m['new_dash_speed_drop_at_end_bpt'] = round(dn[4] - dn[5], 3)
air_v = []; v = 0
for t in range(20): v += .02; air_v.append(v); v *= .91
m['vanilla_air_control_ticks_to_0.2'] = first(air_v, lambda x: x >= .2)
air_n = new_ground(20, lambda t: 1, ground=False); m['new_air_control_ticks_to_0.2'] = first(air_n, lambda x: x >= .2)
(out / 'feel-metrics.json').write_text(json.dumps(m, indent=2))

fig, ax = plt.subplots(2, 2, figsize=(12, 8))
ax[0][0].plot([x * 20 for x in walk], label='vanilla walk'); ax[0][0].plot([x * 20 for x in sprint], label='vanilla sprint')
ax[0][0].plot([x * 20 for x in new_ground(30, lambda t: 1 if t < 15 else 0)], label='R73 spirit (release at 15)', lw=2.5)
ax[0][0].plot([x * 20 for x in vanilla_ground(30, .13, lambda t: 1 if t < 15 else 0)], '--', label='vanilla sprint (release at 15)')
ax[0][0].set_title('Ground speed (m/s) per tick'); ax[0][0].legend(); ax[0][0].set_xlabel('tick')
for name, new, st in (('vanilla', False, '--'), ('R73', True, '-')):
    ax[0][1].plot(jump(new, 99), st, label=f'{name} held', lw=2.5 if new else 1.5); ax[0][1].plot(jump(new, 2), st, label=f'{name} tap (2 ticks)')
ax[0][1].axhline(0, color='k', lw=.5); ax[0][1].set_title('Jump arc (blocks)'); ax[0][1].legend(); ax[0][1].set_xlabel('tick')
ax[1][0].plot([x * 20 for x in dv], '--', label='before: 5 ticks flat, then ground friction'); ax[1][0].plot([x * 20 for x in dn], label='R73 ease-out + carry', lw=2.5)
ax[1][0].set_title('Dash horizontal speed (m/s)'); ax[1][0].legend(); ax[1][0].set_xlabel('tick')
ax[1][1].plot([x * 20 for x in air_v], '--', label='vanilla air control'); ax[1][1].plot([x * 20 for x in air_n], label='R73 air control', lw=2.5)
ax[1][1].set_title('Air control from rest (m/s)'); ax[1][1].legend(); ax[1][1].set_xlabel('tick')
fig.tight_layout(); fig.savefig(out / 'feel-curves.png', dpi=90)
print(json.dumps(m, indent=1))
