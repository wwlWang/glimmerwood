"""FX02: load check and contact sheet of every spirit effect definition in the client.
Requires all 13 definitions loaded without problems (also after a resource reload), then plays each
kind in front of the camera at dusk over a dark floor and screenshots it.
Usage: python3 scripts/verify-spirit-fx-look.py <build_id> [--sheet out.png]"""
import argparse, subprocess
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--sheet'); args = p.parse_args()
c = DevClient(args.build_id)
KINDS = ['BLADE_HIT', 'STOMP_IMPACT', 'ARROW_TRAIL', 'CHECKPOINT_REACHED', 'ROUTE_MARKER', 'DASH', 'DOUBLE_JUMP',
         'WALL_JUMP', 'BASH', 'GRAPPLE_HOOK', 'FORM_SWITCH', 'GLIDE', 'GRAPPLE_TETHER']
X, Y, Z = 1000, 200, 1000
try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    state = c.action('spirit_fx_state'); c.records['loaded'] = state
    assert state['loaded'] == 13 and not state['problems'], state
    c.action('reload_resources'); c.ticks(200); state = c.action('spirit_fx_state'); c.records['reloaded'] = state
    assert state['loaded'] == 13 and not state['problems'] and state['reloads'] >= 2, state
    c.command('gamemode spectator'); c.command(f'fill {X-6} {Y-1} {Z-2} {X+6} {Y-1} {Z+10} minecraft:deepslate_tiles')
    c.command('time set 13000'); c.command(f'tp @s {X+.5} {Y+.4} {Z+.2} 0 18'); c.action('settings', hideGui=True); c.ticks(20)
    shots = []
    for kind in KINDS:
        for i in range(6):
            c.action('spirit_fx_burst', kind=kind, x=X + .5, y=Y + 1, z=Z + 2.5); c.ticks(2)
        c.screenshot('fx-' + kind.lower()); shots.append(c.out / f'fx-{kind.lower()}.png'); c.ticks(30)
    if args.sheet:
        inputs = sum((['-i', str(s)] for s in shots), [])
        cols, rows = 5, 3
        lay = '|'.join(f'{"+".join(["w0"] * (i % cols)) or "0"}_{"+".join(["h0"] * (i // cols)) or "0"}' for i in range(len(shots)))
        scale = ''.join(f'[{i}:v]crop=iw*0.5:ih*0.5,scale=384:-1[v{i}];' for i in range(len(shots)))
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', *inputs, '-filter_complex',
                        scale + ''.join(f'[v{i}]' for i in range(len(shots))) + f'xstack=inputs={len(shots)}:layout={lay}:fill=black',
                        '-frames:v', '1', args.sheet], check=True)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('settings', hideGui=False); c.command('gamemode survival'); c.command('time set 6000')
print(c.out, flush=True)
