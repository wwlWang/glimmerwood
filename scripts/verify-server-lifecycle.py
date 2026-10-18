"""Leave live searches, Bash and an active cave target; inspect title/reopened world state."""
import argparse
import time
from devtools_client import DevClient
from ruin_driver import RuinDriver
from devtools_client import ROOT

parser = argparse.ArgumentParser(); parser.add_argument('build_id'); args = parser.parse_args()
c = DevClient(args.build_id)
world = 'orimod_dev_audit_20261018'
cave = RuinDriver(c, 'cave_altar', (64, 160, 64))

def zero(state):
    life = state['serverLifecycle']
    return life['frozen'] == life['challenges'] == life['regionTickets'] == 0 and all(v == 0 for v in life['locator'].values())

def leave(name, before):
    c.action('leave_world')
    deadline = time.monotonic() + 55
    while time.monotonic() < deadline:
        state = c.request()
        if 'player' not in state and state['screen'] == 'TitleScreen' and zero(state): break
        time.sleep(.1)
    else: raise AssertionError(('State survived shutdown', state))
    c.records['steps'][name] = {'before': before, 'title': state['serverLifecycle']}; c.save()
    print(name + ': shutdown state empty', flush=True)

def reopen():
    c.action('open_world', name=world); wait_world()
    assert zero(c.request()), c.request()['serverLifecycle']

def wait_world():
    deadline = time.monotonic() + 55
    while time.monotonic() < deadline:
        state = c.request()
        if 'player' in state and state['screen'] == 'gameplay' and state['overlay'] == 'none':
            c.action('focus_game'); c.ready(); c.ticks(3); return
        time.sleep(.1)
    raise TimeoutError('World did not finish loading')

try:
    if 'player' in c.request(): c.action('leave_world')
    reopen()
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.action('progress_action', action='locate', target=''); c.ticks(2)
    before = c.request()['serverLifecycle']
    assert before['locator']['searches'] == 1 and 1 <= before['locator']['tickets'] <= 4, before
    assert before['regionTickets'] == before['locator']['tickets'], before
    leave('pending-search', before); reopen()
    c.action('progress_action', action='locate', target=''); c.ticks(2)
    assert c.request()['serverLifecycle']['locator']['searches'] == 1, 'Old cooldown blocked new server search'
    leave('reopened-search', c.request()['serverLifecycle']); reopen()

    for cmd in ['difficulty normal', 'gamerule doMobSpawning false', 'kill @e[tag=orimod_lifecycle_test]',
                'summon husk .5 160 4.5 {Tags:["orimod_lifecycle_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}']:
        c.command(cmd)
    c.reset(y=160)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(3)
    c.action('grant_bash'); c.key('orimod.bash', 100); c.ticks(2)
    before = c.request()['serverLifecycle']; assert before['frozen'] == 1, before
    leave('frozen-bash-target', before); reopen()
    c.command('kill @e[tag=orimod_lifecycle_test]')

    cave.prepare(); baseline = cave.start(); target = cave.cave_to_target()
    before = c.request()['serverLifecycle']; assert before['challenges'] == 1, before
    leave('active-challenge-target', {'lifecycle': before, 'target': target})
    reopen()
    state = c.action('ruin_state')
    assert not state['active'] and not state['recovery']['dimension'], state
    assert not [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.orimod.practice_target']
    assert c.action('progress_state')['server']['progress']['light'] == baseline['light']
    c.records['steps']['challenge-reopened'] = {'state': state, 'position': c.spirit()['server']['position']}; c.save()
    leave('before-other-world', c.request()['serverLifecycle'])
    other = 'orimod_dev_lifecycle_20261018'
    if (ROOT / 'run-devtools/saves' / other / 'level.dat').exists(): c.action('open_world', name=other)
    else: c.action('create_world', name=other, seed=20261018, structures=False)
    wait_world()
    assert zero(c.request()), c.request()['serverLifecycle']
    c.records['steps']['different-world'] = c.request()['serverLifecycle']; c.save()
    leave('other-world-stop', c.request()['serverLifecycle']); reopen()
    c.records['status'] = 'passed'; c.save()
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); c.save(); raise
finally:
    c.action('release_keys')
print(c.out, flush=True)
