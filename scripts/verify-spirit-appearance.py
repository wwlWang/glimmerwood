"""Check the agreed hidden armor appearance and vanilla lead interaction on the real player."""
import sys
from devtools_client import DevClient

c = DevClient(sys.argv[1])

def form(enabled):
    if c.spirit()['server']['form'] != enabled:
        c.action('toggle_form'); c.ticks(12)
    assert c.spirit()['client']['form'] == enabled

def capture(label, armor, toughness, inventory=False):
    # Form transitions restore the player's view preference; apply the review view afterwards.
    c.action('camera', mode='THIRD_PERSON_BACK')
    c.action('review_camera', enabled=True, x=2.4, y=1.2, z=3.6, targetHeight=.75)
    c.ticks(5)
    state = c.spirit()
    c.records['steps'][label] = state; c.save()
    for side in ['client', 'server']:
        assert state[side]['vanilla']['armor'] == armor
        assert state[side]['vanilla']['armorToughness'] == toughness
    c.screenshot(label)
    if inventory:
        c.action('open_inventory'); c.ticks(5)
        c.screenshot(label + '-inventory')
        c.action('close_screen'); c.action('focus_game'); c.ticks(5)

try:
    c.action('close_screen'); c.action('focus_game'); c.ready(); c.action('release_keys')
    c.command('gamemode creative')
    c.command('fill -10 199 -10 10 199 10 smooth_stone')
    c.command('fill -10 200 -10 10 206 10 air')
    c.command('tp @s .5 200 .5 0 0')
    c.command('effect clear @s'); c.command('time set noon'); c.command('weather clear')
    c.command('kill @e[type=item,x=-10,y=199,z=-10,dx=20,dy=8,dz=20]')
    c.action('select_slot', slot=0)
    for slot in ['armor.head', 'armor.chest', 'armor.legs', 'armor.feet', 'weapon.mainhand', 'weapon.offhand']:
        c.command(f'item replace entity @s {slot} with air')
    c.command('gamemode survival')
    c.action('settings', fov=45, hideGui=False)
    for shaders in [False, True]:
        c.action('shaders', enabled=shaders); c.ticks(20)
        prefix = 'on-' if shaders else 'off-'
        c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=2.4, y=1.2, z=3.6, targetHeight=.75)
        c.command('tp @s .5 200 .5 0 0')
        for slot, item in [('head', 'helmet'), ('chest', 'chestplate'), ('legs', 'leggings'), ('feet', 'boots')]:
            c.command(f'item replace entity @s armor.{slot} with minecraft:diamond_{item}')
        form(False); c.ticks(10); capture(prefix + 'human-armored', 20, 8, True)
        form(True); c.ticks(10); capture(prefix + 'spirit-armored', 20, 8, True)
        form(False); c.ticks(10); capture(prefix + 'human-restored', 20, 8)
        form(True)
        for slot in ['head', 'chest', 'legs', 'feet']:
            c.command(f'item replace entity @s armor.{slot} with air')
        c.ticks(8); capture(prefix + 'spirit-unarmored', 0, 0)
        c.command('item replace entity @s armor.head with minecraft:carved_pumpkin')
        c.command('item replace entity @s armor.chest with minecraft:elytra')
        c.ticks(8); capture(prefix + 'spirit-head-wings', 0, 0)
        c.command('item replace entity @s armor.head with air')
        c.command('item replace entity @s armor.chest with air')
        c.command('kill @e[tag=orimod_r12_leash]')
        c.command('summon minecraft:cow 2.5 200 2.5 {NoAI:1b,PersistenceRequired:1b,Tags:["orimod_r12_leash"]}')
        c.command('item replace entity @s hotbar.0 with minecraft:lead 8')
        c.action('review_camera', enabled=False); c.action('camera', mode='FIRST_PERSON')
        c.action('look', yaw=-45, pitch=7); c.ticks(8)
        c.key('use', 1); c.ticks(8)
        entities = c.action('nearby_entities')
        c.records['steps'][prefix + 'lead'] = entities; c.save()
        cows = [e for e in entities['entities'] if e['type'] == 'entity.minecraft.cow']
        assert len(cows) == 1 and cows[0]['leashHolder'] >= 0, 'Real lead input did not attach the cow'
        c.screenshot(prefix + 'lead-first-person')
        c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=-3.5, y=2.2, z=4, targetHeight=.8)
        c.ticks(8); c.screenshot(prefix + 'lead-third-person')
        form(False)
        c.action('camera', mode='THIRD_PERSON_BACK')
        c.action('review_camera', enabled=True, x=-3.5, y=2.2, z=4, targetHeight=.8)
        c.ticks(8); c.screenshot(prefix + 'lead-human')
        form(True); c.ticks(8)
        assert [e for e in c.action('nearby_entities')['entities'] if e['type'] == 'entity.minecraft.cow'][0]['leashHolder'] >= 0
        c.command('kill @e[tag=orimod_r12_leash]')
        c.ticks(22)
        c.command('kill @e[type=item,x=-10,y=199,z=-10,dx=20,dy=8,dz=20]')
        c.command('item replace entity @s hotbar.0 with air')
    c.records.update(status='passed', visual_review='pending', limits='Equipment supplied by commands in an existing disposable development world; not no-command survival acceptance.')
except Exception as error:
    c.records.update(status='failed', error=str(error)); raise
finally:
    c.action('release_keys'); c.action('review_camera', enabled=False)
    c.action('settings', fov=70, hideGui=False); c.action('camera', mode='FIRST_PERSON')
    c.action('open_pause'); c.save()
