"""Batch 5 review on real terrain: in a fresh world, visit spread-out areas until each ruin kind has generated naturally,
then photograph each from outside (high three-quarter and ground level) and from its entrance. Visual review only.
Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/capture-natural-ruins.py <build_id> <world_name> <seed> [per_kind]"""
import math, sys, time
from devtools_client import DevClient
from ruin_driver import RuinDriver

build, world, seed = sys.argv[1], sys.argv[2], int(sys.argv[3])
per_kind = int(sys.argv[4]) if len(sys.argv) > 4 else 2
c = DevClient(build)
c.records['world'] = world; c.records['seed'] = seed
KINDS = ('forest_spring', 'creek_ruins', 'cave_altar')


def look_at(x, y, z, tx, ty, tz):
    dx, dy, dz = tx - x, ty - y, tz - z
    yaw = math.degrees(math.atan2(-dx, dz)); pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    c.command(f'tp @s {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}')


try:
    if 'player' in c.request(): c.action('leave_world')
    c.action('create_world', name=world, seed=seed)
    deadline = time.monotonic() + 120
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline; time.sleep(.5)
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.action('resize', width=1600, height=900); c.ticks(10)
    for command in ['gamemode spectator', 'difficulty peaceful', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
                    'time set 6000', 'weather clear', 'gamerule sendCommandFeedback false']:
        c.command(command)
    c.action('settings', hideGui=True, renderDistance=8)
    seen = {k: [] for k in KINDS}
    areas = [(x, z) for r in (0, 600, 1200, 1800) for x, z in ((r, 0), (0, r), (-r, 0), (0, -r), (r, r), (-r, -r))][:20]
    for ax, az in dict.fromkeys(areas):
        if all(len(v) >= per_kind for v in seen.values()): break
        c.command(f'tp @s {ax} 200 {az}'); c.ticks(160)
        for start in c.action('ruin_state').get('naturalStarts', []):
            kind = start['kind']
            if kind not in seen or len(seen[kind]) >= per_kind or start['origin'] in [s['origin'] for s in seen[kind]]: continue
            seen[kind].append(start)
            d = RuinDriver(c, kind, start['origin'], start.get('rotation', 'NONE'))
            size = d.layout['size']; label = f'{kind}-{len(seen[kind])}'
            centre = d.absolute((size[0] / 2, d.layout['surface_y'], size[2] / 2))
            entry = d.absolute(d.layout['entry'])
            front = d.absolute((size[0] / 2, d.layout['surface_y'], -14))
            c.command(f'tp @s {centre[0]:.1f} {centre[1] + 30:.1f} {centre[2]:.1f}'); c.ticks(120)
            # High three-quarter from in front of the entrance, then ground level outside, then the entrance itself.
            look_at(front[0] + (front[0] - centre[0]) * .2 + 12, centre[1] + 18, front[2] + (front[2] - centre[2]) * .2,
                    centre[0], centre[1] + 2, centre[2]); c.ticks(40); c.screenshot(label + '-high')
            look_at(front[0], front[1] + 2.5, front[2], centre[0], centre[1] + 3, centre[2]); c.ticks(25); c.screenshot(label + '-ground')
            look_at(entry[0], entry[1] + 1.6, entry[2], centre[0], centre[1] + 2, centre[2]); c.ticks(25); c.screenshot(label + '-entry')
            c.records['steps'][label] = {'start': start, 'centre': centre}; c.save()
    c.records['found'] = {k: len(v) for k, v in seen.items()}
    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('settings', hideGui=False)
    except Exception: pass
    c.save()
    print(c.records.get('status'), c.records.get('found'), c.out)
