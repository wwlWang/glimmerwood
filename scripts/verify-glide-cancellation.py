"""The longer glide renewal window must still obey immediate local exit conditions."""
import argparse
import math
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); c = DevClient(p.parse_args().build_id)
try:
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.command('gamemode creative'); c.command('fill 165 219 -3 175 219 0 moss_block')
    c.command('fill 165 200 1 175 228 25 air'); c.command('fill 165 220 -3 175 228 0 air')
    c.command('tp @s 170.5 220 .5'); c.ticks(8); c.command('gamemode survival')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    c.action('grant_air_movement')
    for reason in ['release', 'crouch', 'menu', 'wall', 'water']:
        c.command('fill 165 200 1 175 228 25 air')
        c.reset(170.5, 220, .5); c.key('jump', 100); c.key('forward', 100); c.ticks(18)
        before = c.start(); assert before['client']['movement']['mode'] == 'glide'
        if reason == 'release': c.action('release_keys')
        elif reason == 'crouch': c.key('sneak', 30)
        elif reason == 'menu': c.action('open_inventory')
        elif reason == 'wall': c.command('fill 165 210 8 175 228 8 stone')
        else: c.command('fill 165 210 6 175 224 10 water')
        c.ticks(4 if reason in ['release', 'crouch', 'menu'] else 20)
        after = c.finish(reason, before)
        for side in ['client', 'server']: assert after[side]['movement']['mode'] != 'glide', (reason, after)
        if reason == 'water':
            x, y, z = map(math.floor, after['server']['position'])
            block = c.action('inspect_block', x=x, y=y, z=z)
            assert 'water' in str(block), (after, block)
        print('Glide exits:', reason, flush=True)
        if reason == 'menu': c.action('close_screen')
        c.action('release_keys')
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('release_keys'); c.command('gamemode creative'); c.command('tp @s .5 160 .5')
    c.ticks(8); c.command('gamemode survival'); c.action('open_pause')
