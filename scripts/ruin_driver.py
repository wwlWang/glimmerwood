"""Development fixture setup plus real key/GUI driving for authored ruin routes.

Commands place fixtures and position the player before a run. No commands, teleportation,
counter changes or direct challenge service calls occur while a route is being completed.
"""
import json
import math
from devtools_client import ROOT


class RuinDriver:
    def __init__(self, client, kind, origin, rotation="NONE"):
        self.c, self.kind, self.origin = client, kind, origin
        self.rotation = rotation
        self.angle = {"NONE": 0, "CLOCKWISE_90": 90, "CLOCKWISE_180": 180, "COUNTERCLOCKWISE_90": -90}[rotation]
        self.layout = json.loads((ROOT / 'src/main/resources/orimod/ruin_layout' / (kind + '.json')).read_text())
        self.shrine = self.absolute(self.layout['shrine'], block=True)
        self.safe = self.absolute(self.layout['safe'])
        self.key = 'minecraft:overworld/' + ','.join(str(int(n)) for n in self.shrine)

    def absolute(self, point, block=False):
        x, y, z = point
        edge = 0 if block else 1
        x, z = {"NONE": (x, z), "CLOCKWISE_90": (edge - z, x),
                "CLOCKWISE_180": (edge - x, edge - z), "COUNTERCLOCKWISE_90": (z, edge - x)}[self.rotation]
        return [a + b for a, b in zip(self.origin, (x, y, z))]

    def look(self, yaw=0, pitch=0):
        self.c.action('look', yaw=yaw + self.angle, pitch=pitch)

    def walk_local(self, x, z, limit=50):
        point = self.absolute((x, 0, z))
        self.walk(point[0], point[2], limit)


    def prepare(self):
        c = self.c
        c.action('focus_game'); c.action('close_screen'); c.ready()
        c.action('progress_action', action='exit_challenge', target=''); c.ticks(3)
        c.command('gamemode creative')
        entry = self.absolute(self.layout['entry'])
        c.command('tp @s ' + ' '.join(map(str, entry)) + f' {self.angle} 0'); c.ticks(35)
        c.command('place template orimod:ruins/' + self.kind + ' ' + ' '.join(map(str, self.origin)) + ' ' + ('180' if self.rotation == 'CLOCKWISE_180' else self.rotation.lower()))
        x, y, z = map(int, self.shrine)
        assert c.action('inspect_block', x=x, y=y, z=z)['block'] == 'orimod:' + self.kind + '_shrine'
        c.command('tp @s ' + ' '.join(map(str, self.safe)) + f' {self.angle} 0')
        if not c.spirit()['server']['awakened']:
            c.command('item replace entity @s hotbar.0 with orimod:spirit_core')
            c.action('select_slot', slot=0); c.action('use_item'); c.ticks(5); c.action('close_screen')
        if not c.spirit()['server']['form']:
            c.action('toggle_form'); c.ticks(3)
        c.action('grant_dash'); c.action('grant_bash'); c.action('grant_air_movement')
        c.command('item replace entity @s hotbar.0 with orimod:spirit_blade')
        c.action('select_slot', slot=0); c.command('gamemode survival'); c.command('difficulty peaceful'); c.ticks(5)

    def open(self):
        self.c.action('use_block', **dict(zip(('x', 'y', 'z'), map(int, self.shrine))))
        self.c.ticks(3)
        gui = self.c.action('gui_state')
        assert gui['screen'] == 'RuinScreen', gui
        return gui

    def start(self):
        c = self.c
        c.action('focus_game'); c.action('close_screen'); c.ready()
        assert not c.action('ruin_state')['active'], 'Previous challenge still active'
        c.action('release_keys')
        c.command('tp @s ' + ' '.join(map(str, self.safe)) + f' {self.angle} 0'); c.ticks(6)
        before = c.action('progress_state')['server']['progress']
        gui = self.open()
        button = gui['widgets'][0]
        assert button['active'], gui
        after = c.action('progress_state')['server']['progress']
        expected = 0 if before['rewards'].get('discover/' + self.key, 0) else 15
        assert after['light'] - before['light'] == expected, (before, after)
        c.action('gui_click', x=button['x'] + 4, y=button['y'] + 4); c.ticks(4)
        assert c.action('ruin_state')['active'], c.action('ruin_state')
        return after

    def script(self, steps, ticks):
        self.c.action('focus_game'); self.c.ready()
        steps = json.loads(json.dumps(steps))
        for step in steps:
            if step.get('action', {}).get('op') == 'look':
                step['action']['yaw'] += self.angle
        self.c.action('input_script', steps=steps); self.c.ticks(ticks)
        assert self.c.request()['inputScriptError'] == '', self.c.request()['inputScriptError']
        assert self.c.request()['windowActive'], 'Client lost focus during the route input script'

    def walk(self, x, z, limit=50):
        c = self.c
        point = c.spirit()['client']['position']
        c.action('look', yaw=math.degrees(math.atan2(point[0] - x, z - point[2])), pitch=0)
        c.action('key', name='key.forward', ticks=min(200, limit * 2))
        for _ in range(limit):
            # Steer against the position the player sees. The server copy can trail
            # by several updates; using it made the driver oscillate past ledges.
            point = c.spirit()['client']['position']
            dx, dz = x - point[0], z - point[2]
            distance = math.hypot(dx, dz)
            if distance < .16:
                break
            if distance < .9:
                c.action('key', name='key.sneak', ticks=min(200, limit * 2))
            c.action('look', yaw=math.degrees(math.atan2(-dx, dz)), pitch=0); c.ticks(1)
        else:
            raise AssertionError(('Walk target not reached', x, z, c.spirit()))
        c.action('release_keys'); c.ticks(6)

    def checkpoint(self, expected):
        state = self.c.action('ruin_state')
        assert state.get('active') and state.get('checkpoint') == expected, state

    def forest(self):
        c = self.c
        self.look(90)
        self.script([
            key(0, 'forward', True), key(18, 'jump', True), key(19, 'jump', False),
            key(24, 'jump', True), key(25, 'jump', False), look(25, 10),
            key(31, 'jump', True), key(32, 'jump', False), look(41, 0), key(49, 'forward', False)], 65)
        self.walk_local(8.5, 9); self.checkpoint(1)
        self.walk_local(8.5, 10.8); self.look()
        self.script([key(0, 'forward', True), key(0, 'sprint', True), key(2, 'jump', True), key(3, 'jump', False),
                     key(8, 'jump', True), key(9, 'jump', False), key(22, 'forward', False), key(22, 'sprint', False)], 34)
        self.checkpoint(2)
        self.walk_local(8.5, 17.1); self.look()
        self.script([key(0, 'forward', True), key(1, 'jump', True), key(2, 'jump', False),
                     key(4, 'orimod.dash', True), key(5, 'orimod.dash', False), key(13, 'forward', False)], 30)

    def creek(self):
        c = self.c
        self.look(0, -30); c.key('forward', 4); c.ticks(8)
        self.script([key(0, 'orimod.bash', True), look(3, 0, -50), key(3, 'forward', True),
                     key(4, 'orimod.bash', False), key(25, 'forward', False)], 35)
        self.checkpoint(1)
        self.look(); c.key('forward', 7); c.ticks(11)
        self.script([key(0, 'forward', True), key(0, 'sprint', True), key(2, 'jump', True),
                     key(36, 'forward', False), key(36, 'sprint', False), key(40, 'jump', False)], 47)

    def cave_to_target(self):
        c = self.c
        self.look(0, -80); c.key('forward', 15); c.ticks(19)
        self.script([key(0, 'jump', True), key(1, 'jump', False), key(6, 'orimod.bash', True),
                     key(7, 'orimod.bash', False), key(8, 'forward', True), look(8, 0), key(30, 'forward', False)], 40)
        self.checkpoint(1)
        self.walk_local(11.5, 25.5); self.look()
        self.script([key(0, 'forward', True), key(1, 'jump', True), key(2, 'jump', False),
                     key(16, 'forward', False), key(16, 'sneak', True), key(17, 'orimod.dash', True),
                     key(18, 'orimod.dash', False), key(20, 'sneak', False)], 34)
        self.checkpoint(2)
        targets = [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.orimod.practice_target']
        assert len(targets) == 1 and targets[0]['health'] == 12, targets
        return targets[0]

    def cave(self):
        c = self.c
        self.cave_to_target()
        self.look(); c.key('forward', 11); c.ticks(15)
        self.script([key(0, 'attack', True), key(1, 'attack', False), key(7, 'attack', True), key(8, 'attack', False),
                     key(20, 'attack', True), key(21, 'attack', False)], 44)

    def run(self):
        {'forest_spring': self.forest, 'creek_ruins': self.creek, 'cave_altar': self.cave}[self.kind]()
        state = self.c.action('ruin_state')
        # Once safely landed, follow the visible finish marker rather than relying on
        # identical residual drift after a dash or glide in every newly loaded area.
        if self.kind != 'cave_altar' and state.get('active') and state.get('checkpoint') == len(self.layout['checkpoints']) - 1:
            finish = self.absolute(self.layout['checkpoints'][-1]['position'])
            player = self.c.spirit()['server']
            if player['movement']['grounded'] and abs(player['position'][1] - finish[1]) < .35:
                self.walk(finish[0], finish[2]); self.c.ticks(3)


def key(at, name, down):
    return {'at': at, 'key': 'key.' + name, 'down': down}


def look(at, yaw, pitch=0):
    return {'at': at, 'action': {'op': 'look', 'yaw': yaw, 'pitch': pitch}}
