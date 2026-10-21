"""R100 Spirit Tree client acceptance: placed trees on flat and sloped ground (day/night, several angles), heart
interaction (free ability, once per player per tree), and an aerial night survey of natural generation.
Visual quality is judged from the screenshots by a reviewer, never auto-passed.
Usage: python3 scripts/verify-r100.py <build_id> [--only placed,heart,survey]"""
import argparse, json
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--only', default='placed,heart,survey')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 3000, 120, 3000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def progress():
    return c.action('progress_state')['server']

def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)['block']

def cam(x, y, z, h=1.0):
    c.action('review_camera', enabled=True, x=x, y=y, z=z, targetHeight=h)

def find_heart(cx, cy, cz, r=6):
    """Locate the heart after /place: scan a column box with inspect_block (slow but only once per tree)."""
    for dy in range(-3, 4):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if block(cx + dx, cy + dy, cz + dz) == 'orimod:spirit_tree_heart':
                    return cx + dx, cy + dy, cz + dz
    return None

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty peaceful', 'time set 6000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule sendCommandFeedback false',
                    'effect give @s minecraft:resistance 99999 4 true', 'effect give @s minecraft:night_vision 99999 0 true']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command(f'tp @s {X} {Y + 30} {Z}'); c.ticks(80)

    if 'placed' in only or 'heart' in only:
        # Flat grass pad and a 3-step slope pad, cleared well above.
        for y in range(Y, Y + 40, 4):
            c.command(f'fill {X - 30} {y} {Z - 30} {X + 30} {min(y + 3, Y + 39)} {Z + 30} minecraft:air')
        c.command(f'fill {X - 30} {Y - 2} {Z - 30} {X + 30} {Y - 1} {Z + 30} minecraft:grass_block')
        c.command(f'fill {X + 10} {Y} {Z - 30} {X + 30} {Y} {Z + 30} minecraft:grass_block')
        c.command(f'fill {X + 18} {Y + 1} {Z - 30} {X + 30} {Y + 1} {Z + 30} minecraft:grass_block')
        c.command(f'fill {X + 24} {Y + 2} {Z - 30} {X + 30} {Y + 2} {Z + 30} minecraft:grass_block')
        placed = {}
        for name, (px, py, pz) in {'flat': (X - 14, Y, Z), 'slope': (X + 20, Y + 1, Z)}.items():
            out = c.command(f'place feature orimod:spirit_tree {px} {py} {pz}')
            c.ticks(10)
            placed[name] = {'command': out, 'heart': find_heart(px, py, pz)}
        c.records['placed'] = placed
        check('flat_tree_placed', placed['flat']['heart'] is not None, placed['flat'])
        check('slope_tree_placed', placed['slope']['heart'] is not None, placed['slope'])
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.reset(X - 14.5, Y, Z - 22.5, 0, -10); c.ticks(20)
        for i, (cx, cy, cz, h) in enumerate([(0, 6, -6, 8), (10, 4, 6, 8), (-6, 2, 10, 4)]):
            cam(cx, cy, cz, h); c.ticks(8); c.screenshot(f'flat-day-{i}')
        c.command('time set 18000'); c.ticks(20)
        for i, (cx, cy, cz, h) in enumerate([(0, 6, -6, 8), (10, 4, 6, 8)]):
            cam(cx, cy, cz, h); c.ticks(8); c.screenshot(f'flat-night-{i}')
        c.reset(X + 20.5, Y + 2, Z - 22.5, 0, -10); c.ticks(20)
        cam(-8, 5, -4, 8); c.ticks(8); c.screenshot('slope-night-0')
        c.command('time set 6000'); c.ticks(10); c.screenshot('slope-day-0')
        c.action('review_camera', enabled=False)

    if 'heart' in only:
        heart = c.records.get('placed', {}).get('flat', {}).get('heart')
        if heart:
            hx, hy, hz = heart
            c.command('gamemode survival')
            c.command('ori awaken'); c.command('ori form on')
            before = progress()['abilities']
            # Stand in the 3x3 entrance north of the heart, face it, use.
            c.reset(hx + .5, hy, hz - 1.5, 0, 20); c.ticks(10)
            c.action('settings', hideGui=False)
            c.action('use_block', x=hx, y=hy, z=hz); c.ticks(10)
            c.screenshot('heart-blessing-0'); c.ticks(10); c.screenshot('heart-blessing-1')
            after = progress()['abilities']
            c.action('use_block', x=hx, y=hy, z=hz); c.ticks(10)
            again = progress()['abilities']
            c.records['heart'] = {'before': before, 'after': after, 'again': again, 'bloomed': block(hx, hy, hz)}
            check('heart_grants_one_ability', bin(after).count('1') == bin(before).count('1') + 1, c.records['heart'])
            check('heart_only_once', again == after, c.records['heart'])
            hints = [k for k in progress()['progress']['rewards'] if k.startswith('tree/') or k.startswith('hint/spirit_tree')]
            check('tree_reward_recorded', any(k.startswith('tree/') for k in hints), hints)
            c.command('gamemode creative')

    if 'survey' in only:
        # Natural generation: night aerial views above forests near the world origin of this seed.
        c.command('time set 18000'); c.action('settings', hideGui=True); c.action('review_camera', enabled=False)
        c.action('camera', mode='FIRST_PERSON')
        found = c.command('locate biome minecraft:forest')
        c.records['locate_forest'] = found
        for i, (dx, dz) in enumerate([(0, 0), (256, 0), (0, 256), (-256, 0), (0, -256)]):
            c.command(f'tp @s {dx} 200 {dz} 0 60'); c.ticks(120)
            c.screenshot(f'survey-{i}')
        c.action('settings', hideGui=False)

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('review_camera', enabled=False)
    except Exception: pass
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:3000])
