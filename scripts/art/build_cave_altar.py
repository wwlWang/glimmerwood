"""Cave altar LayoutVersion 1: carved rock vaults, luminous roots and separate sanctuaries.

Never overwrites the LayoutVersion 0 generator/resources. Deterministic nbtlib 2.0.4.
--check compares package/source products without modifying them.
"""
import argparse, hashlib, io, json, math, tempfile
from pathlib import Path
import nbtlib as nbt
from build_ruins import Template, compressed_nbt
ROOT=Path(__file__).resolve().parents[2]
SIZE=(47,32,71)
SHRINE=(10,6,29); MAP=(36,6,29); ALTAR=(23,7,60); DOOR=(15,6,44)
ROOMS=[(23,28,12,10,13),(10,29,8,8,12),(36,29,8,8,12),
       (23,44,11,13,17),(36,44,8,9,18),(23,59,18,10,23)]

def noise(x,y,z): return ((x*734287+y*912931+z*193939)^((x+z)*833))%29

def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')

def add_roots(t,air):
    def protected(p):
        x,y,z=p
        return (22<=x<=24 and 26<=z<=54 and 6<=y<=8) or (5<=x<=11 and 34<=z<=36 and 7<=y<=12) or (33<=x<=41 and 39<=z<=47 and y<=14) or (13<=x<=15 and 28<=z<=30 and y<=8)
    specs=[(4,29,6,28),(15,23,16,24),(32,22,31,24),(42,30,40,31),
           (32,38,31,39),(12,50,14,51),(7,60,10,62),(39,59,36,62)]
    records=[];knots=[]
    for x,z,ex,ez in specs:
        top=max(y for (xx,y,zz),state in t.blocks.items() if xx==ex and zz==ez and state[0]=='minecraft:air')+1
        path=[(x,5,z)];q=[x,5,z]
        for y in range(6,top+1):
            f=(y-5)/(top-5);target=(round(x+(ex-x)*f*f),y,round(z+(ez-z)*f*f))
            for axis in (1,0,2):
                while q[axis]!=target[axis]:
                    q[axis]+=1 if target[axis]>q[axis] else -1;path.append(tuple(q))
        base={(x+dx,y,z+dz) for dx in (0,1) for dz in (0,1) for y in range(5,8)}
        if any(protected(p) for p in set(path)|base):continue
        for p in sorted(base):t.put(*p,'orimod:great_spirit_root',light='none')
        for i,p in enumerate(path):
            if i>=len(path)-3 and p[1]<top:t.put(*p,'dark_oak_fence',north='false',south='false',east='false',west='false',waterlogged='false')
            else:t.put(*p,'orimod:great_spirit_root',light='none')
        own=[]
        for index in (4,11):
            if index>=len(path)-3:continue
            p=path[index]
            if all(sum((p[i]-o[i])**2 for i in range(3))>=16 for o in knots):
                t.put(*p,'orimod:great_spirit_root',light='knot');knots.append(p);own.append(p)
        records.append(dict(path=path,base=sorted(base),knots=own))
    return records

def add_lighting(t,descent):
    # Four small hanging berry-moss tips illuminate each focus without additional root columns.
    # These are real vanilla light sources, attached all the way to the authored vault.
    vines=[]
    for cx,cy,cz in (SHRINE,MAP,ALTAR,DOOR):
        for dx,dz in ((-2,-2),(-2,2),(2,-2),(2,2)):
            x,z=cx+dx,cz+dz;y=10
            if not 0<=x<47 or not 0<=z<71:continue
            if t.blocks.get((x,y,z),('',()))[0]!='minecraft:air':continue
            end=y
            while end<31 and t.blocks.get((x,end,z),('',()))[0]=='minecraft:air':end+=1
            if end==31:continue
            for yy in range(y+1,end):t.put(x,yy,z,'cave_vines_plant',berries='false')
            t.put(x,y,z,'cave_vines',berries='true',age='25');vines.append((x,y,z))
    # Two low moss tips flank the launch pad, outside the actual grapple ray.
    # They light the entire three-block approach even after all vessels are broken.
    for x,y,z in ((23,12,29),(23,12,42),(23,12,52),(34,8,40),(38,8,40)):
        end=y
        while end<31 and t.blocks.get((x,end,z),('',()))[0]=='minecraft:air':end+=1
        assert end<31
        for yy in range(y+1,end):t.put(x,yy,z,'cave_vines_plant',berries='false')
        t.put(x,y,z,'cave_vines',berries='true',age='25');vines.append((x,y,z))
    # A conservative offline block-light flood finds dark supported floor cells.
    # It treats partial solid models as opaque; actual in-engine light is tested independently.
    from collections import deque
    transparent={'minecraft:air','minecraft:glow_lichen','minecraft:cave_vines','minecraft:cave_vines_plant',
                 'minecraft:hanging_roots','minecraft:moss_carpet','orimod:sanctuary_part','orimod:spirit_light_vessel',
                 'orimod:grapple_flower','orimod:keystone_pedestal','orimod:life_cell_pedestal','orimod:energy_cell_pedestal','orimod:ginso_fungus'}
    dirs=((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))
    def emission(s):
        name,props=s;props=dict(props)
        if name=='orimod:great_spirit_root':return {'none':0,'vein':6,'knot':14}[props['light']]
        if name in ('minecraft:cave_vines','minecraft:cave_vines_plant'):return 14 if props.get('berries')=='true' else 0
        return {'minecraft:glow_lichen':7,'orimod:spirit_light_vessel':0,'orimod:grapple_flower':12,'orimod:cave_altar_shrine':11,'orimod:map_stone':9,'orimod:gloom_altar':7,'orimod:keystone_pedestal':10,'orimod:ginso_fungus':8}.get(name,0)
    light={};queue=deque()
    def flood():
        while queue:
            p=queue.popleft();v=light[p]-1
            if v<=0:continue
            for d in dirs:
                q=tuple(p[i]+d[i] for i in range(3))
                if t.blocks.get(q,('',()))[0] in transparent and light.get(q,0)<v:light[q]=v;queue.append(q)
    for p,s in t.blocks.items():
        v=emission(s)
        if v:light[p]=v;queue.append(p)
    flood()
    floor=[]
    for (x,y,z),s in t.blocks.items():
        if s[0]!='minecraft:air' or y<6:continue
        if t.blocks.get((x,y+1,z),('',()))[0] not in transparent:continue
        under=t.blocks.get((x,y-1,z),('',()))[0]
        if under and under not in transparent:floor.append((x,y,z))
    patches=[]
    # Sparse lichen skins on vault/floor stone, never a replacement full-cube floor lamp.
    # Vertical attachment stays inside the same chunk; a wall support across a slice
    # boundary may not exist when vanilla validates multiface blocks during placement.
    for p in sorted(floor,key=lambda p:(p[1],p[2],p[0])):
        if light.get(p,0)>=1:continue
        x,y,z=p;candidates=[]
        for dx in range(-4,5):
            for dz in range(-4,5):
                for dy in (1,2,0,3):
                    q=(x+dx,y+dy,z+dz)
                    if abs(dx)+abs(dz)+dy>5 or t.blocks.get(q,('',()))[0]!='minecraft:air':continue
                    if 22<=q[0]<=24 and 6<=q[1]<=8 and 26<=q[2]<=54 or 35<=q[0]<=37 and 11<=q[1]<=12 and 44<=q[2]<=45:continue
                    for vy,face in ((1,'up'),(-1,'down')):
                        n=t.blocks.get((q[0],q[1]+vy,q[2]),('',()))[0]
                        if n and n not in transparent and not n.endswith(('_stairs','_slab','_fence')):
                            score=sum(1 for f in floor if light.get(f,0)<1 and sum(abs(q[i]-f[i]) for i in range(3))<=5)
                            candidates.append((-score,abs(dx)+abs(dz)+dy,q,face));break
        if not candidates:raise AssertionError(('no wall light for floor',p))
        _,_,q,face=min(candidates)
        t.put(*q,'glow_lichen',**{face:'true'},waterlogged='false');light[q]=7;queue.append(q);flood();patches.append(q)
    assert min(light.get(p,0) for p in floor)>=1
    return dict(berry_moss_tips=vines,lichen=patches,grapple_framing_knots=[(35,14,42),(40,14,43)],conservative_floor_min=min(light.get(p,0) for p in floor),floor_samples=len(floor))

def build(root):
    t=Template(root,'cave_altar_v1',SIZE)
    # Union of vaulted chambers. The bounding box is NOT a solid rectangular shell.
    air=set(); rock=set()
    for cx,cz,rx,rz,h in ROOMS:
        for x in range(max(0,cx-rx-2),min(SIZE[0],cx+rx+3)):
            for z in range(max(0,cz-rz-2),min(SIZE[2],cz+rz+3)):
                q=((x-cx)/rx)**2+((z-cz)/rz)**2
                ripple=.055*math.sin(x*.71+z*.37)+.035*math.sin(z*1.17)
                if q>1.22+ripple:continue
                roof=6+int(h*math.sqrt(max(0,1-min(q,1)*.78)))+noise(x,0,z)%2
                for y in range(3,min(32,roof+3)):
                    rock.add((x,y,z))
                    if q<.98+ripple and 6<=y<=roof:air.add((x,y,z))
    for x,y,z in sorted(rock):
        v=noise(x,y,z)
        material=('tuff' if v<3 else 'cobbled_deepslate' if v<11 else 'deepslate')
        if y==5: material='mossy_stone_bricks' if v<5 else 'polished_deepslate'
        t.put(x,y,z,material)
    for p in air:t.put(*p,'air')
    # Three short flights with two broad turning landings. The saved entry and lower
    # doorway stay at their agreed coordinates; only the authored descent is reshaped.
    descent=[(23,28-z,z) for z in range(8)]
    descent += [(23,21,8)]+[(x,21,8) for x in range(22,16,-1)]
    descent += [(17,29-z,z) for z in range(9,19)]
    descent += [(x,11,19) for x in range(17,22)]
    descent += [(x,32-x,19) for x in range(22,28)]
    descent += [(27,5,z) for z in range(20,26)]
    corridor={};distances={}
    for i,(x,y,z) in enumerate(descent):
        before=descent[max(0,i-1)];after=descent[min(len(descent)-1,i+1)]
        # Square junction pads make a real turn rather than a diagonal gap.
        for dx in range(-2,3):
            for dz in range(-2,3):
                q=(x+dx,z+dz)
                if 0<=q[0]<47 and 0<=q[1]<26:
                    if abs(dx)<=1 and abs(dz)<=1:
                        score=(abs(dx)+abs(dz),y)
                        if score<distances.get(q,(999,999)):corridor[q]=y;distances[q]=score
    # shell first, then full headroom/floor; a later flight never walls off a landing.
    for (x,z),y in corridor.items():
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            if 0<=z+dz<26 and (x+dx,z+dz) not in corridor:
                for yy in range(max(3,y-1),min(32,y+6)):t.put(x+dx,yy,z+dz,'deepslate_bricks')
        for yy in range(max(3,y-1),y+1):t.put(x,yy,z,'polished_deepslate')
        for yy in range(y+1,min(32,y+5)):t.put(x,yy,z,'air')
        if y+5<32:t.put(x,y+5,z,'tuff_bricks')
    for (x,z),y in corridor.items():
        # Full-width steps orient uphill, including the eastbound final flight.
        ups=[(dx,dz) for dx,dz in ((0,-1),(1,0),(-1,0),(0,1)) if corridor.get((x+dx,z+dz))==y+1]
        if ups:
            dx,dz=ups[0];face={(0,-1):'north',(0,1):'south',(-1,0):'west',(1,0):'east'}[(dx,dz)]
            t.put(x,y,z,'polished_deepslate_stairs',facing=face,half='bottom',shape='straight',waterlogged='false')
    # An irregular carved doorway, beneath the separately terrain-adapted exterior portal.
    for x in (20,26):
        t.box((x,27,0),(x,30,2),'chiseled_tuff_bricks')
    for x in range(20,27):
        t.put(x,31,0,'chiseled_deepslate' if x==23 else 'tuff_bricks')
    # A continuous, worn axial path with a narrow dark border; mottling is rare and peripheral.
    for x,y,z in sorted(rock):
        if y!=5:continue
        path=(abs(x-23)<=2 and 25<=z<=63) or (abs(z-29)<=1 and 8<=x<=38)
        edge=(abs(x-23)==3 and 25<=z<=63) or (abs(z-29)==2 and 8<=x<=38)
        if path:t.put(x,y,z,'polished_deepslate' if noise(x,y,z)%11 else 'cracked_deepslate_bricks')
        elif edge:t.put(x,y,z,'deepslate_tiles')
        else:t.put(x,y,z,'deepslate_bricks' if noise(x,y,z)<27 else 'mossy_stone_bricks')
    # A broad half-step and concentric dark border focus the preserved altar core.
    t.box((19,5,56),(27,5,64),'deepslate_tiles')
    t.box((20,6,57),(26,6,63),'polished_deepslate_slab',type='bottom',waterlogged='false')
    t.box((21,6,58),(25,6,62),'polished_deepslate')
    # Carved rear screen / open stone halo. The silhouette frames, rather than enlarges, the crystal.
    for x in range(17,30):
        for y in range(6,20):
            outer=((x-23)/6.5)**2+((y-11)/9)**2
            inner=((x-23)/4.8)**2+((y-11)/7.5)**2
            if outer<=1.1 and (inner>1 or y<=7):
                t.put(x,y,65,'chiseled_tuff_bricks' if (x+y)%7==0 else 'tuff_bricks')
    for x in (17,29):
        t.box((x,5,63),(x+1,11,64),'deepslate_tiles')
        t.box((x,12,63),(x+1,12,64),'chiseled_deepslate')
    # Two short branching key routes. West: ordinary root steps. East: grapple balcony with a safe lower floor.
    for i in range(3):
        t.box((5+i*2,7+i,34),(6+i*2,10+i,36),'air')
        t.box((5+i*2,6+i,34),(6+i*2,6+i,36),'mossy_stone_bricks')
    t.box((9,9,34),(11,11,36),'air')
    t.box((9,8,34),(11,8,36),'mossy_stone_bricks')
    t.put(10,9,35,'orimod:keystone_pedestal')
    t.box((35,6,39),(37,6,41),'polished_deepslate')
    t.box((33,11,43),(39,13,47),'air')
    t.box((33,10,43),(39,10,47),'deepslate_bricks')
    for x,z in ((33,46),(39,46)):t.box((x,6,z),(x,9,z),'chiseled_tuff_bricks')
    t.box((35,14,42),(41,14,43),'stripped_dark_oak_log',axis='x')
    t.box((40,14,44),(40,14,45),'stripped_dark_oak_log',axis='z')
    t.box((40,6,44),(40,14,44),'stripped_dark_oak_log',axis='y')
    # Two inset sap knots frame the flower on the existing, fully supported beam.
    # All other beam faces remain plain; there is no repeated node wall or new collision.
    for p in ((35,14,42),(40,14,43)):t.put(*p,'orimod:great_spirit_root',light='knot')
    # The flower hangs below the beam; the arriving spirit needs headroom above its centre.
    t.put(36,13,42,'hanging_roots',waterlogged='false')
    t.put(36,12,42,'orimod:grapple_flower')
    t.put(38,11,46,'orimod:keystone_pedestal')
    t.put(34,11,46,'orimod:life_cell_pedestal')
    # Wall vault with nine independently owned door parts. It is off the main through-route.
    t.box((2,5,39),(15,12,49),'deepslate_bricks');t.box((3,6,40),(14,11,48),'air')
    t.box((15,6,43),(15,8,45),'air')
    for col in range(-1,2):
        for row in range(3):
            part=('bottom','middle','top')[row]+'_'+('left','center','right')[col+1]
            t.put(15,6+row,44+col,'orimod:keystone_door',facing='east',part=part,filled='0')
    t.put(7,6,44,'orimod:energy_cell_pedestal')
    for p in ((5,6,40),(5,6,48)):t.put(*p,'glow_lichen',down='true',waterlogged='false')
    # Sealed optional chamber reached by charged flame/hammer; no interference with keys or arena.
    t.box((3,5,52),(11,11,59),'deepslate_bricks');t.box((4,6,53),(10,10,58),'air')
    t.box((11,6,54),(11,8,56),'orimod:cracked_ruin_wall')
    t.put(6,6,55,'orimod:spirit_light_vessel');t.put(8,6,57,'orimod:spirit_light_vessel')
    t.put(5,6,53,'glow_lichen',down='true',waterlogged='false')
    # Planted limestone shelves and luminous roots, kept outside the clear spine.
    for p in ((8,6,26),(13,6,32),(33,6,26),(39,6,32),(19,6,49),(29,6,64)):
        t.put(*p,'orimod:spirit_light_vessel')
    # A few continuous tapered roots at the wall: no forest of ribs through the nave.
    roots=add_roots(t,air)
    # All new large objects reserve real parts; do this last so no decor steals ownership.
    assemblies=[('cave_altar_shrine',SHRINE),('map_stone',MAP),('gloom_altar',ALTAR)]
    for kind,(cx,cy,cz) in assemblies:
        r=0 if kind=='map_stone' else 1
        for dx in range(-r,r+1):
            for dy in range(3):
                for dz in range(-r,r+1):
                    if dx==dy==dz==0:t.put(cx,cy,cz,'orimod:'+kind,assembled='true',facing='north')
                    else:t.put(cx+dx,cy+dy,cz+dz,'orimod:sanctuary_part',kind=kind,facing='north',dx=dx+1,dy=dy,dz=dz+1)
    lighting=add_lighting(t,descent)
    layout=dict(size=SIZE,shrine=SHRINE,safe=(14.5,6,29.5),entry=(23.5,29,0.5),surface_y=29,
        checkpoints=[dict(position=(36.5,11,44.5),action='grapple',count=1),dict(position=(28.5,6,48.5),action='stomp',count=1),dict(position=(23.5,6,53.5),action='blade',count=1)],
        critical=[dict(position=p,block='orimod:'+b) for b,p in [('cave_altar_shrine',SHRINE),('map_stone',MAP),('gloom_altar',ALTAR),('grapple_flower',(36,12,42))]],
        clearances=[dict(from_=None)],cells=[dict(position=(34,11,46),kind='life'),dict(position=(7,6,44),kind='energy')],
        keystones=[(10,9,35),(38,11,46)],boss_altar=ALTAR,map_stone=MAP,
        secret=dict(entrance=(11,6,55),facing='east',blocks='wall'),beetles=[(30,6,38),(17,6,58)],bridges=[])
    layout['clearances']=[{'from':(22,6,26),'to':(24,8,54)},{'from':(35,11,44),'to':(37,12,45)}]
    for box in layout['clearances']:
        a,b=box['from'],box['to']
        for x in range(a[0],b[0]+1):
            for y in range(a[1],b[1]+1):
                for z in range(a[2],b[2]+1):
                    assert t.blocks.get((x,y,z),('minecraft:air',()))[0]=='minecraft:air',('route clearance',x,y,z)
    # A per-column vault roof can jump more than its two-block cap at a jagged edge.
    # Close every six-neighbour air boundary, so underground aquifers cannot enter those side gaps.
    # Only fill previously untouched cells; retain the carved route, floor and entrance opening.
    seal=set()
    for (x,y,z),(name,_) in list(t.blocks.items()):
        if name!='minecraft:air':continue
        for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
            p=(x+dx,y+dy,z+dz)
            if p not in t.blocks:
                if p[2]==-1 or p[1]==32 and z<6:continue # open surface doorway only
                assert all(0<=p[i]<SIZE[i] for i in range(3)),('shell beyond template',p)
                seal.add(p)
    for p in sorted(seal):t.put(*p,'deepslate')
    # Canonical state stream and source plan also serve the plain-Java collision/navigation probe.
    palette=[];indexes={};blocks=[]
    for position,state in sorted(t.blocks.items(),key=lambda i:(i[0][1],i[0][2],i[0][0])):
        if state not in indexes:
            indexes[state]=len(palette);p={'Name':nbt.String(state[0])}
            if state[1]:p['Properties']=nbt.Compound({k:nbt.String(v) for k,v in state[1]})
            palette.append(nbt.Compound(p))
        blocks.append(nbt.Compound(pos=nbt.List[nbt.Int](position),state=nbt.Int(indexes[state])))
    f=nbt.File(dict(DataVersion=nbt.Int(3955),size=nbt.List[nbt.Int](SIZE),palette=nbt.List[nbt.Compound](palette),blocks=nbt.List[nbt.Compound](blocks),entities=nbt.List[nbt.Compound]()))
    out=io.BytesIO();f.write(out)
    template=root/'src/main/resources/data/orimod/structure/ruins/cave_altar_v1.nbt';template.parent.mkdir(parents=True,exist_ok=True);template.write_bytes(compressed_nbt(out.getvalue()))
    write_json(root/'src/main/resources/orimod/ruin_layout/cave_altar_v1.json',layout)
    src=root/'art/source/cave_altar/v002';src.mkdir(parents=True,exist_ok=True)
    write_json(src/'manifest.json',dict(generator='scripts/art/build_cave_altar.py',layoutVersion=1,size=SIZE,blocks=len(blocks),palette=len(palette),sha256=hashlib.sha256(template.read_bytes()).hexdigest(),assemblies=assemblies,door=DOOR,rooms=ROOMS,descent=descent,roots=roots,lighting=lighting))
    (src/'plan.txt').write_text(plan(t))
    return [template,root/'src/main/resources/orimod/ruin_layout/cave_altar_v1.json',src/'manifest.json',src/'plan.txt']

def plan(t):
    def char(p):
        name=t.blocks.get(p,('untouched',()))[0].split(':')[-1]
        return {'air':' ','untouched':'.','sanctuary_part':'p','cave_altar_shrine':'S','map_stone':'M','gloom_altar':'A','keystone_door':'D','keystone_pedestal':'K','grapple_flower':'G','spirit_light_vessel':'o','great_spirit_root':'*'}.get(name,'#')
    out=['NORTH = entry, x left-to-right; # stone, * luminous root, p owned part.','Top cut y=7:']
    out+=[''.join(char((x,7,z)) for x in range(47)) for z in range(71)]
    out+=['Long section x=23 (y31 at top):']+[''.join(char((23,y,z)) for z in range(71)) for y in range(31,-1,-1)]
    out+=['Cross section z=29:']+[''.join(char((x,y,29)) for x in range(47)) for y in range(31,-1,-1)]
    return '\n'.join(out)+'\n'

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as d:
            r=Path(d)
            for f in build(r):
                dest=ROOT/f.relative_to(r)
                if not dest.exists() or f.read_bytes()!=dest.read_bytes():raise SystemExit('Mismatch: '+str(dest))
        print('cave_altar v1: all generated products match')
    else:
        for p in build(ROOT):print(p.relative_to(ROOT))
if __name__=='__main__':main()
