"""R105 original 64x64 beetle skin: fractured violet shell, dark belly and jointed legs."""
import argparse
import struct
import zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
TARGET=ROOT/'src/main/resources/assets/orimod/textures/entity/gloom_beetle.png'

def texture():
    def pixel(x,y):
        grain=(x*13+y*7+x*y*3)%11-5
        if y<24:
            crack=(x+(y//3)*2)%17 in (0,1) or (y+x//5)%19==0
            base=(174,81,222) if crack else (51,35,68)
            if x>=48 and y<6:base=(232,142,255)  # small paired eyes
        else:
            base=(24,20,32) if y<40 else (41,29,47)
        return (*[max(0,min(255,c+grain)) for c in base],255)
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for x in range(64) for c in pixel(x,y)) for y in range(64))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();data=texture()
    if args.check:
        if not TARGET.exists() or TARGET.read_bytes()!=data:raise SystemExit('Stale R105 beetle texture')
    else:TARGET.parent.mkdir(parents=True,exist_ok=True);TARGET.write_bytes(data)
    print(('Verified ' if args.check else 'Generated ')+str(TARGET.relative_to(ROOT)))

if __name__=='__main__':main()
