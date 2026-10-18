"""Bash ownership/boss rejection and blade bystander safety through real client input."""
import argparse
from devtools_client import DevClient

parser = argparse.ArgumentParser()
parser.add_argument('build_id')
c = DevClient(parser.parse_args().build_id)
TAG = 'orimod_target_safety'

def entities():
    return c.action('nearby_entities')['entities']

def reset():
    c.action('release_keys')
    c.command(f'kill @e[tag={TAG}]')
    c.reset(y=160)

try:
    c.action('focus_game'); c.ready(); c.action('release_keys')
    for command in ['tp @s .5 160 .5', 'gamerule doMobSpawning false', 'difficulty normal',
                    'gamerule fallDamage false', 'fill -8 159 -8 8 159 14 moss_block',
                    'fill -5 160 -4 5 166 12 air', 'gamemode survival']:
        c.command(command)
    if not c.spirit()['server']['awakened']:
        c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
        c.action('select_slot', slot=0); c.ticks(3); c.key('use', 1); c.ticks(8)
        c.action('close_screen'); c.ticks(3)
    if not c.spirit()['server']['form']:
        c.action('toggle_form'); c.ticks(4)
    c.action('grant_bash')
    for name, owned, expected in [('own-arrow', True, 0), ('foreign-arrow', False, 1)]:
        reset()
        c.command(f'summon arrow .5 161 3.8 {{Tags:["{TAG}"],NoGravity:1b}}')
        if owned:
            c.command(f'data modify entity @e[tag={TAG},limit=1] Owner set from entity @s UUID')
        c.ticks(2); before = c.start(); c.key('orimod.bash', 4); c.ticks(10)
        after = c.finish(name, before, entities=entities())
        assert after['server']['movement']['bashes'] - before['server']['movement']['bashes'] == expected, after
        print(name + ': passed', flush=True)
    reset()
    c.command(f'summon wither .5 160 4 {{Tags:["{TAG}"],NoAI:1b,NoGravity:1b,Invul:0}}')
    c.ticks(2); before = c.start(); original = entities()
    c.key('orimod.bash', 5); c.ticks(10)
    after = c.finish('boss', before, original=original, entities=entities())
    assert after['server']['movement']['bashes'] == before['server']['movement']['bashes'], after
    assert after['server']['movement']['cancelledBashes'] == before['server']['movement']['cancelledBashes'], after
    for kind, expected_hits in [('villager', 0), ('husk', 1)]:
        reset()
        c.command('item replace entity @s hotbar.0 with orimod:spirit_blade')
        c.action('select_slot', slot=0)
        c.command(f'summon {kind} .5 160 2.2 {{Tags:["{TAG}"],NoAI:1b,Silent:1b,PersistenceRequired:1b}}')
        c.ticks(3)
        original = next(e for e in entities() if e['type'] == 'entity.minecraft.' + kind)
        before = c.start(); c.key('attack', 1); c.ticks(18)
        current = next(e for e in entities() if e['id'] == original['id'])
        after = c.finish('blade-' + kind, before, original=original, target=current)
        assert after['server']['combat']['bladeStarts'] == before['server']['combat']['bladeStarts'] + 1, after
        assert after['server']['combat']['bladeHits'] - before['server']['combat']['bladeHits'] == expected_hits, after
        assert current['health'] == original['health'] if expected_hits == 0 else current['health'] < original['health']
        print('blade-' + kind + ': passed', flush=True)
    c.records['status'] = 'passed'; c.save()
except Exception as error:
    c.records.update(status='failed', error=repr(error)); c.save(); raise
finally:
    c.action('release_keys'); c.command(f'kill @e[tag={TAG}]')
    c.command('gamerule fallDamage true'); c.command('difficulty peaceful')
    c.command('tp @s .5 160 .5')
