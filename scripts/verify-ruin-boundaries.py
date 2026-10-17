"""Live challenge cleanup, damaged-route guards, normal death, persistence and timeout.

Uses disposable fixtures in orimod_dev_ruins. Inputs complete the cave movement phases;
commands deliberately create damage, missing blocks and death for boundary coverage.
"""
import argparse
import json
import math
import time
from devtools_client import DevClient
from ruin_driver import RuinDriver

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--base', type=int, default=1728)
args = p.parse_args(); c = DevClient(args.build_id)
cave = RuinDriver(c, 'cave_altar', (args.base, 87, args.base))
forest = RuinDriver(c, 'forest_spring', (args.base + 64, 94, args.base))


def pos_args(pos):
    return dict(zip(('x', 'y', 'z'), map(int, pos)))


def block(pos):
    return c.action('inspect_block', **pos_args(pos))['block']


def set_block(pos, value):
    c.command('setblock ' + ' '.join(str(int(n)) for n in pos) + ' ' + value)


def targets():
    return [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.orimod.practice_target']


def record(name):
    c.records['steps'][name] = {'player': c.spirit(), 'challenge': c.action('ruin_state'), 'progress': c.action('progress_state'), 'targets': targets()}
    c.save()


def assert_returned(driver):
    state = c.action('ruin_state')
    assert not state['active'] and state['recovery']['dimension'] == '', state
    assert targets() == [], targets()
    point = c.spirit()['server']['position']
    assert math.dist(point, driver.safe) < 1, (point, driver.safe)


def refused(reason):
    gui = cave.open()
    assert not gui['widgets'][0]['active'], gui
    assert reason in gui['narration'], gui
    c.screenshot('refused-' + str(len(c.records['steps'])))
    c.action('close_screen'); c.ticks(2)


def exit_in_panel():
    c.key('orimod.panel', 1); c.ticks(3)
    gui = c.action('gui_state')
    guide = next(w for w in gui['widgets'] if w['text'] == '操作指引')
    c.action('gui_click', x=guide['x'] + 5, y=guide['y'] + 5); c.ticks(2)
    listing = next(w for w in c.action('gui_state')['widgets'] if w['type'] == 'ChoiceList')
    index = next(i for i, v in enumerate(listing['entries']) if v.startswith('退出当前挑战.'))
    c.action('gui_click', x=listing['x'] + 24, y=listing['y'] + 4 + index * 25 + 12); c.ticks(2)
    button = next(w for w in c.action('gui_state')['widgets'] if w['type'] == 'SpiritButton' and w['text'] == '退出当前挑战')
    assert button['active'], button
    c.action('gui_click', x=button['x'] + 5, y=button['y'] + 5); c.ticks(4)
    assert any(not w['active'] and w['text'] == '当前没有正在进行的挑战。' for w in c.action('gui_state')['widgets'])
    c.action('close_screen'); c.ticks(2)


try:
    cave.prepare(); c.command('gamerule naturalRegeneration false'); c.command('gamerule keepInventory false')
    c.command('gamerule doMobSpawning false')
    c.command('clear @s orimod:spirit_blade'); refused('背包中需要'); record('missing-blade')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade')
    anchor = next(a for a in cave.layout['critical'] if a['block'] == 'orimod:grapple_flower')
    anchor_pos = cave.absolute(anchor['position'])
    set_block(anchor_pos, 'air'); refused('关键锚点'); record('missing-anchor'); set_block(anchor_pos, anchor['block'])
    obstacle = cave.absolute(cave.layout['clearances'][0]['from'])
    set_block(obstacle, 'stone'); refused('关键锚点'); record('blocked-route'); set_block(obstacle, 'air')
    decoration = cave.absolute((2, 2, 20)); set_block(decoration, 'diamond_block')

    baseline = cave.start(); c.command('damage @s 2 minecraft:generic'); c.ticks(3)
    health = c.spirit()['server']['health']; assert health < 20
    cave.cave_to_target(); record('before-gui-exit'); exit_in_panel(); assert_returned(cave)
    assert block(decoration) == 'minecraft:diamond_block'
    assert c.spirit()['server']['health'] == health
    assert c.action('progress_state')['server']['progress']['light'] == baseline['light']
    record('gui-exit-preserves-health-blocks-reward')
    print('Missing prerequisites/anchors/clearance and normal panel exit passed', flush=True)

    cave.start(); target = cave.cave_to_target()
    c.command('damage @e[type=orimod:practice_target,limit=1,sort=nearest] 100 minecraft:generic'); c.ticks(3)
    assert targets()[0]['health'] == 12
    c.command('kill @e[type=orimod:practice_target,limit=1,sort=nearest]'); c.ticks(4)
    assert_returned(cave); record('target-removed')

    cave.start(); cave.cave_to_target(); c.action('toggle_form'); c.ticks(4)
    assert not c.spirit()['server']['form']; assert_returned(cave); record('form-cancel')
    c.action('toggle_form'); c.ticks(3)

    cave.start(); cave.cave_to_target(); permanent = c.action('progress_state')['server']
    c.command('kill @s'); c.ticks(3)
    assert not c.action('ruin_state')['active'] and targets() == []
    c.action('respawn'); c.ticks(8)
    after = c.action('progress_state')['server']
    assert after['progress'] == permanent['progress'] and after['abilities'] == permanent['abilities']
    assert not c.spirit()['server']['form'] and math.dist(c.spirit()['server']['position'], cave.safe) > 8
    record('death-does-not-return-or-resurrect')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade'); c.action('select_slot', slot=0)
    c.action('toggle_form'); c.ticks(3)
    print('Target loss, form change and normal death cleanup passed', flush=True)

    cave.start(); cave.cave_to_target(); permanent = c.action('progress_state')['server']
    c.action('leave_world')
    c.action('open_world', name='orimod_dev_ruins')
    deadline = time.monotonic() + 90
    while 'player' not in c.request():
        assert time.monotonic() < deadline; time.sleep(.2)
    c.action('focus_game'); c.ready(); c.ticks(5); assert_returned(cave)
    after = c.action('progress_state')['server']
    assert after['progress'] == permanent['progress'] and after['abilities'] == permanent['abilities']
    record('logout-rejoin')

    # Build the destination platform before starting the dimension-cancel trial.
    c.command('gamemode creative'); c.command('execute in minecraft:the_nether run tp @s .5 100 .5'); c.ticks(30)
    c.command('fill -2 99 -2 2 99 2 stone'); c.command('fill -2 100 -2 2 103 2 air')
    c.command('execute in minecraft:overworld run tp @s ' + ' '.join(map(str, cave.safe)))
    c.command('gamemode survival'); c.ticks(5)
    cave.start(); cave.cave_to_target()
    c.command('execute in minecraft:the_nether run tp @s .5 100 .5'); c.ticks(5)
    assert c.request()['player']['dimension'] == 'minecraft:the_nether' and not c.action('ruin_state')['active']
    c.command('execute in minecraft:overworld run tp @s ' + ' '.join(map(str, cave.safe))); c.ticks(10)
    assert targets() == []; record('dimension-cancel')
    print('Logout/rejoin and dimension cleanup passed', flush=True)

    cave.start(); c.action('look', yaw=0, pitch=0); c.key('forward', 10); c.ticks(14)
    safe_floor = [math.floor(cave.safe[0]), math.floor(cave.safe[1]) - 1, math.floor(cave.safe[2])]
    original = block(safe_floor); set_block(safe_floor, 'magma_block'); c.ticks(24)
    assert not c.action('ruin_state')['active']; assert block(safe_floor) == 'minecraft:magma_block'
    assert math.dist(c.spirit()['server']['position'], cave.safe) >= .9
    record('unsafe-return-uses-nearby-ground'); set_block(safe_floor, original)

    forest.prepare(); forest.start(); forest.walk(forest.origin[0] + 12.5, forest.safe[2])
    c.action('look', yaw=0, pitch=0); c.key('forward', 14); c.ticks(24)
    assert_returned(forest); record('water-failure')
    forest.start(); state = c.action('ruin_state'); started = c.spirit()['server']['movement']['gameTime'] - state['elapsed']
    health = c.spirit()['server']['health']; light = c.action('progress_state')['server']['progress']['light']
    c.action('open_inventory'); deadline = time.monotonic() + 100; next_report = time.monotonic() + 20
    while c.action('ruin_state')['active']:
        assert time.monotonic() < deadline, 'Challenge timeout did not fire'
        if time.monotonic() >= next_report:
            print('Timeout trial running:', c.action('ruin_state')['elapsed'], 'server ticks', flush=True); next_report += 20
        time.sleep(.2)
    c.action('close_screen'); c.ticks(3); assert_returned(forest)
    elapsed = c.spirit()['server']['movement']['gameTime'] - started
    assert 1800 <= elapsed <= 1830, elapsed
    assert c.spirit()['server']['health'] == health and c.action('progress_state')['server']['progress']['light'] == light
    record('ninety-second-timeout'); c.records['steps']['ninety-second-timeout']['elapsed_ticks'] = elapsed
    c.records['status'] = 'passed'; print('Return safety, water failure and 90-second timeout passed', flush=True)
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    try: record('failure')
    except Exception: pass
    raise
finally:
    c.save()
    try:
        c.action('release_keys'); c.command('gamerule naturalRegeneration true'); c.action('open_pause')
    except Exception: pass
