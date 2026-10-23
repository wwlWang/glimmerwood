#!/usr/bin/env python3
"""Ginso course acceptance. Real key input between entry and heart; no per-section teleports."""
import json,math,os,sys,time,shutil
from pathlib import Path
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);seed=int(sys.argv[2]) if len(sys.argv)>2 else 115118
resume=Path(sys.argv[3]) if len(sys.argv)>3 else None

def cmd(s):return c.action('command',command=s,captureOutput=True)
def pos():return c.request()['player']['position']
def wait_world():
 end=time.monotonic()+240
 while time.monotonic()<end:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('world')
def face(target):
 p=pos();eye=c.spirit()['client']['eyeHeight'];d=[target[0]-p[0],target[1]-p[1]-eye,target[2]-p[2]]
 c.action('look',yaw=math.degrees(math.atan2(-d[0],d[2])),pitch=-math.degrees(math.atan2(d[1],math.hypot(d[0],d[2]))))
def key(k,t):c.key(k,t)
def step(t=2):
 c.ticks(t);p=pos();c.records.setdefault('motion',[]).append({'tick':c.request()['tick'],'pos':p});return p

def move(target,jump_steps=False):
 for n in range(110):
  p=pos();distance=math.hypot(p[0]-target[0],p[2]-target[2])
  if distance<.12:c.action('release_keys');step(3);return
  c.action('look',yaw=math.degrees(math.atan2(p[0]-target[0],target[2]-p[2])),pitch=5)
  t=max(1,min(4,int(distance/.30)));key('forward',t);q=step(t);c.action('release_keys');step(1)
  if math.dist(p,q)<.04 and jump_steps:key('jump',5);key('forward',8);step(8)
 raise AssertionError(('walk stuck',target,pos()))
def fly_to(target,start):
 p=pos();dx,dz=target[0]-start[0],target[2]-start[2];length=math.hypot(dx,dz)
 dx,dz=dx/length,dz/length
 if (target[0]-p[0])*dx+(target[2]-p[2])*dz<.25:c.action('release_keys');return
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=0);key('forward',65)
 for _ in range(25):
  q=step(2)
  if (target[0]-q[0])*dx+(target[2]-q[2])*dz<.25:break
 else:raise AssertionError(('air transfer blocked',pos(),target))
 c.action('release_keys')

def until_height(y,limit=130):
 for _ in range(limit//2):
  if step()[1]>=y:return
 raise AssertionError(('did not rise',y,pos(),c.spirit()['server']['movement']))
def settle(y):
 c.action('release_keys')
 for _ in range(45):
  p=step(2)
  if abs(p[1]-y)<.25:return
 raise AssertionError(('wrong landing',y,pos()))
def start_video(name):
 c.action('record_video',name=c.build_id+'-'+name,fps=12,seconds=18);return time.monotonic()+18

def end_video(name,end):
 while time.monotonic()<end+.5:time.sleep(.25)
 v=c.action('save_video');source=Path(v['manifest']).parent
 if not source.is_absolute():source=ROOT/'run-devtools'/source
 shutil.copytree(source,c.out/name);c.records.setdefault('videos',{})[name]=v;c.save()

def center(p):return [p[0]+.5,p[1],p[2]+.5]
def local(x,yy,z):
 for _ in range(site['entranceTurns']):x,z=-z,x
 return [base[0]+x+.5,base[1]+yy,base[2]+z+.5]
try:
 if resume:
  previous=json.loads(resume.read_text());world=previous['world'];site=previous['site'];base=site['origin'];c.records['resume']=str(resume)
  if 'player' not in c.request():c.action('open_world',name=world);wait_world()
 else:
  if 'player' in c.request():c.action('leave_world');time.sleep(3)
  world='orimod_dev_'+c.build_id.replace('-','_')+'_'+str(seed)
  c.action('settings',renderDistance=8,hideGui=False,fov=70);c.action('create_world',name=world,seed=seed);wait_world()
  c.action('resize',width=1280,height=800);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
  for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','time set 6000','weather clear','ori companion flame off']:cmd(s)
  c.records['locate']=cmd('locate structure orimod:ginso_tree');base=[-424,99,-888] if seed==115118 else [1336,76,-3064]
  cmd(f'tp @s {base[0]} {base[1]+20} {base[2]-30}');c.ticks(200);time.sleep(5)
  site=next(s for s in c.action('ginso_state')['sites'] if s['origin']==base);assert site['course'],site
  cmd('gamemode creative');cmd('ori awaken');cmd('ori unlock all');cmd('ori form on');c.ticks(10)
  entry=center(site['entry']);cmd(f'tp @s {entry[0]} {entry[1]} {entry[2]}');cmd('gamemode survival');c.key('hotbar.9',1);c.ticks(220)
 c.records['world']=world;c.records['seed']=seed;c.records['site']=site;c.save()
 start_index=0 if not resume else int(os.environ.get('GINSO_SECTION','0'))
 if not resume:
  face(local(0,4,-7));c.screenshot('entrance-day');end=start_video('entry');move(local(0,1,-3),True);end_video('entry',end);c.screenshot('foyer')
 for i,s in enumerate(site['sections']):
  if i<start_index:continue
  name=s['id'];print('Section',name,pos(),flush=True);move(center(s['start']))
  c.records.setdefault('sections',{})[name]={'before':c.spirit(),'startPos':pos()};c.save();end=start_video(name)
  if name=='wall':
   face(local(6,4,0));key('forward',180);key('jump',5);until_height(s['top']+1.3);key('jump',10);step(3);c.action('release_keys')
  elif name=='bash':
   face(center(site['lantern']));key('orimod.bash',9);step(3);c.action('look',yaw=0,pitch=-90);step(6);c.action('release_keys');until_height(s['top']+1.1,40)
  elif name=='grapple':
   face([site['flower'][0]+.5,site['flower'][1]+.5,site['flower'][2]+.5]);key('orimod.bash',30);step(4);face(center(s['goal']));key('forward',65);until_height(s['top']+1.1,50);key('jump',15);step(2)
  elif name=='wind':
   key('jump',2);time.sleep(.15);key('jump',180);until_height(s['top']+1.3,140)
  else:
   face(center(s['goal']));key('jump',5);time.sleep(.28);key('jump',25);key('forward',20);step(2)
  fly_to(center(s['goal']),center(s['start']));settle(s['top']+1)
  c.records['sections'][name]['after']=c.spirit();c.records['sections'][name]['endPos']=pos();c.save();end_video(name,end);face(local(0,s['top']-base[1]+4,-4));c.screenshot(name+'-landing')
  if name=='bash':
   move(local(0,15,0));face([site['checkpoints'][0][0]+.5,site['checkpoints'][0][1]+.5,site['checkpoints'][0][2]+.5]);key('use',1);step(8);c.action('close_screen');c.records['checkpoint1']=c.action('progress_state');c.screenshot('checkpoint1');c.save()
 print('Heart balcony',flush=True);move(local(-4,32,-3));end=start_video('heart-balcony');move(local(3,36,-3));move(local(4,36,-3));move(local(4,38,1));settle(base[1]+38);move(local(0,38,-1));face(local(0,39,1));c.screenshot('water-heart-platform-day');cmd('time set 18000');step(30);c.screenshot('water-heart-platform-night');end_video('heart-balcony',end)
 face([site['checkpoints'][1][0]+.5,site['checkpoints'][1][1]+.5,site['checkpoints'][1][2]+.5]);key('use',1);step(8);c.action('close_screen');c.records['checkpoint2']=c.action('progress_state');c.save()
 c.records['status']='passed';print('Course passed',flush=True)
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);print(repr(e),flush=True);raise
finally:c.action('release_keys');c.save()
