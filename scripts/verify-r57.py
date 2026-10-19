"""R52–R57 real-client check: larger enemy silhouettes at distance, boss escape + victory, Climb, Water Dash and a
keystone door. Play04 arena (1000, 200, 1000). Usage: python3 scripts/verify-r57.py <build_id> [--shots DIR]"""
import argparse, shutil
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r57-{name}.png')

def nearby(kind):
    return [e for e in c.action('nearby_entities')['entities'] if kind in e['type'] and e['alive']]

def me():
    return c.request()['player']['position']

def clean():
    for t in ['gloom_heart', 'gloom_spitter', 'gloom_shot']: c.command(f'kill @e[type=orimod:{t},distance=..80]')

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty normal', 'time set 13000', 'gamerule doMobSpawning false',
                    'effect give @s minecraft:resistance 900 4 true', 'effect give @s minecraft:saturation 900 1 true']:
        c.command(command)
    clean()
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_dash'); c.action('grant_light', amount=3000)
    for ability in ['climb', 'water_dash']: c.action('progress_action', action='ability', target=ability); c.ticks(4)
    c.records['abilities'] = c.action('progress_state')['server']
    c.action('settings', hideGui=False); c.action('camera', mode='FIRST_PERSON')
    # 1. Readability at distance.
    c.reset(X + .5, Y, Z + .5, 0, 0)
    c.command(f'summon orimod:gloom_spitter {X - 2.5} {Y} {Z + 10.5} {{NoAI:1b,PersistenceRequired:1b}}')
    c.command(f'summon orimod:gloom_heart {X + 4.5} {Y + 3} {Z + 16.5} {{NoAI:1b,PersistenceRequired:1b}}')
    c.ticks(30); shot('distance-look')
    clean()
    # 2. Boss escape and victory.
    c.command(f'setblock {X} {Y} {Z + 8} orimod:gloom_altar')
    c.reset(X + .5, Y, Z + 5.5, 0, 40); c.ticks(4)
    c.action('use_block', x=X, y=Y, z=Z + 8); c.ticks(20)
    assert nearby('gloom_heart'), 'boss not woken'
    c.reset(X + .5, Y, Z + 3.5, 0, 0); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command('kill @e[type=orimod:gloom_heart,distance=..40]'); c.ticks(30)
    shot('escape-start'); c.ticks(40); shot('escape-front')
    c.command(f'tp @s {X + .5} {Y} {Z - 16.5} 180 0'); c.ticks(10); shot('victory')
    c.records['progress_after_victory'] = c.action('progress_state')['server']
    c.command(f'setblock {X} {Y} {Z + 8} air'); clean(); c.ticks(60)
    # 3. Climb a 7-high wall.
    c.command(f'fill {X - 3} {Y} {Z + 3} {X + 3} {Y + 6} {Z + 3} minecraft:stone_bricks')
    c.reset(X + .5, Y, Z + 1.7, 0, 0); c.ticks(4)
    y0 = me()[1]
    c.key('jump', 3); c.key('forward', 40)
    top = y0
    for i in range(20):
        c.ticks(2); top = max(top, me()[1])
        if i == 8: shot('climb')
    c.action('release_keys'); c.ticks(10)
    c.records['climb'] = {'start_y': y0, 'top_y': top}
    c.command(f'fill {X - 3} {Y} {Z + 3} {X + 3} {Y + 6} {Z + 3} minecraft:air')
    # 4. Water Dash in a pool.
    c.command(f'fill {X - 4} {Y} {Z + 2} {X + 4} {Y + 3} {Z + 14} minecraft:water')
    c.reset(X + .5, Y + 1, Z + 3.5, 0, 10); c.ticks(10)
    p0 = me(); c.key('orimod.dash', 3); c.ticks(3); shot('water-dash'); c.ticks(5); p1 = me()
    c.records['water_dash'] = {'from': p0, 'to': p1, 'dz': p1[2] - p0[2]}
    c.command(f'fill {X - 4} {Y} {Z + 2} {X + 4} {Y + 3} {Z + 14} minecraft:air'); c.ticks(10)
    # 5. Keystone door.
    c.command(f'fill {X} {Y} {Z + 4} {X + 1} {Y + 1} {Z + 4} orimod:keystone_door')
    c.command('item replace entity @s weapon.mainhand with orimod:keystone 2')
    c.reset(X + .5, Y, Z + 2.5, 0, 10); c.ticks(4); shot('door-sealed')
    c.action('use_block', x=X, y=Y, z=Z + 4); c.ticks(6); shot('door-half')
    c.records['door_half'] = c.action('inspect_block', x=X + 1, y=Y + 1, z=Z + 4)
    c.action('use_block', x=X + 1, y=Y, z=Z + 4); c.ticks(8); shot('door-open')
    c.records['door_open'] = c.action('inspect_block', x=X, y=Y, z=Z + 4)
    c.command(f'fill {X} {Y} {Z + 4} {X + 1} {Y + 1} {Z + 4} minecraft:air')
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    clean(); c.save(); c.action('release_keys'); c.action('camera', mode='THIRD_PERSON_BACK'); c.command('time set 6000')
print(c.out, {k: c.records.get(k) for k in ['status', 'climb', 'water_dash', 'door_half', 'door_open']}, flush=True)
