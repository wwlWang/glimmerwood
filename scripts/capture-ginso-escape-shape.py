#!/usr/bin/env python3
"""Gate 1 v3 review: pristine / first two overlays / current overlay, identical cameras."""
import json, math, os, sys, time
from pathlib import Path
from devtools_client import DevClient
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);base=[5048,71,4312];site=None
old='orimod_dev_r115_c_escape_geometry_capture_v001'
pristine='orimod_dev_r115_c_escape_pristine_v003'
v002='orimod_dev_r115_c_escape_shape_capture_v004'
new='orimod_dev_'+c.build_id.replace('-','_')
def cmd(s):return c.action('command',command=s,captureOutput=True)
def wait_world():
    end=time.monotonic()+360
    while time.monotonic()<end:
        s=c.request()
        if 'player' in s and s['overlay']=='none':
            c.action('focus_game');c.action('close_screen');c.ready();return
        time.sleep(.5)
    raise TimeoutError('world')
def settle():
    end=time.monotonic()+600;zero=None
    while time.monotonic()<end:
        status=c.action('perf_sample')['chunkCompile']
        if status['ready']:
            if zero is None:zero=time.monotonic()
            if time.monotonic()-zero>=5:return
        else:zero=None
        time.sleep(.5)
    raise TimeoutError('chunk mesh')
def face(at):
    p=c.request()['player']['position'];eye=c.spirit()['client']['eyeHeight']
    dx,dz=at[0]-p[0],at[2]-p[2];dy=at[1]-p[1]-eye
    c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
def view(at,target,name):
    cmd('tp @s '+' '.join(f'{value:.3f}' for value in at))
    end=time.monotonic()+20
    while math.dist(c.request()['player']['position'],at)>.2:
        if time.monotonic()>end:raise TimeoutError('client teleport '+name)
        time.sleep(.1)
    face(target);settle();c.screenshot(name)
    c.records.setdefault('cameras',{})[name]={'requested':at,'target':target,'actual':c.request()['player']};c.save();print(name,flush=True)
def setup(name,fresh):
    if 'player' in c.request():cmd('forceload remove all');c.action('leave_world');time.sleep(3)
    c.action('settings',renderDistance=8,hideGui=True,fov=70,vsync=False,fpsLimit=120)
    if fresh:c.action('create_world',name=name,seed=115117)
    else:c.action('open_world',name=name)
    wait_world();c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
    for s in ('gamemode spectator','ori form off','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','weather clear','time set 6000','ori companion flame off'):cmd(s)
    cmd('tp @s 5048 180 4290');time.sleep(15);c.action('settings',renderDistance=12)
def pair(prefix):
    for side in (-1,1):
        at=[base[0]+110*side,base[1]+60,base[2]+120*side]
        view(at,[base[0],base[1]+58,base[2]],prefix+('-southwest' if side<0 else '-northeast'))
def camera_on_route(route,i,forward,name):
    q=route[i]['position'];target=route[min(len(route)-1,i+forward)]['position']
    view([q[0],q[1]+.05,q[2]],[target[0],target[1]+1.4,target[2]],name)
try:
    if '--current-only' not in sys.argv:
        setup(pristine,False);cmd('forceload add 5008 4272 5088 4352')
        time.sleep(5)
        c.records['pristineFixture']=c.action('ginso_restore_pristine_fixture',x=5048,y=71,z=4312)
        c.records['pristineSite']=next(s for s in c.action('ginso_state')['sites'] if s['origin']==base)
        assert c.records['pristineSite']['escapeVersion']==0
        pair('pristine')
        view([base[0]+5,base[1]+132,base[2]-7],[base[0],base[1]+101,base[2]],'pristine-crown-top')
        setup(old,False);c.records['v001World']=old;pair('v001')
        setup(v002,False);c.records['v002World']=v002;pair('v002')
    else:
        import shutil,hashlib
        reference=c.out.parent/'r115-c-escape-shape-capture-v005'
        previous=json.loads((reference/'results.json').read_text());c.records['baselineSource']=str(reference)
        c.records['baselineCameras']={k:v for k,v in previous['cameras'].items() if k.startswith(('pristine','v001','v002'))}
        for name in c.records['baselineCameras']:
            shutil.copy2(reference/(name+'.png'),c.out/(name+'.png'))
        c.records['pristineFixture']=previous['pristineFixture'];c.records['pristineSite']=previous['pristineSite'];c.save()
    setup(new,True);c.records['world']=new;c.records['locate']=cmd('execute positioned 5048 71 4312 run locate structure orimod:ginso_tree')
    site=next(s for s in c.action('ginso_state')['sites'] if s['origin']==base);assert site['escapeVersion']==1
    c.records['site']=site;c.save();pair('v003')
    c.records['topViewSettings']=c.action('settings',clouds='OFF')
    view([base[0]+5,base[1]+132,base[2]-7],[base[0],base[1]+101,base[2]],'v003-crown-top')
    route=site['escapeGeometry']['route'];cleft=site['escapeGeometry']['cleftIndex'];branch=site['escapeGeometry']['branchIndex']
    camera_on_route(route,max(8,(cleft if cleft>0 else branch)//2),6,'helix-inside')
    camera_on_route(route,max(0,cleft-4 if cleft>0 else branch-6),7,'bark-cleft')
    camera_on_route(route,min(len(route)-5,branch+4),5,'hooked-branch-ascent')
    end=route[-1]['position'];earlier=route[max(branch,len(route)-8)]['position']
    dx,dz=end[0]-earlier[0],end[2]-earlier[2];length=math.hypot(dx,dz)
    view([end[0]+dz/length*8,end[1]+4,end[2]-dx/length*8],[end[0],end[1]+1,end[2]],'open-fork-exit')
    c.records['status']='captured';c.action('release_keys');cmd('forceload remove all')
    if '--keep-open' not in sys.argv:
        c.action('leave_world');time.sleep(3);c.action('quit')
except Exception as e:
    c.records.update(status='failed',error=repr(e));print(repr(e),flush=True)
    try:c.action('release_keys');c.screenshot('failure')
    except Exception:pass
    raise
finally:c.save()
