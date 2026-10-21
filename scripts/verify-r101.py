"""R101 Gloom Tide client acceptance: forced tide at night (fog/sky shift screenshots), a wave in the 16-28 block
ring, a lantern ring that keeps every wave out, Flash and Spirit Tree sanctuary, and dawn clean-up + reward.
Visuals are judged by a reviewer. Requires /ori tide and spirit_state.server.tide (active/nearby/kills/nextWaveIn).
Launch the client with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r101.py <build_id> [--only visual,dimension,wave,lantern,flash,tree,dawn]"""
import argparse, json, math
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='visual,dimension,wave,lantern,flash,tree,dawn')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 5000, 120, 5000
TIDE_TYPES = ('gloom_spitter', 'gloom_hawk')
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def tide():
    return c.spirit()['server'].get('tide', {})

def client_tide():
    return c.spirit().get('client', {}).get('tide', {})

def tide_mobs():
    out = []
    for e in c.action('nearby_entities')['entities']:
        if e['alive'] and any(t in e['type'] for t in TIDE_TYPES):
            dx, dz = e['position'][0] - (X + .5), e['position'][2] - (Z + .5)
            out.append({'type': e['type'], 'h': round(math.hypot(dx, dz), 1)})
    return out

def clear_mobs():
    for t in TIDE_TYPES:
        c.command(f'kill @e[type=orimod:{t}]')
    c.ticks(5)

def pin():
    c.command(f'tp @s {X + .5} {Y} {Z + .5}')

def wait_wave(max_ticks):
    """Tick until tide.nearby rises or max_ticks pass; returns the tide snapshot. Re-pins the player each second."""
    waited = 0
    while waited < max_ticks:
        pin(); c.ticks(20); waited += 20
        t = tide()
        if t.get('nearby', 0) > 0: return t, waited
    return tide(), waited

def lantern_ring(block, radius=22, count=14):
    for i in range(count):
        a = i * 2 * math.pi / count
        x, z = X + round(math.cos(a) * radius), Z + round(math.sin(a) * radius)
        c.command(f'setblock {x} {Y} {z} {block}')

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty normal', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning true', 'effect clear @s minecraft:night_vision', 'time set 6000',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori awaken', 'ori form on',
                    'attribute @s minecraft:generic.knockback_resistance base set 1']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    # Flat 81x81 grass pad, cleared well above, far from anything natural.
    c.command(f'tp @s {X} {Y + 30} {Z}'); c.ticks(80)
    for y in range(Y, Y + 24, 4):
        for ox in (-40, 0):
            c.command(f'fill {X + ox} {y} {Z - 40} {X + ox + 40} {y + 3} {Z + 40} minecraft:air')
    for ox in (-40, 0):
        c.command(f'fill {X + ox} {Y - 1} {Z - 40} {X + ox + 40} {Y - 1} {Z + 40} minecraft:grass_block')
    c.reset(X + .5, Y, Z + .5, 0, 0); c.ticks(20)
    c.command('gamemode survival')
    rejected = c.command('ori tide start')['result']
    check('tide_start_rejected_in_daytime', rejected == 0)
    import time as _t
    DAY = 100 + int(_t.time()) % 100000
    c.records['day'] = DAY
    c.command(f'time set {DAY * 24000 + 18000}'); c.ticks(20)

    if 'visual' in only:
        c.action('settings', hideGui=True); c.action('camera', mode='FIRST_PERSON'); c.action('look', yaw=0, pitch=-15)
        c.ticks(10); c.screenshot('night-before-tide')
    started = c.command('ori tide start')['result']
    check('tide_started', started > 0 and tide().get('active'), tide())
    if 'visual' in only:
        c.ticks(20); early = client_tide().get('intensity', 0)
        c.ticks(100); full = client_tide().get('intensity', 0)
        check('fog_fades_in', 0 < early < full and abs(full - .35) < .02, {'after_1s': early, 'after_6s': full})
        c.screenshot('night-tide-fog')
        c.action('look', yaw=0, pitch=-60); c.ticks(5); c.screenshot('night-tide-sky')
        c.action('settings', hideGui=False); c.ticks(5); c.screenshot('night-tide-hud')
        c.action('look', yaw=0, pitch=0)

    if 'dimension' in only:
        c.command('execute in minecraft:the_nether run tp @s 0 120 0'); c.ticks(60)
        nether = client_tide().get('target', -1)
        c.command(f'execute in minecraft:overworld run tp @s {X} {Y} {Z}'); c.ticks(140)
        back = client_tide()
        check('tide_visuals_survive_dimension_change', nether == 0 and abs(back.get('intensity', 0) - .35) < .02,
              {'nether_target': nether, 'back': back})

    if 'wave' in only:
        clear_mobs()
        waited, mobs, t = 0, [], {}
        while waited < 400 and not mobs:
            pin(); c.ticks(4); waited += 4
            mobs = tide_mobs()
        t = tide()
        check('wave_spawns', t.get('nearby', 0) > 0, {'tide': t, 'waited': waited, 'mobs': mobs})
        check('wave_in_ring', mobs and all(15.5 <= m['h'] <= 28.8 for m in mobs), mobs)
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=0, y=14, z=-20, targetHeight=0); c.ticks(10); c.screenshot('wave-overview')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('settings', hideGui=False)

    if 'lantern' in only:
        clear_mobs()
        lantern_ring('orimod:bash_anchor'); c.ticks(5)
        t, waited = wait_wave(3 * 220)
        check('lantern_ring_blocks_waves', t.get('nearby', 0) == 0 and not tide_mobs(), {'tide': t, 'waited': waited})
        c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=0, y=16, z=-26, targetHeight=0); c.ticks(10); c.screenshot('lantern-ring-night')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON'); c.action('settings', hideGui=False)
        lantern_ring('minecraft:air')
        t, waited = wait_wave(400)
        check('waves_resume_after_lanterns_removed', t.get('nearby', 0) > 0, {'tide': t, 'waited': waited})

    if 'flash' in only:
        clear_mobs()
        c.command('ori unlock all'); c.command('ori energy fill')
        c.key('orimod.flash', 2 * 220)  # hold Flash across two wave intervals; refill so energy never runs out
        waited, t, energy = 0, {}, []
        while waited < 2 * 220 - 20:
            pin(); c.command('ori energy fill'); c.ticks(20); waited += 20
            energy.append(round(c.action('progress_state')['server']['energy'], 1))
            t = tide()
            if t.get('nearby', 0) > 0: break
        c.records['flash_state'] = {'energy_each_second': energy}
        c.action('release_keys')
        check('flash_blocks_waves', t.get('nearby', 0) == 0, {'tide': t, 'waited': waited})

    if 'tree' in only:
        clear_mobs()
        placed = c.command(f'place feature orimod:spirit_tree {X} {Y} {Z + 4}'); c.ticks(10)
        check('sanctuary_tree_placed', placed['result'] > 0, placed)
        t, waited = wait_wave(2 * 220)
        check('spirit_tree_sanctuary', t.get('nearby', 0) == 0, {'tide': t, 'waited': waited})
        for y in range(Y, Y + 24, 4):
            c.command(f'fill {X - 16} {y} {Z - 12} {X + 16} {y + 3} {Z + 20} minecraft:air')

    if 'dawn' in only:
        clear_mobs()
        killed = 0
        for _ in range(4):
            wait_wave(300)
            for t in TIDE_TYPES:
                for _ in range(8):
                    if c.command(f'damage @e[type=orimod:{t},limit=1,sort=nearest] 1000 minecraft:player_attack by @s')['result'] <= 0: break
                    killed += 1
            c.ticks(10)
            if tide().get('kills', 0) >= 5: break
        before = c.action('progress_state')['server']['progress']['light']
        kills = tide().get('kills', 0)
        wait_wave(220)  # leave a few live tide mobs for the dawn sweep
        alive_before = len(tide_mobs())
        c.command(f'time set {DAY * 24000 + 23000}'); c.ticks(20)
        after = c.action('progress_state')['server']['progress']['light']
        t = tide()
        check('dawn_ends_tide', not t.get('active'), t)
        check('dawn_discards_tide_mobs', alive_before > 0 and not tide_mobs(), {'alive_before': alive_before})
        check('dawn_reward', kills >= 5 and after - before >= 50, {'kills': kills, 'light': [before, after]})
        c.ticks(5); c.screenshot('dawn-hud')
        c.command(f'time set {DAY * 24000 + 18000}'); c.ticks(5); c.command(f'time set {DAY * 24000 + 23000}'); c.ticks(20)
        again = c.action('progress_state')['server']['progress']['light']
        check('dawn_reward_once', again == after, {'light': [after, again]})

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('review_camera', enabled=False); c.action('release_keys')
        c.command('ori tide stop'); c.command('gamemode creative')
    except Exception: pass
    c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:4000])
