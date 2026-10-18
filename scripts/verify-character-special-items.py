"""Real vanilla special item paths; captures contacts without forcing animation clips."""
import sys
from devtools_client import DevClient

c=DevClient(sys.argv[1])
def capture(name):
    c.screenshot(name);c.records['steps'][name]=c.spirit()
try:
    c.action('close_screen');c.action('focus_game');c.ready();c.reset()
    c.command('item replace entity @s weapon.offhand with air')
    c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
    c.action('select_slot',slot=0)
    for arm in ['RIGHT','LEFT']:
        c.action('settings',mainArm=arm,fov=70,hideGui=False)
        for item in ['crossbow','trident','fishing_rod','spyglass','potion','map']:
            c.action('camera',mode='FIRST_PERSON')
            c.action('settings',fov=70,hideGui=False)
            c.command('item replace entity @s hotbar.0 with minecraft:'+item)
            c.ticks(20);name=arm.lower()+'-'+item
            capture(name+'-first-idle')
            c.key('use',45);c.ticks(15);capture(name+'-first-use')
            c.ticks(35);c.action('release_keys')
            # The throw consumed the trident. The map now has a real map id.
            if item=='trident':c.command('item replace entity @s hotbar.0 with minecraft:trident')
            c.action('settings',fov=45,hideGui=True)
            c.action('review_camera',enabled=True,x=-2.1,y=.8,z=1.7,targetHeight=.65)
            c.ticks(15);capture(name+'-third-idle')
            if item not in ['map','potion','fishing_rod']:
                if item=='crossbow':c.command('item replace entity @s hotbar.0 with minecraft:crossbow')
                c.key('use',45);c.ticks(15);capture(name+'-third-use')
                c.ticks(35);c.action('release_keys')
            c.action('review_camera',enabled=False)
    c.action('release_keys');c.reset();c.action('camera',mode='FIRST_PERSON')
    c.action('settings',mainArm='RIGHT',fov=70,hideGui=False)
    c.command('item replace entity @s hotbar.0 with air');c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error));raise
finally:c.save()
