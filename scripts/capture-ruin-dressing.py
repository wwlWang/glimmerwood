"""Batch 5 review: place the three dressed ruin templates on open ground and photograph them, day and night,
from an overview point and from a player's eye at the entrance (cave: inside the hall). Visual review only.
Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/capture-ruin-dressing.py <build_id>"""
import json, math, sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])
layouts = {n: json.load(open(f'../src/main/resources/orimod/ruin_layout/{n}.json')) for n in ('forest_spring', 'creek_ruins', 'cave_altar')}
G = 120  # ground level (top block at G - 1)
SITES = {'forest_spring': (7000, 7000), 'creek_ruins': (7100, 7000), 'cave_altar': (7200, 7000)}


def look_at(x, y, z, tx, ty, tz):
    dx, dy, dz = tx - x, ty - y, tz - z
    yaw = math.degrees(math.atan2(-dx, dz)); pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    c.command(f'tp @s {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}')


try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.action('resize', width=1600, height=900); c.ticks(10)
    for command in ['gamemode spectator', 'weather clear 99999', 'gamerule doDaylightCycle false', 'gamerule doMobSpawning false',
                    'gamerule sendCommandFeedback false', 'kill @e[type=!minecraft:player]']:
        c.command(command)
    c.action('settings', hideGui=True, renderDistance=8)
    c.action('camera', mode='FIRST_PERSON')
    for name, (x0, z0) in SITES.items():
        size = layouts[name]['size']; surface = layouts[name]['surface_y']
        c.command(f'tp @s {x0 + 10} {G + 30} {z0 + 15}'); c.ticks(120)  # let the site's chunks load before filling
        for k in range(0, size[0] + 24, 16):  # open ground around the site
            c.command(f'fill {x0 - 12 + k} {G - 6} {z0 - 16} {min(x0 - 12 + k + 15, x0 + size[0] + 12)} {G - 2} {z0 + size[2] + 12} minecraft:dirt')
            c.command(f'fill {x0 - 12 + k} {G - 1} {z0 - 16} {min(x0 - 12 + k + 15, x0 + size[0] + 12)} {G - 1} {z0 + size[2] + 12} minecraft:grass_block')
            c.command(f'fill {x0 - 12 + k} {G} {z0 - 16} {min(x0 - 12 + k + 15, x0 + size[0] + 12)} {G + 30} {z0 + size[2] + 12} minecraft:air')
        oy = G - surface  # the layout's walking surface lands on the ground (the cave's stair mouth too)
        c.command(f'place template orimod:ruins/{name} {x0} {oy} {z0}'); c.ticks(40)
        cx, cz = x0 + size[0] / 2, z0 + size[2] / 2
        entry = layouts[name]['entry']
        for time, label in (('6000', 'day'), ('18000', 'night')):
            c.command(f'time set {time}'); c.ticks(5)
            if name == 'cave_altar':
                look_at(x0 + 11.5, oy + 9.5, z0 + 17.5, x0 + 11.5, oy + 4, z0 + 30); c.ticks(30); c.screenshot(f'{name}-hall-{label}')
                look_at(x0 + 11.5, oy + 10.5, z0 + 36.5, x0 + 11.5, oy + 4, z0 + 20); c.ticks(20); c.screenshot(f'{name}-back-{label}')
            else:
                look_at(cx + size[0] * .9, G + size[1] * .7, z0 - size[2] * .35, cx, G + 3, cz); c.ticks(30); c.screenshot(f'{name}-overview-{label}')
                look_at(x0 + entry[0] + .5, oy + entry[1] + 1.1, z0 + entry[2] - 2.5, x0 + entry[0] + .5, oy + entry[1] + 3, z0 + size[2] * .7)
                c.ticks(20); c.screenshot(f'{name}-entry-{label}')
    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('settings', hideGui=False); c.command('gamemode creative'); c.command('time set 6000')
    except Exception: pass
    c.save()
    print(c.records['status'], c.out)
