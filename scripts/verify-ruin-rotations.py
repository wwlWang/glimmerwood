"""Run all 12 template/orientation combinations through real controls and shrine UI."""
import argparse
import shutil
import time
from pathlib import Path
from devtools_client import DevClient, ROOT
from ruin_driver import RuinDriver

p = argparse.ArgumentParser()
p.add_argument('build_id')
p.add_argument('--base', type=int, default=4096)
p.add_argument('--rotations', nargs='+', default=['NONE', 'CLOCKWISE_90', 'CLOCKWISE_180', 'COUNTERCLOCKWISE_90'])
p.add_argument('--kinds', nargs='+', default=['forest_spring', 'creek_ruins', 'cave_altar'])
args = p.parse_args()
c = DevClient(args.build_id)
c.records['fixture_note'] = 'Templates at isolated sky positions in the dedicated audit world; normal movement, shrine GUI and damage complete every route. No natural-density or survival claim.'
previous_auto_jump = c.request()['autoJump']
try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.action('settings', autoJump=False)
    c.action('camera', mode='FIRST_PERSON')
    for command in ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 9000', 'weather clear']:
        c.command(command)
    for ri, rotation in enumerate(args.rotations):
        for ki, kind in enumerate(args.kinds):
            driver = RuinDriver(c, kind, (args.base + ki * 96 + 40, 160, args.base + ri * 96 + 40), rotation)
            label = kind + '-' + rotation.lower()
            driver.prepare()
            baseline = driver.start()
            before = c.start()
            video = kind == 'creek_ruins' and rotation == 'CLOCKWISE_90'
            if video: c.action('record_video', name=args.build_id + '-' + label, fps=30, seconds=15)
            driver.run()
            state = c.action('ruin_state')
            progress = c.action('progress_state')['server']['progress']
            old = baseline['rewards'].get('clear/' + driver.key, 0)
            assert not state['active'], state
            assert progress['rewards'].get('clear/' + driver.key) == old + 1, progress
            assert progress['light'] - baseline['light'] == (30 if old == 0 else 3), (baseline, progress)
            assert not [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.orimod.practice_target']
            c.finish(label, before, rotation=rotation, origin=driver.origin, progress=progress)
            c.screenshot(label)
            if video:
                deadline = time.monotonic() + 30
                while (state := c.request())['video']['recording'] or state['video']['pending']:
                    assert not state['video']['error'] and time.monotonic() < deadline, state['video']
                    time.sleep(.2)
                result = c.action('save_video'); manifest = Path(result['manifest'])
                if not manifest.is_absolute(): manifest = ROOT / 'run-devtools' / manifest
                shutil.copy2(manifest.parent / 'capture.mp4', c.out / (label + '.mp4'))
                shutil.copy2(manifest, c.out / (label + '-video.json'))
            print(label, 'passed', flush=True)
    c.records['status'] = 'passed'
except Exception as error:
    c.records.update(status='failed', error=repr(error))
    try:
        c.records['failure'] = {'state': c.request(), 'spirit': c.spirit(), 'ruin': c.action('ruin_state')}
        result = c.action('save_motion', name=args.build_id + '-failed')
        shutil.copy2(result['path'], c.out / 'failed-motion.json')
    except Exception:
        pass
    raise
finally:
    c.save()
    c.action('release_keys')
    c.action('settings', autoJump=previous_auto_jump)
    c.action('open_pause')
