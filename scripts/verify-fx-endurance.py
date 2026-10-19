"""PERF02: release and end spirit effects repeatedly, leave and return to the same area, and sample
live effects, loop sounds, particles and retained heap (after GC) every round.

Pass: after each round no persistent effect or loop remains, particles fall back near the idle level,
and after two warm-up rounds retained heap does not keep growing round over round."""
import argparse
import json
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--rounds', type=int, default=10)
args = p.parse_args(); c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000           # the MOVE12 arena (built by verify-movement-endurance.py when missing)
FAR = (X + 900, Z + 900)

def sample(gc=True):
    return c.action('perf_sample', gc=gc)

def reset(y=Y, z=Z + .5):
    c.action('release_keys'); c.action('close_screen'); c.command(f'tp @s {X + .5} {y} {z} 0 0'); c.ticks(4)

def burst_round():
    reset(); c.key('orimod.dash', 2); c.ticks(10)                                   # dash
    reset(y=Y + 22); c.key('jump', 80); c.key('forward', 40); c.ticks(30); c.action('release_keys'); c.ticks(40)   # glide start/stop
    reset(y=Y + 12); c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True},
        {'at': 1, 'key': 'key.orimod.dash', 'down': True}, {'at': 3, 'key': 'key.orimod.dash', 'down': False},
        {'at': 4, 'key': 'key.sneak', 'down': False}]); c.ticks(40)                  # stomp + impact
    reset(); c.command(f'setblock {X} {Y + 4} {Z + 4} orimod:grapple_flower'); c.action('look', yaw=0, pitch=-40)
    c.key('orimod.bash', 2); c.ticks(20); c.command(f'setblock {X} {Y + 4} {Z + 4} air')   # grapple
    reset(); c.command('kill @e[tag=orimod_perf02]')
    c.command(f'summon minecraft:husk {X + .5} {Y} {Z + 4.8} {{Tags:["orimod_perf02"],NoAI:1b,PersistenceRequired:1b,Silent:1b}}'); c.ticks(1)
    c.key('orimod.bash', 14); c.ticks(4); c.action('look', yaw=0, pitch=-35); c.action('release_keys'); c.ticks(20)   # bash
    c.command('kill @e[tag=orimod_perf02]')
    for _ in range(3):                                                             # double jumps
        reset(); c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True}, {'at': 2, 'key': 'key.jump', 'down': False},
            {'at': 7, 'key': 'key.jump', 'down': True}, {'at': 9, 'key': 'key.jump', 'down': False}]); c.ticks(30)

try:
    c.action('focus_game'); c.action('close_screen'); c.records['initial'] = c.ready()
    c.command('gamemode creative'); c.command(f'tp @s {X + .5} {Y + 40} {Z + .5}'); c.ticks(60)
    assert c.command(f'execute if block {X} {Y - 1} {Z} minecraft:moss_block')['result'], 'run verify-movement-endurance.py first to build the arena'
    for cmd in ['gamerule fallDamage false', 'gamerule doMobSpawning false', 'difficulty easy', 'time set 6000', 'weather clear']: c.command(cmd)
    c.command(f'tp @s {X + .5} {Y} {Z + .5}'); c.ticks(20); c.command('gamemode survival')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    for grant in ['grant_dash', 'grant_air_movement', 'grant_bash']: c.action(grant)
    c.ticks(100); idle = sample(); c.records['idle'] = idle
    rounds = []
    for r in range(args.rounds):
        burst_round()
        during = sample(gc=False)
        c.command('gamemode creative'); c.command(f'tp @s {FAR[0] + .5} {Y + 40} {FAR[1] + .5}'); c.ticks(120)   # leave: area unloads
        c.command(f'tp @s {X + .5} {Y} {Z + .5}'); c.ticks(80); c.command('gamemode survival'); c.ticks(40)   # return
        after = sample()
        rounds.append({'round': r, 'during': during, 'after': after})
        life = after['fxLifecycle']
        print(f"round {r}: heap {after['heapUsedMb']:.0f} MB, particles {during['particles']}->{after['particles']}, "
              f"active {len(life['active'])}, loops {life['loops']}, started {life['started']} stopped {life['stopped']}", flush=True)
        c.records['rounds'] = rounds; c.save()
    problems = []
    for x in rounds:
        a = x['after']
        if a['fxLifecycle']['active'] or a['fxLifecycle']['loops']: problems.append(('effects left', x['round'], a['fxLifecycle']))
        if int(a['particles']) > int(idle['particles']) + 50: problems.append(('particles', x['round'], a['particles']))
    heap = [x['after']['heapUsedMb'] for x in rounds[2:]]
    n = len(heap); mean_x = (n - 1) / 2; mean_y = sum(heap) / n
    slope = sum((i - mean_x) * (h - mean_y) for i, h in enumerate(heap)) / sum((i - mean_x) ** 2 for i in range(n))
    c.records['heapSlopeMbPerRound'] = slope; c.records['heapAfterWarmup'] = heap
    if slope > 8: problems.append(('heap grows', slope, heap))
    started = rounds[-1]['after']['fxLifecycle']['started'] - idle['fxLifecycle']['started']
    if started < args.rounds: problems.append(('too few persistent effects started', started))
    c.records['problems'] = problems
    print('heap slope after warm-up: %.2f MB/round' % slope, flush=True)
    assert not problems, problems
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('release_keys'); c.action('close_screen'); c.command('gamerule fallDamage true')
print(c.out, flush=True)
