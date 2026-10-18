"""Real local-player input, equipment and inventory checks for restored render layers."""
import shutil
import sys
import time
from devtools_client import DevClient

c = DevClient(sys.argv[1])
try:
    c.action('close_screen'); c.action('focus_game'); c.ready(); c.action('release_keys')
    c.command('gamemode creative'); c.command('effect clear @s')
    c.command('fill -10 199 -10 10 199 60 minecraft:smooth_stone')
    c.command('fill -10 200 -10 10 209 60 minecraft:air')
    c.command('tp @s .5 200 .5 0 0'); c.ticks(10)
    if not c.spirit()['server']['awakened']:
        c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
        c.action('select_slot', slot=0); c.action('use_item'); c.ticks(8); c.action('close_screen')
    if not c.spirit()['server']['form']:
        c.action('toggle_form'); c.ticks(12)
    assert c.spirit()['server']['form']
    c.command('gamemode survival'); c.ticks(8)
    c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
    c.action('settings', fov=45, hideGui=True, mainArm='RIGHT')
    c.action('select_slot', slot=0)
    for slot, item in [('head', 'diamond_helmet'), ('chest', 'diamond_chestplate'),
                       ('legs', 'diamond_leggings'), ('feet', 'diamond_boots')]:
        c.command(f'item replace entity @s armor.{slot} with minecraft:{item}')
    c.command('item replace entity @s hotbar.0 with minecraft:iron_sword')
    c.command('item replace entity @s weapon.offhand with minecraft:shield')
    c.action('shaders', enabled=True); c.ticks(20)
    c.action('review_camera', enabled=True, x=-3, y=1.1, z=2, targetHeight=.7)
    c.action('record_video', name=c.build_id + '-equipped-motion', fps=30, seconds=14)
    c.action('record_pose'); before = c.start()
    c.action('input_script', steps=[
        {'at': 20, 'key': 'key.forward', 'down': True},
        {'at': 50, 'key': 'key.jump', 'down': True}, {'at': 51, 'key': 'key.jump', 'down': False},
        {'at': 80, 'key': 'key.forward', 'down': False},
        {'at': 100, 'key': 'key.sneak', 'down': True}, {'at': 130, 'key': 'key.sneak', 'down': False},
        {'at': 145, 'action': {'op': 'command', 'command': 'item replace entity @s hotbar.0 with minecraft:bow'}},
        {'at': 145, 'action': {'op': 'command', 'command': 'item replace entity @s weapon.offhand with air'}},
        {'at': 155, 'key': 'key.use', 'down': True}, {'at': 215, 'key': 'key.use', 'down': False},
        {'at': 235, 'action': {'op': 'open_inventory'}}, {'at': 260, 'action': {'op': 'close_screen'}},
    ])
    c.ticks(290)
    result = c.action('save_pose', name=c.build_id + '-equipped-pose')
    shutil.copy2(result['path'], c.out / 'equipped-pose.json')
    after = c.finish('equipped-motion', before)
    assert after['client']['position'][2] - before['client']['position'][2] > 3
    c.records['steps']['equipped-motion']['video'] = c.action('save_video')
    c.action('release_keys')
    for shaders in [False, True]:
        c.action('shaders', enabled=shaders); c.ticks(20)
        mode = 'on-' if shaders else 'off-'
        for arm in ['RIGHT', 'LEFT']:
            c.action('settings', mainArm=arm)
            c.action('look', yaw=0, pitch=-45 if arm == 'RIGHT' else 45)
            c.key('use', 50); c.ticks(30)
            c.screenshot(mode + 'bow-' + arm.lower())
            c.records['steps'][mode + 'bow-' + arm.lower()] = dict(state=c.spirit(), item=c.action('item_placement'))
            c.action('release_keys'); c.ticks(22)
        c.action('look', yaw=0, pitch=0); c.action('settings', mainArm='RIGHT', hideGui=False)
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON')
        c.command('item replace entity @s hotbar.0 with minecraft:iron_sword')
        c.command('item replace entity @s weapon.offhand with minecraft:shield')
        c.ticks(12); c.screenshot(mode + 'first-person')
        for name, chest, head in [('armor', 'diamond_chestplate', 'diamond_helmet'),
                                  ('elytra', 'elytra', 'diamond_helmet'), ('head-item', 'elytra', 'carved_pumpkin')]:
            c.command(f'item replace entity @s armor.chest with minecraft:{chest}')
            c.command(f'item replace entity @s armor.head with minecraft:{head}')
            c.action('open_inventory'); c.ticks(8)
            assert c.request()['screen'] == 'InventoryScreen', 'Expected the survival inventory with player preview'
            c.screenshot(mode + 'inventory-' + name)
            c.action('close_screen'); c.ticks(4)
        c.command('item replace entity @s armor.chest with minecraft:diamond_chestplate')
        c.command('item replace entity @s armor.head with minecraft:diamond_helmet')
        c.command('item replace entity @s hotbar.0 with minecraft:bow')
        c.command('item replace entity @s weapon.offhand with air')
        c.action('settings', hideGui=True)
        c.action('review_camera', enabled=True, x=-3, y=1.1, z=2, targetHeight=.7)
        before_reload = c.request()['resourceReloads']
        c.action('reload_resources')
        deadline = time.monotonic() + 60
        while c.request()['resourceReloads'] == before_reload or c.request()['overlay'] != 'none':
            assert time.monotonic() < deadline, 'Resource reload did not complete'; time.sleep(.1)
        c.ticks(20); c.screenshot(mode + 'reloaded')
        c.records['steps'][mode + 'reload'] = c.spirit()
    c.action('release_keys'); c.action('review_camera', enabled=False)
    c.action('settings', fov=70, hideGui=False, mainArm='RIGHT')
    c.action('camera', mode='FIRST_PERSON'); c.action('open_pause')
    c.records.update(status='recorded', visual_review='pending')
except Exception as error:
    c.records.update(status='failed', error=str(error)); raise
finally:
    c.save()
