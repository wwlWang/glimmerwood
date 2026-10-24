#!/usr/bin/env python3
"""Paired Ginso FPS: actual Sodium F3 queues empty + 5 s warmup; three 15 s samples per view."""
import json,math,os,shutil,statistics,time
from pathlib import Path
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient('r115-c-scale-perf-steady-v001')
def cmd(s):return c.action('command',command=s,captureOutput=True)
def ready():
 end=time.monotonic()+360
 while time.monotonic()<end:
  s=c.request()
  if 'player' in s and s['overlay']=='none':
   c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(1)
 raise TimeoutError('world startup')
def settle(label):
 start=time.monotonic();zero=None;history=[]
 while time.monotonic()-start<900:
  sample=c.action('perf_sample');elapsed=time.monotonic()-start
  history.append({'elapsed':elapsed,'sample':sample})
  if sample['chunkCompile']['ready']:
   if zero is None:zero=time.monotonic()
   if time.monotonic()-zero>=5:
    return {'waitSeconds':elapsed,'warmupSeconds':time.monotonic()-zero,'history':history}
  else:zero=None
  time.sleep(1)
 (c.out/(label+'-unready.json')).write_text(json.dumps(history,indent=2))
 raise TimeoutError('Chunk compile not settled: '+label)
def triplet(label):
 r={'settle':settle(label),'runs':[]};c.records['steps'][str(seed)][label]=r;c.save()
 c.screenshot(str(seed)+'-'+label)
 # Re-warm after the readback; every run also records counters while timing frames.
 r['afterScreenshot']=settle(label+'-after-screenshot');c.save()
 for i in range(3):
  before=c.action('perf_sample');assert before['chunkCompile']['ready'],before
  samples=[];c.action('record_frames');start=time.monotonic()
  while time.monotonic()-start<15:
   time.sleep(1);samples.append(c.action('perf_sample'))
  name=f'{seed}-{label}-{i+1}';f=c.action('save_frames',name=c.build_id+'-'+name)
  shutil.copy2(f['path'],c.out/(name+'.json'));d=json.loads(Path(f['path']).read_text());a=sorted(d['frameTimeMs'])
  run={'fps':1000*len(a)/sum(a),'frames':len(a),'medianMs':statistics.median(a),'p95Ms':a[int(len(a)*.95)],'before':before,'during':samples,'state':d['state']}
  r['runs'].append(run);c.save()
  assert all(x['chunkCompile']['ready'] for x in samples),('Chunk work during sample',name)
  print(name,run['fps'],flush=True)
 r['medianFps']=statistics.median(x['fps'] for x in r['runs']);r['rangeFps']=[min(x['fps'] for x in r['runs']),max(x['fps'] for x in r['runs'])];c.save()
try:
 if 'player' in c.request():c.action('leave_world');time.sleep(5)
 c.action('resize',width=1920,height=1015)
 for seed,at in [(115118,[4600,63,-3048]),(115117,[5048,71,4312])]:
  c.records['steps'][str(seed)]={'world':f'orimod_dev_gs3_{seed}_v008','position':[at[0]+.5,at[1]+55-1.62,at[2]-140+.5]};c.save()
  c.action('settings',renderDistance=16,hideGui=True,fov=80,vsync=False,fpsLimit=260,clouds='FANCY')
  c.action('open_world',name=f'orimod_dev_gs3_{seed}_v008');ready()
  c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
  for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','weather clear','time set 6000','ori form off']:cmd(s)
  def face(yaw):cmd(f'tp @s {at[0]} {at[1]+55-1.62} {at[2]-140} {yaw} 0')
  face(0);triplet('tree')
  face(180);triplet('reverse')
  face(0)
  try:
   c.action('ginso_render_probe',hide=True);time.sleep(5);triplet('hidden')
  finally:
   c.action('ginso_render_probe',hide=False);time.sleep(5)
  triplet('restored')
  # Replace the river's jungle-obscured diagonal and the first pass's not-yet-built distant terrain.
  def shot(origin,offset,target,label):
   x,y,z=[a+b for a,b in zip(origin,offset)];dx,dz=origin[0]-x,origin[2]-z
   yaw=math.degrees(math.atan2(-dx,dz));pitch=-math.degrees(math.atan2(origin[1]+target-y,math.hypot(dx,dz)))
   cmd(f'tp @s {x} {y-1.62} {z} {yaw} {pitch}')
   proof=settle(label);c.records['steps'][str(seed)][label]=proof;c.save();c.screenshot(f'{seed}-{label}')
  c.action('settings',fov=65)
  for tod,label in [(6000,'day'),(18000,'night')]:
   cmd('time set '+str(tod));shot(at,(-130,52,-150),55,'distance-198-settled-'+label)
   shot(at,(130,85,100),55,'distance-164-raised-'+label)
  cmd('time set 6000');c.action('settings',renderDistance=22)
  shot(at,(0,100,-300),110,'scale-pair-ginso-300-settled')
  great=c.action('great_tree_state')['origin']
  shot(great,(0,100,-300),110,'scale-pair-great-300-settled')
  cmd('forceload remove all');c.action('leave_world');time.sleep(5)
 c.records['status']='passed';c.save();c.action('quit')
except Exception as e:
 c.records['status']='failed';c.records['error']=repr(e);c.save();raise
