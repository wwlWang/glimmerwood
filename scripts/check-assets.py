"""Validate the exported deformation sample against the actual renderer's input limits."""
import hashlib
import json
import math
from pathlib import Path
import struct

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'art/source/deformation-sample/manifest.json').read_text(encoding='utf-8'))
path = root / manifest['export']
data = path.read_bytes()
magic, version, length = struct.unpack_from('<III', data)
assert magic == 0x46546C67 and version == 2 and length == len(data)
json_length, json_type = struct.unpack_from('<II', data, 12)
assert json_type == 0x4E4F534A
gltf = json.loads(data[20:20 + json_length])
binary_offset = 20 + json_length
binary_length, binary_type = struct.unpack_from('<II', data, binary_offset)
assert binary_type == 0x004E4942
binary = memoryview(data)[binary_offset + 8:binary_offset + 8 + binary_length]

def values(index):
    accessor = gltf['accessors'][index]
    assert 'sparse' not in accessor, 'Sparse accessor not handled by this validator'
    view = gltf['bufferViews'][accessor['bufferView']]
    component = {5121: 'B', 5123: 'H', 5125: 'I', 5126: 'f'}[accessor['componentType']]
    count = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}[accessor['type']]
    fmt = '<' + component * count
    stride = view.get('byteStride', struct.calcsize(fmt))
    start = view.get('byteOffset', 0) + accessor.get('byteOffset', 0)
    for i in range(accessor['count']):
        yield struct.unpack_from(fmt, binary, start + i * stride)

assert len(gltf['meshes']) == 1 and len(gltf['scenes']) == 1 and len(gltf['skins']) == 1
texture = root / manifest['texture']
assert texture.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
assert hashlib.sha256(texture.read_bytes()).hexdigest() == manifest['texture_sha256']
for material in gltf['materials']:
    index = material['pbrMetallicRoughness']['baseColorTexture']['index']
    image = gltf['images'][gltf['textures'][index]['source']]
    assert image['extras']['resourceLocation'] == manifest['texture_resource']
joint_count = len(gltf['skins'][0]['joints'])
palette_slots = len(gltf['nodes']) + joint_count
assert joint_count <= 64 and palette_slots <= 256, (joint_count, palette_slots)
clips = [clip['name'] for clip in gltf['animations']]
assert len(clips) == len(set(clips)) and set(clips) == set(manifest['clips'])
vertices = triangles = 0
for primitive in gltf['meshes'][0]['primitives']:
    attributes = primitive['attributes']
    positions = list(values(attributes['POSITION']))
    joints = list(values(attributes['JOINTS_0']))
    weights = list(values(attributes['WEIGHTS_0']))
    assert len(positions) == len(joints) == len(weights)
    for position, indices, influence in zip(positions, joints, weights):
        assert all(math.isfinite(value) for value in position + influence)
        assert abs(sum(influence) - 1.0) < 0.001 and min(influence) >= 0
        assert all(0 <= index < joint_count for index in indices)
    indices = list(values(primitive['indices']))
    assert len(indices) % 3 == 0 and all(i[0] < len(positions) for i in indices)
    vertices += len(positions)
    triangles += len(indices) // 3
assert triangles == manifest['triangles']
assert hashlib.sha256(data).hexdigest() == manifest['export_sha256']
assert hashlib.sha256((root / manifest['source']).read_bytes()).hexdigest() == manifest['source_sha256']
# The current player adapter packages the same skin/animation data under its production namespace.
player_path = root / manifest['player_export']
player_data = player_path.read_bytes()
assert hashlib.sha256(player_data).hexdigest() == manifest['player_export_sha256']
assert struct.unpack_from('<III', player_data) == (0x46546C67, 2, len(player_data))
player_json_length, player_json_type = struct.unpack_from('<II', player_data, 12)
assert player_json_type == 0x4E4F534A
player_gltf = json.loads(player_data[20:20 + player_json_length])
for image in player_gltf['images']:
    assert image['extras']['resourceLocation'] == 'orimod:textures/entity/ori_pearl.png'
    image['extras']['resourceLocation'] = manifest['texture_resource']
assert player_gltf == gltf, 'Player skin/animations drifted from the validated export'
assert player_data[20 + player_json_length:] == data[20 + json_length:]
assert hashlib.sha256((root / 'src/main/resources/assets/orimod/textures/entity/ori_pearl.png').read_bytes()).hexdigest() == manifest['texture_sha256']
print(json.dumps({'file': str(path), 'vertices': vertices, 'triangles': triangles,
                  'joints': joint_count, 'palette_slots': palette_slots, 'clips': clips,
                  'sha256': manifest['export_sha256'], 'player_file': str(player_path),
                  'player_sha256': manifest['player_export_sha256'], 'result': 'passed'}, indent=2))
