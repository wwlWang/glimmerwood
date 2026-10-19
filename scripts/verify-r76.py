"""R74-R76 real-client check: run-start/stop velocity samples, light trail, dash afterimage, original textures in world and
hotbar. Play04 arena (1000, 200, 1000). Usage: python3 scripts/verify-r76.py <build_id> [--shots DIR]"""
import argparse, shutil, math
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r76-{name}.png')

def me():
    pos = c.request()['player']['position']
    return pos if isinstance(pos, (list, tuple)) else [pos['x'], pos['y'], pos['z']]

def sample(n):
    out = []
    for _ in range(n): c.ticks(1); out.append(me())
    return out

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 13000', 'gamerule doMobSpawning false',
                    'effect give @s minecraft:resistance 900 4 true']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_dash'); c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 6} {Y - 1} {Z - 2} {X + 6} {Y - 1} {Z + 40} minecraft:smooth_stone')
    c.command(f'fill {X - 6} {Y} {Z - 2} {X + 6} {Y + 6} {Z + 40} minecraft:air')
    # 1. Run start / stop velocity (blocks per tick from positions).
    c.reset(X + .5, Y, Z + .5, 0, 20); c.ticks(10)
    c.key('forward', 14); run = sample(22)
    c.records['run_positions'] = run
    c.records['run_speed_bpt'] = [round(math.hypot(b[0] - a[0], b[2] - a[2]), 4) for a, b in zip(run, run[1:])]
    # 2. Trail and dash afterimage.
    c.reset(X + .5, Y, Z + .5, 0, 20); c.ticks(6)
    c.key('forward', 20); c.ticks(8); shot('trail-run')
    c.key('orimod.dash', 2); c.ticks(3); shot('dash-afterimage')
    c.ticks(8)
    c.key('forward', 12); c.key('jump', 6); c.ticks(5); shot('jump-trail'); c.ticks(12)
    # 3. Original textures placed in the world.
    blocks = ['forest_spring_shrine', 'creek_ruins_shrine', 'cave_altar_shrine', 'map_stone', 'storm_altar', 'gloom_altar',
              'keystone_door', 'spirit_sand', 'updraft', 'life_cell_pedestal', 'energy_cell_pedestal', 'keystone_pedestal', 'gloom_water']
    for i, b in enumerate(blocks):
        c.command(f'setblock {X - 6 + i} {Y} {Z + 8} orimod:{b}')
    c.reset(X + .5, Y + 1, Z + 3.5, 0, 25); c.action('camera', mode='FIRST_PERSON'); c.ticks(10); shot('blocks')
    items = ['spirit_light', 'life_cell', 'energy_cell', 'keystone', 'map_fragment', 'spirit_blade', 'spirit_bow', 'spirit_hammer', 'spirit_spear']
    for i, it in enumerate(items): c.command(f'item replace entity @s hotbar.{i} with orimod:{it}')
    c.command('item replace entity @s weapon.mainhand with orimod:spirit_blade'); c.ticks(6); shot('hotbar')
    c.action('camera', mode='THIRD_PERSON_BACK')
finally:
    c.save()
