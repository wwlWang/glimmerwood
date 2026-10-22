"""R113 real-input checks and captures. Launch DevClient with llvmpipe and DISPLAY=:0 first.
Usage: python3 scripts/verify-r113.py <evidence-id> [flight|beauty|inventory|hud|well|death|all]
Does not start/stop Gradle or the client. Uses the existing development world only.
"""
import sys, time
from devtools_client import DevClient
c=DevClient(sys.argv[1]); part=sys.argv[2] if len(sys.argv)>2 else 'all'
X,Y,Z=5800,140,5800

def cmd(s): return c.command(s)
def observe(name,shot=True):
    c.records['steps'][name]={'spirit':c.spirit(),'progress':c.action('progress_state')}
    if shot: c.screenshot(name)
    c.save(); print(name,flush=True)
    return c.records['steps'][name]['spirit']
def form(spirit):
    c.action('release_keys');c.action('close_screen')
    if c.spirit()['server']['form']!=spirit: c.action('toggle_form');c.ticks(5)
    assert c.spirit()['server']['form']==spirit

def ground():
    c.action('release_keys');c.action('review_camera',enabled=False);c.reset(X+.5,Y,Z+.5,0,0);c.ticks(5)

def flight(spirit,feather=True,broken=False):
    ground();form(spirit);cmd('clear @s')
    if feather:cmd('item replace entity @s armor.chest with minecraft:elytra'+('[minecraft:damage=431]' if broken else ''))
    cmd('item replace entity @s hotbar.0 with minecraft:firework_rocket 16');c.action('select_slot',slot=0)
    c.reset(X+.5,Y+45,Z+.5,0,0)
    c.action('review_camera',enabled=True,x=-2.8,y=1.8,z=-2.8,targetHeight=1.2,follow=True)
    c.key('jump',220 if spirit else 2);c.key('forward',180);c.ticks(18 if spirit else 8)
    s=c.spirit();assert s['server']['feather']['gliding'] if spirit else s['server']['vanilla']['fallFlying'],s

def hover(xpos,ypos):
    # GLFW cursor warps suppress their own motion callback. XTest supplies actual pointer input.
    import ctypes as C
    x=C.CDLL('libX11.so.6');t=C.CDLL('libXtst.so.6')
    x.XOpenDisplay.restype=C.c_void_p;d=x.XOpenDisplay(b':0');assert d,'DISPLAY=:0 unavailable'
    x.XDefaultRootWindow.argtypes=[C.c_void_p];x.XDefaultRootWindow.restype=C.c_ulong
    x.XGetInputFocus.argtypes=[C.c_void_p,C.POINTER(C.c_ulong),C.POINTER(C.c_int)]
    x.XGetGeometry.argtypes=[C.c_void_p,C.c_ulong,C.POINTER(C.c_ulong),C.POINTER(C.c_int),C.POINTER(C.c_int)]+[C.POINTER(C.c_uint)]*4
    x.XTranslateCoordinates.argtypes=[C.c_void_p,C.c_ulong,C.c_ulong,C.c_int,C.c_int,C.POINTER(C.c_int),C.POINTER(C.c_int),C.POINTER(C.c_ulong)]
    x.XFlush.argtypes=[C.c_void_p];x.XCloseDisplay.argtypes=[C.c_void_p]
    t.XTestFakeMotionEvent.argtypes=[C.c_void_p,C.c_int,C.c_int,C.c_int,C.c_ulong]
    root=x.XDefaultRootWindow(d);win=C.c_ulong();revert=C.c_int();x.XGetInputFocus(d,C.byref(win),C.byref(revert))
    rr=C.c_ulong();xx=C.c_int();yy=C.c_int();w=C.c_uint();h=C.c_uint();border=C.c_uint();depth=C.c_uint()
    x.XGetGeometry(d,win,C.byref(rr),C.byref(xx),C.byref(yy),C.byref(w),C.byref(h),C.byref(border),C.byref(depth))
    x.XTranslateCoordinates(d,win,root,0,0,C.byref(xx),C.byref(yy),C.byref(rr))
    gui=c.action('gui_state');px=xx.value+round(xpos*w.value/gui['width']);py=yy.value+round(ypos*h.value/gui['height'])
    t.XTestFakeMotionEvent(d,-1,px-1,py-1,0);x.XFlush(d);c.ticks(2)
    t.XTestFakeMotionEvent(d,-1,px,py,0);x.XFlush(d);x.XCloseDisplay(d);c.ticks(5)

def inventory(spirit):
    ground();form(spirit);cmd('clear @s')
    cmd('item replace entity @s hotbar.0 with minecraft:elytra');cmd('item replace entity @s hotbar.1 with minecraft:elytra[minecraft:damage=431]')
    c.action('open_inventory');c.ticks(6)
    for idx,label in [(36,'normal'),(37,'broken')]:
        slots=c.action('container_state')['slots'];slot=next(s for s in slots if s['index']==idx)
        hover(slot['x']+8,slot['y']+8)
        name=('spirit' if spirit else 'human')+'-inventory-'+label
        c.records['steps'][name]={'slots':slots,'spirit':c.spirit()};c.screenshot(name);c.save();print(name,slot['name'],flush=True)
    c.action('close_screen')

try:
    c.action('focus_game');c.action('close_screen');c.ready();c.action('resize',width=1600,height=900)
    c.action('shaders',enabled=False);c.action('settings',renderDistance=4,fpsLimit=40,vsync=False,hideGui=False,fov=70)
    for s in ['difficulty peaceful','weather clear 99999','time set 6000','gamerule doDaylightCycle false','gamerule doMobSpawning false','gamerule sendCommandFeedback false','gamemode survival','ori awaken','ori unlock all','ori companion flame off','effect give @s minecraft:resistance 99999 4 true']:
        cmd(s)
    cmd(f'tp @s {X} {Y+25} {Z}');c.ticks(40)
    cmd(f'forceload add {X-48} {Z-48} {X+48} {Z+48}')
    cmd(f'fill {X-32} {Y-1} {Z-32} {X+32} {Y-1} {Z+32} minecraft:moss_block')
    for y in range(Y,Y+20,4):cmd(f'fill {X-32} {y} {Z-32} {X+32} {y+3} {Z+32} minecraft:air')
    ground()
    if part in ('all','flight'):
        flight(False);observe('human-elytra-flight')
        c.key('use',2);c.ticks(8);observe('human-rocket-boost');assert c.records['steps']['human-rocket-boost']['progress']['server']['inventory']==[{'item':'minecraft:firework_rocket','count':15}]
        flight(True);observe('spirit-feather-glide')
        c.key('use',2);c.ticks(5);observe('spirit-firework-refused');assert c.records['steps']['spirit-firework-refused']['progress']['server']['inventory']==[{'item':'minecraft:firework_rocket','count':16}]
        flight(True,False);observe('spirit-glide-without-equipment')
        flight(True,True,True);observe('spirit-broken-feather-glide')
        # Switch during genuine vanilla flight; the packet must preserve forward movement.
        flight(False);before=observe('handoff-human-before',False);c.action('toggle_form');c.ticks(4)
        mid=observe('handoff-spirit-after',False);assert mid['server']['feather']['gliding'] and not mid['server']['vanilla']['fallFlying']
        c.action('toggle_form');c.ticks(3);after=observe('handoff-human-falling',False)
        assert not after['server']['feather']['gliding'] and not after['server']['vanilla']['fallFlying']
        ground()
    if part=='beauty':
        for spirit in [False,True]:
            flight(spirit);c.action('settings',hideGui=True);c.action('animation_review',enabled=True)
            c.action('review_camera',enabled=True,x=-1.6,y=1.6,z=2.6,targetHeight=1.2,follow=True);c.ticks(5)
            observe(('spirit-feather' if spirit else 'human-elytra')+'-clean')
            c.action('settings',hideGui=False);c.action('animation_review',enabled=False)
        ground()
    if part in ('all','inventory'):
        inventory(False);inventory(True)
    if part in ('all','hud'):
        ground();form(True);cmd('clear @s');cmd('experience set @s 12 levels');cmd('experience set @s 17 points')
        cmd('item replace entity @s armor.chest with minecraft:diamond_chestplate');cmd('ori energy set 67')
        observe('hud-life-energy-armor-xp')
        cmd('effect give @s minecraft:absorption 120 1 true');cmd('effect give @s minecraft:poison 120 0 true');c.ticks(5);observe('hud-poison-absorption')
        cmd('effect clear @s minecraft:poison');cmd('effect give @s minecraft:wither 120 0 true');c.ticks(5);observe('hud-wither-absorption');cmd('effect clear @s minecraft:wither')
    if part in ('all','well'):
        ground();form(True);cmd('clear @s')
        for x in [X,X+24]:cmd(f'setblock {x} {Y} {Z+3} orimod:glades_well[facing=north]')
        for x in [X+24,X]:
            c.action('close_screen');c.reset(x+.5,Y,Z+1.5,0,20);c.action('use_block',x=x,y=Y,z=Z+3);c.ticks(6)
        c.screenshot('well-destination-selection');gui=c.action('gui_state');c.records['steps']['well-menu']=gui;c.save()
        b=next(w for w in gui['widgets'] if str(X+24) in w['text']);c.action('gui_click',x=b['x']+b['width']/2,y=b['y']+10);c.ticks(8)
        s=observe('well-selected-arrival');assert abs(s['server']['position'][0]-(X+24.5))<1
    if part in ('all','death'):
        ground();form(True);cmd(f'spawnpoint @s {X} {Y} {Z}');cmd('gamerule keepInventory false');cmd('clear @s');cmd('give @s minecraft:diamond 1');cmd('kill @s');c.ticks(15)
        observe('death-spirit',True);c.action('respawn');c.ticks(30);s=observe('respawn-still-spirit');assert s['server']['form']
    c.records['status']='ok'
except Exception as e:
    c.records['status']='error';c.records['error']=repr(e);raise
finally:
    try:
        c.action('release_keys');c.action('close_screen');c.action('review_camera',enabled=False);c.action('settings',hideGui=False)
    except Exception:pass
    c.save();print(c.records['status'],c.out,flush=True)
