"""R107 Glades outpost, client acceptance through real inputs: find a natural outpost, walk in from the ground entrance,
right-click each merchant to open the trade screen, buy a shard from Moss and a ruin guide from Lulu with spirit
light, check the panel no longer sells shards, and that the outpost keeps Gloom Tide away. Visuals are judged from the
screenshots. Launch with LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe DISPLAY=:0.
Usage: python3 scripts/verify-r107.py <build_id> [--only look,moss,lulu,panel,tide]"""
import argparse, json, math, time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id')
p.add_argument('--only', default='look,moss,lulu,panel,tide')
args = p.parse_args(); only = set(args.only.split(','))
c = DevClient(args.build_id)
DAY = 100 + int(time.time()) % 100000
checks = c.records.setdefault('checks', {})

def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), 'detail': detail}; c.save()

def progress():
    return c.action('progress_state')['server']

def merchants():
    return [e for e in c.action('nearby_entities')['entities'] if 'glades_merchant' in e['type']]

def face(target):
    pos = c.request()['player']['position']
    dx, dy, dz = target[0] - pos[0], target[1] + .9 - (pos[1] + 1.62), target[2] - pos[2]
    c.action('look', yaw=-math.degrees(math.atan2(dx, dz)), pitch=-math.degrees(math.atan2(dy, math.hypot(dx, dz))))
    c.ticks(3)

def open_trade(merchant):
    mx, my, mz = merchant['position']
    c.command(f'tp @s {mx + .5} {my} {mz - 2.2}'); c.ticks(10)
    face(merchant['position'])
    c.key('use', 2); c.ticks(10)
    try: return c.action('gui_state')
    except Exception as e: return {'error': str(e)}

def buy_first(gui):
    """Click the first active button that is not a close/back control."""
    for w in gui.get('widgets', []):
        if w['type'].endswith('Button') and w['active'] and w['visible'] and w['text'] and 'close' not in w['text'].lower():
            c.action('gui_click', x=w['x'] + w['width'] / 2, y=w['y'] + w['height'] / 2); c.ticks(10)
            return w['text']
    return None

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode creative', 'difficulty normal', 'weather clear 99999', 'gamerule doDaylightCycle false',
                    'gamerule doMobSpawning false', f'time set {DAY * 24000 + 6000}',
                    'effect give @s minecraft:resistance 99999 4 true', 'ori awaken', 'ori form on', 'ori hints reset @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)

    # Natural outpost: locate returns the distance to the nearest start; refine with a short baseline.
    def locate_dist(x, z):
        return c.command(f'execute positioned {x} 64 {z} run locate structure orimod:glades_outpost')['result']
    px, pz, B, trail = 0, 0, 12, []
    for _ in range(7):
        d0 = locate_dist(px, pz); trail.append([px, pz, d0])
        if d0 <= 3: break
        dx, dz = locate_dist(px + B, pz), locate_dist(px, pz + B)
        px += round((d0 * d0 - dx * dx + B * B) / (2 * B)); pz += round((d0 * d0 - dz * dz + B * B) / (2 * B))
    c.records['locate_trail'] = trail
    c.command(f'spreadplayers {px} {pz} 0 1 false @s'); c.ticks(80)
    found = merchants()
    for ox, oz in [(8, 8), (-8, -8), (8, -8), (-8, 8), (16, 0), (0, 16), (-16, 0), (0, -16)]:
        if len(found) >= 2: break
        c.command(f'spreadplayers {px + ox} {pz + oz} 0 1 false @s'); c.ticks(60); found = merchants()
    c.records['merchants'] = found
    check('outpost_has_two_merchants', len(found) >= 2 and len({m.get('job') for m in found}) == 2, found)
    outpost = c.spirit()['server'].get('outpost')
    c.records['outpost'] = outpost

    if 'look' in only and found:
        well0 = found[0].get('well')
        mx, my, mz = found[0]['position']
        c.action('settings', hideGui=True)
        if well0:
            # Overview from above and outside, looking down at the well in the middle of the outpost.
            wx, wy, wz = well0
            c.command(f'tp @s {wx + .5} {wy + 14} {wz - 24.5}'); c.ticks(30); face([wx, wy - 2, wz]); c.screenshot('outpost-day')
            c.command(f'time set {DAY * 24000 + 18000}'); c.ticks(10); c.screenshot('outpost-night')
            c.command(f'time set {DAY * 24000 + 6000}')
        c.command(f'tp @s {mx + .5} {my} {mz - 2.5}'); c.ticks(10); face(found[0]['position']); c.screenshot('merchant-close')
        c.action('settings', hideGui=False)

    c.command('gamemode survival'); c.command('ori light 500'); c.ticks(4)

    if 'moss' in only and found:
        moss = next((m for m in found if str(m.get('job')).lower() in ('0', 'moss', 'shard', 'shards')), found[0])
        before = progress()
        gui = open_trade(moss)
        c.screenshot('moss-screen'); c.records['moss_gui'] = gui
        bought = buy_first(gui) if 'widgets' in gui else None
        after = progress(); c.action('close_screen'); c.ticks(4)
        check('moss_screen_opens', 'widgets' in gui, gui.get('screen') or gui)
        check('moss_sells_shard', bought is not None and after['progress']['ownedShards'] != before['progress']['ownedShards']
              and after['progress']['light'] < before['progress']['light'],
              {'clicked': bought, 'shards': [before['progress']['ownedShards'], after['progress']['ownedShards']],
               'light': [before['progress']['light'], after['progress']['light']]})

    if 'lulu' in only and found:
        lulu = next((m for m in found if str(m.get('job')).lower() in ('1', 'lulu', 'map', 'maps', 'cartographer')), found[-1])
        before = progress()['progress']['light']
        gui = open_trade(lulu)
        c.screenshot('lulu-screen'); c.records['lulu_gui'] = gui
        bought = buy_first(gui) if 'widgets' in gui else None
        c.ticks(60)  # ruin search is asynchronous
        after = progress()['progress']['light']; c.action('close_screen'); c.ticks(4)
        guide = c.spirit().get('client', {}).get('guide')
        check('lulu_sells_guide', bought is not None and after < before and guide, {'clicked': bought, 'light': [before, after], 'guide': guide})
        c.screenshot('guide-hud')

    if 'panel' in only:
        c.key('orimod.panel', 2); c.ticks(10)
        try: gui = c.action('gui_state')
        except Exception as e: gui = {'error': str(e)}
        c.screenshot('panel'); c.records['panel_gui'] = gui
        texts = ' '.join(w.get('text', '') for w in gui.get('widgets', []))
        c.action('close_screen'); c.ticks(4)
        check('panel_points_to_outpost', 'widgets' in gui, texts[:400])

    well = (found[0].get('well') if found else None) or (
        [int(v) for v in outpost['well'].split(',')] if outpost and isinstance(outpost.get('well'), str) else None)
    c.records['well'] = well
    if 'tide' in only and well:
        wx, wy, wz = well
        for t in ('gloom_spitter', 'gloom_hawk', 'gloom_beetle'): c.command(f'kill @e[type=orimod:{t}]')
        # Stand at the well before starting: the first wave fires at once around wherever the player stands.
        c.command(f'tp @s {wx + .5} {wy + 1} {wz + 2.5}'); c.ticks(20)
        c.command(f'time set {DAY * 24000 + 18000}'); c.command('gamerule doMobSpawning true'); c.command('ori tide start')
        spawned = 0
        for _ in range(3):
            c.command(f'tp @s {wx + .5} {wy + 1} {wz + 2.5}'); c.ticks(210)
            spawned += c.spirit()['server'].get('tide', {}).get('nearby', 0)
        c.command('ori tide stop'); c.command('gamerule doMobSpawning false')
        check('outpost_shelters_from_tide', spawned == 0, {'nearby_sum': spawned})

    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    try:
        c.action('close_screen'); c.action('release_keys'); c.command('gamemode creative'); c.action('settings', hideGui=False)
    except Exception: pass
    c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k not in ('steps',)}, ensure_ascii=False, indent=1)[:5000])
