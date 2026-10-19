"""R92 look check against the official Ori references (shots/ref-ori-*.png): soft white-core bloom at night and day,
multi-segment ear/feeler/tail spring chains in motion (run, sharp turn, jump, landing), and the ready stance.
Play04 arena (1000, 200, 1000), shaders off. Usage: python3 scripts/verify-r92.py <build_id> --tag before|after [--shots DIR]"""
import argparse, shutil, json
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--tag', default='after'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r92-{args.tag}-{name}.png')

def client(name):
    return [x['state'] for x in json.load(open(c.out / f'{name}.json'))['samples'] if x['side'] == 'client']

def chains(samples):
    out = []
    for s in samples:
        a = s.get('animation', {}).get('secondary')
        if a: out.append(a)
    return out

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    try: c.action('shaders', enabled=False)
    except Exception as e: c.records['shaders_off_error'] = str(e)
    for command in ['gamemode survival', 'difficulty peaceful', 'time set 6000', 'weather clear 99999', 'gamerule doMobSpawning false',
                    'gamerule doDaylightCycle false', 'effect give @s minecraft:resistance 900 4 true', 'kill @e[type=!player,distance=..60]', 'clear @s']:
        c.command(command)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('ori unlock all'); c.command('ori energy fill'); c.ticks(4)
    c.action('settings', hideGui=True); c.action('camera', mode='THIRD_PERSON_FRONT')
    c.command(f'fill {X - 10} {Y - 1} {Z - 6} {X + 10} {Y - 1} {Z + 30} minecraft:moss_block')
    c.command(f'fill {X - 10} {Y} {Z - 6} {X + 10} {Y + 24} {Z + 30} minecraft:air')
    # 1. Idle, day and night, front view.
    c.reset(X + .5, Y, Z + .5, 180, 12); c.ticks(30); shot('day-idle')
    c.command('time set 18000'); c.ticks(20); shot('night-idle')
    c.action('camera', mode='THIRD_PERSON_BACK'); c.reset(X + .5, Y, Z + .5, 30, 18); c.ticks(20); shot('night-idle-34')
    # 2. Strafe run: the body faces its velocity, so the back camera sees the profile: ears/tail stream back.
    c.reset(X + 6.5, Y, Z + 6.5, 0, 12); b = c.start(); c.key('right', 40); c.ticks(14); shot('night-run-side'); c.ticks(4); shot('night-run-side-2')
    c.key('left', 14); c.ticks(3); shot('night-turn-1'); c.ticks(3); shot('night-turn-2'); c.ticks(16); c.finish('run_turn', b)
    ch = chains(client('run_turn')); c.records['run_turn_chain_samples'] = len(ch)
    if ch:
        c.records['tail_yaw_range'] = [min(x['tail_yaw'][-1] for x in ch), max(x['tail_yaw'][-1] for x in ch)]
        c.records['ear_lift_range'] = [min(x['ear_lift'][-1] for x in ch), max(x['ear_lift'][-1] for x in ch)]
        c.records['tail_tip_lags_root'] = any(abs(x['tail_yaw'][-1]) > abs(x['tail_yaw'][0]) * 1.2 for x in ch)
    # 3. Jump: rise, apex, fall, landing squash with ear flop.
    c.reset(X + 6.5, Y, Z + 8.5, 0, 12); b = c.start(); c.key('right', 30); c.ticks(6); c.key('jump', 8)
    c.ticks(3); shot('night-jump-rise'); c.ticks(5); shot('night-jump-apex'); c.ticks(4); shot('night-jump-fall'); c.ticks(20); c.finish('jump', b)
    ch = chains(client('jump'))
    if ch: c.records['jump_ear_lift_range'] = [min(x['ear_lift'][-1] for x in ch), max(x['ear_lift'][-1] for x in ch)]
    # 4. Landing from height, captured on touchdown.
    c.command(f'fill {X} {Y} {Z + 14} {X} {Y + 5} {Z + 14} minecraft:mossy_cobblestone')
    c.reset(X + .5, Y + 6, Z + 14.5, 0, 12); b = c.start(); c.key('forward', 6); c.ticks(13); shot('night-land'); c.ticks(16); c.finish('land', b)
    ch = chains(client('land'))
    if ch: c.records['land_ear_lift_range'] = [min(x['ear_lift'][-1] for x in ch), max(x['ear_lift'][-1] for x in ch)]
    # 4b. Landing burst: ear flop and tail follow-through frame by frame.
    c.reset(X + .5, Y + 6, Z + 14.5, 0, 12); b = c.start(); c.key('forward', 6); c.ticks(12)
    for i in range(4): shot(f'night-land-burst-{i}'); c.ticks(2)
    c.ticks(10); c.finish('land_burst', b)
    # 5. Day run.
    c.command('time set 6000'); c.reset(X + 6.5, Y, Z + 4.5, 0, 12); c.key('right', 30); c.ticks(14); shot('day-run-side'); c.ticks(20)
    # Evidence plot: every segment's angle over time (tip lags and overshoots the root).
    if args.shots:
        import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
        fig, axes = plt.subplots(2, 2, figsize=(12, 7))
        for row, step in enumerate(['run_turn', 'land_burst']):
            ch = chains(client(step))
            for col, key in enumerate(['tail_yaw' if step == 'run_turn' else 'tail_lift', 'ear_swing' if step == 'run_turn' else 'ear_lift']):
                ax = axes[row][col]
                if ch:
                    for seg in range(len(ch[0][key])):
                        ax.plot([x[key][seg] for x in ch], label=f'{key} seg {seg + 1}')
                ax.set_title(f'{step}: {key} (rad, per client tick)'); ax.legend(fontsize=7)
        fig.tight_layout(); fig.savefig(f'{args.shots}/r92-{args.tag}-chains.png', dpi=90)
    c.records['status'] = 'ok'
except Exception as e:
    c.records['status'] = 'error'; c.records['error'] = repr(e); raise
finally:
    c.action('release_keys'); c.action('settings', hideGui=False); c.save()
    print(json.dumps({k: v for k, v in c.records.items() if k != 'steps'}, ensure_ascii=False, indent=1))
