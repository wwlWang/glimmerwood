"""R20 real-client check: bundled Spirit Glow shader pack and its settings toggle.

Run in a development client inside a dedicated orimod_dev_ world, with Iris installed. Covers: the
first-run choice keeps a shader pack already chosen in Iris; turning the toggle on selects Spirit
Glow and Iris builds its pipeline without a stored error; turning it off restores the previous pack;
switching shaders in Iris is saved to the toggle instead of being reverted. Screenshots are only
evidence that the pipeline renders under this GPU; they are not a visual acceptance.
"""
import sys
from devtools_client import DevClient

PACK = 'Spirit Glow.zip'
c = DevClient(sys.argv[1])
previous = sys.argv[2] if len(sys.argv) > 2 else None

def glow():
    return c.action('mod_config')['spiritGlow']

def wait(label, predicate, ticks=200):
    for _ in range(ticks // 5):
        c.ticks(5)
        state, diag = c.request(), glow()
        if predicate(state['shaders'], diag):
            c.records['steps'][label] = {'shaders': state['shaders'], 'spiritGlow': diag, 'fps': state['fps']}
            c.save()
            return state, diag
    raise AssertionError('%s: %s %s' % (label, c.request()['shaders'], glow()))

try:
    c.records['initial'] = c.ready()
    c.command('time set 1000'); c.command('weather clear')
    first = glow()
    c.records['steps']['first_run'] = {'shaders': c.request()['shaders'], 'spiritGlow': first}
    assert first['iris'] and first['installed'] and first['setupDone'], first
    if previous:
        assert c.request()['shaders']['pack'] == previous and first['toggle'] is False, 'user pack must be kept'
    c.action('spirit_glow', enabled=True)
    wait('enabled', lambda s, d: s['enabled'] and s['pack'] == PACK and d['lastAction'] == 'enable')
    c.ticks(100)
    assert glow().get('irisError') is None, glow()
    c.screenshot('spirit-glow-on-day')
    c.command('time set 13800'); c.ticks(60); c.screenshot('spirit-glow-on-night'); c.command('time set 1000')
    c.action('shaders', enabled=False)
    wait('iris_off_saved', lambda s, d: not s['enabled'] and d['toggle'] is False and d['lastAction'] == 'save_off')
    c.ticks(40); c.screenshot('shaders-off-day')
    c.action('shaders', enabled=True)
    wait('iris_on_saved', lambda s, d: s['enabled'] and s['pack'] == PACK and d['toggle'] is True and d['lastAction'] == 'save_on')
    c.action('spirit_glow', enabled=False)
    if previous:
        wait('restored', lambda s, d: s['pack'] == previous and d['lastAction'] == 'disable')
    else:
        wait('restored', lambda s, d: not s['enabled'] and d['lastAction'] == 'disable')
    c.records['status'] = 'passed'
finally:
    c.save()
print(c.records['status'], c.out)
