"""R100 rework client acceptance: tapered trunk + dome crown, spirit vines, night glow WITHOUT night vision,
and /ori tree nearest against naturally generated trees in a fresh world. Visuals are judged by a reviewer.
Usage: python3 scripts/verify-r100-rework.py <build_id> <world_name> <seed>"""
import argparse, json, math
from pathlib import Path
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('world'); p.add_argument('seed', type=int)
args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 3000, 120, 3000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)['block']

def cam(x, y, z, h=1.0):
    c.action('review_camera', enabled=True, x=x, y=y, z=z, targetHeight=h)

def find_heart(cx, cy, cz, r=6, dy=3):
    for oy in range(-dy, dy + 1):
        for ox in range(-r, r + 1):
            for oz in range(-r, r + 1):
                if block(cx + ox, cy + oy, cz + oz) == 'orimod:spirit_tree_heart':
                    return cx + ox, cy + oy, cz + oz
    return None

def nearest(x, y, z, radius):
    # sendFailure does not throw: a miss is success=true with result 0.
    return c.command(f'execute positioned {x} {y} {z} run ori tree nearest {radius}')['result'] > 0

def distance(x, y, z, hi=1024):
    """Smallest integer radius that finds a heart from (x,y,z); None if nothing within hi."""
    if not nearest(x, y, z, hi): return None
    lo = 1
    while lo < hi:
        mid = (lo + hi) // 2
        if nearest(x, y, z, mid): hi = mid
        else: lo = mid + 1
    return lo

def trilaterate(x, y, z, b=48):
    d0, dx, dz, dy = distance(x, y, z), distance(x + b, y, z), distance(x, y, z + b), distance(x, y + b, z)
    if None in (d0, dx, dz, dy): return None
    # |p-q|^2 = d^2 differences give linear equations in each axis.
    ex = (d0 * d0 - dx * dx + b * b) / (2 * b)
    ez = (d0 * d0 - dz * dz + b * b) / (2 * b)
    ey = (d0 * d0 - dy * dy + b * b) / (2 * b)
    return round(x + ex), round(y + ey), round(z + ez), (d0, dx, dz, dy)

try:
    state = c.request()
    if 'player' not in state:
        saved = (Path(__file__).resolve().parents[1] / 'run-devtools/saves' / args.world / 'level.dat').exists()
        c.action('open_world', name=args.world) if saved else c.action('create_world', name=args.world, seed=args.seed)
    c.ready(); c.action('focus_game'); c.action('close_screen')
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty peaceful', 'time set 6000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
                    'effect give @s minecraft:resistance 99999 4 true', 'effect clear @s minecraft:night_vision']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)

    # 1. Placed tree on a flat pad: day/night, full silhouette and close-ups of vines and the heart.
    c.command(f'tp @s {X} {Y + 30} {Z}'); c.ticks(80)
    for y in range(Y, Y + 40, 4):
        c.command(f'fill {X - 30} {y} {Z - 30} {X + 30} {min(y + 3, Y + 39)} {Z + 30} minecraft:air')
    c.command(f'fill {X - 30} {Y - 2} {Z - 30} {X + 30} {Y - 1} {Z + 30} minecraft:grass_block')
    out = c.command(f'place feature orimod:spirit_tree {X} {Y} {Z}'); c.ticks(10)
    heart = find_heart(X, Y, Z, r=3)
    c.records['placed'] = {'command': out, 'heart': heart}
    check('placed_tree', heart is not None, c.records['placed'])
    check('nearest_finds_placed', heart is not None and distance(X, Y + 1, Z - 20, 64) is not None)
    check('nearest_radius_too_small_fails', not nearest(X, Y + 1, Z - 40, 10))
    c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
    # review_camera offsets are relative to the player (|offset| < 32): stand in the entrance alcove so the
    # camera orbits the tree itself.
    hx, hy, hz = heart if heart else (X, Y, Z - 2)
    c.reset(hx + .5, hy, hz - 2.5, 0, 0); c.ticks(30)
    shots = [('side', 0, 8, -28, 10), ('quarter', 20, 10, -20, 10), ('back', -8, 6, 27, 10), ('under', 3, 1, -7, 12),
             ('vines', 9, 4, -7, 9), ('heart', 1, 1.5, -4, .5)]
    for t, label in [(6000, 'day'), (18000, 'night')]:
        c.command(f'time set {t}'); c.ticks(20)
        for name, cx, cy, cz, h in shots:
            cam(cx, cy, cz, h); c.ticks(10); c.screenshot(f'placed-{label}-{name}')
    c.action('review_camera', enabled=False)

    # 2. Natural generation in this fresh world: grid search with the new command, trilaterate, then shoot it.
    c.action('camera', mode='FIRST_PERSON')
    natural = None
    for i, (gx, gz) in enumerate([(0, 0), (320, 0), (0, 320), (-320, 0), (0, -320), (320, 320), (-320, -320),
                                  (320, -320), (-320, 320), (640, 0), (0, 640), (-640, 0), (0, -640)]):
        c.command(f'tp @s {gx} 200 {gz}'); c.ticks(200)
        if nearest(gx, 100, gz, 1024):
            natural = trilaterate(gx, 100, gz)
            c.records.setdefault('natural_search', []).append({'at': [gx, gz], 'estimate': natural})
            if natural: break
        else:
            c.records.setdefault('natural_search', []).append({'at': [gx, gz], 'estimate': None})
    if natural:
        ex, ey, ez, _ = natural
        c.command(f'tp @s {ex} {ey + 30} {ez}'); c.ticks(60)
        nh = find_heart(ex, ey, ez, r=3, dy=3)
        c.records['natural_heart'] = nh
        check('nearest_locates_natural', nh is not None, {'estimate': natural, 'heart': nh})
        if nh:
            hx, hy, hz = nh
            c.action('camera', mode='THIRD_PERSON_BACK')
            c.reset(hx + .5, hy, hz - 2.5, 0, 0); c.ticks(40)
            for t, label in [(6000, 'day'), (18000, 'night')]:
                c.command(f'time set {t}'); c.ticks(20)
                cam(0, 8, -28, 10); c.ticks(10); c.screenshot(f'natural-{label}-side')
                cam(20, 14, 20, 10); c.ticks(10); c.screenshot(f'natural-{label}-quarter')
            c.action('review_camera', enabled=False)
            c.action('camera', mode='FIRST_PERSON')
            c.command(f'tp @s {hx} {hy + 70} {hz - 50} 0 50'); c.ticks(60); c.screenshot('natural-night-aerial')
    else:
        check('nearest_locates_natural', False, c.records.get('natural_search'))
    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('review_camera', enabled=False)
    except Exception: pass
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:3000])
