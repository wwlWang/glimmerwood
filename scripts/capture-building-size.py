#!/usr/bin/env python3
"""B2 item framing references. Fixed camera/FOV; no gameplay or render overrides.
DISPLAY=:0 LIBGL_ALWAYS_SOFTWARE=1 python3 scripts/capture-building-size.py <evidence-id> <before|human|spirit>
The before pass closes the client. After passes leave it at title for the next pass.
"""
import os,sys,time,json
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);mode=sys.argv[2];c.save()
ids=['minecraft:oak_log','minecraft:cauldron','minecraft:lectern','minecraft:bell','orimod:ginso_wood','orimod:great_spirit_root','orimod:glades_well','orimod:forest_spring_shrine','orimod:creek_ruins_shrine','orimod:cave_altar_shrine','orimod:gloom_altar','orimod:storm_altar','orimod:map_stone','orimod:keystone_door','orimod:great_tree_heart','orimod:ginso_water_heart']
def cmd(text):return c.command(text)
def photo(name):
 c.ticks(8);c.screenshot(name);c.records['steps'][name]=c.request();c.save();print(name,flush=True)
try:
 c.action('open_world',name='orimod_dev_playtest_a_20261024')
 for _ in range(180):
  if 'player' in c.request() and c.request()['overlay']=='none':break
  time.sleep(.5)
 c.action('close_screen');c.action('focus_game');c.ready();c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False)
 c.action('settings',fov=65,renderDistance=4,hideGui=False,vsync=False,fpsLimit=60,mainArm='RIGHT');c.action('gui_scale',scale=3)
 for s in ['gamemode creative','ori awaken','ori companion flame off','gamerule doDaylightCycle false','gamerule doMobSpawning false','gamerule sendCommandFeedback false','time set noon','weather clear','clear @s','forceload add 288 288 320 320','tp @s 304.5 180 304.5 0 -20']:cmd(s)
 c.ticks(50);cmd('fill 288 179 288 320 179 320 minecraft:stone_bricks');cmd('fill 296 180 296 312 190 312 minecraft:air');cmd('kill @e[type=minecraft:item,distance=..25]');cmd('kill @e[type=minecraft:item_frame,distance=..25]');cmd('tp @s 304.5 180 304.5 0 -20');c.ticks(30)
 for form in (['human','spirit'] if mode=='before' else [mode]):
  cmd('ori form '+('on' if form=='spirit' else 'off'));c.ticks(25);assert c.spirit()['server']['form']==(form=='spirit')
  if mode!='before':
   c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON');cmd('tp @s 304.5 180 304.5 0 -20');c.ticks(25)
   c.action('settings',hideGui=True);photo(form+'-background');c.action('settings',hideGui=False)
  for item in (ids[:7]+['orimod:map_stone','orimod:keystone_door'] if mode=='before' else ids):
   name=item.split(':')[1]
   for context in (['main'] if mode=='before' else ['main','off','third']):
    c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON');c.action('settings',hideGui=False)
    cmd('item replace entity @s weapon.offhand with air');cmd('item replace entity @s hotbar.0 with air');c.action('select_slot',slot=0)
    cmd('item replace entity @s '+('weapon.offhand' if context=='off' else 'hotbar.0')+' with '+item)
    cmd('tp @s 304.5 180 304.5 0 '+('0' if context=='third' else '-20'));c.ticks(24)
    if context=='third':
     c.action('review_camera',enabled=True,x=1.8,y=1.1,z=2.8,targetHeight=.7,follow=True);c.action('settings',hideGui=True)
    photo(form+'-'+name+'-'+context)
 c.records['status']='complete';c.records['rig']={'fov':65,'handFovVanilla':70,'resolution':[1920,1015],'guiScale':3,'firstPersonPitch':-20,'thirdOffset':[1.8,1.1,2.8],'shader':False,'software':'llvmpipe','display':':0'}
finally:
 c.action('release_keys');c.action('review_camera',enabled=False);c.action('settings',hideGui=False);cmd('forceload remove all');c.action('leave_world');time.sleep(2)
 if mode=='before':c.action('quit')
 c.save();print('DONE',flush=True)
