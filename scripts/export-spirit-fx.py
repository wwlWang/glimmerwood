"""FX02: export the editable spirit effect sources (art/source/effects/spirit/*.json) into the runtime
definitions the client loads (src/main/resources/assets/orimod/spirit_fx/*.json).

Export validates each source, drops editor-only fields ("notes"), and writes canonical JSON (sorted
keys, 2-space indent, LF), so re-exporting an unchanged source is byte-identical.
Usage: python3 scripts/export-spirit-fx.py [--check]   (--check: fail if any runtime file is stale)"""
import json, pathlib, sys
root = pathlib.Path(__file__).resolve().parent.parent
src_dir, out_dir = root / 'art/source/effects/spirit', root / 'src/main/resources/assets/orimod/spirit_fx'
LAYER_KEYS = {'particle', 'share', 'spread', 'speed'}
# R32 optional shape/look keys (defaults applied by the client when absent)
OPTIONAL_KEYS = {'shape', 'radius', 'length', 'outward', 'color', 'size', 'lifetime'}
SHAPES = {'burst', 'ring', 'arc', 'line', 'column', 'beam'}
import re

def strip(value):
    if isinstance(value, dict): return {k: strip(v) for k, v in value.items() if k != 'notes'}
    if isinstance(value, list): return [strip(v) for v in value]
    return value

def validate(name, d):
    assert d.get('format') == 1, f'{name}: format must be 1'
    assert d.get('kind') == name, f'{name}: kind must match the file name'
    layers = d.get('layers'); assert layers, f'{name}: needs at least one layer'
    for layer in layers:
        assert LAYER_KEYS <= set(layer) <= LAYER_KEYS | OPTIONAL_KEYS, f'{name}: layer keys {sorted(layer)}'
        assert layer.get('shape', 'burst') in SHAPES, f'{name}: shape must be one of {sorted(SHAPES)}'
        assert re.fullmatch(r'#[0-9a-f]{6}', layer.get('color', '#c8f4ff')), f'{name}: color is #rrggbb lower case'
        assert 0 <= layer.get('radius', 0) <= 6 and 0 <= layer.get('length', 0) <= 6 and abs(layer.get('outward', 0)) <= 2, f'{name}: radius/length/outward range'
        assert .02 <= layer.get('size', .25) <= 2 and 1 <= layer.get('lifetime', 16) <= 120, f'{name}: size/lifetime range'
        assert ':' in layer['particle'], f'{name}: particle must be a namespaced id'
        assert len(layer['spread']) == 3 and all(v >= 0 for v in layer['spread']), f'{name}: spread is 3 values >= 0'
        assert layer['speed'] >= 0 and 0 < layer['share'] <= 1, f'{name}: speed >= 0, 0 < share <= 1'
    assert abs(sum(l['share'] for l in layers) - 1) < 1e-6, f'{name}: layer shares must sum to 1'
    assert set(d) == {'format', 'kind', 'layers'}, f'{name}: unknown keys {sorted(set(d) - {"format", "kind", "layers"})}'

def export():
    out = {}
    for path in sorted(src_dir.glob('*.json')):
        d = strip(json.loads(path.read_text(encoding='utf-8'))); validate(path.stem, d)
        out[path.stem + '.json'] = json.dumps(d, indent=2, sort_keys=True) + '\n'
    return out

files = export(); check = '--check' in sys.argv; stale = []
existing = {p.name for p in out_dir.glob('*.json')} if out_dir.exists() else set()
for name, text in files.items():
    target = out_dir / name
    if not target.exists() or target.read_text(encoding='utf-8') != text:
        stale.append(name)
        if not check: out_dir.mkdir(parents=True, exist_ok=True); target.write_bytes(text.encode('utf-8'))
orphans = sorted(existing - set(files))
if check and (stale or orphans): print('stale:', stale, 'orphans:', orphans); sys.exit(1)
for name in orphans if not check else []: (out_dir / name).unlink()
print(f'{len(files)} effects {"checked" if check else "exported"}; changed {stale}; removed {orphans if not check else []}')
