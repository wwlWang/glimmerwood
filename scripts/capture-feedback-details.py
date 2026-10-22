"""Closer hand-side views and real bow charging for A–E. Run with DISPLAY=:0."""
import sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])
c.action('focus_game'); c.action('close_screen'); c.ready()
c.action('shaders', enabled=False)
c.action('settings', fov=60, renderDistance=2, mainArm='RIGHT', hideGui=False)
for cmd in ['gamemode creative', 'ori form on', 'time set 6000', 'kill @e[type=minecraft:item]',
            'forceload add 288 288 320 320', 'fill 288 199 288 320 199 320 minecraft:gray_concrete',
            'fill 288 200 288 320 215 320 minecraft:air', 'clear @s', 'give @s minecraft:arrow 64']:
    c.command(cmd)
c.reset(304.5, 200, 304.5)

def shot(name):
    c.records['steps'][name] = {'before': c.spirit()}
    c.screenshot(name)
    c.records['steps'][name]['after'] = c.spirit()
    c.save()
    print(name, flush=True)

try:
    for arm in ['RIGHT', 'LEFT']:
        c.action('settings', mainArm=arm)
        for item in ['spirit_blade', 'spirit_hammer', 'spirit_spear', 'spirit_bow', 'iron_sword']:
            ns = 'minecraft' if item == 'iron_sword' else 'orimod'
            c.command(f'item replace entity @s weapon.mainhand with {ns}:{item}'); c.ticks(20)
            sign = -1 if arm == 'RIGHT' else 1
            for view, offset in [('side', (sign * 1.6, .9, 0)), ('diagonal', (sign * 1.6, .9, 1.6))]:
                c.action('review_camera', enabled=True, x=offset[0], y=offset[1], z=offset[2], targetHeight=.75, follow=False)
                c.ticks(8); shot(f'{arm}-{item}-{view}')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON')
        c.action('look', yaw=0, pitch=0)
        c.command('item replace entity @s weapon.mainhand with orimod:spirit_bow'); c.ticks(15)
        shot(arm + '-bow-idle-first')
        c.key('use', 100)
        c.ticks(12); shot(arm + '-bow-charge-mid-first')
        c.ticks(10); shot(arm + '-bow-charge-full-first')
        c.action('release_keys'); c.ticks(8)
    c.action('settings', mainArm='RIGHT')
    c.command('item replace entity @s weapon.mainhand with orimod:spirit_blade'); c.command('gamemode survival')
    c.ticks(15); c.action('open_inventory'); shot('blade-inventory-pose'); c.action('close_screen')
    c.records['status'] = 'captured'
finally:
    c.action('release_keys'); c.action('close_screen'); c.action('review_camera', enabled=False)
    c.action('settings', mainArm='RIGHT', hideGui=False)
    c.save()
