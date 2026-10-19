"""R42+ real-client check: a Gloom Spitter spits an orb, Ori Bashes it back through the real R binding, and the
redirected orb damages the spitter. Also takes look shots of the spitter, Spirit Light orb glow and a cell pedestal.
Runs in the play04 test world arena (1000, 200, 1000).
Usage: python3 scripts/verify-gloom-bash.py <build_id> [--shots DIR]"""
import argparse, math, shutil
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r45-{name}.png')

def nearby(kind):
    return [e for e in c.action('nearby_entities')['entities'] if kind in e['type'] and e['alive']]

def dist(a, b):
    return math.dist(a, b)

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty normal', 'time set 13000', 'gamerule doMobSpawning false',
                    'kill @e[type=orimod:gloom_spitter,distance=..60]', 'kill @e[type=orimod:gloom_shot,distance=..60]',
                    'effect give @s minecraft:resistance 600 4 true', 'effect give @s minecraft:saturation 600 1 true']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_bash')
    c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
    attempts = []
    for attempt in range(4):
        c.command('kill @e[type=orimod:gloom_spitter,distance=..60]'); c.command('kill @e[type=orimod:gloom_shot,distance=..60]')
        c.reset(X + .5, Y, Z + .5, 0, 5); c.ticks(4)
        c.command(f'summon orimod:gloom_spitter {X + .5} {Y} {Z + 9.5} {{PersistenceRequired:1b}}'); c.ticks(2)
        spitter = nearby('gloom_spitter')[0]; hp0 = spitter['health']
        if attempt == 0: c.ticks(10); shot('spitter-look')
        me = c.request()['player']['position']
        orb = None
        c.command('tick rate 5')   # slow the world so the HTTP harness reacts inside the orb's flight
        for _ in range(240):   # wait for an orb within Bash range
            orbs = nearby('gloom_shot')
            close = [o for o in orbs if dist(o['position'], me) < 5.0]
            if close: orb = close[0]; break
            c.ticks(1)
        assert orb, 'spitter never spat an orb'
        if attempt == 0: shot('orb-incoming')
        c.key('orimod.bash', 20); c.ticks(2)
        state = c.spirit(); aimed = state['server']['movement']['mode'] == 'bash_aim'
        target = state['server']['movement'].get('targetId')
        c.action('look', yaw=180, pitch=0); c.ticks(1)
        if attempt == 0: shot('bash-aim')
        c.action('release_keys'); c.ticks(1)
        after = [o for o in nearby('gloom_shot') if o['id'] == orb['id']]
        c.command('tick rate 20')
        hp1 = hp0
        for _ in range(30):
            c.ticks(1)
            s = nearby('gloom_spitter'); hp1 = s[0]['health'] if s else 0
            if hp1 < hp0: break
        if attempt == 0 or hp1 < hp0: shot('orb-returned')
        result = {'aimed': aimed, 'target': target, 'orb': orb['id'], 'orb_velocity_after': after[0]['velocity'] if after else None,
                  'spitter_before': hp0, 'spitter_after': hp1}
        attempts.append(result); c.records['attempts'] = attempts; c.save()
        if aimed and hp1 < hp0: break
    c.records['redirect_hit'] = any(a['aimed'] and a['spitter_after'] < a['spitter_before'] for a in attempts)
    assert c.records['redirect_hit'], attempts
    c.command('kill @e[type=orimod:gloom_spitter,distance=..60]'); c.command('kill @e[type=orimod:gloom_shot,distance=..60]')
    # Spirit Light orbs glowing at rest and while pulled in.
    c.reset(X + .5, Y, Z + .5, 0, 25); c.ticks(4)
    for dx, dz in [(-1, 4), (0, 5), (1, 4.5), (2, 6), (-2, 6)]:
        c.command(f'summon minecraft:item {X + .5 + dx} {Y + .2} {Z + .5 + dz} {{Item:{{id:"orimod:spirit_light",count:1}},PickupDelay:32767}}')
    c.ticks(20); shot('light-orbs')
    c.command(f'kill @e[type=minecraft:item,distance=..20]')
    # Cell pedestals.
    c.command(f'setblock {X - 1} {Y} {Z + 3} orimod:life_cell_pedestal'); c.command(f'setblock {X + 2} {Y} {Z + 3} orimod:energy_cell_pedestal')
    c.reset(X + .5, Y, Z - .5, 0, 20); c.ticks(30); shot('cell-pedestals')
    c.command(f'setblock {X - 1} {Y} {Z + 3} air'); c.command(f'setblock {X + 2} {Y} {Z + 3} air')
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.command('tick rate 20'); c.save(); c.action('release_keys'); c.action('settings', hideGui=False); c.command('time set 6000')
print(c.out, c.records.get('redirect_hit'), c.records.get('attempts'), flush=True)
