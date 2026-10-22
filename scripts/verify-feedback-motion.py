"""Feedback batches 2 and 3, client capture through real inputs: the v011 run/jump/land clips from a side camera, the
Spirit Edge combo (arc slashes, lunge, knockback) against practice targets, and the soft target marker on a Bash
target. Records short videos and extracts contact sheets; motion and slash quality are judged from them.
Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-feedback-motion.py <build_id> [--only run,jump,combo,first,marker]"""
import argparse, json, subprocess, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='run,jump,combo,first,marker')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 5000, 120, 5000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def video(name, seconds, inputs, fps=20):
    """Record while running inputs(), then cut the clip into a contact sheet of every other frame."""
    c.action('record_video', name=args.build_id + '-' + name, fps=fps, seconds=seconds)
    inputs()
    deadline = time.monotonic() + seconds + 30
    while time.monotonic() < deadline:
        state = c.request().get('video') or {}
        try: c.action('save_video'); break
        except RuntimeError: time.sleep(.5)
    time.sleep(3)  # ffmpeg finalises the file after the last frame
    folder = c.out / name; folder.mkdir()
    source = c.out.parent.parent / 'run-devtools' / 'devtools' / (args.build_id + '-' + name) / 'capture.mp4'
    if not source.exists(): source = None
    if source is None: c.records.setdefault('missing_video', []).append(name); return
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(source), '-vf', 'fps=10,scale=480:-1,tile=6x4',
                    '-frames:v', '1', str(folder / 'sheet.png')], check=False)
    subprocess.run(['cp', str(source), str(folder / 'capture.mp4')])

def targets_hp():
    return {e['uuid'][:8]: (round(e['health'], 1), [round(v, 2) for v in e['position']])
            for e in c.action('nearby_entities')['entities'] if 'husk' in e['type']}

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.action('resize', width=1600, height=900); c.ticks(10)
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['difficulty easy', 'time set 6000', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', 'gamerule sendCommandFeedback false', 'gamemode survival',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori awaken', 'ori form on', 'clear @s',
                    'give @s orimod:spirit_blade', 'ori companion flame off', 'kill @e[type=minecraft:husk]']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command(f'tp @s {X} {Y + 20} {Z}'); c.ticks(60)
    for y in range(Y, Y + 12, 4):
        c.command(f'fill {X - 20} {y} {Z - 20} {X + 20} {y + 3} {Z + 20} minecraft:air')
    c.command(f'fill {X - 20} {Y - 1} {Z - 20} {X + 20} {Y - 1} {Z + 20} minecraft:smooth_stone')
    c.action('select_slot', slot=0)
    c.action('settings', hideGui=True)

    if 'run' in only:
        c.reset(X - 14.5, Y, Z + .5, -90, 0); c.ticks(10)
        c.action('review_camera', enabled=True, x=0, y=.6, z=-4.5, targetHeight=.7, follow=True)
        def run():
            c.key('forward', 50); c.key('sprint', 50)
        video('run', 3, run)
        c.action('release_keys')

    if 'jump' in only:
        c.reset(X - 6.5, Y, Z + .5, -90, 0); c.ticks(10)
        c.action('review_camera', enabled=True, x=0, y=.8, z=-5, targetHeight=1.2, follow=False)
        def jump():
            c.ticks(4); c.key('jump', 3); c.ticks(12); c.key('jump', 2)
        video('jump', 3, jump)
        c.action('release_keys')

    if 'combo' in only:
        c.reset(X + .5, Y, Z + .5, 0, 5); c.ticks(6)
        # Three targets in front: straight ahead at 3.1 blocks, and 65° to either side at 2.6 (outside the old cone).
        for dx, dz in [(0, 3.1), (2.35, 1.1), (-2.35, 1.1)]:
            c.command(f'summon minecraft:husk {X + .5 + dx} {Y} {Z + .5 + dz} {{NoAI:1b,PersistenceRequired:1b,Silent:1b}}')
        c.ticks(10)
        before = targets_hp(); start = c.request()['player']['position']
        c.action('review_camera', enabled=True, x=4.5, y=3, z=-3, targetHeight=.8, follow=False)
        def combo():
            for _ in range(3):
                c.key('attack', 1); c.ticks(6)
        video('combo', 3, combo)
        c.ticks(10)
        after = targets_hp(); end = c.request()['player']['position']
        c.records['combo'] = {'before': before, 'after': after, 'start': start, 'end': end, 'combat': c.spirit()['server'].get('combat')}
        hit = sum(1 for k in before if k in after and after[k][0] < before[k][0])
        check('combo_hits_wide_fan', hit == 3, c.records['combo'])
        check('combo_lunges_forward', .5 < end[2] - start[2] < 2.2, [start, end])

    if 'first' in only:
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON')
        c.command('kill @e[type=minecraft:husk]')
        c.reset(X + .5, Y, Z + .5, 0, 10); c.ticks(6)
        c.command(f'summon minecraft:husk {X + .5} {Y} {Z + 3.2} {{NoAI:1b,PersistenceRequired:1b,Silent:1b}}')
        def combo_first():
            for _ in range(3):
                c.key('attack', 1); c.ticks(6)
        video('combo-first', 3, combo_first)

    if 'marker' in only:
        c.action('camera', mode='THIRD_PERSON_BACK')
        c.command('kill @e[type=minecraft:husk]')
        c.command('ori unlock bash')
        c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(6)
        c.command(f'summon orimod:gloom_spitter {X + .5} {Y + 1.5} {Z + 5.5} {{NoAI:1b,PersistenceRequired:1b}}')
        c.command(f'setblock {X + 3} {Y + 2} {Z + 6} orimod:bash_anchor')
        c.ticks(20)
        c.action('settings', hideGui=False)
        c.action('look', yaw=0, pitch=-8); c.ticks(10); c.screenshot('marker-spitter')
        c.action('look', yaw=-22, pitch=-12); c.ticks(10); c.screenshot('marker-anchor')
        c.command('kill @e[type=orimod:gloom_spitter]')

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('release_keys'); c.action('review_camera', enabled=False); c.action('settings', hideGui=False)
        c.action('camera', mode='FIRST_PERSON')
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:4000])
