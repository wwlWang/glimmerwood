"""Operate the shrine screen at common window/GUI scales in Chinese and English."""
import argparse
import time
from devtools_client import DevClient
from ruin_driver import RuinDriver

p = argparse.ArgumentParser(); p.add_argument('build_id'); p.add_argument('world')
args = p.parse_args(); c = DevClient(args.build_id)


def loaded():
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline; time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(5)


try:
    if 'player' in c.request(): c.action('leave_world')
    c.action('create_world', name=args.world, seed=8675344); loaded()
    cave = RuinDriver(c, 'cave_altar', (1792, 87, 1792)); cave.prepare()
    # An unavailable start must expose the actual missing-item reason at every size.
    c.command('clear @s orimod:spirit_blade')
    for language in ('zh_cn', 'en_us'):
        c.action('close_screen'); c.action('language', code=language); loaded()
        for width, height, scale in ((640, 480, 2), (1280, 720, 3), (1920, 1080, 4), (1920, 1080, 2)):
            c.action('resize', width=width, height=height); c.ticks(4)
            c.action('gui_scale', scale=scale); c.ticks(4)
            gui = cave.open(); name = f'{language}-{width}x{height}-scale{scale}'
            assert gui['screen'] == 'RuinScreen' and len(gui['widgets']) == 2
            assert not gui['widgets'][0]['active'] and gui['widgets'][1]['active'], gui
            for widget in gui['widgets']:
                assert widget['visible'] and 0 <= widget['x'] < widget['x'] + widget['width'] <= gui['width']
                assert 0 <= widget['y'] < widget['y'] + widget['height'] <= gui['height']
            assert ('灵光刃' if language == 'zh_cn' else 'Spirit Blade') in gui['narration'], gui
            c.screenshot(name + '-top'); c.action('gui_key', key=267); c.ticks(2)
            c.screenshot(name + '-page-down')
            c.action('gui_scroll', x=gui['width'] // 2, y=gui['height'] // 2, amount=1); c.ticks(2)
            c.action('gui_key', key=266); c.ticks(2)
            back = gui['widgets'][1]
            c.action('gui_click', x=back['x'] + back['width'] / 2, y=back['y'] + 10); c.ticks(2)
            assert c.request()['screen'] == 'gameplay' and not c.action('ruin_state')['active']
            c.records['steps'][name] = gui; c.save()
            print('Shrine GUI operated:', name, flush=True)
    c.records['status'] = 'passed'; c.records['visual_review'] = 'Screenshots require separate inspection.'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    raise
finally:
    c.save()
    try:
        c.action('close_screen'); c.action('resize', width=1920, height=1080); c.action('gui_scale', scale=3)
        c.action('language', code='zh_cn'); loaded(); c.action('release_keys'); c.action('open_pause')
    except Exception: pass
