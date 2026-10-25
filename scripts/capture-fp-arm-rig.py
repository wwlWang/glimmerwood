#!/usr/bin/env python3
"""B3 initial arm checkpoint: actual held items and real attack key presses, no pose override.
DISPLAY=:0 LIBGL_ALWAYS_SOFTWARE=1 python3 scripts/capture-fp-arm-rig.py <id> [preview]
"""
import os,sys,time,json,shutil
from pathlib import Path
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);c.save();preview=len(sys.argv)>2
config=ROOT/'run-devtools/config/orimod-client.toml'
restore_hud='vanillaHud = false' in config.read_text()
config.write_text(config.read_text().replace('vanillaHud = false','vanillaHud = true'))

def cmd(s):return c.command(s)
def shot(name):
 c.ticks(5);c.screenshot(name);c.records['steps'][name]=c.spirit();c.save();print(name,flush=True)
def video(name):
 c.action('record_pose');c.action('record_video',name=c.build_id+'-'+name,fps=20,seconds=12)
 start=c.request();c.records['steps'][name]={'start':start,'before':c.spirit()}
 # Real presses, frequent enough to use the input buffer. No combat/session mutation.
 for _ in range(20):
  c.key('attack',1);time.sleep(.24)
 c.action('release_keys');time.sleep(9)
 v=c.action('save_video');p=c.action('save_pose',name=c.build_id+'-'+name+'-poses')
 source=Path(v['manifest']);source=source if source.is_absolute() else ROOT/'run-devtools'/source
 shutil.copytree(source.parent,c.out/name);shutil.copy2(p['path'],c.out/name/'poses.json');c.save()
try:
 if 'player' not in c.request():c.action('open_world',name='orimod_dev_playtest_a_20261024')
 for _ in range(180):
  if 'player' in c.request() and c.request()['overlay']=='none':break
  time.sleep(.5)
 c.action('close_screen');c.action('focus_game');c.ready();c.action('release_keys')
 c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON');c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False)
 c.action('resize',width=1600,height=900)
 c.action('settings',fov=65,renderDistance=4,hideGui=False,vsync=False,fpsLimit=60,mainArm='RIGHT');c.action('gui_scale',scale=3)
 for s in ['gamerule sendCommandFeedback false','tick rate 20','gamemode creative','ori awaken','ori form on','ori companion flame off','gamerule doDaylightCycle false','gamerule doMobSpawning false','time set noon','weather clear','clear @s','forceload add 288 288 320 320','tp @s 304.5 180 304.5 0 0']:cmd(s)
 c.ticks(30);cmd('fill 288 179 288 320 179 320 minecraft:stone_bricks');cmd('fill 296 180 296 312 190 312 minecraft:air');cmd('tp @s 304.5 180 304.5 0 0');c.ticks(220)
 for arm in ['RIGHT','LEFT']:
  c.action('settings',mainArm=arm);cmd('ori form off');c.ticks(25)
  for label,item in [('empty','minecraft:air'),('block','minecraft:oak_log'),('blade','orimod:spirit_blade'),('sword','minecraft:iron_sword')]:
   cmd('item replace entity @s weapon.offhand with air');cmd('item replace entity @s hotbar.0 with '+item);c.action('select_slot',slot=0);c.ticks(25);shot('human-'+arm.lower()+'-'+label+'-idle')
 cmd('ori form on');c.ticks(220)
 for arm in ['RIGHT','LEFT']:
  c.action('settings',mainArm=arm)
  for label,item in [('empty','minecraft:air'),('block','minecraft:oak_log'),('blade','orimod:spirit_blade')]:
   cmd('item replace entity @s weapon.offhand with air');cmd('item replace entity @s hotbar.0 with '+item);c.action('select_slot',slot=0);c.ticks(25)
   shot(arm.lower()+'-'+label+'-idle')
   if not preview and label=='blade':
    cmd('tick rate 5');video(arm.lower()+'-blade-combo');cmd('tick rate 20');c.ticks(25)
  if not preview:
   cmd('item replace entity @s hotbar.0 with air');cmd('item replace entity @s weapon.offhand with minecraft:oak_log');c.ticks(25);shot(arm.lower()+'-block-offhand')
 c.records['status']='complete';c.records['rig']={'fov':65,'handFov':70,'framebuffer':c.request()['framebuffer'],'guiScale':3,'pitch':0,'light':'noon/no-shader','display':':0','software':'llvmpipe','vanillaHudForMeasurement':True}
finally:
 c.action('release_keys');c.action('settings',mainArm='RIGHT');cmd('tick rate 20');cmd('forceload remove all');c.action('leave_world');time.sleep(2);c.action('quit');c.save()
 if restore_hud:config.write_text(config.read_text().replace('vanillaHud = true','vanillaHud = false'))
 print('CLOSED',flush=True)
