"""R113 recovery/wheel real input captures, llvmpipe + DISPLAY=:0 client required.
Usage: python3 scripts/verify-r113-recovery-wheel.py <new-evidence-id>
Uses the development world, writes a small test platform, never starts Gradle or edits assets.
"""
import ctypes as C
import time
import sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])
xlib=C.CDLL('libX11.so.6');xt=C.CDLL('libXtst.so.6')
xlib.XOpenDisplay.restype=C.c_void_p;display=xlib.XOpenDisplay(b':0');assert display
xlib.XKeysymToKeycode.argtypes=[C.c_void_p,C.c_ulong];xlib.XKeysymToKeycode.restype=C.c_uint
xlib.XFlush.argtypes=[C.c_void_p];xlib.XCloseDisplay.argtypes=[C.c_void_p]
xt.XTestFakeKeyEvent.argtypes=[C.c_void_p,C.c_uint,C.c_int,C.c_ulong]
xt.XTestFakeMotionEvent.argtypes=[C.c_void_p,C.c_int,C.c_int,C.c_int,C.c_ulong]
xlib.XDefaultRootWindow.argtypes=[C.c_void_p];xlib.XDefaultRootWindow.restype=C.c_ulong
xlib.XGetInputFocus.argtypes=[C.c_void_p,C.POINTER(C.c_ulong),C.POINTER(C.c_int)]
xlib.XGetGeometry.argtypes=[C.c_void_p,C.c_ulong,C.POINTER(C.c_ulong),C.POINTER(C.c_int),C.POINTER(C.c_int)]+[C.POINTER(C.c_uint)]*4
xlib.XTranslateCoordinates.argtypes=[C.c_void_p,C.c_ulong,C.c_ulong,C.c_int,C.c_int,C.POINTER(C.c_int),C.POINTER(C.c_int),C.POINTER(C.c_ulong)]
held=set()
def key(char,down):
    code=xlib.XKeysymToKeycode(display,ord(char));assert code
    xt.XTestFakeKeyEvent(display,code,int(down),0);xlib.XFlush(display)
    if down:held.add(char)
    else:held.discard(char)
def tap(char):
    key(char,True);time.sleep(.07);key(char,False);c.ticks(3)
def hover(gx,gy):
    win=C.c_ulong();revert=C.c_int();xlib.XGetInputFocus(display,C.byref(win),C.byref(revert))
    rr=C.c_ulong();xx=C.c_int();yy=C.c_int();w=C.c_uint();h=C.c_uint();border=C.c_uint();depth=C.c_uint()
    xlib.XGetGeometry(display,win,C.byref(rr),C.byref(xx),C.byref(yy),C.byref(w),C.byref(h),C.byref(border),C.byref(depth))
    xlib.XTranslateCoordinates(display,win,xlib.XDefaultRootWindow(display),0,0,C.byref(xx),C.byref(yy),C.byref(rr))
    gui=c.action('gui_state');px=xx.value+round(gx*w.value/gui['width']);py=yy.value+round(gy*h.value/gui['height'])
    xt.XTestFakeMotionEvent(display,-1,px-1,py-1,0);xlib.XFlush(display);time.sleep(.05)
    xt.XTestFakeMotionEvent(display,-1,px,py,0);xlib.XFlush(display);c.ticks(4)
def observe(name):
    c.records['steps'][name]={'spirit':c.spirit(),'progress':c.action('progress_state'),'state':c.request()}
    c.screenshot(name);c.save();print(name,flush=True)
    return c.records['steps'][name]
def hp():return c.spirit()['server']['health']
def cmd(text):
    result=c.command(text)
    assert result.get('result',1)>0,(text,result)
    return result
try:
    if 'player' not in c.request():
        c.action('open_world',name='orimod_dev_r113');time.sleep(3)
    c.action('focus_game');c.action('close_screen');c.ready()
    c.action('resize',width=1600,height=900);c.action('gui_scale',scale=3)
    c.action('shaders',enabled=False);c.action('settings',renderDistance=4,fpsLimit=40,vsync=False,hideGui=False,fov=70)
    for command in ['difficulty normal','weather clear 99999','time set 6000','gamerule doDaylightCycle false','gamerule doMobSpawning false',
                    'gamerule naturalRegeneration true','gamemode survival','ori awaken','ori unlock all','ori companion flame off','ori form on','clear @s','effect clear @s']:
        c.command(command)
    c.command('forceload add 5776 5776 5832 5832');c.command('tp @s 5800.5 160 5800.5');c.ticks(25)
    c.command('fill 5784 139 5784 5816 139 5816 minecraft:moss_block')
    for y in range(140,152,4):c.command(f'fill 5784 {y} 5784 5816 {y+3} 5816 minecraft:air')
    c.reset(5800.5,140,5800.5,0,0);c.action('camera',mode='THIRD_PERSON_BACK');c.ticks(210)
    # Tap opens the old panel only after release, held press opens the wheel only.
    tap('g');assert 'SpiritScreen' in c.request()['screen'];observe('panel-tap');c.action('close_screen')
    before=c.request()['player'];key('g',True);time.sleep(.4);c.ticks(4)
    wheel=c.spirit()['client']['skillWheel'];assert wheel['open'],wheel
    cx,cy=wheel['centre'];hover(cx-wheel['radius']*.70,cy)
    wheel=c.spirit()['client']['skillWheel'];assert wheel['hovered']=='regenerate',wheel
    after=c.request()['player'];assert before['yaw']==after['yaw'] and before['pitch']==after['pitch'],(before,after)
    observe('wheel-hover-regenerate');tap('h');c.ticks(4)
    assert c.spirit()['server']['skills']['slots'][2]=='regenerate'
    observe('wheel-bind-h');key('g',False);c.ticks(4)
    assert c.request()['screen']=='gameplay';observe('hud-slots')
    # Damage starts an actual server timer; no health injection and no passive-healing speedup.
    cmd('ori energy set 20');cmd('effect give @s minecraft:instant_health 1 5 true');c.ticks(3)
    cmd('damage @s 8 minecraft:generic');assert hp()<16
    observe('life-damaged');c.ticks(130);assert hp()<16
    observe('life-before-delay');c.ticks(240);assert abs(hp()-16)<.01,hp()
    c.ticks(60);assert abs(hp()-16)<.01,hp();observe('life-passive-cap-80')
    key('h',True);c.ticks(10);observe('regenerate-channel');c.ticks(16);key('h',False);c.ticks(4)
    assert hp()>16,hp();observe('regenerate-above-80')
    # Binding move, empty slot, and release cannot accidentally cast the newly assigned hold skill.
    key('g',True);time.sleep(.4);c.ticks(4);wheel=c.spirit()['client']['skillWheel'];cx,cy=wheel['centre']
    hover(cx-wheel['radius']*.7,cy);key('z',True);c.ticks(4);key('g',False);c.ticks(6)
    assert not c.spirit()['server']['recovery']['regenerating'];key('z',False);c.ticks(3)
    assert c.spirit()['server']['skills']['slots']==['regenerate','charge_flame',''];observe('binding-moved-empty-slot')
    # Restore original two slots + H=Regenerate for reviewer convenience.
    key('g',True);time.sleep(.4);c.ticks(4);w=c.spirit()['client']['skillWheel'];cx,cy=w['centre']
    hover(cx-w['radius']*.7,cy);tap('h');hover(cx,cy-w['radius']*.7);tap('z');key('g',False);c.ticks(5)
    # The former fixed mappings still route every hold/release/tap through the original server action.
    cmd('ori energy set 100');key('x',True);c.ticks(17);key('x',False);c.ticks(3)
    assert abs(c.action('progress_state')['server']['energy']-90)<.1;observe('charge-flame-slot-release')
    cmd('ori energy set 100');tap('z');assert abs(c.action('progress_state')['server']['energy']-85)<.1;observe('burst-slot-cooldown')
    cmd('ori energy set 0');observe('hud-insufficient-energy')
    key('g',True);time.sleep(.4);c.ticks(4);w=c.spirit()['client']['skillWheel'];cx,cy=w['centre']
    hover(cx,cy+w['radius']*.7);tap('h');key('g',False);c.ticks(4);cmd('ori energy set 100')
    key('h',True);c.ticks(12);observe('flash-slot-hold');key('h',False);c.ticks(4)
    e=c.action('progress_state')['server']['energy'];assert e<100,e
    key('g',True);time.sleep(.4);c.ticks(4);w=c.spirit()['client']['skillWheel'];cx,cy=w['centre']
    hover(cx-w['radius']*.7,cy);tap('h');key('g',False);c.ticks(4)
    c.records['status']='passed';c.save()
finally:
    for char in list(held):key(char,False)
    c.action('release_keys');c.action('close_screen');xlib.XCloseDisplay(display);c.save()
