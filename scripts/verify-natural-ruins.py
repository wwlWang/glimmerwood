"""Find actual structure starts with the player guide across registered fresh seeds.

Commands only set up a reproducible player and move between exploration areas. No
template placement occurs. Each entrance approach and challenge uses normal inputs.
This is world-generation validation, not the command-free survival campaign.
"""
import argparse
import json
import math
import time
from devtools_client import DevClient, ROOT
from ruin_driver import RuinDriver

p = argparse.ArgumentParser()
p.add_argument('build_id'); p.add_argument('world'); p.add_argument('seed', type=int)
p.add_argument('--resume', action='store_true')
p.add_argument('--continue-evidence')
p.add_argument('--start-area', type=int, nargs=2)
p.add_argument('--kinds', nargs='+', choices=['forest_spring', 'creek_ruins', 'cave_altar'],
               default=['forest_spring', 'creek_ruins', 'cave_altar'])
args = p.parse_args(); c = DevClient(args.build_id)
c.records['world'] = args.world; c.records['seed'] = args.seed
c.records['required_kinds'] = args.kinds


def wait_world():
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline
        time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(5)


def locate():
    started = time.monotonic()
    old_revision = c.action('ruin_state')['replyRevision']
    c.action('select_slot', slot=1); c.ticks(2); c.action('use_item'); c.ticks(3)
    deadline = time.monotonic() + 40
    while True:
        state = c.action('ruin_state')
        if state['replyRevision'] > old_revision and not state['search']['active'] and '正在寻找' not in state['reply']:
            state['search_seconds'] = time.monotonic() - started
            return state
        assert time.monotonic() < deadline, state
        time.sleep(.1)


def start_here(driver):
    c.action('focus_game'); c.ready()
    before = c.action('progress_state')['server']['progress']
    gui = driver.open(); button = gui['widgets'][0]
    assert button['active'], gui
    after = c.action('progress_state')['server']['progress']
    assert after['light'] - before['light'] == (0 if before['rewards'].get('discover/' + driver.key) else 15)
    c.action('gui_click', x=button['x'] + 4, y=button['y'] + 4); c.ticks(4)
    assert c.action('ruin_state')['active']
    return after


try:
    if 'player' in c.request(): c.action('leave_world')
    c.action('open_world' if args.resume else 'create_world', name=args.world, **({} if args.resume else {'seed': args.seed}))
    wait_world()
    for command in ['gamemode creative', 'difficulty peaceful', 'gamerule doMobSpawning false',
                    'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'time set 9000', 'weather clear']:
        c.command(command)
    if not c.spirit()['server']['awakened']:
        c.command('item replace entity @s hotbar.0 with orimod:spirit_core'); c.action('select_slot', slot=0)
        c.action('use_item'); c.ticks(7); c.action('close_screen')
        progress = c.action('progress_state')['server']
        assert sum(i['count'] for i in progress['inventory'] if i['item'] == 'orimod:ruin_guide') == 1, progress
        assert progress['progress']['rewards']['gift/ruin_guide'] == 1
        c.records['steps']['guide-gift'] = progress
    c.command('item replace entity @s hotbar.1 with orimod:ruin_guide')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade')
    found = set(); visited = set(); refusals = set(); relocate = True; area_index = 0
    if args.continue_evidence:
        assert args.resume and args.continue_evidence.replace('-', '').replace('_', '').isalnum()
        previous = json.loads((ROOT / 'evidence' / args.continue_evidence / 'results.json').read_text(encoding='utf-8'))
        assert previous['seed'] == args.seed and previous['world'] == args.world
        found.update(previous.get('found', [])); refusals.update(previous.get('refused_missing_abilities', []))
        c.records['previous_verified_steps'] = args.continue_evidence
        c.records['previous_source_sha256'] = previous['source_sha256']
    areas = [(0, 0), (1024, 0), (0, 1024), (-1024, 0), (0, -1024), (2048, 1024), (-2048, -1024)]
    if args.start_area: areas.insert(0, tuple(args.start_area))
    for attempt in range(18):
        if set(args.kinds) <= found: break
        c.action('close_screen'); c.command('gamemode creative')
        if relocate or attempt % 3 == 0:
            x, z = areas[min(area_index, len(areas) - 1)]; area_index += 1; relocate = False
            c.command(f'tp @s {x} 170 {z} 0 0'); c.ticks(55)
        origin_position = c.spirit()['server']['position']
        state = locate()
        if '稍等' in state['reply']:
            c.ticks(210); state = locate()
        c.records['steps'][f'search-{attempt}'] = {'from': origin_position, 'result': state}; c.save()
        if state['guide'] == 'none' or '入口' not in state['reply']:
            print('Search result:', state['reply'], flush=True)
            relocate = True
            c.ticks(210); continue
        guide = state['guide']; kind = guide['kind']; entry = guide['entry']
        assert math.hypot(entry[0] - origin_position[0], entry[2] - origin_position[2]) <= 2048
        assert tuple(entry) not in visited, ('Guide returned a cleared site', entry)
        visited.add(tuple(entry))
        # Load the actual generated start, then observe the terrain from outside its footprint.
        c.command(f'tp @s {entry[0] + .5} {entry[1] + 8} {entry[2] - 12.5} 0 22'); c.ticks(80)
        starts = c.action('ruin_state')['naturalStarts']
        selected = next(s for s in starts if s['kind'] == kind
                        and abs(s['origin'][0] - entry[0]) < 25 and abs(s['origin'][2] - entry[2]) < 45)
        driver = RuinDriver(c, kind, selected['origin'])
        assert [math.floor(v) for v in driver.absolute(driver.layout['entry'])] == entry
        label = f'{attempt}-{kind}'
        c.screenshot(label + '-terrain')
        # The approach starts outside the footprint; no teleport occurs until after completion.
        # Start on actual earth outside the paving. Dropping from doorway height
        # can land on a tree crown rather than test the ground-level entrance.
        approach_x, approach_z = entry[0], driver.origin[2] - 9
        for ground_y in range(entry[1] + 24, driver.origin[1] - 17, -1):
            block = c.action('inspect_block', x=approach_x, y=ground_y, z=approach_z)
            if block['fullBlock'] and not block['foliage']:
                break
        else:
            raise AssertionError(('No natural landing ground', selected))
        for dy in (1, 2):
            assert c.action('inspect_block', x=approach_x, y=ground_y + dy, z=approach_z)['collisionEmpty'], ('Blocked natural landing', selected)
        approach = [approach_x + .5, ground_y + 1, approach_z + .5]
        c.command('tp @s ' + ' '.join(map(str, approach)) + ' 0 0')
        c.command('gamemode survival'); c.action('select_slot', slot=0); c.action('focus_game'); c.ready(); c.ticks(10)
        before = c.start()
        c.screenshot(label + '-ground-approach')
        driver.walk(driver.safe[0], driver.safe[2], limit=140)
        assert math.dist(c.spirit()['server']['position'], driver.safe) < .5, c.spirit()
        c.finish(label + '-entrance', before, natural_start=selected, guide=guide)
        gui = driver.open()
        if not gui['widgets'][0]['active']:
            assert '能力' in gui['narration'], gui
            refusals.add(kind); c.screenshot(label + '-requirements')
        c.action('close_screen')
        # Grant only this route's declared movement set, keeping base movement unchanged.
        if kind == 'forest_spring': c.action('grant_dash')
        elif kind == 'creek_ruins': c.action('grant_bash'); c.action('grant_air_movement')
        else: c.action('grant_air_movement')
        c.ticks(3)
        baseline = start_here(driver); before = c.start(); driver.run()
        after = c.action('progress_state')['server']['progress']
        assert not c.action('ruin_state')['active'], c.action('ruin_state')
        assert after['rewards'].get('clear/' + driver.key) == 1
        assert after['light'] - baseline['light'] == 30
        c.finish(label + '-route', before, natural_start=selected, progress=after)
        c.screenshot(label + '-completed')
        found.add(kind); c.records['found'] = sorted(found); c.records['refused_missing_abilities'] = sorted(refusals); c.save()
        print('Natural start completed:', args.seed, kind, selected['origin'], 'types', len(found), flush=True)
        c.ticks(205)
    assert set(args.kinds) <= found, found
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    try: c.records['failure'] = {'state': c.request(), 'spirit': c.spirit(), 'ruin': c.action('ruin_state')}
    except Exception: pass
    try:
        result = c.action('save_motion', name=args.build_id + '-failed')
        import shutil
        shutil.copy2(result['path'], c.out / 'failed-motion.json')
    except Exception: pass
    raise
finally:
    c.save()
    try: c.action('release_keys'); c.action('open_pause')
    except Exception: pass
