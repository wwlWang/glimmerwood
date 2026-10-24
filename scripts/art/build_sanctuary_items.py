#!/usr/bin/env python3
"""Deterministic architecture item models; --check is read-only."""
import argparse, hashlib, json
from pathlib import Path
from sanctuary_items import PARTICLES, model
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'art/source/sanctuary_items/v001'
def enc(value):return (json.dumps(value,indent=2)+'\n').encode()
files={ROOT/f'src/main/resources/assets/orimod/models/item/{name}.json':enc(model(name)) for name in PARTICLES}
files.update({SOURCE/path.name:data for path,data in list(files.items())})
files[SOURCE/'manifest.json']=enc({'version':1,'generator':'scripts/art/build_sanctuary_items.py',
    'renderer':'SanctuaryItemRenderer -> SanctuaryRenderer.assembly, shared with placed cores',
    'files':{str(p.relative_to(ROOT)):hashlib.sha256(v).hexdigest() for p,v in files.items()}})
ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');args=ap.parse_args()
for path,data in files.items():
    if args.check:assert path.exists() and path.read_bytes()==data,f'Stale {path}'
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'Sanctuary items: {len(files)} files, written={0 if args.check else len(files)}')
