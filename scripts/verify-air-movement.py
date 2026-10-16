"""Live glide, grapple, stomp and fall-rule checks via real key mappings."""
import argparse
import json
from devtools_client import DevClient

parser = argparse.ArgumentParser()
parser.add_argument('build_id')
c = DevClient(parser.parse_args().build_id)

def trace(name, side='server'):
    return [v['state'] for v in json.loads((c.out / (name + '.json')).read_text())['samples'] if v['side'] == side]

def settle(max_ticks=130):
    for _ in range(max_ticks // 5):
        if c.spirit()['server']['movement']['grounded']:
            return
        c.ticks(5)
    raise AssertionError('Player did not land')

def stomp():
    c.action('input_script', steps=[{'at': 0, 'key': 'key.sneak', 'down': True},
        {'at': 1, 'key': 'key.orimod.dash', 'down': True}, {'at': 3, 'key': 'key.orimod.dash', 'down': False},
        {'at': 4, 'key': 'key.sneak', 'down': False}])

try:
    c.records['initial'] = c.ready()
    c.action('grant_air_movement')
    for command in ['gamerule fallDamage true', 'fill -20 99 -10 20 99 20 moss_block',
                    'fill -3 100 -3 3 130 10 air', 'difficulty peaceful']:
        c.command(command)
    c.reset(y=125)
    before = c.start()
    c.key('jump', 100)
    c.key('forward', 26)
    c.ticks(25)
    middle = c.spirit()
    c.action('look', yaw=90, pitch=0)
    c.key('forward', 20)
    c.ticks(18)
    held = c.spirit()
    c.action('release_keys')
    c.ticks(10)
    after = c.finish('glide-steer-release', before, middle=middle, held=held)
    assert held['server']['movement']['glides'] > before['server']['movement']['glides'], held
    assert 1 < before['server']['position'][1] - held['server']['position'][1] < 9, held
    assert held['server']['position'][0] < middle['server']['position'][0] - 1, held
    assert after['server']['movement']['mode'] != 'glide', after
    assert after['server']['position'][1] < held['server']['position'][1] - 2, after
    print('Glide: descent cap, steering and release passed', flush=True)

    for reason in ['arrival', 'removed', 'blocked', 'crouch', 'menu']:
        c.reset()
        c.command('setblock 0 104 4 orimod:grapple_flower')
        c.action('look', yaw=0, pitch=-40)
        before = c.start()
        c.key('orimod.bash', 2)
        c.ticks(2)
        if reason == 'removed': c.command('setblock 0 104 4 air')
        elif reason == 'blocked': c.command('fill -1 100 2 1 105 2 stone')
        elif reason == 'crouch': c.key('sneak', 6)
        elif reason == 'menu': c.action('open_inventory')
        c.ticks(12)
        if reason == 'menu': c.action('close_screen')
        after = c.finish('grapple-' + reason, before)
        assert after['server']['movement']['grapples'] > before['server']['movement']['grapples'], after
        if reason == 'arrival':
            assert after['server']['movement']['completedGrapples'] > before['server']['movement']['completedGrapples'], after
            assert max(s['position'][1] for s in trace('grapple-arrival')) > 103, after
        else:
            assert after['server']['movement']['cancelledGrapples'] > before['server']['movement']['cancelledGrapples'], after
            assert after['server']['movement']['mode'] != 'grapple', after
        c.command('setblock 0 104 4 air')
        c.command('fill -1 100 2 1 105 2 air')
        c.ticks(40)
        print('Grapple: ' + reason, flush=True)

    for kind in ['normal', 'transform', 'glide', 'stomp', 'stomp-cancel']:
        c.reset(y=112)
        before = c.start()
        if kind == 'transform': c.action('toggle_form')
        elif kind == 'glide': c.key('jump', 180)
        elif kind.startswith('stomp'):
            stomp()
            c.ticks(3)
            if kind == 'stomp-cancel':
                c.action('open_inventory')
                c.ticks(2)
                c.action('close_screen')
        settle(190)
        after = c.finish('fall-' + kind, before)
        if kind in ['normal', 'transform', 'stomp-cancel']:
            assert after['server']['health'] < 20, after
        else:
            assert after['server']['health'] == 20, after
        if kind == 'stomp':
            assert after['server']['movement']['stomps'] > before['server']['movement']['stomps'], after
            assert after['server']['movement']['stompImpacts'] - before['server']['movement']['stompImpacts'] == 1, after
        if kind == 'stomp-cancel':
            assert after['server']['movement']['stompImpacts'] == before['server']['movement']['stompImpacts'], after
        if kind == 'transform':
            c.action('toggle_form')
            c.ticks(3)
        print(f'Fall rule: {kind}, health={after["server"]["health"]}', flush=True)
    c.records['status'] = 'passed'
    c.save()
except Exception as error:
    c.records['status'] = 'failed'
    c.records['error'] = repr(error)
    c.save()
    raise
finally:
    c.action('release_keys')
    c.command('tp @s .5 100 .5')
    c.command('gamerule fallDamage true')
print(c.out, flush=True)
