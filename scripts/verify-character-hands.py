"""Capture real vanilla item-use paths with both main-hand preferences."""
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
        c.action('open_world',name='orimod_dev_character_20261018');c.ready()
    c.action('close_screen');c.action('focus_game');c.ready()
    if not c.request()['shaders']['enabled']:
        c.action('shaders',enabled=True);c.ticks(40)
    c.reset()
    c.command('item replace entity @s weapon.offhand with air')
    c.action('camera',mode='FIRST_PERSON')
    c.action('settings',fov=70,hideGui=False)
    for arm in ('RIGHT','LEFT'):
        c.action('settings',mainArm=arm)
        for label,item in [('empty','air'),('pickaxe','iron_pickaxe'),('torch','torch'),('apple','apple'),('bow','bow')]:
            c.command('item replace entity @s hotbar.0 with minecraft:'+item)
            c.action('select_slot',slot=0);c.ticks(30)
            c.screenshot(arm.lower()+'-'+label)
        c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
        c.key('use',45);c.ticks(30);c.screenshot(arm.lower()+'-bow-charge')
        c.ticks(18);c.action('release_keys')
        c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe')
        c.command('item replace entity @s weapon.offhand with minecraft:shield')
        c.ticks(15);c.key('use',40);c.ticks(12);c.screenshot(arm.lower()+'-shield-block')
        c.ticks(30);c.action('release_keys')
        c.command('item replace entity @s weapon.offhand with air')
        c.command('item replace entity @s hotbar.0 with minecraft:golden_apple 8')
        c.ticks(15);c.key('use',34);c.ticks(12);c.screenshot(arm.lower()+'-eat')
        c.ticks(25);c.action('release_keys')
    c.action('settings',mainArm='RIGHT',fov=35)
    c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe')
    c.command('effect clear @s')
    c.action('camera',mode='THIRD_PERSON_FRONT')
    c.ticks(20);c.screenshot('third-pickaxe-close')
    c.command('item replace entity @s hotbar.0 with minecraft:bow');c.ticks(12)
    c.key('use',45);c.ticks(30);c.screenshot('third-bow-charge-close')
    c.records['steps']['bow-charge']=c.spirit()
    c.ticks(20);c.action('release_keys')
    c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe')
    c.command('item replace entity @s weapon.offhand with minecraft:shield')
    c.ticks(12);c.key('use',40);c.ticks(12);c.screenshot('third-shield-block-close')
    c.records['steps']['shield-block']=c.spirit()
    c.ticks(30);c.action('release_keys')
    c.action('settings',mainArm='RIGHT',fov=70)
    c.action('camera',mode='FIRST_PERSON')
    c.command('item replace entity @s hotbar.0 with air')
    c.command('item replace entity @s weapon.offhand with air')
    c.action('shaders',enabled=False);c.ticks(30);c.screenshot('empty-shaders-off')
    c.action('shaders',enabled=True);c.ticks(30);c.screenshot('empty-shaders-on')
    c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error))
    raise
finally:
    c.save()
