"""Restore optional development libraries from the locked upstream URLs (Python 3)."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
lock = json.loads((ROOT / 'dependencies/lock.json').read_text(encoding='utf-8'))

def digest(path):
    value = hashlib.sha512()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()

for artifact in lock['artifacts'] + lock['sourceBuilds']:
    name = artifact['filename']
    if Path(name).name != name or '/' in name or '\\' in name:
        raise ValueError('Invalid artifact filename')
    destination = ROOT / 'libs' / artifact.get('kind', 'runtime') / name
    if not destination.exists():
        if 'url' not in artifact:
            raise RuntimeError(f'Missing {name}: run git lfs pull to restore the locked build')
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_name(name + '.partial')
        request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'OriMod private development'})
        with urllib.request.urlopen(request, timeout=60) as response, temporary.open('wb') as target:
            while block := response.read(1024 * 1024):
                target.write(block)
        if digest(temporary) != artifact['sha512']:
            raise RuntimeError(f'Download checksum mismatch: {name}')
        temporary.replace(destination)
    if digest(destination) != artifact['sha512']:
        raise RuntimeError(f'Existing dependency differs from lock: {name}; preserve it before restoring')
    print(f'Verified {name}')
