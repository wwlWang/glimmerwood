"""Fixed-camera old/new Gloom bodies and real server-driven clips; llvmpipe + DISPLAY=:0."""
import sys
import time
import json
import hashlib
from pathlib import Path
from devtools_client import DevClient

c = DevClient(sys.argv[1])
if 'player' not in c.request():
    c.action('open_world', name='orimod_dev_feedback_r108')
c.action('focus_game'); time.sleep(1); c.action('close_screen'); c.ready()
c.action('shaders', enabled=False)
c.action('settings', fov=60, renderDistance=2, hideGui=True)
for command in ['gamerule doMobSpawning false', 'gamerule doMobLoot false', 'gamerule doDaylightCycle false',
                'weather clear', 'difficulty normal', 'gamemode spectator', 'ori form off', 'ori companion flame off',
                'forceload add 480 480 544 544', 'fill 496 180 496 528 180 528 minecraft:stone',
                'fill 496 181 496 528 196 528 minecraft:air']:
    c.command(command)
c.reset(512.5, 181, 512.5)
kinds = ['spitter', 'beetle', 'hawk', 'heart', 'matriarch']
camera = {'spitter': (1.65, 1.15, 2.3, .7), 'beetle': (1.2, .9, 1.65, .4),
          'hawk': (1.35, 1.4, 1.9, .45), 'heart': (2.6, 2.6, 3.7, 1.2),
          'matriarch': (2, 1.6, 2.8, .7)}
if '--wing-clearance' in sys.argv:
    camera['matriarch'] = (3, 2.8, 4.4, 1.3)
c.records['assets'] = {}
manifests = {}
for kind in kinds:
    manifest = json.loads(Path(f'art/source/monsters/gloom_{kind}/manifest.json').read_text())
    digest = hashlib.sha256(Path(manifest['packaged']).read_bytes()).hexdigest()
    assert digest == manifest['export_sha256']
    manifests[kind] = manifest
    c.records['assets'][kind] = {'revision': manifest['revision'], 'sha256': digest}
c.records['camera'] = camera


def frame(kind):
    x, y, z, height = camera[kind]
    e = creature(kind)
    if kind == 'hawk' and e.get('visual', {}).get('clip') == 'dive':
        x, y, z, height = .8, .8, 1.1, .25
    px, py, pz = e['position']
    c.action('review_camera', enabled=True, x=x, y=y, z=z, targetHeight=height, follow=False, entity=e['id'])
    # The detached camera stays here; keep the spectator head out of the image
    # and avoid player/entity push velocity contaminating the idle clip.
    c.command(f'tp @s {px} {py} {pz + 6}')


def creature(kind):
    values = c.action('nearby_entities')['entities']
    return next((e for e in values if e['type'] == 'entity.orimod.gloom_' + kind and e['alive']), None)


def wait_visual(kind, expected=None, min_time=0, max_timer=None):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        e = creature(kind)
        if e and e.get('visual', {}).get('active') and (expected is None or e['visual']['clip'] == expected and e['visual']['blend'] >= .99 and e['visual']['time'] >= min_time and (max_timer is None or e['visual']['timer'] <= max_timer)):
            return e
        time.sleep(.03)
    raise AssertionError((kind, expected, e))


def prepare(kind):
    c.command('tick unfreeze')
    c.command('tick rate 20')
    c.reset(512.5, 181, 512.5)
    # Killing a boss may spawn adds; clear bosses before their minions.
    for name in ['heart', 'matriarch', 'hawk', 'beetle', 'spitter']:
        c.command(f'kill @e[type=orimod:gloom_{name},distance=..30]')
    c.ticks(45)
    c.command('kill @e[type=minecraft:item,distance=..30]')
    c.action('creature_visuals', legacy=False)
    c.command(f'summon orimod:gloom_{kind} 512.5 181 512.5 {{NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f],HomeX:512,HomeY:181,HomeZ:512,Tags:["ori_visual_probe"]}}')
    c.ticks(3)
    frame(kind)
    c.command(f'data merge entity @e[type=orimod:gloom_{kind},limit=1,sort=nearest] {{Motion:[0d,0d,0d]}}')
    c.ticks(15)
    return wait_visual(kind)


def capture(name, kind, expected=None):
    e = wait_visual(kind, expected)
    c.records['steps'][name] = {'entity': e, 'requested_time': min_time, 'clip_duration': spec['seconds'],
                               'screenshot': c.screenshot(name)['state']}
    c.save()


def key_pose(daylight, kind, action, clip):
    spec = manifests[kind]['clips'][clip]
    min_time = .7 * spec['seconds'] if clip in ['telegraph', 'ring', 'slam', 'feathers', 'gust'] else .2
    total = {('spitter', 'telegraph'): 14, ('beetle', 'telegraph'): 15, ('hawk', 'telegraph'): 20,
             ('matriarch', 'telegraph'): 30, ('matriarch', 'feathers'): 20, ('matriarch', 'gust'): 25,
             ('heart', 'ring'): 30, ('heart', 'slam'): 25}.get((kind, clip))
    e = prepare(kind)
    # Keep the attack target outside the model. Flight paths/headings are real server AI.
    c.command('tp @s 512.5 181 518.5 180 0')
    c.command('tick rate 5')
    c.action('creature_action', id=e['id'], action=action)
    if kind in ['hawk', 'heart', 'matriarch']:
        c.command(f'data merge entity @e[type=orimod:gloom_{kind},limit=1,sort=nearest] {{NoAI:0b}}')
    e = wait_visual(kind, clip, min_time, int(total * .3) if total else None)
    c.command('tick freeze')
    # A moving creature may have left its spawn point; reframe only after reaching
    # the requested pose. No entity/animation state is injected for the screenshot.
    e = creature(kind)
    frame(kind)
    time.sleep(.4)
    name = f'{daylight}-{kind}-{clip}'
    c.records['steps'][name] = {'entity': creature(kind), 'frozen': True,
                               'server_telegraph_ticks': total,
                               'requested_time': min_time, 'clip_duration': spec['seconds'],
                               'screenshot': c.screenshot(name)['state']}
    c.save()
    c.command('tick unfreeze')


try:
    for daylight in (['day'] if '--keyposes' in sys.argv else ['day', 'night']):
        c.command('time set ' + ('noon' if daylight == 'day' else 'midnight'))
        if '--keyposes' not in sys.argv:
            for kind in kinds:
                prepare(kind)
                c.action('creature_visuals', legacy=True); c.ticks(8)
                c.records['steps'][daylight + '-' + kind + '-legacy'] = {'entity': creature(kind)}
                c.screenshot(daylight + '-' + kind + '-legacy'); c.save()
                c.action('creature_visuals', legacy=False); c.ticks(8)
                capture(daylight + '-' + kind + '-idle', kind, 'idle')
            # Spitter: actual server charge and release, not an animation-preview override.
            e = prepare('spitter'); c.command('tick rate 5')
            c.action('creature_action', id=e['id'], action='telegraph'); c.ticks(3)
            capture(daylight + '-spitter-telegraph-early', 'spitter', 'telegraph')
            capture(daylight + '-spitter-attack', 'spitter', 'attack')
            c.ticks(12)
            c.command('damage @e[type=orimod:gloom_spitter,limit=1,sort=nearest] 1 minecraft:generic')
            capture(daylight + '-spitter-hurt', 'spitter', 'hurt')
            c.ticks(12)
            c.command('data merge entity @e[type=orimod:gloom_spitter,limit=1,sort=nearest] {Motion:[0.12d,0d,0d]}')
            capture(daylight + '-spitter-move', 'spitter', 'move')
            c.command('kill @e[type=orimod:gloom_spitter,limit=1,sort=nearest]')
            # nearby_entities also retains dying entities, whose alive flag is already false.
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                values = c.action('nearby_entities')['entities']
                dead = next((e for e in values if e['type'] == 'entity.orimod.gloom_spitter' and e.get('visual', {}).get('clip') == 'death' and e['visual']['time'] >= .35), None)
                if dead:
                    c.records['steps'][daylight + '-spitter-death'] = {'entity': dead}
                    c.screenshot(daylight + '-spitter-death'); c.save(); break
                time.sleep(.02)
            else:
                raise AssertionError('Death clip not observed')
        for kind, action, clip in [('spitter', 'telegraph', 'telegraph'), ('beetle', 'telegraph', 'telegraph'), ('beetle', 'flip', 'flipped'),
                                   ('hawk', 'telegraph', 'telegraph'), ('hawk', 'telegraph', 'dive'),
                                   ('matriarch', 'telegraph', 'telegraph'), ('matriarch', 'gust', 'gust'), ('matriarch', 'feathers', 'feathers'),
                                   ('heart', 'ring', 'ring'), ('heart', 'slam', 'slam')]:
            if '--wing-clearance' in sys.argv and (kind != 'matriarch' or clip not in ['telegraph', 'gust']):
                continue
            key_pose(daylight, kind, action, clip)
    c.records['status'] = 'captured'; c.save()
finally:
    c.action('release_keys'); c.command('tick unfreeze'); c.command('tick rate 20')
    c.action('creature_visuals', legacy=False)
    c.action('review_camera', enabled=False)
    c.action('settings', hideGui=False)
print(c.out)
