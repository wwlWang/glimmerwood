"""Jump prediction must respect committed weapon phases, including a stalled server."""
import argparse
import json
from devtools_client import DevClient
from ruin_driver import key

p = argparse.ArgumentParser(); p.add_argument('build_id'); args = p.parse_args(); c = DevClient(args.build_id)
try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.command('gamemode survival'); c.command('difficulty peaceful')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(3)
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade'); c.action('select_slot', slot=0); c.ticks(5)
    for name, hitch, attack_at, jump_at, expected in [('windup', 0, 0, 1, 0), ('hitch-windup', 300, 2, 3, 0), ('recovery', 0, 0, 8, 1)]:
        c.reset(40.5, 100, 40.5); before = c.start()
        steps = [key(attack_at, 'attack', True), key(attack_at + 1, 'attack', False), key(jump_at, 'jump', True), key(jump_at + 1, 'jump', False)]
        if hitch: steps.append({'at': 0, 'action': {'op': 'server_hitch', 'milliseconds': hitch}})
        c.action('input_script', steps=steps); c.ticks(45); after = c.finish(name, before)
        samples = json.loads((c.out / (name + '.json')).read_text())['samples']
        peak = max(s['state']['position'][1] for s in samples if s['side'] == 'client')
        accepted = after['server']['movement']['jumps'] - before['server']['movement']['jumps']
        c.records['steps'][name].update({'accepted_jumps': accepted, 'peak_y': peak}); c.save()
        assert accepted == expected, (name, accepted)
        if expected == 0: assert peak <= 100.01, (name, peak)
        else: assert peak > 101 and after['server']['combat']['lastCancel'] == 'movement'
        print(name, accepted, peak, flush=True)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save()
    try: c.action('release_keys'); c.action('open_pause')
    except Exception: pass
