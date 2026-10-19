"""R59–R64 real-client check: Charge Flame cleansing gloom water, Spirit Spear, Gloom Hawk dive telegraph, map stone survey,
spirit-sand Burrow. Play04 arena (1000, 200, 1000). Usage: python3 scripts/verify-r64.py <build_id> [--shots DIR]"""
import argparse, shutil
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r64-{name}.png')

def nearby(kind):
    return [e for e in c.action('nearby_entities')['entities'] if kind in e['type'] and e['alive']]

def me():
    return c.request()['player']['position']

def clean():
    for t in ['orimod:gloom_hawk', 'orimod:gloom_spitter', 'minecraft:zombie', 'orimod:spirit_spear']: c.command(f'kill @e[type={t},distance=..80]')

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty normal', 'time set 13000', 'gamerule doMobSpawning false',
                    'effect give @s minecraft:resistance 900 4 true', 'effect give @s minecraft:saturation 900 1 true']:
        c.command(command)
    clean()
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_dash'); c.action('grant_light', amount=4000)
    for ability in ['bash', 'light_burst', 'charge_flame', 'bow', 'spear', 'water_dash']:
        c.action('progress_action', action='ability', target=ability); c.ticks(4)
    c.records['abilities'] = c.action('progress_state')['server']
    c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK')
    # 1. Charge Flame on a gloom-water pool with a zombie.
    c.command(f'fill {X - 3} {Y - 1} {Z + 2} {X + 3} {Y - 1} {Z + 7} orimod:gloom_water')
    c.command(f'summon minecraft:zombie {X + .5} {Y} {Z + 3.5} {{NoAI:1b,Silent:1b,PersistenceRequired:1b}}')
    c.reset(X + .5, Y, Z + .5, 0, 30); c.ticks(10); shot('gloom-water')
    hp0 = nearby('zombie')[0]['health']
    c.key('orimod.charge_flame', 16); c.ticks(8); shot('charge-flame-hold'); c.ticks(10); shot('charge-flame-burst')
    c.records['charge_flame'] = {'zombie_hp_before': hp0, 'zombie_hp_after': [e['health'] for e in nearby('zombie')],
                                 'center_block': c.action('inspect_block', x=X, y=Y - 1, z=Z + 3)}
    c.command(f'fill {X - 3} {Y - 1} {Z + 2} {X + 3} {Y - 1} {Z + 7} minecraft:stone replace orimod:gloom_water')
    c.command(f'fill {X - 3} {Y - 1} {Z + 2} {X + 3} {Y - 1} {Z + 7} minecraft:stone replace minecraft:water')
    clean(); c.ticks(10)
    # 2. Spirit Spear through three zombies in a line.
    for dz in (4, 6, 8): c.command(f'summon minecraft:zombie {X + .5} {Y} {Z + dz + .5} {{NoAI:1b,Silent:1b,PersistenceRequired:1b}}')
    c.command('item replace entity @s weapon.mainhand with orimod:spirit_spear')
    c.reset(X + .5, Y, Z + .5, 0, 5); c.ticks(6)
    c.key('use', 2); c.ticks(3); shot('spear-flight'); c.ticks(10)
    c.records['spear'] = [e['health'] for e in nearby('zombie')]
    clean(); c.command('item replace entity @s weapon.mainhand with minecraft:air'); c.ticks(10)
    # 3. Gloom Hawk circling, telegraph and dive.
    c.reset(X + .5, Y, Z + .5, 0, -25)
    c.command(f'summon orimod:gloom_hawk {X + .5} {Y + 7} {Z + 6.5} {{PersistenceRequired:1b}}')
    hawk = []
    for i in range(16):
        c.ticks(8); h = nearby('gloom_hawk')
        hawk.append(h[0] if h else None)
        if i in (3, 8, 12): shot(f'hawk-{i}')
    c.records['hawk'] = hawk
    clean(); c.ticks(10)
    # 4. Map stone survey.
    c.command(f'setblock {X} {Y} {Z + 2} orimod:map_stone')
    c.reset(X + .5, Y, Z + .5, 0, 30); c.ticks(6)
    c.action('use_block', x=X, y=Y, z=Z + 2); c.ticks(20); shot('map-stone')
    c.command(f'setblock {X} {Y} {Z + 2} air')
    # 5. Burrow through a 3-thick spirit-sand wall.
    c.command(f'fill {X - 2} {Y} {Z + 3} {X + 2} {Y + 2} {Z + 5} orimod:spirit_sand')
    c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(6)
    z0 = me()[2]; c.key('forward', 30); track = []
    for i in range(12):
        c.ticks(2); track.append(me())
        if i == 4: shot('burrow-inside')
    c.action('release_keys'); c.ticks(6); shot('burrow-out')
    c.records['burrow'] = {'start_z': z0, 'track': track}
    c.command(f'fill {X - 2} {Y} {Z + 3} {X + 2} {Y + 2} {Z + 5} air')
    c.records['status'] = 'ok'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    clean(); c.save(); c.action('release_keys'); c.command('time set 6000')
print(c.out, {k: c.records.get(k) for k in ['status', 'charge_flame', 'spear', 'burrow']}, flush=True)
