"""Keep the live vanilla fishing line attached through handedness, crouch and movement."""
import shutil
import sys
from pathlib import Path
from devtools_client import DevClient

c = DevClient(sys.argv[1])


def capture(name):
    c.screenshot(name); c.records['steps'][name] = c.spirit(); c.save()


try:
    c.action('close_screen'); c.action('focus_game'); c.ready(); c.reset()
    c.action('select_slot', slot=0)
    for shaders in [False, True]:
        c.action('shaders', enabled=shaders); c.ticks(20)
        prefix = 'on-' if shaders else 'off-'
        for arm in ['RIGHT', 'LEFT']:
            c.action('settings', mainArm=arm)
            for offhand in [False, True]:
                name = prefix + arm.lower() + ('-offhand' if offhand else '-mainhand')
                c.reset(); c.action('camera', mode='FIRST_PERSON')
                c.action('review_camera', enabled=False)
                c.action('settings', fov=70, hideGui=False)
                c.command('item replace entity @s weapon.mainhand with air')
                c.command('item replace entity @s weapon.offhand with air')
                slot = 'weapon.offhand' if offhand else 'weapon.mainhand'
                c.command(f'item replace entity @s {slot} with minecraft:fishing_rod')
                c.ticks(10); c.key('use', 1); c.ticks(16)
                assert len([e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.minecraft.fishing_bobber']) == 1
                capture(name + '-first')
                c.action('camera', mode='THIRD_PERSON_BACK'); c.action('settings', fov=45, hideGui=True)
                c.action('review_camera', enabled=True, x=-2.2, y=1.1, z=2.1, targetHeight=.85)
                c.ticks(8); capture(name + '-third')
                c.action('review_camera', enabled=True, x=2.2, y=1.1, z=2.1, targetHeight=.85)
                c.ticks(8); capture(name + '-opposite')
                c.key('sneak', 22); c.ticks(10); capture(name + '-crouch')
                c.action('release_keys'); c.ticks(14)
                if arm == 'RIGHT' and not offhand:
                    video_name = c.build_id + '-' + prefix + 'motion'
                    c.action('record_video', name=video_name, fps=30, seconds=6)
                    c.action('input_script', steps=[
                        {'at': 5, 'key': 'key.left', 'down': True},
                        {'at': 35, 'key': 'key.left', 'down': False},
                        {'at': 40, 'action': {'op': 'look', 'yaw': 60, 'pitch': -25}},
                        {'at': 60, 'key': 'key.sneak', 'down': True},
                        {'at': 80, 'key': 'key.sneak', 'down': False},
                        {'at': 85, 'action': {'op': 'look', 'yaw': -60, 'pitch': 25}},
                    ])
                    c.ticks(125)
                    result = c.action('save_video')
                    path = Path('run-devtools') / result['manifest']
                    shutil.copy2(path, c.out / (prefix + 'frames.json'))
                    shutil.copy2(path.parent / 'capture.mp4', c.out / (prefix + 'motion.mp4'))
                    assert not c.request()['inputScriptError']
                c.key('use', 1); c.ticks(8)
                assert not any(e['type'] == 'entity.minecraft.fishing_bobber' for e in c.action('nearby_entities')['entities'])
    c.records.update(status='passed', visual_review='pending')
except Exception as error:
    c.records.update(status='failed', error=str(error)); raise
finally:
    c.action('release_keys'); c.action('review_camera', enabled=False)
    c.action('settings', mainArm='RIGHT', fov=70, hideGui=False)
    c.command('item replace entity @s weapon.mainhand with air')
    c.command('item replace entity @s weapon.offhand with air')
    c.action('camera', mode='FIRST_PERSON'); c.action('open_pause'); c.save()
