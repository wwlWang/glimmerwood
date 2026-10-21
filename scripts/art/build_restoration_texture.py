#!/usr/bin/env python3
"""Original broad restoration-column falloff, uniform along its height (vertex alpha supplies the upward fade)."""
import argparse
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/orimod/textures/misc/restoration_column.png'

def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data))+kind+data+struct.pack('>I', zlib.crc32(kind+data)&0xffffffff)
    def pixel(x):
        distance=abs((x+.5)/64*2-1)
        t=max(0,min(1,(distance-.22)/.78))
        return (255,255,255,round(255*(1-t*t*(3-2*t))))
    raw=b''.join(b'\0'+bytes(c for x in range(64) for c in pixel(x)) for _ in range(8))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,8,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    data=png()
    if args.check:
        if not OUT.exists() or OUT.read_bytes()!=data: raise SystemExit('Stale restoration column texture')
    else:
        OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_bytes(data)
    print('Restoration column '+('verified' if args.check else 'generated'))
