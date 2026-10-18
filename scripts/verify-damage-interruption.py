"""Damage active actions through real key mappings and inspect both tick streams."""
import argparse
import json
from devtools_client import DevClient

parser = argparse.ArgumentParser()
parser.add_argument('build_id')
c = DevClient(parser.parse_args().build_id)

def press(at, key, down=True): return {'at': at, 'key': 'key.' + key, 'down': down}
def command(at, text): return {'at': at, 'action': {'op': 'command', 'command': text}}
def mode(sample, action, side):
    state = sample['state']
    if action in ['blade', 'bow']:
        return state['combat']['mode'] if side == 'server' else state['combat']['view']['mode']
    return state['movement']['mode']

try:
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.action('grant_dash'); c.action('grant_bash'); c.action('grant_air_movement')
    if not c.action('progress_state')['server']['abilities'] & 32:
        c.action('grant_light', amount=30); c.action('progress_action', action='ability', target='bow'); c.ticks(4)
    for text in ['gamemode survival', 'difficulty normal', 'gamerule doMobSpawning false',
                 'gamerule naturalRegeneration false', 'gamerule fallDamage false',
                 'fill -15 159 -15 15 159 15 moss_block', 'fill -3 160 -3 3 194 10 air',
                 'item replace entity @s hotbar.0 with orimod:spirit_blade',
                 'item replace entity @s hotbar.1 with orimod:spirit_bow',
                 'item replace entity @s hotbar.2 with minecraft:air']:
        c.command(text)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(3)
    for action in ['blade', 'bow', 'dash', 'bash_aim', 'grapple', 'stomp', 'glide']:
        cases = [(False, 0), (True, 0)] + ([(True, n) for n in range(1, 4)] if action == 'bow' else [])
        for external, repeat in cases:
            name = action + ('-attack' if external else '-environment') + (f'-repeat-{repeat}' if repeat else '')
            c.action('release_keys'); c.action('close_screen')
            c.command('kill @e[tag=orimod_damage_test]')
            c.command('setblock 0 164 4 air')
            c.reset(y=190 if action in ['stomp', 'glide'] else 160)
            c.ticks(16)  # Clear the previous case's vanilla immunity.
            c.command('effect give @s minecraft:instant_health 1 5 true')
            c.command('summon husk .5 160 4.5 {Tags:["orimod_damage_test"],NoAI:1b,Silent:1b,PersistenceRequired:1b}')
            c.action('select_slot', slot=0 if action == 'blade' else 1 if action == 'bow' else 2)
            c.action('set_energy', amount=100); c.ticks(3)
            steps = []; damage_at = 2; duration = 10
            if action == 'blade': steps = [press(0, 'attack'), press(1, 'attack', False)]
            elif action == 'bow': steps = [press(0, 'use')]; damage_at = 6 + repeat % 3; duration = damage_at + 10
            elif action == 'dash': steps = [press(0, 'orimod.dash'), press(1, 'orimod.dash', False)]
            elif action == 'bash_aim': steps = [press(0, 'orimod.bash')]; damage_at = 5
            elif action == 'grapple':
                c.command('kill @e[tag=orimod_damage_test]')
                c.command('summon husk -4 160 .5 {Tags:["orimod_damage_test"],NoAI:1b,Silent:1b}')
                c.command('setblock 0 164 4 orimod:grapple_flower'); c.action('look', yaw=0, pitch=-40)
                steps = [press(0, 'orimod.bash'), press(1, 'orimod.bash', False)]
            elif action == 'stomp':
                # Reset once more so preparation cannot consume the available falling distance.
                c.reset(y=190)
                steps = [press(0, 'sneak'), press(1, 'orimod.dash'), press(2, 'orimod.dash', False), press(3, 'sneak', False)]
                damage_at = 4
            elif action == 'glide':
                c.reset(y=190); c.key('jump', 100); c.ticks(7)
                assert c.spirit()['server']['movement']['mode'] == 'glide'
                # input_script releases keys first; keep the held glide input within this tick.
                steps = [press(0, 'jump')]; damage_at = 3
            source = 'minecraft:mob_attack by @e[tag=orimod_damage_test,limit=1]' if external else 'minecraft:on_fire'
            steps.append(command(damage_at, 'damage @s 1 ' + source))
            before = c.start(); c.action('input_script', steps=steps); c.ticks(duration)
            assert not c.request()['inputScriptError'], c.request()['inputScriptError']
            after = c.finish(name, before)
            samples = json.loads((c.out / (name + '.json')).read_text())['samples']
            detail = {}
            for side in ['server', 'client']:
                stream = [s for s in samples if s['side'] == side]
                damaged = [s for s in stream if s['state']['health'] < 20]
                assert damaged, (name, side, 'no health loss')
                hit = stream.index(damaged[0])
                assert any(mode(s, action, side) == action for s in stream[:hit]), (name, side, 'action did not start before damage')
                after_hit = stream[hit:hit + 3]
                detail[side] = {'first_damage_tick': damaged[0]['tick'], 'modes': [mode(s, action, side) for s in after_hit]}
                if external:
                    assert any(mode(s, action, side) in ['idle', 'resume'] for s in after_hit), (name, side, detail[side])
                else:
                    assert mode(damaged[0], action, side) == action, (name, side, detail[side])
            if action in ['blade', 'bow']:
                delta = after['server']['combat']['cancellations'] - before['server']['combat']['cancellations']
                assert delta == int(external), (name, delta)
            if action == 'bow' and external:
                assert not after['client']['combat']['usingItem'] and after['server']['combat']['bowShots'] == before['server']['combat']['bowShots']
            c.records['steps'][name]['damage_window'] = detail; c.save()
            print(name + ': passed', flush=True)
    c.records['status'] = 'passed'; c.save()
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); c.save(); raise
finally:
    c.action('release_keys'); c.command('kill @e[tag=orimod_damage_test]'); c.command('setblock 0 164 4 air')
    c.command('tp @s .5 160 .5 0 0'); c.command('gamerule fallDamage true'); c.command('difficulty peaceful')
    c.command('effect give @s minecraft:instant_health 1 5 true')
print(c.out, flush=True)
