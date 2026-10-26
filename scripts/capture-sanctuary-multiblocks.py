#!/usr/bin/env python3
"""B4 item placement / legacy comparison / actual collision overlay. DISPLAY=:0, llvmpipe."""
import json, math, os, sys, time
from devtools_client import DevClient
assert os.environ.get('DISPLAY') == ':0'
c=DevClient(sys.argv[1])
IDS=['glades_well','forest_spring_shrine','creek_ruins_shrine','cave_altar_shrine','map_stone','great_tree_heart','gloom_altar','storm_altar']
BASE=(6720,180,6720)
ONLY=set(sys.argv[2:])
def inspect(p): return c.action('inspect_block',**dict(zip(('x','y','z'),p)))
def overlay(p=None):return c.action('sanctuary_collision',enabled=p is not None,**(dict(zip(('x','y','z'),p)) if p else {}))
def look(eye,target):
 dx,dy,dz=[b-a for a,b in zip(eye,target)]
 c.command(f'tp @s {eye[0]} {eye[1]-1.62} {eye[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
def shot(name,p,pair=False,wire=False):
 c.command('gamemode spectator');c.command('clear @s');overlay(p if wire else None)
 target=(p[0]+(3 if pair else .5),p[1]+1.15,p[2]+.5)
 off=(3.5,3.2,-7.7) if pair else (3.4,2.7,-4.8)
 look(tuple(a+b for a,b in zip(target,off)),target);c.ticks(16);c.screenshot(name)
def place(p,kind):
 c.command('gamemode creative');c.command('clear @s');c.command(f'tp @s {p[0]+.5} {p[1]} {p[2]-2.5} 0 25')
 c.command('item replace entity @s hotbar.0 with orimod:'+kind);c.action('select_slot',slot=0);c.ticks(8)
 c.action('use_block',x=p[0],y=p[1]-1,z=p[2]);c.ticks(12)
 state=inspect(p);assert 'assembled=true' in state['state'],state
 assert state['clientSanctuary']['present'] and state['sanctuary']['present'],state
 r=0 if kind=='map_stone' else 1
 cells=overlay(p)['cells'];overlay()
 assert len(cells)==(3 if kind=='map_stone' else 27),cells
 for cell in cells:
  assert ('orimod:'+kind if cell['position']==list(p) else 'orimod:sanctuary_part') in cell['state'],cell
 part=inspect((p[0],p[1]+1,p[2]));assert not part['sanctuary']['present'] and not part['clientSanctuary']['present'],part
 return {'core':p,'state':state,'cells':cells}
try:
 if 'player' not in c.request():
  c.action('open_world',name=os.environ.get('ORIMOD_CAPTURE_WORLD','orimod_dev_playtest_a_20261024'))
  end=time.monotonic()+180
  while 'player' not in c.request() or c.request()['overlay']!='none':
   assert time.monotonic()<end;time.sleep(.5)
 c.action('focus_game');c.action('close_screen');c.ready();c.action('resize',width=1600,height=900)
 c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
 c.action('settings',hideGui=True,renderDistance=6,fov=65,vsync=False)
 for cmd in ['difficulty peaceful','gamemode spectator','gamerule doDaylightCycle false','gamerule doMobSpawning false','gamerule doWeatherCycle false','weather clear','time set 6000','ori awaken','ori form off','ori companion flame off']:
  c.command(cmd)
 c.command(f'tp @s {BASE[0]} {BASE[1]+6} {BASE[2]}');c.ticks(80)
 # Each pair is far enough from every other pair; vanilla brick grid gives a scale reference.
 for i,kind in enumerate(IDS):
  if ONLY and kind not in ONLY: continue
  p=(BASE[0]+(i%4)*14,BASE[1],BASE[2]+(i//4)*14)
  c.command(f'fill {p[0]-3} {p[1]-1} {p[2]-4} {p[0]+9} {p[1]-1} {p[2]+5} minecraft:stone_bricks')
  c.command(f'fill {p[0]-3} {p[1]} {p[2]-4} {p[0]+9} {p[1]+5} {p[2]+5} minecraft:air')
  data=place(p,kind);legacy=(p[0]+5,p[1],p[2]);c.command(f'setblock {legacy[0]} {legacy[1]} {legacy[2]} orimod:{kind}')
  c.ticks(8);data['legacy']=inspect(legacy);assert 'assembled=false' in data['legacy']['state'];c.records['steps'][kind]=data;c.save()
  shot(kind+'-new',p);shot(kind+'-legacy-pair',p,pair=True)
  data['collision']=overlay(p);shot(kind+'-collision',p,wire=True);overlay();c.save()
  print('Captured',kind,flush=True)
 # The old cave pair deliberately still uses the adjacent legacy compact-model path.
 p=(BASE[0]+3,BASE[1],BASE[2]+32)
 c.command(f'fill {p[0]-4} {p[1]-1} {p[2]-4} {p[0]+5} {p[1]-1} {p[2]+4} minecraft:stone_bricks')
 c.command(f'setblock {p[0]} {p[1]} {p[2]} orimod:cave_altar_shrine')
 c.command(f'setblock {p[0]-1} {p[1]} {p[2]} orimod:map_stone');c.ticks(12);shot('legacy-cave-compact-pair',p)
 c.records['status']='captured';c.records['settings']={'DISPLAY':':0','renderer':'llvmpipe','FOV':65,'requestedResolution':[1600,900],'framebuffer':c.request()['framebuffer'],'shader':False,'new':'actual BlockItem use, assembled=true','legacy':'setblock default assembled=false'}
except Exception as e:
 c.records['status']='error';c.records['error']=repr(e);raise
finally:
 c.save();print(c.out,flush=True)
