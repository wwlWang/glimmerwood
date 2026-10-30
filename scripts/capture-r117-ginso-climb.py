#!/usr/bin/env python3
"""Actual-input Course 3 timing: one entry setup teleport, then keys through every hop.
Requires the dedicated new-tree evidence world already open; never edits route blocks.
Each attempt (including failed ones) retains timed milestones and one motion trace.
"""
import argparse, json, math, os, shutil, time
from pathlib import Path
from devtools_client import DevClient, ROOT

parser=argparse.ArgumentParser();parser.add_argument('attempt');parser.add_argument('--human-reference',action='store_true');args=parser.parse_args()
assert os.environ.get('DISPLAY')==':0'
c=DevClient.__new__(DevClient);c.build_id='r117-c2-'+args.attempt
c.out=ROOT/'evidence/r117-c/c2-runs'/args.attempt;c.out.mkdir(parents=True,exist_ok=True)
c.connection=json.loads((ROOT/'run-devtools/devtools/connection.json').read_text());c.records={'steps':{},'attempt':args.attempt}
site=json.loads((ROOT/'evidence/r117-c/c2-layout/results.json').read_text())['site'];base=site['origin']
assert site['courseVersion']==3 and site['escapeVersion']==2
reference={}
if args.human_reference:
 for attempt in ('run-01','run-03','run-04'):
  measured=json.loads((ROOT/'evidence/r117-c/c2-runs'/attempt/'results.json').read_text())
  for point in measured['milestones']:reference[point['name']]=max(reference.get(point['name'],0),point['seconds']*1.3)
 c.records['pacing']='Real input plus waits at recorded milestones, targeting pointwise slowest of runs 01/03/04 ×1.3. No clock edits.'

def p():return c.request()['player']['position']
def step(n=2):c.ticks(n);return p()
def local(x,y,z):
 for _ in range(site['entranceTurns']):x,z=-z,x
 return [base[0]+x+.5,base[1]+y,base[2]+z+.5]
def centre(a):return [a[0]+.5,a[1],a[2]+.5]
def face(at,pitch=None):
 q=p();eye=c.spirit()['client']['eyeHeight'];dx,dz=at[0]-q[0],at[2]-q[2]
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=pitch if pitch is not None else -math.degrees(math.atan2(at[1]-q[1]-eye,math.hypot(dx,dz))))
def move(at,expect=True,jump_steps=False):
 stuck=0
 for _ in range(90):
  before=p();dist=math.hypot(at[0]-before[0],at[2]-before[2])
  if dist<.18:
   c.action('release_keys');step(3)
   if expect:assert abs(p()[1]-at[1])<.8,('walk wrong height',at,p())
   return
  face(at,7);n=max(1,min(4,int(dist/.24)));c.key('forward',n);step(n);c.action('release_keys');step(1)
  stuck=stuck+1 if math.dist(before,p())<.02 else 0
  if stuck>2 and jump_steps:
   c.key('jump',3);c.key('forward',5);step(6);c.action('release_keys');step(4);stuck=0
  if stuck>6:raise AssertionError(('walk blocked',at,p()))
 raise TimeoutError(('walk',at,p()))
def use(at):face(at);c.key('use',1);step(3)
def lift_until(y,limit=100):
 for _ in range(limit//2):
  if step()[1]>=y:return
 raise AssertionError(('rise stalled',y,p(),c.spirit()['server']['movement']))
def transfer(at):
 for _ in range(25):
  q=p();d=math.hypot(at[0]-q[0],at[2]-q[2])
  if d<.3:break
  face(at,0);c.key('forward',min(4,max(1,int(d/.25))));step(2)
 else:raise AssertionError(('air transfer',at,p()))
 c.action('release_keys')
 for _ in range(40):
  q=step(2)
  if c.request()['player']['onGround'] and at[1]-.3<=q[1]<=at[1]+1.6:
   move(at,expect=False);step(8)
   assert abs(p()[1]-at[1])<.3,('wind deck landing',at,p())
   return
 raise AssertionError(('landing',at,p()))
def script(steps):c.action('input_script',steps=steps)
def escape():return c.spirit()['server']['ginso']['escape']
def milestone(name):
 s=escape()
 if name in reference and s['outcome']=='NONE':
  delay=max(0,math.ceil(reference[name]*20)-s['elapsedTicks'])
  if delay:step(delay)
 q=p();s=escape();row={'name':name,'position':q,'localFeet':q[1]-base[1],'seconds':s['elapsedTicks']/20,'escape':s}
 c.records.setdefault('milestones',[]).append(row);c.save();print(json.dumps(row),flush=True)
 if s['outcome'] not in ('NONE','SUCCESS'):raise AssertionError(('escape ended',s))

def run():
 c.action('release_keys');c.action('focus_game');c.action('close_screen');c.action('review_camera',enabled=False)
 c.action('settings',renderDistance=5,hideGui=False,fov=70,autoJump=False)
 for command in ['gamemode creative','ori reset','ori awaken','ori unlock climb','ori unlock bash','ori unlock grapple','ori unlock glide','ori form on']:c.command(command)
 c.key('hotbar.9',1);step(8)
 c.command('tp @s '+' '.join(map(str,centre(site['entry']))));c.command('gamemode survival');step(110)
 face(local(0,6,-5));c.screenshot('entry')
 move(local(0,4.5,-5),jump_steps=True)
 gate=local(0,5,-4);state=c.action('inspect_block',x=int(gate[0]),y=int(gate[1]),z=int(gate[2]))['state']
 if 'open=false' in state:use(local(0,5.7,-4))
 move(local(0,5,-2))
 face(local(0,5.6,0));c.screenshot('bottom-heart')
 c.action('record_motion');c.key('use',1);step(3);s=escape();assert s['phase'] in ('PREPARE','RUNNING'),s
 c.records['started']=s;milestone('start')
 for index,h in enumerate(site['hops']):
  c.records['currentHop']=index;c.save();print('HOP',index,h['section'],p(),flush=True)
  move(centre(h['start']),expect=h['section']!='wind');milestone(f'{index:02d}-{h["section"]}-launch')
  if h['section']=='wall':
   face(local(6*h['side'],h['floor']-base[1]+4,0),0);c.key('forward',100);c.key('jump',3);lift_until(h['top']+2.6,140)
   q=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(q[0]-goal[0],goal[2]-q[2]))
   script([{'at':0,'key':'key.forward','down':True},{'at':0,'key':'key.jump','down':True},{'at':2,'action':{'op':'look','yaw':yaw,'pitch':0}},{'at':3,'key':'key.jump','down':False},{'at':16,'key':'key.forward','down':False}]);step(20)
  elif h['section']=='bash':
   at=h['interaction'];face([at[0]+.5,at[1]+.5,at[2]+.5]);q=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(q[0]-goal[0],goal[2]-q[2]))
   script([{'at':0,'key':'key.orimod.bash','down':True},{'at':3,'action':{'op':'look','yaw':yaw,'pitch':-90}},{'at':10,'key':'key.orimod.bash','down':False},{'at':17,'action':{'op':'look','yaw':yaw,'pitch':0}},{'at':17,'key':'key.forward','down':True},{'at':21,'key':'key.jump','down':True},{'at':24,'key':'key.jump','down':False},{'at':29,'key':'key.forward','down':False}]);step(38)
  elif h['section'] in ('grapple','crown'):
   at=h['interaction'];face([at[0]+.5,at[1]+.5,at[2]+.5]);q=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(q[0]-goal[0],goal[2]-q[2]))
   inputs=[{'at':0,'key':'key.orimod.bash','down':True},{'at':3,'key':'key.orimod.bash','down':False},{'at':9,'action':{'op':'look','yaw':yaw,'pitch':0}},{'at':9,'key':'key.forward','down':True},{'at':26 if h['section']=='grapple' else 18,'key':'key.forward','down':False}]
   if h['section']=='crown':inputs.extend([{'at':9,'key':'key.jump','down':True},{'at':12,'key':'key.jump','down':False}])
   script(inputs);step(36)
  elif h['section']=='wind':
   script([{'at':0,'key':'key.jump','down':True},{'at':3,'key':'key.jump','down':False},{'at':7,'key':'key.jump','down':True},{'at':160,'key':'key.jump','down':False}]);lift_until(h['top']+(1.2 if h['top']==base[1]+78 else 2.6),150)
  else:
   q=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(q[0]-goal[0],goal[2]-q[2]))
   script([{'at':0,'key':'key.jump','down':True},{'at':6,'key':'key.jump','down':False},{'at':8,'key':'key.jump','down':True},{'at':10,'action':{'op':'look','yaw':yaw,'pitch':0}},{'at':10,'key':'key.forward','down':True},{'at':24,'key':'key.jump','down':False},{'at':28,'key':'key.forward','down':False}]);step(38)
  if h['section']!='wind':
   c.action('release_keys');step(10)
   assert h['goal'][1]-.6<=p()[1]<=h['goal'][1]+1.6,('scheduled landing',h,p())
   move(centre(h['goal']));assert abs(p()[1]-h['goal'][1])<.3,('deck landing',h,p())
  else:transfer(centre(h['goal']))
  milestone(f'{index:02d}-{h["section"]}-land')
 c.records['finished']=escape();assert c.records['finished']['outcome']=='SUCCESS',c.records['finished']
 c.records['status']='passed';c.screenshot('success')
try:run()
except Exception as e:
 c.records.update(status='failed',error=repr(e));print(repr(e),flush=True)
 c.action('release_keys');c.screenshot('failure');c.records['failureState']=c.spirit()
finally:
 c.action('release_keys')
 if c.records.get('started'):
  motion=c.action('save_motion',name=c.build_id+'-motion');shutil.copy2(motion['path'],c.out/'motion.json');c.records['motion']=motion
 c.save()
