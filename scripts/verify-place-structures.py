"""Exercise vanilla /place structure against full chunks in a disposable audit world."""
import argparse
import json
import time
from pathlib import Path
from devtools_client import DevClient, ROOT

p = argparse.ArgumentParser()
p.add_argument('build_id'); p.add_argument('report', type=Path)
args = p.parse_args()
report = json.loads(args.report.read_text(encoding='utf-8'))
c = DevClient(args.build_id)
log = ROOT / 'run-devtools/logs/latest.log'
log_start = log.stat().st_size
c.records['fixture_note'] = __doc__
try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.command('gamemode creative')
    for kind in ('forest_spring', 'creek_ruins', 'cave_altar'):
        row = min((x for x in report['rows'] if x['kind'] == kind), key=lambda x: x['distance'])
        x, _, z = row['position']
        c.command(f'tp @s {x + 8} 170 {z + 8} 0 0'); c.ticks(70)
        result = c.command(f'place structure orimod:{kind} {x} 70 {z}')
        assert result['success'], (kind, result)
        c.records['steps'][kind] = {'startChunkPosition':row['position'], 'result':result}
        c.save(); print(kind, 'full-world /place structure passed', flush=True)
    time.sleep(.5)
    with log.open('rb') as stream:
        stream.seek(log_start)
        lines = stream.read().decode('utf-8', errors='replace')
    assert 'WORLD_SURFACE_WG' not in lines and 'Requested chunk unavailable' not in lines, lines
    c.records['status'] = 'passed'
except Exception as error:
    c.records.update(status='failed', error=repr(error)); raise
finally:
    c.save(); c.action('release_keys'); c.action('open_pause'); c.action('save_world')
