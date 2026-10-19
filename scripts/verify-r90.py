"""R88-R90 real-client check after the playtest report: sneak + jump jumps, a held jump falls with rising speed (no
auto-glide), a re-press in the air still glides, air pose weights, and shots of the soft aura, companion orb and the
condensed-light weapons (held, charging, thrown, inventory). Play04 arena (1000, 200, 1000), shaders off.
Usage: python3 scripts/verify-r90.py <build_id> [--shots DIR]"""
import argparse, shutil, json
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r90-{name}.png')

def client(name):
    return [x['state'] for x in json.load(open(c.out / f'{name}.json'))['samples'] if x['side'] == 'client']

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 6000', 'gamerule doMobSpawning false',
                    'effect give @s minecraft:resistance 900 4 true', 'kill @e[type=!player,distance=..60]']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori unlock all'); c.command('ori energy fill'); c.ticks(4)
    c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 8} {Y - 1} {Z - 4} {X + 8} {Y - 1} {Z + 30} minecraft:smooth_stone')
    c.command(f'fill {X - 8} {Y} {Z - 4} {X + 8} {Y + 24} {Z + 30} minecraft:air')
    # 1. Sneak + jump: must leave the ground.
    c.reset(X + .5, Y, Z + .5, 0, 10); b = c.start(); c.key('sneak', 24); c.ticks(4); c.key('jump', 3); c.ticks(18); c.finish('sneak_jump', b)
    s = client('sneak_jump'); y0 = s[0]['position'][1]
    c.records['sneak_jump_rise'] = max(x['position'][1] for x in s) - y0
    c.records['sneak_jump_crouched'] = any(x['vanilla']['pose'] == 'CROUCHING' for x in s)
    # 2. Held jump on flat ground: no glide, the fall speeds up tick by tick.
    c.reset(X + .5, Y, Z + .5, 0, 10); b = c.start(); c.key('jump', 34); c.ticks(36); c.finish('held_jump', b)
    s = client('held_jump'); ys = [x['position'][1] for x in s]
    c.records['held_jump_modes'] = sorted({x['movement']['mode'] for x in s})
    c.records['held_jump_height'] = max(ys) - ys[0]
    top = ys.index(max(ys)); dys = [round(ys[i + 1] - ys[i], 3) for i in range(top, min(len(ys) - 1, top + 12))]
    c.records['held_jump_fall_dy'] = dys
    # 3. From a tower: held jump off the edge falls freely, a fresh press glides.
    c.command(f'fill {X} {Y} {Z + 10} {X} {Y + 11} {Z + 10} minecraft:smooth_stone')
    c.reset(X + .5, Y + 12, Z + 10.5, 0, 10); b = c.start(); c.key('jump', 40); c.key('forward', 6); c.ticks(16); shot('held-fall'); c.ticks(24); c.finish('tower_held', b)
    s = client('tower_held'); c.records['tower_held_modes'] = sorted({x['movement']['mode'] for x in s})
    ys = [x['position'][1] for x in s]; c.records['tower_held_dy'] = [round(ys[i + 1] - ys[i], 3) for i in range(len(ys) - 1)][:24]
    c.reset(X + .5, Y + 12, Z + 10.5, 0, 10); b = c.start(); c.key('jump', 2); c.key('forward', 6); c.ticks(12); c.key('jump', 30); c.ticks(8); shot('repress-glide'); c.ticks(20); c.finish('tower_glide', b)
    c.records['tower_glide_modes'] = sorted({x['movement']['mode'] for x in client('tower_glide')})
    # 4. Air pose weights in the animation diagnostics.
    c.reset(X + .5, Y, Z + 8.5, 0, 10); b = c.start(); c.key('jump', 12); c.key('forward', 20); c.ticks(5); shot('jump-rise'); c.ticks(6); shot('jump-fall'); c.ticks(10); c.finish('air_pose', b)
    an = [x.get('animation', {}) for x in client('air_pose')]
    c.records['air_base'] = sorted({a.get('base') for a in an if a.get('visible')})
    c.records['lean_range'] = [min(a.get('leanPitch', 0) for a in an if a.get('visible')), max(a.get('leanPitch', 0) for a in an if a.get('visible'))]
    # 5. Run with lean, at dusk so the soft aura shows.
    c.command('time set 13800')
    c.reset(X + .5, Y, Z + .5, 0, 20); b = c.start(); c.key('forward', 30); c.ticks(14); shot('run-aura'); c.ticks(16); c.finish('run', b)
    c.command('time set 18000'); c.reset(X + .5, Y, Z + 4.5, 180, 15); c.ticks(10)
    c.action('camera', mode='THIRD_PERSON_FRONT'); c.ticks(4); shot('night-aura-front')
    # 6. Weapons: held hammer, charging, spear, blade, inventory icons.
    c.command('clear @s'); c.command('give @s orimod:spirit_hammer'); c.command('give @s orimod:spirit_spear'); c.command('give @s orimod:spirit_blade')
    c.action('select_slot', slot=0); c.ticks(6); shot('hammer-held')
    c.key('use', 10); c.ticks(8); shot('hammer-charge'); c.ticks(10)
    c.action('select_slot', slot=2); c.ticks(6); shot('blade-held')
    c.action('select_slot', slot=1); c.ticks(6); shot('spear-held')
    c.action('camera', mode='THIRD_PERSON_BACK'); c.command('time set 13000'); c.ticks(4)
    c.key('use', 3); c.ticks(3); shot('spear-thrown'); c.ticks(10)
    c.action('open_inventory'); c.ticks(6); shot('inventory'); c.action('close_screen')
    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    c.action('release_keys'); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k != 'steps'}, ensure_ascii=False, indent=1))
