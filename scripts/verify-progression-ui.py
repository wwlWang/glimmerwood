"""Capture the native panel at common GUI scales and exercise keyboard selection."""
import argparse
from devtools_client import DevClient
parser=argparse.ArgumentParser();parser.add_argument('build_id')
c=DevClient(parser.parse_args().build_id)

def panel():
    c.key('orimod.panel',2);c.ticks(3)
    assert c.action('gui_state')['screen']=='SpiritScreen'
def listing(state): return next(w for w in state['widgets'] if w['type']=='ChoiceList')
try:
    c.ready();panel()
    for _ in range(12):
        if listing(c.action('gui_state'))['focused']: break
        c.action('gui_key',key=258)
    original=listing(c.action('gui_state'))['selected']
    assert listing(c.action('gui_state'))['focused']
    c.action('gui_key',key=264)
    changed=listing(c.action('gui_state'))['selected']
    assert changed!=original,(original,changed)
    c.action('gui_key',key=265)
    assert listing(c.action('gui_state'))['selected']==original
    c.records['keyboard']={'before':original,'after_down':changed,'after_up':original}
    for width,height,scale in [(1920,1080,2),(1920,1080,3),(1920,1080,4),(1280,720,3)]:
        c.action('resize',width=width,height=height)
        c.action('gui_scale',scale=scale)
        c.ticks(5)
        state=c.action('gui_state')
        for widget in state['widgets']:
            if widget['visible']:
                assert widget['x']>=0 and widget['y']>=0 and widget['x']+widget['width']<=state['width'] and widget['y']+widget['height']<=state['height'],widget
        name=f'{width}x{height}-scale{scale}'
        c.screenshot(name)
        c.records['steps'][name]=state;c.save()
    c.records['status']='functional-pass-awaiting-visual-review';c.save()
finally:
    c.action('resize',width=1920,height=1080)
    c.action('gui_scale',scale=4)
    c.action('close_screen')
print(c.out,flush=True)
