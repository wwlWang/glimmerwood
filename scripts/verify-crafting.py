"""Craft release recipes through vanilla inventory/table screens and test guide delivery.

Ingredients and the safe test platform are supplied as fixtures. The actual recipe
matching, taking output, progression conversion and guide use run through player UI.
"""
import argparse
import time
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('world')
args = p.parse_args(); c = DevClient(args.build_id)


def progress(): return c.action('progress_state')['server']
def count(item): return sum(v['count'] for v in progress()['inventory'] if v['item'] == item)
def snap(name):
    c.records['steps'][name] = {'progress': progress(), 'state': c.request()}; c.save()


def click_slot(index):
    state = c.action('container_state')
    slot = next(s for s in state['slots'] if s['index'] == index)
    c.action('gui_click', x=slot['x'] + 8, y=slot['y'] + 8); c.ticks(2)


def craft(name, ingredients, output, amount=1, table=False):
    c.action('close_screen'); c.command('clear @s')
    for i, item in enumerate(ingredients): c.command(f'item replace entity @s hotbar.{i} with {item}')
    if table:
        c.action('use_block', x=1, y=100, z=1); c.ticks(4)
    else: c.action('open_inventory'); c.ticks(3)
    expected_screen = 'CraftingScreen' if table else 'InventoryScreen'
    assert c.action('container_state')['screen'] == expected_screen
    for i, item in enumerate(ingredients):
        slots = c.action('container_state')['slots']
        source = next(s for s in slots if s['index'] >= (10 if table else 9) and s['item'] == item)
        click_slot(source['index']); click_slot(i + 1)
    result = next(s for s in c.action('container_state')['slots'] if s['index'] == 0)
    assert result['item'] == output and result['count'] == amount, result
    c.screenshot(name + '-recipe')
    before = progress()['progress']['light']
    click_slot(0)
    empty = next(s for s in c.action('container_state')['slots'] if s['index'] >= (10 if table else 9) and s['item'] == 'minecraft:air')
    click_slot(empty['index']); c.action('gui_key', key=256); c.ticks(25)
    if output == 'orimod:spirit_light':
        assert progress()['progress']['light'] - before == amount and count(output) == 0
    else: assert count(output) == amount, progress()
    snap(name)


try:
    if 'player' in c.request(): c.action('leave_world')
    c.action('create_world', name=args.world, seed=8675340)
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline; time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready()
    for command in ['gamemode creative', 'tp @s .5 110 .5', 'gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 9000']:
        c.command(command)
    c.ticks(30)
    for command in ['fill -4 99 -4 4 99 4 moss_block', 'fill -4 100 -4 4 108 4 air',
                    'setblock 1 100 1 crafting_table', 'tp @s .5 100 .5 0 0', 'gamemode survival', 'difficulty peaceful']:
        c.command(command)
    # All slots occupied: the gift remains pending instead of duplicating or dropping.
    for i in range(36): c.command(f'item replace entity @s ' + (f'hotbar.{i}' if i < 9 else f'inventory.{i - 9}') + ' with stone 64')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_core'); c.action('select_slot', slot=0)
    c.action('use_item'); c.ticks(25); c.action('close_screen')
    assert count('orimod:ruin_guide') == 0 and progress()['progress']['rewards'].get('gift/ruin_guide', 0) == 0
    snap('full-inventory-pending-guide')
    c.command('item replace entity @s hotbar.2 with air'); c.ticks(25)
    assert count('orimod:ruin_guide') == 1 and progress()['progress']['rewards']['gift/ruin_guide'] == 1
    c.action('use_item'); c.ticks(25); c.action('close_screen')
    assert count('orimod:ruin_guide') == 1
    c.command('clear @s orimod:ruin_guide'); c.ticks(25)
    assert count('orimod:ruin_guide') == 0
    snap('one-time-guide-delivery')
    craft('core', ['minecraft:copper_ingot', 'minecraft:glass', 'minecraft:copper_ingot',
                   'minecraft:glass', 'minecraft:iron_ingot', 'minecraft:glass',
                   'minecraft:copper_ingot', 'minecraft:glass', 'minecraft:copper_ingot'], 'orimod:spirit_core', table=True)
    craft('blade', ['minecraft:iron_sword', 'minecraft:glow_ink_sac', 'minecraft:amethyst_shard'], 'orimod:spirit_blade')
    craft('bow', ['minecraft:bow', 'minecraft:glow_ink_sac', 'minecraft:amethyst_shard'], 'orimod:spirit_bow')
    craft('guide', ['minecraft:copper_ingot', 'minecraft:paper'], 'orimod:ruin_guide')
    craft('peaceful-light', ['minecraft:copper_ingot', 'minecraft:torch', 'minecraft:sugar'], 'orimod:spirit_light', amount=4)
    assert c.spirit()['server']['awakened']
    c.records['status'] = 'passed'; print('Five real crafting recipes and one-time guide delivery passed', flush=True)
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    try: snap('failure')
    except Exception: pass
    raise
finally:
    c.save()
    try: c.action('release_keys'); c.action('open_pause')
    except Exception: pass
