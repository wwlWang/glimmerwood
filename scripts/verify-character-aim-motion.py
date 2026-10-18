"""Sweep real bow aim while walking and jumping; keep render-frame poses and video."""
import math, shutil, sys
from pathlib import Path
from devtools_client import DevClient

c = DevClient(sys.argv[1])
try:
    c.action('close_screen'); c.action('focus_game'); c.ready()
    c.action('settings', fov=45, hideGui=True, fpsLimit=120, vsync=False)
    c.command('item replace entity @s weapon.offhand with air')
    c.command('item replace entity @s hotbar.0 with minecraft:bow')
    c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
    c.action('select_slot', slot=0)
    for arm in ['RIGHT', 'LEFT']:
        c.reset(); c.action('settings', mainArm=arm)
        c.action('review_camera', enabled=True, x=-2.5, y=.9, z=1.5, targetHeight=.7)
        c.ticks(20)
        name = arm.lower()
        steps = [{'at':0, 'key':'key.use', 'down':True}, {'at':200, 'key':'key.use', 'down':False},
                 {'at':50, 'key':'key.forward', 'down':True}, {'at':140, 'key':'key.forward', 'down':False},
                 {'at':170, 'key':'key.jump', 'down':True}, {'at':172, 'key':'key.jump', 'down':False}]
        for tick in range(0, 201, 2):
            steps.append({'at':tick, 'action':{'op':'look', 'yaw':35*math.sin(tick*math.tau/160),
                                              'pitch':85*math.sin(tick*math.tau/200)}})
        c.action('record_video', name=c.build_id+'-'+name, fps=60, seconds=12)
        c.action('record_pose'); before=c.start()
        c.action('input_script', steps=steps); c.ticks(245)
        result=c.action('save_pose', name=c.build_id+'-'+name+'-pose')
        shutil.copy2(result['path'], c.out/(name+'-pose.json'))
        video = c.action('save_video')
        c.finish(name,before,video=video)
        manifest = Path('run-devtools') / video['manifest']
        shutil.copy2(manifest, c.out / (name + '-frames.json'))
        shutil.copy2(manifest.parent / 'capture.mp4', c.out / (name + '.mp4'))
        assert not c.request()['inputScriptError']
    c.action('release_keys'); c.reset(); c.action('review_camera', enabled=False)
    c.action('settings', mainArm='RIGHT', fov=70, hideGui=False, fpsLimit=260)
    c.command('item replace entity @s hotbar.0 with air')
    c.action('camera',mode='FIRST_PERSON'); c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error)); raise
finally: c.save()
