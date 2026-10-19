"""R48–R50 real-client check: new Gloom Spitter look, Spirit Hammer smash, and the Gloom Heart boss (altar wake,
telegraph, Bash an orb back into it through the real R binding, phase two). Play04 arena (1000, 200, 1000).
Usage: python3 scripts/verify-boss.py <build_id> [--shots DIR]"""
import argparse, math, shutil
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r50-{name}.png')

def nearby(kind):
    return [e for e in c.action('nearby_entities')['entities'] if kind in e['type'] and e['alive']]

def clean():
    for t in ['gloom_heart', 'gloom_spitter', 'gloom_shot']: c.command(f'kill @e[type=orimod:{t},distance=..80]')
    c.command('kill @e[type=minecraft:zombie,distance=..40]')

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty normal', 'time set 13000', 'gamerule doMobSpawning false',
                    'effect give @s minecraft:resistance 900 4 true', 'effect give @s minecraft:saturation 900 1 true']:
        c.command(command)
    clean()
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_bash')
    c.action('settings', hideGui=True); c.action('camera', mode='FIRST_PERSON')
    # 1. Gloom Spitter look and charge telegraph.
    c.reset(X + .5, Y, Z + .5, 0, 5)
    c.command(f'summon orimod:gloom_spitter {X + .5} {Y} {Z + 4.5} {{PersistenceRequired:1b}}'); c.ticks(20)
    for i in range(6): c.ticks(6); shot(f'spitter-{i}')
    clean()
    # 2. Spirit Hammer smash on a zombie ahead.
    c.command('item replace entity @s weapon.mainhand with orimod:spirit_hammer')
    c.command(f'summon minecraft:zombie {X + .5} {Y} {Z + 3} {{NoAI:1b,Silent:1b,PersistenceRequired:1b}}'); c.ticks(4)
    c.reset(X + .5, Y, Z + .5, 0, 20); c.action('camera', mode='THIRD_PERSON_BACK')
    hp0 = nearby('zombie')[0]['health']
    c.key('use', 18); c.ticks(14); shot('hammer-windup'); c.ticks(6); shot('hammer-smash')
    z = nearby('zombie'); c.records['hammer'] = {'before': hp0, 'after': z[0]['health'] if z else 0}
    assert c.records['hammer']['after'] < hp0, c.records['hammer']
    clean(); c.command('item replace entity @s weapon.mainhand with minecraft:air')
    # 3. Gloom Heart: wake it at an altar 8 blocks ahead.
    c.command(f'setblock {X} {Y} {Z + 8} orimod:gloom_altar')
    c.reset(X + .5, Y, Z + 5.5, 0, 40); c.ticks(4)   # within reach of the altar
    c.action('use_block', x=X, y=Y, z=Z + 8); c.ticks(10)
    c.reset(X + .5, Y, Z + .5, 0, -15)
    heart = nearby('gloom_heart'); assert heart, 'altar did not wake the Gloom Heart'
    c.records['heart_spawn'] = heart[0]
    for i in range(5): c.ticks(8); shot(f'heart-{i}')
    attempts = []
    for attempt in range(5):
        c.command('effect give @s minecraft:instant_health 1 5 true')
        hp0 = nearby('gloom_heart')[0]['health']
        me = c.request()['player']['position']
        c.command('tick rate 5')
        orb = None
        for _ in range(300):
            close = [o for o in nearby('gloom_shot') if math.dist(o['position'], me) < 5]
            if close: orb = close[0]; break
            c.ticks(1)
        if not orb: c.command('tick rate 20'); attempts.append({'orb': None}); continue
        c.key('orimod.bash', 20); c.ticks(2)
        state = c.spirit(); aimed = state['server']['movement']['mode'] == 'bash_aim'
        hb = nearby('gloom_heart')[0]['position']; eye = [me[0], me[1] + 1.6, me[2]]
        d = [hb[0] - orb['position'][0], hb[1] + .8 - orb['position'][1], hb[2] - orb['position'][2]]
        yaw = math.degrees(math.atan2(d[0], -d[2]))      # look away from the Heart: the orb flies back at it
        pitch = math.degrees(math.atan2(d[1], math.hypot(d[0], d[2])))
        c.action('look', yaw=yaw, pitch=pitch); c.ticks(1)
        if attempt == 0: shot('heart-bash-aim')
        c.action('release_keys')
        hp1 = hp0
        for _ in range(40):
            c.ticks(1)
            hs = nearby('gloom_heart'); hp1 = hs[0]['health'] if hs else 0
            if hp1 < hp0: break
        c.command('tick rate 20')
        attempts.append({'aimed': aimed, 'target': state['server']['movement'].get('targetId'), 'orb': orb['id'], 'before': hp0, 'after': hp1})
        c.records['bash_attempts'] = attempts; c.save()
        c.reset(X + .5, Y, Z + .5, 0, -15)
        if aimed and hp1 < hp0: shot('heart-hit'); break
    c.records['heart_redirect_hit'] = any(a.get('aimed') and a['after'] < a['before'] for a in attempts)
    # Phase two.
    c.command('damage @e[type=orimod:gloom_heart,limit=1] 50 minecraft:generic'); c.ticks(10)
    c.records['phase2_spitters'] = len(nearby('gloom_spitter'))
    for i in range(4): c.ticks(10); shot(f'heart-phase2-{i}')
    c.records['status'] = 'passed' if c.records['heart_redirect_hit'] else 'redirect-missed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.command('tick rate 20'); clean(); c.command(f'setblock {X} {Y} {Z + 8} air')
    c.save(); c.action('release_keys'); c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK'); c.command('time set 6000')
print(c.out, c.records.get('status'), c.records.get('hammer'), c.records.get('bash_attempts'), c.records.get('phase2_spitters'), flush=True)
