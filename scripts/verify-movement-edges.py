"""Boundary trials for coyote time, menu cancellation and charge persistence in a live client."""
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

def script(steps):
    request({'op': 'input_script', 'steps': steps})

def wait_ticks(count):
    target = request()['tick'] + count
    while request()['tick'] < target:
        time.sleep(.012)

def reset(x=.5, y=100, z=.5):
    request({'op': 'release_keys'})
    command(f'tp @s {x} {y} {z} 0 0')
    command('effect give @s minecraft:instant_health 1 5 true')
    wait_ticks(8)

def start():
    request({'op': 'record_motion'})
    return state()

def finish(name, before):
    after = state()
    result = request({'op': 'save_motion', 'name': args.build_id + '-' + name})
    shutil.copy2(result['path'], OUT / (name + '.json'))
    records[name] = {'before': before, 'after': after, 'samples': result['samples']}
    save()
    return after, json.loads((OUT / (name + '.json')).read_text())

def save():
    (OUT / 'results.json').write_text(json.dumps(records, indent=2))

records = {'status': 'running', 'build_id': args.build_id, 'fall_damage': 'disabled for high-platform control trials; restored afterwards'}
try:
    deadline = time.monotonic() + 60
    while request().get('screen') != 'gameplay' or 'player' not in request():
        assert time.monotonic() < deadline
        time.sleep(.5)
    assert request()['windowActive']
    assert state()['server']['form']
    command('gamerule fallDamage false')
    command('fill -2 124 -2 2 124 0 moss_block')
    for delay, expected in [(1, 'jump'), (3, 'jump'), (4, 'double_jump'), (5, 'double_jump')]:
        reset(.5, 125, 1.28)
        before = start()
        script([key(0, 'forward', True), key(delay, 'jump', True), key(delay + 2, 'jump', False), key(12, 'forward', False)])
        wait_ticks(17)
        after, trace = finish('coyote-' + str(delay), before)
        new_actions = [s['state']['movement']['lastAction'] for s in trace['samples'] if s['side'] == 'server'
                       and s['state']['movement']['jumps'] > before['server']['movement']['jumps']]
        assert expected in new_actions, (expected, new_actions)
        print(f'coyote {delay} ticks: {expected}', flush=True)

    for delay, expected_count in [(18, 2), (20, 3)]:
        reset()
        before = start()
        script([key(0, 'jump', True), key(2, 'jump', False), key(7, 'jump', True), key(9, 'jump', False),
                key(delay, 'jump', True), key(delay + 3, 'jump', False)])
        wait_ticks(38)
        after, trace = finish('landing-buffer-' + str(delay), before)
        actual = after['server']['movement']['jumps'] - before['server']['movement']['jumps']
        assert actual == expected_count, (delay, expected_count, actual)
        print(f'landing input at {delay}: {actual} total jumps', flush=True)

    command('fill -8 131 12 8 160 12 stone')
    reset(.5, 145, 11.7)
    before = start()
    script([key(0, 'forward', True), key(14, 'jump', True), key(16, 'jump', False),
            key(18, 'orimod.dash', True), key(20, 'orimod.dash', False),
            key(30, 'jump', True), key(32, 'jump', False),
            key(34, 'orimod.dash', True), key(36, 'orimod.dash', False), key(45, 'forward', False)])
    wait_ticks(48)
    after, trace = finish('same-wall-no-refill', before)
    assert after['server']['movement']['wallJumps'] - before['server']['movement']['wallJumps'] == 2, (before, after)
    assert after['server']['movement']['dashes'] - before['server']['movement']['dashes'] == 1, (before, after)
    assert after['server']['movement']['rejectedDashes'] > before['server']['movement']['rejectedDashes'], (before, after)
    print('same wall: a second wall jump does not refill the spent dash', flush=True)

    reset()
    for menu in ['open_inventory', 'open_pause']:
        before = start()
        request({'op': menu})
        request({'op': 'key', 'name': 'key.jump', 'ticks': 35})
        request({'op': 'key', 'name': 'key.orimod.dash', 'ticks': 35})
        wait_ticks(5)
        request({'op': 'close_screen', 'keepKeys': True})
        wait_ticks(5)
        request({'op': 'release_keys'})
        wait_ticks(2)
        after, trace = finish(menu, before)
        for count in ['jumps', 'dashes']:
            assert after['server']['movement'][count] == before['server']['movement'][count], (menu, before, after)
        assert after['server']['position'] == before['server']['position'], (menu, before, after)
        print(menu + ': held skills do not fire on return', flush=True)

    reset(.5, 125, .5)
    before = start()
    script([key(0, 'jump', True), key(2, 'jump', False), key(4, 'orimod.dash', True), key(6, 'orimod.dash', False)])
    wait_ticks(9)
    request({'op': 'toggle_form'})
    wait_ticks(2)
    request({'op': 'toggle_form'})
    wait_ticks(4)
    request({'op': 'key', 'name': 'key.orimod.dash', 'ticks': 2})
    wait_ticks(4)
    after, trace = finish('air-form-no-refill', before)
    assert after['server']['movement']['dashes'] - before['server']['movement']['dashes'] == 1, (before, after)
    assert after['server']['movement']['rejectedDashes'] > before['server']['movement']['rejectedDashes'], (before, after)
    assert after['server']['form'] and after['server']['position'][1] > 100, after
    print('air form changes: no extra dash charge', flush=True)

    records['status'] = 'passed'
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
