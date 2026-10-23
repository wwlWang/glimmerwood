#!/usr/bin/env python3
"""Natural enlarged Spirit Trees: two fresh saves, distance/base/light evidence and measured frame times."""
import argparse,json,math,os,shutil,time
from pathlib import Path
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
parser=argparse.ArgumentParser();parser.add_argument('evidence');parser.add_argument('seeds',type=int,nargs='*');parser.add_argument('--first-seed-perf',action='store_true');args=parser.parse_args()
c=DevClient(args.evidence);seeds=args.seeds or [115115,115116]
def cmd(s):return c.action('command',command=s,captureOutput=True)
def ready():
 end=time.monotonic()+360
 while time.monotonic()<end:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('world startup')
def view(at,off,target,name):
 p=[a+b for a,b in zip(at,off)];dx,dz=at[0]-p[0],at[2]-p[2];dy=at[1]+target-p[1]
 cmd(f'tp @s {p[0]} {p[1]-1.62} {p[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
 time.sleep(30 if 'distance' in name else 15);c.screenshot(name);print(name,flush=True)
def perf(name):
 load=c.action('perf_sample');c.action('record_frames');time.sleep(15);v=c.action('save_frames',name=c.build_id+'-'+name)
 shutil.copy2(v['path'],c.out/(name+'.json'));d=json.loads(Path(v['path']).read_text());t=sorted(d['frameTimeMs'])
 return {'loadBefore':load,'samples':len(t),'fps':1000*len(t)/sum(t),'medianMs':t[len(t)//2],'p95Ms':t[int(len(t)*.95)],'state':d['state']}
try:
 if 'player' in c.request():c.action('leave_world');time.sleep(4)
 # Check both untouched worlds before spending time on the photographic run.
 for seed in seeds:
  world='orimod_dev_'+c.build_id.replace('-','_')+'_'+str(seed);record={'world':world};c.records['steps'][str(seed)]=record;c.save()
  print('Creating',world,flush=True);c.action('settings',renderDistance=6,hideGui=True,fov=65,vsync=False,fpsLimit=260,clouds="FANCY")
  c.action('create_world',name=world,seed=seed);ready()
  site=c.action('great_tree_state');record['before']=site;c.save()
  assert site['present'] and site['shapeVersion']==2,site
  assert 300<=site['distance']<=800,('site outside agreed ring',site['origin'],site['distance'])
  print('Site',seed,site['origin'],site['distance'],flush=True)
  c.action('leave_world');time.sleep(5)
 for seed in seeds:
  record=c.records['steps'][str(seed)];world=record['world'];c.action('settings',renderDistance=6)
  c.action('open_world',name=world);ready();c.action('resize',width=1600,height=900)
  c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
  for s in ['gamemode spectator','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set 6000','ori form off']:cmd(s)
  site=c.action('great_tree_state');assert site['origin']==record['before']['origin']
  at=site['origin'];record['locate']=cmd('locate structure orimod:great_spirit_tree');record['report']=cmd('ori great_tree site');c.save()
  assert record['locate']['result']>0 and record['report']['result']>0
  cmd(f'tp @s {at[0]} {at[1]+90} {at[2]-50}');time.sleep(20)
  c.action('settings',renderDistance=22);time.sleep(40)
  for tod,label in [(6000,'day'),(18000,'night')]:
   cmd('time set '+str(tod));view(at,(0,100,-270),108,f'{seed}-distance-270-{label}')
   view(at,(200,105,-200),110,f'{seed}-distance-283-{label}')
  # Cloud/fog diagnosis is preserved in v003; subsequent passes keep settings identical without re-testing it.
  cmd('time set 6000');view(at,(0,100,-270),108,f'{seed}-distance-270-day-settled');view(at,(200,105,-200),110,f'{seed}-distance-283-day-settled')
  if not args.first_seed_perf or seed==seeds[0]:
   c.action('settings',renderDistance=16,fov=80);cmd('time set 6000');view(at,(0,110,-140),110,f'{seed}-distance-140-perf-day')
   record['perf140day']=perf(f'{seed}-frames-140-day');cmd('time set 18000');view(at,(0,110,-140),110,f'{seed}-distance-140-perf-night');record['perf140night']=perf(f'{seed}-frames-140-night');c.save()
   # Same position, pitch, FOV, distance, sky/cloud and resolution; turn 180 degrees toward terrain without the tree.
   for tod,label in [(6000,'day'),(18000,'night')]:
    cmd('time set '+str(tod));cmd(f'tp @s {at[0]} {at[1]+110-1.62} {at[2]-140} 180 0');time.sleep(30)
    c.screenshot(f'{seed}-baseline-140-away-{label}');record['baseline140'+label]=perf(f'{seed}-frames-140-away-{label}');c.save()
   record['baselineMethod']='Same camera position, pitch and render settings; yaw rotated 180 degrees. Matching surrounding terrain, not a tree-deleted clone.'
  c.action('settings',fov=65);cmd('time set 6000');view(at,(0,130,-96),130,f'{seed}-leaf-edge-20');view([at[0]+40,at[1],at[2]-25],(0,3,-30),128,f'{seed}-canopy-up-day')
  for off,up,label in [((0,5,-48),18,'trunk-base'),((45,8,-35),14,'root-front'),((-39,7,28),13,'root-back'),((65,32,42),34,'base-overview'),((0,2,-21),7,'entrance')]:view(at,off,up,f'{seed}-{label}')
  cmd('time set 18000');view(at,(0,130,-96),130,f'{seed}-leaf-edge-20-night');view([at[0]+40,at[1],at[2]-25],(0,3,-30),128,f'{seed}-canopy-up-night');view(at,(35,3,-32),6,f'{seed}-under-canopy-night');view(at,(0,3,-26),6,f'{seed}-entrance-night')
  record['after']=c.action('great_tree_state');record['report_after']=cmd('ori great_tree site');c.save()
  cmd('forceload remove all');c.action('leave_world');time.sleep(5)
  c.action('open_world',name=world);ready();again=c.action('great_tree_state');assert again['origin']==at and again['shapeVersion']==2
  record['reload']=again;c.save();c.action('leave_world');time.sleep(4)
 c.records['status']='passed';c.action('quit')
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);raise
finally:c.save()
