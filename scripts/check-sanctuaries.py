#!/usr/bin/env python3
"""Verify packaged R115 GLBs/textures against the retained original Blender source exports (read-only)."""
import hashlib
import json
import struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'art/source/sanctuaries/v002'
manifest=json.loads((SOURCE/'manifest.json').read_text())
for asset in manifest['assets']:
    assert (SOURCE/(asset['name']+'.blend')).is_file(), asset['name']
    for name,record in asset['files'].items():
        source=(SOURCE/name).read_bytes();package=(ROOT/record['package']).read_bytes()
        assert source==package and hashlib.sha256(package).hexdigest()==record['sha256'],name
        if name.endswith('.png'):
            assert struct.unpack_from('>II',source,16)==(512,64),name
        if name.endswith('.glb'):
            size=struct.unpack_from('<I',source,12)[0];gltf=json.loads(source[20:20+size])
            assert all(s['magFilter']==9728 and s['minFilter']==9728 for s in gltf['samplers']),name
            assert all('TEXCOORD_1' not in p['attributes'] for m in gltf['meshes'] for p in m['primitives']),name
            assert all('matrix' not in n and 'rotation' not in n and 'translation' not in n for n in gltf['nodes']),name
            assert all(i['extras']['resourceLocation'].startswith('orimod:textures/sanctuary/') for i in gltf['images']),name
print(f"Sanctuaries: {len(manifest['assets'])} original sources and packaged GLB/PBR assets passed")
