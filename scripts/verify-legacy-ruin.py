"""Verify a saved pre-rotation structure start and the legacy-spacing player guide.

The input world is a hash-verified copy of the closed baseline audit world. Commands
only prepare the player and the initial approach; walking and the route use real input.
"""
import argparse
import math
import time
from devtools_client import DevClient
from ruin_driver import RuinDriver

p = argparse.ArgumentParser()
p.add_argument('build_id'); p.add_argument('world')
args = p.parse_args()
c = DevClient(args.build_id)
c.records.update(fixture_note=__doc__, world=args.world, seed=8675321, legacy_start_chunk=[1, 18])
try:
    c.action('release_keys')
    if 'player' in c.request(): c.action('leave_world')
    c.action('open_world', name=args.world)
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline
        time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(8)
    for command in ['gamemode creative', 'difficulty peaceful', 'gamerule doMobSpawning false',
                    'gamerule doDaylightCycle false', 'time set 9000', 'weather clear']:
        c.command(command)
    if not c.spirit()['server']['awakened']:
        c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
        c.action('select_slot', slot=0); c.action('use_item'); c.ticks(7); c.action('close_screen')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(3)
    c.action('grant_bash'); c.action('grant_air_movement')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade')
    c.command('item replace entity @s hotbar.1 with orimod:ruin_guide')
    c.command('tp @s 26 170 290 0 20'); c.ticks(85)
    starts = c.action('ruin_state')['naturalStarts']
    selected = next(s for s in starts if s['kind'] == 'creek_ruins' and s['origin'][0] == 16 and s['origin'][2] == 288)
    assert selected['rotation'] == 'NONE', selected
    driver = RuinDriver(c, 'creek_ruins', selected['origin'])
    entry = list(map(math.floor, driver.absolute(driver.layout['entry'])))
    outside = driver.absolute((driver.layout['entry'][0], 0, -8.5))
    x, z = math.floor(outside[0]), math.floor(outside[2])
    for y in range(entry[1] + 24, driver.origin[1] - 18, -1):
        block = c.action('inspect_block', x=x, y=y, z=z)
        if block['fullBlock'] and not block['foliage']: break
    else: raise AssertionError(('Missing legacy landing', selected))
    for dy in (1, 2): assert c.action('inspect_block', x=x, y=y + dy, z=z)['collisionEmpty']
    approach = [x + .5, y + 1, z + .5]
    c.command('tp @s ' + ' '.join(map(str, approach)) + ' 0 0'); c.ticks(8)
    c.command('gamemode survival'); c.action('focus_game'); c.ready(); c.ticks(5)
    started = time.monotonic(); old = c.action('ruin_state')['replyRevision']
    c.action('select_slot', slot=1); c.ticks(2); c.action('use_item')
    deadline = time.monotonic() + 70
    while True:
        state = c.action('ruin_state')
        if state['replyRevision'] > old and not state['search']['active'] and '正在寻找' not in state['reply']: break
        assert time.monotonic() < deadline, state
        time.sleep(.1)
    assert state['guide'] != 'none' and state['guide']['entry'] == entry and state['guide']['kind'] == 'creek_ruins', state
    c.action('select_slot', slot=0)
    before = c.start(); driver.walk(driver.safe[0], driver.safe[2], limit=180)
    assert math.dist(c.spirit()['server']['position'], driver.safe) < .5
    c.finish('legacy-entrance', before, natural_start=selected, landing=approach, guide=state['guide'], guide_seconds=time.monotonic() - started)
    baseline = c.action('progress_state')['server']['progress']
    gui = driver.open(); button = gui['widgets'][0]
    assert button['active'], gui
    discovered = c.action('progress_state')['server']['progress']
    assert discovered['light'] - baseline['light'] == 15
    c.action('gui_click', x=button['x'] + 4, y=button['y'] + 4); c.ticks(4)
    before = c.start(); driver.run()
    progress = c.action('progress_state')['server']['progress']
    assert not c.action('ruin_state')['active']
    assert progress['light'] - discovered['light'] == 30
    assert progress['rewards'].get('clear/' + driver.key) == 1
    c.finish('legacy-route', before, natural_start=selected, progress=progress)
    c.records['status'] = 'passed'
    print('Legacy saved start, guide, entrance and route passed', flush=True)
except Exception as error:
    c.records.update(status='failed', error=repr(error))
    try: c.records['failure'] = {'state': c.request(), 'spirit': c.spirit(), 'ruin': c.action('ruin_state')}
    except Exception: pass
    raise
finally:
    c.save(); c.action('release_keys'); c.action('open_pause'); c.action('save_world')
