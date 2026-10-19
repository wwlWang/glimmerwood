"""R66–R70 real-client check: Spirit Arc volley, map fragment, Gloom Matriarch (summon, dive telegraph, stun, feathers,
phase two with hawks). Play04 arena (1000, 200, 1000). Usage: python3 scripts/verify-r70.py <build_id> [--shots DIR]"""
import argparse, shutil
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r70-{name}.png')

def nearby(kind):
    return [e for e in c.action('nearby_entities')['entities'] if kind in e['type'] and e['alive']]

def clean():
    for t in ['orimod:gloom_matriarch', 'orimod:gloom_hawk', 'orimod:gloom_shot', 'minecraft:zombie', 'minecraft:arrow', 'minecraft:item']:
        c.command(f'kill @e[type={t},distance=..90]')

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty normal', 'time set 13000', 'gamerule doMobSpawning false',
                    'effect give @s minecraft:resistance 900 4 true', 'effect give @s minecraft:saturation 900 1 true']:
        c.command(command)
    clean()
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_dash'); c.action('grant_light', amount=3000)
    for ability in ['bow', 'bash']: c.action('progress_action', action='ability', target=ability); c.ticks(4)
    c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK')
    # 1. Spirit Arc volley: sneak + full charge.
    for dx in (-3, 0, 3): c.command(f'summon minecraft:zombie {X + dx + .5} {Y} {Z + 12.5} {{NoAI:1b,Silent:1b,PersistenceRequired:1b}}')
    c.command('item replace entity @s weapon.mainhand with orimod:spirit_bow')
    c.reset(X + .5, Y, Z + .5, 0, -2); c.ticks(6)
    c.key('sneak', 34); c.key('use', 26); c.ticks(27); shot('arc-volley'); c.ticks(20)
    c.records['volley_zombies'] = [round(e['health'], 1) for e in nearby('zombie')]
    clean(); c.ticks(4)
    # 2. Map fragment.
    c.command('item replace entity @s weapon.mainhand with orimod:map_fragment')
    c.key('use', 2); c.ticks(10); shot('map-fragment')
    c.records['fragment_left'] = c.request().get('player', {}).get('mainHand')
    # 3. Gloom Matriarch.
    c.command('item replace entity @s weapon.mainhand with minecraft:air')
    c.command(f'setblock {X} {Y - 1} {Z + 6} orimod:storm_altar')
    c.reset(X + .5, Y, Z + 4.5, 0, 50); c.ticks(4)
    c.action('use_block', x=X, y=Y - 1, z=Z + 6); c.ticks(10)
    assert nearby('gloom_matriarch'), 'matriarch not summoned'
    c.reset(X + .5, Y, Z - 4.5, 0, -20)
    track = []
    for i in range(36):
        c.ticks(6); m = nearby('gloom_matriarch')
        track.append({k: m[0].get(k) for k in ('position', 'health')} if m else None)
        if i in (6, 12, 18, 24, 30): shot(f'matriarch-{i}')
    c.records['matriarch_track'] = track
    c.command('damage @e[type=orimod:gloom_matriarch,limit=1] 80 minecraft:generic'); c.ticks(30)
    shot('matriarch-phase2')
    c.records['phase2_hawks'] = len(nearby('gloom_hawk'))
    c.command(f'setblock {X} {Y - 1} {Z + 6} minecraft:stone')
    c.records['status'] = 'ok'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    clean(); c.save(); c.action('release_keys'); c.command('time set 6000')
print(c.out, {k: c.records.get(k) for k in ['status', 'volley_zombies', 'phase2_hawks']}, flush=True)
