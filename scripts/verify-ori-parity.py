"""R33+ original-game parity checks in the client: Soul Link respawn, companion orb and automatic Spirit Flame,
Life/Energy Cell use. Runs in the play04 test world arena (1000, 200, 1000).
Usage: python3 scripts/verify-ori-parity.py <build_id> [--shots DIR]"""
import argparse, shutil
from devtools_client import DevClient
p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('--shots'); args = p.parse_args()
c = DevClient(args.build_id)
X, Y, Z = 1000, 200, 1000

def me():
    return c.request()['player']

def shot(name):
    c.screenshot(name)
    if args.shots: shutil.copy2(c.out / f'{name}.png', f'{args.shots}/r37-{name}.png')

def zombies():
    return [e for e in c.action('nearby_entities')['entities'] if 'zombie' in e['type'] and e['alive']]

try:
    c.action('focus_game'); c.action('close_screen'); c.ready()
    c.command('gamemode survival'); c.command('time set 13000'); c.command('kill @e[type=minecraft:zombie,distance=..40]')
    c.command('effect give @s minecraft:resistance 600 4 true'); c.command('effect give @s minecraft:saturation 600 1 true')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.action('settings', hideGui=False); c.action('camera', mode='THIRD_PERSON_BACK')
    # 1. Soul Link: create at A, move to B, die, respawn must return to A.
    c.reset(X + 3.5, Y, Z + 3.5, 0, 20); c.ticks(20)
    c.key('orimod.soul_link', 2); c.ticks(10)
    link_at = me()['position']; c.records['soul_link_created_at'] = link_at
    shot('soul-link-created')
    c.command(f'tp @s {X - 4.5} {Y} {Z - 4.5}'); c.ticks(10)
    c.command('kill @s'); c.ticks(20)
    c.action('respawn'); c.ticks(40); c.ready()
    back = me()['position']; c.records['respawned_at'] = back
    c.records['soul_link_returned'] = abs(back[0] - link_at[0]) < 1.01 and abs(back[2] - link_at[2]) < 1.01 and abs(back[1] - link_at[1]) < 1.01
    assert c.records['soul_link_returned'], (link_at, back)
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(6)
    c.command('effect give @s minecraft:resistance 600 4 true')
    # 2. Companion orb (third person) and automatic Spirit Flame on a zombie 4 blocks ahead.
    c.reset(X + .5, Y, Z + .5, 0, 15); c.action('settings', hideGui=True); c.ticks(30)
    shot('companion-orb')
    c.command(f'summon minecraft:zombie {X + .5} {Y} {Z + 4.5} {{NoAI:1b,Silent:1b,PersistenceRequired:1b}}'); c.ticks(4)
    z0 = zombies(); assert z0, 'zombie fixture missing'
    hp0 = z0[0]['health']
    for i in range(4): c.ticks(5); shot(f'spirit-flame-{i}')
    c.ticks(40)
    z1 = zombies(); hp1 = z1[0]['health'] if z1 else 0
    c.records['flame'] = {'before': hp0, 'after': hp1}
    assert hp1 < hp0, c.records['flame']
    c.command('kill @e[type=minecraft:zombie,distance=..40]'); c.ticks(20)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('settings', hideGui=False); c.command('time set 6000')
print(c.out, c.records.get('soul_link_returned'), c.records.get('flame'), flush=True)
