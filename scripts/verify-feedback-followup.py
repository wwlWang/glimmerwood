"""Real-key lunge, first-person weapons and Bash captures. llvmpipe + DISPLAY=:0 only."""
import math
import sys
import time
from devtools_client import DevClient

c = DevClient(sys.argv[1])
state = c.request()
if 'player' not in state:
    c.action('open_world', name='orimod_dev_feedback_r108')
c.action('focus_game'); time.sleep(1); c.action('close_screen'); c.ready()
c.action('review_camera', enabled=False)
c.action('settings', fov=60, renderDistance=2, hideGui=False)
c.action('shaders', enabled=False)
for command in ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set noon',
                'weather clear', 'gamemode survival', 'clear @s', 'ori awaken', 'ori form on',
                'ori unlock all', 'ori companion flame off', 'forceload add 288 288 320 320',
                'fill 294 198 294 314 198 320 minecraft:stone',
                'fill 294 199 294 314 210 320 minecraft:air']:
    c.command(command)
c.action('camera', mode='FIRST_PERSON')
c.reset(304.5, 199, 304.5)
c.command('item replace entity @s weapon.mainhand with orimod:spirit_blade'); c.ticks(20)
before = c.start()
for i in range(6):
    c.key('attack', 1); c.ticks(6)
c.ticks(30)
after = c.finish('three-slashes', before)
c.records['steps']['three-slashes']['distance'] = math.dist(before['client']['position'], after['client']['position'])
c.records['steps']['flame-off'] = c.spirit()
c.command('ori companion flame on'); c.ticks(5)
c.records['steps']['flame-on'] = c.spirit()
c.command('ori companion flame off')

# Same dark-room light and camera as the reviewed C captures, with all four spirit weapons.
c.command('fill 298 198 298 310 206 310 minecraft:gray_concrete hollow')
c.reset(304.5, 199, 304.5, 0, 15)
c.command('time set midnight')
for pack in ['none', 'Spirit Glow.zip']:
    if pack == 'none': c.action('shaders', enabled=False)
    else: c.action('iris_pack', name=pack)
    c.ticks(20)
    for item in ['iron_sword', 'spirit_blade', 'spirit_spear', 'spirit_bow', 'spirit_hammer']:
        namespace = 'minecraft' if item == 'iron_sword' else 'orimod'
        c.command(f'item replace entity @s weapon.mainhand with {namespace}:{item}')
        c.ticks(20); c.screenshot(pack.split()[0] + '-' + item)
    c.records['steps']['hands-' + pack] = c.spirit(); c.save()

c.action('shaders', enabled=False)
c.command('fill 298 199 298 310 206 310 minecraft:air')
c.command('time set noon'); c.command('clear @s')
c.command('setblock 304 200 308 orimod:bash_anchor')
try:
    for view in ['end-on', 'tilted']:
        c.reset(304.5, 199, 304.5)
        eye = c.spirit()['client']['eyeHeight']
        pitch = -math.degrees(math.atan2(200.5 - (199 + eye), 4))
        c.action('look', yaw=0, pitch=pitch)
        c.action('focus_game'); time.sleep(.5); c.ready()
        c.ticks(6)
        # Slow the vanilla tick clock only for the short aim screenshot; no ability timers are patched.
        c.command('tick rate 5')
        c.action('key', name='key.orimod.bash', ticks=30)
        c.ticks(2)
        if view == 'tilted': c.action('look', yaw=18, pitch=pitch-18)
        c.records['steps']['bash-' + view] = c.spirit()
        assert c.records['steps']['bash-' + view]['client']['movement']['mode'] == 'bash_aim'
        c.screenshot('bash-' + view); c.save()
        c.action('release_keys'); c.command('tick rate 20'); c.ticks(20)
finally:
    c.action('release_keys'); c.command('tick rate 20')
c.action('release_keys')
c.reset(304.5,199,304.5,0,15)
c.records['status'] = 'captured'; c.save()
print(c.out)
