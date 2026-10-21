"""R104: original 16x16 light-platform textures and model variants; --check is read-only."""
import argparse
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path('src/main/resources/assets/orimod')


def png(mode):
    def pixel(x, y):
        edge = min(x, y, 15-x, 15-y)
        rune = (x in (4, 11) and 4 <= y <= 11) or (y in (4, 11) and 4 <= x <= 11)
        if mode == 'ghost':
            return (121, 204, 238, 115 if edge == 0 else 55 if edge == 1 else 35 if rune else 0)
        shade = ((x*11+y*7) % 9) - 4
        base = (211, 239, 247) if mode == 'lit' else (104, 165, 193)
        if edge <= 1 or rune:
            base = (234, 252, 255) if mode == 'lit' else (164, 226, 248)
        return (*[min(255, c+shade) for c in base], 255)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind+data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for x in range(16) for c in pixel(x,y)) for y in range(16))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0)) + chunk(b'IDAT',zlib.compress(raw,9)) + chunk(b'IEND',b'')


def assets():
    files = {}
    def js(path,value): files[path]=(json.dumps(value,indent=2)+'\n').encode()
    for mode in ('ghost','lit','blink'):
        name='spirit_platform_'+mode
        files[ASSETS/'textures/block'/(name+'.png')] = png(mode)
        js(ASSETS/'models/block'/(name+'.json'),{'parent':'minecraft:block/cube_all',
            'render_type':'minecraft:translucent' if mode=='ghost' else 'minecraft:solid',
            'textures':{'all':'orimod:block/'+name}})
    js(ASSETS/'blockstates/spirit_platform.json',{'variants':{
        'lit=false':{'model':'orimod:block/spirit_platform_ghost'},
        'lit=true,blink=false':{'model':'orimod:block/spirit_platform_lit'},
        'lit=true,blink=true':{'model':'orimod:block/spirit_platform_blink'}}})
    js(ASSETS/'models/item/spirit_platform.json',{'parent':'orimod:block/spirit_platform_lit'})
    js(Path('src/main/resources/data/orimod/loot_table/blocks/spirit_platform.json'),{
        'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'orimod:spirit_platform'}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    return files


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    for relative,data in assets().items():
        path=ROOT/relative
        if args.check:
            if not path.exists() or path.read_bytes()!=data: raise SystemExit(f'Stale R104 asset: {relative}')
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        print(f'{"Verified" if args.check else "Generated"} {relative}')


if __name__=='__main__': main()
