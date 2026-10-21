#!/usr/bin/env python3
"""R108 same-tree comparisons. Open orimod_dev_r108 first; use llvmpipe and DISPLAY=:0.
Uses real right-click and fixed review_camera anchors, so the new ritual relocation cannot move the comparison camera.
"""
import argparse
import json
import ctypes
import ctypes.util
import os
import time
from devtools_client import DevClient

p=argparse.ArgumentParser(description=__doc__);p.add_argument('build_id');args=p.parse_args()
assert os.environ.get('DISPLAY') == ':0', 'Use DISPLAY=:0 for all graphical commands'
c=DevClient(args.build_id)
HEART=(4000,120,3998)
ANCHOR=(4000.5,120,3996.5)
VIEWS={'near':(-4,2.5,-5,1.3),'canopy':(-10,6,-18,8),'wide':(-12,4,-24,10)}

def ritual(): return c.spirit()['server']['restoration']
def age(): return 300-ritual()['remaining']
def wait_age(target):
    deadline=time.monotonic()+40
    while age()<target:
        if not ritual()['active'] or time.monotonic()>deadline: raise RuntimeError('Ceremony ended/stalled before capture')
        time.sleep(.025)

def shot(name):
    c.records.setdefault('captures',{})[name]={'state':c.spirit(),'camera_settings':c.records.get('view'),'player':c.request()['player']}
    c.screenshot(name);c.save()

def move_mouse(dx,dy):
    x11=ctypes.CDLL(ctypes.util.find_library('X11'));xtest=ctypes.CDLL(ctypes.util.find_library('Xtst'))
    x11.XOpenDisplay.argtypes=[ctypes.c_char_p];x11.XOpenDisplay.restype=ctypes.c_void_p
    x11.XFlush.argtypes=[ctypes.c_void_p];x11.XCloseDisplay.argtypes=[ctypes.c_void_p]
    xtest.XTestFakeRelativeMotionEvent.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_ulong]
    display=x11.XOpenDisplay(b':0');assert display,'DISPLAY=:0 unavailable'
    try:
        assert xtest.XTestFakeRelativeMotionEvent(display,dx,dy,0)
        x11.XFlush(display)
    finally:x11.XCloseDisplay(display)

def prepare(view):
    c.action('review_camera',enabled=False);c.command('ori restoration reset');c.ticks(100)
    c.reset(*ANCHOR,0,29.2488);c.command('ori wisp give all');c.ticks(5)
    if view:
        x,y,z,height=VIEWS[view];c.action('review_camera',enabled=True,x=x,y=y,z=z,targetHeight=height,follow=False)
        c.records['view']={'offset':[x,y,z],'targetHeight':height,'anchor':ANCHOR,'follow':False}
    else:
        c.action('camera',mode='FIRST_PERSON')
        c.records['view']='first_person_opening_guide_no_mouse_input'
    c.ticks(5)

try:
    c.action('focus_game');c.action('close_screen');c.ready();c.action('shaders',enabled=False)
    # Consume vanilla ignoreFirstMove outside the session, then confirm native input reaches the player.
    move_mouse(24,0);c.ticks(2);yaw=c.request()['player']['yaw']
    move_mouse(-24,0);c.ticks(2);assert abs(c.request()['player']['yaw']-yaw)>.1
    c.action('settings',hideGui=True,fov=70,fpsLimit=60,vsync=False)
    for command in ['gamemode survival','difficulty peaceful','time set 18000','weather clear 99999',
                    'gamerule doDaylightCycle false','gamerule doMobSpawning false','ori awaken','ori form on',
                    'forceload add 3980 3980 4020 4020']:
        c.command(command)
    c.records['heart']=HEART
    block=c.action('inspect_block',x=HEART[0],y=HEART[1],z=HEART[2]);assert block['block']=='orimod:spirit_tree_heart',block
    # Preserve the existing tree's exact shape and coordinates. Its blessing was claimed in the R108 acceptance world.
    for view,frames in [('canopy',[180,215]),('wide',[260,280]),(None,[20,100,180,260]),('near',[30,80])]:
        prepare(view)
        if view:shot(view+'-unbloomed')
        c.key('use',2);c.ticks(5)
        assert ritual()['active'],ritual()
        if view:assert not c.spirit()['client']['restoration']['viewGuide']['active']
        for target in frames:
            wait_age(target);shot((view or 'first-person')+'-'+str(target))
        while ritual()['active']:c.ticks(10)
        c.ticks(100)
        if view:shot(view+'-bloomed')
    # A real X11 motion event exercises GLFW -> MouseHandler, not the look diagnostic setter.
    prepare(None);c.key('use',2);c.ticks(1)
    before=c.spirit();assert before['client']['restoration']['viewGuide']['active'],before
    move_mouse(24,12)
    c.ticks(2);stopped=c.spirit();angles=c.request()['player']
    assert stopped['client']['restoration']['viewGuide']['cancelled'],stopped
    assert not stopped['client']['restoration']['viewGuide']['active'],stopped
    c.ticks(25);later=c.request()['player'];after=c.spirit()
    assert abs(angles['yaw']-later['yaw'])<.001 and abs(angles['pitch']-later['pitch'])<.001,(angles,later)
    assert after['client']['restoration']['viewGuide']['cancelled'],after
    c.records['mouse_interrupt']={'before':before,'stopped':stopped,'angles':angles,'after':after,'later':later}
    c.action('raw_key',key=256);c.ticks(5)
    assert not ritual()['active']
    c.records['status']='passed'
except Exception as error:
    c.records['status']='failed';c.records['error']=str(error);raise
finally:
    c.action('release_keys');c.action('review_camera',enabled=False);c.action('settings',hideGui=False)
    c.command('forceload remove all');c.save()
print(json.dumps({'status':c.records['status'],'captures':list(c.records.get('captures',{}))}))
