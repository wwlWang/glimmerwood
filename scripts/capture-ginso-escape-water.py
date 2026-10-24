#!/usr/bin/env python3
"""Water renderer-only paired benchmark + final art closeups. Explicit preview, NOT gameplay evidence."""
import json,math,os,shutil,statistics,time
from pathlib import Path
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient('r115-c-escape-water-capture-v001');site=None

def cmd(s):return c.action('command',command=s,captureOutput=True)
def local(x,y,z):
 for _ in range(site['entranceTurns']):x,z=-z,x
 b=site['origin'];return [b[0]+x+.5,b[1]+y,b[2]+z+.5]
def face(at):
 p=c.request()['player']['position'];eye=c.spirit()['client']['eyeHeight'];dx,dz=at[0]-p[0],at[2]-p[2];dy=at[1]-p[1]-eye
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
def settle():
 until=time.monotonic()+600;zero=None;history=[]
 while time.monotonic()<until:
  s=c.action('perf_sample');history.append(s)
  if s['chunkCompile']['ready']:
   if zero is None:zero=time.monotonic()
   if time.monotonic()-zero>=5:return history
  else:zero=None
  time.sleep(.5)
 raise TimeoutError('chunk queues')
def shot(at,target,name):
 cmd('tp @s '+' '.join(map(str,at)));c.ticks(4);face(target);settle();c.screenshot(name);c.records.setdefault('cameras',{})[name]={'position':c.request()['player'],'target':target};c.save()
def preview(on,y=99.25):
 result=c.action('ginso_water_preview',enabled=on,localWaterY=y);c.records.setdefault('previews',[]).append(result);c.save()
def triplet(label,on):
 preview(on);history=settle();c.screenshot(label);history+=settle();r={'settle':history,'runs':[]};c.records['steps'][label]=r;c.save()
 for i in range(3):
  c.action('record_frames');begin=time.monotonic();queues=[]
  while time.monotonic()-begin<12:time.sleep(1);queues.append(c.action('perf_sample'))
  result=c.action('save_frames',name=c.build_id+'-'+label+'-'+str(i));p=Path(result['path']);shutil.copy2(p,c.out/(label+'-'+str(i)+'.json'))
  data=json.loads(p.read_text());a=sorted(data['frameTimeMs']);assert a
  r['runs'].append({'fps':1000*len(a)/sum(a),'frames':len(a),'medianMs':statistics.median(a),'p95Ms':a[int(len(a)*.95)],'queues':queues});c.save()
  assert all(q['chunkCompile']['ready'] for q in queues),'Recompiles during timed sample'
 r['medianFps']=statistics.median(x['fps'] for x in r['runs']);r['rangeFps']=[min(x['fps'] for x in r['runs']),max(x['fps'] for x in r['runs'])];c.save();print(label,r['medianFps'],r['rangeFps'],flush=True)

try:
 c.action('release_keys');c.action('review_camera',enabled=False);c.action('camera',mode='FIRST_PERSON');c.action('focus_game');c.action('close_screen')
 site=next(s for s in c.action('ginso_state')['sites'] if s['origin']==[5048,71,4312]);c.records.update(site=site,mode='renderer-only fixed-level preview; server session must remain inactive')
 c.records['before']=c.spirit();assert c.records['before']['server']['ginso']['escape']['phase'] not in ('PREPARE','RUNNING')
 cmd('gamemode spectator');cmd('ori form off');c.action('settings',hideGui=True,renderDistance=8,fov=70,vsync=False,fpsLimit=120)
 # Use the actual clear route start; the previous off-route camera landed inside bark.
 q=site['escapeGeometry']['route'][0]['position']
 shot(q,local(0,95.05,0),'water-heart-root-cradle')
 route=site['escapeGeometry']['route'];q=route[36]['position'];target=route[23]['position']
 shot([q[0],q[1]+2.2,q[2]],[target[0],target[1]+.3,target[2]],'water-chase-camera-dry')
 c.records['fixedCamera']=c.request()['player'];c.records['localWaterY']=99.25;c.save()
 triplet('water-off',False);triplet('water-on',True);triplet('water-off-return',False)
 off=c.records['steps']['water-off']['medianFps'];on=c.records['steps']['water-on']['medianFps'];back=c.records['steps']['water-off-return']['medianFps']
 c.records['pairedCostPercent']=100*(1-on/statistics.median([off,back]));c.save()
 preview(True);c.screenshot('water-chasing-from-below-preview');preview(False)
 # Final root-lip art, with camera position documented separately from the input videos.
 q=route[-7]['position'];target=route[-1]['position'];shot([q[0],q[1]+.1,q[2]],[target[0],target[1]+.4,target[2]],'root-lip-open-exit')
 q=route[69]['position'];target=route[75]['position'];shot([q[0],q[1]+.1,q[2]],[target[0],target[1]+.4,target[2]],'root-lip-branch-ascent')
 c.records['after']=c.spirit();assert c.records['after']['server']['ginso']['escape']['phase'] not in ('PREPARE','RUNNING');c.records['status']='passed';c.save()
 c.action('release_keys');cmd('forceload remove all');c.action('leave_world');time.sleep(3);c.action('quit')
except BaseException as e:
 c.records['status']='failed';c.records['error']=repr(e);c.save()
 try:preview(False);c.action('release_keys')
 except Exception:pass
 raise
