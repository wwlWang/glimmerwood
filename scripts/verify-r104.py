"""R104 light platforms, client acceptance through real inputs: ghost platforms carry nobody until a lantern lit by
Charge Flame, a held Flash, a Light Burst or a Spirit Tree heart lights them; they blink before going out and drop
the player; a natural forest spring light bridge can be lit and crossed. Visuals are judged from the screenshots.
Launch the client with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r104.py <build_id> [--only ghost,lantern,flash,burst,tree,ruin]"""
import argparse, json
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='ghost,lantern,flash,burst,tree,ruin')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 8000, 120, 8000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def state(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)['state']

def lit(x, y, z):
    return 'lit=true' in state(x, y, z)

def player_y():
    return c.request()['player']['position'][1]

def pad(pit=True):
    """Stone floor with a 7-deep pit under the platform row at Z+3..Z+7, X-1..X+1."""
    c.command('gamemode creative')
    for y in range(Y - 8, Y + 16, 4):
        c.command(f'fill {X - 20} {y} {Z - 20} {X + 20} {y + 3} {Z + 20} minecraft:air')
    c.command(f'fill {X - 20} {Y - 9} {Z - 20} {X + 20} {Y - 1} {Z + 20} minecraft:stone_bricks')
    if pit: c.command(f'fill {X - 2} {Y - 8} {Z + 2} {X + 2} {Y - 1} {Z + 8} minecraft:air')
    c.command(f'fill {X - 1} {Y - 1} {Z + 3} {X + 1} {Y - 1} {Z + 7} orimod:spirit_platform')
    c.reset(X + .5, Y, Z + .5, 0, 10); c.ticks(10)

def row():
    return [(X + dx, Y - 1, Z + dz) for dx in (-1, 0, 1) for dz in range(3, 8)]

def survival():
    c.command('gamemode survival'); c.command('ori energy fill'); c.ticks(4)

def stands_on_platform():
    """Drop the player onto the platform middle; True if they stop on it instead of falling into the pit."""
    c.command(f'tp @s {X + .5} {Y + 1} {Z + 5.5}'); c.ticks(30)
    return player_y() >= Y - .1

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty peaceful', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', 'time set 6000', 'effect give @s minecraft:resistance 99999 4 true',
                    'ori awaken', 'ori form on', 'ori unlock all', 'ori hints reset @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command(f'tp @s {X} {Y + 20} {Z}'); c.ticks(60)

    if 'ghost' in only:
        pad(); c.action('settings', hideGui=True)
        c.screenshot('ghost-day'); c.command('time set 18000'); c.ticks(10); c.screenshot('ghost-night')
        c.command('time set 6000'); c.action('settings', hideGui=False)
        survival()
        check('ghost_unlit', not any(lit(*b) for b in row()))
        check('ghost_has_no_collision', not stands_on_platform(), {'y': player_y()})

    if 'lantern' in only:
        pad()
        c.command(f'setblock {X} {Y} {Z + 1} orimod:bash_anchor')  # 2-6 blocks from the row
        survival(); c.reset(X + .5, Y, Z - 1.5, 0, 10)
        c.key('orimod.charge_flame', 16); c.ticks(20)
        on = [b for b in row() if lit(*b)]
        check('lantern_lights_platforms', len(on) >= 9, {'lit': len(on)})
        c.action('settings', hideGui=True); c.screenshot('lantern-lit'); c.action('settings', hideGui=False)
        check('lit_platform_carries_player', stands_on_platform(), {'y': player_y()})
        # Lantern burns 100 ticks, platform keeps 100 more, blinking for the last 30: watch it go out under the player.
        blink, gone = None, None
        for t in range(0, 260, 5):
            c.command(f'tp @s {X + .5} {Y} {Z + 5.5}'); c.ticks(5)
            s = state(X, Y - 1, Z + 5)
            if blink is None and ('fading=true' in s or 'blink=true' in s): blink = t; c.screenshot('platform-blink')
            if 'lit=false' in s: gone = t; break
        c.ticks(20)
        check('platform_blinks_then_goes_out', blink is not None and gone is not None and blink < gone,
              {'blink_at': blink, 'out_at': gone})
        check('player_drops_when_out', player_y() < Y - 1, {'y': player_y()})

    if 'flash' in only:
        pad(); survival(); c.reset(X + .5, Y, Z + .5, 0, 10)
        c.key('orimod.flash', 60); c.ticks(25)
        on = [b for b in row() if lit(*b)]
        c.action('release_keys')
        check('flash_lights_platforms', len(on) >= 6, {'lit': len(on)})

    if 'burst' in only:
        pad(); survival(); c.reset(X + .5, Y, Z + .5, 0, 35)  # aim at the pit floor beyond the row
        c.key('orimod.light_burst', 2); c.ticks(30)
        on = [b for b in row() if lit(*b)]
        check('light_burst_lights_platforms', len(on) >= 3, {'lit': len(on)})

    if 'tree' in only:
        pad(pit=False)
        placed = c.command(f'place feature orimod:spirit_tree {X} {Y} {Z + 14}'); c.ticks(20)
        on = [b for b in row() if lit(*b)]
        check('tree_heart_keeps_platforms_lit', placed['result'] > 0 and len(on) >= 9, {'placed': placed, 'lit': len(on)})
        c.ticks(240)
        still = [b for b in row() if lit(*b)]
        check('tree_light_is_permanent', len(still) == len(on), {'after_240': len(still)})

    if 'ruin' in only:
        def locate_dist(x, z):
            return c.command(f'execute positioned {x} 64 {z} run locate structure orimod:forest_spring')['result']
        px, pz, B, trail = 0, 0, 12, []
        for _ in range(6):
            d0 = locate_dist(px, pz); trail.append([px, pz, d0])
            if d0 <= 3: break
            dx, dz = locate_dist(px + B, pz), locate_dist(px, pz + B)
            px += round((d0 * d0 - dx * dx + B * B) / (2 * B)); pz += round((d0 * d0 - dz * dz + B * B) / (2 * B))
        c.records['locate_trail'] = trail
        bridges = []
        for ox, oz in [(0, 0), (4, 4), (-4, -4), (8, 0), (0, 8), (-8, 0), (0, -8), (8, 8)]:
            c.command('gamemode creative'); c.command(f'spreadplayers {px + ox} {pz + oz} 0 1 false @s'); c.ticks(40)
            bridges = c.action('ruin_state').get('bridges', [])
            if bridges: break
        c.records['bridges'] = bridges
        check('ruin_bridge_reported', len(bridges) == 1, bridges)
        if bridges:
            b = bridges[0]; ex, ey, ez = b['entry']
            c.command(f'tp @s {ex + .5} {ey} {ez + .5}'); c.ticks(20)
            import math
            xx, _, xz = b['exit']
            c.action('look', yaw=-math.degrees(math.atan2(xx - ex, xz - ez)), pitch=15); c.ticks(4)
            c.action('settings', hideGui=True); c.screenshot('ruin-bridge-ghost'); c.action('settings', hideGui=False)
            survival()
            # Bash the overhanging lantern from the shore, as a player would: the bridge must light on release and
            # the player must land on a lit platform, not in the water.
            import math
            lx, ly, lz = b['lanterns'][0]
            c.command(f'tp @s {ex + .5} {ey} {ez + .5}'); c.ticks(10)
            ddx, ddy, ddz = lx - ex, ly + .5 - (ey + 1.62), lz - ez
            yaw = -math.degrees(math.atan2(ddx, ddz)); pitch = -math.degrees(math.atan2(ddy, math.hypot(ddx, ddz)))
            c.action('look', yaw=yaw, pitch=pitch); c.ticks(4)
            c.key('orimod.bash', 8); c.ticks(14)
            c.screenshot('ruin-bridge-bash')
            c.ticks(30)
            check('ruin_bridge_lantern_lit', 'lit=true' in state(lx, ly, lz), {'lantern': [lx, ly, lz]})
            pos = c.request()['player']['position']
            below = state(math.floor(pos[0]), math.floor(pos[1] - .5), math.floor(pos[2]))
            in_water = c.spirit()['server'].get('movement', {}).get('inWater')
            check('ruin_bridge_lands_on_lit_platform', 'spirit_platform' in below and 'lit=true' in below,
                  {'pos': pos, 'below': below, 'inWater': in_water})
            c.action('settings', hideGui=True); c.screenshot('ruin-bridge-lit'); c.action('settings', hideGui=False)
            c.command('time set 18000'); c.ticks(10)
            c.action('settings', hideGui=True); c.screenshot('ruin-bridge-night'); c.action('settings', hideGui=False)
            c.command('time set 6000')

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('release_keys'); c.command('gamemode creative'); c.action('settings', hideGui=False)
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:4000])
