#!/usr/bin/env python3
"""Supplement the real-input course run: second-seed silhouette and river entry grading."""
import json,math,os,sys,time,shutil
from pathlib import Path
from devtools_client import DevClient,ROOT
assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);source=json.loads(Path(sys.argv[2]).read_text());c.records['walkthrough']=sys.argv[2];c.records['world']=source['world']
def cmd(s):return c.action('command',command=s,captureOutput=True)
def pos():return c.request()['player']['position']
def local(site,x,y,z):
 for _ in range(site['entranceTurns']):x,z=-z,x
 a=site['origin'];return [a[0]+x+.5,a[1]+y,a[2]+z+.5]
def face(target):
 p=pos();e=c.spirit()['client']['eyeHeight'];dx,dy,dz=target[0]-p[0],target[1]-p[1]-e,target[2]-p[2]
 c.action('look',yaw=math.degrees(math.atan2(-dx,dz)),pitch=-math.degrees(math.atan2(dy,math.hypot(dx,dz))))
def view(at,point,target,name):
 cmd(f'tp @s {point[0]} {point[1]} {point[2]}');face(target);c.ticks(100);time.sleep(4);c.screenshot(name);print(name,flush=True)
def camera(site,offset,up,name):
 b=site['origin'];view(b,[b[i]+offset[i] for i in range(3)],[b[0],b[1]+up,b[2]],name)
try:
 c.action('focus_game');c.action('close_screen');c.action('release_keys');cmd('gamemode spectator');c.action('settings',renderDistance=12,hideGui=True,fov=65)
 for label,base in [('115118',source['site']['origin']),('115118-river',[3576,63,2232])]:
  cmd(f'tp @s {base[0]} {base[1]+20} {base[2]-30}');c.ticks(200);time.sleep(10)
  site=next(s for s in c.action('ginso_state')['sites'] if s['origin']==base);c.records['steps'][label]={'site':site};c.save()
  for tod,suffix in [(6000,'day'),(18000,'night')]:
   cmd('time set '+str(tod));camera(site,(-90,30,-100),27,label+'-distance-134-'+suffix)
   view(base,local(site,0,3,-28),local(site,0,5,-8),label+'-root-arch-'+suffix)
  cmd('time set 6000');camera(site,(-27,8,-21),8,label+'-stilt-roots');camera(site,(28,64,-30),43,label+'-forks')
  if label=='115118':
   for section in site['sections']:
    y=section['floor']-base[1]+1
    for tod,suffix in [(6000,'day'),(18000,'night')]:
     cmd('time set '+str(tod));view(base,local(site,0,y,-3),[section['start'][0]+.5,section['top']+1,section['start'][2]+.5],label+'-room-'+section['id']+'-'+suffix)
  else:
   cmd('time set 6000');c.action('settings',hideGui=False);cmd('gamemode survival');p=site['entry'];cmd(f'tp @s {p[0]+.5} {p[1]} {p[2]+.5}');c.ticks(10)
   c.action('record_video',name=c.build_id+'-river-entry',fps=12,seconds=20);end=time.monotonic()+20;trail=[];target=local(site,0,1,-3)
   for i in range(150):
    p=pos();trail.append(p);d=math.hypot(p[0]-target[0],p[2]-target[2])
    if d<.32:break
    c.action('look',yaw=math.degrees(math.atan2(p[0]-target[0],target[2]-p[2])),pitch=0)
    t=max(1,min(5,int(d/.3)));c.key('forward',t);c.ticks(t);q=pos();c.action('release_keys')
    if math.dist(p,q)<.05:c.key('jump',5);c.key('forward',10);c.ticks(10)
   else:raise AssertionError(('river entry blocked',pos(),target))
   c.action('release_keys');c.records['steps'][label]['entryWalk']=trail;c.records['steps'][label]['entryEnd']=pos();c.save();c.screenshot('river-entry-arrival')
   while time.monotonic()<end+.5:time.sleep(.25)
   v=c.action('save_video');p=Path(v['manifest']).parent
   if not p.is_absolute():p=ROOT/'run-devtools'/p
   shutil.copytree(p,c.out/'river-entry');c.records['steps'][label]['video']=v
 c.records['status']='passed';cmd('forceload remove all');c.action('leave_world');time.sleep(3);c.action('quit')
except Exception as e:c.records['status']='failed';c.records['error']=repr(e);raise
finally:c.save()
