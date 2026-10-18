"""Package one explicit character revision, preserving the previous player GLB."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT=Path(__file__).resolve().parents[2]
source=ROOT/sys.argv[1]
manifest=json.loads(source.read_text(encoding='utf-8'))
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
export=ROOT/manifest['export']
assert digest(export)==manifest['export_sha256']
assert digest(ROOT/manifest['source'])==manifest['source_sha256']
assert digest(ROOT/manifest['blend'])==manifest['blend_sha256']
target=ROOT/'src/main/resources/assets/orimod/models/entity/ori.glb'
if target.exists() and digest(target)!=manifest['export_sha256']:
    previous=digest(target)
    backup=ROOT/'art/source/character/replaced'/(previous+'.glb')
    backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():
        shutil.copy2(target,backup)
    assert digest(backup)==previous
target.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(export,target)
manifest['player_export']=str(target.relative_to(ROOT).as_posix())
manifest['player_export_sha256']=digest(target)
for resource,entry in manifest['textures'].items():
    assert digest(ROOT/entry['path'])==entry['sha256']
    namespace,path=resource.split(':',1)
    destination=ROOT/'src/main/resources/assets'/namespace/path
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(ROOT/entry['path'],destination)
    entry['packaged_path']=str(destination.relative_to(ROOT).as_posix())
(ROOT/'art/source/character/manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'player':str(target),'sha256':digest(target),'revision':manifest['revision']}))
