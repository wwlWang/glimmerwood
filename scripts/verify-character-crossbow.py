"""Operate vanilla crossbows and fishing rods through real input in a disposable world."""
import sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])


def loaded(offhand):
    path = 'Inventory[{Slot:-106b}]' if offhand else 'SelectedItem'
    result = c.command('data get entity @s ' + path + '.components."minecraft:charged_projectiles"')
    return result['success'] and result['result'] > 0


def arrows():
    return sum(s['count'] for s in c.action('progress_state')['server']['inventory'] if s['item'] == 'minecraft:arrow')


def capture(name):
    c.screenshot(name)
    c.records['steps'][name] = c.spirit()
    c.save()


def view(third):
    c.action('camera', mode='THIRD_PERSON_BACK' if third else 'FIRST_PERSON')
    c.action('settings', fov=45 if third else 70, hideGui=third)
    c.action('review_camera', enabled=third, x=-2.1, y=.9, z=1.7, targetHeight=.7)
    c.ticks(6)


try:
    c.action('close_screen'); c.action('focus_game'); c.ready(); c.reset()
    c.command('gamemode survival'); c.command('time set noon'); c.command('weather clear')
    c.action('select_slot', slot=0)
    for shaders in [False, True]:
        c.action('shaders', enabled=shaders); c.ticks(20)
        for arm in ['RIGHT', 'LEFT']:
            c.action('settings', mainArm=arm)
            for offhand in [False, True]:
                name = ('on-' if shaders else 'off-') + arm.lower() + ('-offhand' if offhand else '-mainhand')
                c.reset(); view(True)
                c.command('kill @e[type=arrow,distance=..64]')
                c.command('item replace entity @s weapon.mainhand with air')
                c.command('item replace entity @s weapon.offhand with air')
                c.command('item replace entity @s hotbar.1 with minecraft:arrow 64')
                slot = 'weapon.offhand' if offhand else 'weapon.mainhand'
                c.command(f'item replace entity @s {slot} with minecraft:crossbow')
                c.ticks(12)
                assert not loaded(offhand)
                before = arrows()
                c.key('use', 8); c.ticks(12)
                assert not loaded(offhand) and arrows() == before, name + ': early release must cancel loading'
                capture(name + '-cancelled')
                c.key('use', 38); c.ticks(10)
                capture(name + '-loading-third')
                c.ticks(34); c.action('release_keys'); c.ticks(8)
                assert loaded(offhand) and arrows() == before - 1, name + ': load consumes exactly one arrow'
                capture(name + '-loaded-third')
                view(False); capture(name + '-loaded-first')
                c.ticks(30)
                assert loaded(offhand), name + ': charged arrow must remain while aiming'
                c.key('use', 1); c.ticks(2)
                entities = c.action('nearby_entities')
                c.records['steps'][name + '-shot'] = entities
                assert any(e['type'] == 'entity.minecraft.arrow' for e in entities['entities']), name + ': missing native arrow'
                assert not loaded(offhand) and arrows() == before - 1
                c.ticks(16); capture(name + '-fired-first')
                c.action('release_keys')
            # One click casts; holding use repeatedly would also reel the hook in.
            name = ('on-' if shaders else 'off-') + arm.lower() + '-fishing'
            c.reset(); view(False)
            c.command('item replace entity @s weapon.offhand with air')
            c.command('item replace entity @s weapon.mainhand with minecraft:fishing_rod')
            c.ticks(12); c.key('use', 1); c.ticks(15)
            hooks = [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.minecraft.fishing_bobber']
            assert len(hooks) == 1, name + ': expected one cast hook'
            c.records['steps'][name + '-hook'] = hooks
            capture(name + '-first')
            view(True); capture(name + '-third')
            c.key('use', 1); c.ticks(8)
            assert not any(e['type'] == 'entity.minecraft.fishing_bobber' for e in c.action('nearby_entities')['entities'])
    c.records.update(status='passed', visual_review='pending', limits='Command-supplied equipment in a copied development world; still images do not prove every transition frame.')
except Exception as error:
    c.records.update(status='failed', error=str(error)); raise
finally:
    c.action('release_keys'); c.action('review_camera', enabled=False)
    c.action('settings', mainArm='RIGHT', fov=70, hideGui=False)
    c.command('item replace entity @s weapon.mainhand with air')
    c.command('item replace entity @s weapon.offhand with air')
    c.action('camera', mode='FIRST_PERSON'); c.action('open_pause'); c.save()
