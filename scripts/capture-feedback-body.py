"""Dark-room control for world body, first-person paws and inventory preview. DISPLAY=:0."""
import sys
import time
from devtools_client import DevClient

c = DevClient(sys.argv[1])
c.action('focus_game'); c.action('close_screen'); c.ready()
c.action('settings', fov=60, renderDistance=2, hideGui=False)
for cmd in ['kill @e[type=minecraft:item]', 'gamemode survival', 'clear @s', 'ori form on',
            'time set midnight', 'forceload add 288 288 320 320',
            'fill 298 199 298 310 207 310 minecraft:gray_concrete hollow']:
    c.command(cmd)
c.reset(304.5, 200, 304.5)
for pack in ['none', 'Spirit Glow.zip']:
    if pack == 'none': c.action('shaders', enabled=False)
    else: c.action('iris_pack', name=pack)
    c.command('clear @s'); c.ticks(20)
    c.action('review_camera', enabled=True, x=0, y=1, z=3, targetHeight=.7, follow=False)
    c.ticks(10); c.screenshot(pack.split()[0] + '-third')
    c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON')
    c.action('look', yaw=0, pitch=15)
    for held in ['empty', 'sword']:
        if held == 'sword': c.command('item replace entity @s weapon.mainhand with minecraft:iron_sword')
        c.ticks(20); c.screenshot(pack.split()[0] + '-arm-' + held)
    c.command('clear @s'); c.ticks(10)
    c.action('open_inventory'); time.sleep(.8)
    c.screenshot(pack.split()[0] + '-inventory'); c.action('close_screen')
    c.records['steps'][pack] = c.spirit(); c.save()
c.action('shaders', enabled=False)
c.records['status'] = 'captured'; c.save()
