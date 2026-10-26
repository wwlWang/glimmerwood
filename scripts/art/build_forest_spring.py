"""Forest Spring LayoutVersion 1: open spring cloister, separate sanctuaries and a light bridge.
Never writes LayoutVersion 0. stdlib + pinned nbtlib; --check reproduces source/package products.
"""
import argparse,hashlib,io,json,math,tempfile
from pathlib import Path
import nbtlib as nbt
from build_ruins import Template,compressed_nbt
ROOT=Path(__file__).resolve().parents[2]
SIZE=(39,32,55);SHRINE=(7,6,17);MAP=(31,6,17);ALTAR=(19,6,46);DOOR=(29,6,40)
ELLIPSES=[(19,25,17,22),(7,17,6,9),(31,17,6,9),(19,44,13,9)]
WATER=set();ROOTS=[]
GROUND_MEMBERS={'orimod:great_spirit_root','minecraft:chiseled_stone_bricks',
               'minecraft:mossy_stone_bricks','minecraft:cracked_stone_bricks',
               'minecraft:stone_bricks'}
def noise(x,y,z):return ((x*734287+y*912931+z*193939)^((x+z)*833))%29
def write_json(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n')
def ellipse(x,z,cx,cz,rx,rz):return ((x-cx)/rx)**2+((z-cz)/rz)**2

def root(t,points,width=1,knots=()):
    # Catmull-Rom centreline, sampled sub-voxel, with face-connected diagonal steps.
    # Unlike the original Y-then-X route, the bend grows through its whole height.
    samples=[]
    for i in range(len(points)-1):
        a,b,c,d=points[max(0,i-1)],points[i],points[i+1],points[min(len(points)-1,i+2)]
        for j in range(65):
            u=j/64
            samples.append(tuple(round(.5*((2*b[k])+(-a[k]+c[k])*u+(2*a[k]-5*b[k]+4*c[k]-d[k])*u*u+(-a[k]+3*b[k]-3*c[k]+d[k])*u*u*u)) for k in range(3)))
    path=[samples[0]]
    for target in samples[1:]:
        p=list(path[-1])
        while tuple(p)!=target:
            axis=max(range(3),key=lambda k:abs(target[k]-p[k]));p[axis]+=1 if target[axis]>p[axis] else -1
            if tuple(p) in path:path=path[:path.index(tuple(p))+1]
            else:path.append(tuple(p))
    own=[]
    taper=max(p[1] for p in points)-3
    for x,y,z in path:
        for dx in range(width if y<taper else 1):
            for dz in range(width if y<taper-2 else 1):t.put(x+dx,y,z+dz,'orimod:great_spirit_root',light='none')
    if width>1:
        for x,y,z in (path[0],path[-1]):
            if y==5:
                for dx,dz in ((-1,0),(0,-1),(2,0),(0,2)):
                    if t.blocks.get((x+dx,5,z+dz),('',()))[0]!='minecraft:water':t.put(x+dx,5,z+dz,'orimod:great_spirit_root',light='none')
    for k in knots:
        k=min(path,key=lambda p:sum((p[i]-k[i])**2 for i in range(3)))
        t.put(*k,'orimod:great_spirit_root',light='knot');own.append(k)
    ROOTS.append(dict(path=path,knots=own))

def berries(t,x,y,z,length=2):
    # A real supported hanging plant; two small warm fruits replace glyph-covered pillars.
    support=t.blocks.get((x,y+1,z),('minecraft:air',()))[0]
    if support=='minecraft:air' or support.endswith(('leaves','slab','wall','stairs')):return
    for i in range(length):
        if t.blocks.get((x,y-i,z),('',()))[0] not in ('minecraft:air',''):break
        t.put(x,y-i,z,'cave_vines' if i==length-1 else 'cave_vines_plant',berries='true',**({'age':'0'} if i==length-1 else {}))

def support_ground_members(t,land):
    # Dressing can extend a pier/root foot beyond the initial garden outline.
    # Finish its authored foundation as well, so runtime grading and carver-hole
    # support use the same bottom-DIRT footprint. Elevated branches stay elevated.
    extra=[]
    for (x,y,z),(name,_) in sorted(list(t.blocks.items())):
        if y!=5 or name not in GROUND_MEMBERS or (x,z) in land:continue
        for yy in range(5):
            before=t.blocks.get((x,yy,z),('minecraft:air',()))[0]
            assert before=='minecraft:air',('foundation would overwrite authored content',x,yy,z,before)
            t.put(x,yy,z,'dirt')
        land.add((x,z));extra.append(dict(column=(x,z),member=name))
    return extra

def ruin_room(t,a,b,secret=False):
    x0,z0=a;x1,z1=b
    # High broken shell: the roof is gone, but the window sills and unstepped outer
    # wall still protect the sealed reward. No rubble staircase bypasses the door.
    for x in range(x0,x1+1):
        for z in range(z0,z1+1):
            edge=x in (x0,x1) or z in (z0,z1)
            for y in range(6,16):
                if edge:
                    u=(x-x0)+(z-z0)*.75
                    top=11+round(1.6*math.sin(u*.63)+.8*math.sin(u*1.21))
                    if z==z0 and x0+2<=x<=x0+4:top=9 # a broad collapse, not alternating crenellations
                    block='mossy_stone_bricks' if noise(x,y,z)<15 else 'cracked_stone_bricks'
                    if y>top:block='air'
                    t.put(x,y,z,block,**({'type':'bottom','waterlogged':'false'} if block.endswith('slab') else {}))
                else:t.put(x,y,z,'air')
    # Narrow stone mullions keep sight lines open but cannot be crawled through.
    for z in (z0+2,z1-2):
        for x in (x0,x1):t.put(x,7,z,'mossy_stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
    for x in (x0+2,x1-2):
        for z in (z0,z1):t.put(x,7,z,'mossy_stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
    # Two incomplete inward ribs and surviving corners, rather than a flat cube roof.
    for x,z,dx,dz in ((x0,z0,1,1),(x1,z1,-1,-1)):
        t.box((x,9,z),(x,12,z),'mossy_stone_bricks')
        for step in range(1,3):t.put(x+dx*step,12,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
        t.put(x+dx*2,12,z+dz,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
    # Exterior vines attach to real wall faces and do not offer a climbable ladder.
    # Use lichen/moss on the low facade; long vines only hang into the closed interior.
    for x,z in ((x0+1,z0+1),(x1-1,z1-1)):
        back=z-1 if z==z0+1 else z+1
        t.box((x,6,back),(x,11,back),'mossy_stone_bricks')
        t.put(x,11,z,'mossy_stone_bricks');berries(t,x,10,z,3)
    for x,z in ((x0+2,z0+1),(x1-2,z1-1)):
        t.put(x,5,z,'moss_block')
        t.put(x,6,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
    for x,z,face in ((x0+1,z0-1,'south'),(x1-1,z1+1,'north')):
        for y in (6,7):
            if t.blocks.get((x,y,z),('',()))[0]=='minecraft:air':t.put(x,y,z,'vine',**{face:'true'})
    # Partial doorway arch survives on the pool-facing wall.
    face=x1 if secret else x0;middle=(z0+z1)//2
    for z in range(middle-2,middle+3):
        t.put(face,9+(1 if abs(z-middle)<2 else 0),z,'chiseled_stone_bricks' if z==middle else 'mossy_stone_bricks')

def light(t):
    # Sources are permanent root knots/lichen/foliage, never vessels or unlit lanterns.
    from collections import deque
    transparent={'minecraft:air','minecraft:water','minecraft:moss_carpet','minecraft:glow_lichen','minecraft:vine','minecraft:cave_vines','minecraft:cave_vines_plant','minecraft:hanging_roots','orimod:spirit_vine','orimod:spirit_leaves','minecraft:azalea_leaves','minecraft:flowering_azalea_leaves','orimod:sanctuary_part','orimod:spirit_platform','orimod:spirit_light_vessel','orimod:bash_anchor','orimod:keystone_pedestal','orimod:life_cell_pedestal','orimod:energy_cell_pedestal'}
    dirs=((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))
    value={};queue=deque()
    def emission(s):
        n,p=s;p=dict(p)
        if n=='orimod:great_spirit_root':return {'none':0,'vein':6,'knot':14}[p['light']]
        # Harvestable fruit and vessels never contribute to baseline coverage.
        return {'minecraft:verdant_froglight':15,'minecraft:glow_lichen':7,'orimod:spirit_vine':10,'orimod:spirit_leaves':9,'orimod:forest_spring_shrine':11,'orimod:map_stone':9,'orimod:storm_altar':7,'orimod:keystone_pedestal':10,'orimod:updraft':8}.get(n,0)
    def flood():
        while queue:
            p=queue.popleft();v=value[p]-1
            if v<=0:continue
            for d in dirs:
                q=tuple(p[i]+d[i] for i in range(3))
                n=t.blocks.get(q,('minecraft:air',()))[0]
                if (n in transparent or n.endswith(('_wall','_slab','_stairs'))) and all(0<=q[i]<SIZE[i] for i in range(3)) and value.get(q,0)<v:value[q]=v;queue.append(q)
    for p,s in t.blocks.items():
        v=emission(s)
        if v:value[p]=v;queue.append(p)
    flood()
    # Include every exposed stone/root roof and ledge, not only the main walking route.
    floors=[]
    for (x,y,z),(n,_) in list(t.blocks.items()):
        if n not in transparent and not n.endswith(('_fence','_wall','_stairs','_slab')) and y+2<SIZE[1] and t.blocks.get((x,y+1,z),('minecraft:air',()))[0]=='minecraft:air' and t.blocks.get((x,y+2,z),('minecraft:air',()))[0]=='minecraft:air':floors.append((x,y+1,z))
    patches=[]
    for p in sorted(floors,key=lambda p:(p[1],p[2],p[0])):
        if value.get(p,0)>=2:continue
        # Thin moss-light on the actual supporting surface, same chunk column, no collision.
        t.put(*p,'glow_lichen',down='true',waterlogged='false');patches.append(p);value[p]=7;queue.append(p);flood()
    # A bottom slab is itself a half-height landing. Lichen cannot stand on its
    # upper face: attach it to the adjacent full masonry/root instead.
    for (x,y,z),(n,_) in sorted(list(t.blocks.items())):
        if not n.endswith('_slab') or value.get((x,y,z),0)>=3:continue
        candidates=[]
        for dx in range(-3,4):
            for dz in range(-3,4):
                for yy in range(y-2,y+3):
                    q=(x+dx,yy,z+dz);base=(q[0],yy-1,q[2]);b=t.blocks.get(base,('minecraft:air',()))[0]
                    d=abs(dx)+abs(dz)+abs(yy-y)
                    if d<=5 and b not in transparent and not b.endswith(('_slab','_wall','_stairs')) and t.blocks.get(q,('',()))[0]=='minecraft:air':candidates.append((d,q))
        if candidates:
            _,p=min(candidates);t.put(*p,'glow_lichen',down='true',waterlogged='false');patches.append(p);value[p]=7;queue.append(p);flood()
    return dict(lichen=patches,floor_samples=len(floors),conservative_min=min(value.get(p,0) for p in floors))

def build(out):
    WATER.clear();ROOTS.clear();t=Template(out,'forest_spring_v1',SIZE)
    land={(x,z) for x in range(39) for z in range(55) if any(ellipse(x,z,*e)<=1 for e in ELLIPSES)}
    land|={(x,z) for x in range(16,23) for z in range(0,12)}
    land|={(x,z) for x in range(2,11) for z in range(35,42)}
    land|={(x,z) for x in range(29,38) for z in range(36,46)}
    # A feathered dirt rim meets the beard terrain; only the inner garden has a stone bed.
    for x,z in sorted(land):
        edge=any((x+dx,z+dz) not in land for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
        for y in range(6):t.put(x,y,z,'dirt' if y<4 or edge else 'stone_bricks')
        garden=math.sin(x*.43)+math.cos(z*.37)+math.sin((x-z)*.25)
        t.put(x,5,z,'moss_block' if edge or garden>.8 else 'grass_block' if garden>.25 else 'coarse_dirt' if noise(x,5,z)<3 else 'mossy_stone_bricks')
        for y in range(6,32):t.put(x,y,z,'air')
    # Readable broad paths circle the water; no random high-contrast floor patches.
    for x,z in land:
        q=ellipse(x,z,19,25,12,13)
        if .95<q<1.55 or 17<=x<=21 and (z<14 or z>36) or 14<=z<=19 and (x<10 or x>28):
            t.put(x,5,z,'stone_bricks' if noise(x,5,z)>7 else 'mossy_stone_bricks' if noise(x,5,z)>2 else 'coarse_dirt')
        if 18<=x<=20 and (z<14 or z>36):t.put(x,5,z,'polished_andesite' if noise(x,5,z)>3 else 'cracked_stone_bricks')
    # Sealed two-deep source pool, wholly authored bed and vertical ring.
    pool={(x,z) for x,z in land if ellipse(x,z,19,25,10,11)<1+.09*math.sin(x*.8+z*.23)+.055*math.cos(z*.9-x*.3)}
    for x,z in sorted(pool):
        t.put(x,3,z,'clay')
        for y in (4,5):t.put(x,y,z,'water',level='0');WATER.add((x,y,z))
    for x,z in land-pool:
        if any((x+dx,z+dz) in pool for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))):
            for y in (3,4,5):t.put(x,y,z,'moss_block' if y==5 else 'clay' if y==3 else 'gravel')
    # A few shallow coves have gravel under one block of water. Keep the island's
    # no-jump channel deep and unchanged by bank dressing.
    for x,z in pool:
        if (x<13 or x>26) and any((x+dx,z+dz) not in pool for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))):
            t.put(x,4,z,'gravel');WATER.discard((x,4,z))
    # Low submerged return stairs; their dry lip reaches only the near bank, never the island.
    for z,y in ((14,5),(15,4)):
        for x in range(15,18):t.put(x,y,z,'mossy_stone_bricks');WATER.discard((x,y,z))
    # Portal: continuous root arch, uneven carved piers, glowing signature, open sky above.
    for x in (13,25):
        for y in range(5,10 if x==13 else 12):t.put(x,y,3,'chiseled_stone_bricks' if y in (5,9) else 'mossy_stone_bricks')
    root(t,[(14,5,5),(14,11,5),(16,14,5),(19,16,5),(22,14,5),(24,11,5),(24,5,5)],2,[])
    t.put(19,16,5,'orimod:great_spirit_root',light='knot')
    # Four open-sided, differently framed garden spaces. Stone arcades never fill the sightline.
    for cx,cz in ((7,17),(31,17)):
        for index,(dx,dz) in enumerate(((-4,-4),(-4,4),(4,4),(4,-4))):
            x,z=cx+dx,cz+dz
            top=(7,10,8,5)[(index+(1 if cx==31 else 0))%4]
            for y in range(5,top+1):t.put(x,y,z,'mossy_stone_bricks' if noise(x,y,z)<17 else 'cracked_stone_bricks')
            t.put(x,top+1,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
            if top>7:t.put(x,6,z-1,'glow_lichen',south='true',waterlogged='false')
            if top==5:
                for dz2 in (1,2):t.put(x,5,z+dz2,'chiseled_stone_bricks' if dz2==2 else 'mossy_stone_bricks')
        root(t,[(cx-4,5,cz+4),(cx-4,12,cz+4),(cx,15,cz+4),(cx+4,12,cz+4),(cx+4,5,cz+4)],2,[(cx-4,8,cz+4)])
        for x in range(cx-3,cx+4):
            y=14-(abs(x-cx)//2)
            if t.blocks.get((x,y,cz+4),('',()))[0]=='minecraft:air':t.put(x,y,cz+4,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
    # The storm focus has a root halo and two carved, broken flanking uprights.
    root(t,[(10,5,49),(10,12,49),(13,17,49),(19,20,49),(25,17,49),(28,12,49),(28,5,49)],2,[])
    for x in (15,23):
        t.box((x,5,48),(x,9 if x==15 else 11,48),'mossy_stone_bricks')
        t.put(x,10 if x==15 else 12,48,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
        t.put(x,10 if x==15 else 12,47,'orimod:great_spirit_root',light='none')
        berries(t,x,9 if x==15 else 11,47,2)
    for x,z in ((15,43),(23,43)):
        t.box((x,5,z),(x,6 if x==15 else 8,z),'mossy_stone_bricks')
    # Attached, thick crowns. Overlapping ellipsoids soften the outline; their
    # undersides droop at the edge instead of forming one-block leaf plates.
    for cx,cy,cz,rx,rz in ((6,15,22,6,5),(32,15,22,5,5),(12,18,49,6,5),(25,19,49,7,5)):
        start=min((p for p,s in t.blocks.items() if s[0]=='orimod:great_spirit_root'),key=lambda p:(p[0]-cx)**2+(p[1]-cy+3)**2+(p[2]-cz)**2)
        for dx,dy,dz in ((-3,1,0),(3,0,1),(1,-1,3)):
            root(t,[start,(cx,cy-1,cz),(cx+dx,cy+dy,cz+dz)])
        for x in range(max(0,cx-rx),min(39,cx+rx+1)):
            for z in range(max(0,cz-rz),min(55,cz+rz+1)):
                q=((x-cx)/rx)**2+((z-cz)/rz)**2
                if q>1+.13*math.sin(x*.8+z*.6):continue
                top=cy+round(2.5*math.sqrt(max(0,1-q)))
                bottom=cy-1-int(q>.6)-int(noise(x,cy,z)<5)
                for y in range(bottom,top+1):
                    if t.blocks.get((x,y,z),('',()))[0] in ('minecraft:air',''):
                        glow=noise(x,y,z)<2
                        t.put(x,y,z,'orimod:spirit_leaves' if glow else 'azalea_leaves',**({'bloomed':'false','great_tree':'false'} if glow else {'persistent':'true','distance':'1','waterlogged':'false'}))
                if q>.55 and noise(x,cy,z)<4:berries(t,x,bottom-1,z,2)
    # Small branches and fruit behind each focus provide ambient light without
    # covering the architecture with repeated sap-knot motifs.
    for cx,cz in ((7,17),(31,17),(19,46)):
        for side in (-1,1):
            root(t,[(cx+side*3,5,cz+3),(cx+side*3,10,cz+3),(cx+side*2,11,cz+1)],1)
            berries(t,cx+side*2,10,cz+1,2)
        # A low branch at one side of the court, leaving the central approach clear.
        side=-1 if cx<20 else 1
        root(t,[(cx+side*4,5,cz-4),(cx+side*4,9,cz-4),(cx+side*2,9,cz-3)],1)
        berries(t,cx+side*2,8,cz-3,2)
    # Basic forest course along the west bank: wall-jump landing, double-jump ledges, dash terrace.
    root(t,[(7,5,25),(7,12,25),(9,14,25),(11,12,25),(11,5,25)],1,[])
    t.box((8,7,24),(10,7,26),'mossy_stone_bricks')
    t.box((5,9,28),(9,9,30),'mossy_stone_bricks');t.put(7,10,29,'orimod:keystone_pedestal')
    # Return stepping stones beside the west course, reachable without unlocked skills.
    for x,y,z in ((4,6,25),(4,7,26),(4,8,27)):
        t.box((x,y,z),(x+1,y,z+1),'mossy_stone_bricks')
    t.box((5,7,32),(9,7,33),'mossy_stone_bricks')
    # Light bridge terminates on an isolated reward island. Deep pool is the safe retreat.
    for z,y in ((15,5),(16,6),(17,7),(18,7)):
        for x in range(18,21):
            for yy in range(4,y+1):t.put(x,yy,z,'mossy_stone_bricks');WATER.discard((x,yy,z))
    t.put(19,7,17,'orimod:great_spirit_root',light='knot')
    for x in range(17,22):
        for z in range(27,32):
            if ellipse(x,z,19,29,2.6,2.6)>1 or (x,z) in ((17,28),(21,30)):continue
            for y in range(3,8):t.put(x,y,z,'moss_block' if y==7 else 'clay' if y<5 else 'mossy_stone_bricks');WATER.discard((x,y,z))
    platforms=[(19,7,z) for z in range(19,27)]
    for p in platforms:t.put(*p,'orimod:spirit_platform',lit='false',fading='false')
    t.put(19,9,22,'orimod:bash_anchor',lit='false')
    # Small, continuous wooden arm supports the lamp without offering a bypass to the island.
    root(t,[(23,3,22),(23,10,22),(19,10,22)],1,[(19,10,22)])
    for x in (16,22):
        root(t,[(x,3,16),(x,8,16)],1);t.put(x,8,17,'orimod:great_spirit_root',light='none');berries(t,x,7,17,1)
    for p in list(WATER):
        if t.blocks[p][0]!='minecraft:water':WATER.discard(p)
    t.put(19,8,29,'orimod:keystone_pedestal');t.put(20,8,30,'orimod:spirit_light_vessel')
    # Narrow water-side root lights outline the mechanic without lighting the magic platforms.
    for x,z in ((16,28),(22,28)):
        # Inset into the existing pool bed; transparent water carries the light, not a hidden light block.
        t.put(x,3,z,'orimod:great_spirit_root',light='knot')
    # Optional sealed west root chamber, separated from the course and reward island.
    ruin_room(t,(2,35),(10,41),secret=True)
    t.box((10,6,37),(10,8,39),'orimod:cracked_ruin_wall')
    t.put(5,6,38,'orimod:spirit_light_vessel')
    # New optional two-key treasury; old forest had no key gate, old version remains unchanged.
    ruin_room(t,(29,36),(37,45))
    t.box((29,6,39),(29,8,41),'air')
    for col in range(-1,2):
        for row in range(3):t.put(29,6+row,40-col,'orimod:keystone_door',facing='west',part=('bottom','middle','top')[row]+'_'+('left','center','right')[col+1],filled='0')
    t.put(34,6,40,'orimod:energy_cell_pedestal')
    for z in (37,43):
        t.put(28,10,z,'orimod:great_spirit_root',light='none');berries(t,28,9,z,2)
    t.put(32,10,37,'mossy_stone_bricks');t.put(32,10,36,'mossy_stone_bricks');berries(t,32,9,37,2)
    # A little updraft beside the storm courtyard preserves the forest's optional wind play.
    t.put(12,6,38,'orimod:updraft');t.put(13,6,38,'orimod:updraft')
    for p in ((4,6,13),(34,6,13),(13,6,45),(25,6,46)):t.put(*p,'orimod:spirit_light_vessel')
    assemblies=[('forest_spring_shrine',SHRINE),('map_stone',MAP),('storm_altar',ALTAR)]
    for kind,(cx,cy,cz) in assemblies:
        r=0 if kind=='map_stone' else 1
        for dx in range(-r,r+1):
            for dy in range(3):
                for dz in range(-r,r+1):
                    if dx==dy==dz==0:t.put(cx,cy,cz,'orimod:'+kind,assembled='true',facing='north')
                    else:t.put(cx+dx,cy+dy,cz+dz,'orimod:sanctuary_part',kind=kind,facing='north',dx=dx+1,dy=dy,dz=dz+1)
    # Fixed verdant inlays under thin moss are part of the masonry, not harvestable
    # fruit. Keep the central walking axis and assembly parts clear.
    for cx,cz in ((7,17),(31,17),(19,46)):
        for dx,dz in ((-2,-2),(2,2)):
            t.put(cx+dx,5,cz+dz,'verdant_froglight',axis='y')
            if t.blocks.get((cx+dx,6,cz+dz),('',()))[0]=='minecraft:air':t.put(cx+dx,6,cz+dz,'moss_carpet')
    for x,z in ((27,40),(32,40)):
        t.put(x,5,z,'verdant_froglight',axis='y');t.put(x,6,z,'moss_carpet')
    foundation_extensions=support_ground_members(t,land)
    lighting=light(t)
    layout=dict(size=SIZE,shrine=SHRINE,safe=(10.5,6,16.5),entry=(19.5,6,.5),surface_y=6,approach_y=6,
      checkpoints=[dict(position=(9.5,8,25.5),action='wall_jump',count=1),dict(position=(8.5,10,28.5),action='jump',count=2),dict(position=(7.5,8,32.5),action='dash',count=1)],
      critical=[dict(position=p,block='orimod:'+b) for b,p in [('forest_spring_shrine',SHRINE),('great_spirit_root',(7,9,25)),('bash_anchor',(19,9,22))]],
      clearances=[{'from':(17,6,0),'to':(21,8,13)},{'from':(8,8,24),'to':(10,10,24)},{'from':(8,10,28),'to':(9,12,28)}],
      cells=[dict(position=(34,6,40),kind='energy')],keystones=[(7,10,29),(19,8,29)],storm_altar=ALTAR,map_stone=MAP,
      updrafts=[(12,6,38),(13,6,38)],secret=dict(entrance=(10,6,38),facing='east',blocks='wall'),beetles=[(27,6,34),(12,6,44)],
      bridges=[dict(entry=(19,8,18),exit=(19,8,27),platforms=platforms,lanterns=[(19,9,22)],escape=[(19,4,19),(16,5,15),(16,6,14)],facing='south')])
    for b in layout['clearances']:
        a,c=b['from'],b['to']
        for x in range(a[0],c[0]+1):
            for y in range(a[1],c[1]+1):
                for z in range(a[2],c[2]+1):assert t.blocks.get((x,y,z),('',()))[0] in ('minecraft:air','minecraft:glow_lichen'),('clearance',x,y,z,t.blocks.get((x,y,z)))
    # Every source has authored support/rim. No procedural flow is relied on for the spring.
    for x,y,z in WATER:
        for dx,dy,dz in ((1,0,0),(-1,0,0),(0,0,1),(0,0,-1),(0,-1,0)):
            q=(x+dx,y+dy,z+dz);assert t.blocks.get(q,('minecraft:air',()))[0] not in ('minecraft:air','minecraft:glow_lichen'),('pool leak',q)
    palette=[];indexes={};blocks=[]
    for p,s in sorted(t.blocks.items(),key=lambda e:(e[0][1],e[0][2],e[0][0])):
        if s not in indexes:
            indexes[s]=len(palette);a={'Name':nbt.String(s[0])}
            if s[1]:a['Properties']=nbt.Compound({k:nbt.String(v) for k,v in s[1]})
            palette.append(nbt.Compound(a))
        blocks.append(nbt.Compound(pos=nbt.List[nbt.Int](p),state=nbt.Int(indexes[s])))
    f=nbt.File(dict(DataVersion=nbt.Int(3955),size=nbt.List[nbt.Int](SIZE),palette=nbt.List[nbt.Compound](palette),blocks=nbt.List[nbt.Compound](blocks),entities=nbt.List[nbt.Compound]()));data=io.BytesIO();f.write(data)
    template=out/'src/main/resources/data/orimod/structure/ruins/forest_spring_v1.nbt';template.parent.mkdir(parents=True,exist_ok=True);template.write_bytes(compressed_nbt(data.getvalue()))
    contract=out/'src/main/resources/orimod/ruin_layout/forest_spring_v1.json';write_json(contract,layout)
    src=out/'art/source/forest_spring/v002';write_json(src/'manifest.json',dict(generator='scripts/art/build_forest_spring.py',layoutVersion=1,size=SIZE,sha256=hashlib.sha256(template.read_bytes()).hexdigest(),blocks=len(blocks),palette=len(palette),water=sorted(WATER),roots=ROOTS,lighting=lighting,assemblies=assemblies,door=DOOR,land=sorted(land),foundation_extensions=foundation_extensions))
    def cell(x,y,z):return {'minecraft:air':' ','minecraft:water':'~','orimod:great_spirit_root':'r','orimod:forest_spring_shrine':'S','orimod:map_stone':'M','orimod:storm_altar':'A','orimod:spirit_platform':'=','orimod:keystone_door':'D','orimod:bash_anchor':'B'}.get(t.blocks.get((x,y,z),('',()))[0],'#' if (x,y,z) in t.blocks else '.')
    (src/'plan.txt').write_text('NORTH entry. ~=spring r=root S=shrine M=map A=storm B=lamp ==light bridge D=door\nPlan y6\n'+'\n'.join(''.join(cell(x,6,z) for x in range(39)) for z in range(55))+'\nLong section x19\n'+'\n'.join(''.join(cell(19,y,z) for z in range(55)) for y in range(31,-1,-1))+'\nCross section z25\n'+'\n'.join(''.join(cell(x,y,25) for x in range(39)) for y in range(31,-1,-1))+'\n')
    return [template,contract,src/'manifest.json',src/'plan.txt']

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
    if a.check:
        with tempfile.TemporaryDirectory() as d:
            out=Path(d)
            for path in build(out):
                q=ROOT/path.relative_to(out)
                if not q.exists() or path.read_bytes()!=q.read_bytes():raise SystemExit('Mismatch: '+str(q))
        print('forest_spring v1: all generated products match')
    else:
        for p in build(ROOT):print(p.relative_to(ROOT))
if __name__=='__main__':main()
