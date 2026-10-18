"""Real key inputs while deliberately stalling only the integrated server for <=500 ms."""
import argparse
import json
from devtools_client import DevClient
from ruin_driver import key

p = argparse.ArgumentParser(); p.add_argument('build_id')
args = p.parse_args(); c = DevClient(args.build_id)
try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.command('gamemode creative')
    c.command('fill 36 99 36 48 99 60 moss_block'); c.command('fill 36 100 36 48 111 60 air')
    c.command('tp @s 40.5 100 40.5'); c.ticks(8)
    assert c.spirit()['server']['movement']['grounded'], 'Fixture must land before leaving creative mode'
    c.command('gamemode survival'); c.command('difficulty peaceful')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(3)
    peaks = []
    for milliseconds, at in [(0, 0), (150, 0), (300, 0), (500, 0), (300, 4), (500, 4)]:
        c.reset(40.5, 100, 40.5); before = c.start()
        steps = [key(2, 'jump', True), key(3, 'jump', False), key(8, 'jump', True), key(9, 'jump', False),
                 key(14, 'jump', True), key(15, 'jump', False)]
        if milliseconds: steps.insert(0, {'at': at, 'action': {'op': 'server_hitch', 'milliseconds': milliseconds}})
        c.action('input_script', steps=steps); expected_edge = before['client']['movement']['gameTime'] + 3
        c.ticks(65); name = f'hitch-{milliseconds}-at-{at}'; after = c.finish(name, before)
        samples = json.loads((c.out / (name + '.json')).read_text())['samples']
        clients = [s for s in samples if s['side'] == 'client']
        peak = max(s['state']['position'][1] for s in clients); peaks.append(peak)
        first_lift = next(s['tick'] for s in clients if s['state']['position'][1] > 100.05)
        accepted = after['server']['movement']['jumps'] - before['server']['movement']['jumps']
        assert accepted == 2, (name, accepted, after)
        assert after['client']['movement']['pendingPredictions'] == 0
        assert after['client']['movement']['predictionMismatches'] == before['client']['movement']['predictionMismatches']
        assert after['client']['movement']['predictionRejections'] == before['client']['movement']['predictionRejections']
        assert 102.2 <= peak <= 102.8, (name, peak)
        assert -1 <= first_lift - expected_edge <= 2, (name, first_lift, expected_edge)
        assert abs(after['server']['position'][1] - 100) < .01
        c.records['steps'][name].update({'accepted_jumps': accepted, 'peak_y': peak,
                                        'response_client_ticks': first_lift - expected_edge})
        c.save(); print(name, 'accepted', accepted, 'peak', round(peak, 3), 'response ticks', first_lift - expected_edge, flush=True)
    assert max(peaks) - min(peaks) < .15, peaks
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save()
    try: c.action('release_keys'); c.action('open_pause')
    except Exception: pass
