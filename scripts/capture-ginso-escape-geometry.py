#!/usr/bin/env python3
"""Gate 1: new-save geometry photographs and manual return-door access, no escape session."""
import math, os, re, sys, time
from devtools_client import DevClient

assert os.environ.get('DISPLAY') == ':0'
c=DevClient(sys.argv[1]);site=None
def cmd(s): return c.action('command',command=s,captureOutput=True)
def pos(): return c.request()['player']['position']
def local(x,y,z):
    for _ in range(site['entranceTurns']): x,z=-z,x
    b=site['origin'];return [b[0]+x+.5,b[1]+y,b[2]+z+.5]
def face(target):
    p=pos();eye=c.spirit()['client']['eyeHeight'];dx,dy,dz=target[0]-p[0],target[1]-p[1]-eye,target[2]-p[2]
    c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
def settled():
    begin=time.monotonic();zero=None
    while time.monotonic()-begin<600:
        data=c.action('perf_sample')['chunkCompile']
        if data['ready']:
            if zero is None: zero=time.monotonic()
            if time.monotonic()-zero>5:return
        else:zero=None
        time.sleep(.5)
    raise TimeoutError('Chunk mesh queues did not settle')
def view(at,target,name):
    cmd('tp @s '+' '.join(map(str,at)));face(target);settled();c.screenshot(name)
    c.records.setdefault('cameras',{})[name]={'position':pos(),'target':target};c.save();print(name,flush=True)
def move(target):
    for _ in range(80):
        p=pos();d=math.hypot(target[0]-p[0],target[2]-p[2])
        if d<.18:return
        c.action('look',yaw=math.degrees(math.atan2(p[0]-target[0],target[2]-p[2])),pitch=0)
        n=max(1,min(4,int(d/.2)));c.key('forward',n);c.ticks(n);c.action('release_keys');c.ticks(2)
    raise AssertionError(('Blocked walk',pos(),target))
def use(at):
    face(at);c.key('use',1);c.ticks(4);c.action('release_keys')
def inspect(at):return c.action('inspect_block',x=math.floor(at[0]),y=math.floor(at[1]),z=math.floor(at[2]))
def toggle(at,opened,label):
    before=inspect(at)
    # An open door has no panel at the centre of its cell. Aim at the actual
    # collision box, rather than claiming a click at the empty cell worked.
    box=list(map(float,re.findall(r'-?\d+(?:\.\d+)?',before['shape'][0])))
    aim=[math.floor(at[i])+(box[i]+box[i+3])/2 for i in range(3)]
    use(aim);after=inspect(at)
    c.records.setdefault('manualDoorTransitions',[]).append({'label':label,'before':before,'after':after,'aim':aim,'player':c.request()['player']});c.save()
    assert f'open={str(opened).lower()}' in after['state'],(label,after)

try:
    if 'player' in c.request():cmd('forceload remove all');c.action('leave_world');time.sleep(3)
    c.action('settings',renderDistance=8,hideGui=True,fov=70,vsync=False,fpsLimit=120)
    resume=os.environ.get('ORIMOD_ESCAPE_DOORS_WORLD');world=resume or 'orimod_dev_'+c.build_id.replace('-','_')
    if resume:c.action('open_world',name=world)
    else:c.action('create_world',name=world,seed=115117)
    deadline=time.monotonic()+360
    while time.monotonic()<deadline:
        if 'player' in c.request() and c.request()['overlay']=='none':break
        time.sleep(.5)
    else:raise TimeoutError('World startup')
    c.action('focus_game');c.action('close_screen');c.ready();c.action('resize',width=1600,height=900)
    c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False);c.action('camera',mode='FIRST_PERSON')
    for s in ['gamemode spectator','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','weather clear','time set 6000','ori companion flame off'] : cmd(s)
    c.records['locate']=cmd('execute positioned 5048 71 4312 run locate structure orimod:ginso_tree')
    cmd('tp @s 5048 180 4290');time.sleep(20)
    site=next(s for s in c.action('ginso_state')['sites'] if s['origin']==[5048,71,4312]);assert site['escapeVersion']==1,site
    c.records.update(site=site,world=world);c.save()
    b=site['origin']
    if not resume:
        # These are geometry views. Flight/teleport is used to set cameras, not claimed as a playthrough.
        c.action('settings',renderDistance=12)
        b=site['origin'];view([b[0]-110,b[1]+60,b[2]-120],[b[0],b[1]+58,b[2]],'distance-day')
        view(local(2,95.1,3),local(-1,95,0),'heart-forecourt-return-gates')
        route=site['escapeGeometry']['route'];q=route[28]['position'];nextq=route[34]['position']
        view([q[0],q[1]+.3,q[2]], [nextq[0],nextq[1]+1.2,nextq[2]],'rising-root-gallery')
        end=route[-1]['position'];view(local(24,site['height']-1,-20),[end[0],end[1]+1,end[2]],'fork-exit')
    # End-of-course porch, both hand-operated doors, and the old retreat approach.
    c.action('settings',hideGui=False);cmd('ori reset');cmd('gamemode survival')
    for z in (0,1):
        p=local(-2,93.5,z);cmd('tp @s '+' '.join(map(str,p)));c.ticks(65)
        door=local(-1,94,z)
        if 'open=true' in inspect(door)['state']:toggle(door,False,f'z{z}-prepare-close')
        toggle(door,True,f'z{z}-open-from-course');move(local(0,94,z))
        assert abs(pos()[1]-b[1]-94)<.1,pos()
        toggle(door,False,f'z{z}-close-from-hall');c.screenshot(f'door-{z}-closed-from-hall')
        toggle(door,True,f'z{z}-open-from-hall');move(local(-2,93.5,z))
        c.records.setdefault('courseReturns',[]).append(c.request()['player']);move(local(0,94,z))
    move(local(0,94,-3))
    if 'open=false' in inspect(local(0,94,-5))['state']:toggle(local(0,94,-5),True,'retreat-open')
    move(local(0,94,-6));c.records['retreatArrival']=c.request()['player'];c.save()
    c.records['status']='passed';c.action('release_keys');cmd('forceload remove all');c.action('leave_world');time.sleep(3);c.action('quit')
except Exception as error:
    c.records.update(status='failed',error=repr(error));print(repr(error),flush=True)
    try:c.action('release_keys');c.screenshot('failure');c.records['failureState']=c.spirit()
    except Exception:pass
    raise
finally:c.save()
