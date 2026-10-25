#!/usr/bin/env python3
"""Actual-input bow/hammer/spear comparisons; preserve the unchanged human renderer as the ruler."""
import os,sys,time,json,shutil
from pathlib import Path
from devtools_client import DevClient,ROOT

assert os.environ.get('DISPLAY') == ':0'
c=DevClient(sys.argv[1]);c.save()
config=ROOT/'run-devtools/config/orimod-client.toml'
old=config.read_text();config.write_text(old.replace('vanillaHud = false','vanillaHud = true'))
def cmd(s):return c.command(s)
def fixed_look():
 c.action('look',yaw=0,pitch=0);c.ticks(5)
def check_look():
 p=c.request()['player'];assert abs(p['pitch'])<.01 and abs((p['yaw']+180)%360-180)<.01,p
def shot(name):
 fixed_look();c.screenshot(name);check_look();c.records['steps'][name]=c.spirit();c.save();print(name,flush=True)
def video(name,kind):
 fixed_look()
 cmd('tick rate 2');c.action('record_motion');c.action('record_pose')
 c.action('record_video',name=c.build_id+'-'+name,fps=10,seconds=21)
 c.records['steps'][name]={'start':c.request(),'before':c.spirit()};c.save()
 time.sleep(1)
 if kind=='spear':c.key('use',1)
 elif kind=='mace':c.key('attack',1)
 else:c.key('use',30)
 time.sleep(21);c.action('release_keys')
 for _ in range(100):
  videoState=c.request()['video']
  if not videoState['recording'] and videoState['pending']==0:break
  time.sleep(.2)
 else:raise TimeoutError('Video did not finish')
 v=c.action('save_video');p=c.action('save_pose',name=c.build_id+'-'+name+'-poses')
 m=c.action('save_motion',name=c.build_id+'-'+name+'-motion')
 source=Path(v['manifest']);source=source if source.is_absolute()else ROOT/'run-devtools'/source
 shutil.copytree(source.parent,c.out/name);shutil.copy2(p['path'],c.out/name/'poses.json');shutil.copy2(m['path'],c.out/name/'motion.json')
 for sample in json.loads((c.out/name/'poses.json').read_text())['samples']:
  angles=sample.get('firstPerson',{}).get('timing',{})
  if 'yaw'in angles:assert abs(angles['pitch'])<.01 and abs((angles['yaw']+180)%360-180)<.01,(name,sample['frame'],angles)
 check_look()
 c.save();cmd('tick rate 20');c.ticks(20);print(name,flush=True)
try:
 for _ in range(180):
  if c.request()['overlay']=='none':break
  time.sleep(.5)
 else:raise TimeoutError('Initial resources did not finish')
 if 'player'not in c.request():c.action('open_world',name='orimod_dev_playtest_a_20261024')
 for _ in range(180):
  if 'player'in c.request()and c.request()['overlay']=='none':break
  time.sleep(.5)
 c.action('close_screen');c.action('focus_game');c.ready();c.action('release_keys')
 c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON');c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False)
 c.action('resize',width=1600,height=900);c.action('settings',fov=65,renderDistance=4,hideGui=False,vsync=False,fpsLimit=60,mainArm='RIGHT');c.action('gui_scale',scale=3)
 for s in ['gamerule sendCommandFeedback false','tick rate 20','gamemode creative','ori awaken','ori unlock all','ori companion flame off','gamerule doDaylightCycle false','gamerule doMobSpawning false','time set noon','weather clear','clear @s','forceload add 288 288 320 320','tp @s 304.5 180 304.5 0 0','give @s arrow 64']:cmd(s)
 c.ticks(100)
 for human in ([False] if len(sys.argv)>2 and sys.argv[2]=='spirit' else [True,False]):
  cmd('ori form '+('off'if human else'on'));c.ticks(30)
  for arm in ['RIGHT','LEFT']:
   c.action('settings',mainArm=arm)
   kinds=[('bow','minecraft:bow'),('mace','minecraft:mace'),('trident','minecraft:trident')]if human else [('bow','orimod:spirit_bow'),('hammer','orimod:spirit_hammer'),('spear','orimod:spirit_spear')]
   cmd('item replace entity @s weapon.offhand with air');cmd('item replace entity @s hotbar.0 with air');c.action('select_slot',slot=0);c.ticks(20)
   shot(('human-'if human else'')+arm.lower()+'-empty')
   for kind,item in kinds:
    if len(sys.argv)>3 and kind not in sys.argv[3].split(','):continue
    cmd('item replace entity @s hotbar.0 with '+item);cmd('ori energy set 100');c.ticks(30)
    name=('human-'if human else'')+arm.lower()+'-'+kind
    shot(name+'-idle')
    if not human:
     c.action('record_pose',isolateWeapon=True);shot(name+'-item-only')
     c.action('save_pose',name=c.build_id+'-'+name+'-item-only')
    video(name+'-action',kind)
 c.records['status']='complete'
 c.records['rig']={'fov':65,'handFov':70,'serverTickRateDuringVideo':2,'framebuffer':c.request()['framebuffer'],'display':':0','software':'llvmpipe','input':'real use/attack key with automatic release; no pose override'}
finally:
 try:
  c.action('release_keys');c.action('settings',mainArm='RIGHT')
  if 'player' in c.request():
   cmd('tick rate 20');cmd('forceload remove all');c.action('leave_world');time.sleep(2)
  c.action('quit')
 finally:
  c.save();config.write_text(old);print('CLOSED',flush=True)
