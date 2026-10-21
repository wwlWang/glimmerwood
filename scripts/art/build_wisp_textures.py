#!/usr/bin/env python3
"""R108 original 16px heart, feather and root wisps; deterministic RGBA icons and uncollected silhouettes."""
import argparse
import math
from pathlib import Path
import struct
import zlib
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/orimod/textures/gui'
COLORS = {'heart': (255,213,138), 'wing': (198,160,255), 'earth': (117,232,176)}

def png(kind, missing):
    def pixel(x,y):
        dx,dy=x-7.5,y-7.5
        if kind=='heart': inside=(abs(dx)/5.4+abs(dy+1)/6<1) or (dy<0 and ((dx-2.4)**2+(dy+2)**2<8 or (dx+2.4)**2+(dy+2)**2<8))
        elif kind=='wing': inside=abs(dx+dy*.6)<(4-abs(dy)*.4) and abs(dy)<6 and (int(y)%3!=0 or abs(dx+dy*.6)<1.5)
        else: inside=abs(dx)<1 and abs(dy)<6 or dy>0 and abs(abs(dx)-dy*.8)<1.2 or dy<-1 and abs(abs(dx)+dy*.7)<1.5
        if not inside: return (0,0,0,0)
        if missing: return (55,76,86,230)
        pulse=max(0,1-math.hypot(dx,dy)/7)
        return tuple(min(255,int(c+(255-c)*pulse)) for c in COLORS[kind])+(255,)
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for x in range(16) for c in pixel(x,y)) for y in range(16))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    for kind in COLORS:
        for missing in (False,True):
            p=OUT/f'wisp_{kind}{"_missing" if missing else ""}.png';data=png(kind,missing)
            if args.check:
                if not p.exists() or p.read_bytes()!=data: raise SystemExit(f'Stale wisp icon: {p}')
            else: OUT.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    print('6 wisp icons '+('verified' if args.check else 'generated'))
if __name__=='__main__': main()
