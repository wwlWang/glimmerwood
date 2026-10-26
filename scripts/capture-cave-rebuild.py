#!/usr/bin/env python3
"""Natural cave LayoutVersion 1 art checkpoint, plus labelled template cutaway diagrams."""
import math,os,re,sys,time
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);c.save()
def cmd(text):return c.action('command',command=text,captureOutput=True)
def required(text):
 r=cmd(text);c.records.setdefault('fixture_commands',[]).append({'command':text,'response':r});c.save()
 if not r.get('success',False):raise RuntimeError(f'Fixture command failed: {text}: {r}')
 return r
def look(eye,target):
 dx,dy,dz=[b-a for a,b in zip(eye,target)]
 cmd(f'tp @s {eye[0]} {eye[1]-1.62} {eye[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
def shot(name,eye,target):
 if os.environ.get('CAVE_ONLY') and name not in os.environ['CAVE_ONLY'].split(','):return
 look(eye,target);c.ticks(35);time.sleep(2);c.screenshot(name);c.records['steps'][name]={'eye':eye,'target':target,'state':c.request()};c.save();print(name,flush=True)
def wait():
 for _ in range(360):
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('close_screen');c.action('focus_game');c.ready();return
  time.sleep(.5)
 raise RuntimeError('world startup timeout')
try:
 if 'player' in c.request():c.action('leave_world');time.sleep(3)
 c.action('settings',renderDistance=8,fov=65,hideGui=True,vsync=False,fpsLimit=60)
 world=os.environ.get('CAVE_WORLD') or 'orimod_dev_'+c.build_id.replace('-','_')
 if os.environ.get('CAVE_WORLD'):c.action('open_world',name=world)
 else:c.action('create_world',name=world,seed=116116)
 wait()
 c.action('resize',width=1600,height=900);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
 for s in ['gamemode spectator','ori form off','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set noon','ori companion flame off']:cmd(s)
 if os.environ.get('CAVE_WORLD'):
  # The last saved camera may be at the remote cutaway fixture. Re-locate from the
  # original capture area, not from that unrelated location (potentially a slow search).
  cmd('tp @s 0 110 128');c.ticks(40)
 c.records['world']=world;c.records['locate']=cmd('locate structure orimod:cave_altar');c.save()
 m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',str(c.records['locate']['messages']));assert m,c.records['locate']
 x,z=int(m[1]),int(m[2]);cmd(f'tp @s {x} 110 {z}');c.ticks(160);time.sleep(8)
 state=c.action('ruin_state');c.records['ruin_state']=state;c.save();print(state,flush=True)
 # /locate returns the generation entry; choose nearest newly generated cave start.
 starts=state['naturalStarts']
 site=min((s for s in starts if s['kind']=='cave_altar' and s.get('layoutVersion')==1),key=lambda s:(s['origin'][0]-x)**2+(s['origin'][2]-z)**2)
 base=site['origin'];rot=site['rotation'];c.records['site']=site
 def point(v):
  a,y,b=v
  if rot=='CLOCKWISE_90':a,b=1-b,a
  elif rot=='CLOCKWISE_180':a,b=1-a,1-b
  elif rot=='COUNTERCLOCKWISE_90':a,b=b,1-a
  return [base[0]+a,base[1]+y,base[2]+b]
 def localshot(name,eye,target):shot(name,point(eye),point(target))
 c.records['settings']={'DISPLAY':':0','renderer':'llvmpipe','FOV':65,'renderDistance':8,'framebuffer':c.request()['framebuffer'],'shader':False}
 localshot('natural-entrance-overview',(23.5,42,-9),(23.5,29,-3))
 localshot('carved-frontage',(29.5,35.5,-11),(23.5,35,-6))
 localshot('frontage-oblique',(31.5,35.5,-12),(23.5,35,-6))
 localshot('portal-high-front',(19.5,41.5,-9.5),(23.5,34.5,-6.5))
 localshot('natural-entrance',(23.5,30.6,0.5),(23.5,27.6,4.5))
 localshot('descending-ribs',(23.5,25,6),(23.5,15,16))
 localshot('descent-first-turn',(23.5,23,7.5),(17.5,22,8.5))
 localshot('descent-second-turn',(17.5,16.5,14.5),(20.5,12,19.5))
 localshot('vestibule-wide',(23.5,8,25),(23.5,9,35))
 localshot('shrine-chamber',(15.5,9,24),(10.5,8,29.5))
 localshot('map-chamber',(36.5,8.2,24.5),(36.5,8,29.5))
 localshot('grapple-gallery',(36.5,9,39.5),(36.5,12.5,43))
 localshot('keystone-vault-door',(21.5,8,42.5),(15.5,7.5,44.5))
 localshot('altar-vault-wide',(28.5,11.5,53.5),(23.5,11,60.5))
 localshot('altar-dais',(27.5,9.5,55),(23.5,9,60.5))
 cmd('time set midnight');localshot('altar-night',(27.5,9.5,55),(23.5,9,60.5))
 cmd('time set noon')
 # A focused natural-world re-shoot must not generate unrelated cutaway fixtures.
 if not os.environ.get('CAVE_ONLY') or any(n.startswith('diagram-') for n in os.environ['CAVE_ONLY'].split(',')):
  # Diagrams use a separate copy above the world. These are labelled cutaway fixtures, never natural-world claims.
  cmd('tp @s 2023 200 2029');c.ticks(140);cmd('forceload add 1984 1984 2080 2096')
  required('place template orimod:ruins/cave_altar_v1 2000 180 2000');c.ticks(100)
  for a,b in [(2000,2034),(2035,2070)]:required(f'fill 2000 194 {a} 2046 211 {b} air')
  shot('diagram-top-cutaway',[2023.5,240,2035.5],[2023.5,185,2035.6])
  required('place template orimod:ruins/cave_altar_v1 2000 180 2000');c.ticks(80)
  for a,b in [(2000,2034),(2035,2070)]:required(f'fill 2000 180 {a} 2021 211 {b} air')
  shot('diagram-long-section',[1964,211,2035],[2026,197,2035])
  # A separate stair-only cutaway removes shell materials, retaining the three polished flights.
  required('place template orimod:ruins/cave_altar_v1 2000 180 2000');c.ticks(80)
  for material in ['tuff_bricks','deepslate_bricks','deepslate','cobbled_deepslate','tuff','chiseled_tuff_bricks','chiseled_deepslate']:
   required(f'fill 2014 185 2000 2030 211 2026 air replace minecraft:{material}')
  shot('diagram-descent-cutaway',[2038,221,1988],[2021,195,2013])
 c.records['status']='complete-first-art-checkpoint';c.save()
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);c.save();raise
finally:
 try:
  c.action('release_keys');cmd('forceload remove all')
  if os.environ.get('CAVE_REVIEW_HOLD')!='1':
   c.action('settings',hideGui=False);c.action('leave_world');time.sleep(3);c.action('quit')
 except Exception as e:c.records['cleanup_error']=repr(e)
 c.save()
