"""Disposable world-copy checks for old chunks and a persisted active challenge.

The snapshot case exercises recovery from saved in-progress data, not an OS crash.
Only orimod_dev_ worlds are copied, and every copied file is SHA-256 verified.
"""
import argparse
import hashlib
import io
import json
import math
import shutil
import time
import zlib
import nbtlib
from devtools_client import DevClient, ROOT
from ruin_driver import RuinDriver

p = argparse.ArgumentParser()
p.add_argument('build_id'); p.add_argument('world'); p.add_argument('mode', choices=['oldchunks', 'snapshot'])
args = p.parse_args(); c = DevClient(args.build_id)
assert args.world.startswith('orimod_dev_') and args.world.replace('_', '').isalnum()
copy_name = args.world + '_verified_copy'
c.records.update(world=args.world, copy=copy_name, mode=args.mode)


def wait_world():
    deadline = time.monotonic() + 100
    while 'player' not in c.request() or c.request()['overlay'] != 'none':
        assert time.monotonic() < deadline; time.sleep(.2)
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.ticks(5)


def copy_verified():
    saves = (ROOT / 'run-devtools/saves').resolve()
    source = (saves / args.world).resolve(); target = (saves / copy_name).resolve()
    assert source.parent == saves and target.parent == saves and source.is_dir() and not target.exists()
    files = {f.relative_to(source).as_posix(): hashlib.sha256(f.read_bytes()).hexdigest()
             for f in source.rglob('*') if f.is_file() and f.name != 'session.lock'}
    shutil.copytree(source, target, ignore=shutil.ignore_patterns('session.lock'))
    for relative, expected in files.items():
        assert hashlib.sha256((source / relative).read_bytes()).hexdigest() == expected, ('Source changed while copying', relative)
        assert hashlib.sha256((target / relative).read_bytes()).hexdigest() == expected, relative
    (c.out / 'copy-sha256.json').write_text(json.dumps(files, indent=2), encoding='utf-8')
    c.records['copy_verified_files'] = len(files); c.save()
    return source, target, files


def locate():
    c.action('select_slot', slot=1); c.ticks(2)
    revision = c.action('ruin_state')['replyRevision']; c.action('use_item')
    deadline = time.monotonic() + 45; checks = []
    while True:
        state = c.action('ruin_state')
        if state['search']['active']: checks.append(state['search'])
        if state['replyRevision'] > revision and not state['search']['active'] and '正在寻找' not in state['reply']:
            return {'result': state, 'checks': checks}
        assert time.monotonic() < deadline, state
        time.sleep(.1)


def saved_block(world, x, y, z):
    """Check the copied chunk contents, not just byte equality with a partial save."""
    chunk_x, chunk_z = x // 16, z // 16
    data = (world / 'region' / f'r.{chunk_x // 32}.{chunk_z // 32}.mca').read_bytes()
    index = (chunk_x % 32 + chunk_z % 32 * 32) * 4
    sector = int.from_bytes(data[index:index + 3], 'big')
    assert sector, ('Snapshot is missing fixture chunk', chunk_x, chunk_z)
    offset = sector * 4096; length = int.from_bytes(data[offset:offset + 4], 'big')
    assert data[offset + 4] == 2, 'Unexpected region compression'
    root = nbtlib.File.parse(io.BytesIO(zlib.decompress(data[offset + 5:offset + 4 + length])))
    section = next(s for s in root['sections'] if int(s['Y']) == y // 16)
    states = section['block_states']; palette = states['palette']
    palette_index = 0
    if len(palette) > 1:
        bits = max(4, (len(palette) - 1).bit_length()); per_long = 64 // bits
        cell = (y % 16 * 16 + z % 16) * 16 + x % 16
        packed = int(states['data'][cell // per_long]) & ((1 << 64) - 1)
        palette_index = packed >> (cell % per_long * bits) & ((1 << bits) - 1)
    return str(palette[palette_index]['Name'])


try:
    if 'player' in c.request(): c.action('leave_world')
    c.action('create_world', name=args.world, seed=8675342 if args.mode == 'oldchunks' else 8675343,
             structures=args.mode != 'oldchunks')
    wait_world()
    for command in ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set 9000', 'difficulty peaceful']:
        c.command(command)
    if args.mode == 'oldchunks':
        c.command('gamemode creative'); c.command('tp @s .5 150 .5 0 0'); c.ticks(50)
        c.command('setblock 0 149 0 diamond_block')
        c.command('item replace entity @s hotbar.0 with orimod:spirit_core'); c.action('select_slot', slot=0)
        c.action('use_item'); c.ticks(8); c.action('close_screen')
        c.command('item replace entity @s hotbar.1 with orimod:ruin_guide')
        before = locate(); c.records['steps']['disabled-generation-search'] = before; c.save()
        assert '未找到' in before['result']['reply'] and before['result']['guide'] == 'none', before
        assert before['checks'] and max(s['checked'] for s in before['checks']) >= before['checks'][0]['candidates'] - 1
        c.action('leave_world')
        source, target, hashes = copy_verified()
        level = nbtlib.load(target / 'level.dat')
        assert not level['Data']['WorldGenSettings']['generate_features']
        level['Data']['WorldGenSettings']['generate_features'] = nbtlib.Byte(1)
        level.save()
        c.action('open_world', name=copy_name); wait_world()
        assert c.action('inspect_block', x=0, y=149, z=0)['block'] == 'minecraft:diamond_block'
        old = locate(); c.records['steps']['same-old-chunks-after-enabling'] = old; c.save()
        assert '未找到' in old['result']['reply'] and old['result']['guide'] == 'none', old
        c.screenshot('old-chunks-no-result')
        c.command('tp @s 8192.5 170 .5 0 0'); c.ticks(210)
        fresh = locate(); c.records['steps']['new-area'] = fresh; c.save()
        assert '入口' in fresh['result']['reply'] and fresh['result']['guide'] != 'none', fresh
        entry = fresh['result']['guide']['entry']
        c.command(f'tp @s {entry[0] + .5} {entry[1] + 8} {entry[2] - 10.5} 0 25'); c.ticks(80)
        starts = c.action('ruin_state')['naturalStarts']; assert starts, starts
        c.records['steps']['new-generated-start'] = starts; c.screenshot('new-area-ruin')
        for relative, expected in hashes.items():
            assert hashlib.sha256((source / relative).read_bytes()).hexdigest() == expected, ('Original modified', relative)
    else:
        cave = RuinDriver(c, 'cave_altar', (1536, 87, 1536)); cave.prepare(); cave.start(); cave.cave_to_target()
        before = c.action('ruin_state'); progress = c.action('progress_state')['server']['progress']
        assert before['active'] and before['checkpoint'] == 2 and before['recovery']['dimension']
        c.action('open_pause'); c.ticks(5)
        c.records['flush'] = c.action('save_world'); assert c.records['flush']['saved']
        c.records['steps']['saved-active-session'] = {'ruin': before, 'progress': progress, 'spirit': c.spirit()}
        _, target, _ = copy_verified()
        shrine = tuple(map(int, cave.shrine)); floor = (int(cave.safe[0]), int(cave.safe[1]) - 1, int(cave.safe[2]))
        assert saved_block(target, *shrine) == 'orimod:cave_altar_shrine'
        assert saved_block(target, *floor) == 'minecraft:deepslate_bricks'
        c.records['copied_fixture_blocks'] = {'shrine': saved_block(target, *shrine), 'safe_floor': saved_block(target, *floor)}
        c.action('leave_world'); c.action('open_world', name=copy_name); wait_world(); c.ticks(15)
        after = c.action('ruin_state'); actual = c.spirit()['server']
        assert not after['active'] and after['recovery']['dimension'] == '', after
        assert actual['health'] > 0 and math.dist(actual['position'], cave.safe) < .6, actual
        assert c.action('progress_state')['server']['progress'] == progress
        assert not any(e['type'] == 'entity.orimod.practice_target' for e in c.action('nearby_entities')['entities'])
        c.records['steps']['restored-copy'] = {'ruin': after, 'spirit': actual}; c.screenshot('restored-safe-point')
    c.records['status'] = 'passed'; print(args.mode + ' verified on a hash-checked disposable copy', flush=True)
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error)
    raise
finally:
    c.save()
    try: c.action('release_keys'); c.action('open_pause')
    except Exception: pass
