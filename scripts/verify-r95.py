"""R95 pose pass: run, swim (cruise + water dash), spirit glide, elytra, held items. Side / three-quarter shots plus the
animation diagnostics (base clip, pose override weights). Play04 arena (1000, 200, 1000).
Usage: python3 scripts/verify-r95.py <build_id> [--shots DIR] [--tag before|after]"""
import argparse, shutil, json
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); p.add_argument('--tag', default='after'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r95-{args.tag}-{name}.png')

def client(name):
    return [x['state'] for x in json.load(open(c.out / f'{name}.json'))['samples'] if x['side'] == 'client']

def anim(samples):
    return [s['animation'] for s in samples if s.get('animation', {}).get('visible')]

def poses(an):
    out = {}
    for a in an:
        for k, v in (a.get('secondary', {}).get('pose') or {}).items():
            if isinstance(v, (int, float)): out[k] = max(out.get(k, 0), v)
    return out

def side(x=0, y=.9, z=4.2, h=.55):
    c.action('review_camera', enabled=True, x=x, y=y, z=z, targetHeight=h)

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 1000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
                    'effect give @s minecraft:resistance 900 4 true', 'kill @e[type=!player,distance=..80]', 'clear @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori unlock all'); c.command('ori energy fill'); c.ticks(4)
    c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 14} {Y - 1} {Z - 6} {X + 14} {Y - 1} {Z + 40} minecraft:moss_block')
    c.command(f'fill {X - 14} {Y} {Z - 6} {X + 14} {Y + 30} {Z + 40} minecraft:air')

    # 1. Run, side view (towards -X).
    c.reset(X + 12.5, Y, Z + 2.5, 90, 10); side(z=-2.6, y=.35, h=.45); c.ticks(4)
    b = c.start(); c.key('forward', 60); c.ticks(18)
    for i in range(6): shot(f'run-{i}'); c.ticks(1)
    c.ticks(20); c.finish('run', b)
    an = anim(client('run')); c.records['run_pose'] = poses(an); c.records['run_lean_max'] = max((a['leanPitch'] for a in an), default=0)

    # 2. Swim: a deep pool; sprint-swim (cruise), then a water dash, then idle treading.
    c.command(f'fill {X - 12} {Y - 8} {Z + 14} {X + 12} {Y - 2} {Z + 38} minecraft:stone')
    c.command(f'fill {X - 11} {Y - 7} {Z + 15} {X + 11} {Y - 1} {Z + 37} minecraft:water')
    c.reset(X + 9.5, Y - 4.5, Z + 26.5, 90, 0); side(z=-3.2, y=.2, h=.3); c.ticks(6)
    b = c.start(); c.key('sprint', 70); c.key('forward', 70); c.ticks(16)
    for i in range(3): shot(f'swim-{i}'); c.ticks(2)
    c.key('orimod.dash', 2); c.ticks(1)
    for i in range(4): shot(f'swimdash-{i}'); c.ticks(1)
    c.ticks(30); c.finish('swim', b)
    an = anim(client('swim')); c.records['swim_bases'] = sorted({a['base'] for a in an}); c.records['swim_pose'] = poses(an)
    c.reset(X + .5, Y - 3.5, Z + 26.5, 0, 0); side(x=2.6, z=2.0, y=.3, h=.4); c.ticks(10)
    b = c.start(); c.ticks(6); shot('tread-0'); c.key('forward', 20); c.ticks(8); shot('tread-1'); c.ticks(14); c.finish('tread', b)
    an = anim(client('tread')); c.records['tread_bases'] = sorted({a['base'] for a in an}); c.records['tread_pose'] = poses(an)
    c.command(f'fill {X - 11} {Y - 7} {Z + 15} {X + 11} {Y - 1} {Z + 37} minecraft:air')

    # 3. Spirit glide from a tower (fresh press in the air), side view.
    c.command(f'fill {X - 2} {Y} {Z + 2} {X + 2} {Y + 14} {Z + 4} minecraft:smooth_stone')
    c.reset(X + .5, Y + 15, Z + 3.5, 0, 10); side(x=-3.4, z=0, y=.5, h=.5); c.ticks(4)
    b = c.start(); c.key('forward', 40); c.key('jump', 2); c.ticks(12); c.key('jump', 40); c.ticks(8)
    for i in range(4): shot(f'glide-{i}'); c.ticks(3)
    c.ticks(20); c.finish('glide', b)
    an = anim(client('glide')); c.records['glide_bases'] = sorted({a['base'] for a in an}); c.records['glide_pose'] = poses(an)

    # 4. Elytra: same tower, ground jump then a jump press in the air starts fall flying.
    c.command('item replace entity @s armor.chest with minecraft:elytra')
    # Walk off the tower and press jump while falling (a ground jump's short hop can land before the press).
    c.reset(X + .5, Y + 15, Z + 3.5, 0, 10); c.ticks(10)
    b = c.start(); c.key('forward', 8); c.ticks(12); c.key('jump', 20); c.ticks(3)
    c.records['elytra_started'] = c.spirit()['client']['vanilla'].get('fallFlying')
    c.key('forward', 30); c.action('look', yaw=0, pitch=15); c.ticks(4)
    for i in range(4): shot(f'elytra-{i}'); c.ticks(3)
    c.ticks(16); c.finish('elytra', b)
    s = client('elytra'); an = anim(s)
    c.records['elytra_flying'] = any(a.get('secondary', {}).get('pose', {}).get('family') == 'elytra' for a in an)
    c.records['elytra_bases'] = sorted({a['base'] for a in an}); c.records['elytra_pose'] = poses(an)
    c.command('item replace entity @s armor.chest with air')

    # 5. Held items, three-quarter view: torch, pickaxe while walking, trident charge, spirit spear ready.
    c.command('clear @s')
    c.command('item replace entity @s hotbar.0 with minecraft:torch'); c.command('item replace entity @s hotbar.1 with minecraft:iron_pickaxe')
    c.command('item replace entity @s hotbar.2 with minecraft:trident'); c.command('item replace entity @s hotbar.3 with orimod:spirit_spear')
    c.reset(X + .5, Y, Z + 8.5, 0, 10); side(x=1.6, z=1.9, y=.5, h=.55); c.ticks(4)
    b = c.start()
    c.action('select_slot', slot=0); c.ticks(14); shot('hold-torch')
    c.action('select_slot', slot=1); c.ticks(14); shot('hold-pickaxe')
    c.key('forward', 16); c.ticks(10); shot('hold-pickaxe-walk'); c.ticks(8)
    c.action('select_slot', slot=2); c.ticks(10); shot('hold-trident')
    c.key('use', 20); c.ticks(14); shot('trident-charge'); c.ticks(4); c.action('release_keys'); c.ticks(20)
    c.action('select_slot', slot=3); c.ticks(14); shot('hold-spear')
    c.finish('hold', b)
    an = anim(client('hold')); c.records['hold_overlays'] = sorted({a.get('overlay') for a in an}); c.records['hold_pose'] = poses(an)

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('review_camera', enabled=False)
    except Exception: pass
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k != 'steps'}, ensure_ascii=False, indent=1))
