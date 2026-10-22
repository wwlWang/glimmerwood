"""Close third-person review of Ori: the held-item arm pose for every weapon (front/side/back three-quarter stills),
and short close videos of idle, run, jump and the blade combo. Visual review only.
Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/capture-ori-closeup.py <build_id> [--only hold,idle,run,jump,combo]"""
import argparse, subprocess, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--only', default='hold,idle,run,jump,combo')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 5300, 120, 5300
ITEMS = ['air', 'orimod:spirit_blade', 'orimod:spirit_spear', 'orimod:spirit_hammer', 'orimod:spirit_bow',
         'orimod:spirit_core', 'minecraft:iron_sword', 'minecraft:torch']
# Ori faces +Z (yaw 0); its right hand is toward -X.
VIEWS = {'front': (0, .75, 2.3), 'right': (-2.3, .75, .2), 'left': (2.3, .75, .2), 'back34': (1.6, 1.0, -1.7)}


def video(name, seconds, inputs, fps=20):
    c.action('record_video', name=args.build_id + '-' + name, fps=fps, seconds=seconds)
    inputs()
    deadline = time.monotonic() + seconds + 30
    while time.monotonic() < deadline:
        try: c.action('save_video'); break
        except RuntimeError: time.sleep(.5)
    time.sleep(3)
    folder = c.out / name; folder.mkdir()
    source = c.out.parent.parent / 'run-devtools' / 'devtools' / (args.build_id + '-' + name) / 'capture.mp4'
    if not source.exists(): c.records.setdefault('missing_video', []).append(name); return
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', str(source), '-vf', 'fps=10,scale=480:-1,tile=6x4',
                    '-frames:v', '1', str(folder / 'sheet.png')], check=False)
    subprocess.run(['cp', str(source), str(folder / 'capture.mp4')])


try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.action('resize', width=1600, height=900); c.ticks(10)
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['difficulty peaceful', 'time set 6000', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', 'gamerule sendCommandFeedback false', 'gamemode survival',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori awaken', 'ori form on', 'clear @s',
                    'ori companion flame off', 'kill @e[type=!minecraft:player]']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command(f'tp @s {X} {Y + 20} {Z}'); c.ticks(60)
    for y in range(Y, Y + 12, 4):
        c.command(f'fill {X - 20} {y} {Z - 20} {X + 20} {y + 3} {Z + 20} minecraft:air')
    c.command(f'fill {X - 20} {Y - 1} {Z - 20} {X + 20} {Y - 1} {Z + 20} minecraft:moss_block')
    c.action('settings', hideGui=True)

    if 'hold' in only:
        for item in ITEMS:
            c.command('clear @s')
            if item != 'air': c.command(f'item replace entity @s hotbar.0 with {item}')
            c.action('select_slot', slot=0)
            c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(30)  # settle into the idle carry pose
            for view, (dx, dy, dz) in VIEWS.items():
                c.action('review_camera', enabled=True, x=dx, y=dy, z=dz, targetHeight=.65, follow=False); c.ticks(6)
                c.screenshot(f'hold-{item.split(":")[-1]}-{view}')
            c.action('review_camera', enabled=False)
        c.command('clear @s')

    if 'idle' in only:
        c.command('item replace entity @s hotbar.0 with orimod:spirit_blade'); c.action('select_slot', slot=0)
        c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(10)
        c.action('review_camera', enabled=True, x=-2.2, y=.7, z=1.2, targetHeight=.6, follow=False)
        video('idle', 4, lambda: c.ticks(1))

    if 'run' in only:
        c.command('clear @s')  # empty hands: a held weapon's carry layer would hold the arms still
        c.reset(X - 14.5, Y, Z + .5, -90, 0); c.ticks(10)
        c.action('review_camera', enabled=True, x=0, y=.55, z=2.4, targetHeight=.6, follow=True)
        def run():
            c.key('forward', 50); c.key('sprint', 50); c.ticks(20)
            c.records['run_visual'] = c.spirit().get('client', {}).get('visual')
        video('run', 3, run)
        c.action('release_keys')

    if 'jump' in only:
        c.reset(X - 4.5, Y, Z + .5, -90, 0); c.ticks(10)
        c.action('review_camera', enabled=True, x=0, y=.9, z=3.2, targetHeight=1.2, follow=True)
        video('jump', 3, lambda: (c.ticks(4), c.key('jump', 3), c.ticks(12), c.key('jump', 2), c.ticks(10), c.key('jump', 2)))
        c.action('release_keys')

    if 'combo' in only:
        c.reset(X + .5, Y, Z + .5, 0, 5); c.ticks(6)
        c.action('review_camera', enabled=True, x=-2.6, y=1.1, z=.8, targetHeight=.7, follow=False)
        video('combo', 3, lambda: [(c.key('attack', 1), c.ticks(6)) for _ in range(3)])

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('release_keys'); c.action('review_camera', enabled=False); c.action('settings', hideGui=False)
        c.action('camera', mode='FIRST_PERSON')
    except Exception: pass
    c.save()
    print(c.records['status'], c.out)
