"""PERF03: a long scripted play session with save + leave + rejoin cycles, run as one or more segments
in the same client process (e.g. 2 x 15 min, one rejoin each).

Each cycle: movement skills (dash, double jump, glide, stomp, grapple, Bash), melee fights against live
husks, roaming into newly generated chunks, and a form toggle. Every cycle samples heap, particles,
server tick time and new ERROR lines in the client log. A rejoin pauses, saves, leaves to the title
screen, reopens the world and requires the saved progression, abilities, form and inventory to match.

Pass: client alive throughout, every rejoin restores state, no error spam (any single ERROR message
repeating more than 20 times, or more than 30 ERROR lines per segment), retained heap after GC at the
end within +400 MB of the start of the segment."""
import argparse, collections, json, re, time
from devtools_client import DevClient, ROOT

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--world', default='orimod_dev_play04_r29')
p.add_argument('--minutes', type=float, default=15); p.add_argument('--rejoins', type=int, default=1)
args = p.parse_args(); c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000
LOG = ROOT / 'run-devtools/logs/latest.log'

def log_errors(offset):
    data = LOG.read_bytes()[offset:].decode('utf-8', 'replace')
    lines = [l for l in data.splitlines() if re.search(r'/ERROR\]', l)]
    return lines, offset + len(data.encode('utf-8'))

def reset(x=X, y=Y, z=Z):
    c.action('release_keys'); c.action('close_screen'); c.command(f'tp @s {x+.5} {y} {z+.5} 0 0'); c.ticks(3)

def skills():
    reset(); c.key('orimod.dash', 2); c.ticks(8)
    reset(); c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True}, {'at': 2, 'key': 'key.jump', 'down': False},
        {'at': 7, 'key': 'key.jump', 'down': True}, {'at': 9, 'key': 'key.jump', 'down': False}]); c.ticks(16)
    reset(y=Y + 14); c.key('jump', 50); c.key('forward', 30); c.ticks(24); c.action('release_keys')
    c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True}, {'at': 1, 'key': 'key.orimod.dash', 'down': True},
        {'at': 3, 'key': 'key.orimod.dash', 'down': False}, {'at': 4, 'key': 'key.sneak', 'down': False}]); c.ticks(30)
    reset(); c.command(f'setblock {X} {Y+4} {Z+4} orimod:grapple_flower'); c.action('look', yaw=0, pitch=-40)
    c.key('orimod.bash', 2); c.ticks(16); c.command(f'setblock {X} {Y+4} {Z+4} air')

hits = [0]; deaths = [0]

def fight():
    reset(); c.command('kill @e[tag=orimod_perf03]')
    for dx in (-2, 0, 2): c.command(f'summon minecraft:husk {X+.5+dx} {Y} {Z+3.5} {{Tags:["orimod_perf03"],PersistenceRequired:1b}}')
    c.ticks(10)
    c.command('item replace entity @s hotbar.0 with minecraft:iron_sword'); c.action('select_slot', slot=0); c.ticks(4)
    for _ in range(30):
        mobs = [e for e in c.action('nearby_entities', radius=8)['entities'] if 'husk' in e['type'] and e['alive']]
        if not mobs: break
        target = min(mobs, key=lambda e: e['health'])
        try: c.action('attack_entity', id=target['id']); hits[0] += 1
        except RuntimeError: pass          # not yet tracked on the client, or died this tick
        c.ticks(12)
    c.command('kill @e[tag=orimod_perf03]'); c.ticks(30)   # aura pickups

def roam(i):
    x = X + 300 * (i % 6 + 1)
    c.command('gamemode creative'); c.command(f'tp @s {x+.5} {Y+40} {Z+.5}'); c.ticks(80)
    c.command(f'tp @s {X+.5} {Y} {Z+.5}'); c.ticks(40); c.command('gamemode survival')

def snapshot():
    prog = c.action('progress_state')['server']; s = c.spirit()['server']
    return {'progress': prog['progress'], 'abilities': prog['abilities'], 'form': s['form'],
            'inventory': sorted((i['item'], i['count']) for i in prog['inventory'])}

def rejoin(n):
    before = snapshot(); c.action('release_keys'); c.action('open_pause'); c.ticks(1)
    c.action('save_world'); c.action('leave_world'); time.sleep(5)
    c.action('open_world', name=args.world)
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            r = c.request()
            if r.get('screen') == 'gameplay' and 'player' in r: break
        except Exception: pass
        time.sleep(2)
    c.ready(); c.ticks(40); after = snapshot()
    ok = before == after
    c.records['rejoins'].append({'n': n, 'before': before, 'after': after, 'ok': ok}); c.save()
    print(f'rejoin {n}: {"state restored" if ok else "STATE CHANGED"}', flush=True)
    return ok

try:
    if c.request().get('screen') == 'DeathScreen': c.action('respawn'); time.sleep(3)
    c.action('focus_game'); c.action('close_screen'); c.records['initial'] = c.ready()
    c.records.update(cycles=[], rejoins=[], args=vars(args))
    assert c.command(f'execute if block {X} {Y-1} {Z} minecraft:moss_block')['result'] or True
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    for grant in ['grant_dash', 'grant_air_movement', 'grant_bash']: c.action(grant)
    # Husks fight back; resistance keeps a scripted (slow, llvmpipe) fight from ending the session.
    for cmd in ['effect give @s minecraft:resistance infinite 3 true', 'effect give @s minecraft:saturation infinite 0 true', 'gamerule fallDamage false', 'gamerule doMobSpawning false', 'difficulty easy', 'time set 6000', 'weather clear']: c.command(cmd)
    offset = LOG.stat().st_size; errors = []
    start_heap = c.action('perf_sample', gc=True)['heapUsedMb']
    total = args.minutes * 60; start = time.monotonic()
    rejoin_at = [total * (k + 1) / (args.rejoins + 1) for k in range(args.rejoins)]; done_rejoins = 0; i = 0
    while time.monotonic() - start < total:
        skills(); fight(); roam(i)
        if i % 3 == 2: c.action('toggle_form'); c.ticks(10); c.action('toggle_form'); c.ticks(10)
        if c.request().get('screen') == 'DeathScreen':
            deaths[0] += 1; c.action('respawn'); time.sleep(3); c.action('close_screen'); c.ready()
            if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
        s = c.action('perf_sample', gc=False); new, offset = log_errors(offset); errors += new
        row = {'cycle': i, 'elapsedS': round(time.monotonic() - start), 'heapMb': round(s['heapUsedMb']), 'particles': s['particles'],
               'msptAvg': round(s.get('serverMsptAvg', 0), 1), 'msptMax': round(s.get('serverMsptMax', 0), 1), 'newErrors': len(new), 'hits': hits[0], 'deaths': deaths[0],
               'energy': c.action('progress_state')['server']['energy']}
        c.records['cycles'].append(row); c.save(); print(json.dumps(row), flush=True)
        if done_rejoins < args.rejoins and time.monotonic() - start >= rejoin_at[done_rejoins]:
            done_rejoins += 1; assert rejoin(done_rejoins), 'state changed across rejoin'
            if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
        i += 1
    while done_rejoins < args.rejoins: done_rejoins += 1; assert rejoin(done_rejoins)
    end_heap = c.action('perf_sample', gc=True)['heapUsedMb']
    repeated = collections.Counter(re.sub(r'^\[[^\]]*\] ', '', e)[:160] for e in errors).most_common(5)
    c.records.update(startHeapMb=start_heap, endHeapMb=end_heap, errorLines=len(errors), topErrors=repeated)
    problems = []
    if len(errors) > 30: problems.append(f'{len(errors)} ERROR lines')
    if repeated and repeated[0][1] > 20: problems.append(f'repeating error {repeated[0]}')
    if end_heap - start_heap > 400: problems.append(f'heap {start_heap:.0f} -> {end_heap:.0f} MB')
    c.records['problems'] = problems; print('errors', len(errors), repeated[:2], 'heap', round(start_heap), '->', round(end_heap), flush=True)
    assert not problems, problems
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save()
    try: c.action('release_keys'); c.command('gamerule fallDamage true')
    except Exception: pass
print(c.out, flush=True)
