"""Deterministic first-person blade combo / handedness / sprint / aspect check over the devtools bridge.

Run against an already open devtools world (Ori form, creative is fine); no desktop input is synthesised:
    python3 scripts/verify-fp-combo.py <unique-evidence-id> [--shots]
Each case records per-frame `firstPerson.hands` diagnostics (SpiritFirstPersonHand) while an input_script clicks
attack at ticks 0 / 4 / 12, which chains all three stages (buffer opens one tick into a stage, chains at tick six).
Reports, per case: stages seen, the largest per-frame pose change normalised to one tick, and the aspect inset.
--shots also saves a screenshot at each stage's cut (visual review only; shader brightness on llvmpipe is not valid).
"""
import json
import sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])
SHOTS = '--shots' in sys.argv
c.action('focus_game'); c.action('close_screen'); c.ready()
c.action('release_keys'); c.action('review_camera', enabled=False)
for cmd in ['difficulty peaceful', 'gamerule doDaylightCycle false', 'time set 6000', 'weather clear',
            'ori awaken', 'ori form on', 'clear @s', 'item replace entity @s hotbar.0 with orimod:spirit_blade']:
    c.command(cmd)
c.action('select_slot', slot=0)

COMBO = [{'at': t, 'key': 'key.attack', 'down': d} for t in (0, 4, 12) for d in (True,)] + \
        [{'at': t + 1, 'key': 'key.attack', 'down': False} for t in (0, 4, 12)]
COMBO.sort(key=lambda s: s['at'])

def pose_distance(a, b):
    return sum(abs(x - y) for x, y in zip(a[:3], b[:3])) + 100 * sum(abs(x - y) for x, y in zip(a[3:], b[3:]))

def case(name, arm='RIGHT', size=(1280, 720), offhand=False, sprint=False):
    c.action('settings', mainArm=arm)
    c.action('resize', width=size[0], height=size[1])
    c.command('item replace entity @s weapon.offhand with ' + ('orimod:spirit_blade' if offhand else 'minecraft:air'))
    c.reset(.5, 100, .5)
    c.command('fill -3 99 -3 3 99 40 minecraft:stone')
    c.ticks(10)
    c.action('record_pose')
    steps = list(COMBO)
    if sprint:
        steps = [{'at': 0, 'key': 'key.forward', 'down': True}, {'at': 0, 'key': 'key.sprint', 'down': True}] + \
                [dict(s, at=s['at'] + 8) for s in COMBO] + \
                [{'at': 40, 'key': 'key.forward', 'down': False}, {'at': 40, 'key': 'key.sprint', 'down': False}]
    c.action('input_script', steps=steps)
    if SHOTS:
        for stage_tick in (3, 10, 18):
            c.ticks(stage_tick if stage_tick == 3 else 7 if stage_tick == 10 else 8)
            c.screenshot(f'{name}-t{stage_tick}')
    c.ticks(45)
    result = c.action('save_pose', name=c.build_id + '-' + name)
    samples = json.load(open(result['path']))['samples']
    main = 'left' if arm == 'LEFT' else 'right'
    stages, worst, insets, actions = set(), 0.0, set(), set()
    prev = None
    for s in samples:
        h = s['firstPerson']['hands'][main]
        stages.add(h['stage']); insets.add(round(h['inset'], 3)); actions.add(h['action'])
        if prev is not None:
            dt = s['gameTime'] - prev[1]
            if dt > 1e-3:
                worst = max(worst, pose_distance(h['pose'], prev[0]) / max(dt, .05))
        prev = (h['pose'], s['gameTime'])
    off = {s['firstPerson']['hands']['right' if main == 'left' else 'left']['action'] for s in samples}
    c.records['steps'][name] = {'samples': len(samples), 'stages': sorted(stages), 'actions': sorted(actions),
                                'offhandActions': sorted(off), 'insets': sorted(insets),
                                'maxPoseChangePerTick': round(worst, 1)}
    c.save()
    print(name, c.records['steps'][name], flush=True)

try:
    case('right-16x9')
    case('left-16x9', arm='LEFT')
    case('right-offhand-16x9', offhand=True)
    case('left-offhand-16x9', arm='LEFT', offhand=True)
    case('right-sprint-16x9', sprint=True)
    case('left-sprint-16x9', arm='LEFT', sprint=True)
    case('right-16x10', size=(1280, 800))
    case('right-4x3', size=(1024, 768))
    case('left-4x3', arm='LEFT', size=(1024, 768))
finally:
    c.action('release_keys'); c.action('settings', mainArm='RIGHT'); c.action('resize', width=1280, height=720)
    c.records['status'] = 'done'; c.save()
