#!/usr/bin/env python3
"""Natural forest spring LayoutVersion 1 art checkpoint, plus labelled template cutaway diagrams."""
import math,os,re,sys,time
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);c.save()
def cmd(text):
 try:return c.action('command',command=text,captureOutput=True)
 except RuntimeError as e:
  if not text.startswith('tp @s ') or 'TimeoutException' not in str(e):raise
  # Teleport may generate destination chunks longer than the bridge's five-second
  # command future. Observe its postcondition, never resend the mutation.
  tokens=text.split()[2:5];target=list(map(float,tokens))
  for i in (0,2):
   if re.fullmatch(r'-?\d+',tokens[i]):target[i]+=.5 # Vanilla integer X/Z centre in the block.
  deadline=time.monotonic()+180
  while time.monotonic()<deadline:
   try:s=c.request()
   except RuntimeError as o:
    if 'TimeoutException' not in str(o):raise
    continue
   position=s.get('player',{}).get('position')
   if position and math.dist(position,target)<.05:
    c.records.setdefault('completed_after_timeout',[]).append({'command':text,'observed':position});c.save()
    return {'success':True,'observed_after_timeout':True}
   time.sleep(.5)
  raise RuntimeError('Teleport postcondition not observed: '+text) from e
def required(text):
 r=cmd(text);c.records.setdefault('fixture_commands',[]).append({'command':text,'response':r});c.save()
 if not r.get('success',False):raise RuntimeError(f'Fixture command failed: {text}: {r}')
 return r
def look(eye,target):
 dx,dy,dz=[b-a for a,b in zip(eye,target)]
 cmd(f'tp @s {eye[0]} {eye[1]-1.62} {eye[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
def shot(name,eye,target):
 if os.environ.get('FOREST_ONLY') and name not in os.environ['FOREST_ONLY'].split(','):return
 look(eye,target);c.ticks(35);time.sleep(2);c.screenshot(name);c.records['steps'][name]={'eye':eye,'target':target,'state':c.request()};c.save();print(name,flush=True)
def wait():
 for _ in range(360):
  try:s=c.request()
  except RuntimeError as e:
   if 'TimeoutException' not in str(e):raise
   c.records.setdefault('startup_observation_timeouts',[]).append(str(e));c.save();continue
  if 'player' in s and s['overlay']=='none':c.action('close_screen');c.action('focus_game');c.ready();return
  time.sleep(.5)
 raise RuntimeError('world startup timeout')
try:
 if 'player' in c.request():c.action('leave_world');time.sleep(3)
 c.action('settings',renderDistance=8,fov=65,hideGui=True,vsync=False,fpsLimit=60)
 world=os.environ.get('FOREST_WORLD') or 'orimod_dev_'+c.build_id.replace('-','_')
 if os.environ.get('FOREST_WORLD'):c.action('open_world',name=world)
 else:
  try:c.action('create_world',name=world,seed=116117)
  except RuntimeError as e:
   if 'TimeoutException' not in str(e):raise
   c.records['startup_request_timeout']=str(e);c.save() # Observe completion; never repeat the mutation.

 wait()
 c.action('resize',width=1600,height=900);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
 for s in ['gamemode spectator','ori form off','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set noon','ori companion flame off']:cmd(s)
 if os.environ.get('FOREST_WORLD'):
  # The last saved camera may be at the remote cutaway fixture. Re-locate from the
  # original capture area, not from that unrelated location (potentially a slow search).
  cmd('tp @s 0 110 128');c.ticks(40)
 c.records['world']=world;c.records['locate']=cmd('locate structure orimod:forest_spring');c.save()
 m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',str(c.records['locate']['messages']));assert m,c.records['locate']
 x,z=int(m[1]),int(m[2]);cmd(f'tp @s {x} 110 {z}');c.ticks(160);time.sleep(8)
 state=c.action('ruin_state');c.records['ruin_state']=state;c.save();print(state,flush=True)
 # /locate returns the generation entry; choose nearest newly generated forest start.
 starts=state['naturalStarts']
 site=min((s for s in starts if s['kind']=='forest_spring' and s.get('layoutVersion')==1),key=lambda s:(s['origin'][0]-x)**2+(s['origin'][2]-z)**2)
 base=site['origin'];rot=site['rotation'];c.records['site']=site
 def point(v):
  a,y,b=v
  if rot=='CLOCKWISE_90':a,b=1-b,a
  elif rot=='CLOCKWISE_180':a,b=1-a,1-b
  elif rot=='COUNTERCLOCKWISE_90':a,b=b,1-a
  return [base[0]+a,base[1]+y,base[2]+b]
 def localshot(name,eye,target):shot(name,point(eye),point(target))
 c.records['settings']={'DISPLAY':':0','renderer':'llvmpipe','FOV':65,'renderDistance':8,'framebuffer':c.request()['framebuffer'],'shader':False}
 localshot('entrance-overview',(44,30,-14),(19,8,22))
 localshot('root-frontage',(19.5,10,-8),(19.5,10,5))
 localshot('spring',(19.5,11,11),(19.5,7,26))
 localshot('shrine-court',(7.5,9,9),(7.5,8,17.5))
 localshot('map-court',(31.5,9,9),(31.5,8,17.5))
 localshot('storm-court',(25.5,10,37),(19.5,9,46.5))
 localshot('light-bridge',(22.5,10,17),(19.5,8.8,23))
 localshot('wall-jump-course',(14.5,11,24),(7.5,9,28))
 localshot('keystone-vault',(25.5,8,39),(29.5,7.5,40.5))
 localshot('terrain-edge',(-6,9,23),(5,5,27))
 localshot('terrain-retaining-edge',(-13,7,15),(-4,1,18))
 localshot('natural-birdseye',(19.5,65,27.5),(19.5,6,27.6))
 localshot('ruined-courts-birdseye',(46,39,26),(19.5,7,35))
 cmd('time set midnight')
 localshot('spring-night',(19.5,11,11),(19.5,7,26))
 localshot('storm-night',(25.5,10,37),(19.5,9,46.5))
 cmd('time set noon')
 # A labelled, separately placed section fixture; this is not a claim about natural terrain.
 if not os.environ.get('FOREST_ONLY') or 'diagram-section' in os.environ['FOREST_ONLY'].split(','):
  cmd('tp @s 2019 200 2027');c.ticks(140);cmd('forceload add 1984 1984 2064 2080')
  required('place template orimod:ruins/forest_spring_v1 2000 180 2000');c.ticks(100)
  # A transparent seal holds the cut spring water in the labelled diagram.
  # The plane leaves the storm assembly at x=18..20 intact.
  required('fill 2016 180 2000 2016 211 2054 barrier')
  required('fill 2000 180 2000 2015 211 2027 air')
  required('fill 2000 180 2028 2015 211 2054 air')
  shot('diagram-section',[1978,199,2027],[2020,190,2027])
 c.records['status']='complete-art-checkpoint';c.save()
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);c.save();raise
finally:
 try:
  c.action('release_keys');cmd('forceload remove all')
  if os.environ.get('FOREST_REVIEW_HOLD')!='1':
   c.action('settings',hideGui=False);c.action('leave_world');time.sleep(3);c.action('quit')
 except Exception as e:c.records['cleanup_error']=repr(e)
 c.save()
