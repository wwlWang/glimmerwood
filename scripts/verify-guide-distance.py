"""Search beyond 1500 blocks in a fresh normal world, then verify the actual entrance."""
import argparse
import math
import time
from devtools_client import DevClient
from ruin_driver import RuinDriver

p = argparse.ArgumentParser()
p.add_argument('build_id'); p.add_argument('world')
p.add_argument('--reuse', action='store_true')
args = p.parse_args(); c = DevClient(args.build_id)
c.records.update(fixture_note=__doc__, world=args.world, seed=8675321, search_origin=[100, 170, -1600])
try:
    c.action('release_keys')
    if 'player' in c.request(): c.action('leave_world')
    c.action('open_world' if args.reuse else 'create_world', name=args.world, **({} if args.reuse else {'seed':8675321}))
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline
        time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(8)
    c.command('gamemode creative')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
    c.action('select_slot', slot=0); c.action('use_item'); c.ticks(8); c.action('close_screen')
    c.command('item replace entity @s hotbar.0 with orimod:ruin_guide')
    c.command('tp @s 100 170 -1600 0 0'); c.ticks(40)
    origin = c.spirit()['server']['position']
    old = c.action('ruin_state')['replyRevision']
    c.action('use_item'); started = time.monotonic()
    observations = []
    while True:
        state = c.action('ruin_state')
        observations.append({'seconds':time.monotonic() - started, 'search':state['search']})
        if state['replyRevision'] > old and not state['search']['active'] and '正在寻找' not in state['reply']: break
        assert time.monotonic() - started < 90, state
        time.sleep(.1)
    c.records['observations'] = observations
    c.records['result'] = state
    assert state['guide'] != 'none' and '入口' in state['reply'], state
    guide = state['guide']; entry = guide['entry']
    distance = math.hypot(entry[0] - origin[0], entry[2] - origin[2])
    assert 1500 < distance <= 2048, (distance, guide)
    c.records.update(distance=distance, search_seconds=time.monotonic() - started)
    c.command(f'tp @s {entry[0] + .5} {entry[1] + 14} {entry[2] + .5} 0 0'); c.ticks(85)
    actual = next(s for s in c.action('ruin_state')['naturalStarts'] if s['kind'] == guide['kind']
                  and list(map(math.floor, RuinDriver(c, s['kind'], s['origin'], s['rotation']).absolute(
                      RuinDriver(c, s['kind'], s['origin'], s['rotation']).layout['entry']))) == entry)
    driver = RuinDriver(c, actual['kind'], actual['origin'], actual['rotation'])
    shrine = c.action('inspect_block', **dict(zip(('x', 'y', 'z'), map(int, driver.shrine))))
    assert shrine['block'] == 'orimod:' + actual['kind'] + '_shrine', (actual, shrine)
    c.records.update(status='passed', actual_start=actual, actual_shrine=shrine)
    print('Cold guide found actual entrance', round(distance, 1), 'blocks away in', round(c.records['search_seconds'], 2), 'seconds', flush=True)
except Exception as error:
    c.records.update(status='failed', error=repr(error))
    try: c.records['failure'] = c.action('ruin_state')
    except Exception: pass
    raise
finally:
    c.save(); c.action('release_keys'); c.action('open_pause'); c.action('save_world')
