"""Inventory preview with both hands, shader modes and supported GUI scales."""
import sys
from devtools_client import DevClient
c=DevClient(sys.argv[1])
try:
    c.action('close_screen');c.action('focus_game');c.ready();c.reset()
    c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe')
    c.command('item replace entity @s weapon.offhand with minecraft:shield')
    c.action('select_slot',slot=0)
    c.action('open_inventory')
    for enabled in [True,False]:
        c.action('shaders',enabled=enabled);c.ticks(20)
        for width,height,scale in [(1920,1080,3),(1920,1080,4),(1280,720,2),(854,480,2)]:
            c.action('resize',width=width,height=height);c.ticks(10)
            c.action('gui_scale',scale=scale);c.ticks(10)
            for arm in ['RIGHT','LEFT']:
                c.action('settings',mainArm=arm);c.ticks(5)
                name=f'{"on" if enabled else "off"}-{width}-{height}-{scale}-{arm.lower()}'
                c.screenshot(name);c.records['steps'][name]=c.request()
    c.action('resize',width=1920,height=1080);c.ticks(10)
    c.action('gui_scale',scale=3);c.ticks(10)
    c.action('shaders',enabled=True);c.ticks(20)
    c.action('settings',mainArm='RIGHT')
    c.action('close_screen');c.command('item replace entity @s hotbar.0 with air')
    c.command('item replace entity @s weapon.offhand with air')
    c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error));raise
finally:c.save()
