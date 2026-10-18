"""Operate vanilla held items with a separate, passive review camera."""
import sys,time
from devtools_client import DevClient

c=DevClient(sys.argv[1])
try:
    deadline=time.monotonic()+90
    while c.request()['overlay']!='none':
        assert time.monotonic()<deadline;time.sleep(.2)
    if 'player' not in c.request():
        c.action('open_world',name='orimod_dev_character_20261018');c.ready()
    c.action('close_screen');c.action('focus_game');c.ready();c.reset()
    c.command('effect clear @s');c.command('item replace entity @s weapon.offhand with air')
    c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
    c.action('settings',fov=45,hideGui=True)
    for arm in ['RIGHT','LEFT']:
        c.action('settings',mainArm=arm)
        for label,item in [('pickaxe','iron_pickaxe'),('bow','bow')]:
            c.command('item replace entity @s hotbar.0 with minecraft:'+item)
            c.action('select_slot',slot=0);c.ticks(30)
            for angle,offset in [('side',(-2.4,.8,0)),('front',(0,.9,2.6)),('quarter',(-1.8,.9,2))]:
                c.action('review_camera',enabled=True,x=offset[0],y=offset[1],z=offset[2],targetHeight=.68)
                c.ticks(10)
                if label=='bow':c.key('use',50);c.ticks(30)
                c.screenshot(arm.lower()+'-'+label+'-'+angle)
                c.records['steps'][arm+'-'+label+'-'+angle]=c.spirit()
                if label=='bow':c.action('release_keys');c.ticks(25)
    c.action('review_camera',enabled=False)
    c.action('settings',mainArm='RIGHT',fov=70,hideGui=False)
    c.action('camera',mode='FIRST_PERSON');c.command('item replace entity @s hotbar.0 with air')
    c.ticks(15);c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error));raise
finally:c.save()
