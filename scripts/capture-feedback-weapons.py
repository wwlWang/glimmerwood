"""Repeatable real-input A–E evidence. Run against an already open devtools world.

DISPLAY=:0 python3 scripts/capture-feedback-weapons.py <unique-evidence-id>
No asset replacement, synthetic flight flag or render-only weapon shortcuts.
"""
import json
import sys
import time
from devtools_client import DevClient

c = DevClient(sys.argv[1])
c.action('focus_game'); c.action('close_screen'); c.ready()
c.action('release_keys'); c.action('review_camera', enabled=False)
c.action('settings', autoJump=False, mainArm='RIGHT', fov=60, renderDistance=2,
         hideGui=False, vsync=False, fpsLimit=60)
c.action('shaders', enabled=False)
for cmd in ['difficulty peaceful', 'gamerule doDaylightCycle false',
            'gamerule doWeatherCycle false', 'time set 6000', 'weather clear',
            'forceload add 288 288 320 320',
            'fill 288 199 288 320 199 320 minecraft:gray_concrete',
            'fill 288 200 288 320 215 320 minecraft:air',
            'tp @s 304.5 200 304.5 0 0', 'ori unlock all', 'clear @s']:
    c.command(cmd)

def shot(name):
    c.ticks(6)
    c.records['steps'][name] = c.spirit()
    c.screenshot(name); c.save()
    print(name, flush=True)

try:
    for form, mode in [('off', 'creative'), ('on', 'creative'), ('on', 'survival')]:
        c.command('gamemode survival'); c.command('gamemode ' + mode)
        c.command('ori form ' + form); c.ticks(15)
        c.reset(304.5, 200, 304.5)
        before = c.start()
        c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True},
            {'at': 1, 'key': 'key.jump', 'down': False},
            {'at': 4, 'key': 'key.jump', 'down': True},
            {'at': 5, 'key': 'key.jump', 'down': False}])
        c.ticks(14)
        c.finish('double-tap-' + mode + '-' + form, before)
        print('double-tap', mode, form, flush=True)

    c.command('gamemode creative'); c.command('clear @s')
    weapons = ['spirit_blade', 'spirit_hammer', 'spirit_spear', 'spirit_bow', 'iron_sword', 'spirit_core']
    for slot, item in enumerate(weapons):
        namespace = 'minecraft' if item == 'iron_sword' else 'orimod'
        c.command(f'item replace entity @s hotbar.{slot} with {namespace}:{item}')
    c.command('give @s minecraft:arrow 64')
    for form in ['on', 'off']:
        c.command('ori form ' + form); c.ticks(15)
        c.reset(304.5, 200, 304.5)
        for slot, item in enumerate(weapons):
            c.action('select_slot', slot=slot); c.ticks(15)
            for view, xyz in [('front', (0, 1, 3)), ('side', (3, 1, 0)), ('back', (0, 1, -3))]:
                if form == 'off' and item == 'spirit_core': continue
                c.action('review_camera', enabled=True, x=xyz[0], y=xyz[1], z=xyz[2],
                         targetHeight=.7 if form == 'on' else .9, follow=False)
                shot(f'{form}-{item}-{view}')
            if form == 'off': continue
            c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON')
            c.action('look', yaw=0, pitch=0)
            shot(f'{form}-{item}-first')
            c.action('open_inventory'); time.sleep(.5)
            c.screenshot(f'{form}-{item}-gui')
            c.action('close_screen'); c.ticks(3)
    c.command('ori form on'); c.ticks(10); c.action('select_slot', slot=3)
    c.action('camera', mode='FIRST_PERSON')
    c.key('use', 60); shot('bow-pulling-first'); c.action('release_keys')
    for item in ['spirit_bow', 'spirit_core']:
        c.command('kill @e[type=minecraft:item]')
        c.command(f'summon minecraft:item 304.5 200.3 306 {{Item:{{id:"orimod:{item}",count:1}},PickupDelay:32767,NoGravity:1b}}')
        c.action('look', yaw=0, pitch=10); shot(item + '-dropped')
    c.records['status'] = 'captured'
finally:
    c.action('release_keys'); c.action('close_screen'); c.action('review_camera', enabled=False)
    c.action('settings', hideGui=False)
    c.save()
