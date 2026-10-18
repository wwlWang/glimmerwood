"""Check the preserved technical sample and the currently packaged player asset."""
import hashlib
import json
import math
from pathlib import Path
import struct

ROOT=Path(__file__).resolve().parents[1]
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def validate(manifest, formal=False):
    path=ROOT/manifest['export']
    data=path.read_bytes()
    assert struct.unpack_from('<III',data)==(0x46546C67,2,len(data))
    size,kind=struct.unpack_from('<II',data,12)
    assert kind==0x4E4F534A
    gltf=json.loads(data[20:20+size])
    binary_size,kind=struct.unpack_from('<II',data,20+size)
    assert kind==0x004E4942
    binary=memoryview(data)[28+size:28+size+binary_size]
    def values(index):
        accessor=gltf['accessors'][index]
        assert 'sparse' not in accessor
        view=gltf['bufferViews'][accessor['bufferView']]
        fmt='<'+{5121:'B',5123:'H',5125:'I',5126:'f'}[accessor['componentType']]*{'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[accessor['type']]
        stride=view.get('byteStride',struct.calcsize(fmt))
        start=view.get('byteOffset',0)+accessor.get('byteOffset',0)
        return [struct.unpack_from(fmt,binary,start+i*stride) for i in range(accessor['count'])]
    assert len(gltf['meshes'])==1 and len(gltf['scenes'])==1 and len(gltf['skins'])==1
    textures=manifest['textures'] if formal else {manifest['texture_resource']:{'path':manifest['texture'],'sha256':manifest['texture_sha256']}}
    for resource,entry in textures.items():
        texture=ROOT/entry['path']
        assert texture.read_bytes().startswith(b'\x89PNG\r\n\x1a\n') and digest(texture)==entry['sha256']
        if formal:
            assert struct.unpack_from('>II',texture.read_bytes(),16)==(1024,1024)
            assert digest(ROOT/entry['packaged_path'])==entry['sha256']
    for image in gltf['images']:
        assert image['extras']['resourceLocation'] in textures
    for material in gltf['materials']:
        texture=material['pbrMetallicRoughness']['baseColorTexture']
        assert texture.get('texCoord',0)==0
        if 'emissiveTexture' in material:
            assert material['emissiveTexture'].get('texCoord',0)==0
    joint_count=len(gltf['skins'][0]['joints'])
    palette=len(gltf['nodes'])+joint_count
    assert joint_count<=64 and palette<=256
    expected=manifest['animation']['clips'] if formal else manifest['clips']
    names=[a['name'] for a in gltf['animations']]
    assert len(names)==len(set(names)) and set(names)==set(expected)
    vertices=triangles=0
    for p in gltf['meshes'][0]['primitives']:
        a=p['attributes']
        assert 'TEXCOORD_0' in a and 'TEXCOORD_1' not in a
        positions,joints,weights=(values(a[k]) for k in ('POSITION','JOINTS_0','WEIGHTS_0'))
        assert len(positions)==len(joints)==len(weights)
        for point,indices,influence in zip(positions,joints,weights):
            assert all(math.isfinite(v) for v in point+influence)
            assert abs(sum(influence)-1)<.001 and min(influence)>=0
            assert all(0<=i<joint_count for i in indices)
        indices=values(p['indices'])
        assert len(indices)%3==0 and all(0<=i[0]<len(positions) for i in indices)
        vertices+=len(positions); triangles+=len(indices)//3
    assert triangles==manifest['triangles'] and triangles<=15000
    assert digest(path)==manifest['export_sha256'] and digest(ROOT/manifest['source'])==manifest['source_sha256']
    if formal:
        assert len(gltf['materials'])==1 and len(gltf['meshes'][0]['primitives'])==1
        assert 'metallicRoughnessTexture' in gltf['materials'][0]['pbrMetallicRoughness']
        assert 'emissiveTexture' in gltf['materials'][0]
        assert digest(ROOT/manifest['blend'])==manifest['blend_sha256']
        assert digest(ROOT/manifest['player_export'])==manifest['player_export_sha256']==manifest['export_sha256']
        nodes={n.get('name') for n in gltf['nodes']}
        assert set(manifest['attachments']).issubset(nodes)
        for clip in gltf['animations']:
            metadata=expected[clip['name']]
            for channel in clip['channels']:
                sampler=clip['samplers'][channel['sampler']]
                times=[v[0] for v in values(sampler['input'])]
                samples=values(sampler['output'])
                assert all(math.isfinite(v) for sample in samples for v in sample)
                assert all(a<b for a,b in zip(times,times[1:]))
                if channel['target']['path']=='rotation':
                    assert all(abs(sum(v*v for v in q)-1)<.002 for q in samples)
                if metadata['loop']:
                    first,last=samples[0],samples[-1]
                    error=min(max(abs(a-b) for a,b in zip(first,last)),max(abs(a+b) for a,b in zip(first,last))) if channel['target']['path']=='rotation' else max(abs(a-b) for a,b in zip(first,last))
                    assert error<.002,(clip['name'],channel['target'],error)
    return {'file':str(path),'triangles':triangles,'vertices':vertices,'joints':joint_count,'palette_slots':palette,'clips':names,'sha256':digest(path),'result':'passed'}

technical=json.loads((ROOT/'art/source/deformation-sample/manifest.json').read_text(encoding='utf-8'))
results=[validate(technical)]
formal_path=ROOT/'art/source/character/manifest.json'
if formal_path.exists():
    results.append(validate(json.loads(formal_path.read_text(encoding='utf-8')),True))
else:
    assert digest(ROOT/technical['player_export'])==technical['player_export_sha256']
hands_path=ROOT/'art/source/first-person/manifest.json'
if hands_path.exists():
    hands=json.loads(hands_path.read_text(encoding='utf-8'))
    assert digest(ROOT/hands['blend'])==hands['blend_sha256']
    assert digest(ROOT/hands['derived_from'])==hands['derived_from_sha256']
    for name,entry in hands['models'].items():
        path=ROOT/entry['packaged']
        assert digest(path)==digest(ROOT/entry['source'])==entry['sha256']
        data=path.read_bytes();size=struct.unpack_from('<I',data,12)[0]
        gltf=json.loads(data[20:20+size]);binary=memoryview(data)[28+size:]
        assert len(gltf['meshes'])==1 and len(gltf['nodes'])==1 and not gltf.get('skins')
        assert len(gltf['materials'])==1 and not gltf.get('animations')
        primitive=gltf['meshes'][0]['primitives'][0]
        assert 'TEXCOORD_1' not in primitive['attributes']
        position=gltf['accessors'][primitive['attributes']['POSITION']]
        view=gltf['bufferViews'][position['bufferView']]
        for i in range(position['count']):
            xyz=struct.unpack_from('<fff',binary,view.get('byteOffset',0)+position.get('byteOffset',0)+i*view.get('byteStride',12))
            assert all(math.isfinite(v) and abs(v)<1 for v in xyz),(name,xyz)
        triangles=gltf['accessors'][primitive['indices']]['count']//3
        assert triangles==entry['triangles'] and triangles<1800
        results.append({'file':str(path),'triangles':triangles,'result':'passed','sha256':entry['sha256']})
print(json.dumps(results,indent=2))
