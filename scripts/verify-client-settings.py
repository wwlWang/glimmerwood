"""R15 real-client check of the settings screen and the behaviour each setting drives.

Run in a development client at the title screen or in a dedicated orimod_dev_ world. It edits
run-devtools/config/orimod-client.toml through the real NeoForge settings screen and leaves the
last values in place so a restart check can follow (`--expect` mode). GUI coordinates assume the
1920x1080 window at GUI scale 4 (480x270 GUI units) used by the development instance.
"""
import json
import sys
import time
from devtools_client import DevClient, ROOT

CONFIG = ROOT / 'run-devtools/config/orimod-client.toml'
# GUI units: section buttons and the two rows of each sub-screen, Done button.
ROW1, ROW2, ROW3 = 46, 71, 97
LEFT, RIGHT, MID, DONE = 247, 393, 320, (368, 254)

c = DevClient(sys.argv[1])
mode = sys.argv[2] if len(sys.argv) > 2 else 'edit'

def config():
    return c.action('mod_config')

def click(x, y):
    c.ticks(2)  # let the screen finish its first layout before input
    c.action('gui_click', x=x, y=y)

def done():
    click(*DONE)

def set_feedback(shake_right, flash_right):
    c.action('open_mod_config'); click(MID, ROW3 if len(menu_rows()) == 3 else ROW2)
    click(RIGHT if shake_right else LEFT, ROW1); click(RIGHT if flash_right else LEFT, ROW2)
    done(); done(); c.ticks(2)
    if c.request()['screen'] != 'gameplay': c.action('close_screen')
    v = config()['visuals']
    assert ('screenShake=100' if shake_right else 'screenShake=0') in v and ('screenFlash=100' if flash_right else 'screenFlash=0') in v, v

def menu_rows():
    # Rows visible on the root screen: spirit camera, visuals, feedback (bookkeeping entries hidden).
    return ['spiritCamera', 'visuals', 'feedback']

def toggle_particles():
    c.action('open_mod_config'); click(MID, ROW2); click(MID, ROW2); done(); done(); c.ticks(2)
    if c.request()['screen'] != 'gameplay': c.action('close_screen')

def hurt():
    before = config()['feedback']['hurtTriggers']
    c.command('effect give @s minecraft:instant_health 1 5 true'); c.ticks(5)
    c.command('damage @s 1 minecraft:generic'); c.ticks(2)
    after = config()['feedback']
    assert after['hurtTriggers'] == before + 1, after
    return after

def blade_hit():
    c.ticks(30)  # let the previous swing and its recovery finish
    c.command('kill @e[type=minecraft:zombie]')
    c.command('summon minecraft:zombie 0.5 100 2.3 {NoAI:1b,PersistenceRequired:1b}'); c.ticks(4)
    # A killed zombie stays listed during its death animation; use the newly summoned one.
    zombie = max((e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.minecraft.zombie' and e['health'] > 0),
                 key=lambda e: e['id'])
    received = config()['fx']['received']
    c.action('look', yaw=0, pitch=10); c.action('attack_entity', id=zombie['id']); c.ticks(30)
    fx = config()['fx']
    assert fx['received'] > received, fx
    return fx['BLADE_HIT']

if mode == '--expect':
    expected = json.loads(sys.argv[3])
    got = config()
    c.records['steps']['after_restart'] = got
    assert got['configVersion'] == 2 and got['visuals'] == expected['visuals'], got
    c.records['status'] = 'passed'; c.save(); print('settings persisted', got['visuals']); sys.exit(0)

deadline = time.monotonic() + 90
while c.request()['overlay'] != 'none':
    assert time.monotonic() < deadline; time.sleep(.2)
if c.request()['screen'] != 'TitleScreen' and 'player' not in c.request():
    c.action('close_screen')
c.records['steps']['initial'] = config()
c.records['config_file_before'] = CONFIG.read_text(encoding='utf-8')
if 'player' not in c.request():
    c.action('create_world', name='orimod_dev_settings_' + c.build_id.replace('-', '_')[-24:], seed=8675399)
    c.ready()
c.command('gamerule doDaylightCycle false'); c.command('time set 18000')
c.command('fill -6 99 -6 6 99 12 stone'); c.command('fill -6 100 -6 6 110 12 air'); c.command('tp @s 0.5 100 0.5 0 0')
c.command('item replace entity @s hotbar.0 with orimod:spirit_core'); c.action('select_slot', slot=0); c.action('use_item'); c.ticks(8)
c.action('close_screen')
if not c.spirit()['server'].get('form'):
    c.action('toggle_form'); c.ticks(10)
c.command('gamemode survival'); c.command('difficulty easy')
c.command('item replace entity @s hotbar.1 with orimod:spirit_blade'); c.action('select_slot', slot=1)

# Start from a known state: shake 0 / flash 100 through the real sliders.
set_feedback(False, True)
c.records['steps']['shake0_flash100'] = {'config': config()['visuals'], 'hurt': hurt()}
assert c.records['steps']['shake0_flash100']['hurt']['lastShake'] == 0
set_feedback(True, False)
step = {'config': config()['visuals'], 'hurt': hurt()}
c.records['steps']['shake100_flash0'] = step
assert step['hurt']['lastShake'] > 0

particles = config()['visuals']
c.records['steps']['blade_before_toggle'] = {'config': particles, 'fx': blade_hit()}
toggle_particles()
c.records['steps']['blade_after_toggle'] = {'config': config()['visuals'], 'fx': blade_hit()}
a, b = c.records['steps']['blade_before_toggle']['fx'], c.records['steps']['blade_after_toggle']['fx']
assert a['advanced'] != b['advanced'] and min(a['essential'], b['essential']) >= 1
assert (b['decorative'] == 0) == (not b['advanced'])
c.screenshot('after-edits')
c.records['config_file_after'] = CONFIG.read_text(encoding='utf-8')
c.records['status'] = 'recorded'
c.records['restart_check'] = 'restart the client, then run with --expect \'{"visuals": "%s"}\'' % config()['visuals']
c.save()
print(json.dumps(c.records['steps'], indent=1)[:2000])
