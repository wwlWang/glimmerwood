"""R105 Gloom Beetle client acceptance with real inputs: the model (idle / windup / flipped), frontal armour vs a
blow from behind, Stomp flips it, a locked-direction charge that a sidestep dodges into a wall (stun, then any hit
flips it), and beetles in Gloom Tide waves. Visuals are judged from the screenshots by a reviewer.
Needs devtools nearby_entities `state` for beetles. Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r105.py <build_id> [--only look,armor,stomp,charge,tide]"""
import argparse, json, math, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='look,armor,stomp,charge,tide')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 9000, 120, 9000
DAY = 100 + int(time.time()) % 100000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def beetles():
    return [e for e in c.action('nearby_entities')['entities'] if 'gloom_beetle' in e['type'] and e['alive']]

def beetle():
    b = beetles()
    return b[0] if b else None

def pad():
    c.command('gamemode creative')
    for y in range(Y, Y + 12, 4):
        c.command(f'fill {X - 24} {y} {Z - 24} {X + 24} {y + 3} {Z + 24} minecraft:air')
    c.command(f'fill {X - 24} {Y - 1} {Z - 24} {X + 24} {Y - 1} {Z + 24} minecraft:stone_bricks')
    c.command('kill @e[type=orimod:gloom_beetle]')
    c.reset(X + .5, Y, Z + .5, 0, 10); c.ticks(5)

def summon(dz, yaw, ai=True):
    tag = '' if ai else ',NoAI:1b'
    c.command(f'summon orimod:gloom_beetle {X + .5} {Y} {Z + dz + .5} {{Rotation:[{yaw}f,0f]{tag}}}'); c.ticks(5)

def survival():
    c.command('gamemode survival'); c.command('ori energy fill'); c.ticks(4)

def stomp():
    c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True},
        {'at': 1, 'key': 'key.orimod.dash', 'down': True}, {'at': 3, 'key': 'key.orimod.dash', 'down': False},
        {'at': 4, 'key': 'key.sneak', 'down': False}])

def hit(entity_id):
    c.action('attack_entity', id=entity_id); c.ticks(12)

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
    c.command(f'tp @s {X} {Y + 20} {Z}'); c.ticks(60)

    if 'look' in only:
        pad(); summon(4, 180, ai=False)
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=3, y=2, z=2, targetHeight=.4); c.ticks(10); c.screenshot('beetle-day')
        c.command(f'time set {DAY * 24000 + 18000}'); c.ticks(10); c.screenshot('beetle-night')
        c.command(f'time set {DAY * 24000 + 6000}')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('settings', hideGui=False)

    if 'armor' in only:
        # Same blade, same beetle (NoAI, facing the player): a hit to the face vs a hit to the back.
        pad(); summon(2, 180, ai=False); survival()
        b = beetle(); h0 = b['health']
        c.reset(X + .5, Y, Z + .5, 0, 20); hit(b['id'])
        h1 = beetle()['health']
        c.reset(X + .5, Y, Z + 4.5, 180, 20); c.ticks(12); hit(b['id'])
        h2 = beetle()['health']
        front, back = h0 - h1, h1 - h2
        check('front_armor_reduces', front > 0 and back > 0 and front <= back * .3, {'front': front, 'back': back})

    if 'stomp' in only:
        pad(); summon(4, 180, ai=False); survival()
        c.command(f'tp @s {X + .5} {Y + 8} {Z + 4.5} 0 80'); c.ticks(3); stomp()
        state = None
        for _ in range(10):
            c.ticks(4); b = beetle(); state = b and b.get('state')
            if state == 'flipped': break
        check('stomp_flips_beetle', state == 'flipped', {'state': state})
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=3, y=2.5, z=-2, targetHeight=.3); c.ticks(6); c.screenshot('beetle-flipped')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('settings', hideGui=False)
        c.ticks(80)
        check('flip_recovers', (beetle() or {}).get('state') != 'flipped', beetle())

    if 'charge' in only:
        # Wall 3 blocks behind the player; the beetle faces the player 8 blocks away. On windup the player sidesteps:
        # the charge keeps its locked heading, misses and stuns itself on the wall; any blow then flips it.
        pad()
        # The charge covers at most 7 blocks: beetle 5 blocks in front, wall 1 block behind the player (6 away).
        c.command(f'fill {X - 6} {Y} {Z - 1} {X + 6} {Y + 2} {Z - 1} minecraft:stone_bricks')
        survival(); c.reset(X + .5, Y, Z + .5, 0, 5)
        summon(5, 180)
        states, windup_seen = [], False
        for _ in range(60):
            c.ticks(2); b = beetle(); s = b and b.get('state'); states.append(s)
            if s == 'windup' and not windup_seen:
                windup_seen = True; c.screenshot('beetle-windup')
                c.command(f'tp @s {X + 4.5} {Y} {Z + .5}')  # sidestep
            if s == 'stunned': break
        check('windup_before_charge', windup_seen and 'charging' in states, states)
        check('charge_hits_wall_and_stuns', states and states[-1] == 'stunned', states)
        b = beetle()
        if b:
            c.reset(b['position'][0], Y, b['position'][2] + 1.5, 180, 30)  # beside the stunned beetle, facing it
            hit(b['id'])
            check('stunned_hit_flips', (beetle() or {}).get('state') == 'flipped', beetle())

    if 'tide' in only:
        pad(); survival()
        c.command(f'time set {DAY * 24000 + 18000}'); c.command('gamerule doMobSpawning true')
        c.command('ori tide start')
        types = {}
        for _ in range(6):
            c.command(f'tp @s {X + .5} {Y} {Z + .5}'); c.ticks(210)
            for e in c.action('nearby_entities')['entities']:
                if 'gloom' in e['type']: types[e['type']] = types.get(e['type'], 0) + 1
            for t in ('gloom_spitter', 'gloom_hawk', 'gloom_beetle'): c.command(f'kill @e[type=orimod:{t}]')
        c.command('ori tide stop'); c.command('gamerule doMobSpawning false')
        check('tide_waves_include_beetles', any('gloom_beetle' in t for t in types), types)

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
