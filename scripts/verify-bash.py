"""Exercise Bash through its real R binding, including target restoration and cancellation."""
import argparse
from devtools_client import DevClient

parser = argparse.ArgumentParser()
parser.add_argument('build_id')
client = DevClient(parser.parse_args().build_id)

def nearby():
    return client.action('nearby_entities')['entities']

def target():
    return next(entity for entity in nearby() if entity['type'] == 'entity.minecraft.husk')

def setup_target():
    client.command('kill @e[tag=orimod_bash_test]')
    client.command('summon minecraft:husk .5 100 4.8 {Tags:["orimod_bash_test"],PersistenceRequired:1b,Silent:1b}')
    client.ticks(1)

def aim(hold=20):
    client.key('orimod.bash', hold)
    client.ticks(2)
    state = client.spirit()
    assert state['server']['movement']['mode'] == 'bash_aim', state
    assert state['client']['movement']['mode'] == 'bash_aim', state
    return state

try:
    client.records['initial'] = client.ready()
    assert client.spirit()['server']['form']
    for command in ['difficulty normal', 'gamerule doMobSpawning false', 'gamerule fallDamage false',
                    'fill -3 100 2 3 104 8 air', 'fill -5 99 -5 5 99 10 moss_block']:
        client.command(command)
    client.action('grant_bash')
    client.reset()
    setup_target()
    before = client.start()
    original = target()
    held = aim()
    client.action('look', yaw=0, pitch=-35)
    client.ticks(1)
    client.action('release_keys')
    client.ticks(2)
    launched_target = target()
    after = client.finish('release', before, original=original, held=held, launched_target=launched_target)
    assert after['server']['movement']['bashes'] - before['server']['movement']['bashes'] == 1, after
    assert after['client']['position'][1] > 100.5, after
    assert launched_target['velocity'][2] < 0, launched_target
    assert not launched_target['noGravity'] and not launched_target['noAi'], launched_target
    print('Bash release: aimed launch and opposite target reaction', flush=True)

    for reason in ['timeout', 'menu', 'damage', 'form', 'target-death']:
        client.reset()
        setup_target()
        before = client.start()
        held = aim()
        selected_id = held['server']['movement']['targetId']
        if reason == 'timeout':
            client.ticks(14)
        elif reason == 'menu':
            client.action('open_inventory')
            client.ticks(4)
            client.action('close_screen', keepKeys=True)
        elif reason == 'damage':
            client.command('damage @s 1 minecraft:mob_attack by @e[tag=orimod_bash_test,limit=1]')
            client.ticks(3)
        elif reason == 'form':
            client.action('toggle_form')
            client.ticks(3)
        else:
            client.command('kill @e[tag=orimod_bash_test]')
            client.ticks(3)
        client.action('release_keys')
        client.ticks(2)
        survivors = nearby()
        after = client.finish(reason, before, held=held, entities=survivors)
        assert after['server']['movement']['mode'] != 'bash_aim', (reason, after)
        assert after['server']['movement']['bashes'] == before['server']['movement']['bashes'], (reason, after)
        assert after['server']['movement']['cancelledBashes'] > before['server']['movement']['cancelledBashes'], (reason, after)
        for entity in survivors:
            if entity['id'] == selected_id:
                assert not entity['noGravity'] and not entity['noAi'], entity
        if reason == 'form':
            client.action('toggle_form')
            client.ticks(3)
        print('Bash cancellation: ' + reason, flush=True)

    client.reset()
    setup_target()
    client.command('fill -1 100 2 1 103 2 stone')
    before = client.start()
    client.key('orimod.bash', 8)
    client.ticks(5)
    after = client.finish('occluded', before)
    assert after['server']['movement']['mode'] != 'bash_aim', after
    assert after['server']['movement']['bashes'] == before['server']['movement']['bashes'], after
    print('Occluded enemy is rejected', flush=True)

    client.action('release_keys')
    client.command('fill -1 100 2 1 103 2 air')
    client.command('kill @e[tag=orimod_bash_test]')
    client.command('setblock 0 101 4 orimod:bash_anchor')
    client.reset()
    before = client.start()
    held = aim()
    client.command('setblock 0 101 4 air')
    client.ticks(3)
    after = client.finish('removed-anchor', before, held=held)
    assert after['server']['movement']['mode'] != 'bash_aim', after
    assert after['server']['movement']['cancelledBashes'] > before['server']['movement']['cancelledBashes'], after
    print('Removed anchor cancels the hold', flush=True)
    client.records['status'] = 'passed'
    client.save()
except Exception as error:
    client.records['status'] = 'failed'
    client.records['error'] = repr(error)
    client.save()
    raise
finally:
    client.action('release_keys')
    client.command('tp @s .5 100 .5')
    client.command('gamerule fallDamage true')
    client.command('kill @e[tag=orimod_bash_test]')
    client.command('difficulty peaceful')
print(client.out, flush=True)
