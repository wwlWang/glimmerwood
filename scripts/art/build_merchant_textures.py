"""Render two original R107 merchant skins from the tracked palette source; no external assets."""
import argparse,json,struct,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def render(job):
    def pixel(x,y):
        base=job['fur'] if y<22 or y>=38 else job['cloth']
        if 34<=x<48 and y<12:base=(201,159,151)
        if 48<=x and 12<=y<18:base=(18,32,39)
        if 40<=x<48 and 14<=y<20:base=(213,220,204)
        if 22<=y<38 and (x+y)%9==0:base=job['accent']
        n=(x*17+y*13+x*y)%9-4
        return tuple(max(0,min(255,c+n)) for c in base)+(255,)
    def chunk(k,d):return struct.pack('>I',len(d))+k+d+struct.pack('>I',zlib.crc32(k+d)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for x in range(64) for c in pixel(x,y)) for y in range(64))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    source=json.loads((ROOT/'art/source/entities/glades_merchant.json').read_text())
    for i,job in enumerate(source['jobs']):
        path=ROOT/f'src/main/resources/assets/orimod/textures/entity/glades_merchant_{i}.png';data=render(job)
        if args.check:assert path.read_bytes()==data,f'Stale {path}'
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        print(('Verified ' if args.check else 'Wrote ')+str(path.relative_to(ROOT)))
