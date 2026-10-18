"""Temporarily remove runtime GLB clips, reload, exercise real render paths, then restore bytes.

Only build/resources is changed. Authoring files, packaged source resources and manifests stay intact.
"""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
import time
from devtools_client import DevClient, ROOT

parser = argparse.ArgumentParser(); parser.add_argument('build_id'); args = parser.parse_args()
c = DevClient(args.build_id)
source = ROOT / 'src/main/resources/assets/orimod/models/entity/ori.glb'
runtime = ROOT / 'build/resources/main/assets/orimod/models/entity/ori.glb'
original = source.read_bytes()
assert runtime.read_bytes() == original, 'Runtime GLB must match source before the test'
original_sha = hashlib.sha256(original).hexdigest()
json_size, json_kind = struct.unpack_from('<II', original, 12)
assert json_kind == 0x4E4F534A
document = json.loads(original[20:20 + json_size])
binary_chunks = original[20 + json_size:]
all_names = {a['name'] for a in document['animations']}

def modified(remove):
    gltf = dict(document)
    gltf['animations'] = [a for a in document['animations'] if a['name'] not in remove]
    payload = json.dumps(gltf, separators=(',', ':')).encode('utf-8')
    payload += b' ' * (-len(payload) % 4)
    return struct.pack('<IIIII', 0x46546C67, 2, 20 + len(payload) + len(binary_chunks), len(payload), 0x4E4F534A) + payload + binary_chunks

def reload_with(remove):
    c.action('release_keys'); c.action('close_screen')
    variant = modified(remove) if remove else original
    runtime.write_bytes(variant)
    c.records.setdefault('variants', []).append({'removed': sorted(remove), 'sha256': hashlib.sha256(variant).hexdigest()}); c.save()
    before = c.request()['resourceReloads']; c.action('reload_resources')
    deadline = time.monotonic() + 55
    while time.monotonic() < deadline:
        state = c.request()
        if state['reloadError']: raise AssertionError(state['reloadError'])
        if state['resourceReloads'] > before and state['overlay'] == 'none':
            animation = c.spirit()['client']['animation']
            if animation.get('visible') and set(animation.get('missingClips', [])) == remove:
                c.action('focus_game'); c.ticks(4); return animation
        time.sleep(.1)
    raise AssertionError(('Model did not reload expected missing clips', sorted(remove), c.spirit()))

def combo(name):
    c.action('settings', mainArm='LEFT')
    c.command('item replace entity @s hotbar.0 with orimod:spirit_blade'); c.action('select_slot', slot=0); c.ticks(8)
    before = c.start()
    c.action('input_script', steps=[{'at': at, 'key': 'key.attack', 'down': down}
        for at, down in [(0, True), (1, False), (6, True), (7, False), (19, True), (20, False)]])
    c.ticks(38); after = c.finish(name, before)
    assert after['server']['combat']['bladeStarts'] - before['server']['combat']['bladeStarts'] == 3
    samples = json.loads((c.out / (name + '.json')).read_text(encoding='utf-8'))['samples']
    second = [s['state']['animation'] for s in samples if s['side'] == 'client'
              and s['state']['animation'].get('overlay') == 'blade_2_left']
    assert second, 'Second left-handed blade overlay was not exercised'
    return second

try:
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.command('tp @s .5 160 .5 0 0'); c.command('difficulty peaceful')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    c.action('camera', mode='THIRD_PERSON_FRONT'); c.ticks(10)
    c.records['original_sha256'] = original_sha
    reload_with({'blade_2_left'})
    fallback = combo('missing-left-combo')
    assert all(a['overlayClip'] == 'idle' for a in fallback), fallback
    c.records['steps']['left-overlay-fallback'] = fallback[0]; c.save()

    reload_with({'idle', 'hold', 'hold_left'})
    c.command('item replace entity @s hotbar.0 with minecraft:iron_pickaxe')
    c.command('item replace entity @s weapon.offhand with minecraft:shield'); c.ticks(8)
    c.action('open_inventory'); c.ticks(8); c.screenshot('missing-idle-holds-inventory')
    assert c.action('container_state')['screen'] == 'InventoryScreen'
    c.records['steps']['inventory-missing-idle-holds'] = c.spirit(); c.save(); c.action('close_screen')

    animation = reload_with(all_names)
    assert animation['baseClip'] == 'ori_missing_rest', animation
    fallback = combo('no-clips-combo')
    assert all(a['overlayClip'] == 'ori_missing_rest' for a in fallback), fallback
    c.action('settings', mainArm='RIGHT')
    c.command('item replace entity @s weapon.offhand with air')
    subprocess.run([sys.executable, str(ROOT / 'scripts/verify-damage-interruption.py'), args.build_id + '-actions'], cwd=ROOT, check=True)
    c.records['steps']['all-clips-actions'] = {'evidence': args.build_id + '-actions', 'status': 'passed'}
    c.action('open_inventory'); c.ticks(6); c.screenshot('no-clips-inventory'); c.action('close_screen')
    c.ticks(6); c.screenshot('no-clips-world')
    reload_with(set())
    restored = combo('restored-left-combo')
    assert all(a['overlayClip'] == 'blade_2_left' and not a['missingClips'] for a in restored), restored
    c.screenshot('restored-world')
    c.records['steps']['restored'] = c.spirit(); c.records['status'] = 'passed'; c.save()
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); c.save(); raise
finally:
    runtime.write_bytes(original)
    assert hashlib.sha256(source.read_bytes()).hexdigest() == original_sha
    assert hashlib.sha256(runtime.read_bytes()).hexdigest() == original_sha
    c.records['runtime_restored_sha256'] = original_sha; c.save()
    try:
        if c.records['status'] != 'passed': c.action('reload_resources')
        c.action('release_keys'); c.action('close_screen'); c.action('settings', mainArm='RIGHT')
        c.action('camera', mode='FIRST_PERSON')
    except Exception: pass
print(c.out, flush=True)
