"""Exercise the real crosshair, mouse actions and vanilla menus in both forms/views."""
import math
import sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])


def aim(x, y, z):
    state = c.spirit()['client']; at = state['position']
    dx, dy, dz = x-at[0], y-at[1]-state['eyeHeight'], z-at[2]
    c.action('look', yaw=math.degrees(math.atan2(-dx, dz)), pitch=math.degrees(math.atan2(-dy, math.hypot(dx, dz))))
    c.ticks(8)
    return c.request()['hit']


def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)['block']


def inventory(item):
    return sum(s['count'] for s in c.action('progress_state')['server']['inventory'] if s['item'] == 'minecraft:'+item)


def capture(name, **extra):
    c.screenshot(name); c.records['steps'][name] = dict(state=c.spirit(), observed=c.request(), **extra); c.save()


def click(slot):
    c.action('gui_click', x=slot['x']+8, y=slot['y']+8); c.ticks(8)


try:
    c.action('close_screen'); c.action('focus_game'); c.ready(); c.action('review_camera', enabled=False)
    c.command('gamemode creative'); c.command('fill -10 99 -10 10 99 10 smooth_stone')
    c.command('fill -10 100 -10 10 107 10 air'); c.reset()
    c.command('gamemode survival'); c.command('time set noon'); c.command('weather clear')
    c.action('shaders', enabled=True); c.action('settings', fov=70, hideGui=False)
    for spirit in [False, True]:
        if c.spirit()['server']['form'] != spirit:
            c.action('toggle_form'); c.ticks(15)
        for camera in ['FIRST_PERSON', 'THIRD_PERSON_BACK']:
            name = ('spirit-' if spirit else 'human-') + camera.lower()
            c.action('camera', mode=camera); c.action('select_slot', slot=0); c.reset()
            c.command('clear @s'); c.command('kill @e[tag=orimod_r14_trade]')
            c.command('fill -1 100 2 1 102 4 air')
            c.command('fill -1 100 3 1 100 3 minecraft:stone')
            c.command('item replace entity @s hotbar.0 with minecraft:diamond_pickaxe')
            c.ticks(8); hit=aim(.5,100.5,3.5)
            assert hit['type']=='BLOCK' and hit['block']==[0,100,3], hit
            capture(name+'-mine-before', hit=hit)
            c.key('attack', 22); c.ticks(26); c.action('release_keys')
            assert block(0,100,3)=='minecraft:air'
            assert block(-1,100,3)==block(1,100,3)=='minecraft:stone'
            capture(name+'-mined')

            c.command('setblock 0 100 3 minecraft:stone')
            c.command('item replace entity @s hotbar.0 with minecraft:oak_planks 8')
            c.ticks(8); hit=aim(.5,100.5,3.5)
            assert hit['block']==[0,100,3] and hit['face']=='NORTH', hit
            c.key('use',1); c.ticks(8)
            assert block(0,100,2)=='minecraft:oak_planks' and inventory('oak_planks')==7
            capture(name+'-placed', hit=hit)

            c.command('setblock 0 100 2 air'); c.command('item replace entity @s hotbar.0 with air')
            c.command('setblock 0 100 3 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:apple",count:3}]}')
            c.ticks(8); hit=aim(.5,100.5,3.5)
            assert hit['block']==[0,100,3]
            c.key('use',1); c.ticks(8)
            menu=c.action('container_state'); assert menu['screen']=='ContainerScreen'
            assert menu['slots'][0]['item']=='minecraft:apple' and menu['slots'][0]['count']==3
            capture(name+'-chest-open', menu=menu)
            destination=next(s for s in menu['slots'] if s['index']>=27 and s['item']=='minecraft:air')
            click(menu['slots'][0]); click(destination)
            assert inventory('apple')==3
            c.action('gui_key', key=256); c.action('focus_game'); c.ticks(8)
            assert c.request()['screen']=='gameplay'

            c.command('setblock 0 100 3 air'); c.command('item replace entity @s hotbar.0 with minecraft:emerald 2')
            c.command('summon minecraft:villager .5 100 3.5 {Tags:["orimod_r14_trade"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",type:"minecraft:plains",level:2},Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:bread",count:2},uses:0,maxUses:99,rewardExp:0b}]}}')
            c.ticks(10); hit=aim(.5,100.8,3.5)
            assert hit['type']=='ENTITY'
            c.key('use',1); c.ticks(10)
            menu=c.action('container_state'); assert menu['screen']=='MerchantScreen'
            capture(name+'-trade-open', menu=menu, hit=hit)
            payment=next(s for s in menu['slots'] if s['index']>=3 and s['item']=='minecraft:emerald')
            click(payment); click(menu['slots'][0])
            menu=c.action('container_state'); assert menu['slots'][2]['item']=='minecraft:bread'
            click(menu['slots'][2]); click(payment)
            assert inventory('bread')==2
            c.action('gui_key', key=256); c.action('focus_game'); c.ticks(10)
            assert inventory('emerald')==1
            capture(name+'-traded')
            c.command('kill @e[tag=orimod_r14_trade]')
            c.key('forward', 10); c.ticks(14)
            assert c.spirit()['server']['position'][2] > 1, 'Control did not resume after menus'
    c.records.update(status='passed', visual_review='pending', limits='Fixtures and materials supplied by commands. All interactions use the actual crosshair and vanilla input/menu paths.')
except Exception as error:
    c.records.update(status='failed', error=str(error)); raise
finally:
    c.action('release_keys'); c.action('review_camera', enabled=False)
    if c.request()['screen']!='gameplay': c.action('close_screen')
    c.action('open_pause'); c.save()
