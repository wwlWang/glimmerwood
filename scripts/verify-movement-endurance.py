"""MOVE12: 100 seeded mixed movements and cancellations through the real key mappings.

After every action the player must come back to a clean state on both sides: idle mode, grounded,
no pending predictions, air jump/dash refilled, inside the arena and with bounded counter deltas
(no stuck takeover, floating, endless dash or wrong cooldown)."""
import argparse
import json
import random
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--count', type=int, default=100)
p.add_argument('--seed', type=int, default=20261019)
args = p.parse_args(); c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000          # arena floor top; far from the scenery and ruins
rng = random.Random(args.seed)
FLOWER = (X, Y + 4, Z + 4)

def mv(side='server'):
    return c.spirit()[side]['movement']

def reset(x=X + .5, y=Y, z=Z + .5, yaw=0, pitch=0):
    c.action('release_keys'); c.action('close_screen')
    c.command(f'tp @s {x} {y} {z} {yaw} {pitch}')
    c.ticks(4)

def stomp():
    c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True},
        {'at': 1, 'key': 'key.orimod.dash', 'down': True}, {'at': 3, 'key': 'key.orimod.dash', 'down': False},
        {'at': 4, 'key': 'key.sneak', 'down': False}])

def husk():
    c.command('kill @e[tag=orimod_move12]')
    c.command(f'summon minecraft:husk {X + .5} {Y} {Z + 4.8} {{Tags:["orimod_move12"],NoAI:1b,PersistenceRequired:1b,Silent:1b}}')
    c.ticks(1)

# Each action starts from a reset, issues real key input and may cancel part-way.
def a_jump():           reset(); c.key('jump', 2); c.ticks(4)
def a_double_jump():    reset(); c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True}, {'at': 2, 'key': 'key.jump', 'down': False}, {'at': 7, 'key': 'key.jump', 'down': True}, {'at': 9, 'key': 'key.jump', 'down': False}]); c.ticks(12)
def a_ground_dash():    reset(yaw=rng.choice([0, 90, 180, 270])); c.key('orimod.dash', 2); c.ticks(6)
def a_air_dash():       reset(y=Y + 12); c.action('input_script', steps=[{'at': 0, 'key': 'key.orimod.dash', 'down': True}, {'at': 2, 'key': 'key.orimod.dash', 'down': False}, {'at': 10, 'key': 'key.orimod.dash', 'down': True}, {'at': 12, 'key': 'key.orimod.dash', 'down': False}]); c.ticks(14)
def a_dash_wall():      reset(z=Z + 9.5); c.key('orimod.dash', 2); c.ticks(8)
def a_dash_teleport():  reset(); c.key('orimod.dash', 2); c.ticks(2); c.command(f'tp @s {X + 5.5} {Y} {Z - 4.5}'); c.ticks(4)
def glide(cancel):
    reset(y=Y + 22); c.key('jump', 120); c.key('forward', 40); c.ticks(rng.randint(10, 24))
    if cancel == 'release': c.action('release_keys')
    elif cancel == 'crouch': c.key('sneak', 20)
    elif cancel == 'menu': c.action('open_inventory'); c.ticks(3); c.action('close_screen')
def a_glide_release():  glide('release')
def a_glide_crouch():   glide('crouch')
def a_glide_menu():     glide('menu')
def grapple(cancel):
    reset(); c.command('setblock %d %d %d orimod:grapple_flower' % FLOWER); c.action('look', yaw=0, pitch=-40)
    c.key('orimod.bash', 2); c.ticks(2)
    if cancel == 'removed': c.command('setblock %d %d %d air' % FLOWER)
    elif cancel == 'crouch': c.key('sneak', 6)
    elif cancel == 'menu': c.action('open_inventory'); c.ticks(2); c.action('close_screen')
    c.ticks(12); c.command('setblock %d %d %d air' % FLOWER)
def a_grapple():        grapple(None)
def a_grapple_removed(): grapple('removed')
def a_grapple_crouch(): grapple('crouch')
def a_grapple_menu():   grapple('menu')
def a_stomp():          reset(y=Y + 12); stomp(); c.ticks(3)
def a_stomp_menu():     reset(y=Y + 12); stomp(); c.ticks(3); c.action('open_inventory'); c.ticks(2); c.action('close_screen')
def a_wall_jump():      reset(z=Z + 11.7, y=Y + 24); c.action('input_script', steps=[{'at': 0, 'key': 'key.forward', 'down': True}, {'at': 14, 'key': 'key.jump', 'down': True}, {'at': 16, 'key': 'key.jump', 'down': False}, {'at': 40, 'key': 'key.forward', 'down': False}]); c.ticks(42)
def a_form_midair():    reset(y=Y + 10); c.ticks(2); c.action('toggle_form'); c.ticks(4); c.action('toggle_form'); c.ticks(4)
def bash(cancel):
    reset(); husk(); c.key('orimod.bash', 14); c.ticks(4)
    if cancel: c.key('sneak', 6); c.ticks(6)
    else: c.action('look', yaw=0, pitch=-35); c.action('release_keys'); c.ticks(10)
    c.command('kill @e[tag=orimod_move12]')
def a_bash():           bash(False)
def a_bash_cancel():    bash(True)

ACTIONS = [a_jump, a_double_jump, a_ground_dash, a_air_dash, a_dash_wall, a_dash_teleport, a_glide_release,
           a_glide_crouch, a_glide_menu, a_grapple, a_grapple_removed, a_grapple_crouch, a_grapple_menu,
           a_stomp, a_stomp_menu, a_wall_jump, a_form_midair, a_bash, a_bash_cancel]
COUNTERS = ['jumps', 'dashes', 'glides', 'grapples', 'stomps', 'bashes', 'wallJumps', 'completedGrapples',
            'cancelledGrapples', 'cancelledBashes', 'rejectedDashes', 'stompImpacts', 'predictionRejections']

def settle(limit=240):
    for _ in range(limit // 4):
        s = c.spirit()
        if all(s[side]['movement']['grounded'] and s[side]['movement']['mode'] == 'idle' for side in ('server', 'client')):
            c.ticks(4)
            return c.spirit()
        c.ticks(4)
    return c.spirit()

def check(name, before, after):
    problems = []
    for side in ('server', 'client'):
        m = after[side]['movement']
        if m['mode'] != 'idle': problems.append(f'{side} mode {m["mode"]}')
        if not m['grounded']: problems.append(f'{side} not grounded')
        if m.get('airDashSpent') or m.get('airJumpSpent'): problems.append(f'{side} air move not refilled')
    ms = after['server']['movement']
    if ms['pendingPredictions'] or ms['pendingJumps']: problems.append('pending predictions')
    x, y, z = after['server']['position']
    if not (X - 19 < x < X + 20 and Z - 19 < z < Z + 20 and Y - .01 <= y < Y + 31): problems.append(f'outside arena {after["server"]["position"]}')
    if abs(ms['velocity'][1]) > .1: problems.append(f'vertical velocity {ms["velocity"][1]}')
    if not after['server']['form']: problems.append('left spirit form')
    if after['server']['health'] <= 0: problems.append('dead')
    delta = {k: ms[k] - before['server']['movement'][k] for k in COUNTERS}
    if delta['dashes'] > 2 or delta['jumps'] > 3: problems.append(f'counter jump {delta}')
    return delta, problems

try:
    c.action('focus_game'); c.action('close_screen'); c.records['initial'] = c.ready(); c.action('release_keys')
    # llvmpipe at ~10 FPS sends movement input in bursts; a gap over 4 server ticks is treated as stale input
    # and ends grapple/bash. Shaders off and a short view keep the client near 20 ticks/s like a real GPU.
    c.action('shaders', enabled=False); c.action('settings', renderDistance=6); c.ticks(40)
    c.command('gamemode creative'); c.command(f'tp @s {X+.5} {Y+40} {Z+.5}'); c.ticks(60)   # load the chunks before filling
    for cmd in [f'fill {X-20} {Y-1} {Z-20} {X+20} {Y-1} {Z+20} minecraft:moss_block',
                f'fill {X-20} {Y} {Z-20} {X+20} {Y+16} {Z+20} minecraft:air', f'fill {X-20} {Y+17} {Z-20} {X+20} {Y+32} {Z+20} minecraft:air',
                f'fill {X-20} {Y} {Z-20} {X+20} {Y+16} {Z-20} minecraft:glass', f'fill {X-20} {Y} {Z+20} {X+20} {Y+16} {Z+20} minecraft:glass',
                f'fill {X-20} {Y} {Z-20} {X-20} {Y+16} {Z+20} minecraft:glass', f'fill {X+20} {Y} {Z-20} {X+20} {Y+16} {Z+20} minecraft:glass',
                f'fill {X-4} {Y} {Z+12} {X+4} {Y+30} {Z+12} minecraft:stone',
                'gamerule fallDamage false', 'gamerule doMobSpawning false', 'difficulty easy', 'gamerule doDaylightCycle false', 'time set 6000', 'weather clear']:
        c.command(cmd)
    for probe in [f'{X-20} {Y-1} {Z-20} minecraft:moss_block', f'{X+20} {Y-1} {Z+20} minecraft:moss_block', f'{X} {Y+20} {Z+12} minecraft:stone']:
        assert c.command('execute if block ' + probe)['result'], 'arena not built: ' + probe
    c.command(f'tp @s {X+.5} {Y} {Z+.5}'); c.ticks(20); c.command('gamemode survival')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    for grant in ['grant_dash', 'grant_air_movement', 'grant_bash']: c.action(grant)
    plan = [ACTIONS[i % len(ACTIONS)] for i in range(len(ACTIONS))]   # every kind at least once
    plan += [rng.choice(ACTIONS) for _ in range(args.count - len(plan))]
    rng.shuffle(plan)
    c.records['seed'] = args.seed; c.records['plan'] = [f.__name__[2:] for f in plan]; c.records['actions'] = []
    totals = {k: 0 for k in COUNTERS}; failures = []
    for i, action in enumerate(plan):
        settle(); before = c.spirit()
        action(); c.action('release_keys')
        after = settle()
        delta, problems = check(action.__name__, before, after)
        for k in COUNTERS: totals[k] += delta[k]
        c.records['actions'].append({'i': i, 'action': action.__name__[2:], 'delta': delta, 'problems': problems,
                                     'position': after['server']['position'], 'mode': after['server']['movement']['mode']})
        if problems: failures.append((i, action.__name__[2:], problems))
        print(f'{i:3d} {action.__name__[2:]:16s} {"OK" if not problems else problems}', flush=True)
        if i % 10 == 9: c.save()
    c.records['totals'] = totals; c.records['failures'] = failures
    for k in ['jumps', 'dashes', 'glides', 'grapples', 'stomps', 'bashes', 'wallJumps']:
        assert totals[k] > 0, f'{k} never happened: {totals}'
    assert not failures, failures
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.action('shaders', enabled=True); c.action('settings', renderDistance=c.records['initial']['renderDistance'])
    c.save(); c.action('release_keys'); c.action('close_screen'); c.command('gamerule fallDamage true')
print(json.dumps(c.records.get('totals')), c.out, flush=True)
