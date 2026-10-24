#!/usr/bin/env python3
"""Course-v2 real-input acceptance. One initial entry teleport; no inter-hop teleports.
Controllers use short automatically released key presses; failed attempts remain in the record.
"""
import json,math,os,shutil,sys,time
from pathlib import Path
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);seed=115117
world='orimod_dev_'+c.build_id.replace('-','_')
def cmd(s):
 r=c.action('command',command=s,captureOutput=True);c.records.setdefault('commands',[]).append({'command':s,'reply':r});return r
def p():return c.request()['player']['position']
def step(n=2):
 c.ticks(n);at=p();c.records.setdefault('motion',[]).append({'tick':c.request()['tick'],'pos':at});return at
def local(x,y,z):
 for _ in range(site['entranceTurns']):x,z=-z,x
 return [base[0]+x+.5,base[1]+y,base[2]+z+.5]
def centre(a):return [a[0]+.5,a[1],a[2]+.5]
def face(at,pitch=None):
 atp=p();eye=c.spirit()['client']['eyeHeight'];dx,dz=at[0]-atp[0],at[2]-atp[2]
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=pitch if pitch is not None else -math.degrees(math.atan2(at[1]-atp[1]-eye,math.hypot(dx,dz))))
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
def video(name,seconds=30):
 c.action('record_video',name=c.build_id+'-'+name,fps=12,seconds=seconds);return time.monotonic()+seconds
def savevideo(name,end):
 while time.monotonic()<end+.5:time.sleep(.2)
 while c.request()['video']['pending']:time.sleep(.2)
 v=c.action('save_video');source=Path(v['manifest']).parent
 if not source.is_absolute():source=ROOT/'run-devtools'/source
 shutil.copytree(source,c.out/name);c.records.setdefault('videos',{})[name]=v;c.save()
def lift_until(y,limit=100):
 for _ in range(limit//2):
  if step()[1]>=y:return
 raise AssertionError(('rise stalled',y,p(),c.spirit()['server']['movement']))
def transfer(at,keep_glide=False):
 for _ in range(25):
  before=p();d=math.hypot(at[0]-before[0],at[2]-before[2])
  if d<.3:break
  face(at,0);c.key('forward',min(4,max(1,int(d/.25))));step(2)
 else:raise AssertionError(('air transfer',at,p()))
 c.action('release_keys')
 for _ in range(40):
  q=step(2)
  if abs(q[1]-at[1])<.3 and c.request()['player']['onGround']:return
 raise AssertionError(('landing',at,p()))
def script(steps):c.action('input_script',steps=steps)
def jump_script(second=7,hold=100,first_release=3):
 script([{'at':0,'key':'key.jump','down':True},{'at':first_release,'key':'key.jump','down':False},{'at':second,'key':'key.jump','down':True},{'at':hold,'key':'key.jump','down':False}])
def bind(well,index):
 move(centre([well[0],well[1],well[2]-2]) if site['entranceTurns']==0 else local(0,well[1]-base[1],1))
 use([well[0]+.5,well[1]+.7,well[2]+.5]);c.action('close_screen');step(3)
 c.records.setdefault('wells',[]).append({'index':index,'progress':c.action('progress_state'),'spirit':c.spirit(),'binding':cmd('data get entity @s \"neoforge:attachments\".\"orimod:soul_link\"')});c.screenshot('well-'+str(index));c.save()
def miss(h,index):
 name='fall-middle' if index==6 else 'fall-high';end=video(name,15);before=c.spirit();c.action('record_motion')
 if h['section']=='grapple':
  at=h['interaction'];face([at[0]+.5,at[1]+.5,at[2]+.5]);script([{'at':0,'key':'key.orimod.bash','down':True},{'at':3,'key':'key.orimod.bash','down':False}])
 else:jump_script(8,24,6)
 step(45);c.action('release_keys');step(10);after=c.spirit()
 assert c.request()['player']['onGround'] and p()[1]>=h['floor']+1,('fall escaped lesson',h,p())
 assert c.request()['player']['health']==20,('fall damaged player',c.request()['player'])
 motion=c.action('save_motion',name=c.build_id+'-'+name);shutil.copy2(motion['path'],c.out/(name+'-motion.json'))
 c.records.setdefault('falls',[]).append({'name':name,'before':before,'after':after,'motion':motion});c.save();savevideo(name,end);c.screenshot(name+'-landed')
 move(centre(h['start']))
def descent():
 cmd('ori reset');step(8);assert c.action('progress_state')['server']['abilities']==0
 c.records['returnBefore']=c.spirit();points=list(reversed(site['retreat']));begin=time.monotonic();seconds=0;part=1
 name='zero-skill-return-'+str(part);end=video(name)
 at=time.monotonic();move(local(0,94,-3));use(local(0,94.8,-5));move(local(0,94,-7));seconds+=time.monotonic()-at
 for i,hole in enumerate(points[:-1]):
  if time.monotonic()>end-12:
   savevideo(name,end);part+=1;name='zero-skill-return-'+str(part);end=video(name)
  at=time.monotonic();move(centre(hole),expect=False);expected=points[i+1][1]
  for _ in range(40):
   q=step(2)
   if c.request()['player']['onGround']:break
  assert abs(p()[1]-expected)<.25,('descent landing',hole,expected,p())
  assert c.request()['player']['health']==20,('descent health',c.request()['player'])
  seconds+=time.monotonic()-at;c.records.setdefault('returnLandings',[]).append({'position':p(),'health':c.request()['player']['health']});c.save()
 if time.monotonic()>end-15:
  savevideo(name,end);part+=1;name='zero-skill-return-'+str(part);end=video(name)
 at=time.monotonic();move(local(0,4,-6));move(centre(site['entry']));seconds+=time.monotonic()-at
 c.records['returnAfter']=c.spirit();c.records['returnSeconds']=seconds;c.records['returnWallSeconds']=time.monotonic()-begin
 savevideo(name,end);c.screenshot('zero-skill-return-entry');c.save()
def setup():
 global site,base
 if len(sys.argv)>2:
  previous=json.loads(Path(sys.argv[2]).read_text());site=previous['site'];base=site['origin'];c.records.update(world=previous['world'],site=site,resume=sys.argv[2],resumeHop=previous.get('currentHop'));c.save();return
 if 'player' in c.request():cmd('forceload remove all');c.action('leave_world');time.sleep(3)
 c.action('settings',renderDistance=6,hideGui=False,fov=75,vsync=False,fpsLimit=120,autoJump=False)
 c.action('create_world',name=world,seed=seed)
 end=time.monotonic()+360
 while time.monotonic()<end:
  s=c.request()
  if 'player' in s and s['overlay']=='none':break
  time.sleep(.5)
 else:raise TimeoutError('world startup')
 c.action('focus_game');c.action('close_screen');c.ready();c.action('resize',width=1280,height=800)
 c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
 for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','time set 6000','weather clear','ori companion flame off']:cmd(s)
 cmd('tp @s 5048 92 4290');c.records['locate']=cmd('locate structure orimod:ginso_tree');time.sleep(20)
 site=next(s for s in c.action('ginso_state')['sites'] if s['origin'][0]==5048);base=site['origin'];assert site['courseVersion']==2,site
 c.records.update(world=world,site=site);c.save()
 cmd('gamemode creative');cmd('ori awaken');cmd('ori unlock climb');cmd('ori form on');c.key('hotbar.9',1);step(10)
 at=centre(site['entry']);cmd('tp @s '+' '.join(map(str,at)));cmd('gamemode survival');step(60)
def run():
 setup();print('Entry',p(),flush=True);face(local(0,6,-5));c.screenshot('entrance')
 if c.records.get('resumeHop') is None:
  end=video('entrance');move(local(0,4.5,-5),jump_steps=True);use(local(0,5.7,-4));move(local(0,5,-2));savevideo('entrance',end)
 for index,h in enumerate(site['hops']):
  if index<int(c.records.get('resumeHop') or 0):continue
  print('Hop',index,h['section'],p(),flush=True)
  c.records['currentHop']=index;c.save();walk_begin=time.monotonic();move(centre(h['start']),expect=h['section']!='wind');walk_seconds=time.monotonic()-walk_begin
  if h['section'] in ('bash','grapple','wind') and (index==0 or site['hops'][index-1]['section']!=h['section']) and not(c.action('progress_state')['server']['abilities']&{'bash':2,'grapple':8,'wind':4}[h['section']]):
   cmd('ori hints reset');step(25);face(local(5*h['side'],h['floor']-base[1]+3,0));c.screenshot(h['section']+'-missing-skill')
   c.records.setdefault('lockedHints',{})[h['section']]=c.action('progress_state');c.save()
   cmd('ori unlock '+{'bash':'bash','grapple':'grapple','wind':'glide'}[h['section']]);step(8)
  if index in (6,15):miss(h,index)
  before=c.spirit();begin=time.monotonic();tick=c.request()['tick'];c.action('record_motion');name=f'{index:02d}-{h["section"]}';end=video(name)
  c.records['currentAttempt']={'hop':index,'tick':tick,'walkSeconds':walk_seconds,'startedMonotonic':begin};c.save()
  if h['section']=='wall':
   face(local(6*h['side'],h['floor']-base[1]+4,0),0);c.key('forward',100);c.key('jump',3)
   lift_until(h['top']+2.6,140)
   at=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(at[0]-goal[0],goal[2]-at[2]))
   script([{'at':0,'key':'key.forward','down':True},{'at':0,'key':'key.jump','down':True},
    {'at':2,'action':{'op':'look','yaw':yaw,'pitch':0}}, {'at':3,'key':'key.jump','down':False},{'at':16,'key':'key.forward','down':False}]);step(20)
  elif h['section']=='bash':
   at=h['interaction'];face([at[0]+.5,at[1]+.5,at[2]+.5]);origin=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(origin[0]-goal[0],goal[2]-origin[2]))
   script([{'at':0,'key':'key.orimod.bash','down':True},{'at':3,'action':{'op':'look','yaw':yaw,'pitch':-90}},
    {'at':10,'key':'key.orimod.bash','down':False},{'at':17,'action':{'op':'look','yaw':yaw,'pitch':0}},
    {'at':17,'key':'key.forward','down':True},{'at':21,'key':'key.jump','down':True},{'at':24,'key':'key.jump','down':False},
    {'at':29,'key':'key.forward','down':False}]);step(38)
  elif h['section']=='grapple':
   at=h['interaction'];face([at[0]+.5,at[1]+.5,at[2]+.5]);origin=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(origin[0]-goal[0],goal[2]-origin[2]))
   script([{'at':0,'key':'key.orimod.bash','down':True},{'at':3,'key':'key.orimod.bash','down':False},
    {'at':9,'action':{'op':'look','yaw':yaw,'pitch':0}},
    {'at':9,'key':'key.forward','down':True},{'at':26,'key':'key.forward','down':False}]);step(36)
  elif h['section']=='wind':
   jump_script(7,160);lift_until(h['top']+(1.2 if h['top']==base[1]+78 else 2.6),150)
   if h['top']==base[1]+78:use(local(1,80,0))
  else:
   origin=p();goal=centre(h['goal']);yaw=math.degrees(math.atan2(origin[0]-goal[0],goal[2]-origin[2]))
   script([{'at':0,'key':'key.jump','down':True},{'at':6,'key':'key.jump','down':False},{'at':8,'key':'key.jump','down':True},
    {'at':10,'action':{'op':'look','yaw':yaw,'pitch':0}},{'at':10,'key':'key.forward','down':True},{'at':24,'key':'key.jump','down':False},
    {'at':28,'key':'key.forward','down':False}]);step(38)
  if h['section'] in ('wall','bash','grapple','jump'):
   c.action('release_keys');step(10)
   # A legal landing can be on the 1.5-block guard or the half-step lip.
   # Walk onto the deck before checking its final height; never jump here.
   assert h['goal'][1]-.6<=p()[1]<=h['goal'][1]+1.6,('scheduled landing',h,p())
   move(centre(h['goal']))
   assert abs(p()[1]-h['goal'][1])<.3,('deck landing',h,p())
  else:transfer(centre(h['goal']))
  c.records.setdefault('hops',[]).append({'index':index,'section':h['section'],'before':before,'after':c.spirit(),'seconds':time.monotonic()-begin,'walkSeconds':walk_seconds,'ticks':c.request()['tick']-tick})
  motion=c.action('save_motion',name=c.build_id+'-'+name);shutil.copy2(motion['path'],c.out/(name+'-motion.json'))
  c.save();savevideo(name,end);c.screenshot(name+'-landing')
  for j,well in enumerate(site['checkpoints']):
   if well[1]==h['top']+1:bind(well,j+1)
 c.records['status']='ascent-passed';c.save();print('Ascent passed',flush=True)
 descent();c.records['status']='passed';c.save();print('Descent passed',flush=True)
try:run()
except Exception as e:
 c.records.update(status='failed',error=repr(e));print(repr(e),flush=True)
 try:
  c.action('release_keys');c.screenshot('failure');c.records['failureState']=c.spirit()
  motion=c.action('save_motion',name=c.build_id+'-failure');shutil.copy2(motion['path'],c.out/'failure-motion.json')
  deadline=time.monotonic()+35
  while time.monotonic()<deadline and (c.request()['video']['recording'] or c.request()['video']['pending']):time.sleep(.2)
  v=c.action('save_video');source=Path(v['manifest']).parent
  if not source.is_absolute():source=ROOT/'run-devtools'/source
  shutil.copytree(source,c.out/'failure-video');c.records['failureVideo']=v
 except Exception:pass
 raise
finally:
 c.action('release_keys');c.save()
