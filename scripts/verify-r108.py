"""R108 Wisps of Will and Restoration, client acceptance through real inputs: place a spirit tree, receive its blessing,
check the panel's Wisps page before/after collecting all three, right-click the heart in spirit form to run the full
15-second ceremony (movement keys must not move the player, mouse look stays free), then check the reward, the
advancement and bloomed leaves. Also: real Esc (through KeyboardHandler) skips without opening the pause menu, a
repeat ceremony pays nothing, leaving the dimension cancels and keeps the wisps, and a restored world's tide is spitters only.
Visuals are judged from the screenshots. Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r108.py <build_id> [--only panel,ritual,repeat,esc,cancel,tide]"""
import argparse, json, math
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='panel,ritual,repeat,esc,cancel,tide')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
X, Y, Z = 4000, 120, 4000
GLFW_ESCAPE = 256
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def progress():
    return c.action('progress_state')['server']

def light():
    return progress()['progress']['light']

def block(x, y, z):
    return c.action('inspect_block', x=x, y=y, z=z)

def state():
    s = c.spirit()
    return {'server': s['server'].get('restoration'), 'client': s.get('client', {}).get('restoration'),
            'wisps': s['server'].get('wisps'), 'pos': c.request()['player']['position']}

def find_heart(cx, cy, cz, r=6):
    for dy in range(-3, 4):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if block(cx + dx, cy + dy, cz + dz)['block'] == 'orimod:spirit_tree_heart':
                    return cx + dx, cy + dy, cz + dz
    return None

def find_leaves(hx, hy, hz, limit=12):
    found = []
    for dx, dz in [(0, 0), (3, 0), (-3, 0), (0, 3), (0, -3), (5, 5), (-5, -5), (5, -5), (-5, 5)]:
        for dy in range(2, 26):
            b = block(hx + dx, hy + dy, hz + dz)
            if b['block'] == 'orimod:spirit_leaves':
                found.append((hx + dx, hy + dy, hz + dz)); break
        if len(found) >= limit: break
    return found

def bloomed(positions):
    return sum('bloomed=true' in block(*q)['state'] for q in positions)

def has_advancement():
    return c.command('execute if entity @s[advancements={orimod:restoration=true}]')['result'] > 0

def face(target, dy=.5):
    pos = c.request()['player']['position']
    tx, ty, tz = target[0] + .5 - pos[0], target[1] + dy - (pos[1] + 1.62), target[2] + .5 - pos[2]
    c.action('look', yaw=-math.degrees(math.atan2(tx, tz)), pitch=-math.degrees(math.atan2(ty, math.hypot(tx, tz))))
    c.ticks(3)

def approach(heart):
    hx, hy, hz = heart
    c.reset(hx + .5, hy, hz - 1.5, 0, 20); c.ticks(10); face(heart)

def right_click():
    c.key('use', 2); c.ticks(8)

def open_wisp_page(name):
    c.key('orimod.panel', 2); c.ticks(10)
    gui = c.action('gui_state')
    tab = next((w for w in gui.get('widgets', []) if w.get('text') in ('Will Wisps', '意志之光')), None)
    if tab: c.action('gui_click', x=tab['x'] + tab['width'] / 2, y=tab['y'] + tab['height'] / 2); c.ticks(6)
    gui = c.action('gui_state'); c.screenshot(name)
    c.action('close_screen'); c.ticks(4)
    return {'tab': tab is not None, 'widgets': [w.get('text') for w in gui.get('widgets', []) if w.get('text')]}

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty peaceful', 'time set 18000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'gamerule sendCommandFeedback false',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori restoration reset']:
        c.command(command)
    c.command(f'tp @s {X} {Y + 30} {Z}'); c.ticks(80)
    for y in range(Y, Y + 40, 4):
        c.command(f'fill {X - 24} {y} {Z - 24} {X + 24} {min(y + 3, Y + 39)} {Z + 24} minecraft:air')
    c.command(f'fill {X - 24} {Y - 2} {Z - 24} {X + 24} {Y - 1} {Z + 24} minecraft:grass_block')
    c.command(f'place feature orimod:spirit_tree {X} {Y} {Z}'); c.ticks(10)
    heart = find_heart(X, Y, Z); c.records['heart'] = heart
    check('tree_placed', heart is not None, heart)
    if heart is None: raise RuntimeError('No spirit tree heart')
    hx, hy, hz = heart
    leaves = find_leaves(*heart); c.records['leaf_samples'] = leaves
    check('leaves_unbloomed_before', leaves and bloomed(leaves) == 0, {'samples': len(leaves), 'bloomed': bloomed(leaves)})

    c.command('gamemode survival'); c.command('ori awaken'); c.command('ori form on'); c.command('ori wisp clear')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori light 0'); c.ticks(2)
    # Blessing first: without the three wisps the heart grants its ability as in R100.
    approach(heart); abilities = progress()['abilities']; right_click()
    # A rerun in the same world already holds this tree's blessing (the heart stays bloomed).
    check('blessing_received', progress()['abilities'] != abilities or 'bloomed=true' in block(*heart)['state'],
          [abilities, progress()['abilities']])

    c.action('settings', hideGui=True)
    c.action('review_camera', enabled=True, x=12, y=8, z=-16, targetHeight=6); c.ticks(10)
    c.screenshot('tree-night-before')
    c.action('review_camera', enabled=False); c.action('settings', hideGui=False); c.ticks(4)

    if 'panel' in only:
        c.records['panel_empty'] = open_wisp_page('panel-wisps-empty')
        check('panel_has_wisp_page', c.records['panel_empty']['tab'], c.records['panel_empty'])
    c.command('ori wisp give all'); c.ticks(4)
    check('wisps_collected', state()['wisps']['mask'] == 7, state()['wisps'])
    if 'panel' in only:
        c.records['panel_full'] = open_wisp_page('panel-wisps-full')

    if 'ritual' in only:
        approach(heart); before_light = light(); right_click()
        start = state(); c.records['ritual_start'] = start
        check('ritual_starts', start['server']['active'] and start['client']['active'], start)
        origin = start['pos']; samples = []; shots = {1, 3, 4, 5, 6, 8, 10, 12, 13, 14}
        c.action('settings', hideGui=True)
        moved = None
        for second in range(1, 16):
            if second == 2:
                # Real movement keys during the ceremony; the server-held origin must not change.
                c.key('forward', 20); c.key('jump', 10); c.key('left', 20)
            if second == 7:
                # Mouse look stays free; the yaw change must stick.
                c.action('look', yaw=120, pitch=-25)
            c.ticks(20)
            s = state(); samples.append({'second': second, **s})
            if second == 3: moved = max(abs(a - b) for a, b in zip(s['pos'], origin))
            if second in shots: c.screenshot(f'ritual-{second:02d}')
        c.action('release_keys')
        c.records['ritual_samples'] = samples
        drift = max(max(abs(a - b) for a, b in zip(s['pos'], origin)) for s in samples if s['server'] and s['server']['active'])
        check('movement_locked', drift < .05, {'max_drift': drift, 'after_keys': moved})
        yaw = c.request()['player'].get('yaw'); c.records['yaw_after_look'] = yaw
        c.ticks(20)
        end = state(); after_light = light(); c.records['ritual_end'] = end
        check('ritual_finishes', not end['server']['active'] and end['server']['completed'] and end['server']['restored'], end)
        check('reward_500_light', after_light - before_light == 500, [before_light, after_light])
        check('advancement_granted', has_advancement())
        c.ticks(60)
        n = bloomed(leaves); check('leaves_bloomed_after', n == len(leaves), {'bloomed': n, 'samples': len(leaves)})
        c.screenshot('ritual-after-hud'); c.action('settings', hideGui=False)
        c.action('settings', hideGui=True)
        c.action('review_camera', enabled=True, x=12, y=8, z=-16, targetHeight=6); c.ticks(10)
        c.screenshot('tree-night-after')
        c.action('review_camera', enabled=False); c.action('settings', hideGui=False); c.ticks(4)

    if 'repeat' in only:
        approach(heart); before = light(); right_click()
        repeat = state()
        if repeat['server']['active']:
            c.ticks(60); c.action('raw_key', key=GLFW_ESCAPE); c.ticks(10)
        c.ticks(10)
        check('repeat_pays_nothing', light() == before and state()['server']['completed'], {'light': [before, light()], 'started': repeat['server']['active']})

    if 'esc' in only:
        c.command('ori restoration reset'); c.ticks(40)
        approach(heart); before = light(); right_click()
        started = state()['server']['active']
        c.ticks(60); c.screenshot('esc-before')
        key = c.action('raw_key', key=GLFW_ESCAPE); c.ticks(10)
        after = state(); screen = c.request()['screen']; c.screenshot('esc-after')
        c.records['esc'] = {'started': started, 'key': key, 'after': after, 'screen': screen}
        check('esc_skips_without_pause_menu', started and not after['server']['active'] and screen == 'gameplay', c.records['esc'])
        check('esc_settles_same_reward', light() - before == 500 and after['server']['completed'], [before, light()])
        # Outside the ceremony Esc is vanilla again.
        c.action('raw_key', key=GLFW_ESCAPE); c.ticks(6); screen = c.request()['screen']
        check('esc_outside_opens_pause', screen != 'gameplay', screen)
        c.action('close_screen'); c.ticks(4)

    if 'cancel' in only:
        c.command('ori restoration reset'); c.ticks(120)
        n0 = bloomed(leaves)
        approach(heart); before = light(); right_click()
        started = state()['server']['active']; c.ticks(180)
        mid = bloomed(leaves)
        # Damage (even /kill) is cancelled during the ceremony, so a dimension change exercises the cancel path.
        c.command('execute in minecraft:the_nether run tp @s 0 100 0'); c.ticks(40)
        c.command(f'execute in minecraft:overworld run tp @s {hx + .5} {hy} {hz - 1.5}'); c.ticks(40)
        c.ready(); c.ticks(120)
        after = state(); n1 = bloomed(leaves)
        c.records['cancel'] = {'started': started, 'after': after, 'bloomed': [n0, mid, n1]}
        check('dimension_change_cancels_keeps_wisps', started and not after['server']['active'] and not after['server']['completed']
              and after['wisps']['mask'] == 7 and light() == before, c.records['cancel'])
        check('cancel_reverts_leaves', n1 == 0, c.records['cancel']['bloomed'])
        c.command('effect give @s minecraft:resistance 99999 4 true')

    if 'tide' in only:
        if not state()['server']['restored']:
            approach(heart); right_click(); c.ticks(20); c.action('raw_key', key=GLFW_ESCAPE); c.ticks(20)
        c.command('ori form on'); c.command('difficulty normal')
        c.command(f'tp @s {X + 200} {Y + 30} {Z}'); c.ticks(60)
        c.command(f'fill {X + 180} {Y - 2} {Z - 20} {X + 220} {Y - 1} {Z + 20} minecraft:grass_block')
        c.command(f'tp @s {X + 200} {Y} {Z}'); c.ticks(20)
        c.command('gamerule doMobSpawning true'); c.command('ori tide start')
        types = {}
        for _ in range(3):
            c.ticks(200)
            for e in c.action('nearby_entities')['entities']:
                if 'gloom' in e['type'] and 'shot' not in e['type']:  # skip spitter projectiles
                    types[e['type']] = types.get(e['type'], 0) + 1
        c.command('ori tide stop'); c.command('gamerule doMobSpawning false')
        for t in ('gloom_spitter', 'gloom_hawk', 'gloom_beetle', 'gloom_crawler'): c.command(f'kill @e[type=orimod:{t}]')
        c.records['tide_types'] = types
        check('restored_tide_spitters_only', types and all('spitter' in t for t in types), types)

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('close_screen'); c.action('release_keys'); c.action('review_camera', enabled=False)
        c.command('gamemode creative'); c.action('settings', hideGui=False)
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps', 'ritual_samples')}, ensure_ascii=False, indent=1)[:6000])
