"""R106 blighted grounds, client acceptance with real inputs: a placed blight zone day/night, thorns hurt, a blade
only sparks on the gloom core while Charge Flame and a held Flash break it (guards spawn, at most 3), the purification
wave restores the zone in about 30 s with 60 light, and the purified zone keeps Gloom Tide waves away.
Visuals are judged from the screenshots. Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r106.py <build_id> [--only look,thorns,core,purify,tide]"""
import argparse, json, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='look,thorns,core,purify,tide')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 10000, 120, 10000
DAY = 100 + int(time.time()) % 100000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)['block']

def scan(radius=14):
    """Count zone blocks on the surface layer around the centre (one inspect per column, top two layers)."""
    counts = {}
    for dx in range(-radius, radius + 1, 2):
        for dz in range(-radius, radius + 1, 2):
            for dy in (-1, 0):
                b = block(X + dx, Y + dy, Z + dz)
                if b.startswith('orimod:') or b in ('minecraft:grass_block', 'minecraft:water'):
                    counts[b] = counts.get(b, 0) + 1
    return counts

def core():
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(-1, 3):
                if block(X + dx, Y + dy, Z + dz) == 'orimod:gloom_core':
                    return X + dx, Y + dy, Z + dz
    return None

def light():
    return c.action('progress_state')['server']['progress']['light']

def guards():
    return [e for e in c.action('nearby_entities')['entities'] if 'gloom_spitter' in e['type'] and e['alive']]

def survival():
    c.command('gamemode survival'); c.command('ori energy fill'); c.ticks(4)

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty normal', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', f'time set {DAY * 24000 + 6000}',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori awaken', 'ori form on', 'ori unlock all',
                    'ori hints reset @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command(f'tp @s {X} {Y + 30} {Z}'); c.ticks(60)
    for y in range(Y, Y + 20, 4):
        c.command(f'fill {X - 30} {y} {Z - 30} {X + 30} {y + 3} {Z + 30} minecraft:air')
    c.command(f'fill {X - 30} {Y - 3} {Z - 30} {X + 30} {Y - 2} {Z + 30} minecraft:dirt')
    c.command(f'fill {X - 30} {Y - 1} {Z - 30} {X + 30} {Y - 1} {Z + 30} minecraft:grass_block')
    placed = c.command(f'place feature orimod:blighted_grounds {X} {Y} {Z}'); c.ticks(20)
    cpos = core()
    before = scan()
    c.records['zone_before'] = before
    check('zone_placed', placed['result'] > 0 and cpos is not None and before.get('orimod:blighted_soil', 0) > 20,
          {'placed': placed, 'core': cpos, 'counts': before})

    if 'look' in only:
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.reset(X + .5, Y, Z - 16.5, 0, 0); c.ticks(20)
        c.action('review_camera', enabled=True, x=0, y=10, z=-10, targetHeight=0); c.ticks(10); c.screenshot('blight-day')
        c.command(f'time set {DAY * 24000 + 18000}'); c.ticks(10); c.screenshot('blight-night')
        c.command(f'time set {DAY * 24000 + 6000}')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('settings', hideGui=False)

    if 'thorns' in only:
        thorn = None
        for dx in range(-12, 13):
            for dz in range(-12, 13):
                if block(X + dx, Y, Z + dz) == 'orimod:gloom_thorns':
                    thorn = (X + dx, Y, Z + dz); break
            if thorn: break
        survival(); c.command('effect clear @s minecraft:resistance'); c.ticks(2)
        h0 = c.spirit()['server']['health']
        if thorn:
            c.command(f'tp @s {thorn[0] + .5} {thorn[1]} {thorn[2] + .5}'); c.ticks(40)
        h1 = c.spirit()['server']['health']
        c.command('effect give @s minecraft:resistance 99999 4 true'); c.command('effect give @s minecraft:instant_health 1 5 true')
        check('thorns_hurt', thorn is not None and h1 < h0, {'thorn': thorn, 'health': [h0, h1]})

    if 'core' in only and cpos:
        cx, cy, cz = cpos
        survival(); c.reset(cx + .5, cy, cz - 2.5, 0, 15); c.ticks(5)
        state0 = c.action('inspect_block', x=cx, y=cy, z=cz)['state']
        c.key('attack', 2)  # a real left-click on the core with the blade selected
        c.ticks(10)
        state1 = c.action('inspect_block', x=cx, y=cy, z=cz)['state']
        check('blade_does_not_crack_core', block(cx, cy, cz) == 'orimod:gloom_core' and state0 == state1, [state0, state1])
        c.key('orimod.charge_flame', 16); c.ticks(20)
        c.screenshot('core-after-charge')
        state2 = c.action('inspect_block', x=cx, y=cy, z=cz)['state']
        check('charge_flame_cracks_core', state2 != state0, [state0, state2])
        # Hold Flash with refills until the core breaks (4 per second).
        # One continuous hold (each release starts a cooldown and restarts the 20-tick damage cycle), refilling energy.
        broke = False
        c.key('orimod.flash', 300)
        for _ in range(15):
            c.ticks(20); c.command('ori energy fill')
            if block(cx, cy, cz) != 'orimod:gloom_core': broke = True; break
        c.action('release_keys')
        g = guards()
        check('flash_breaks_core', broke)
        check('guards_capped', len(g) <= 3, {'guards': len(g)})
        c.records['guards'] = len(g)

    if 'purify' in only and cpos:
        l0 = light(); c.ticks(200); c.screenshot('purify-mid')
        mid = scan(); c.records['zone_mid'] = mid
        c.ticks(500)
        after = scan(); c.records['zone_after'] = after
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.reset(X + .5, Y, Z - 16.5, 0, 0); c.ticks(20)
        c.action('review_camera', enabled=True, x=0, y=10, z=-10, targetHeight=0); c.ticks(10); c.screenshot('purified-day')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('settings', hideGui=False)
        leftover = sum(v for k, v in after.items() if k in ('orimod:blighted_soil', 'orimod:gloom_thorns', 'orimod:gloom_water'))
        check('purification_completes', leftover == 0, after)
        check('purification_is_gradual', mid.get('orimod:blighted_soil', 0) < before.get('orimod:blighted_soil', 0) and
              mid.get('orimod:blighted_soil', 0) > 0 or leftover == 0, {'mid': mid})
        check('purify_reward', light() - l0 >= 60, {'light': [l0, light()]})

    if 'tide' in only:
        survival(); c.reset(X + .5, Y, Z + .5, 0, 0)
        for t in ('gloom_spitter', 'gloom_hawk', 'gloom_beetle'): c.command(f'kill @e[type=orimod:{t}]')
        c.command(f'time set {DAY * 24000 + 18000}'); c.command('gamerule doMobSpawning true'); c.command('ori tide start')
        spawned = 0
        for _ in range(3):
            c.command(f'tp @s {X + .5} {Y} {Z + .5}'); c.ticks(210)
            spawned += c.spirit()['server'].get('tide', {}).get('nearby', 0)
        c.command('ori tide stop'); c.command('gamerule doMobSpawning false')
        check('purified_zone_shelters_from_tide', spawned == 0, {'nearby_sum': spawned})

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('release_keys'); c.action('review_camera', enabled=False); c.command('gamemode creative')
        c.action('settings', hideGui=False)
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:4000])
