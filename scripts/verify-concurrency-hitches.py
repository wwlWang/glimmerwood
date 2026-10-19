"""PERF05 (cloud part): compare a quiet placeholder arena with key skills fired back to back among
many mobs. Records client frame times (record_frames) and integrated-server tick times (perf_sample).

The frozen FPS budget (avg >= 60, 1% low >= 40 on the registered machine) needs a real GPU; this script
asserts only what software rendering can judge: no multi-second stall (frame > 2 s) and no server tick
over 250 ms while skills overlap, and reports the busy/quiet frame-time ratio."""
import argparse, json, statistics, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--seconds', type=int, default=40)
p.add_argument('--mobs', type=int, default=16)
args = p.parse_args(); c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def build_arena():
    c.command('gamemode creative'); c.command(f'tp @s {X+.5} {Y+40} {Z+.5}'); c.ticks(60)
    for cmd in [f'fill {X-20} {Y-1} {Z-20} {X+20} {Y-1} {Z+20} minecraft:moss_block',
                f'fill {X-20} {Y} {Z-20} {X+20} {Y+16} {Z+20} minecraft:air', f'fill {X-20} {Y+17} {Z-20} {X+20} {Y+32} {Z+20} minecraft:air',
                'gamerule fallDamage false', 'gamerule doMobSpawning false', 'difficulty easy', 'gamerule doDaylightCycle false', 'time set 6000', 'weather clear']:
        c.command(cmd)
    assert c.command(f'execute if block {X+20} {Y-1} {Z+20} minecraft:moss_block')['result'], 'arena not built'
    c.command(f'tp @s {X+.5} {Y} {Z+.5} 0 0'); c.ticks(20); c.command('gamemode survival')

def reset(y=Y): c.action('release_keys'); c.action('close_screen'); c.command(f'tp @s {X+.5} {y} {Z+.5} 0 0'); c.ticks(3)

def burst():
    reset(); c.key('orimod.dash', 2); c.ticks(6)
    reset(); c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True}, {'at': 2, 'key': 'key.jump', 'down': False},
        {'at': 7, 'key': 'key.jump', 'down': True}, {'at': 9, 'key': 'key.jump', 'down': False}]); c.ticks(14)
    reset(Y + 14); c.key('jump', 50); c.key('forward', 30); c.ticks(24); c.action('release_keys')
    c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True}, {'at': 1, 'key': 'key.orimod.dash', 'down': True},
        {'at': 3, 'key': 'key.orimod.dash', 'down': False}, {'at': 4, 'key': 'key.sneak', 'down': False}]); c.ticks(30)
    reset(); c.command(f'setblock {X} {Y+4} {Z+4} orimod:grapple_flower'); c.action('look', yaw=0, pitch=-40)
    c.key('orimod.bash', 2); c.ticks(16); c.command(f'setblock {X} {Y+4} {Z+4} air')
    reset(); c.key('orimod.bash', 14); c.ticks(4); c.action('look', yaw=0, pitch=-35); c.action('release_keys'); c.ticks(12)

def mobs(n):
    c.command('kill @e[tag=orimod_perf05]')
    for i in range(n):
        dx, dz = (i % 4) * 3 - 4.5, (i // 4) * 3 + 3
        c.command(f'summon minecraft:husk {X+.5+dx} {Y} {Z+.5+dz} {{Tags:["orimod_perf05"],NoAI:1b,PersistenceRequired:1b,Silent:1b}}')

def stats(frames):
    s = sorted(frames); n = len(s); worst = s[-max(1, n // 100):]
    return {'frames': n, 'avgFps': 1000 / statistics.mean(s), 'onePercentLowFps': 1000 / statistics.mean(worst),
            'p99Ms': s[int(n * .99) - 1], 'maxMs': s[-1], 'over250ms': sum(f > 250 for f in s), 'over1000ms': sum(f > 1000 for f in s)}

def phase(name, busy):
    c.action('record_frames'); start = time.monotonic(); worst_tick = 0; ticks_avg = []
    while time.monotonic() - start < args.seconds:
        if busy: burst()
        else: c.ticks(20)
        s = c.action('perf_sample', gc=False); worst_tick = max(worst_tick, s['serverMsptMax']); ticks_avg.append(s['serverMsptAvg'])
    saved = c.action('save_frames', name=c.build_id + '-' + name)
    frames = json.load(open(saved['path']))['frameTimeMs']
    result = stats(frames) | {'serverMsptMax': worst_tick, 'serverMsptAvg': statistics.mean(ticks_avg)}
    c.records['phases'][name] = result; c.save(); print(name, json.dumps({k: round(v, 1) for k, v in result.items()}), flush=True)
    return result

try:
    c.action('focus_game'); c.action('close_screen'); c.records['initial'] = c.ready(); c.records['phases'] = {}
    build_arena()
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    for grant in ['grant_dash', 'grant_air_movement', 'grant_bash']: c.action(grant)
    c.action('shaders', enabled=True); c.ticks(60)
    quiet = phase('quiet', False)
    mobs(args.mobs); c.ticks(20); c.screenshot('busy-arena')
    busy = phase('busy', True)
    c.command('kill @e[tag=orimod_perf05]')
    c.records['busyQuietFrameRatio'] = quiet['avgFps'] / busy['avgFps']
    problems = [k for k, bad in [('multi-second stall', busy['maxMs'] > 2000), ('server tick > 250 ms', busy['serverMsptMax'] > 250)] if bad]
    c.records['problems'] = problems; assert not problems, (problems, busy)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('release_keys'); c.action('close_screen'); c.command('gamerule fallDamage true')
print(json.dumps(c.records.get('busyQuietFrameRatio')), c.out, flush=True)
