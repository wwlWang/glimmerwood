"""C regression: fixed dark-room hands/body, ordinary hands, and shader toggle round trip.

Run only in the handed-over development client (llvmpipe + DISPLAY=:0).
The preserved HEAD pack must be installed as 'Ori Hand Before.zip'; it is never
copied over the working tree or the shipped Spirit Glow pack.
"""
import argparse
from devtools_client import DevClient

p = argparse.ArgumentParser()
p.add_argument('build_id')
p.add_argument('--packs', default='none-before,Ori Hand Before.zip,Spirit Glow.zip,none-after')
args = p.parse_args()
c = DevClient(args.build_id)

try:
    if 'player' not in c.request():
        c.action('open_world', name='orimod_dev_feedback_r108')
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.action('resize', width=1920, height=1015)
    c.action('settings', fov=60, renderDistance=2, hideGui=False, fpsLimit=60)
    c.action('review_camera', enabled=False)
    c.action('camera', mode='FIRST_PERSON')
    for cmd in ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
                'time set midnight', 'weather clear', 'difficulty peaceful', 'gamemode survival',
                'ori awaken', 'ori form on', 'ori companion flame off', 'clear @s',
                'forceload add 288 288 320 320',
                'fill 298 199 298 310 207 310 minecraft:gray_concrete hollow']:
        c.command(cmd)
    c.reset(304.5, 200, 304.5, 0, 15)
    c.ticks(40)
    for pack in args.packs.split(','):
        if pack.startswith('none-'):
            c.action('shaders', enabled=False)
        else:
            c.action('iris_pack', name=pack)
        c.ticks(40)
        label = {'Ori Hand Before.zip': 'before', 'Spirit Glow.zip': 'after'}.get(pack, pack)
        diag = c.action('mod_config')['spiritGlow']
        if not pack.startswith('none-'):
            assert c.request()['shaders']['enabled'], c.request()['shaders']
            assert diag.get('irisError') is None, diag
        c.records['steps'][label] = {'state': c.spirit(), 'shader': c.request()['shaders'], 'diagnostics': diag}
        for form in ['spirit', 'human']:
            c.command('ori form ' + ('on' if form == 'spirit' else 'off'))
            items = ['empty', 'iron_sword', 'spirit_blade', 'spirit_spear', 'spirit_hammer', 'spirit_bow'] if form == 'spirit' else ['empty', 'iron_sword']
            for item in items:
                namespace = 'orimod' if item.startswith('spirit_') else 'minecraft'
                c.command('clear @s')
                if item != 'empty':
                    c.command(f'item replace entity @s weapon.mainhand with {namespace}:{item}')
                c.reset(304.5, 200, 304.5, 0, 15)
                c.ticks(30)
                c.screenshot(f'{label}-{form}-{item}')
            if form == 'spirit':
                c.command('item replace entity @s weapon.mainhand with orimod:spirit_blade')
                c.reset(304.5, 200, 304.5, 0, 0)
                c.ticks(30)
                c.action('review_camera', enabled=True, x=-1.7, y=1.1, z=2.0, targetHeight=.65, follow=False)
                c.ticks(12)
                c.screenshot(f'{label}-third-blade')
                c.action('review_camera', enabled=False)
                c.action('camera', mode='FIRST_PERSON')
        c.save()
    c.records['status'] = 'captured'
finally:
    c.action('review_camera', enabled=False)
    c.action('release_keys')
    c.command('ori form on')
    c.save()
print(c.out)
