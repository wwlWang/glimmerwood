"""R112 runtime evidence. Invoked by capture-ori-closeup.py --animation-pass --only all.

Commands build fixtures and set up inputs; clips are always chosen by the real player state, never forced.
Each take contains a video, a 4x6 contact sheet and per-frame runtime diagnostics (pose.json).
"""
import json
import math
import shutil
import subprocess
import time


def run(c, only):
    x, y, z = 5500, 130, 5500
    scenarios = {}

    def cmd(text):
        return c.command(text)

    def put(item='air'):
        cmd('clear @s')
        if item != 'air': cmd('item replace entity @s hotbar.0 with ' + item)
        c.action('select_slot', slot=0)

    def stage(dx=0, dy=0, dz=0, yaw=0, pitch=0, item='air', distance=1.8):
        c.action('release_keys'); c.action('review_camera', enabled=False)
        put(item); c.reset(x + dx + .5, y + dy, z + dz + .5, yaw, pitch); c.ticks(8)
        c.action('review_camera', enabled=True, x=-distance * math.cos(math.radians(yaw)), y=.58, z=-distance * math.sin(math.radians(yaw)), targetHeight=.58, follow=True)

    def sample(name):
        c.records.setdefault('observations', {})[name] = c.spirit()

    def key(name, ticks):
        c.key(name, ticks)

    def walk(sprint=False, crouch=False):
        stage(dx=-15, yaw=-90)
        def act():
            key('forward', 45)
            if sprint: key('sprint', 45)
            if crouch: key('sneak', 45)
            c.ticks(16); sample('sprint' if sprint else 'crouch_walk' if crouch else 'run')
        return act

    def jump(air_blade=False):
        stage(item='orimod:spirit_blade' if air_blade else 'air')
        def act():
            key('jump', 9); c.ticks(5); sample('jump-rise')
            if air_blade: key('attack', 1)
            c.ticks(6); sample('jump-apex'); c.ticks(7); sample('jump-fall')
        return act

    def wall(up):
        c.action('progress_action', action='equip', target='sticky')
        stage(dx=-10, dz=3.2)
        def act():
            cmd(f'tp @s {x-9.5} {y+4} {z+3.7} {0 if up else -90} 0')
            key('forward' if up else 'right', 50); c.ticks(9)
            c.ticks(6); sample('wall_climb' if up else 'wall_hold')
        return act

    def ladder():
        stage(dx=9, dy=1, dz=3.2)
        def act():
            key('forward', 40); c.ticks(12); sample('ladder-up')
            c.action('release_keys'); key('sneak', 30); c.ticks(8); sample('ladder-hold')
        return act

    def crawl():
        # Ceiling underside .8125 above the floor: too low even for Ori's 1.0-block crouch.
        stage(dx=-9, dz=-9)
        c.action('review_camera', enabled=True, x=-1.8, y=.35, z=0, targetHeight=.35, follow=True)
        def act():
            cmd(f'tp @s {x - 9.5} {y} {z - 7.5} 0 0')
            key('forward', 12); c.ticks(12); c.action('release_keys'); sample('crawl')
        return act

    def water(kind):
        stage(dx=0, dy=2.1 if kind == 'surface' else -.7, dz=12, yaw=-90)
        def act():
            if kind != 'idle': key('forward', 50)
            if kind == 'surface': key('jump', 50)
            if kind == 'dash':
                c.ticks(4); key('orimod.dash', 1)
                c.ticks(2); sample('water-dash')
            else: c.ticks(10); sample('water-' + kind)
        return act

    def air(kind):
        stage(dx=4)
        def act():
            cmd(f'tp @s {x+4.5} {y+24} {z+.5} 0 0')
            c.ticks(2)
            if kind == 'glide': key('jump', 55)
            elif kind == 'stomp':
                key('sneak', 8); key('orimod.dash', 2)
            c.ticks(8); sample(kind)
        return act

    def use(item, ticks=40, offhand=False):
        stage(item=item)
        if offhand:
            put(); cmd('item replace entity @s weapon.offhand with ' + item)
        if item == 'minecraft:shield':
            c.action('review_camera', enabled=True, x=1.25 if offhand else -1.25, y=.65, z=1.25, targetHeight=.58, follow=True)
        if 'bow' in item: cmd('give @s minecraft:arrow 64')
        def act():
            key('use', ticks); c.ticks(min(16, ticks)); sample(item.split(':')[-1] + '-use')
            c.ticks(max(0, ticks - 16) + 2); sample(item.split(':')[-1] + '-release')
        return act

    def spell(kind):
        stage()
        c.action('set_energy', amount=100)
        def act():
            key('orimod.' + kind, 35 if kind in ('charge_flame', 'flash') else 1)
            c.ticks(14); sample(kind)
            c.ticks(24); sample(kind + '-release')
        return act

    def launch():
        stage(pitch=-75)
        def act():
            key('sneak', 35); c.ticks(24); sample('launch-charge')
            key('jump', 2); c.ticks(4); sample('launch')
        return act

    def place():
        stage(pitch=40, item='minecraft:stone')
        return lambda: (key('use', 1), c.ticks(3), sample('place'))

    def combo():
        stage(item='orimod:spirit_blade')
        def act():
            for i in range(3): key('attack', 1); c.ticks(7); sample('blade-' + str(i + 1))
        return act

    def mine():
        stage(dx=15, dz=2, pitch=18, item='minecraft:diamond_pickaxe')
        cmd(f'fill {x+15} {y} {z+4} {x+16} {y+2} {z+5} minecraft:obsidian')
        def act():
            key('attack', 50); c.ticks(12); sample('mine')
        return act

    def hurt(direction):
        stage(); cmd('effect clear @s'); cmd('difficulty normal')
        cmd('kill @e[tag=r112_source]')
        dx, dz = {'front': (0, 3), 'back': (0, -3), 'side': (-3, 0)}[direction]
        cmd(f'summon minecraft:armor_stand {x+.5+dx} {y} {z+.5+dz} {{Tags:["r112_source"],Marker:1b,Invisible:1b,NoGravity:1b}}')
        def act():
            cmd('damage @s 2 minecraft:mob_attack by @e[tag=r112_source,limit=1]')
            c.ticks(2); sample('hurt-' + direction)
        return act

    scenarios.update(idle=lambda: (stage(), key('forward', 2), c.ticks(12), lambda: c.ticks(20))[3], run=lambda: walk(), sprint=lambda: walk(True),
                     crouch_walk=lambda: walk(crouch=True), crawl=crawl, jump=jump, wall_hold=lambda: wall(False),
                     wall_climb=lambda: wall(True), ladder=ladder, swim_surface=lambda: water('surface'),
                     swim=lambda: water('underwater'), swim_idle=lambda: water('idle'), swim_dash=lambda: water('dash'),
                     glide=lambda: air('glide'), stomp=lambda: air('stomp'), combo=combo, blade_air=lambda: jump(True),
                     hammer=lambda: use('orimod:spirit_hammer', 26), spear=lambda: use('orimod:spirit_spear', 1),
                     bow=lambda: use('orimod:spirit_bow', 26), shield=lambda: use('minecraft:shield'),
                     shield_left=lambda: use('minecraft:shield', offhand=True),
                     cast=lambda: spell('charge_flame'), light_burst=lambda: spell('light_burst'), flash=lambda: spell('flash'),
                     place=place, launch=launch,
                     eat=lambda: use('minecraft:golden_apple'), drink=lambda: use('minecraft:potion'), mine=mine,
                     hurt_front=lambda: hurt('front'), hurt_back=lambda: hurt('back'), hurt_side=lambda: hurt('side'))
    scenarios['throw'] = lambda: use('minecraft:snowball', 1)
    try:
        c.action('focus_game'); c.action('close_screen'); c.ready()
        c.action('resize', width=1280, height=800); c.ticks(6)
        c.action('shaders', enabled=False); c.ticks(12)
        c.records['review'] = c.action('animation_review', enabled=True)
        for command in ['difficulty peaceful', 'time set 6000', 'weather clear 99999', 'gamerule doDaylightCycle false',
                        'gamerule doMobSpawning false', 'gamerule sendCommandFeedback false', 'gamemode survival',
                        'ori awaken', 'ori form on', 'ori unlock all', 'ori companion flame off', 'effect clear @s']:
            cmd(command)
        cmd(f'forceload add {x-32} {z-32} {x+32} {z+32}')
        cmd(f'tp @s {x} {y+20} {z}'); c.ticks(40)
        for dy in range(-3, 18, 3): cmd(f'fill {x-24} {y+dy} {z-18} {x+24} {y+dy+2} {z+24} minecraft:air')
        cmd(f'fill {x-24} {y-1} {z-18} {x+24} {y-1} {z+24} minecraft:moss_block')
        cmd(f'fill {x-14} {y} {z+4} {x-6} {y+12} {z+4} minecraft:stone')
        cmd(f'fill {x+9} {y} {z+4} {x+9} {y+12} {z+4} minecraft:stone')
        cmd(f'fill {x+9} {y} {z+3} {x+9} {y+12} {z+3} minecraft:ladder[facing=north]')
        cmd(f'fill {x-12} {y} {z-8} {x-7} {y} {z-2} minecraft:oak_trapdoor[half=top,open=false]')
        cmd(f'fill {x-6} {y-3} {z+8} {x+13} {y+3} {z+20} minecraft:glass hollow')
        cmd(f'fill {x-5} {y-2} {z+9} {x+12} {y+2} {z+19} minecraft:water')
        cmd(f'fill {x-5} {y-3} {z+9} {x+12} {y-3} {z+19} minecraft:stone')
        cmd(f'fill {x-5} {y+3} {z+9} {x+12} {y+3} {z+19} minecraft:air')
        c.action('settings', hideGui=True)
        for name, prepare in scenarios.items():
            if 'all' not in only and name not in only: continue
            action = prepare(); c.ticks(4)
            folder = c.out / name; folder.mkdir()
            c.action('record_pose')
            video_name = c.build_id + '-' + name
            c.action('record_video', name=video_name, fps=12, seconds=3.6)
            action()
            deadline = time.monotonic() + 40
            while True:
                try: result = c.action('save_video'); break
                except RuntimeError:
                    if time.monotonic() >= deadline: raise
                    time.sleep(.3)
            pose = c.action('save_pose', name=video_name + '-pose')
            shutil.copy2(pose['path'], folder / 'pose.json')
            source = c.out.parent.parent / 'run-devtools' / 'devtools' / video_name / 'capture.mp4'
            deadline = time.monotonic() + 20
            while not source.exists() and time.monotonic() < deadline: time.sleep(.2)
            shutil.copy2(source, folder / 'capture.mp4')
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(source), '-vf',
                            'fps=8,crop=iw*0.4:ih*0.8:iw*0.3:ih*0.1,scale=400:-2,tile=4x6', '-frames:v', '1', str(folder / 'sheet.png')], check=True)
            c.records['steps'][name] = {'video': str(folder / 'capture.mp4'), 'frames': pose['samples']}
            c.action('release_keys'); cmd('effect give @s minecraft:instant_health 1 5 true'); c.save()
        c.records['review_end'] = c.action('animation_review')
        c.records['status'] = 'ok'
    except Exception as e:
        c.records['status'] = 'error'; c.records['error'] = repr(e)
        raise
    finally:
        for op, args in [('release_keys', {}), ('animation_review', {'enabled': False}), ('review_camera', {'enabled': False}),
                         ('settings', {'hideGui': False}), ('camera', {'mode': 'FIRST_PERSON'})]:
            try: c.action(op, **args)
            except Exception: pass
        try:
            cmd('effect clear @s'); cmd('kill @e[tag=r112_source]'); cmd('difficulty peaceful'); cmd('forceload remove all')
        except Exception: pass
        c.save(); print(c.records['status'], c.out)
