"""Walk and complete three rotated natural starts from an actual /locate audit world.

Copy the CLOSED audit world's directory to a fresh orimod_dev_ save before running.
Commands grant fixture abilities and approach each site. All entrance walking,
guide use, shrine interaction and challenge completion use normal game inputs.
"""
import argparse
import json
import math
import time
from pathlib import Path
from devtools_client import DevClient
from ruin_driver import RuinDriver

p = argparse.ArgumentParser()
p.add_argument('build_id'); p.add_argument('world'); p.add_argument('report', type=Path)
args = p.parse_args()
report = json.loads(args.report.read_text(encoding='utf-8'))
assert report['status'] == 'complete'
c = DevClient(args.build_id)
c.records.update(fixture_note=__doc__, seed=report['seed'], world=args.world, locate_report=str(args.report))
previous_auto_jump = c.request()['autoJump']

def ready_world():
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline
        time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(8)

try:
    c.action('release_keys')
    if 'player' in c.request(): c.action('leave_world')
    c.action('open_world', name=args.world); ready_world()
    for command in ['gamemode creative', 'difficulty peaceful', 'gamerule doMobSpawning false',
                    'gamerule doDaylightCycle false', 'time set 9000', 'weather clear']:
        c.command(command)
    c.action('settings', autoJump=False)
    if not c.spirit()['server']['awakened']:
        c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
        c.action('select_slot', slot=0); c.action('use_item'); c.ticks(7); c.action('close_screen')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(3)
    c.action('grant_dash'); c.action('grant_bash'); c.action('grant_air_movement')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade')
    c.command('item replace entity @s hotbar.1 with orimod:ruin_guide')
    for kind, rotation in [('forest_spring', 'CLOCKWISE_90'), ('creek_ruins', 'CLOCKWISE_180'), ('cave_altar', 'COUNTERCLOCKWISE_90')]:
        candidates = [row for row in report['rows'] if row['kind'] == kind and row['rotation'] == rotation]
        assert candidates, ('Audit lacks requested orientation', kind, rotation)
        selected = min(candidates, key=lambda row: math.hypot(row['entry'][0] - report['spawn'][0], row['entry'][2] - report['spawn'][2]))
        driver = RuinDriver(c, kind, selected['templateOrigin'], rotation)
        outside = driver.absolute((driver.layout['entry'][0], driver.layout['entry'][1] + 16, -8.5))
        c.command('gamemode creative')
        c.command('tp @s ' + ' '.join(map(str, outside)) + f' {driver.angle} 20'); c.ticks(85)
        starts = c.action('ruin_state')['naturalStarts']
        actual = next(s for s in starts if s['kind'] == kind and s['origin'] == list(driver.origin))
        assert actual['rotation'] == rotation, actual
        x, z = math.floor(outside[0]), math.floor(outside[2])
        for y in range(selected['entry'][1] + 24, driver.origin[1] - 18, -1):
            block = c.action('inspect_block', x=x, y=y, z=z)
            if block['fullBlock'] and not block['foliage']: break
        else: raise AssertionError(('Missing natural landing', selected))
        for dy in (1, 2): assert c.action('inspect_block', x=x, y=y + dy, z=z)['collisionEmpty'], ('Blocked landing', selected)
        approach = [x + .5, y + 1, z + .5]
        c.command('tp @s ' + ' '.join(map(str, approach)) + f' {driver.angle} 0'); c.ticks(8)
        c.command('gamemode survival'); c.action('focus_game'); c.ready(); c.ticks(5)
        c.screenshot(kind + '-natural-entry')
        started = time.monotonic(); old = c.action('ruin_state')['replyRevision']
        c.action('select_slot', slot=1); c.ticks(2); c.action('use_item')
        deadline = time.monotonic() + 70
        while True:
            state = c.action('ruin_state')
            if state['replyRevision'] > old and not state['search']['active'] and '正在寻找' not in state['reply']: break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
        assert state['guide'] != 'none' and state['guide']['entry'] == selected['entry'], (selected, state)
        assert state['guide']['kind'] == kind, state
        guide_seconds = time.monotonic() - started
        c.action('select_slot', slot=0)
        before = c.start(); driver.walk(driver.safe[0], driver.safe[2], limit=180)
        assert math.dist(c.spirit()['server']['position'], driver.safe) < .5
        c.finish(kind + '-entrance', before, natural_start=actual, landing=approach, guide=state['guide'], guide_seconds=guide_seconds)
        baseline = c.action('progress_state')['server']['progress']
        gui = driver.open(); button = gui['widgets'][0]
        assert button['active'], gui
        discovered = c.action('progress_state')['server']['progress']
        assert discovered['light'] - baseline['light'] == (0 if baseline['rewards'].get('discover/' + driver.key) else 15)
        c.action('gui_click', x=button['x'] + 4, y=button['y'] + 4); c.ticks(4)
        assert c.action('ruin_state')['active']
        before = c.start(); driver.run()
        progress = c.action('progress_state')['server']['progress']
        assert not c.action('ruin_state')['active']
        assert progress['light'] - discovered['light'] == 30
        assert progress['rewards'].get('clear/' + driver.key) == 1
        c.finish(kind + '-route', before, natural_start=actual, progress=progress)
        c.ticks(205)
        print(kind, rotation, 'natural entrance, guide and route passed', flush=True)
    c.records['status'] = 'passed'
except Exception as error:
    c.records.update(status='failed', error=repr(error))
    try: c.records['failure'] = {'state': c.request(), 'spirit': c.spirit(), 'ruin': c.action('ruin_state')}
    except Exception: pass
    raise
finally:
    c.save(); c.action('release_keys'); c.action('settings', autoJump=previous_auto_jump)
    c.action('open_pause'); c.action('save_world')
