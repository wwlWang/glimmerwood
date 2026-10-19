"""FX02: load check and look sheets of every spirit effect definition in the client.
Requires all 14 definitions loaded without problems (also after a resource reload). Then, at dusk over a dark
floor with a fixed first-person spectator camera close to the effect, plays each kind the way the game emits it
(one burst for one-shots, one burst per tick for trails/persistent effects) and screenshots it early (~2 ticks)
and mid-animation (~5 ticks). A second pass shows selected effects around Ori in third person for scale.
Usage: python3 scripts/verify-spirit-fx-look.py <build_id> [--sheet out.png] [--ori out.png]"""
import argparse
from PIL import Image, ImageDraw, ImageFont
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--sheet'); p.add_argument('--ori'); args = p.parse_args()
c = DevClient(args.build_id)
KINDS = ['BLADE_HIT', 'STOMP_IMPACT', 'ARROW_TRAIL', 'CHECKPOINT_REACHED', 'ROUTE_MARKER', 'DASH', 'DOUBLE_JUMP',
         'WALL_JUMP', 'BASH', 'BASH_AIM', 'GRAPPLE_HOOK', 'FORM_SWITCH', 'GLIDE', 'GRAPPLE_TETHER']
PERSISTENT = {'GLIDE', 'GRAPPLE_TETHER', 'BASH_AIM'}
X, Y, Z = 1000, 200, 1000
EX, EY, EZ = X + .5, Y + 1.2, Z + 3.2           # effect anchor, ~3 blocks in front of the camera
LOOK, SIDE = [0, 0, 1], [1, .12, 0]             # arcs face away from the camera; trails move sideways
FONT = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26)

def play(kind, ticks, anchor=(EX, EY, EZ), moving=True):
    """Emit like the game: persistent kinds and trails every tick (moving sideways), markers spaced out, others once."""
    x, y, z = anchor
    if kind in PERSISTENT or kind == 'ARROW_TRAIL':
        for t in range(ticks):
            dx = (t - ticks / 2) * .22 if moving and kind != 'BASH_AIM' else 0
            c.action('spirit_fx_burst', kind=kind, x=x + dx, y=y, z=z, look=LOOK, motion=SIDE); c.ticks(1)
    elif kind == 'ROUTE_MARKER':
        for i in range(5): c.action('spirit_fx_burst', kind=kind, x=x - 1.2 + i * .6, y=y, z=z + i * .3, look=LOOK, motion=SIDE)
        c.ticks(ticks)
    else:
        c.action('spirit_fx_burst', kind=kind, x=x, y=y, z=z, look=LOOK, motion=SIDE); c.ticks(ticks)

def label(path, text, crop, cy=.5):
    img = Image.open(path).convert('RGB'); w, h = img.size
    cw, ch = int(w * crop), int(h * crop)
    top = max(0, min(h - ch, int(h * cy - ch / 2)))
    img = img.crop(((w - cw) // 2, top, (w + cw) // 2, top + ch)).resize((480, 270), Image.LANCZOS)
    d = ImageDraw.Draw(img); d.rectangle((0, 0, 480, 36), fill=(0, 0, 0)); d.text((8, 4), text, fill=(255, 255, 255), font=FONT)
    return img

def sheet(cells, cols, out):
    rows = (len(cells) + cols - 1) // cols
    s = Image.new('RGB', (480 * cols, 270 * rows), (0, 0, 0))
    for i, cell in enumerate(cells): s.paste(cell, ((i % cols) * 480, (i // cols) * 270))
    s.save(out)

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    state = c.action('spirit_fx_state'); c.records['loaded'] = state
    assert state['loaded'] == len(KINDS) and not state['problems'], state
    c.action('reload_resources'); c.ticks(200); state = c.action('spirit_fx_state'); c.records['reloaded'] = state
    assert state['loaded'] == len(KINDS) and not state['problems'] and state['reloads'] >= 2, state
    c.command('gamemode spectator'); c.command(f'fill {X-6} {Y-1} {Z-2} {X+6} {Y-1} {Z+10} minecraft:deepslate_tiles')
    c.command('time set 13000'); c.action('camera', mode='FIRST_PERSON')
    c.command(f'tp @s {X+.5} {Y} {Z} 0 6'); c.action('settings', hideGui=True, fov=70); c.ticks(20)
    cells = []
    for kind in KINDS:
        play(kind, 2); c.screenshot(f'fx-{kind.lower()}-early')
        if kind in PERSISTENT or kind == 'ARROW_TRAIL': play(kind, 3)
        else: c.ticks(3)
        c.screenshot(f'fx-{kind.lower()}-mid')
        cells += [label(c.out / f'fx-{kind.lower()}-early.png', kind.lower() + ' · early', .7, .42),
                  label(c.out / f'fx-{kind.lower()}-mid.png', kind.lower() + ' · mid', .7, .42)]
        c.ticks(30)
    if args.sheet: sheet(cells, 4, args.sheet)
    # Ori in frame for scale: survival-like creative player in spirit form, third-person back camera.
    c.command('gamemode creative'); c.command(f'tp @s {X+.5} {Y} {Z+1} 0 20'); c.ticks(10)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('camera', mode='THIRD_PERSON_BACK'); c.ticks(10)
    me = (X + .5, Y + .9, Z + 1)
    ori = []
    for kind, ticks in [('BASH_AIM', 8), ('STOMP_IMPACT', 4), ('BLADE_HIT', 2), ('DASH', 3), ('DOUBLE_JUMP', 4), ('FORM_SWITCH', 5)]:
        anchor = (me[0], Y + .1, me[2]) if kind in ('STOMP_IMPACT', 'DOUBLE_JUMP') else me
        play(kind, ticks, anchor, moving=False); c.screenshot(f'ori-{kind.lower()}')
        ori.append(label(c.out / f'ori-{kind.lower()}.png', 'Ori + ' + kind.lower(), .75)); c.ticks(30)
    if args.ori: sheet(ori, 3, args.ori)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('settings', hideGui=False); c.command('gamemode survival'); c.command('time set 6000')
print(c.out, flush=True)
