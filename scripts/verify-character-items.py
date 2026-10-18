"""Repeatable item/material captures in the preserved character fixture."""
import sys
import time
from devtools_client import DevClient

c=DevClient(sys.argv[1])
try:
    deadline=time.monotonic()+90
    while c.request()['overlay']!='none':
        assert time.monotonic()<deadline
        time.sleep(.2)
    if 'player' not in c.request():
        c.action('open_world',name='orimod_dev_character_20261018')
        c.ready()
    c.action('close_screen'); c.action('focus_game'); c.ready()
    c.action('camera',mode='THIRD_PERSON_FRONT')
    c.reset(yaw=0,pitch=0)
    c.command('time set 8000')
    c.command('item replace entity @s weapon.offhand with air')
    for name,item in [('pickaxe','iron_pickaxe'),('torch','torch'),('food','apple'),('bow','bow')]:
        c.command('item replace entity @s hotbar.0 with minecraft:'+item)
        c.action('select_slot',slot=0); c.ticks(18)
        c.screenshot(name+'-front')
        c.records['steps'][name]=c.spirit()
    c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe')
    c.command('item replace entity @s weapon.offhand with minecraft:shield')
    c.ticks(15);c.screenshot('pickaxe-shield-front')
    c.records['steps']['both-hands']=c.spirit()
    c.action('camera',mode='THIRD_PERSON_BACK');c.ticks(5);c.screenshot('pickaxe-shield-back')
    c.command('item replace entity @s hotbar.0 with air')
    c.command('item replace entity @s weapon.offhand with air')
    c.action('camera',mode='THIRD_PERSON_FRONT')
    c.action('shaders',enabled=False);c.ticks(40);c.screenshot('day-shaders-off')
    c.records['steps']['shaders-off']=c.spirit()
    c.action('shaders',enabled=True);c.ticks(40)
    c.command('time set 18000');c.ticks(10);c.screenshot('night-shaders-on')
    c.records['steps']['shaders-on']=c.spirit()
    c.command('time set 8000');c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error))
    raise
finally:
    c.save()
