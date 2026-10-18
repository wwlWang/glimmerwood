"""Real walking inputs compare vanilla auto-jump with Ori jump accounting."""
import argparse
import json
from devtools_client import DevClient, ROOT

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--observe', action='store_true')
args = p.parse_args(); c = DevClient(args.build_id)
original = next(line.split(':', 1)[1] == 'true' for line in (ROOT / 'run-devtools/options.txt').read_text(encoding='utf-8').splitlines() if line.startswith('autoJump:'))
try:
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.command('gamemode creative'); c.command('fill 86 159 -5 94 159 20 moss_block')
    c.command('fill 86 160 -5 94 166 20 air'); c.command('tp @s 90.5 160 .5 0 0'); c.ticks(8)
    c.command('gamemode survival'); c.command('difficulty peaceful'); c.command('effect clear @s')
    c.action('select_slot', slot=8); c.command('item replace entity @s hotbar.8 with air')
    for name, spirit, enabled, block, expected in [
        ('human-auto', False, True, 'stone', 0),
        ('ori-disabled', True, False, 'stone', 0),
        ('ori-auto', True, True, 'stone', 1),
        ('ori-slab', True, True, 'stone_slab[type=bottom]', 0),
        ('ori-stairs', True, True, 'stone_stairs[facing=south]', 0),
    ]:
        c.action('settings', autoJump=enabled)
        if c.spirit()['server']['form'] != spirit: c.action('toggle_form'); c.ticks(4)
        c.command('fill 86 160 -5 94 166 20 air')
        c.command('fill 89 160 5 91 160 11 ' + block)
        c.reset(90.5, 160, .5); before = c.start(); c.key('forward', 45); c.ticks(48)
        after = c.finish(name, before)
        samples = json.loads((c.out / (name + '.json')).read_text(encoding='utf-8'))['samples']
        peak = max(row['state']['position'][1] for row in samples if row['side'] == 'client')
        jumps = after['server']['movement']['jumps'] - before['server']['movement']['jumps']
        result = {'accepted_jumps': jumps, 'expected_jumps': expected, 'peak': peak,
                  'crossed': after['server']['position'][2] > 6}
        c.records['steps'][name].update(result); c.save(); print(name, result, flush=True)
        if name == 'ori-disabled': assert not result['crossed'] and peak < 160.1
        else: assert result['crossed'], (name, after)
        if not args.observe: assert jumps == expected, (name, result)
    if not args.observe:
        c.command('fill 86 160 -5 94 166 20 air'); c.command('fill 89 160 5 91 160 11 stone')
        c.reset(90.5, 160, .5); before = c.start(); c.key('forward', 40)
        for _ in range(35):
            c.ticks(1)
            if c.spirit()['client']['position'][1] > 160.1: break
        else: raise AssertionError('Automatic jump did not start')
        c.ticks(2)
        c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True},
            {'at': 1, 'key': 'key.jump', 'down': False}, {'at': 3, 'key': 'key.jump', 'down': True},
            {'at': 4, 'key': 'key.jump', 'down': False}])
        c.ticks(10); after = c.finish('auto-plus-manual-double', before)
        jumps = after['server']['movement']['jumps'] - before['server']['movement']['jumps']
        assert jumps == 2, ('Automatic plus manual jumps', jumps, after)
        assert after['client']['movement']['pendingPredictions'] == 0
        assert after['client']['movement']['predictionMismatches'] == before['client']['movement']['predictionMismatches']
        print('auto-plus-manual-double: two accepted jumps; third input rejected', flush=True)
    c.records['status'] = 'observed' if args.observe else 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save()
    c.action('release_keys'); c.action('settings', autoJump=original); c.action('open_pause')
