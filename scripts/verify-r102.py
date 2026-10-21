"""R102 Glimmer lines client acceptance: each trigger shows its line in the separate cold-white row (devtools
spirit_state.client.line {id, text, remaining}), once-only lines stay silent the second time, cooldowns hold, and a
line never hides a functional actionbar message. Visual overlap is judged from the screenshots by a reviewer.
Launch the client with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r102.py <build_id> [--only night,tide,tree,health,boss,ruin,human]"""
import argparse, json, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='night,tide,tree,health,boss,ruin,human')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 6000, 120, 6000
DAY = 100 + int(time.time()) % 100000  # /time set is absolute; never reuse a settled day
GAP = 210  # lines share the 200-tick global gap with hints
checks = c.records.setdefault('checks', {})
c.records['day'] = DAY

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def line():
    return c.spirit()['client'].get('line') or {}

def wait_line(expected, ticks=120):
    """Poll the client line row; returns the first line seen (any id) or {}."""
    waited = 0
    while waited < ticks:
        c.ticks(4); waited += 4
        l = line()
        if l.get('id') and l.get('remaining', 0) > 0:
            return l
    return {}

def quiet(ticks=GAP):
    c.ticks(ticks)

def setnight(hour):
    c.command(f'time set {DAY * 24000 + hour}')

def pad():
    c.command(f'tp @s {X} {Y + 30} {Z}'); c.ticks(60)
    for y in range(Y, Y + 28, 4):
        for ox in (-40, 0):
            c.command(f'fill {X + ox} {y} {Z - 40} {X + ox + 40} {y + 3} {Z + 40} minecraft:air')
    for ox in (-40, 0):
        c.command(f'fill {X + ox} {Y - 1} {Z - 40} {X + ox + 40} {Y - 1} {Z + 40} minecraft:grass_block')
    c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(20)

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty normal', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', 'effect clear @s minecraft:night_vision', f'time set {DAY * 24000 + 6000}',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori awaken', 'ori form on', 'ori hints reset @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    pad(); c.command('gamemode survival'); quiet()

    if 'night' in only:
        setnight(13100); l = wait_line('night_one', 160)
        check('night_one', l.get('id') == 'night_one', l); c.screenshot('night-one')
        quiet(); setnight(6000); c.ticks(20); setnight(13100)
        again = wait_line('night_one', 160)
        check('night_one_once', again.get('id') != 'night_one', again)
        quiet(); setnight(6000); c.ticks(20)

    if 'tide' in only:
        setnight(18000); quiet()
        c.command('gamerule doMobSpawning true'); c.command('ori tide start')
        l = wait_line('tide_begin', 160)
        c.screenshot('tide-begin-with-actionbar')
        check('tide_begin', l.get('id') == 'tide_begin', l)
        quiet(); c.command('ori tide stop')
        l = wait_line('tide_end', 160)
        c.screenshot('tide-end-with-summary')
        check('tide_end', l.get('id') == 'tide_end', l)
        c.command('gamerule doMobSpawning false')
        for t in ('gloom_spitter', 'gloom_hawk'): c.command(f'kill @e[type=orimod:{t}]')
        quiet(); setnight(6000); c.ticks(20)

    if 'tree' in only:
        c.command('gamemode creative')
        placed = c.command(f'place feature orimod:spirit_tree {X} {Y} {Z + 12}'); c.ticks(10)
        heart = None
        for dz in range(6, 14):
            for dx in range(-3, 4):
                if c.action('inspect_block', x=X + dx, y=Y, z=Z + dz)['block'] == 'orimod:spirit_tree_heart':
                    heart = (X + dx, Y, Z + dz)
        c.command('gamemode survival')
        check('tree_placed', heart is not None, {'placed': placed, 'heart': heart})
        if heart:
            hx, hy, hz = heart
            c.reset(hx + .5, hy, hz - 1.5, 0, 20); c.ticks(10)
            c.action('use_block', x=hx, y=hy, z=hz)
            l = wait_line('tree_found', 280)  # may wait out a hint's 200-tick gap
            c.screenshot('tree-found-with-blessing')
            check('tree_found', l.get('id') == 'tree_found', l)
            quiet()
            c.command(f'place feature orimod:spirit_tree {X - 30} {Y} {Z + 12}'); c.ticks(10)
            c.command(f'tp @s {X - 30} {Y} {Z + 7}'); c.ticks(10)
            for dx in range(-3, 4):
                for dz in range(-6, 0):
                    if c.action('inspect_block', x=X - 30 + dx, y=Y, z=Z + 12 + dz)['block'] == 'orimod:spirit_tree_heart':
                        c.action('use_block', x=X - 30 + dx, y=Y, z=Z + 12 + dz)
            again = wait_line('tree_found', 280)
            check('tree_found_once', again.get('id') != 'tree_found', again)
            pad(); quiet()

    if 'health' in only:
        c.command('effect clear @s minecraft:resistance'); c.command('effect give @s minecraft:instant_health 1 10 true'); c.ticks(10)
        c.command('damage @s 16 minecraft:generic'); l = wait_line('low_health', 80)
        check('low_health', l.get('id') == 'low_health', l)
        quiet(); c.command('effect give @s minecraft:instant_health 1 10 true'); c.ticks(10)
        c.command('damage @s 16 minecraft:generic'); again = wait_line('low_health', 80)
        check('low_health_cooldown', again.get('id') != 'low_health', again)
        c.command('effect give @s minecraft:instant_health 1 10 true'); c.command('effect give @s minecraft:resistance 99999 4 true')
        quiet()

    if 'boss' in only:
        c.command('ori spawn heart'); l = wait_line('boss_start', 200)
        c.screenshot('boss-start')
        check('boss_start', l.get('id') == 'boss_start', l)
        c.command('kill @e[type=orimod:gloom_heart]'); c.command('kill @e[type=orimod:gloom_shot]')
        quiet(); pad(); quiet()

    if 'ruin' in only:
        # /place structure does not register structure starts/references, so use a natural ruin. vanilla locate
        # returns the horizontal distance as its result; trilaterate the start from three points 256 apart.
        def locate_dist(x, z):
            return c.command(f'execute positioned {x} 64 {z} run locate structure orimod:forest_spring')['result']
        B = 256
        d0, dx, dz = locate_dist(0, 0), locate_dist(B, 0), locate_dist(0, B)
        c.records['ruin_distances'] = [d0, dx, dz]
        if min(d0, dx, dz) > 0:
            ex = round((d0 * d0 - dx * dx + B * B) / (2 * B)); ez = round((d0 * d0 - dz * dz + B * B) / (2 * B))
            c.records['ruin_estimate'] = [ex, ez]
            # Entry needs a position inside a ruin piece's box; the locate estimate can land on the edge (±3 blocks),
            # so try a few nearby surface points like a player walking in.
            l = {}
            for ox, oz in [(0, 0), (1, 1), (-2, 2), (2, -2), (0, 4), (4, 0)]:
                c.command(f'spreadplayers {ex + ox} {ez + oz} 0 1 false @s')
                l = wait_line('first_ruin_forest_spring', 60)
                if l.get('id'): break
            if not l.get('id'): l = wait_line('first_ruin_forest_spring', 300)
            c.screenshot('first-ruin')
            check('first_ruin_forest_spring', l.get('id') == 'first_ruin_forest_spring',
                  {'line': l, 'estimate': [ex, ez], 'player': c.request()['player'].get('position')})
        else:
            check('first_ruin_forest_spring', False, {'distances': [d0, dx, dz]})
        quiet(); pad(); quiet()

    if 'human' in only:
        c.command('ori form off'); c.ticks(10)
        c.command('effect clear @s minecraft:resistance'); c.command('effect give @s minecraft:instant_health 1 10 true'); c.ticks(1300)
        c.command('damage @s 16 minecraft:generic'); l = wait_line('low_health', 80)
        check('human_form_silent', not l.get('id'), l)
        c.command('effect give @s minecraft:instant_health 1 10 true'); c.command('effect give @s minecraft:resistance 99999 4 true')
        c.command('ori form on')

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('release_keys'); c.command('ori tide stop'); c.command('gamemode creative')
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:4000])
