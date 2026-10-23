#!/usr/bin/env python3
"""R115 B: natural generation and real forward-input walkthrough, without abilities or jumps."""
import json,math,os,shutil,sys,time
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);seed=int(sys.argv[2]) if len(sys.argv)>2 else 115115
world='orimod_dev_'+c.build_id.replace('-','_')+'_'+str(seed)
previous=json.loads(__import__('pathlib').Path(sys.argv[3]).read_text()) if len(sys.argv)>3 else None
if previous:world=previous['world'];c.records['resume_source']=sys.argv[3]
def command(s):return c.action('command',command=s,captureOutput=True)
def wait_world():
 deadline=time.monotonic()+240
 while time.monotonic()<deadline:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('world')
def pos():return c.request()['player']['position']
def face(target):
 p=pos();eye=c.spirit()['client']['eyeHeight'];dx,dy,dz=target[0]-p[0],target[1]-p[1]-eye,target[2]-p[2]
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
def move(target):
 # Only camera yaw and vanilla forward key. No teleport/velocity/ground injection within a route.
 trail=[];stuck=0
 for _ in range(100):
  p=pos();d=math.hypot(target[0]-p[0],target[2]-p[2]);trail.append(p)
  if d<.18:
   c.action('release_keys');c.ticks(2)
   if abs(pos()[1]-target[1])>.6:raise AssertionError(('wrong landing',target,pos()))
   return trail
  c.action('look',yaw=math.degrees(math.atan2(p[0]-target[0],target[2]-p[2])),pitch=8)
  ticks=max(1,min(6,int(d/.23)))
  c.key('forward',ticks);c.ticks(ticks);c.action('release_keys');c.ticks(2)
  q=pos();stuck=stuck+1 if math.dist(p,q)<.015 else 0
  if stuck>5:c.screenshot('blocked-'+str(len(c.records.setdefault('walk',[]))));raise AssertionError(('walk blocked',target,p))
 raise TimeoutError(('walk target',target,pos()))
def video_start(name,seconds=30):
 c.action('record_video',name=c.build_id+'-'+name,fps=12,seconds=seconds);return time.monotonic()+seconds

def video_end(name,end):
 while time.monotonic()<end+1:time.sleep(.5)
 v=c.action('save_video');source=__import__('pathlib').Path(v['manifest']).parent;source=source if source.is_absolute() else ROOT/'run-devtools'/source;shutil.copytree(source,c.out/name);v['evidence']=str(c.out/name);c.records.setdefault('videos',{})[name]=v;c.save();return v

def walk(name,targets):
 print('Walking',name,flush=True);before=c.spirit();end=video_start(name);all_samples=[]
 try:
  for target in targets:all_samples+=move(target)
 finally:
  c.action('release_keys');c.records.setdefault('walk',[]).append({'name':name,'targets':targets,'samples':all_samples,'before':before,'after':c.spirit()});c.save()
 video_end(name,end)
 if name.startswith('ascent-'):
  lap=int(name[-1]);face([x+2.5,y+lap*8+1.5,z-1.5] if lap<3 else [x+2.5,y+27,z+1.5])
 elif name.startswith('descent-'):face([x+1,y+int(name[-1])*8-1,z+1])
 c.ticks(5);c.screenshot(name)
 if name.startswith('ascent-'):
  command('time set 18000');c.ticks(15);c.screenshot(name+'-night');command('time set 6000');c.ticks(15)
try:
 if '--from-hall' in sys.argv:
  c.action('focus_game');c.ready();site=c.action('great_tree_state');base=site['origin'];x,y,z=base;route=site['ascent']
  c.records['world']=world;c.records['seed']=seed;c.records['site']=site;c.records['walkthrough_source']=sys.argv[3]
  def absolute(p):return [x+p[0]+.5,y+p[1],z+p[2]+.5]
  assert abs(pos()[1]-(y+24))<.1,'Continuation must begin at the walked-to hall'
 else:
  if not previous and 'player' in c.request():c.action('leave_world');time.sleep(3)
  c.action('settings',renderDistance=6,hideGui=False,fov=70)
  if not previous:c.action('create_world',name=world,seed=seed)
  elif 'player' not in c.request():c.action('open_world',name=world)
  wait_world()
  c.records['world']=world;c.records['seed']=seed;c.save()
  c.action('resize',width=1280,height=800);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
  for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','time set 6000','weather clear','ori companion flame off']:command(s)
  site=c.action('great_tree_state');base=site['origin'];x,y,z=base
  c.records['locate']=command('locate structure orimod:great_spirit_tree');c.records['site_report']=command('ori great_tree site');c.save()
  command(f'tp @s {x} {y+25} {z-35}');c.ticks(160);time.sleep(3)
  site=c.action('great_tree_state');c.records['site']=site;c.save()
  command('gamemode creative');command('ori awaken');command('ori form on');c.ticks(240)
  c.records['first_sight']=c.action('progress_state');c.screenshot('first-sight');c.save()
  def absolute(p):return [x+p[0]+.5,y+p[1],z+p[2]+.5]
  command(f'tp @s {x+.5} {y} {z-13.5} 0 0');command('gamemode survival');command('ori form off');c.key('hotbar.9',1);c.ticks(20)
  face([x+.5,y+4,z-7.5]);c.ticks(5);c.screenshot('root-arch-ground-day');command('time set 18000');c.ticks(20);c.screenshot('root-arch-ground-night');command('time set 6000');c.ticks(20)
  route=site['ascent']
  for lap in range(3):
   targets=[]
   if lap==0:targets=[absolute([0,0,-3])]
   for i in (0,5,6,11,12,17,18,23):
    q=route[lap*24+i];targets.append([q[0]+.5,q[1],q[2]+.5])
   walk('ascent-'+str(lap+1),targets)
  walk('hall-arrival',[absolute([0,24,-2]),absolute([2,24,-2])]);face([x+2.5,y+27,z+1.5]);c.ticks(10);c.screenshot('heart-hall-day');face([x+2.5,y+32,z+1.5]);c.ticks(5);c.screenshot('heart-hall-vault-day');face([x+2.5,y+27,z+1.5])
 command('time set 18000');c.ticks(30);c.screenshot('heart-hall-night');c.records['heart_be']=c.action('inspect_block',x=x+2,y=y+25,z=z+1);c.save()
 command('ori awaken');command('ori form on');command('ori wisp give all');command('ori restoration reset');command('ori light 0');c.ticks(10)
 face([x+2.5,y+25.5,z+1.5]);c.ticks(3);c.key('use',2);c.ticks(5)
 start=c.spirit();c.records['ritual_start']=start;assert start['server']['restoration']['active'],start
 print('Ritual active',flush=True);end=video_start('ritual',18);shots=[(20,'01'),(80,'05'),(80,'09'),(80,'13')]
 for ticks,label in shots:c.ticks(ticks);c.screenshot('ritual-'+label)
 c.ticks(70);finish=c.spirit();c.records['ritual_finish']=finish;c.records['ritual_progress']=c.action('progress_state');assert finish['server']['restoration']['completed'],finish
 assert c.records['ritual_progress']['server']['progress']['light']==500,c.records['ritual_progress']
 video_end('ritual',end);print('Ritual completed',flush=True)
 # Walk down the same staircase in reverse to the root cave, still no jump input.
 command('ori form off');command('time set 6000')
 for lap in (2,1,0):
  indices=[23,18,17,12,11,6,5,0]
  targets=[[route[lap*24+i][0]+.5,route[lap*24+i][1],route[lap*24+i][2]+.5] for i in indices]
  walk('descent-'+str(lap+1),targets)
 cave=[absolute([0,0,-3]),absolute([0,0,-8]),absolute([-4,0,-8])]+[[q[0]+.5,q[1],q[2]+.5] for q in site['descent']]+[absolute([-1,-8,2])]
 walk('root-cave',cave);face([x-.5,y-7.5,z+3.5]);c.screenshot('root-cave-reward')
 command('ori form on');c.ticks(5);before=c.action('progress_state');c.key('use',2);c.ticks(10);after=c.action('progress_state');c.key('use',2);c.ticks(10)
 c.records['cave_reward']={'before':before,'after':after,'repeat':c.action('progress_state')}
 def cells(s):return sum(i['count'] for i in s['server']['inventory'] if i['item']=='orimod:energy_cell')
 assert cells(after)==cells(before)+1,'Missing cave energy cell'
 assert cells(c.records['cave_reward']['repeat'])==cells(after),'Repeated cave reward'
 c.screenshot('root-cave-claimed');c.save()
 # A separate test block tests refusal without altering the generated monument.
 command(f'fill {x+44} {y} {z-3} {x+48} {y+4} {z+2} air');command(f'fill {x+44} {y-1} {z-3} {x+48} {y-1} {z+2} minecraft:stone');command(f'setblock {x+46} {y} {z} orimod:spirit_tree_heart');command(f'tp @s {x+46.5} {y} {z-1.5} 0 15');c.ticks(10)
 face([x+46.5,y+.5,z+.5]);c.ticks(3)
 c.records['ancestral_hit']=c.request()['hit'];assert c.records['ancestral_hit'].get('block')==[x+46,y,z],'Ancestral fixture sightline obstructed'
 c.key('use',2);c.ticks(10);c.key('use',2);c.ticks(10)
 c.records['ancestral_refusal']=c.spirit();assert not c.spirit()['server']['restoration']['active']
 c.records['ancestral_claim']=c.action('progress_state');assert f'tree/minecraft:overworld/{x+46}/{y}/{z}' in c.records['ancestral_claim']['server']['progress']['rewards'],'Ancestral interaction never reached server'
 c.screenshot('ancestral-refuses-ritual')
 command(f'setblock {x+46} {y} {z} air');c.records['site_after']=c.action('great_tree_state');c.records['status']='passed';print('Capture passed',flush=True)
except Exception as e:
 c.records['status']='failed';c.records['error']=repr(e);raise
finally:
 c.action('release_keys');c.save()
