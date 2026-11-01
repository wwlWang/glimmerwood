"""Small, reproducible v2 deltas over frozen v1 structures; never writes the inputs.
Run with the project's art Python (nbtlib). --check compares generated products.
"""
from pathlib import Path
import argparse, gzip, hashlib, io, json
import nbtlib as n
ROOT = Path(__file__).resolve().parents[2]

def build(kind):
    source = ROOT / f'src/main/resources/data/orimod/structure/ruins/{kind}_v1.nbt'
    data = n.load(source)
    palette = data['palette']; blocks = {tuple(map(int,b['pos'])): b for b in data['blocks']}
    changes = []
    def put(at, name, properties=None):
        state = n.Compound({'Name': n.String(name)})
        if properties: state['Properties'] = n.Compound({k:n.String(v) for k,v in properties.items()})
        index = next((i for i,s in enumerate(palette) if s == state), None)
        if index is None: index=len(palette); palette.append(state)
        old=blocks.get(at)
        if old is not None and int(old['state']) == index: return
        changes.append({'position':at, 'before':str(palette[int(old['state'])]['Name']) if old else 'minecraft:structure_void', 'after':name})
        blocks[at]=n.Compound({'pos':n.List[n.Int](at),'state':n.Int(index)})
    layout=json.loads((ROOT/f'src/main/resources/orimod/ruin_layout/{kind}_v1.json').read_text())
    if kind == 'forest_spring':
        rooms=[(2,35,10,41),(29,36,37,45)]
        for x0,z0,x1,z1 in rooms:
            for x in range(x0,x1+1):
                for z in range(z0,z1+1):
                    put((x,12,z),'minecraft:mossy_stone_bricks' if (x*7+z*3)%5 else 'minecraft:cracked_stone_bricks')
                    if x in (x0,x1) or z in (z0,z1):
                        for y in range(9,12): put((x,y,z),'minecraft:mossy_stone_bricks')
        layout['sealed_reward_roofs']=[{'from':[a,12,b],'to':[c,12,d]} for a,b,c,d in rooms]
    else:
        for x in range(10,13):
            at=(x,12,29)
            assert str(palette[int(blocks[at]['state'])]['Name'])=='orimod:great_spirit_root'
            put(at,'minecraft:air')
        # Rejoin the existing x=8 support from its side/rear, leaving the whole central launch line open.
        for at in [(9,12,29),(9,12,30),(9,13,30),(10,13,30),(11,13,30),(11,13,29)]:
            put(at,'orimod:great_spirit_root',{'light':'none'})
        layout['clearances'].append({'from':[12,12,29],'to':[12,12,29]})
    data['blocks']=n.List[n.Compound]([blocks[p] for p in sorted(blocks)])
    out=io.BytesIO();data.write(out);raw=gzip.compress(out.getvalue(),mtime=0)
    manifest={'generator':'scripts/art/build_p5k_ruins_v2.py','layoutVersion':2,
        'base_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sha256':hashlib.sha256(raw).hexdigest(),
        'changed_blocks':changes,'old_versions_unchanged':True}
    return {f'src/main/resources/data/orimod/structure/ruins/{kind}_v2.nbt':raw,
        f'src/main/resources/orimod/ruin_layout/{kind}_v2.json':(json.dumps(layout,ensure_ascii=False,indent=2)+'\n').encode(),
        f'art/source/{kind}/'+('v005' if kind=='creek_ruins' else 'v003')+'/manifest.json':(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode()}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    for kind in ['forest_spring','creek_ruins']:
        for name,data in build(kind).items():
            path=ROOT/name
            # Guard frozen inputs and all previously committed products against an art-version collision.
            import subprocess
            tracked=subprocess.run(['git','cat-file','-e','c3bd64e:'+name],cwd=ROOT,capture_output=True).returncode==0
            assert not tracked, 'Refusing to overwrite baseline product: '+name
            if args.check: assert path.read_bytes()==data,name
            else: path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            print(name,len(data))
