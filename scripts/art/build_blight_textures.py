"""R106 original 16x16 blight textures; standard library only, --check is read-only."""
from pathlib import Path
import argparse
import json
import math
import struct
import zlib
OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/orimod/textures/block'

def png(pixel, frames=1):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for x in range(16) for c in pixel(x,y)) for y in range(16*frames))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR',struct.pack('>IIBBBBB',16,16*frames,8,6,0,0,0)) + chunk(b'IDAT',zlib.compress(raw,9)) + chunk(b'IEND',b'')

def pixel(kind,x,y,damage=0):
    grain = (x*13+y*31+x*y*3)%19-9
    if kind=='soil':
        stain = ((x-4)**2+(y-6)**2<13 or (x-12)**2+(y-13)**2<10)
        rgb = (54,29,64) if stain else (43,34,42)
    elif kind=='thorn':
        stem = abs(x-(6+round(2*math.sin(y*.6))))<=1 or abs(x-(11-round(y*.2)))<=1
        spike = (y in (3,7,11) and 1<=x<=12 and (x+y)%3!=0)
        if not (stem or spike): return (0,0,0,0)
        rgb = (101,53,128) if spike else (49,29,65)
    elif kind=='log':
        groove = (x+(y//5)%2)%5==0
        rgb = (34,29,36) if groove else (65,57,64)
    elif kind=='end':
        ring = int(math.hypot(x-7.5,y-7.5))%3==0
        rgb = (37,27,43) if ring else (79,65,79)
    else:
        facet = x+y<15
        rgb = (75,33,109) if facet else (46,21,69)
        if abs(x-7)+(y//4)%2<=1: rgb=(121,61,159)
        crack = any(abs(x-((y*(i+2)//3+3*i)%14+1))==0 and y>=2+i for i in range(damage))
        if crack: rgb=(205,151,235)
    return tuple(max(0,min(255,c+grain)) for c in rgb)+(255,)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    textures={'blighted_soil':('soil',0),'gloom_thorns':('thorn',0),'withered_spirit_log':('log',0),'withered_spirit_log_top':('end',0)}
    textures.update({f'gloom_core_{i}':('core',i) for i in range(5)})
    for name,(kind,damage) in textures.items():
        path=OUT/(name+'.png')
        frames=12 if kind=='core' else 1
        def animated(x,y):
            rgba=pixel(kind,x,y%16,damage)
            if frames==1: return rgba
            pulse=.85+.2*math.sin(2*math.pi*(y//16)/frames)
            return tuple(round(min(255,c*pulse)) for c in rgba[:3])+(rgba[3],)
        data=png(animated,frames)
        if args.check:
            if not path.exists() or path.read_bytes()!=data: raise SystemExit(f'Stale blight texture: {path}')
        else: OUT.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        print(f'{"Verified" if args.check else "Generated"} {path.name}')
        if frames>1:
            metadata=path.with_suffix('.png.mcmeta');text=json.dumps({'animation':{'frametime':4,'interpolate':True}},indent=2)+'\n'
            if args.check:
                if not metadata.exists() or metadata.read_text()!=text: raise SystemExit(f'Stale animation: {metadata}')
            else: metadata.write_text(text)
if __name__=='__main__':main()
