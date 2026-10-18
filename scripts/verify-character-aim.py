"""Actual bow use at several pitch angles, with a passive side camera and inventory views."""
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
    c.command('item replace entity @s weapon.offhand with air')
    c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
    c.action('settings',fov=45,hideGui=True)
    for arm in ['RIGHT','LEFT']:
        c.action('settings',mainArm=arm)
        c.command('item replace entity @s hotbar.0 with minecraft:bow')
        c.action('select_slot',slot=0)
        c.action('review_camera',enabled=True,x=-2.8,y=.8,z=0,targetHeight=.68)
        for pitch in [-85,-55,0,55,85]:
            c.action('look',yaw=0,pitch=pitch);c.key('use',60);c.ticks(30)
            name=arm.lower()+'-pitch-'+str(pitch)
            c.screenshot(name);c.records['steps'][name]=c.spirit()
            c.records['steps'][name]['itemPlacement']=c.action('item_placement')
            c.action('release_keys');c.ticks(24)
    c.action('review_camera',enabled=False)
    c.action('look',yaw=0,pitch=0)
    c.action('settings',mainArm='RIGHT',fov=70,hideGui=False)
    c.action('camera',mode='FIRST_PERSON')
    c.command('item replace entity @s hotbar.0 with air')
    c.action('open_inventory');c.ticks(10);c.screenshot('inventory-on')
    c.action('shaders',enabled=False);c.ticks(15);c.screenshot('inventory-off')
    c.action('shaders',enabled=True);c.ticks(15)
    c.action('close_screen');c.ticks(10);c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error));raise
finally:c.save()
