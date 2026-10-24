#!/usr/bin/env python3
"""R115 Gate2: real right click + real W key pulses. No teleport inside any attempt.
Camera setup/return between completed attempts is recorded explicitly; failure return is server-owned.
"""
import json,math,os,shutil,sys,time,subprocess,re
from pathlib import Path
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);ROOT=Path(__file__).resolve().parents[1];site=None

def cmd(s):return c.action('command',command=s,captureOutput=True)
def state():return c.spirit()
def pos():return c.request()['player']['position']
def local(x,y,z):
 for _ in range(site['entranceTurns']):x,z=-z,x
 b=site['origin'];return [b[0]+x+.5,b[1]+y,b[2]+z+.5]
def face(target):
 p=pos();eye=state()['client']['eyeHeight'];dx,dz=target[0]-p[0],target[2]-p[2];dy=target[1]-p[1]-eye
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
def wait_world():
 until=time.monotonic()+360
 while time.monotonic()<until:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('world')
def settle():
 until=time.monotonic()+600;zero=None
 while time.monotonic()<until:
  s=c.action('perf_sample')['chunkCompile']
  if s['ready']:
   if zero is None:zero=time.monotonic()
   if time.monotonic()-zero>=5:return
  else:zero=None
  time.sleep(.5)
 raise TimeoutError('chunk compile')
def startvideo(name):
 env=dict(os.environ,DISPLAY=':0',XAUTHORITY=str(Path.home()/'.Xauthority'))
 line=subprocess.check_output(['xprop','-root','_NET_ACTIVE_WINDOW'],env=env,text=True);window=re.search(r'0x[0-9a-f]+',line).group()
 title=subprocess.check_output(['xprop','-id',window,'WM_NAME'],env=env,text=True);assert 'Minecraft' in title,title
 out=c.out/(name+'.mp4');log=(c.out/(name+'-encoder.log')).open('w')
 proc=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','warning','-n','-f','x11grab','-window_id',str(int(window,16)),'-framerate','12','-draw_mouse','0','-i',':0.0','-an','-vf','crop=trunc(iw/2)*2:trunc(ih/2)*2','-c:v','libx264','-preset','ultrafast','-crf','23','-pix_fmt','yuv420p',str(out)],stdin=subprocess.PIPE,stdout=log,stderr=log,env=env)
 c.records.setdefault('videos',{})[name]={'path':str(out.relative_to(ROOT)),'capture':'DISPLAY=:0 / Minecraft window only / continuous x11grab','window':window,'title':title,'started':time.monotonic()};c.save()
 return proc,log

def savevideo(name,video):
 proc,log=video;proc.communicate(b'q\n',timeout=30);log.close();assert proc.returncode==0,proc.returncode
 c.records['videos'][name]['finished']=time.monotonic();c.save()

def reset_after_success():
 # Between attempts only, no teleport within the recorded escape.
 c.action('release_keys');c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON')
 cmd('tp @s '+' '.join(map(str,local(0,94,1))));c.ticks(110)

def attempt(name,walk):
 c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON');c.action('release_keys')
 face(local(0,95,0));c.ticks(4)
 before=state();c.action('record_motion');video=startvideo(name)
 started=time.monotonic();c.key('use',1)
 until=time.monotonic()+5
 while time.monotonic()<until:
  s=state()['server']['ginso']['escape']
  if s['phase']=='PREPARE':break
  time.sleep(.05)
 else:raise AssertionError(('right click did not start',s))
 if walk:
  # Offset camera only; all route progress is by real movement keys, no abilities needed.
  c.action('review_camera',enabled=True,x=-2.5,y=4.2,z=3,targetHeight=.8,follow=True)
  points=[r['position'] for r in site['escapeGeometry']['route']]
  c.action('walk_route',points=points)
 else:c.screenshot(name+'-heart-taken')
 samples=[];shots=set()
 while time.monotonic()-started<90:
  snapshot=state();s=snapshot['server']['ginso']['escape'];samples.append({'wallSeconds':time.monotonic()-started,'player':c.request()['player'],'escape':s})
  if walk:
   progress=c.action('walk_route_state');samples[-1]['controller']=dict((k,v) for k,v in progress.items() if k!='reached')
   for tag,fraction in [('root-spiral',.2),('branch-ascent',.7)]:
    if progress['index']>=progress['total']*fraction and tag not in shots:c.screenshot(name+'-'+tag);shots.add(tag)
   if progress['status'] not in ('walking','complete'):raise AssertionError(('controller',progress,s))
  else:
   relative=s.get('waterY',0)-pos()[1]
   for tag,threshold in [('enter-water',.1),('underwater',1.25)]:
    if relative>=threshold and tag not in shots:c.screenshot(name+'-'+tag);shots.add(tag)
  if s['phase'] not in ('PREPARE','RUNNING'):break
  time.sleep(.12)
 c.action('release_keys');after=state();run=after['server']['ginso']['escape'];expected='SUCCESS' if walk else 'SUBMERGED'
 c.records['steps'][name]={'before':before,'after':after,'samples':samples,'wallSeconds':time.monotonic()-started,'expected':expected,'controller':c.action('walk_route_state') if walk else None};c.save()
 c.screenshot(name+'-result');motion=c.action('save_motion',name=c.build_id+'-'+name);shutil.copy2(motion['path'],c.out/(name+'-motion.json'));savevideo(name,video)
 assert run['outcome']==expected,(name,run)
 return run

try:
 if 'player' in c.request():cmd('forceload remove all');c.action('leave_world');time.sleep(3)
 world=os.environ.get('ORIMOD_ESCAPE_WORLD') or 'orimod_dev_'+c.build_id.replace('-','_')
 c.action('settings',renderDistance=8,hideGui=False,fov=70,vsync=False,fpsLimit=120)
 if os.environ.get('ORIMOD_ESCAPE_WORLD'):c.action('open_world',name=world)
 else:c.action('create_world',name=world,seed=115117)
 wait_world();c.action('resize',width=1600,height=900);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False)
 for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','weather clear','time set 6000','ori companion flame off']:cmd(s)
 cmd('tp @s 5048 180 4290');time.sleep(15);cmd('forceload add 5016 4280 5080 4344');settle()
 site=next(s for s in c.action('ginso_state')['sites'] if s['origin']==[5048,71,4312]);c.records.update(world=world,site=site,graphics={'DISPLAY':':0','software':True,'RD':8,'FOV':70});c.save()
 assert site['escapeVersion']==1
 cmd('gamemode survival');cmd('ori reset');cmd('ori awaken');cmd('ori form on');c.action('select_slot',slot=8);cmd('effect clear @s');cmd('gamerule naturalRegeneration false')
 reset_after_success();c.screenshot('water-heart-before')
 success=attempt('success',True);reset_after_success()
 failed=attempt('drowning-failure',False);c.ticks(110)
 retry=attempt('retry-success',True)
 assert success['rewardLight']==100 and retry['rewardLight']==0,(success,retry)
 c.records['status']='gameplay-passed';c.save()
 # Leave running for separate explicit renderer-only perf sampling, then normal shutdown.
except BaseException as e:
 c.records['status']='failed';c.records['error']=repr(e);c.save()
 try:c.action('release_keys')
 except Exception:pass
 raise
