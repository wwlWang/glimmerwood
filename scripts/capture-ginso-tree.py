#!/usr/bin/env python3
"""R115 C exterior checkpoint: natural /locate sites, terrain-noise density, two fresh saves."""
import json,math,os,re,sys,time
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);seeds=[int(s) for s in sys.argv[2:]] or [115117,115118]
def cmd(s):return c.action('command',command=s,captureOutput=True)
def wait_world():
 deadline=time.monotonic()+240
 while time.monotonic()<deadline:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('World startup')
def coord(result):
 assert result['result']>0,result
 m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',str(result['messages']));assert m,result
 return [int(m[1]),int(m[2])]
def view(origin,offset,up,name):
 p=[a+b for a,b in zip(origin,offset)];dx,dz=origin[0]-p[0],origin[2]-p[2];dy=origin[1]+up-p[1]
 cmd(f'tp @s {p[0]} {p[1]-1.62} {p[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
 c.ticks(160);time.sleep(10);c.screenshot(name);print(name,flush=True)
try:
 if 'player' in c.request():c.action('leave_world');time.sleep(3)
 for seed in seeds:
  name=f'orimod_dev_{c.build_id.replace("-","_")}_{seed}';c.records['steps'][str(seed)]={'world':name};record=c.records['steps'][str(seed)];c.save()
  c.action('settings',renderDistance=6,hideGui=True,fov=65);c.action('create_world',name=name,seed=seed);wait_world()
  c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
  for command in ['gamemode spectator','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set 6000','ori form off']:cmd(command)
  record['spawn']=c.request()['player']['position'];record['great_tree']=c.action('great_tree_state')
  print('Locating',seed,flush=True);record['locate']=cmd('locate structure orimod:ginso_tree');xz=coord(record['locate']);c.save()
  print('Survey',seed,flush=True);record['probe']=c.action('ginso_probe',radius=4);c.save()
  origins=[s['origin'] for s in record['probe']['sites'] if [s['origin'][0]>>4,s['origin'][2]>>4]==[xz[0]>>4,xz[1]>>4]]
  at=origins[0] if origins else [xz[0],90,xz[1]]
  cmd(f'tp @s {at[0]} {at[1]+25} {at[2]-35}');c.ticks(180);time.sleep(4)
  state=c.action('ginso_state');record['loaded']=state
  actual=[s for s in state['sites'] if [s['origin'][0]>>4,s['origin'][2]>>4]==[xz[0]>>4,xz[1]>>4]];assert actual,('Located start missing',xz,state)
  record['site']=actual[0];at=actual[0]['origin'];assert 'orimod:ginso_wood' in actual[0]['baseBlock'],actual
  gp=record['great_tree']['origin'];assert math.hypot(at[0]-gp[0],at[2]-gp[2])>=300
  c.save();c.action('settings',renderDistance=12);c.ticks(200);time.sleep(15)
  for time_of_day,label in [(6000,'day'),(18000,'night')]:
   cmd('time set '+str(time_of_day))
   view(at,(-90,30,-100),27,f'{seed}-distance-134-{label}')
   view(at,(100,32,75),26,f'{seed}-distance-125-{label}')
  cmd('time set 6000')
  for offset,h,label in [((0,4,-33),8,'entrance'),((0,2.6,-17),5,'entrance-ground'),((-27,8,-21),8,'stilt-roots'),((25,13,29),12,'wet-base'),((32,50,-24),40,'hollow-crown'),((28,64,-30),43,'broken-forks')]:view(at,offset,h,f'{seed}-{label}')
  cmd('time set 18000');view(at,(0,4,-22),4,f'{seed}-entrance-night')
  if seed==115118:
   river=[3576,63,2232];cmd(f'tp @s {river[0]} {river[1]+25} {river[2]-35}');c.ticks(200);time.sleep(15)
   record['river']=c.action('ginso_state');assert any(s['origin']==river for s in record['river']['sites']);c.save()
   for tod,label in [(6000,'day'),(18000,'night')]:
    cmd('time set '+str(tod));view(river,(-90,30,-100),27,f'{seed}-river-distance-{label}')
   cmd('time set 6000')
   for offset,h,label in [((-27,8,-21),8,'stilt-roots'),((25,13,29),12,'wet-base'),((0,4,-33),8,'entrance'),((28,64,-30),43,'broken-forks')]:view(river,offset,h,f'{seed}-river-{label}')
   cmd('time set 18000');view(river,(0,4,-22),4,f'{seed}-river-entrance-night')
  record['additional_locates']=[]
  # Two more true vanilla /locate calls per seed, independent starts, not a hand-placed gallery.
  for x,z in [(2048,0),(-2048,2048)]:
   cmd(f'tp @s {x} 150 {z}');c.ticks(60)
   result=cmd('locate structure orimod:ginso_tree');record['additional_locates'].append({'from':[x,z],'result':result,'target':coord(result)});c.save()
  cmd('forceload remove all');c.action('leave_world');time.sleep(3)
 c.records['status']='passed'
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);raise
finally:c.save()
