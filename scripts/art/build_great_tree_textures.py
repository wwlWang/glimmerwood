#!/usr/bin/env python3
"""Original 16-texel bark, masked veins and opaque teal leaf backing."""
from pathlib import Path
import argparse,hashlib,json,math,struct,zlib
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/orimod/textures/block'
SOURCE=ROOT/'art/source/great_spirit_tree/v004'

def noise(x,y):return ((x*374761393 ^ y*668265263)*1274126177&0xffffffff)/0xffffffff
def bark(x,y):
    grain=7*math.sin(x*1.6+.4*math.sin(y*.4))+(noise(x,y)-.5)*10
    if (x+y//6)%7==0:grain-=9
    return tuple(round(v+grain) for v in (64,59,54))+(255,)
def vein(x,y):
    d=min(abs(x-7.5-1.2*math.sin(y*math.pi/8)),abs(y-7.5-1.2*math.sin(x*math.pi/8)))
    if d<.65:return (200,245,255,255)
    if d<1.5:return (68,174,216,255)
    return (0,0,0,0)
def leaf_core(x,y,bloomed=False):
    # Small overlapping diagonal leaf blades, carved midribs; opaque teal under every cutout hole.
    u=(x+2*(y//4%2))%4;v=y%4
    blade=abs((u-1.5)*.9+(v-1.5)*.25)+abs((v-1.5)*.7)<1.7
    grain=(noise(x+31,y+17)-.5)*10+(12 if blade else -7)
    if u==v:grain-=8
    base=(80,207,219) if bloomed else (94,177,204)
    return tuple(round(c+grain) for c in base)+(255,)
def png(pixel):
    def chunk(k,v):return struct.pack('!I',len(v))+k+v+struct.pack('!I',zlib.crc32(k+v)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for x in range(16) for c in pixel(x,y)) for y in range(16))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')

p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
files={OUT/'great_spirit_bark.png':png(bark),OUT/'great_spirit_vein.png':png(vein),OUT/'great_tree_leaf_core.png':png(leaf_core),OUT/'great_tree_leaf_core_bloomed.png':png(lambda x,y:leaf_core(x,y,True))}
for name,data in list(files.items()):files[SOURCE/name.name]=data
manifest={'generator':'scripts/art/build_great_tree_textures.py','size':[16,16],'filter':'nearest','emission':'bark retains world light; masked vein overlay; dense leaf backing is opaque and fullbright like the cutout skin','assets':{x.relative_to(ROOT).as_posix():hashlib.sha256(b).hexdigest() for x,b in files.items()}}
files[SOURCE/'textures.json']=(json.dumps(manifest,indent=2)+'\n').encode()
for path,data in files.items():
    if args.check:assert path.read_bytes()==data,f'Stale {path}'
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'Great tree textures: {len(files)} files, written={0 if args.check else len(files)}')
