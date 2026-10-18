"""Package the current skinned source for the player adapter, with its own resource namespace."""
import hashlib
import json
from pathlib import Path
import shutil
import struct

root = Path(__file__).resolve().parents[2]
if (root / 'art/source/character/manifest.json').exists():
    raise SystemExit('A character revision is packaged. Use promote_character.py; the technical sample must not overwrite it.')
manifest_path = root / 'art/source/deformation-sample/manifest.json'
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
source = root / manifest['export']
data = source.read_bytes()
size = struct.unpack_from('<I', data, 12)[0]
gltf = json.loads(data[20:20 + size])
for image in gltf['images']:
    image['extras']['resourceLocation'] = 'orimod:textures/entity/ori_pearl.png'
encoded = json.dumps(gltf, ensure_ascii=False, separators=(',', ':')).encode()
encoded += b' ' * (-len(encoded) % 4)
chunks = data[20 + size:]
target = root / 'src/main/resources/assets/orimod/models/entity/ori.glb'
texture = root / 'src/main/resources/assets/orimod/textures/entity/ori_pearl.png'
target.parent.mkdir(parents=True, exist_ok=True)
texture.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(struct.pack('<III', 0x46546C67, 2, 20 + len(encoded) + len(chunks))
    + struct.pack('<II', len(encoded), 0x4E4F534A) + encoded + chunks)
shutil.copy2(root / manifest['texture'], texture)
manifest['player_export'] = str(target.relative_to(root))
manifest['player_export_sha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
manifest['player_asset_status'] = 'Temporary deformation sample; final model and animation acceptance pending'
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(target)
