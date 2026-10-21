"""R103 cracked ruin walls/floors and spirit light vessels, client acceptance through real inputs:
Charge Flame opens a 3x3 wall (collapse wave screenshots), Stomp opens a floor, the companion's Spirit Flame
shatters a vessel in sight but not one behind a wall, and a natural forest spring's secret room opens.
Visuals are judged from the screenshots by a reviewer.
Launch the client with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r103.py <build_id> [--only wall,floor,vessel,ruin]"""
import argparse, json
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='wall,floor,vessel,ruin')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 7000, 120, 7000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)['block']

def count(cells, block_id):
    return sum(block(*cell) == block_id for cell in cells)

def light():
    return c.action('progress_state')['server']['progress']['light']

def pad():
    c.command('gamemode creative')
    for y in range(Y, Y + 16, 4):
        c.command(f'fill {X - 20} {y} {Z - 20} {X + 20} {y + 3} {Z + 20} minecraft:air')
    c.command(f'fill {X - 20} {Y - 4} {Z - 20} {X + 20} {Y - 1} {Z + 20} minecraft:stone_bricks')
    c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(10)

def survival():
    c.command('gamemode survival'); c.command('ori energy fill'); c.ticks(4)

def stomp():
    c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True},
        {'at': 1, 'key': 'key.orimod.dash', 'down': True}, {'at': 3, 'key': 'key.orimod.dash', 'down': False},
        {'at': 4, 'key': 'key.sneak', 'down': False}])

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty peaceful', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', 'time set 6000', 'effect give @s minecraft:resistance 99999 4 true',
                    'ori awaken', 'ori form on', 'ori unlock all', 'ori hints reset @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command(f'tp @s {X} {Y + 20} {Z}'); c.ticks(60)

    if 'wall' in only:
        pad()
        wall = [(X + dx, Y + dy, Z + 3) for dx in (-1, 0, 1) for dy in (0, 1, 2)]
        c.command(f'fill {X - 1} {Y} {Z + 3} {X + 1} {Y + 2} {Z + 3} orimod:cracked_ruin_wall')
        c.command(f'setblock {X + 3} {Y} {Z + 3} orimod:cracked_ruin_wall')  # one-block gap: must survive
        c.action('settings', hideGui=True); c.action('look', yaw=0, pitch=5); c.ticks(10)
        c.screenshot('wall-day')
        c.command('time set 18000'); c.ticks(10); c.screenshot('wall-night'); c.command('time set 6000')
        c.action('settings', hideGui=False)
        survival(); c.reset(X + .5, Y, Z + 1.5, 0, 5); c.ticks(5)
        c.key('orimod.charge_flame', 16); c.ticks(17)
        c.screenshot('wall-collapse-mid'); c.ticks(12)
        c.screenshot('wall-collapse-after')
        left = count(wall, 'orimod:cracked_ruin_wall')
        check('charge_flame_opens_wall', left == 0, {'left': left})
        check('separate_wall_survives', block(X + 3, Y, Z + 3) == 'orimod:cracked_ruin_wall')
        c.command('gamemode creative')

    if 'floor' in only:
        pad()
        floor = [(X + dx, Y - 1, Z + 4 + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
        c.command(f'fill {X - 1} {Y - 1} {Z + 3} {X + 1} {Y - 1} {Z + 5} orimod:cracked_ruin_floor')
        c.command(f'fill {X - 1} {Y - 4} {Z + 3} {X + 1} {Y - 2} {Z + 5} minecraft:air')
        # reset() waits 6 ticks, long enough to land from a short drop; teleport high and stomp at once.
        survival(); c.command(f'tp @s {X + .5} {Y + 16} {Z + 4.5} 0 60'); c.ticks(3)
        stomp(); c.ticks(40)
        c.screenshot('floor-after-stomp')
        left = count(floor, 'orimod:cracked_ruin_floor')
        check('stomp_opens_floor', left == 0, {'left': left})
        c.command('gamemode creative')

    if 'vessel' in only:
        pad()
        c.command(f'setblock {X} {Y} {Z + 4} orimod:spirit_light_vessel')
        # A second vessel sealed behind stone: the companion's flame must not shoot through walls.
        c.command(f'fill {X + 7} {Y} {Z + 3} {X + 9} {Y + 2} {Z + 5} minecraft:stone')
        c.command(f'setblock {X + 8} {Y} {Z + 4} orimod:spirit_light_vessel')
        c.action('settings', hideGui=True); c.action('look', yaw=0, pitch=10); c.ticks(10)
        c.screenshot('vessel-day'); c.action('settings', hideGui=False)
        survival(); before = light()
        broke = False
        for _ in range(20):
            c.ticks(10)
            if block(X, Y, Z + 4) != 'orimod:spirit_light_vessel':
                broke = True; c.screenshot('vessel-shatter'); break
        # Orbs scatter around the vessel, outside the 2.5-block magnet: walk onto the spot to collect them.
        c.command(f'tp @s {X + .5} {Y} {Z + 4.5}'); c.ticks(80)
        gained = light() - before
        check('flame_shatters_vessel_in_sight', broke, {'gained_light': gained})
        check('vessel_light_collected', 25 <= gained <= 40, {'gained_light': gained})
        check('vessel_behind_wall_untouched', block(X + 8, Y, Z + 4) == 'orimod:spirit_light_vessel')
        c.command('gamemode creative')

    if 'ruin' in only:
        # /place structure registers no starts, so find a natural forest spring. locate's result is the distance to
        # the nearest start, which can be a different ruin from far-apart points: refine with a short baseline.
        def locate_dist(x, z):
            return c.command(f'execute positioned {x} 64 {z} run locate structure orimod:forest_spring')['result']
        px, pz, B, trail = 0, 0, 12, []
        for _ in range(6):
            d0 = locate_dist(px, pz)
            trail.append([px, pz, d0])
            if d0 <= 3: break
            dx, dz = locate_dist(px + B, pz), locate_dist(px, pz + B)
            px += round((d0 * d0 - dx * dx + B * B) / (2 * B)); pz += round((d0 * d0 - dz * dz + B * B) / (2 * B))
        c.records['locate_trail'] = trail
        secrets = []
        for ox, oz in [(0, 0), (4, 4), (-4, -4), (8, 0), (0, 8), (-8, 0), (0, -8), (8, 8)]:
            c.command('gamemode creative'); c.command(f'spreadplayers {px + ox} {pz + oz} 0 1 false @s'); c.ticks(40)
            secrets = c.action('ruin_state').get('secrets', [])
            if secrets: break
        c.records['secrets'] = secrets
        check('ruin_secret_reported', len(secrets) == 1 and secrets[0].get('blocks') == 'wall', secrets)
        if secrets:
            sx, sy, sz = secrets[0]['entrance']
            # `facing` points out of the room; stand two blocks outside and look back at the seal.
            fx, fz = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}.get(secrets[0].get('facing'), (0, -1))
            yaw = {(0, -1): 0, (0, 1): 180, (-1, 0): -90, (1, 0): 90}[(fx, fz)]
            c.command('gamemode creative'); c.command(f'tp @s {sx + 2 * fx + .5} {sy} {sz + 2 * fz + .5} {yaw} 5'); c.ticks(20)
            c.action('settings', hideGui=True); c.screenshot('ruin-secret-wall'); c.action('settings', hideGui=False)
            survival(); c.key('orimod.charge_flame', 16); c.ticks(40)
            c.screenshot('ruin-secret-open')
            opened = block(sx, sy, sz) != 'orimod:cracked_ruin_wall'
            check('ruin_secret_opens', opened, {'entrance': [sx, sy, sz], 'after': block(sx, sy, sz)})

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('release_keys'); c.command('gamemode creative'); c.action('settings', hideGui=False)
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:4000])
