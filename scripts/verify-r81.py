"""R78-R81 real-client check: zero-delay Dash / Glide / Bash-aim prediction (client vs server first tick), dash exit speed,
gait blend weights, trail shots. Play04 arena (1000, 200, 1000). Usage: python3 scripts/verify-r81.py <build_id> [--shots DIR]"""
import argparse, shutil, json, math
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r81-{name}.png')

def first(name, mode):
    s = json.load(open(c.out / f'{name}.json'))['samples']; out = {}
    for side in ('client', 'server'):
        ticks = [x['tick'] for x in s if x['side'] == side and (x['state'].get('movement') or {}).get('mode') == mode]
        out[side] = min(ticks) if ticks else None
    return out

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 6000', 'gamerule doMobSpawning false', 'effect give @s minecraft:resistance 900 4 true']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('grant_dash')
    c.action('grant_bash'); c.action('grant_air_movement'); c.ticks(4)
    c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 6} {Y - 1} {Z - 2} {X + 6} {Y - 1} {Z + 40} minecraft:smooth_stone')
    c.command(f'fill {X - 6} {Y} {Z - 2} {X + 6} {Y + 20} {Z + 40} minecraft:air')
    # 1. Dash: client starts on its own tick; exit carries into the run instead of sliding.
    c.reset(X + .5, Y, Z + .5, 0, 20); b = c.start(); c.key('forward', 24); c.ticks(5); c.key('orimod.dash', 2); c.ticks(3); shot('dash'); c.ticks(22); c.finish('dash', b)
    c.records['dash_first_tick'] = first('dash', 'dash')
    # 2. Run (gait blend) with trail.
    c.reset(X + .5, Y, Z + .5, 0, 20); b = c.start(); c.key('forward', 30); c.ticks(12); shot('run-trail'); c.ticks(20); c.finish('run', b)
    # 3. Glide: jump, hold jump while falling.
    c.reset(X + .5, Y + 8, Z + .5, 0, 10); c.ticks(2); b = c.start(); c.key('jump', 30); c.key('forward', 30); c.ticks(14); shot('glide'); c.ticks(18); c.finish('glide', b)
    c.records['glide_first_tick'] = first('glide', 'glide')
    # 4. Bash aim on an anchor in front.
    c.command(f'setblock {X} {Y + 3} {Z + 4} orimod:bash_anchor')
    c.reset(X + .5, Y, Z + .5, 0, -25); c.key('jump', 2); c.ticks(5); b = c.start(); c.key('orimod.bash', 6); c.ticks(3); shot('bash-aim'); c.ticks(10); c.finish('bash', b)
    c.records['bash_first_tick'] = first('bash', 'bash_aim')
    c.records['movement'] = c.spirit()
finally:
    c.save()
