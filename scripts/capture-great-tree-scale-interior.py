#!/usr/bin/env python3
"""Rescaled landmark: real-key walking, reversible stair, wind ride, ritual, and lookout FPS."""
import json, math, os, shutil, sys, time
from pathlib import Path
from devtools_client import DevClient, ROOT
assert os.environ.get('DISPLAY') == ':0'
c=DevClient(sys.argv[1]); c.build_id+='-safe2'; seed=int(sys.argv[2]) if len(sys.argv)>2 else 115115
world='orimod_dev_scale_interior_'+sys.argv[1].rsplit('-',1)[-1]+'_safe2_'+str(seed)
def cmd(s): return c.action('command',command=s,captureOutput=True)
def position(): return c.request()['player']['position']
def ready():
 end=time.monotonic()+360
 while time.monotonic()<end:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('world startup')
def face(at):
 p=position();eye=c.spirit()['client']['eyeHeight'];dx,dz=at[0]-p[0],at[2]-p[2]
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(at[1]-p[1]-eye,math.hypot(dx,dz))))
def move(at):
 trail=[];stuck=0
 for _ in range(120):
  p=position();d=math.hypot(at[0]-p[0],at[2]-p[2]);trail.append(p)
  if d<.18:
   c.action('release_keys');c.ticks(2)
   if abs(position()[1]-at[1])>.61:raise AssertionError(('Wrong landing',at,position()))
   return trail
  c.action('look',yaw=math.degrees(math.atan2(p[0]-at[0],at[2]-p[2])),pitch=7)
  ticks=max(1,min(6,int(d/.23)));c.key('forward',ticks);c.ticks(ticks);c.action('release_keys');c.ticks(2)
  q=position();stuck=stuck+1 if math.dist(p,q)<.015 else 0
  if stuck>5:c.screenshot('blocked-'+str(len(c.records.get('walk',[]))));raise AssertionError(('Walk blocked',at,p))
 raise TimeoutError(('walk',at,position()))
def video(name,seconds=30):
 c.action('record_video',name=c.build_id+'-'+name,fps=12,seconds=seconds);return time.monotonic()+seconds
def save_video(name,end):
 while time.monotonic()<end+1:time.sleep(.25)
 while c.request()['video']['pending']:time.sleep(.25)
 v=c.action('save_video');p=Path(v['manifest']).parent
 if not p.is_absolute():p=ROOT/'run-devtools'/p
 shutil.copytree(p,c.out/name);c.records.setdefault('videos',{})[name]=v;c.save()
def walk(name,targets):
 print('Walk',name,flush=True);before=c.spirit();began=time.monotonic();samples=[];seconds=0;ticks=0;part=1
 clip=name+'-part01';end=video(clip)
 try:
  for i,p in enumerate(targets):
   # Stop at a waypoint before a clip fills. No movement is omitted between clips.
   if time.monotonic()>end-12:
    save_video(clip,end);part+=1;clip=name+'-part'+str(part).zfill(2);end=video(clip)
   c.records['currentWalk']={'name':name,'waypoint':i,'target':p,'position':position()};c.save()
   tick=c.request()['tick'];start=time.monotonic();samples+=move(p);seconds+=time.monotonic()-start;ticks+=c.request()['tick']-tick
   assert time.monotonic()<end,('A waypoint move exceeded the active video',name,i)
 finally:
  c.action('release_keys');c.records.setdefault('walk',[]).append({'name':name,'targets':targets,'samples':samples,'before':before,'after':c.spirit(),'seconds':seconds,'clientTicks':ticks,'wallSecondsIncludingClipWaits':time.monotonic()-began,'parts':part});c.save()
 save_video(clip,end);c.screenshot(name)
def waypoints(route):
 return [[p[0]+.5,p[1],p[2]+.5] for i,p in enumerate(route) if i==0 or i==len(route)-1 or i%12 in (0,11)]
try:
 if 'player' in c.request():cmd('forceload remove all');c.action('leave_world');time.sleep(4)
 c.action('settings',renderDistance=6,hideGui=False,fov=70,vsync=False,fpsLimit=260,clouds='FANCY',autoJump=False)
 c.action('create_world',name=world,seed=seed);ready();c.records['world']=world;c.save()
 c.action('resize',width=1280,height=800);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
 for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','time set 6000','weather clear','ori companion flame off']:cmd(s)
 site=c.action('great_tree_state');x,y,z=site['origin']
 c.records['locate']=cmd('locate structure orimod:great_spirit_tree');c.records['site_report']=cmd('ori great_tree site');cmd(f'tp @s {x} {y+20} {z-35}');time.sleep(30)
 site=c.action('great_tree_state');c.records['site']=site;c.save()
 def at(a,b,d):return [x+a+.5,y+b,z+d+.5]
 cmd('gamemode creative');cmd('ori awaken');cmd('ori form on');c.ticks(160);c.records['first_sight']=c.action('progress_state');c.screenshot('first-sight')
 assert c.action('progress_state')['server']['abilities']==0,'Zero-ability walk fixture acquired a skill'
 c.key('hotbar.9',1);c.ticks(5)
 cmd(f'tp @s {x+.5} {y+2} {z-31.5} 0 0');face(at(0,9,-16));c.ticks(10);c.screenshot('root-arch-exterior-day')
 cmd('time set 18000');c.ticks(20);c.screenshot('root-arch-exterior-night');cmd('time set 6000')
 cmd('ori form off');cmd(f'tp @s {x+.5} {y} {z-23.5} 0 0');cmd('gamemode survival');c.key('hotbar.9',1);c.ticks(20)
 walk('entrance-to-stairs',[at(0,0,-8),at(0,0,-3)])
 lower=site['ascent']
 for lap in range(3):
  pts=[[lower[lap*24+i][0]+.5,lower[lap*24+i][1],lower[lap*24+i][2]+.5] for i in (0,5,6,11,12,17,18,23)]
  walk('lower-'+str(lap+1),pts);face(at(2,lap*8+8,1));c.screenshot('lower-turn-'+str(lap+1))
 walk('hall-arrival',[at(0,24,-2),at(2,24,-2)]);face(at(2,28,1));c.screenshot('hall-day');face(at(2,38,1));c.screenshot('hall-vault-day');face(at(2,28,1))
 cmd('time set 18000');c.ticks(20);c.screenshot('hall-night');cmd('time set 6000')
 # Walk every turn, with corner waypoints and continuous real movement between them. No teleports/jump.
 upper=site['upperAscent'];walk('hall-to-upper',[at(-4,24,-2),[upper[0][0]+.5,upper[0][1],upper[0][2]+.5]])
 start=c.request();began=time.monotonic()
 for lap in range(math.ceil(len(upper)/48)):
  walk('upper-'+str(lap+1).zfill(2),waypoints(upper[lap*48:min(len(upper),(lap+1)*48)]))
 walk('lookout-arrival',[at(-2,218,7),at(-2,218,0),at(4,218,0)])
 c.records['upperTiming']={'wallSecondsIncludingVideoWaits':time.monotonic()-began,'clientTicksIncludingWaits':c.request()['tick']-start['tick'],'walkingSeconds':sum(w['seconds'] for w in c.records['walk'] if w['name'].startswith('upper-')),'walkingClientTicks':sum(w['clientTicks'] for w in c.records['walk'] if w['name'].startswith('upper-'))};c.save()
 walk('lookout-view-edge',[at(4,218,-4)])
 c.action('look',yaw=180,pitch=18);c.action('settings',renderDistance=16);time.sleep(25);c.screenshot('lookout-view-day')
 c.action('record_frames');time.sleep(15);v=c.action('save_frames',name=c.build_id+'-lookout-view');shutil.copy2(v['path'],c.out/'lookout-frames-valid.json')
 f=json.loads(Path(v['path']).read_text());c.records['lookoutFPS']=1000*len(f['frameTimeMs'])/sum(f['frameTimeMs']);c.records['lookoutView']={'yaw':180,'pitch':18,'position':position(),'frames':'lookout-frames-valid.json'};c.save()
 cmd('time set 18000');c.ticks(20);c.screenshot('lookout-view-night');cmd('time set 6000');c.action('settings',renderDistance=8)
 walk('lookout-view-return',[at(4,218,0)])
 # Same no-ability stair in reverse, not a teleport back to the hall.
 walk('lookout-return',[at(-2,218,0),at(-2,218,7),[upper[-1][0]+.5,upper[-1][1],upper[-1][2]+.5]])
 reverse=list(reversed(upper))
 for lap in range(math.ceil(len(reverse)/48)):
  # Use actual direction changes, because reverse chunks don't align with the forward corner index.
  chunk=reverse[lap*48:min(len(reverse),(lap+1)*48)];pts=[]
  for i,p in enumerate(chunk):
   if i in (0,len(chunk)-1) or i>0 and i+1<len(chunk) and (p[0]-chunk[i-1][0],p[2]-chunk[i-1][2])!=(chunk[i+1][0]-p[0],chunk[i+1][2]-p[2]):pts.append([p[0]+.5,p[1],p[2]+.5])
  walk('return-'+str(lap+1).zfill(2),pts)
 walk('back-to-heart',[at(-4,24,-2),at(2,24,-2)])
 # Complete the return to the entrance by the SAME lower stair, still with zero abilities.
 walk('hall-to-lower-return',[at(0,24,-2),[lower[-1][0]+.5,lower[-1][1],lower[-1][2]+.5]])
 for lap in reversed(range(3)):
  pts=[[lower[lap*24+i][0]+.5,lower[lap*24+i][1],lower[lap*24+i][2]+.5] for i in (23,18,17,12,11,6,5,0)]
  walk('lower-return-'+str(lap+1),pts)
 walk('return-to-entrance',[at(0,0,-3),at(0,0,-8),at(0,0,-24)])
 c.records['zeroSkillReturn']=c.action('progress_state');assert c.records['zeroSkillReturn']['server']['abilities']==0
 # Walk back up for the separate ritual/wind trial; no setup teleport bypasses a route segment.
 walk('ritual-approach',[at(0,0,-8),at(0,0,-3)])
 for lap in range(3):
  walk('ritual-lower-'+str(lap+1),[[lower[lap*24+i][0]+.5,lower[lap*24+i][1],lower[lap*24+i][2]+.5] for i in (0,5,6,11,12,17,18,23)])
 walk('ritual-hall',[at(0,24,-2),at(2,24,-2)]);cmd('ori form on');cmd('ori wisp give all');cmd('ori restoration reset');cmd('ori light 0');c.ticks(10);face(at(2,25.5,1))
 c.key('use',2);c.ticks(5);c.records['ritualStart']=c.spirit();assert c.records['ritualStart']['server']['restoration']['active']
 end=video('ritual',18)
 for ticks,label in [(20,'01'),(80,'05'),(80,'09'),(80,'13')]:c.ticks(ticks);c.screenshot('ritual-'+label)
 c.ticks(70);c.records['ritualFinish']=c.spirit();assert c.records['ritualFinish']['server']['restoration']['completed'];save_video('ritual',end)
 # Real jump + held Glide through the windway, only after the zero-skill walking acceptance.
 cmd('ori unlock glide');walk('hall-to-wind',[at(6,24,-2),at(6,24,3),at(4,24,3)]);face(at(4,80,3));end=video('wind-ride',30);before=c.spirit();windStarted=time.monotonic();windTick=c.request()['tick'];samples=[]
 # Glide deliberately needs a fresh airborne edge; holding the ground jump is not Glide.
 # Schedule edges in client ticks: HTTP observations can consume the entire short hop.
 c.action('input_script',steps=[{'at':0,'key':'key.jump','down':True},
  {'at':3,'key':'key.jump','down':False},{'at':7,'key':'key.jump','down':True},
  {'at':1000,'key':'key.jump','down':False}])
 deadline=time.monotonic()+45
 while position()[1]<y+221:
  samples.append({'pos':position(),'tick':c.request()['tick']})
  if time.monotonic()>deadline:raise AssertionError(('wind ride stalled',position(),c.spirit()))
  c.ticks(4)
 c.action('look',yaw=180,pitch=10);c.key('forward',15);c.ticks(15);c.action('release_keys')
 landingDeadline=time.monotonic()+15
 while not c.spirit()['server']['movement']['grounded']:
  if time.monotonic()>landingDeadline:raise AssertionError(('Wind exit did not land',position()))
  c.ticks(2)
 c.records['windRide']={'before':before,'after':c.spirit(),'samples':samples,'secondsToLanding':time.monotonic()-windStarted,'clientTicksToLanding':c.request()['tick']-windTick};assert abs(position()[1]-(y+218))<1,position();save_video('wind-ride',end);c.screenshot('wind-exit-lookout')
 # Separate close-ups after the walking/ride films; these setup teleports are recorded explicitly.
 cmd(f'tp @s {x+6.5} {y+24} {z+6.5}');c.ticks(10);face(at(4,31,8));c.screenshot('hall-hanging-root-supports-south');face(at(9,30,4));c.screenshot('hall-hanging-root-supports-east')
 cmd(f'tp @s {x-.5} {y-8} {z+2.5}');c.ticks(15);face(at(-1,-7.5,3));c.screenshot('root-cave')
 c.records['caveBefore']=c.action('progress_state');c.key('use',2);c.ticks(10);c.records['caveAfter']=c.action('progress_state');c.key('use',2);c.ticks(10);c.records['caveRepeat']=c.action('progress_state')
 def cells(s):return sum(i['count'] for i in s['server']['inventory'] if i['item']=='orimod:energy_cell')
 assert cells(c.records['caveAfter'])==cells(c.records['caveBefore'])+1
 assert cells(c.records['caveRepeat'])==cells(c.records['caveAfter'])
 cmd(f'fill {x+89} {y-1} {z-2} {x+93} {y-1} {z+2} stone');cmd(f'fill {x+89} {y} {z-2} {x+93} {y+4} {z+2} air');cmd(f'setblock {x+91} {y} {z} orimod:spirit_tree_heart');cmd(f'tp @s {x+91.5} {y} {z-1.5}');c.ticks(10)
 face([x+91.5,y+.5,z+.5]);c.key('use',2);c.ticks(10);c.key('use',2);c.ticks(10);c.screenshot('ancestral-refusal');c.records['ancestralRefusal']=c.spirit();assert not c.records['ancestralRefusal']['server']['restoration']['active']
 cmd(f'setblock {x+91} {y} {z} air')
 upNames={'entrance-to-stairs','lower-1','lower-2','lower-3','hall-arrival','hall-to-upper','lookout-arrival'}
 downNames={'lookout-return','back-to-heart','hall-to-lower-return','return-to-entrance'}
 c.records['walkTiming']={}
 for key,subset in [('up',[w for w in c.records['walk'] if w['name'] in upNames or w['name'].startswith('upper-')]),('down',[w for w in c.records['walk'] if w['name'] in downNames or w['name'].startswith('return-') or w['name'].startswith('lower-return-')])]:
  c.records['walkTiming'][key]={'seconds':sum(w['seconds'] for w in subset),'clientTicks':sum(w['clientTicks'] for w in subset),'segments':[w['name'] for w in subset]}
 c.records['siteAfter']=c.action('great_tree_state');c.records['status']='passed';cmd('forceload remove all');c.action('leave_world');time.sleep(4);c.action('quit')
except BaseException as e:
 c.records['status']='failed';c.records['error']=repr(e);raise
finally:
 try:c.action('release_keys')
 except Exception:pass
 c.save()
