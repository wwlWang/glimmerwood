"""Package one explicit creature revision into the game resources (GLB + its three textures)."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
source = ROOT / sys.argv[1]
manifest = json.loads(source.read_text(encoding='utf-8'))
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
export = ROOT / manifest['export']
assert digest(export) == manifest['export_sha256']
target = ROOT / 'src/main/resources/assets/orimod/models/entity' / (manifest['creature'] + '.glb')
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(export, target)
for resource, entry in manifest['textures'].items():
    assert digest(ROOT / entry['path']) == entry['sha256']
    namespace, path = resource.split(':', 1)
    destination = ROOT / 'src/main/resources/assets' / namespace / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / entry['path'], destination)
promoted = ROOT / 'art/source/monsters' / manifest['creature'] / 'manifest.json'
promoted.write_text(json.dumps(dict(manifest, packaged=target.relative_to(ROOT).as_posix()), indent=2) + '\n', encoding='utf-8')
print(json.dumps({'creature': manifest['creature'], 'revision': manifest['revision'], 'sha256': digest(target)}))
