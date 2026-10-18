"""Render vanilla player attachments on a disposable client-only player in a test world."""
import sys
import time
from devtools_client import DevClient

c = DevClient(sys.argv[1])
try:
    c.ready(); c.action('close_screen'); c.action('focus_game'); c.action('release_keys')
    c.command('gamemode creative')
    c.command('fill -10 199 -10 10 199 10 minecraft:smooth_stone')
    c.command('fill -10 200 -10 10 206 10 minecraft:air')
    c.command('tp @s 0.5 200 0.5 0 0')
    c.command('time set noon'); c.command('gamerule doDaylightCycle false'); c.command('weather clear')
    for slot in ['armor.head', 'armor.chest', 'armor.legs', 'armor.feet', 'weapon.mainhand', 'weapon.offhand']:
        c.command(f'item replace entity @s {slot} with air')
    c.command('effect give @s minecraft:invisibility 99999 0 true')
    c.action('settings', fov=45, hideGui=False)
    c.action('review_camera', enabled=True, x=2.4, y=1.2, z=3.6, targetHeight=.75)
    reset = dict(spirit=True, glowing=False, invisible=False, arrows=0, stingers=0, cape=False, shoulders=False, nameTag='ALWAYS', teamColor='WHITE',
                 head='minecraft:air', chest='minecraft:air', legs='minecraft:air', feet='minecraft:air',
                 mainhand='minecraft:air', offhand='minecraft:air')
    cases = [
        ('body', {}),
        ('armor', dict(head='minecraft:diamond_helmet', chest='minecraft:diamond_chestplate',
                       legs='minecraft:diamond_leggings', feet='minecraft:diamond_boots')),
        ('items', dict(mainhand='minecraft:bow', offhand='minecraft:shield')),
        ('pumpkin', dict(head='minecraft:carved_pumpkin')),
        ('elytra', dict(chest='minecraft:elytra', cape=True, mainhand='minecraft:iron_sword', offhand='minecraft:shield')),
        ('cape-parrots-stuck', dict(cape=True, shoulders=True, arrows=3, stingers=3)),
        ('glowing', dict(glowing=True, teamColor='AQUA')),
        ('hidden-name', dict(nameTag='NEVER')),
        ('invisible-equipment', dict(invisible=True, mainhand='minecraft:iron_sword', offhand='minecraft:shield', chest='minecraft:elytra')),
        ('invisible-glowing', dict(invisible=True, glowing=True)),
        ('human-control', dict(spirit=False, head='minecraft:diamond_helmet', chest='minecraft:diamond_chestplate',
                               legs='minecraft:diamond_leggings', feet='minecraft:diamond_boots', mainhand='minecraft:iron_sword')),
    ]
    for shaders in [False, True]:
        c.action('shaders', enabled=shaders); c.ticks(25)
        for name, changes in cases:
            c.action('layer_preview', name='Layer check', x=.5, y=200, z=.5, yaw=0, **(reset | changes))
            c.ticks(24)
            label = ('on-' if shaders else 'off-') + name
            c.records['steps'][label] = c.action('layer_preview', name='Layer check')
            c.screenshot(label); c.save()
        c.action('layer_preview', name='Layer check', **(reset | dict(glowing=True, teamColor='AQUA')))
        c.command('fill -2 200 2 3 203 2 minecraft:stone')
        c.ticks(20)
        label = ('on-' if shaders else 'off-') + 'glowing-through-wall'
        c.records['steps'][label] = c.action('layer_preview', name='Layer check')
        c.screenshot(label); c.save()
        c.command('fill -2 200 2 3 203 2 minecraft:air')
    c.action('layer_preview', name='Layer check', enabled=False)
    c.command('effect clear @s minecraft:invisibility')
    c.action('review_camera', enabled=False); c.action('settings', fov=70, hideGui=False)
    c.action('camera', mode='FIRST_PERSON'); c.action('open_pause')
    c.records.update(status='recorded', visual_review='pending', fixture='Client-only player, not multiplayer acceptance')
except Exception as error:
    c.records.update(status='failed', error=str(error)); raise
finally:
    c.save()
