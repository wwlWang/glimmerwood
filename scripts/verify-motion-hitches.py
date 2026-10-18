"""Continuous Bash/glide travel during integrated-server stalls must retain air charges."""
import argparse
import json
import math
from devtools_client import DevClient, ROOT
from ruin_driver import key

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--observe', action='store_true')
args = p.parse_args(); c = DevClient(args.build_id)
original_auto = next(line.split(':', 1)[1] == 'true' for line in (ROOT / 'run-devtools/options.txt').read_text(encoding='utf-8').splitlines() if line.startswith('autoJump:'))
try:
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.command('gamemode creative'); c.command('fill 117 219 -3 123 219 0 moss_block')
    c.command('fill 117 200 1 123 228 40 air'); c.command('fill 117 220 -3 123 228 0 air')
    c.command('tp @s 120.5 220 .5 0 0'); c.ticks(8); c.command('gamemode survival')
    c.command('gamerule fallDamage false'); c.command('difficulty peaceful'); c.command('effect clear @s')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    c.action('grant_bash'); c.action('grant_air_movement'); c.action('settings', autoJump=False)
    c.command('setblock 120 221 4 orimod:bash_anchor')
    glide_descents = []
    for milliseconds in [0, 600, 1000, 1500]:
        c.reset(120.5, 220, .5); c.ticks(40); before = c.start()
        steps = [key(0, 'orimod.bash', True), key(2, 'orimod.bash', False)]
        if milliseconds: steps.append({'at': 4, 'action': {'op': 'server_hitch', 'milliseconds': milliseconds}})
        c.action('input_script', steps=steps); c.ticks(45)
        name = 'bash-' + str(milliseconds); after = c.finish(name, before)
        rows = json.loads((c.out / (name + '.json')).read_text(encoding='utf-8'))['samples']
        server = [r['state'] for r in rows if r['side'] == 'server']
        max_step = max(math.dist(a['position'], b['position']) for a, b in zip(server, server[1:]))
        result = {'max_server_step': max_step, 'airJumpSpent': after['server']['movement']['airJumpSpent'],
                  'airDashSpent': after['server']['movement']['airDashSpent'], 'position': after['server']['position']}
        c.records['steps'][name].update(result); c.save(); print(name, result, flush=True)
        assert after['server']['movement']['bashes'] == before['server']['movement']['bashes'] + 1
        assert not after['server']['movement']['grounded']
        if not args.observe:
            assert not result['airJumpSpent'] and not result['airDashSpent'], result
            assert after['server']['movement']['relocations'] == before['server']['movement']['relocations']
    for milliseconds in [0, 600, 1000, 1500]:
        c.reset(120.5, 220, .5); c.key('jump', 100); c.key('forward', 75); c.ticks(18)
        before = c.start(); assert before['server']['movement']['mode'] == 'glide', before
        if milliseconds: c.action('server_hitch', milliseconds=milliseconds)
        c.ticks(40); name = 'glide-' + str(milliseconds); after = c.finish(name, before)
        rows = json.loads((c.out / (name + '.json')).read_text(encoding='utf-8'))['samples']
        server = [r['state'] for r in rows if r['side'] == 'server']
        max_step = max(math.dist(a['position'], b['position']) for a, b in zip(server, server[1:]))
        result = {'max_server_step': max_step, 'descent': before['server']['position'][1] - after['server']['position'][1],
                  'mode': after['server']['movement']['mode']}
        glide_descents.append(result['descent'])
        c.records['steps'][name].update(result); c.save(); print(name, result, flush=True)
        if not args.observe:
            for side in ['server', 'client']:
                a, b = before[side]['movement'], after[side]['movement']
                assert b['mode'] == 'glide' and b['airJumpSpent'] == a['airJumpSpent'] and b['airDashSpent'] == a['airDashSpent']
                assert b['relocations'] == a['relocations']
    if not args.observe:
        assert max(glide_descents) - min(glide_descents) < .6, ('Stalls changed glide descent', glide_descents)
    c.records['status'] = 'observed' if args.observe else 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('release_keys'); c.command('gamemode creative'); c.command('tp @s .5 160 .5')
    c.ticks(8); c.command('gamemode survival')
    c.command('gamerule fallDamage true'); c.action('settings', autoJump=original_auto); c.action('open_pause')
