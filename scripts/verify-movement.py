"""Live movement trials through ordinary key mappings; retain both server and client tick traces."""
import argparse
import json
from pathlib import Path
import shutil
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('build_id')
args = parser.parse_args()
assert args.build_id.replace('-', '').replace('_', '').isalnum()
OUT = ROOT / 'evidence' / args.build_id
OUT.mkdir(parents=True, exist_ok=False)
connection = json.loads((ROOT / 'run-devtools/devtools/connection.json').read_text())

def request(action=None):
    req = urllib.request.Request(connection['url'] + ('/action' if action else '/state'),
        data=json.dumps(action).encode() if action else None,
        headers={'X-Ori-Devtools': connection['token'], 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=55) as response:
        return json.load(response)

def command(text):
    request({'op': 'command', 'command': text})

def state():
    return request({'op': 'spirit_state'})

def key(at, name, down):
    return {'at': at, 'key': 'key.' + name, 'down': down}

def trial(name, steps, seconds=2.6):
    before = state()
    request({'op': 'record_motion'})
    request({'op': 'input_script', 'steps': steps})
    time.sleep(seconds)
    request({'op': 'release_keys'})
    after = state()
    result = request({'op': 'save_motion', 'name': args.build_id + '-' + name})
    shutil.copy2(result['path'], OUT / (name + '.json'))
    trace = json.loads((OUT / (name + '.json')).read_text())
    records[name] = {'before': before, 'after': after, 'samples': result['samples']}
    save()
    return before['server'], after['server'], trace

def save():
    (OUT / 'results.json').write_text(json.dumps(records, indent=2))

def reset(x=.5, y=100, z=.5, yaw=0):
    request({'op': 'release_keys'})
    command(f'tp @s {x} {y} {z} {yaw} 0')
    command('effect give @s minecraft:instant_health 1 5 true')
    time.sleep(.6)

records = {'status': 'running', 'build_id': args.build_id, 'fall_damage': 'disabled for high-platform control trials; restored afterwards'}
try:
    deadline = time.monotonic() + 60
    while True:
        initial = request()
        if initial['screen'] == 'gameplay' and initial['overlay'] == 'none' and 'player' in initial:
            break
        assert time.monotonic() < deadline, initial
        time.sleep(.5)
    assert initial['windowActive'], 'Game must be focused; test does not bypass input guards'
    records['initial'] = initial
    assert state()['server']['form'], 'Use the awakened form test save'
    for cmd in ['gamemode survival', 'difficulty peaceful', 'gamerule fallDamage false', 'gamerule doDaylightCycle false', 'time set 9000',
                'fill -20 100 -20 20 116 20 air', 'fill -20 99 -20 20 99 20 moss_block',
                'fill -8 100 12 8 130 12 stone', 'fill 8 100 7 16 105 7 glass_pane',
                'fill -2 124 -2 2 124 0 moss_block']:
        command(cmd)
    reset()
    before, after, trace = trial('double-jump', [key(0, 'jump', True), key(2, 'jump', False),
        key(7, 'jump', True), key(9, 'jump', False), key(12, 'jump', True), key(14, 'jump', False)])
    client_heights = [r['state']['position'][1] for r in trace['samples'] if r['side'] == 'client']
    assert after['movement']['jumps'] - before['movement']['jumps'] == 2, (before, after)
    assert max(client_heights) > 102, max(client_heights)
    assert after['position'][1] == 100, after
    print('double jump: two accepted jumps, third rejected, landed', flush=True)

    reset()
    request({'op': 'grant_dash'})
    time.sleep(.2)
    before, after, trace = trial('ground-dash', [key(0, 'orimod.dash', True), key(2, 'orimod.dash', False)], 1.4)
    assert after['movement']['dashes'] - before['movement']['dashes'] == 1, (before, after)
    assert 4 < after['position'][2] - before['position'][2] < 9, (before, after)
    print('ground dash: fixed burst and clean exit', flush=True)

    reset(12.5, 100, 3.5)
    before, after, trace = trial('thin-wall', [key(0, 'orimod.dash', True), key(2, 'orimod.dash', False)], 1.4)
    positions = [r['state']['position'] for r in trace['samples']]
    assert max(p[2] for p in positions) < 7.3, max(p[2] for p in positions)
    assert after['movement']['mode'] == 'idle', after
    print('dash collision: thin glass pane blocks both sides', flush=True)

    reset(.5, 125, .5)
    before, after, trace = trial('air-dash-charge', [key(0, 'jump', True), key(2, 'jump', False),
        key(4, 'orimod.dash', True), key(6, 'orimod.dash', False),
        key(17, 'orimod.dash', True), key(19, 'orimod.dash', False)], 1.6)
    assert after['movement']['dashes'] - before['movement']['dashes'] == 1, (before, after)
    assert after['movement']['rejectedDashes'] - before['movement']['rejectedDashes'] == 1, (before, after)
    print('air dash: second burst rejected after cooldown while still airborne', flush=True)

    reset(.5, 124, 11.7)
    before, after, trace = trial('wall-slide-jump', [key(0, 'forward', True),
        key(14, 'jump', True), key(16, 'jump', False), key(55, 'forward', False)], 3.1)
    assert after['movement']['wallJumps'] - before['movement']['wallJumps'] == 1, (before, after)
    wall_frames = [r for r in trace['samples'] if r['side'] == 'client' and r['state']['movement']['mode'] == 'wall_slide']
    assert len(wall_frames) >= 6, len(wall_frames)
    assert min(r['state']['position'][2] for r in trace['samples'] if r['side'] == 'client') < 11, 'wall jump did not leave the wall'
    print('wall: steady slide, jump pushes away, release restores falling', flush=True)
    records['status'] = 'initial_trials_passed'
    save()
except Exception as error:
    records['status'] = 'failed'
    records['error'] = repr(error)
    save()
    raise
finally:
    request({'op': 'release_keys'})
    command('tp @s .5 100 .5')
    time.sleep(.2)
    command('gamerule fallDamage true')
print(OUT, flush=True)
