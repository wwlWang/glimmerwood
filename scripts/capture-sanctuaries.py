#!/usr/bin/env python3
"""R115 A visual review. Copies/opens an existing pre-R115 save, never places new ruins into the old sites.
Requires an idle DevClient launched with llvmpipe and DISPLAY=:0. The gallery is separate at (6400,150,6400).
Usage: DISPLAY=:0 ... python3 scripts/capture-sanctuaries.py <evidence-name> <gallery|legacy|insitu>
"""
import json
import math
import os
import sys
import time
from devtools_client import DevClient, ROOT
from ruin_driver import RuinDriver

assert os.environ.get('DISPLAY')==':0'
c=DevClient(sys.argv[1]);mode=sys.argv[2]
def look(eye,target):
    dx,dy,dz=[b-a for a,b in zip(eye,target)]
    c.command(f'tp @s {eye[0]} {eye[1]-1.62} {eye[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
def shot(name,centre,offset=(3,2.6,-4.5)):
    c.command('gamemode spectator');look([a+b for a,b in zip(centre,offset)],[centre[0],centre[1]+.8,centre[2]])
    c.ticks(25);c.screenshot(name)
def inspect(p):return c.action('inspect_block',**dict(zip(('x','y','z'),map(int,p))))
def use(p):
    c.command('gamemode creative');c.command('clear @s');c.command(f'tp @s {p[0]+.5} {p[1]} {p[2]-2.5} 0 5');c.ticks(5)
    c.action('use_block',**dict(zip(('x','y','z'),p)));c.ticks(10)
def prepared():
    if 'player' not in c.request():
        c.action('open_world',name=os.environ.get('ORIMOD_CAPTURE_WORLD','orimod_dev_r115legacy'))
        until=time.monotonic()+120
        while 'player' not in c.request() or c.request()['overlay']!='none':
            assert time.monotonic()<until;time.sleep(.5)
    c.action('focus_game');c.action('close_screen');c.ready();c.action('resize',width=1600,height=900)
    c.action('spirit_glow',enabled=False);c.action('shaders',enabled=False);c.action('camera',mode='FIRST_PERSON')
    c.action('settings',hideGui=True,renderDistance=6,fov=65)
    for command in ['gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set 6000','ori awaken','ori form off','ori companion flame off']:
        c.command(command)

try:
    prepared();c.records['mode']=mode
    if mode=='gallery':
        c.command('gamemode spectator');c.command('tp @s 6410 155 6400');c.ticks(80)
        c.command('difficulty peaceful');c.command('kill @e[type=minecraft:item,distance=..80]')
        c.command('fill 6395 149 6395 6448 149 6416 minecraft:stone_bricks');c.command('fill 6395 150 6395 6448 158 6416 minecraft:air')
        ids=['glades_well','forest_spring_shrine','creek_ruins_shrine','cave_altar_shrine','map_stone','storm_altar','gloom_altar']
        for i,id in enumerate(ids):
            p=(6400+i*6,150,6400);c.command('setblock '+' '.join(map(str,p))+' orimod:'+id)
        c.ticks(60)
        for daytime in [6000,18000]:
            c.command('time set '+str(daytime))
            for i,id in enumerate(ids):
                p=(6400+i*6,150,6400);shot(id+('-day' if daytime==6000 else '-night'),(p[0]+.5,p[1],p[2]+.5))
                c.records['steps'][id]=inspect(p);c.save()
        shot('shrine-20-blocks',(6406.5,150,6400.5),(0,1.8,-20))
        c.command('time set 6000')
        p=(6424,150,6400);use(p);c.action('close_screen');shot('map-stone-activated',(6424.5,150,6400.5))
        c.records['steps']['map-active']=inspect(p)
        for i,id,mob in [(5,'storm_altar','gloom_matriarch'),(6,'gloom_altar','gloom_heart')]:
            p=(6400+i*6,150,6400);c.command('difficulty normal');use(p)
            c.records['steps'][id+'-active']=inspect(p);shot(id+'-active',(p[0]+.5,p[1],p[2]+.5))
            c.command('kill @e[type=orimod:'+mob+']');c.ticks(25)
            c.command('kill @e[type=orimod:gloom_spitter,distance=..80]');c.command('kill @e[type=minecraft:item,distance=..80]')
            c.records['steps'][id+'-defeated']=inspect(p);shot(id+'-defeated',(p[0]+.5,p[1],p[2]+.5))
        p=(6400,150,6410)
        c.command('fill 6400 150 6410 6401 151 6410 orimod:keystone_door')
        shot('door-empty',(6401,150,6410.5),(2.6,2.3,4.5))
        c.command('gamemode creative');c.command('tp @s 6401 150 6413 180 4');c.command('item replace entity @s hotbar.0 with orimod:keystone 2');c.action('select_slot',slot=0);c.ticks(5)
        c.action('use_block',**dict(zip(('x','y','z'),p)));c.ticks(5)
        shot('door-one-key',(6401,150,6410.5),(2.6,2.3,4.5))
        c.command('gamemode creative');c.command('tp @s 6401 150 6413 180 4');c.ticks(5)
        c.action('record_video',name=c.build_id+'-door-opening',fps=20,seconds=3)
        c.action('use_block',**dict(zip(('x','y','z'),p)));c.ticks(6);c.screenshot('door-opening');c.ticks(60)
        c.records['video']=c.action('save_video');c.records['steps']['door-open']=inspect(p)
        shot('door-open',(6401,150,6410.5),(2.6,2.3,4.5))
    elif mode=='insitu':
        old=json.loads((ROOT/'evidence/r114-worldgen-v001/results.json').read_text())
        c.command('difficulty peaceful')
        def localshot(d,name,eye,target):
            c.command('gamemode spectator');look(d.absolute(eye),d.absolute(target));c.ticks(25);c.screenshot(name)
        for kind in ['forest_spring','cave_altar']:
            start=old['steps'][kind]['start'];d=RuinDriver(c,kind,start['origin'],start['rotation'])
            c.command('tp @s '+' '.join(map(str,d.safe)));c.ticks(90)
            c.records['steps'][kind]={'start':start,'core_positions':{}}
            features=[('map_stone',(8.5,8.7,1.5)),('storm_altar',(1.5,9,33.5))] if kind=='forest_spring' else [('map_stone',(10.5,4.5,17.5)),('boss_altar',(12.5,4.5,33))]
            for key,eye in features:
                p=d.absolute(d.layout[key],block=True)
                data=inspect(p);assert data['sanctuary']['present'] and data['clientSanctuary']['present'],data
                c.records['steps'][kind]['core_positions'][key]={'position':p,'state':data}
                centre=[v+.5 for v in d.layout[key]];centre[1]+=.7
                for daytime,tag in [(6000,'day'),(18000,'night')]:
                    c.command(f'time set {daytime}');localshot(d,kind+'-'+key+'-'+tag,eye,centre)
                c.save()
            if kind=='cave_altar':
                cells=[d.absolute(p,block=True) for p in d.layout['vault']['door']]
                states=[inspect(p) for p in cells]
                assert all(p['block']=='orimod:keystone_door' and 'filled=0' in p['state'] for p in states),states
                c.records['steps']['vault']={'cells':cells,'empty':states}
                eye=(7.4,3.8,18.6);target=(4.5,3,20)
                for phase in ['empty','one-key','open']:
                    for daytime,tag in [(6000,'day'),(18000,'night')]:
                        c.command(f'time set {daytime}');localshot(d,'cave-vault-'+phase+'-'+tag,eye,target)
                    if phase!='open':
                        c.command('gamemode creative');c.command('clear @s')
                        c.command('item replace entity @s hotbar.0 with orimod:keystone 1');c.action('select_slot',slot=0)
                        look(d.absolute((7,3.62,19.7)),d.absolute(target));c.ticks(5)
                        c.action('use_block',**dict(zip(('x','y','z'),cells[0])));c.ticks(30)
                        now=[inspect(p) for p in cells]
                        if phase=='empty':assert all('filled=1' in a['state'] for a in now),now
                        else:assert all(a['block']=='minecraft:air' for a in now),now
                        c.records['steps']['vault']['one_key' if phase=='empty' else 'open']=now;c.save()
    else:
        old=json.loads((ROOT/'evidence/r114-worldgen-v001/results.json').read_text())
        for kind in ['forest_spring','creek_ruins','cave_altar']:
            start=old['steps'][kind]['start'];d=RuinDriver(c,kind,start['origin'],start['rotation']);p=d.shrine
            c.command(f'tp @s {p[0]} {p[1]+4} {p[2]}');c.ticks(80)
            data=inspect(p);assert data['sanctuary']['present'] and data['clientSanctuary']['present'],data
            c.records['steps'][kind]={'start':start,'shrine':p,'before':data}
            for t,tag in [(6000,'day'),(18000,'night')]:
                c.command(f'time set {t}')
                centre=(p[0]+.5,p[1],p[2]+.5)
                offset=(d.safe[0]-centre[0],d.safe[1]+1.9-centre[1],d.safe[2]-centre[2])
                shot('old-'+kind+'-'+tag,centre,offset)
            use(p);c.records['steps'][kind]['interaction']=c.action('gui_state');c.action('close_screen')
            c.command(f'tp @s {p[0]+500} 160 {p[2]}');c.ticks(100)
            c.command(f'tp @s {p[0]} {p[1]+4} {p[2]}');c.ticks(100)
            data=inspect(p);assert data['clientSanctuary']['present'],data
            c.records['steps'][kind]['resync']=data;c.save()
        c.command('time set 6000');c.command('tp @s -303 112 16');c.ticks(100)
        merchants=c.action('nearby_entities',radius=48)['entities']
        wells=[e['well'] for e in merchants if 'well' in e];assert wells,merchants
        p=wells[0];c.records['steps']['old-outpost']={'well':p,'state':inspect(p)}
        shot('old-outpost-well-day',(p[0]+.5,p[1],p[2]+.5));c.command('time set 18000');shot('old-outpost-well-night',(p[0]+.5,p[1],p[2]+.5))
        use(tuple(p));c.records['steps']['old-outpost']['interaction']=c.action('gui_state');c.action('close_screen')
        c.command(f'tp @s {p[0]+500} 160 {p[2]}');c.ticks(100)
        c.command(f'tp @s {p[0]} {p[1]+4} {p[2]}');c.ticks(100)
        data=inspect(p);assert data['clientSanctuary']['present'],data
        c.records['steps']['old-outpost']['resync']=data
    c.records['status']='captured'
except Exception as e:
    c.records['status']='error';c.records['error']=repr(e);raise
finally:
    c.save();print(c.out)
