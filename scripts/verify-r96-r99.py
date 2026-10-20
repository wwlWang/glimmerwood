"""R96–R99 client acceptance: Spirit Lantern, Glimmer companion, Flash, new shards. Screenshots plus server-side
state checks; visual quality is judged from the shots by a reviewer, never auto-passed.
Arena (2000, 200, 2000) in a dedicated orimod_dev_ world.
Usage: python3 scripts/verify-r96-r99.py <build_id> [--only lantern,glimmer,flash,shards]"""
import argparse, json
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--only', default='lantern,glimmer,flash,shards')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 2000, 200, 2000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}
    c.save()

def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)

def lit(x, y, z):
    return 'lit=true' in json.dumps(block(x, y, z))

def progress():
    return c.action('progress_state')['server']

def energy():
    return progress()['energy']

def companion():
    """Client-side Glimmer diagnostics (devtools spirit_state.client.companion, R97 follow-up); None if absent."""
    return c.spirit()['client'].get('companion')

def side(x=0, y=.9, z=4.2, h=.55):
    c.action('review_camera', enabled=True, x=x, y=y, z=z, targetHeight=h)

def clear(top=20):
    """Vanilla /fill caps at 32768 blocks, so clear the arena in 4-high slabs."""
    for y in range(Y, Y + top + 1, 4):
        c.command(f'fill {X - 20} {y} {Z - 20} {X + 40} {min(y + 3, Y + top)} {Z + 40} minecraft:air')

def room(x0, z0, size=9, height=5):
    """A sealed dark stone room with an air interior; returns the interior floor y."""
    c.command(f'fill {x0} {Y - 1} {z0} {x0 + size} {Y + height} {z0 + size} minecraft:deepslate_bricks')
    c.command(f'fill {x0 + 1} {Y} {z0 + 1} {x0 + size - 1} {Y + height - 1} {z0 + size - 1} minecraft:air')
    return Y

try:
    c.action('focus_game'); c.action('close_screen')
    if c.request().get('player', {}).get('health', 1) <= 0: c.action('respawn'); c.ticks(20)
    c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode survival', 'difficulty easy', 'time set 18000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule sendCommandFeedback false',
                    'kill @e[type=!player,distance=..120]', 'clear @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori unlock all'); c.command('ori energy fill'); c.command('ori light 5000'); c.ticks(4)
    # Load the arena chunks before editing them: commands into unloaded chunks silently fail.
    c.command('effect give @s minecraft:resistance 99999 4 true'); c.command('effect give @s minecraft:slow_falling 60 0 true')
    c.command(f'tp @s {X + 10} {Y + 2} {Z + 10}'); c.ticks(80)
    c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 20} {Y - 1} {Z - 20} {X + 40} {Y - 1} {Z + 40} minecraft:moss_block')
    clear()

    if 'lantern' in only:
        # R96: dark room, three lanterns on the far wall; Light Burst ignites them.
        room(X, Z)
        lanterns = [(X + 3, Y + 2, Z + 8), (X + 5, Y + 2, Z + 8), (X + 7, Y + 2, Z + 8)]
        for lx, ly, lz in lanterns: c.command(f'setblock {lx} {ly} {lz} orimod:bash_anchor')
        c.reset(X + 5.5, Y, Z + 2.5, 0, -5); c.action('settings', hideGui=True); side(x=1.8, z=-2.4, y=1.2, h=1.0); c.ticks(10)
        c.screenshot('lantern-dim')
        check('lantern_dim_by_default', not any(lit(*l) for l in lanterns))
        c.key('orimod.light_burst', 2); c.ticks(14)
        c.screenshot('lantern-burst-lit')
        lit_after = [lit(*l) for l in lanterns]
        check('light_burst_ignites', any(lit_after), lit_after)
        c.ticks(40); c.screenshot('lantern-burst-lit-later')
        c.ticks(70)
        check('lantern_expires', not any(lit(*l) for l in lanterns))
        c.screenshot('lantern-expired')
        # Charge Flame next to the lanterns.
        c.reset(X + 5.5, Y, Z + 6.5, 0, 0); c.ticks(6)
        c.key('orimod.charge_flame', 16); c.ticks(20)
        c.screenshot('lantern-charge-flame')
        check('charge_flame_ignites', any(lit(*l) for l in lanterns))
        c.action('review_camera', enabled=False)

    if 'glimmer' in only:
        # R97: companion on open ground at night, idle and running, then in a dark room, then a hint.
        clear()
        c.command('ori hints reset'); c.command('effect give @s minecraft:resistance 900 4 true')
        c.reset(X + 10.5, Y, Z + 10.5, 0, 10); c.action('settings', hideGui=True); side(x=1.6, z=2.2, y=1.0, h=1.1); c.ticks(30)
        for i in range(3): c.screenshot(f'glimmer-idle-{i}'); c.ticks(5)
        c.reset(X + 10.5, Y, Z - 15.5, 0, 10); side(x=-3.0, z=0.4, y=1.0, h=1.0); c.ticks(6)
        c.key('sprint', 50); c.key('forward', 50); c.ticks(20)
        lags = []
        for i in range(3):
            c.screenshot(f'glimmer-run-{i}'); comp = companion()
            if comp: lags.append(comp.get('anchorDistance'))
            c.ticks(2)
        c.action('release_keys'); c.ticks(20)
        c.records['run_lag'] = lags
        if lags: check('glimmer_follows_while_sprinting', all(l is not None and l < 2.5 for l in lags), lags)
        # Low health mood (resistance absorbs nothing from /damage generic? use effect harm-free set via damage).
        c.command('effect clear @s minecraft:resistance'); c.command('damage @s 15 minecraft:generic'); c.ticks(30)
        c.screenshot('glimmer-low-health')
        c.command('effect give @s minecraft:instant_health 1 5 true'); c.command('effect give @s minecraft:resistance 900 4 true'); c.ticks(10)
        # Dark room: ground pool under the companion, third and first person.
        room(X + 20, Z)
        c.reset(X + 25.5, Y, Z + 5.5, 0, 20); side(x=1.6, z=2.2, y=1.0, h=1.0); c.ticks(30)
        c.screenshot('glimmer-dark-third')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('look', yaw=0, pitch=45); c.ticks(20)
        c.screenshot('glimmer-dark-first')
        c.action('camera', mode='THIRD_PERSON_BACK')
        # Hint: an updraft near the player in the open; GUI visible to read the action bar.
        clear()
        c.command(f'setblock {X + 14} {Y} {Z + 14} orimod:updraft')
        c.reset(X + 10.5, Y, Z + 10.5, -45, 10); c.command('ori hints reset'); c.action('settings', hideGui=False)
        side(x=2.0, z=2.6, y=1.4, h=1.0)
        hinted = None
        for i in range(30):
            c.ticks(5)
            seen = [k for k in progress()['progress']['rewards'] if k.startswith('hint/updraft')]
            if seen: hinted = seen; break
        check('updraft_hint_recorded', bool(hinted), hinted)
        for i in range(4): c.screenshot(f'glimmer-hint-{i}'); c.ticks(8)
        c.action('review_camera', enabled=False)

    if 'flash' in only:
        # R98: dark room with two lanterns, a Gloom Spitter and gloom water; hold Flash.
        clear()
        room(X, Z + 20, size=12)
        lz = Z + 28  # within Flash.RADIUS (7) of the player at Z + 24.5
        for lx in (X + 3, X + 9): c.command(f'setblock {lx} {Y + 2} {lz} orimod:bash_anchor')
        c.command(f'setblock {X + 6} {Y - 1} {Z + 26} orimod:gloom_water')
        c.command(f'summon orimod:gloom_spitter {X + 6.5} {Y} {Z + 29.5}')
        c.reset(X + 6.5, Y, Z + 24.5, 0, 0); c.command('ori energy fill'); c.action('settings', hideGui=True)
        side(x=2.4, z=-2.6, y=1.4, h=1.0); c.ticks(10)
        spitter = lambda: [e for e in c.action('nearby_entities').get('entities', []) if 'gloom_spitter' in json.dumps(e)]
        before = {'energy': energy(), 'spitter': spitter()}
        c.screenshot('flash-before')
        c.key('orimod.flash', 70); c.ticks(12)
        c.screenshot('flash-0'); c.ticks(14); c.screenshot('flash-1')
        mid = {'energy': energy(), 'lanterns': [lit(lx, Y + 2, lz) for lx in (X + 3, X + 9)]}
        c.ticks(30); c.screenshot('flash-2')
        after = {'energy': energy(), 'spitter': spitter(), 'water': block(X + 6, Y - 1, Z + 26)}
        c.ticks(20); c.screenshot('flash-released')
        c.records['flash'] = {'before': before, 'mid': mid, 'after': after}
        check('flash_drains_energy', before['energy'] is not None and after['energy'] is not None and after['energy'] < before['energy'] - 10,
              [before['energy'], mid['energy'], after['energy']])
        check('flash_lights_lanterns', all(mid['lanterns']), mid['lanterns'])
        check('flash_cleanses_gloom_water', 'gloom_water' not in json.dumps(after['water']), after['water'])
        c.action('review_camera', enabled=False)

    if 'shards' in only:
        # R99: buy + equip Sticky, wall slide speed against a tall wall; panel screenshots.
        clear(30)
        for shard in ['sticky', 'life_harvest', 'thorn', 'ultra_bash', 'energy_harvest']:
            c.action('progress_action', action='shard', target=shard); c.ticks(2)
        for shard in ['sticky', 'thorn', 'ultra_bash', 'life_harvest']:
            c.action('progress_action', action='equip', target=shard); c.ticks(2)
        prog = progress(); c.records['shard_progress'] = {'owned': prog['progress']['ownedShards'], 'equipped': prog['progress']['equippedShards'], 'slots': prog['slots']}
        c.command(f'fill {X} {Y} {Z + 5} {X + 6} {Y + 25} {Z + 5} minecraft:stone')
        c.reset(X + 3.5, Y + 20, Z + 4.4, 0, 0); c.action('settings', hideGui=True); side(x=-3.2, z=-.8, y=.6, h=.6); c.ticks(4)
        b = c.start(); c.key('forward', 3); c.ticks(30)
        c.screenshot('sticky-idle-on-wall')
        mid = c.spirit()
        y_hold = mid['server']['position'][1]
        c.ticks(20); held = c.spirit()
        c.key('back', 20); c.ticks(14)
        c.screenshot('sticky-push-away')
        away = c.spirit()
        c.finish('sticky', b)
        c.records['sticky_mid'] = mid
        modes = [x['server']['movement']['mode'] for x in (mid, held, away)]
        drop = y_hold - held['server']['position'][1]
        c.records['sticky'] = {'modes': modes, 'drop_over_20_ticks': drop}
        check('sticky_holds_on_wall', modes[0] == 'wall_slide' and modes[1] == 'wall_slide' and drop < 1.5, c.records['sticky'])
        check('sticky_releases_when_pushing_away', modes[2] != 'wall_slide', modes)
        c.action('review_camera', enabled=False); c.action('settings', hideGui=False)
        c.reset(X + 10.5, Y, Z + 10.5, 0, 0); c.ticks(4)
        c.key('orimod.panel', 2); c.ticks(10); c.screenshot('panel-0')
        c.action('gui_scroll', x=480, y=300, amount=-10); c.ticks(4); c.screenshot('panel-scrolled')
        c.action('close_screen')

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try: c.action('review_camera', enabled=False)
    except Exception: pass
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps', 'sticky_mid')}, ensure_ascii=False, indent=1))
