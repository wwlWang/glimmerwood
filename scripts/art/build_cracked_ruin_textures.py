"""R103: original 16x16 etched stone and faceted Spirit Light crystal. Standard library only."""
import argparse
import json
import math
from pathlib import Path
from build_lantern_textures import png

ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path("src/main/resources/assets/orimod")


def segment(x, y, a, b):
    dx, dy = b[0]-a[0], b[1]-a[1]
    t = max(0, min(1, ((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)))
    return math.hypot(x-a[0]-t*dx, y-a[1]-t*dy)


def stone(x, y, floor=False):
    lines = ([(8,8),(2,1)],[(8,8),(14,2)],[(8,8),(15,12)],[(8,8),(5,15)],[(8,8),(0,10)]) if floor else (
        [(8,0),(6,5)],[(6,5),(10,9)],[(10,9),(8,15)],[(6,5),(1,7)],[(10,9),(14,6)])
    d = min(segment(x,y,*line) for line in lines)
    if d < .46: return (190,237,255)
    if d < 1.15: return (69,124,156)
    seam = y % 8 == 0 or (x+(8 if y//8 % 2 else 0)) % 16 == 0
    shade = ((x*17+y*31+x*y*3)%13)-6-(15 if seam else 0)
    return tuple(c+shade for c in (62,69,78))


def vessel(x,y,top=False):
    edge = min(x,y,15-x,15-y) if top else min(x,15-x)
    if top and edge < 3 or not top and y in (0,1,14,15): return (47,76,106)
    facet = (x*7+y*3)%9
    glow = max(0,1-math.hypot(x-7.5,y-7.5)/9)
    return tuple(round(a+(b-a)*glow)+facet for a,b in zip((64,135,181),(214,240,246)))


def assets():
    files = {}
    for name, pixel in {"cracked_ruin_wall":stone,"cracked_ruin_floor":lambda x,y:stone(x,y,True),
                        "spirit_light_vessel":vessel,"spirit_light_vessel_top":lambda x,y:vessel(x,y,True)}.items():
        files[ASSETS/"textures/block"/(name+".png")] = png(pixel)
    def js(path,value): files[path]=(json.dumps(value,indent=2)+"\n").encode()
    for name in ("cracked_ruin_wall","cracked_ruin_floor","spirit_light_vessel"):
        js(ASSETS/"blockstates"/(name+".json"),{"variants":{"":{"model":"orimod:block/"+name}}})
        js(ASSETS/"models/item"/(name+".json"),{"parent":"orimod:block/"+name})
        js(Path("src/main/resources/data/orimod/loot_table/blocks")/(name+".json"),{"type":"minecraft:block","pools":[]})
        if name != "spirit_light_vessel":
            js(ASSETS/"models/block"/(name+".json"),{"parent":"minecraft:block/cube_all","textures":{"all":"orimod:block/"+name}})
        else:
            def element(a,b):
                return {"from":a,"to":b,"faces":{face:{"texture":"#top" if face in ("up","down") else "#side"} for face in ("up","down","north","south","east","west")}}
            js(ASSETS/"models/block"/(name+".json"),{"parent":"minecraft:block/block","textures":{
                "particle":"orimod:block/"+name,"side":"orimod:block/"+name,"top":"orimod:block/spirit_light_vessel_top"},
                "elements":[element([3,0,3],[13,2,13]),element([4,2,4],[12,8,12]),element([5,8,5],[11,10,11])]})
    return files


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--check",action="store_true");args=parser.parse_args()
    for relative,data in assets().items():
        path=ROOT/relative
        if args.check:
            if not path.exists() or path.read_bytes()!=data: raise SystemExit(f"Stale R103 asset: {relative}")
        else: path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        print(f'{"Verified" if args.check else "Generated"} {relative}')


if __name__ == "__main__": main()
