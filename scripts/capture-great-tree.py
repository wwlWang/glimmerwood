#!/usr/bin/env python3
"""R115 B first-shape checkpoint. Two NEW normal worlds; never /place the landmark for evidence.
Run with DISPLAY=:0 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe after starting runDevtoolsClient.
"""
import json
import math
import os
import sys
import time
from devtools_client import DevClient, ROOT

assert os.environ.get('DISPLAY') == ':0'
resume = '--resume' in sys.argv
args = [arg for arg in sys.argv[1:] if arg != '--resume']
if resume:
    c = DevClient.__new__(DevClient)
    c.build_id = args[0]
    assert c.build_id.replace('-', '').replace('_', '').isalnum()
    c.out = ROOT / 'evidence' / c.build_id
    c.connection = json.loads((ROOT / 'run-devtools/devtools/connection.json').read_text())
    c.records = json.loads((c.out / 'results.json').read_text())
    c.records.setdefault('resumes', []).append(time.time())
else:
    c = DevClient(args[0])
seeds = list(map(int,args[1:])) or [115115,115116]

def command(text):
    result=c.action('command', command=text, captureOutput=True)
    return result

def wait_world():
    deadline=time.monotonic()+240
    while time.monotonic()<deadline:
        state=c.request()
        if 'player' in state and state['overlay']=='none':
            c.action('focus_game');c.action('close_screen');c.ready();return
        time.sleep(.5)
    raise TimeoutError('World startup')

def view(origin,offset,target_height,name):
    eye=[a+b for a,b in zip(origin,offset)]
    target=[origin[0],origin[1]+target_height,origin[2]]
    dx,dy,dz=[b-a for a,b in zip(eye,target)]
    command(f'tp @s {eye[0]} {eye[1]-1.62} {eye[2]} {math.degrees(math.atan2(-dx,dz))} {-math.degrees(math.atan2(dy,math.hypot(dx,dz)))}')
    c.ticks(100);time.sleep(3);c.screenshot(name);print(name,flush=True)

try:
    if 'player' in c.request():c.action('leave_world');time.sleep(3)
    for seed in seeds:
        name=f'orimod_dev_{c.build_id.replace("-","_")}_{seed}'
        prior = c.records['steps'].get(str(seed))
        if prior and 'reload_site' in prior:
            print('Already complete',name,flush=True);continue
        print('Opening' if prior else 'Creating',name,flush=True)
        c.action('settings',renderDistance=6,hideGui=True,fov=65)
        if prior:c.action('open_world',name=name)
        else:c.action('create_world',name=name,seed=seed)
        wait_world()
        c.action('resize',width=1600,height=900)
        c.action('shaders',enabled=False);c.action('spirit_glow',enabled=False)
        c.action('camera',mode='FIRST_PERSON')
        for text in ['gamemode spectator','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','weather clear','time set 6000','ori form off']:
            command(text)
        site=c.action('great_tree_state');assert site['present'],site
        origin=site['origin'];spawn=site['spawn']
        command('tp @s '+' '.join(map(str,spawn)))
        locate=command('locate structure orimod:great_spirit_tree');assert locate['result']>0,locate
        report=command('ori great_tree site');assert report['result']>0,report
        if not prior:c.records['steps'][str(seed)]={'world':name,'site_before':site,'locate':locate,'site_report':report}
        c.save()
        # Visit the tree first to let all slices decorate before taking a distant view.
        command(f'tp @s {origin[0]} {origin[1]+32} {origin[2]-45}')
        c.ticks(160);time.sleep(5)
        c.action('settings',renderDistance=12)
        c.ticks(120);time.sleep(5)
        for daytime,label in [(6000,'day'),(18000,'night')]:
            command(f'time set {daytime}')
            view(origin,(0,40,-140),36,f'{seed}-distance-140-{label}')
            view(origin,(110,42,-110),36,f'{seed}-distance-155-{label}')
        command('time set 6000')
        for offset,height,label in [((0,5,-36),7,'entrance'),((0,1.7,-18),4,'entrance-ground'),((24,8,-27),6,'root-front'),((-31,8,18),6,'root-back'),((38,23,12),18,'base-overview')]:
            view(origin,offset,height,f'{seed}-{label}')
        site=c.action('great_tree_state')
        c.records['steps'][str(seed)]['site_after']=site
        c.records['steps'][str(seed)]['site_report_after']=command('ori great_tree site')
        c.save()
        command('forceload remove all');c.action('leave_world');time.sleep(3)
        # Existing saved site must be republished before neighbouring chunks decorate on a reload.
        c.action('open_world',name=name);wait_world()
        again=c.action('great_tree_state');assert again['origin']==origin,again
        c.records['steps'][str(seed)]['reload_site']=again;c.save()
        c.action('leave_world');time.sleep(3)
    c.records['status']='passed'
finally:
    c.save()
