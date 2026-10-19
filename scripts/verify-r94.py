"""R94 animation polish: land into run, gait knee depth, ledge mantle (and its jump cancel), weapon swing/charge.
Play04 arena (1000, 200, 1000). Side shots use the dev review camera.
Usage: python3 scripts/verify-r94.py <build_id> [--shots DIR]"""
import argparse, shutil, json
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r94-{name}.png')

def client(name):
    return [x['state'] for x in json.load(open(c.out / f'{name}.json'))['samples'] if x['side'] == 'client']

def anim(samples):
    return [s['animation'] for s in samples if s.get('animation', {}).get('visible')]

def side(x=0, y=.9, z=4.2, h=.55):
    c.action('review_camera', enabled=True, x=x, y=y, z=z, targetHeight=h)

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 1000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
                    'effect give @s minecraft:resistance 900 4 true', 'kill @e[type=!player,distance=..60]', 'clear @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori unlock all'); c.command('ori energy fill'); c.ticks(4)
    c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 14} {Y - 1} {Z - 6} {X + 14} {Y - 1} {Z + 30} minecraft:moss_block')
    c.command(f'fill {X - 14} {Y} {Z - 6} {X + 14} {Y + 24} {Z + 30} minecraft:air')

    # 1. Land into run: run off a 3-high ledge (towards -X) holding the direction, side camera.
    c.command(f'fill {X + 3} {Y} {Z + 8} {X + 12} {Y + 2} {Z + 10} minecraft:smooth_stone')
    c.reset(X + 11.5, Y + 3, Z + 9.5, 0, 10); side(z=-3.0, y=.4, h=.5); c.ticks(4)
    b = c.start(); c.key('right', 50); c.ticks(31)
    for i in range(7): shot(f'landrun-{i}'); c.ticks(1)
    c.ticks(16); c.finish('land_run', b)
    an = anim(client('land_run'))
    c.records['land_run_bases'] = [a['base'] for a in an]
    c.records['land_run_weight_max'] = max((a['secondary'].get('landRun', 0) for a in an), default=0)
    c.records['land_run_full_land_clip'] = any(a['base'] in ('land_light', 'land_heavy') for a in an)
    # Control: stand-still drop from the same ledge (no input on landing) keeps the ordinary land clip.
    c.reset(X + 3.6, Y + 3, Z + 9.5, 90, 10); c.ticks(2)
    b = c.start(); c.key('forward', 4); c.ticks(30); c.finish('land_still', b)
    c.records['land_still_bases'] = sorted({a['base'] for a in anim(client('land_still'))})

    # 2. Run knee depth, side view on flat ground (towards -X).
    c.reset(X + 12.5, Y, Z + 2.5, 0, 10); side(z=-2.4, y=.35, h=.45); c.ticks(4)
    b = c.start(); c.key('right', 60); c.ticks(18)
    for i in range(6): shot(f'run-knee-{i}'); c.ticks(1)
    c.ticks(20); c.finish('run_knee', b)
    an = anim(client('run_knee'))
    c.records['gait_extra_max'] = max((a['secondary'].get('gaitExtra', 0) for a in an), default=0)
    c.records['gait_bones'] = an[-1]['secondary'].get('gaitBones') if an else None

    # 3. Wall mantle: 2-high wall ahead (+Z); run, jump, hold forward -> pop over the lip.
    c.command(f'fill {X - 3} {Y} {Z + 20} {X + 3} {Y + 1} {Z + 29} minecraft:mossy_cobblestone')
    c.reset(X + .5, Y, Z + 17.5, 0, 10); side(x=-3.2, z=0, y=.9, h=.8); c.ticks(4)
    b = c.start(); c.key('forward', 24); c.ticks(5); c.key('jump', 3); c.ticks(3)
    for i in range(8): c.ticks(1); shot(f'mantle-{i}')
    c.ticks(16); after = c.finish('mantle', b)
    s = client('mantle'); an = anim(s)
    c.records['mantle_seen'] = any(a['secondary'].get('mantle') for a in an)
    c.records['mantle_bases'] = [a['base'] for a in an if a['secondary'].get('mantle')]
    c.records['mantle_final_y'] = after['client']['position'][1] if 'position' in after['client'] else None
    ys = [x['position'][1] for x in s]; c.records['mantle_y_path'] = [round(v - Y, 2) for v in ys][:40]
    c.records['mantle_on_top'] = sum(1 for v in ys if abs(v - (Y + 2)) < .02) >= 5
    # 3b. Cancel: re-press jump mid-pop -> double jump (rises above the lip), no mantle push-through.
    c.reset(X + .5, Y, Z + 17.5, 0, 10); c.ticks(2)
    b = c.start(); c.key('forward', 40); c.ticks(5); c.key('jump', 2); c.ticks(9); c.key('jump', 3); c.ticks(30); c.finish('mantle_cancel', b)
    s = client('mantle_cancel'); an = anim(s)
    c.records['cancel_modes'] = sorted({x['movement']['mode'] for x in s})
    c.records['cancel_peak'] = round(max(x['position'][1] for x in s) - Y, 2)

    # 4. Weapons, three-quarter view.
    c.action('review_camera', enabled=False); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command('clear @s'); c.command('give @s orimod:spirit_blade'); c.command('give @s orimod:spirit_hammer'); c.command('give @s orimod:spirit_spear')
    c.reset(X + .5, Y, Z + 2.5, 0, 10); side(x=1.5, z=1.7, y=.55, h=.6); c.ticks(4)
    c.action('select_slot', slot=0); c.ticks(6)
    b = c.start(); c.key('attack', 1); c.ticks(1); shot('blade-1a'); c.ticks(2); shot('blade-1b'); c.ticks(3); c.key('attack', 1); c.ticks(3); shot('blade-2'); c.ticks(10); c.key('attack', 1); c.ticks(4); shot('blade-3'); c.ticks(20)
    c.finish('blade', b)
    an = anim(client('blade'))
    c.records['blade_twist_range'] = [min((a['secondary'].get('twist', 0) for a in an), default=0), max((a['secondary'].get('twist', 0) for a in an), default=0)]
    c.records['blade_overlays'] = sorted({a.get('overlay') for a in an})
    c.action('select_slot', slot=1); c.ticks(6)
    b = c.start(); c.key('use', 24); c.ticks(6); shot('hammer-charge-a'); c.ticks(10); shot('hammer-charge-b'); c.ticks(9); shot('hammer-slam-a'); c.ticks(3); shot('hammer-slam-b'); c.ticks(14)
    c.finish('hammer', b)
    an = anim(client('hammer'))
    c.records['hammer_arch_range'] = [min((a['secondary'].get('spineArch', 0) for a in an), default=0), max((a['secondary'].get('spineArch', 0) for a in an), default=0)]
    c.action('select_slot', slot=2); c.ticks(8); shot('spear-ready')
    b = c.start(); c.key('use', 2); c.ticks(1); shot('spear-throw-a'); c.ticks(2); shot('spear-throw-b'); c.ticks(14); c.finish('spear', b)
    an = anim(client('spear'))
    c.records['spear_twist_range'] = [min((a['secondary'].get('twist', 0) for a in an), default=0), max((a['secondary'].get('twist', 0) for a in an), default=0)]
    c.records['spear_overlays'] = sorted({a.get('overlay') for a in an})

    ok = (not c.records['land_run_full_land_clip'] and c.records['land_run_weight_max'] > .3 and c.records['gait_extra_max'] > .1
          and c.records['mantle_seen'] and c.records['mantle_on_top'])
    c.records['status'] = 'ok' if ok else 'warn'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('review_camera', enabled=False)
    except Exception: pass
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k != 'steps'}, ensure_ascii=False, indent=1))
