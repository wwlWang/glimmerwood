"""Check both vanilla crafting screens while changing the actual world difficulty."""
import argparse
from devtools_client import DevClient

parser = argparse.ArgumentParser()
parser.add_argument('build_id')
c = DevClient(parser.parse_args().build_id)
ingredients = ['minecraft:copper_ingot', 'minecraft:torch', 'minecraft:sugar']

def container(): return c.action('container_state')
def slot(index): return next(s for s in container()['slots'] if s['index'] == index)
def click(index):
    target = slot(index)
    c.action('gui_click', x=target['x'] + 8, y=target['y'] + 8); c.ticks(2)
def light(): return c.action('progress_state')['server']['progress']['light']

try:
    c.action('focus_game'); c.ready(); c.action('release_keys')
    assert c.spirit()['server']['awakened'], 'Use an awakened development player'
    c.command('gamemode survival'); c.command('setblock 1 160 1 crafting_table')
    for table in [False, True]:
        name = 'table' if table else 'inventory'
        c.action('close_screen'); c.command('clear @s'); c.command('difficulty peaceful')
        for i, item in enumerate(ingredients):
            c.command(f'item replace entity @s hotbar.{i} with {item}')
        if table: c.action('use_block', x=1, y=160, z=1)
        else: c.action('open_inventory')
        c.ticks(4)
        assert container()['screen'] == ('CraftingScreen' if table else 'InventoryScreen')
        for i, item in enumerate(ingredients):
            source = next(s for s in container()['slots'] if s['index'] >= (10 if table else 9) and s['item'] == item)
            click(source['index']); click(i + 1)
        assert slot(0)['item'] == 'orimod:spirit_light' and slot(0)['count'] == 4
        before = light(); observations = {}
        c.screenshot(name + '-peaceful')
        for difficulty in ['normal', 'hard', 'easy']:
            c.command('difficulty ' + difficulty); c.ticks(4)
            assert slot(0)['item'] == 'minecraft:air', (difficulty, container())
            click(0)
            for i, item in enumerate(ingredients):
                assert slot(i + 1)['item'] == item and slot(i + 1)['count'] == 1
            assert light() == before, 'Rejected craft granted light'
            observations[difficulty] = container()
            if difficulty == 'normal': c.screenshot(name + '-normal')
        # The existing filled grid becomes craftable again without moving an ingredient.
        c.command('difficulty peaceful'); c.ticks(4)
        assert slot(0)['item'] == 'orimod:spirit_light' and slot(0)['count'] == 4
        click(0)
        empty = next(s for s in container()['slots'] if s['index'] >= (10 if table else 9) and s['item'] == 'minecraft:air')
        click(empty['index']); c.action('close_screen'); c.ticks(25)
        assert light() == before + 4, 'Peaceful crafted light was not converted exactly once'
        c.records['steps'][name] = {'beforeLight': before, 'afterLight': light(), 'blockedDifficulties': observations}
        c.save(); print(name + ': Peaceful works; Easy/Normal/Hard reject; cached output clears and returns', flush=True)
    c.records['status'] = 'passed'; c.save()
except Exception as error:
    c.records.update(status='failed', error=repr(error)); c.save(); raise
finally:
    c.action('release_keys'); c.action('close_screen'); c.command('difficulty peaceful')
