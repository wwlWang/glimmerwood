#!/usr/bin/env python3
"""Natural creek ruins LayoutVersion 1 art checkpoint, plus labelled template cutaway diagrams."""
import json,math,os,re,sys,time
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);c.save()
manifest=json.loads((c.out.parents[1]/'art/source/creek_ruins/v004/manifest.json').read_text())
c.records['art_audit']={k:manifest[k] for k in ('artVersion','sha256','node_audit','support_audit','wheel')};c.save()
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
 if os.environ.get('CREEK_ONLY') and name not in os.environ['CREEK_ONLY'].split(','):return
 look(eye,target);c.ticks(35);time.sleep(5);c.screenshot(name);c.records['steps'][name]={'eye':eye,'target':target,'state':c.request()};c.save();print(name,flush=True)
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
 world=os.environ.get('CREEK_WORLD') or 'orimod_dev_'+c.build_id.replace('-','_')
 if os.environ.get('CREEK_WORLD'):c.action('open_world',name=world)
 else:
  try:c.action('create_world',name=world,seed=116118)
  except RuntimeError as e:
   if 'TimeoutException' not in str(e):raise
   c.records['startup_request_timeout']=str(e);c.save() # Observe completion; never repeat the mutation.

 wait()
 c.action('resize',width=1600,height=900);c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
 for s in ['gamemode spectator','ori form off','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set noon','ori companion flame off']:cmd(s)
 if os.environ.get('CREEK_WORLD'):
  # The last saved camera may be at the remote cutaway fixture. Re-locate from the
  # original capture area, not from that unrelated location (potentially a slow search).
  cmd('tp @s 0 110 128');c.ticks(40)
 c.records['world']=world;c.records['locate']=cmd('locate structure orimod:creek_ruins');c.save()
 m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',str(c.records['locate']['messages']));assert m,c.records['locate']
 x,z=int(m[1]),int(m[2]);cmd(f'tp @s {x} 110 {z}');c.ticks(160);time.sleep(30)
 state=c.action('ruin_state');c.records['ruin_state']=state;c.save();print(state,flush=True)
 # /locate returns the generation entry; choose nearest newly generated creek start.
 starts=state['naturalStarts']
 site=min((s for s in starts if s['kind']=='creek_ruins' and s.get('layoutVersion')==1),key=lambda s:(s['origin'][0]-x)**2+(s['origin'][2]-z)**2)
 base=site['origin'];rot=site['rotation'];c.records['site']=site
 def point(v):
  a,y,b=v
  if rot=='CLOCKWISE_90':a,b=1-b,a
  elif rot=='CLOCKWISE_180':a,b=1-a,1-b
  elif rot=='COUNTERCLOCKWISE_90':a,b=b,1-a
  return [base[0]+a,base[1]+y,base[2]+b]
 def localshot(name,eye,target):shot(name,point(eye),point(target))
 c.records['settings']={'DISPLAY':':0','renderer':'llvmpipe','FOV':65,'renderDistance':8,'framebuffer':c.request()['framebuffer'],'shader':False}
 localshot('entrance-overview',(49,36,-17),(22,13,28))
 localshot('root-frontage',(22.5,13,-9),(22.5,15,4))
 localshot('stream',(34,22,12),(22,9,34))
 localshot('low-bridge-grille',(24.5,8.8,10),(21,7.5,5.5))
 localshot('map-pavilion',(10,13,3),(8.5,11.5,11.5))
 localshot('shrine-court',(20,14,17),(11.5,11.5,22.5))
 localshot('waterwheel',(35,11.62,24.5),(23.5,11.5,23.5))
 localshot('waterwheel-wet-rim',(31,13,18),(23.5,9.5,23.5))
 localshot('waterwheel-downstream-side',(34,11.62,19),(23.5,11.5,23.5))
 localshot('bash-broken-bridge',(15,15,22),(13,15,34))
 localshot('wind-landing',(8,26,41),(18.5,19,52))
 localshot('lightbridge-shaft',(33,12,30),(34.5,12.5,39))
 localshot('sluice-tower-closeup',(24,19,30),(34.5,16,38))
 localshot('dry-retreat',(2,21,51),(12,16,51))
 localshot('terrain-edge',(-7,12,23),(6,9,28))
 localshot('rear-bank-edge',(22,26,79),(22,17,61))
 localshot('natural-birdseye',(22.5,76,33.5),(22.5,10,33.6))
 localshot('courts-birdseye',(55,48,33),(20,14,32))
 cmd('time set midnight')
 localshot('stream-night',(34,22,12),(22,9,34))
 localshot('wind-night',(8,26,41),(18.5,19,52))
 cmd('time set noon')
 # Reviewed natural exceptions must be photographed at their actual saved site,
 # not reconstructed in a flat fixture. Extra shots keep explicit world cameras.
 if os.environ.get('CREEK_EXTRA_SITES'):
  c.records['extra_sites']=[]
  with open(os.environ['CREEK_EXTRA_SITES']) as f:extras=json.load(f)
  main_base,main_rot=base,rot
  for extra in extras:
   base,rot=extra['origin'],extra['rotation']
   cmd(f'tp @s {base[0]} {base[1]+30} {base[2]}');c.ticks(100);time.sleep(8)
   observed=c.action('ruin_state')
   assert any(s['origin']==base and s.get('layoutVersion')==1 and s['kind']=='creek_ruins' for s in observed['naturalStarts']),extra
   c.records['extra_sites'].append({'requested':extra,'observed':observed});c.save()
   for view in extra['shots']:
    # Extras are explicitly requested even when CREEK_ONLY selects main-site views.
    selected=os.environ.pop('CREEK_ONLY',None)
    try:localshot(view['name'],view['eye'],view['target'])
    finally:
     if selected is not None:os.environ['CREEK_ONLY']=selected
  base,rot=main_base,main_rot
 if os.environ.get('CREEK_COMPARE_ALL')=='1':
  c.records['comparison_sites']={}
  for kind in ('forest_spring','cave_altar'):
   cmd('tp @s 0 110 128');c.ticks(30)
   found=cmd('locate structure orimod:'+kind)
   m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',str(found['messages']));assert m,found
   cmd(f'tp @s {m[1]} 130 {m[2]}');c.ticks(140);time.sleep(8)
   c.records['comparison_sites'][kind]={'locate':found,'ruins':c.action('ruin_state')};c.save()
 # Separate authored-template cross section. A labelled diagram, not natural terrain.
 if not os.environ.get('CREEK_ONLY') or 'diagram-section' in os.environ['CREEK_ONLY'].split(','):
  cmd('tp @s 2022 210 2033');c.ticks(140);cmd('forceload add 1984 1984 2064 2080')
  required('place template orimod:ruins/creek_ruins_v1 2000 180 2000');c.ticks(100)
  required('fill 2022 180 2000 2022 223 2066 barrier')
  for z0,z1 in ((0,22),(23,44),(45,66)):
   required(f'fill 2000 180 {2000+z0} 2021 223 {2000+z1} air')
  shot('diagram-section',[1974,206,2033],[2025,194,2033])
 c.records['status']='complete-art-checkpoint';c.save()
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);c.save();raise
finally:
 try:
  c.action('release_keys');cmd('forceload remove all')
  if os.environ.get('CREEK_REVIEW_HOLD')!='1':
   c.action('settings',hideGui=False)
   started=time.monotonic()
   try:c.action('leave_world')
   except RuntimeError as e:
    if 'TimeoutException' not in str(e):raise
    c.records['save_request_timeout']=str(e) # Observe completion; do not repeat disconnect.
   deadline=started+600
   while time.monotonic()<deadline:
    state=c.request()
    if 'player' not in state and state['screen']=='TitleScreen':
     c.records['save_to_title']={'seconds':time.monotonic()-started,'state':state}
     c.screenshot('saved-title');c.save();break
    time.sleep(.25)
   else:raise TimeoutError('Save did not return to title within 600s')
   if os.environ.get('CREEK_KEEP_CLIENT')!='1':c.action('quit')
 except Exception as e:c.records['cleanup_error']=repr(e)
 c.save()
