"""Three complete runs per ruin, normal entry, saved rewards, and 1080p route videos.

Run in orimod_dev_ruins. Fixture placement and travel use development commands;
this is WORLD03/05 route evidence, not the command-free survival acceptance run.
"""
import argparse
import json
import shutil
import time
from pathlib import Path
from devtools_client import DevClient, ROOT
from ruin_driver import RuinDriver

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--base', type=int, default=1024)
p.add_argument('--kinds', nargs='+', choices=('forest_spring', 'creek_ruins', 'cave_altar'), default=['forest_spring', 'creek_ruins', 'cave_altar'])
args = p.parse_args(); c = DevClient(args.build_id)
c.records['fixture_note'] = __doc__
try:
    for kind in args.kinds:
        index = ('forest_spring', 'creek_ruins', 'cave_altar').index(kind)
        driver = RuinDriver(c, kind, (args.base + index * 64, 87 if index == 2 else 94, args.base))
        driver.prepare()
        for repeat in range(3):
            baseline = driver.start(); before = c.start()
            if repeat == 0:
                c.action('record_video', name=args.build_id + '-' + kind, fps=30, seconds=15)
            driver.run()
            state = c.action('ruin_state'); progress = c.action('progress_state')['server']['progress']
            assert not state['active'], state
            old = baseline['rewards'].get('clear/' + driver.key, 0)
            assert progress['rewards']['clear/' + driver.key] == old + 1, progress
            assert progress['light'] == baseline['light'] + (30 if old == 0 else 3), (baseline, progress)
            best = progress['rewards']['best/' + driver.key]
            assert 0 < best <= 1800 and best <= baseline['rewards'].get('best/' + driver.key, 1800), progress
            targets = [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.orimod.practice_target']
            assert targets == [], targets
            c.finish(kind + '-' + str(repeat + 1), before, challenge=state, progress=progress, baseline=baseline)
            c.screenshot(kind + '-finish-' + str(repeat + 1))
            if repeat == 0:
                while (state := c.request())['video']['recording'] or state['video']['pending']:
                    assert not state['video']['error'], state['video']
                    time.sleep(.2)
                result = c.action('save_video'); manifest = Path(result['manifest'])
                if not manifest.is_absolute(): manifest = ROOT / 'run-devtools' / manifest
                shutil.copy2(manifest.parent / 'capture.mp4', c.out / (kind + '.mp4'))
                shutil.copy2(manifest, c.out / (kind + '-video.json'))
            print(kind, 'run', repeat + 1, 'passed; light', progress['light'], 'best ticks', best, flush=True)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    try:
        c.records['failure_state'] = {'player': c.spirit(), 'challenge': c.action('ruin_state')}
        c.action('save_motion', name=args.build_id + '-failed')
    except Exception:
        pass
    raise
finally:
    c.save(); c.action('release_keys'); c.action('open_pause')
