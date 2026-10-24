#!/usr/bin/env python3
"""Ginso v3 exterior: fixed-window locate census, natural wetlands, views and reverse FPS baseline."""
import argparse,hashlib,json,math,os,re,shutil,time
from pathlib import Path
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
p=argparse.ArgumentParser();p.add_argument('evidence');p.add_argument('--revision',default='v001');p.add_argument('--seed',type=int,choices=[115117,115118]);args=p.parse_args()
c=DevClient(args.evidence)
c.records['compiledShapeSha256']=hashlib.sha256(Path('build/classes/java/main/cn/wwl/orimod/world/TallGinsoShape.class').read_bytes()).hexdigest()
def cmd(s):return c.action('command',command=s,captureOutput=True)
def ready():
 end=time.monotonic()+360
 while time.monotonic()<end:
  s=c.request()
  if 'player' in s and s['overlay']=='none':c.action('focus_game');c.action('close_screen');c.ready();return
  time.sleep(.5)
 raise TimeoutError('world startup')
def locate(site):
 x,y,z=site['origin'];result=cmd(f'execute positioned {x+1} {y} {z+1} run locate structure orimod:ginso_tree')
 m=re.search(r'\[(-?\d+), [^,]+, (-?\d+)\]',str(result.get('messages')));assert m,(site,result)
 a,b=int(m[1]),int(m[2]);assert (a>>4,b>>4)==(x>>4,z>>4),(site,result)
 return {'site':site,'locate':result,'chunk':[a>>4,b>>4]}
def settled():
 # llvmpipe can take minutes after a teleport/resource reload; fixed sleeps give incomplete terrain.
 start=time.monotonic();zero=None
 while time.monotonic()-start<900:
  sample=c.action('perf_sample')['chunkCompile']
  if sample['ready']:
   if zero is None:zero=time.monotonic()
   if time.monotonic()-zero>=5:return
  else:zero=None
  time.sleep(1)
 raise TimeoutError('Sodium chunk queues did not settle')
def view(at,off,target,name,wait=15):
 q=[a+b for a,b in zip(at,off)];dx,dz=at[0]-q[0],at[2]-q[2];dy=at[1]+target-q[1]
 cmd(f'tp @s {q[0]} {q[1]-1.62} {q[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
 time.sleep(wait);settled();c.screenshot(name);print(name,flush=True)
def perf(name):
 settled();before=c.action('perf_sample');c.action('record_frames');time.sleep(15)
 f=c.action('save_frames',name=c.build_id+'-'+name);shutil.copy2(f['path'],c.out/(name+'.json'))
 d=json.loads(Path(f['path']).read_text());a=sorted(d['frameTimeMs'])
 return {'loadBefore':before,'samples':len(a),'fps':1000*len(a)/sum(a),'medianMs':a[len(a)//2],'p95Ms':a[int(len(a)*.95)],'state':d['state']}
try:
 if 'player' in c.request():c.action('leave_world');time.sleep(4)
 c.action('resize',width=1920,height=1015)
 for seed,preferred in [(115117,'minecraft:swamp'),(115118,'minecraft:river')]:
  if args.seed and args.seed!=seed:continue
  world=f'orimod_dev_gs3_{seed}_{args.revision}';r={'world':world};c.records['steps'][str(seed)]=r;c.save()
  c.action('settings',renderDistance=4,hideGui=True,fov=65,vsync=False,fpsLimit=260,clouds='FANCY');c.action('create_world',name=world,seed=seed);ready()
  c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
  for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','weather clear','time set 6000','ori form off','tp @s 0 200 0']:cmd(s)
  c.ticks(60);r['great']=c.action('great_tree_state');r['probe']=c.action('ginso_probe',radius=4,spacing=52,separation=20);r['probeMode']='Current 52/20 placement only; old and original-spacing comparison already recorded in density-final.json';r['queries']=[];c.save()
  print('probe',seed,r['probe']['histogram'],flush=True)
  for site in r['probe']['sites']:r['queries'].append(locate(site));c.save()
  r['window']={'minXZ':[-4096,-4096],'maxXZExclusive':[5120,5120],'area':9216**2};r['count']=len({tuple(q['chunk']) for q in r['queries']});r['treesPerMillionBlocks']=r['count']*1e6/9216**2;c.save()
  # A riverbank is a terrain relationship, not necessarily the tree centre's biome.
  # 115118's bamboo-jungle root footprint borders the river; old-runtime survey proves it.
  expected=[5048,71,4312] if seed==115117 else [4600,63,-3048]
  sites=[s for s in r['probe']['sites'] if s['origin']==expected]
  r['sampleSelection']='Natural swamp' if seed==115117 else 'Natural riverbank; centre biome bamboo_jungle, shore surveyed in r115-c-scale-old-river-finish-v001'
  assert sites,('Required reviewed natural site disappeared',seed,expected)
  selected=sites[0];at=selected['origin'];r['selected']=selected;r['selectedLocate']=locate(selected);c.save()
  cmd(f'tp @s {at[0]} {at[1]+30} {at[2]-35}');time.sleep(25)
  loaded=c.action('ginso_state');matches=[s for s in loaded['sites'] if s['origin']==at];assert matches,loaded
  r['site']=matches[0];assert r['site']['shapeVersion']==3 and r['site']['courseVersion']==1,r['site'];c.save()
  assert all(f.get('supported',False) for f in r['site']['rootSupports']),r['site']['rootSupports']
  assert math.hypot(at[0]-r['great']['origin'][0],at[2]-r['great']['origin'][2])>=300
  c.action('settings',renderDistance=16);time.sleep(90)
  for tod,label in [(6000,'day'),(18000,'night')]:
   cmd('time set '+str(tod));view(at,(-130,52,-150),55,f'{seed}-distance-198-{label}',25)
   view(at,(130,50,100),55,f'{seed}-distance-164-{label}',20)
  cmd('time set 6000')
  crownAngle=r['site']['rootArches'][0]['angle']-.81
  view(at,(-math.sin(crownAngle)*75,85,math.cos(crownAngle)*75),87,f'{seed}-crown-profile-day',20)
  for off,target,label in [((-32,9,-29),13,'stilt-roots'),((30,8,25),15,'wet-base'),((17,22,-16),83,'under-crown'),((38,100,-30),96,'broken-forks')]:view(at,off,target,f'{seed}-{label}')
  arches=r['site']['rootArches'];assert all(a['loaded'] for a in arches),arches
  assert sum(a['clearCells']==a['testedCells'] for a in arches)>=3,arches
  r['archVegetation']=[]
  for i,arch in enumerate(arches):
   if arch['clearCells']!=arch['testedCells']:
    # Vanilla bamboo decorates after the structure. Preserve it and the real collision count;
    # three other completely clear arches meet the 3–4-opening contract.
    x,y,z=arch['center']
    for dx in range(-1,2):
     for dz in range(-1,2):
      for dy in range(1,7):
       block=c.action('inspect_block',x=x+dx,y=y+dy,z=z+dz)
       if not block['collisionEmpty'] or block['fluid']!='minecraft:empty':
        assert block['block']=='minecraft:bamboo' and block['fluid']=='minecraft:empty',block
        r['archVegetation'].append({'arch':i,'position':[x+dx,y+dy,z+dz],'block':block})
    c.save()
   a=arch['angle'];view(arch['center'],(-math.sin(a)*11,3,math.cos(a)*11),3,f'{seed}-root-arch-{i}',15)
  if seed==115118:view(at,(-65,85,-60),0,f'{seed}-riverbank-survey',25)
  # Rotate the entrance camera together with the saved entrance. Exact v1 course is intentionally temporary.
  def rotate(x,z):
   for _ in range(r['site']['entranceTurns']):x,z=-z,x
   return x,z
  for offY,z,target,label in [(5,-36,8,'entry-front'),(2.5,-24,7,'entry-close')]:
   x,z=rotate(0,z);view(at,(x,offY,z),target,f'{seed}-{label}')
  cmd('time set 18000');x,z=rotate(0,-32);view(at,(x,4,z),8,f'{seed}-entry-night')
  for tod,label in [(6000,'day'),(18000,'night')]:
   c.action('settings',fov=80);cmd('time set '+str(tod));view(at,(0,55,-140),55,f'{seed}-perf-tree-{label}',30)
   r['perfTree'+label]=perf(f'{seed}-frames-tree-{label}')
   cmd(f'tp @s {at[0]} {at[1]+55-1.62} {at[2]-140} 180 0');time.sleep(30);c.screenshot(f'{seed}-perf-reverse-{label}')
   r['perfReverse'+label]=perf(f'{seed}-frames-reverse-{label}');c.save()
   if label=='day' and r['perfTree'+label]['fps']<r['perfReverse'+label]['fps']*.85:
    # Separate direction-dependent terrain cost from Ginso model cost, without editing world blocks.
    cmd(f'tp @s {at[0]} {at[1]+55-1.62} {at[2]-140} 0 0')
    try:
     c.action('ginso_render_probe',hide=True);time.sleep(45)
     c.screenshot(f'{seed}-perf-tree-models-hidden');r['perfHiddenModels']=perf(f'{seed}-frames-hidden-models');c.save()
    finally:
     c.action('ginso_render_probe',hide=False);time.sleep(45)
    c.screenshot(f'{seed}-perf-tree-models-restored');r['perfRestoredModels']=perf(f'{seed}-frames-restored-models');c.save()

  r['perfMethod']='Same position/pitch/FOV80/render16/1920x1015/Fancy clouds, yaw +180 toward terrain, 15s frame samples; reverse is not a tree-deleted clone.'
  c.action('settings',fov=65);cmd('time set 6000');view(at,(-130,52,-150),55,f'{seed}-distance-198-day-settled',30)
  c.action('settings',renderDistance=22,fov=65);cmd('time set 6000');view(at,(0,100,-300),110,f'{seed}-scale-pair-ginso-300',40)
  great=r['great']['origin'];view(great,(0,100,-300),110,f'{seed}-scale-pair-great-300',45)
  if seed==115117:
   slopes=[]
   for candidate in r['probe']['candidates']:
    m=re.search(r'low=(\d+) high=(\d+)',candidate['detail'])
    if candidate['reason']=='OK' and m and int(m[2])-int(m[1])>=5 and candidate['origin']!=at:slopes.append(candidate)
   assert slopes,'No sloping sample in census'
   slope=slopes[0];sp=slope['origin'];r['slopeLocate']=locate(slope)
   c.action('settings',renderDistance=12);cmd(f'tp @s {sp[0]} {sp[1]+12} {sp[2]-32}');time.sleep(40)
   r['slopeState']=c.action('ginso_state');c.save()
   found=[s for s in r['slopeState']['sites'] if s['origin']==sp];assert found and all(f.get('supported',False) for f in found[0]['rootSupports']),r['slopeState']
   for off,target,label in [((-32,7,-29),11,'roots-front'),((29,8,25),12,'roots-back'),((0,16,-38),10,'base-overview')]:view(sp,off,target,f'{seed}-slope-{label}',20)
  c.save();c.action('leave_world');time.sleep(5);c.action('settings',renderDistance=4);c.action('open_world',name=world);ready()
  cmd(f'tp @s {at[0]} {at[1]+30} {at[2]-35}');time.sleep(20)
  r['reload']=c.action('ginso_state');assert any(s['origin']==at and s['shapeVersion']==3 and s['courseVersion']==1 for s in r['reload']['sites']);c.save()
  cmd('forceload remove all');c.action('leave_world');time.sleep(4)
 c.records['status']='passed';c.save();c.action('quit')
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);c.save();raise
