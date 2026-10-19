"""R93 full animation audit: stronger secondary follow-through, land squash, run bounce, air tuck/stretch, leg tuck.
Play04 arena. Usage: python3 scripts/verify-r93.py <build_id> [--shots DIR]"""
import argparse, shutil, json
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r93-{name}.png')

def client(name):
    return [x['state'] for x in json.load(open(c.out / f'{name}.json'))['samples'] if x['side'] == 'client']

def chains(samples):
    return [s['animation']['secondary'] for s in samples if s.get('animation', {}).get('secondary')]

def squashes(samples):
    return [s['animation']['squash'] for s in samples if s.get('animation') and 'squash' in s['animation']]

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 18000', 'weather clear 99999',
                    'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
                    'effect give @s minecraft:resistance 900 4 true', 'kill @e[type=!player,distance=..60]', 'clear @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori unlock all'); c.command('ori energy fill'); c.ticks(4)
    c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_BACK')
    c.command(f'fill {X - 10} {Y - 1} {Z - 6} {X + 10} {Y - 1} {Z + 30} minecraft:moss_block')
    c.command(f'fill {X - 10} {Y} {Z - 6} {X + 10} {Y + 24} {Z + 30} minecraft:air')

    # Idle
    c.reset(X + .5, Y, Z + .5, 30, 18); c.ticks(40); shot('night-idle')
    # Run side profile
    c.reset(X + 6.5, Y, Z + 6.5, 0, 12); b = c.start(); c.key('right', 50); c.ticks(16); shot('night-run'); c.ticks(8)
    c.key('left', 16); c.ticks(4); shot('night-turn'); c.ticks(20); c.finish('run_turn', b)
    ch = chains(client('run_turn'))
    c.records['run_chain_n'] = len(ch)
    if ch:
        c.records['tail_tip_range'] = [min(x['tail_yaw'][-1] for x in ch), max(x['tail_yaw'][-1] for x in ch)]
        c.records['tail_tip_lags'] = any(abs(x['tail_yaw'][-1]) > abs(x['tail_yaw'][0]) * 1.25 for x in ch)
        c.records['bones'] = ch[0].get('bones')
        legs = [x.get('legTuck', 0) for x in ch]
        c.records['leg_tuck_run_max'] = max(abs(v) for v in legs) if legs else 0

    # Jump rise/apex/fall
    c.reset(X + 6.5, Y, Z + 8.5, 0, 12); b = c.start(); c.key('right', 30); c.ticks(6); c.key('jump', 8)
    c.ticks(2); shot('night-jump-rise'); c.ticks(5); shot('night-jump-apex'); c.ticks(5); shot('night-jump-fall')
    c.ticks(24); c.finish('jump', b)
    sq = squashes(client('jump')); ch = chains(client('jump'))
    if sq: c.records['jump_squash_range'] = [min(sq), max(sq)]
    if ch:
        c.records['jump_leg_tuck_max'] = max(abs(x.get('legTuck', 0)) for x in ch)
        c.records['jump_ear_tip_range'] = [min(x['ear_lift'][-1] for x in ch), max(x['ear_lift'][-1] for x in ch)]

    # High land
    c.command(f'fill {X} {Y} {Z + 14} {X} {Y + 5} {Z + 14} minecraft:mossy_cobblestone')
    c.reset(X + .5, Y + 6, Z + 14.5, 0, 12); b = c.start(); c.key('forward', 6); c.ticks(12)
    for i in range(5): shot(f'night-land-{i}'); c.ticks(2)
    c.ticks(12); c.finish('land', b)
    sq = squashes(client('land')); ch = chains(client('land'))
    if sq: c.records['land_squash_min'] = min(sq)
    if ch:
        c.records['land_ear_tip_range'] = [min(x['ear_lift'][-1] for x in ch), max(x['ear_lift'][-1] for x in ch)]
        c.records['land_leg_tuck_max'] = max(x.get('legTuck', 0) for x in ch)

    # Day run bounce
    c.command('time set 6000'); c.reset(X + 6.5, Y, Z + 4.5, 0, 12); c.key('right', 40); c.ticks(14); shot('day-run')
    c.ticks(16)

    if args.shots:
        import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
        fig, axes = plt.subplots(2, 2, figsize=(12, 7))
        for ax, step, key in [
            (axes[0][0], 'run_turn', 'tail_yaw'), (axes[0][1], 'run_turn', 'ear_lift'),
            (axes[1][0], 'land', 'ear_lift'), (axes[1][1], 'land', 'tail_lift')]:
            ch = chains(client(step))
            if ch:
                for seg in range(len(ch[0][key])):
                    ax.plot([x[key][seg] for x in ch], label=f'seg{seg+1}')
            ax.set_title(f'{step} {key}'); ax.legend(fontsize=7)
        fig.tight_layout(); fig.savefig(f'{args.shots}/r93-chains.png', dpi=90)

    # Pass gates (soft — log, don't hard-fail the script except status)
    ok = True
    if c.records.get('bones') != 34: ok = False; c.records['fail_bones'] = c.records.get('bones')
    if not c.records.get('tail_tip_lags'): ok = False
    if (c.records.get('land_squash_min') or 1) > 0.82: ok = False
    c.records['status'] = 'ok' if ok else 'warn'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k != 'steps'}, ensure_ascii=False, indent=1))
