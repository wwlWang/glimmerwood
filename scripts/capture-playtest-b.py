#!/usr/bin/env python3
"""P1/P2 and B1/B2 review evidence. DISPLAY=:0; commands only arrange capture fixtures.
Run after the full suite, with the dev client idle at its title screen. No production state probes added.
"""
import json,math,os,sys,time,shutil
from pathlib import Path
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);c.save()
def world(name):
 if 'player' in c.request():c.action('leave_world');time.sleep(2)
 c.action('open_world',name=name)
 for _ in range(180):
  if 'player' in c.request() and c.request()['overlay']=='none':break
  time.sleep(.5)
 c.action('close_screen');c.action('focus_game');c.ready()
 c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('settings',fov=65,renderDistance=4,hideGui=True,vsync=False,fpsLimit=60)
 for cmd in ['difficulty peaceful','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule sendCommandFeedback false','gamerule announceAdvancements false','weather clear','time set noon','ori companion flame off']:
  c.command(cmd)
def shot(name,data=None,wait=8):
 c.ticks(wait);c.screenshot(name);c.records['steps'][name]=data if data is not None else c.request();c.save();print(name,flush=True)
def look(eye,target):
 dx,dy,dz=[b-a for a,b in zip(eye,target)]
 c.command(f'tp @s {eye[0]} {eye[1]-1.62} {eye[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
def inspect(pos):return c.action('inspect_block',**dict(zip(['x','y','z'],pos)))
def camera(x,y,z,entity=None):
 kw=dict(enabled=True,x=x,y=y,z=z,targetHeight=.8,follow=True)
 if entity is not None:kw['entity']=entity
 c.action('review_camera',**kw)
try:
 world('orimod_dev_playtest_a_20261024');c.action('resize',width=1600,height=900)
 for cmd in ['tick rate 20','gamemode survival','ori reset','ori awaken','ori form on','ori companion flame off','clear @s','tp @s 0 180 0 0 0','gamerule naturalRegeneration false']:c.command(cmd)
 c.action('animation_review',enabled=True)
 actor=c.action('spirit_capture_actor',mode='glide',x=0,y=182,z=3,yaw=0);c.records['actor']=actor;c.command('tp @s 12 180 0 0 0');c.ticks(40)
 for name,off in [('front',(0,.85,2.5)),('side',(2.5,.85,0)),('back',(0,.85,-2.5)),('diagonal',(1.77,.85,1.77))]:
  camera(*off,entity=actor['id']);c.ticks(12);shot('feather-'+name)
 c.action('spirit_capture_actor',mode='remove');c.action('review_camera',enabled=False);c.action('animation_review',enabled=False);c.command('tp @s 0 180 0 0 0')
 c.command('clear @s');c.action('settings',hideGui=False);c.ticks(210);c.command('tick rate 10');c.action('camera',mode='FIRST_PERSON');c.command('effect give @s instant_health 1 10 true');c.command('ori energy fill');c.ticks(10)
 c.command('damage @s 12 minecraft:generic');c.ticks(30)
 assert c.spirit()['server']['form'], 'Recovery capture requires spirit form'
 shot('regen-first-before',c.spirit())
 c.action('record_video',name=c.build_id+'-regen-first',fps=15,seconds=9)
 c.key('orimod.light_burst',30);c.ticks(10);shot('regen-first-gather',c.spirit(),wait=0);c.ticks(12);shot('regen-first-pulse',c.spirit(),wait=0);c.ticks(20);c.action('release_keys');shot('regen-first-after',c.spirit())
 assert c.spirit()['server']['health']>8, 'Recovery capture must include real healing'
 time.sleep(9);v=c.action('save_video');shutil.copytree(ROOT/'run-devtools'/Path(v['manifest']).parent,c.out/'regen-first-video');c.command('tick rate 20')
 # B2: ordinary inventory/item entity/item frame and held render contexts, same fixed lighting/FOV.
 c.command('gamemode creative');c.command('clear @s');c.command('ori form off');c.action('settings',hideGui=False);c.action('gui_scale',scale=3)
 ids=['ginso_wood','great_spirit_root','glades_well','forest_spring_shrine','creek_ruins_shrine','cave_altar_shrine','gloom_altar','storm_altar','map_stone','keystone_door','great_tree_heart','ginso_water_heart']
 for i,item in enumerate(ids):c.command(f'item replace entity @s '+('hotbar.'+str(i) if i<9 else 'inventory.'+str(i-9))+f' with orimod:{item}')
 c.command('gamemode survival');c.ticks(15);c.action('open_inventory');time.sleep(3);shot('items-gui',c.action('container_state'));c.action('close_screen');c.command('gamemode creative')
 c.command('forceload add 272 272 336 336');c.command('tp @s 304.5 180 304.5 0 0');c.ticks(100)
 c.command('fill 280 179 280 320 179 320 minecraft:stone_bricks');c.command('fill 296 180 298 312 188 312 minecraft:air');c.command('tp @s 304.5 180 304.5 0 0');c.ticks(60)
 assert inspect((304,179,304))['block']=='minecraft:stone_bricks','Gallery floor missing'
 c.command('setblock 304 181 308 minecraft:stone_bricks')
 for item in ids:
  c.command('item replace entity @s hotbar.0 with orimod:'+item);c.action('select_slot',slot=0);c.action('camera',mode='FIRST_PERSON');c.action('review_camera',enabled=False)
  c.command('tp @s 304.5 180 304.5 0 0');c.action('settings',hideGui=False);c.ticks(15);shot(item+'-hand')
  camera(1.6,1.3,2.6);c.action('settings',hideGui=True);shot(item+'-third');c.action('review_camera',enabled=False)
  c.command('kill @e[type=minecraft:item,distance=..20]');c.command('kill @e[type=minecraft:item_frame,distance=..20]')
  c.command(f'summon minecraft:item 303.5 180.4 307 {{Item:{{id:"orimod:{item}",count:1}},PickupDelay:32767,NoGravity:1b}}')
  c.command(f'summon minecraft:item_frame 304.5 181.5 307.97 {{Facing:2b,Fixed:1b,Item:{{id:"orimod:{item}",count:1}}}}')
  c.command('item replace entity @s hotbar.0 with air');look((304.3,181.62,305.6),(304,181,307.6));c.ticks(15);shot(item+'-drop-frame')
 c.command('kill @e[type=minecraft:item,distance=..20]');c.command('kill @e[type=minecraft:item_frame,distance=..20]')
 c.command('ori form on');c.action('settings',hideGui=False)
 for item in ['glades_well','great_tree_heart','map_stone']:
  c.command('item replace entity @s hotbar.0 with orimod:'+item);c.action('select_slot',slot=0);c.command('tp @s 304.5 180 304.5 0 0');c.ticks(15);shot(item+'-spirit-hand')
 # B1: a full authored-template fixture for the new layout; generation/NBT paths independently covered by GameTest.
 c.command('gamemode spectator');c.command('ori form off');c.command('tp @s 112 187 115');c.ticks(80)
 c.command('place template orimod:ruins/cave_altar 100 180 100');c.command('setblock 103 182 115 orimod:map_stone')
 c.ticks(80);look((110.5,184.5,117.5),(106,183,115.5));shot('new-cave-pair',{'map':inspect((103,182,115)),'shrine':inspect((108,182,115)),'fixture':'current complete cave template + layout map coordinate; no natural-start claim'})
 look((105.5,183.7,120.5),(105.5,183,114.5));shot('new-cave-passage')
 c.command('forceload remove all')
 # B1 old-save proof: visit the untouched pre-R115 naturally generated cave.
 world('orimod_dev_r115legacy');c.command('gamemode spectator');c.command('ori form off');c.action('camera',mode='FIRST_PERSON')
 c.command('tp @s -527 54 607');c.ticks(100)
 oldmap=(-529,52,607);shrine=(-529,52,606)
 look((-526.5,54.5,603.5),(-528.5,53.1,606.7));shot('old-cave-compact',{'map':inspect(oldmap),'shrine':inspect(shrine)})
 look((-532,54,610),(-528.5,53.1,606.7));shot('old-cave-compact-reverse')
 c.command('gamemode creative');c.command('ori awaken');c.command('ori form on');c.command('clear @s');c.command('tp @s -528.5 52 608.5 180 25');c.ticks(10)
 c.action('use_block',x=oldmap[0],y=oldmap[1],z=oldmap[2]);c.ticks(8);c.records['steps']['legacy-map-used']=c.action('progress_state');c.action('close_screen')
 c.command('tp @s -528.5 52 604.5 0 25');c.ticks(10);c.action('use_block',x=shrine[0],y=shrine[1],z=shrine[2]);c.ticks(8);c.records['steps']['legacy-shrine-used']=c.action('gui_state');c.action('close_screen')
 c.records['status']='complete'
finally:
 c.action('release_keys');c.action('spirit_capture_actor',mode='remove');c.action('review_camera',enabled=False);c.action('settings',hideGui=False)
 c.command('tick rate 20');c.command('forceload remove all');c.action('leave_world');time.sleep(2);c.action('quit');c.save();print('CLOSED',flush=True)
