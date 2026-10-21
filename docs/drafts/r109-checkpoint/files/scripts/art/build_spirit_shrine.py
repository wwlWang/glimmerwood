"""R109 original circular arena and its reserved, closed ward shell. --check is read-only."""
import argparse, io, json, math
from pathlib import Path
import nbtlib as nbt
import struct, zlib
from build_ruins import Template, compressed_nbt
ROOT=Path(__file__).resolve().parents[2]
SIZE=(25,14,25)
def disk(x,z,r): return (x-12)**2+(z-12)**2<=r*r
def ward(x,y,z):
    # Wider underground chamber lets the entry step cross above the lower shell.
    if y==1: return disk(x,z,12)
    if y in (2,3): return disk(x,z,12) and not disk(x,z,11)
    if y==13: return disk(x,z,11)
    return 4<=y<=12 and disk(x,z,11) and not disk(x,z,10)
WARD=[(x,y,z) for y in range(14) for z in range(25) for x in range(25) if ward(x,y,z)]
SPAWNS=[(x,4,z) for z in range(5,20,2) for x in range(5,20,2) if 25<=(x-12)**2+(z-12)**2<=64 and not (x in range(6,9) and z in range(9,12)) and not (x in range(15,18) and z in range(12,15))]
LAYOUT=dict(size=SIZE,entry=[12,5,0],altar=[12,4,12],wards=WARD,spawns=SPAWNS)
def png(pixels):
    def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(sum(row,())) for row in pixels)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
def build():
    t=Template(ROOT,'spirit_shrine',SIZE)
    t.box((0,0,0),(24,0,24),'stone_bricks');t.box((0,1,0),(24,13,24),'air')
    for z in range(25):
        for x in range(25):
            r=(x-12)**2+(z-12)**2
            if r<=121:
                t.put(x,2,z,'stone_bricks')
                t.put(x,3,z,'chiseled_stone_bricks' if 72<=r<=85 or x==12 or z==12 else 'mossy_stone_bricks' if (x*17+z*31)%13==0 else 'stone_bricks')
            elif x in (0,24) or z in (0,24):
                t.box((x,1,z),(x,6,z),'mossy_stone_bricks')
    # Single broad entrance descends into the arena; outside walls keep other entries distinct.
    t.box((10,5,0),(14,9,0),'air');t.box((10,4,0),(14,4,0),'stone_bricks')
    for x,z in [(6,6),(18,6),(4,12),(20,12),(6,18),(18,18)]:
        t.box((x,4,z),(x,9,z),'orimod:spirit_log',axis='y');t.put(x,10,z,'orimod:spirit_leaves',bloomed='false')
    for x,z in [(7,10),(16,13)]:t.box((x,4,z),(x+1,4,z),'polished_andesite')
    t.put(12,4,12,'orimod:spirit_shrine_altar',completed='false')
    for p in WARD:t.put(*p,'air')
    palette=[];indexes={};blocks=[]
    for pos,state in sorted(t.blocks.items(),key=lambda e:(e[0][1],e[0][2],e[0][0])):
        if state not in indexes:
            indexes[state]=len(palette);v={'Name':nbt.String(state[0])}
            if state[1]:v['Properties']=nbt.Compound({k:nbt.String(val) for k,val in state[1]})
            palette.append(nbt.Compound(v))
        blocks.append(nbt.Compound({'pos':nbt.List[nbt.Int](pos),'state':nbt.Int(indexes[state])}))
    f=nbt.File({'DataVersion':nbt.Int(3955),'size':nbt.List[nbt.Int](SIZE),'palette':nbt.List[nbt.Compound](palette),'blocks':nbt.List[nbt.Compound](blocks),'entities':nbt.List[nbt.Compound]()})
    stream=io.BytesIO();f.write(stream)
    out={'data/orimod/structure/spirit_shrine.nbt':compressed_nbt(stream.getvalue()),'../orimod/spirit_shrine.json':(json.dumps(LAYOUT,indent=2)+'\n').encode()}
    # Original 16px icy-violet lattice; alpha makes a legible veil, not an opaque wall.
    for name in ('shrine_ward','spirit_shrine_altar','spirit_shrine_altar_completed'):
        im=[[None]*16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                if name=='shrine_ward':
                    edge=x in (0,15) or y in (0,15);rune=abs(x-7)+abs(y-7) in (4,5)
                    color=(195,177,255,180) if edge or rune else (96,64,170,35)
                else:
                    vein=x in (2,13) or y in (2,13) or abs(x-7)+abs(y-7)==5
                    lit=name.endswith('completed');v=10*((x*17+y*29)%3)
                    color=(175,240,255,255) if vein and lit else (152,100,212,255) if vein else (44+v,51+v,69+v,255)
                im[y][x]=color
        out[f'assets/orimod/textures/block/{name}.png']=png(im)
    def js(path,obj):out[path]=(json.dumps(obj,indent=2)+'\n').encode()
    for name in ('shrine_ward','spirit_shrine_altar','spirit_shrine_altar_completed'):
        model={'parent':'minecraft:block/cube_all','textures':{'all':'orimod:block/'+name}}
        if name=='shrine_ward':model['render_type']='minecraft:translucent'
        js(f'assets/orimod/models/block/{name}.json',model)
    js('assets/orimod/blockstates/shrine_ward.json',{'variants':{'':{'model':'orimod:block/shrine_ward'}}})
    js('assets/orimod/blockstates/spirit_shrine_altar.json',{'variants':{f'completed={str(v).lower()}':{'model':'orimod:block/spirit_shrine_altar'+('_completed' if v else '')} for v in (False,True)}})
    for name in ('shrine_ward','spirit_shrine_altar'):js(f'assets/orimod/models/item/{name}.json',{'parent':'orimod:block/'+name})
    return {('src/main/resources/orimod/spirit_shrine.json' if k.startswith('../') else 'src/main/resources/'+k):v for k,v in out.items()}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    for name,data in build().items():
        path=ROOT/name
        if args.check:assert path.exists() and path.read_bytes()==data,f'Generated asset changed: {name}'
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        print(('Verified ' if args.check else 'Wrote ')+name)
