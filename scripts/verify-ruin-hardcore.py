"""A separate disposable hardcore world: challenge cleanup must keep vanilla spectating."""
import argparse
import time
from devtools_client import DevClient
from ruin_driver import RuinDriver

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('world')
args = p.parse_args(); c = DevClient(args.build_id)
try:
    if 'player' in c.request(): c.action('leave_world')
    c.action('create_world', name=args.world, seed=8675341, hardcore=True)
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline; time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready()
    cave = RuinDriver(c, 'cave_altar', (1280, 87, 1280)); cave.prepare()
    c.command('difficulty hard'); c.command('gamerule doMobSpawning false'); c.command('gamerule keepInventory false')
    cave.start(); cave.cave_to_target()
    before = c.action('progress_state')['server']; assert c.action('ruin_state')['hardcore']
    c.command('kill @s'); c.ticks(30)
    state = c.action('ruin_state')
    assert not state['active'] and state['recovery']['dimension'] == ''
    assert not any(e['type'] == 'entity.orimod.practice_target' for e in c.action('nearby_entities')['entities'])
    gui = c.action('gui_state'); assert gui['screen'] == 'DeathScreen', gui
    c.screenshot('hardcore-death-screen'); c.records['steps']['dead'] = {'gui': gui, 'ruin': state, 'spirit': c.spirit()}
    spectate = next(w for w in gui['widgets'] if '旁观' in w['text'])
    assert spectate['active']; c.action('gui_click', x=spectate['x'] + 5, y=spectate['y'] + 5); c.ticks(15)
    state = c.action('ruin_state'); after = c.action('progress_state')['server']
    assert state['hardcore'] and state['gameMode'] == 'spectator' and not state['active'], state
    assert after['progress'] == before['progress'] and after['abilities'] == before['abilities']
    c.records['steps']['spectator'] = {'ruin': state, 'progress': after}; c.screenshot('spectator')
    c.action('leave_world'); c.action('open_world', name=args.world)
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline; time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(5)
    state = c.action('ruin_state'); assert state['hardcore'] and state['gameMode'] == 'spectator' and not state['active']
    c.records['steps']['rejoined'] = state
    c.records['status'] = 'passed'; print('Hardcore death, vanilla spectator button and rejoin passed', flush=True)
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    raise
finally:
    c.save()
    try: c.action('release_keys'); c.action('open_pause')
    except Exception: pass
