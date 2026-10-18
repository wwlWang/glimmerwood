"""R18/R19 real-client smoke: spirit camera cycle button, and persistent glide effect lifecycle.

Run in a development client inside a dedicated orimod_dev_ world. Glide starts the client-side
persistent effect and loop; landing, form switch, death and dimension change must each end it.
Abilities and terrain use development fixtures (commands); this is not a no-command survival check.
"""
import sys
from devtools_client import DevClient

ROW1, DONE = 46, (368, 254)
c = DevClient(sys.argv[1])

def cfg():
    return c.action('mod_config')

def life():
    return cfg()['fxLifecycle']

def click(x, y):
    c.ticks(2); c.action('gui_click', x=x, y=y)

def glide_start():
    c.reset(y=125); c.ticks(4)
    c.action('input_script', steps=[{'at': 0, 'key': 'key.jump', 'down': True}])
    for _ in range(40):
        c.ticks(2)
        if any(a.endswith(':GLIDE') for a in life()['active']):
            return life()
    raise AssertionError('glide effect did not start: %s' % life())

def ended(label):
    for _ in range(20):
        c.ticks(2)
        state = life()
        if not any(a.endswith(':GLIDE') for a in state['active']) and state['loops'] == 0:
            c.records['steps'][label] = state; c.save(); return state
    raise AssertionError('%s left the glide effect running: %s' % (label, life()))

try:
    c.records['initial'] = c.ready()
    # R19: camera option is a cycle button on the root settings screen; values commit on Done.
    if not c.spirit()['client']['form']: c.action('toggle_form'); c.ticks(6)
    seen = [cfg()['spiritCamera']]
    for i in range(3):
        c.action('open_mod_config'); c.ticks(4)
        click(393, ROW1); c.ticks(2)
        if i == 0: c.screenshot('settings-camera-cycled')
        click(*DONE); c.ticks(4)
        if c.request()['screen'] != 'gameplay': c.action('close_screen')
        c.ticks(4)
        state = cfg(); seen.append(state['spiritCamera'])
        live = c.request().get('camera')
        c.records['steps'].setdefault('camera_live', []).append(live)
        assert live == state['spiritCamera'], (live, state['spiritCamera'])
    assert len(set(seen[:3])) == 3 and seen[3] == seen[0], seen
    c.records['steps']['camera_cycle'] = seen; c.save()

    c.action('grant_air_movement')
    for command in ['gamerule fallDamage false', 'fill -20 99 -10 20 99 20 moss_block', 'fill -3 100 -3 3 130 10 air', 'difficulty peaceful']:
        c.command(command)
    if not c.spirit()['client']['form']: c.action('toggle_form'); c.ticks(6)

    c.records['steps']['glide'] = glide_start(); c.screenshot('glide-active')
    c.action('release_keys'); ended('released')
    glide_start(); c.action('toggle_form'); ended('form_switch'); c.action('release_keys'); c.action('toggle_form'); c.ticks(10)
    glide_start(); c.command('kill @s'); ended('death')
    c.action('release_keys'); c.ticks(10); c.action('respawn'); c.ticks(20); c.ready()
    if not c.spirit()['client']['form']: c.action('toggle_form'); c.ticks(6)
    glide_start(); c.command('execute in minecraft:the_nether run tp @s 0 120 0'); ended('dimension')
    c.action('release_keys'); c.command('execute in minecraft:overworld run tp @s 0.5 100 0.5'); c.ticks(20)
    c.records['steps']['final'] = life()
    c.records['status'] = 'passed'; c.save(); print('fx lifecycle smoke passed', c.records['steps'].keys())
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); c.save(); raise
