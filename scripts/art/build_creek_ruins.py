#!/usr/bin/env python3
"""Versioned creek valley. Never regenerates the legacy creek or other ruins.
Source -> deterministic NBT + route/terrain contracts + manifest/sections.
"""
import argparse, hashlib, io, json, math, tempfile
from pathlib import Path
import nbtlib as nbt
from build_ruins import Template, compressed_nbt
import build_forest_spring as shared

ROOT=Path(__file__).resolve().parents[2]
SIZE=(45,44,67); SHRINE=(11,10,22); MAP=(8,10,11)
WATER=set(); FLOW=set(); GLOOM=set(); ROOTS=[]; LAND={}; MEMBERS=[]; WEIRS=[]; FLOORS=set(); WALL_FEET=set()
DIRS=((1,0),(-1,0),(0,1),(0,-1))
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def name(t,p):return t.blocks.get(tuple(p),('minecraft:air',()))[0]
def noise(x,y,z):return (x*734287+y*912931+z*193939)%29
def rear_edge(x,level):
    # Non-parallel contours; keep at least one column between risers.
    edge=0
    for i in range(level+1):
        raw=60+2*i+round(2.65*math.sin(x*.21+i*.65)+.35*math.cos(x*.37-i*.45))
        edge=max(edge+1,raw)
    return edge
def ground(x,z):
    base=9+max(0,min(9,(z-29)//4))
    if z>=57:base=min(base,17-sum(z>rear_edge(x,i) for i in range(7)))
    return base
def cx(z):return 21+round(4*math.sin((z-7)/48*math.pi/2)+1.2*math.sin(z*.26))
def wy(z):return 13 if z>=51 else 11 if z>=40 else 9 if z>=20 else 7 if z>=18 else 6
def foundation(t,x,z,top):
    LAND[(x,z)]=top
    for y in range(top+1):t.put(x,y,z,'dirt' if y<3 else 'stone' if y<top-1 else 'moss_block' if y==top else 'rooted_dirt')
def floor(t,x,z,y,block='mossy_stone_bricks',clear=4):
    if not (0<=x<SIZE[0] and 0<=z<SIZE[2]):return # The entrance path continues in RuinApproachPiece.
    LAND[(x,z)]=y;FLOORS.add((x,z))
    for yy in range(y+1):
        if name(t,(x,yy,z))=='minecraft:air':t.put(x,yy,z,'stone')
    t.put(x,y,z,block)
    for yy in range(y+1,min(SIZE[1],y+clear+1)):t.put(x,yy,z,'air')
def deck(t,a,b,y):
    for x in range(a[0],b[0]+1):
        for z in range(a[1],b[1]+1):floor(t,x,z,y,'mossy_stone_bricks' if noise(x,y,z)<10 else 'stone_bricks',6)
def path(t,points,width=1):
    for a,b in zip(points,points[1:]):
        length=max(abs(a[i]-b[i]) for i in range(3))
        for j in range(length+1):
            v=j/max(1,length);x,y,z=[round(a[i]+(b[i]-a[i])*v) for i in range(3)]
            for dx in range(-width,width+1):
                for dz in range(-width,width+1):floor(t,x+dx,z+dz,y,'stone_bricks' if noise(x,y,z)>5 else 'mossy_stone_bricks')
def pier(t,x,z,base,top,width=1):
    for dx in range(width):
        for dz in range(width):
            if (x+dx,z+dz) not in LAND:foundation(t,x+dx,z+dz,base)
            for y in range(base+1,top+1):t.put(x+dx,y,z+dz,'mossy_stone_bricks' if noise(x,y,z)<12 else 'stone_bricks')
            MEMBERS.append((x+dx,z+dz,base,top))
def root(t,points,width=2):
    shared.ROOTS.clear();shared.root(t,points,width=width)
    ROOTS.extend(shared.ROOTS)
    # Endpoints are genuine anchored feet; buried material belongs to the same footprint.
    for x,y,z in (points[0],points[-1]):
        if (x,z) in LAND and y<=LAND[(x,z)]+1:
            for dx in range(width):
                for dz in range(width):
                    if (x+dx,z+dz) not in LAND:foundation(t,x+dx,z+dz,y)
def leaves(t,c,rx,ry,rz):
    for x in range(c[0]-rx,c[0]+rx+1):
        for y in range(c[1]-ry,c[1]+ry+1):
            for z in range(c[2]-rz,c[2]+rz+1):
                d=((x-c[0])/rx)**2+((y-c[1])/ry)**2+((z-c[2])/rz)**2
                if d<1+.1*math.sin(x*.8+z*.7) and all(0<=v<SIZE[i] for i,v in enumerate((x,y,z))) and name(t,(x,y,z))=='minecraft:air':
                    t.put(x,y,z,'azalea_leaves' if noise(x,y,z)>4 else 'flowering_azalea_leaves',persistent='true',distance='1',waterlogged='false')
def tree(t,x,z,height,lean):
    y=LAND[(x,z)];top=(x+lean,y+height,z+2)
    # One slowly shifting, thick centreline; only the crown tips become single voxels.
    points=[]
    for dy in range(height+1):
        u=dy/height;a=x+round(lean*(.2*u+.8*u*u));b=z+round(2*u*u)
        points.append((a,y+dy,b))
        for dx in range(2 if dy<height-2 else 1):
            for dz in range(2 if dy<height-4 else 1):t.put(a+dx,y+dy,b+dz,'orimod:great_spirit_root',light='none')
    ROOTS.append(dict(path=points,knots=[]))
    for dx,dz in ((-2,0),(0,-2),(2,0),(0,2)):
        if (x+dx,z+dz) in LAND:root(t,[(x+dx,LAND[(x+dx,z+dz)],z+dz),(x,y+2,z),(x+1,y+4,z+1)],1)
    for dx,dy,dz,rx,ry,rz in ((-3,-1,-2,4,4,3),(3,1,1,3,4,4),(1,-3,4,4,3,3),(-1,2,0,3,3,3)):
        end=(top[0]+dx,top[1]+dy,top[2]+dz)
        root(t,[(top[0],top[1]-4,top[2]),(top[0]+dx//2,top[1]-2,top[2]+dz//2),end],1)
        leaves(t,end,rx,ry,rz)
        # Small hanging lobes overlap the big masses; no concentric plate-like tiers.
        leaves(t,(end[0]+(1 if dx>0 else -1),end[1]-2,end[2]+1),2,3,2)
def walls(t,x0,z0,x1,z1,base):
    # Open, collapsed enclosure: wide river-facing opening and no ceiling.
    for x in range(x0,x1+1):
        for z in range(z0,z1+1):
            if x not in (x0,x1) and z not in (z0,z1):continue
            if (x==x1 and z0+2<=z<=z1-2) or (z==z0 and x0+2<=x<=x1-2):continue
            if (x,z) not in LAND:foundation(t,x,z,base)
            WALL_FEET.add((x,z,base))
            top=base+2+round(1.2*math.sin(x*.67+z*.43)+.8*math.cos(z*.9-x*.2))
            for y in range(base+1,max(base+1,top)+1):t.put(x,y,z,'mossy_stone_bricks' if noise(x,y,z)<18 else 'cracked_stone_bricks')
    for x,z in ((x0+1,z0+1),(x1-1,z1-1)):
        t.put(x,base+1,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
def assembly(t,kind,p):
    r=0 if kind=='map_stone' else 1
    for dx in range(-r,r+1):
        for dy in range(3):
            for dz in range(-r,r+1):
                if dx==dy==dz==0:t.put(*p,'orimod:'+kind,assembled='true',facing='north')
                else:t.put(p[0]+dx,p[1]+dy,p[2]+dz,'orimod:sanctuary_part',kind=kind,facing='north',dx=dx+1,dy=dy,dz=dz+1)
def glow(t,x,y,z):
    t.put(x,y,z,'verdant_froglight',axis='y')
    if name(t,(x,y+1,z))=='minecraft:air':t.put(x,y+1,z,'moss_carpet')

def water(t):
    # Five source-filled reaches. Spill lips are single-wide and independently recorded.
    lips={50:(13,11),39:(11,9),19:(9,7),17:(7,6)}
    for z in range(7,62):
        if z in lips:continue
        y=wy(z);c=cx(z);w=2 if z%11<8 else 1
        for x in range(c-w,c+w+1):WATER.add((x,y,z))
    for z,(upper,lower) in lips.items():
        x=cx(z+1)
        # Complete spillway across the entire width, one block above upstream water.
        for xx in range(min(cx(z-1),cx(z+1))-4,max(cx(z-1),cx(z+1))+5):
            for y in range(lower-2,upper+2):t.put(xx,y,z,'mossy_stone_bricks')
        WATER.add((x,lower,z))
        for y in range(lower+1,upper+1):FLOW.add((x,y,z))
        WEIRS.append(dict(z=z,x=x,upper=upper,lower=lower,flow=[(x,y,z) for y in range(lower+1,upper+1)]))
    for z in range(3,7):
        for x in range(19,24):WATER.add((x,6,z))
    # The mill wheel's lower TWO rows stand in a connected, source-filled pit.
    # Its masonry-lined bed joins the raised reach and the z19 downstream fall.
    for x in range(21,27):
        for z in range(20,29):
            for y in (8,9):WATER.add((x,y,z))
    # Keep the millrace's two stone side banks continuous, including where
    # the upstream meander used to protrude through the new lining.
    WATER.difference_update({p for p in WATER if 20<=p[2]<=28 and (p[0]<=20 or p[0]>=27)})
    # Clear first, then seal. A lower reach must not erase the higher spill's
    # retaining wall in the next iteration (set iteration order is irrelevant).
    for x,y,z in sorted(WATER|FLOW):
        for yy in range(y+1,min(SIZE[1],y+4)):
            if (x,yy,z) not in WATER|FLOW:t.put(x,yy,z,'air')
    for x,y,z in sorted(WATER|FLOW):
        for d in ((1,0,0),(-1,0,0),(0,0,1),(0,0,-1),(0,-1,0),(0,-2,0)):
            q=(x+d[0],y+d[1],z+d[2])
            if q not in WATER|FLOW:t.put(*q,'clay' if d[1]<0 else 'mossy_stone_bricks')
    # A built millrace, not a grass excavation. Lining stays outside wet cells.
    for x in range(20,28):
        for z in range(20,29):
            if (x,8,z) in WATER:t.put(x,7,z,'mossy_stone_bricks')
            if x in (20,27):
                for y in range(7,10):t.put(x,y,z,'mossy_stone_bricks')
    # Rebuild weir top after clearing the air above water, leaving only the declared notch.
    for w in WEIRS:
        z=w['z'];x=w['x']
        for xx in range(min(cx(z-1),cx(z+1))-4,max(cx(z-1),cx(z+1))+5):
            if xx!=x:
                for y in range(w['lower']-1,w['upper']+2):t.put(xx,y,z,'mossy_stone_bricks')
    for p in sorted(WATER):t.put(*p,'water',level='0')
    for p in sorted(FLOW):t.put(*p,'water',level='8')
    # A blind sealed terminus with a physical player grille; no connection to world terrain.
    t.box((18,4,2),(24,9,2),'stone_bricks');t.box((18,9,3),(24,9,6),'stone_bricks')
    for x in range(19,24):
        for y in range(6,9):t.put(x,y,6,'iron_bars',north='false',south='false',east='true',west='true',waterlogged='true' if y==6 else 'false')
    # Dry shelf stepping out of the open channel, not into the blind pipe.
    for z in (9,12):
        x=cx(z)-3;t.put(x,7,z,'mossy_stone_brick_stairs',facing='west',half='bottom',shape='straight',waterlogged='false')
        floor(t,x-1,z,8,'mossy_stone_bricks',2)
    # The old cleanseable hazard is a separate sunk trough, never part of the clear creek.
    for x in range(29,34):
        for z in range(45,51):
            for y in range(7,11):t.put(x,y,z,'mossy_stone_bricks' if y==10 else 'clay')
    for x in range(30,33):
        for z in range(46,50):
            p=(x,9,z);GLOOM.add(p);t.put(*p,'orimod:gloom_water',level='0');t.put(x,10,z,'air')

def wheel(t):
    # Literal reviewed nine-pixel drawing. Two thin rings, one empty axle-width
    # between them; no filler joints or forced fence links that turn it into a grid.
    rows=['...XXX...','.XX...XX.','.X..|..X.','X...|...X',
          'X---O---X','X...|...X','.X..|..X.','.XX...XX.','...XXX...']
    cy,cz=12,24;ring=[];immersed=[]
    for x in (22,24):
        for row,line in enumerate(rows):
            for col,symbol in enumerate(line):
                p=(x,cy+4-row,cz-4+col);wet=p in WATER
                if symbol=='X':
                    ring.append(p)
                    if wet:immersed.append(p)
                    if row in (1,2,6,7):
                        t.put(*p,'dark_oak_stairs',facing='south' if col<4 else 'north',half='bottom' if row<4 else 'top',shape='straight',waterlogged=str(wet).lower())
                    else:
                        t.put(*p,'dark_oak_planks');WATER.discard(p)
                elif symbol in '|-':
                    horizontal=symbol=='-'
                    t.put(*p,'dark_oak_fence',north=str(horizontal).lower(),south=str(horizontal).lower(),east='false',west='false',waterlogged=str(wet).lower())
                elif symbol=='.':
                    t.put(*p,'water',level='0') if wet else t.put(*p,'air')
    t.box((19,cy,cz),(29,cy,cz),'stripped_dark_oak_log',axis='x')
    for a in (18,28):
        pier(t,a,23,8,cy,2)
        t.put(a,cy+1,23,'mossy_stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
        t.put(a,cy,23,'verdant_froglight',axis='x')
    # Eight surviving paddle boards bridge the gap between the rings. None is
    # pasted onto their visible front/back faces or inside the hollow disc.
    for dy,dz in ((4,0),(3,-3),(0,-4),(-3,-3),(-4,0),(-3,3),(0,4),(3,3)):
        p=(23,cy+dy,cz+dz)
        t.put(*p,'dark_oak_trapdoor',facing='north' if dz<0 else 'south',half='top' if dy<0 else 'bottom',open=str(dy==0).lower(),powered='false',waterlogged=str(p in WATER).lower())
    assert {p[1] for p in immersed}=={8,9}
    return dict(center=(23,cy,cz),radius=4,planes=[22,24],drawing=rows,ring=ring,immersed=immersed,spokes=4,paddles=8,pit=[(21,8,20),(26,9,28)],bank_y=9,visible_rows_above_bank=7)

def sluice_facade(t):
    # The accepted collision shell stays intact. Blind arch recesses are built
    # outside it; they cannot become a new route to the dark bridge reward.
    for x in range(30,40):
        for z in range(32,45):
            if x not in (30,39) and z not in (32,44):continue
            for y in range(10,22):
                if name(t,(x,y,z))=='minecraft:air':continue
                material='cracked_stone_bricks' if noise(x,y,z)<9 else 'mossy_stone_bricks'
                if y in (13,18):material='tuff_bricks'
                if name(t,(x,y,z)).endswith('stone_bricks'):t.put(x,y,z,material)
    # Broad fallen crest, not alternating battlements or an enclosing second roof.
    for x in range(30,40):
        for z in range(32,45):
            if x not in (30,39) and z not in (32,44):continue
            height=max(0,round(1+1.5*math.sin(x*.51+z*.43)+.7*math.cos(z*.8)))
            for y in range(22,22+height):t.put(x,y,z,'mossy_stone_bricks' if noise(x,y,z)<18 else 'cracked_stone_bricks')
    for x,z in ((29,34),(29,42),(40,34),(40,42),(32,45),(38,45)):
        base=LAND.get((x,z),ground(x,z));pier(t,x,z,base,18)
        t.put(x,19,z,'mossy_stone_brick_stairs',facing='east' if x<35 else 'west',half='bottom',shape='straight',waterlogged='false')
    # Recessed Gothic windows: the original wall is the dark back plane.
    for side in (29,40):
        facing='east' if side==29 else 'west'
        for cz in (37,40):
            for z in (cz-1,cz+1):
                for y in range(14,18):t.put(side,y,z,'tuff_bricks')
            for z in range(cz-1,cz+2):
                t.put(side,13,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
                t.put(side,18 if z==cz else 17,z,'mossy_stone_brick_stairs',facing='north' if z<cz else 'south',half='top',shape='straight',waterlogged='false')
                for y in range(14,17):t.put(30 if side==29 else 39,y,z,'polished_deepslate')
    # A vertical gate groove and a broken panel on the river-facing wall.
    for z in range(36,40):
        for y in range(10,14):t.put(29,y,z,'stripped_dark_oak_log' if z<38 else 'dark_oak_trapdoor',**({'axis':'y'} if z<38 else dict(facing='west',half='bottom',open='true',powered='false',waterlogged='false')))
    for x,z in ((29,33),(40,43),(37,45)):
        base=LAND.get((x,z),ground(x,z))
        if (x,z) not in LAND:foundation(t,x,z,base)
        t.put(x,base+1,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
    # Roof rubble breaks the flat outline while staying on the sealed roof.
    for x,z in ((31,34),(32,35),(36,42),(37,42),(38,43)):
        t.put(x,22,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')

def bridge_piers(t):
    # All four faces, not only the side hidden from the approach camera. Niches
    # are one block deep, backed by solid masonry; the complete landing stays.
    for x0,z0,x1,z1,base,top in ((10,31,15,38,9,14),(16,51,22,57,13,18)):
        for x in range(x0,x1+1):
            for z in range(z0,z1+1):
                if x not in (x0,x1) and z not in (z0,z1):continue
                for y in range(base+1,top):
                    if name(t,(x,y,z)) in ('minecraft:stone','minecraft:stone_bricks'):
                        t.put(x,y,z,'cracked_stone_bricks' if (x+z+y)%5==0 else 'mossy_stone_bricks')
        faces=[([(x0,z) for z in range(z0+1,z1)],(1,0),'north','south','west'),
               ([(x1,z) for z in range(z0+1,z1)],(-1,0),'north','south','east'),
               ([(x,z0) for x in range(x0+1,x1)],(0,1),'west','east','north'),
               ([(x,z1) for x in range(x0+1,x1)],(0,-1),'west','east','south')]
        for cells,(dx,dz),left,right,outside in faces:
            for i,(x,z) in enumerate(cells):
                rise=1 if i in (0,len(cells)-1) else 3
                for y in range(base+1,base+rise+1):
                    t.put(x,y,z,'air')
                    t.put(x+dx,y,z+dz,'cracked_stone_bricks')
                t.put(x,base+rise+1,z,'mossy_stone_brick_stairs',facing=left if i<(len(cells)-1)/2 else right,half='top',shape='straight',waterlogged='false')
            x,z=cells[len(cells)//2]
            glow(t,x,base,z)
            # Wet luminous moss inside each blind arch; no glyph or harvestable fruit.
            t.put(x,base+1,z,'glow_lichen',waterlogged='false',**{ {'west':'east','east':'west','north':'south','south':'north'}[outside]:'true'})
        # The protected landing rectangle is unmodified. An irregular, tapering
        # broken cornice OUTSIDE it changes the silhouette instead of deleting
        # required landing cells. Leave the near Bash approach and the west C2
        # return open; additions never enter water, wind or the dark bridge.
        for x,z,dx,dz in [(x,z0-1,0,-1) for x in range(x0,x1+1)]+[(x,z1+1,0,1) for x in range(x0,x1+1)]+[(x0-1,z,-1,0) for z in range(z0,z1+1)]+[(x1+1,z,1,0) for z in range(z0,z1+1)]:
            if (z<z0 and top==14) or (x<x0 and top==18):continue
            if name(t,(x,top-1,z))!='minecraft:air' or (x,top-1,z) in WATER|FLOW:continue
            k=(x*17+z*11)%7
            if k in (0,3):continue # broken corners and missing one/two-block groups
            facing='west' if dx<0 else 'east' if dx>0 else 'north' if dz<0 else 'south'
            t.put(x,top-1,z,'mossy_stone_brick_stairs',facing=facing,half='top',shape='straight',waterlogged='false')
            if k in (1,2,5):t.put(x,top,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
            if k==4 and name(t,(x+dx,top-2,z+dz))=='minecraft:air':
                t.put(x+dx,top-2,z+dz,'mossy_stone_brick_stairs',facing=facing,half='top',shape='straight',waterlogged='false')
            face={'west':'east','east':'west','north':'south','south':'north'}[facing]
            if name(t,(x,top-2,z))=='minecraft:air':t.put(x,top-2,z,'vine',**{face:'true'})
        # Hanging moss off solid corner masonry, never across the top landing.
        for x,z,face in ((x0-1,z0+1,'east'),(x1+1,z1-1,'west')):
            if name(t,(x,base+2,z))=='minecraft:air':t.put(x,base+2,z,'vine',**{face:'true'})


def broken_bridge_wings(t):
    # The central landing rectangles and all flight corridors are immutable.
    # Remnant arch abutments and fallen haunches outside them break the silhouette.
    for x,z,base,heights,facing in ((16,38,9,[7,5,2],'north'),(23,55,13,[7,4,2],'west')):
        for i,h in enumerate(heights):
            a,b=(x+i,z) if facing=='north' else (x,z+i)
            if (a,b) not in LAND:foundation(t,a,b,base)
            for y in range(base+1,base+h):
                t.put(a,y,b,'mossy_stone_bricks' if noise(a,y,b)>8 else 'cracked_stone_bricks')
            t.put(a,base+h,b,'mossy_stone_brick_stairs',facing=facing,half='bottom',shape='straight',waterlogged='false')
            MEMBERS.append((a,b,base,base+h-1))
            # Vines cling to the exterior side of a solid remnant, not in free air.
            vx,vz=(a,b+1) if facing=='north' else (a+1,b)
            for y in range(base+2,base+h-1):
                if name(t,(vx,y,vz))=='minecraft:air':t.put(vx,y,vz,'vine',**({'north':'true'} if facing=='north' else {'west':'true'}))
        # Rubble slopes down to the same solid ground; no detached cubes.
        for a,b in ((x+1,z+1),(x+2,z+1),(x+2,z+2)):
            floor_y=LAND.get((a,b),ground(a,b))
            if (a,b) not in LAND:foundation(t,a,b,floor_y)
            if name(t,(a,floor_y+1,b))=='minecraft:air':
                t.put(a,floor_y+1,b,'mossy_stone_brick_slab',type='bottom',waterlogged='false')
    # One tall broken buttress and a broad diagonal rubble shoulder on the
    # outside of the sealed gatehouse. The original inside shell is untouched.
    for x,z,top in ((40,43,25),(41,43,22),(42,43,18),(40,44,20),(41,44,17)):
        base=LAND.get((x,z),ground(x,z));pier(t,x,z,base,top)
        t.put(x,top+1,z,'mossy_stone_brick_slab',type='bottom',waterlogged='false')

def broken_top_silhouettes(t):
    # North/south flight and return corridors remain empty. All crown rubble is
    # OUTSIDE the frozen landing rectangles; no top floor cell is removed.
    def remnant(x,z,top,facing='east'):
        base=LAND.get((x,z),ground(x,z))
        if (x,z) not in LAND:foundation(t,x,z,base)
        if top<=base:return
        for y in range(base+1,top):t.put(x,y,z,'mossy_stone_bricks' if noise(x,y,z)>6 else 'cracked_stone_bricks')
        t.put(x,top,z,'mossy_stone_brick_stairs',facing=facing,half='bottom',shape='straight',waterlogged='false')
        MEMBERS.append((x,z,base,top-1))
    # C1: asymmetric corner haunches, one standing wall and two retreating faces.
    for x,z,h in [(8,30,11),(9,30,13),(16,30,12),(17,30,10),
                  (9,31,17),(9,32,16),(9,33,14),(9,34,12),(9,39,12),
                  (8,31,15),(8,32,13),(8,33,11),
                  (16,31,13),(16,32,15),(16,33,16),(16,37,17),(16,38,16),(16,39,13)]:
        remnant(x,z,h)
    for x,h in zip(range(10,16),[13,12,11,12,13,12]):remnant(x,39,h,'south')
    # C2 is the pier visible in the accepted wind-landing camera. Keep the full
    # glide landing and its west return clear, dress the far/east edges only.
    for x,z,h in [(23,51,17),(23,52,19),(23,53,21),(23,54,20),(23,56,18),(23,57,16),
                  (24,52,17),(24,53,18),(24,54,16),(15,55,20),(15,56,21),(15,57,19)]:
        remnant(x,z,h)
    for x,h in zip(range(16,24),[18,20,21,20,18,17,18,16]):remnant(x,58,h,'south')
    # Vines stay attached to an outside solid side; never hang across a landing.
    for x,y,z,face in [(8,15,31,'east'),(8,14,31,'east'),(17,15,37,'west'),(17,14,37,'west'),(24,19,53,'west'),(24,18,53,'west')]:
        if name(t,(x,y,z))=='minecraft:air':t.put(x,y,z,'vine',**{face:'true'})
    # Original sealed roof is y21. Replace only the old cosmetic crest above it,
    # with large unequal remnants and broad collapsed gaps, not small battlements.
    t.box((30,22,32),(39,29,44),'air')
    north=[22,24,24,23,21,21,22,24,24,22]
    south=[21,22,24,24,23,22,22,21,21,21]
    west=[22,24,24,23,22,21,21,22,25,25,24,22,21]
    east=[22,23,25,25,24,22,22,23,24,24,22,21,21]
    for x in range(30,40):
        for z in range(32,45):
            choices=[]
            if z==32:choices.append(north[x-30])
            if z==44:choices.append(south[x-30])
            if x==30:choices.append(west[z-32])
            if x==39:choices.append(east[z-32])
            if not choices:continue
            top=min(choices)
            for y in range(22,top):t.put(x,y,z,'mossy_stone_bricks' if noise(x,y,z)>8 else 'cracked_stone_bricks')
            if top>21:t.put(x,top,z,'mossy_stone_brick_stairs',facing='north' if z==32 else 'south' if z==44 else 'east' if x==39 else 'west',half='bottom',shape='straight',waterlogged='false')
    # South-east corner falls diagonally to ground beyond the sealed shell.
    for x,z,top in [(40,45,20),(41,46,17),(42,47,14),(43,48,13)]:remnant(x,z,top,'south')
    # Two broken roof beams have supported roots and unequal cut-off ends.
    for x,z,top in [(32,35,24),(37,41,25)]:
        for y in range(22,top+1):t.put(x,y,z,'cracked_stone_bricks')
        t.put(x,22,z,'verdant_froglight',axis='y')
    t.box((32,25,35),(35,25,35),'stripped_dark_oak_log',axis='x')
    t.put(35,24,35,'dark_oak_stairs',facing='east',half='top',shape='straight',waterlogged='false')
    t.box((37,26,39),(37,26,41),'stripped_dark_oak_log',axis='z')


def exposed_node_faces(t,nodes):
    details=[]
    transparent={'minecraft:air','minecraft:water','minecraft:glow_lichen','minecraft:moss_carpet','minecraft:vine','minecraft:cave_vines','minecraft:cave_vines_plant'}
    for p in nodes:
        faces=[]
        for d in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
            q=tuple(p[i]+d[i] for i in range(3));n=name(t,q)
            if n in transparent or n.endswith(('_leaves','_stairs','_slab','_wall','_fence')) or n.startswith('orimod:') and n!='orimod:great_spirit_root':faces.append(d)
        assert len(faces)<=2,('node has too many visible faces',p,faces)
        details.append(dict(position=p,faces=faces))
    return dict(blocks=len(nodes),faces=sum(len(q['faces']) for q in details),per_node=details)

def audit_support(t):
    # Checked on every generation and --check; no legacy products are involved.
    missing=sorted(FLOORS-LAND.keys())
    gaps=[]
    for x,z,base in sorted(WALL_FEET|{(x,z,b) for x,z,b,_ in MEMBERS}):
        for y in range(base+1):
            if name(t,(x,y,z)) in ('minecraft:air','minecraft:water','minecraft:glow_lichen'):
                gaps.append((x,y,z))
    roots={p for p,s in t.blocks.items() if s[0]=='orimod:great_spirit_root'}
    floating=[];seen=set();directions=((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))
    for p in sorted(roots):
        if p in seen:continue
        component=[];queue=[p];seen.add(p);supported=False
        while queue:
            q=queue.pop();component.append(q)
            for d in directions:
                r=tuple(q[i]+d[i] for i in range(3));n=name(t,r)
                if r in roots:
                    if r not in seen:seen.add(r);queue.append(r)
                elif n not in ('minecraft:air','minecraft:water','minecraft:glow_lichen','minecraft:vine','minecraft:cave_vines','minecraft:cave_vines_plant','minecraft:moss_carpet') and not n.endswith('_leaves'):
                    supported=True
        if not supported:floating.append(component)
    assert not missing,('floor/path not in LAND',missing)
    assert not gaps,('ground member lacks continuous support',gaps)
    assert not floating,('floating root components',floating)
    return dict(floor_columns=len(FLOORS),missing_floor_columns=len(missing),ground_members=len(WALL_FEET)+len(MEMBERS),support_gaps=len(gaps),root_blocks=len(roots),floating_root_components=len(floating))

def build(out):
    for s in (WATER,FLOW,GLOOM,FLOORS,WALL_FEET):s.clear()
    for s in (ROOTS,MEMBERS,WEIRS):s.clear()
    LAND.clear();t=Template(out,'creek_ruins_v1',SIZE)
    for x in range(2,44):
        for z in range(2,67):
            if ((x-22)/22)**2+((z-34)/35)**2<1+.045*math.sin(z*.38) and z<=min(66,65+round(1.2*math.sin(x*.23+.5))):
                y=ground(x,z);foundation(t,x,z,y)
                if z>=57:
                    patch=math.sin(x*.31+z*.28)+.6*math.cos(x*.53-z*.19)
                    t.put(x,y,z,'gravel' if abs(x-(35+round(2.5*math.sin((z-54)*.27))))<=(2 if 60<=z<=62 or 68<=z<=69 else 1) else 'coarse_dirt' if patch>.65 else 'andesite' if patch<-.8 else 'moss_block' if patch<-.2 else 'grass_block')
                for yy in range(y+1,min(35,y+16)):t.put(x,yy,z,'air')
    for x in range(18,27):
        for z in range(0,12):
            foundation(t,x,z,9)
            for y in range(10,24):t.put(x,y,z,'air')
    # Public dry banks and the explicit descending return, before water and gameplay.
    path(t,[(22,9,0),(22,9,10),(8,9,11),(6,9,18),(6,9,29),(5,9,38),(12,9,40),(12,9,49),(7,9,49)],1)
    path(t,[(22,9,10),(40,9,16),(41,9,30),(40,11,37),(41,11,39),(42,13,45),(40,13,47),(37,15,53)],1)
    deck(t,(4,7),(13,15),9);walls(t,3,7,14,16,9)
    deck(t,(6,18),(16,28),9);walls(t,5,18,17,29,9)
    deck(t,(10,25),(15,32),9)
    # Four organic trees, thick below the crown and sparse light nodes added separately.
    for a in ((4,22,16,3),(38,14,18,-3),(9,57,15,3),(35,58,14,-3)):tree(t,*a)
    root(t,[(16,9,3),(17,15,3),(21,18,4),(26,14,3),(27,9,3)],2)
    for x in (16,27):pier(t,x,3,9,14,2)
    # Broken pillars with irregular spacing and heights, all rooted in ground.
    for x,z,h in ((7,6,4),(14,16,2),(36,18,6),(36,30,3),(5,59,2),(34,58,5)):
        y=LAND.get((x,z),ground(x,z));pier(t,x,z,y,y+h)
    water(t)
    # Low public arch: enough room below to leave the open water, blind pipe is grated.
    for x in range(14,39):
        for z in range(14,17):t.put(x,9,z,'stone_bricks')
    for x in (15,29):
        for z in (14,16):
            for i in range(3):t.put(x+(i if x==15 else -i),8-i//2,z,'mossy_stone_brick_stairs',facing='east' if x==15 else 'west',half='top',shape='straight',waterlogged='false')
    wheel_info=wheel(t)
    # Main Bash rise. Clearing here protects the actual launch corridor from tree dressing.
    t.box((10,10,25),(15,20,32),'air')
    # Broad near edge accepts direct Bash without requiring a preparatory jump:
    # steep releases used to strike the old z33 face just below the deck.
    deck(t,(10,31),(15,38),14)
    # Support the non-colliding lantern from below; an overhead root used to
    # catch the player's head on shallower A -> C1 launch trajectories.
    root(t,[(8,9,29),(8,11,29),(10,12,29),(12,12,29)],1)
    t.put(12,13,29,'orimod:bash_anchor',lit='false')
    for x in list(range(10,17))+list(range(27,34)):
        for z in (34,35,36):t.put(x,14,z,'stone_bricks')
    for x,sign in ((16,-1),(27,1)):
        for j in range(4):
            for z in (34,36):t.put(x+sign*j,13-j//2,z,'mossy_stone_brick_stairs',facing='east' if sign<0 else 'west',half='top',shape='straight',waterlogged='false')
    # Dry cross-wind ledge, fully separate from the high clear-water channel.
    deck(t,(15,40),(19,46),9)
    updrafts=[]
    for x in range(16,19):
        for z in range(42,46):
            updrafts.append((x,10,z));t.put(x,10,z,'orimod:updraft')
            for y in range(11,25):t.put(x,y,z,'air')
    deck(t,(16,51),(22,57),18)
    t.put(20,19,55,'orimod:energy_cell_pedestal')
    for a,b,y in (((12,51),(14,54),15),((9,50),(11,53),12),((6,49),(8,52),9),((7,35),(9,38),11),((4,35),(6,38),9)):deck(t,a,b,y)
    # Optional dark stair: enclosed bearing-house shaft, no back door into the reward.
    t.box((30,9,32),(39,21,44),'mossy_stone_bricks');t.box((31,10,33),(38,20,43),'air')
    t.box((32,10,32),(34,12,34),'air')
    for x in range(31,39):
        for z in range(35,39):
            t.put(x,8,z,'clay');t.put(x,9,z,'water',level='0');WATER.add((x,9,z))
    platforms=[(33,10,35),(34,11,35),(35,12,35),(35,13,36),(34,14,36)]
    for p in platforms:t.put(*p,'orimod:spirit_platform',lit='false',fading='false',blink='false')
    t.box((33,14,39),(35,14,42),'mossy_stone_bricks')
    t.box((36,11,39),(37,11,41),'mossy_stone_bricks')
    t.put(34,15,41,'orimod:spirit_light_vessel')
    # Broad arch mouth and broken outside ribs; roof remains for anti-bypass geometry.
    for x in (31,35):t.put(x,13,32,'mossy_stone_brick_stairs',facing='east' if x==31 else 'west',half='top',shape='straight',waterlogged='false')
    sluice_facade(t)
    bridge_piers(t)
    broken_bridge_wings(t)
    broken_top_silhouettes(t)
    # Permanent wet-moss glints on the axle and blind arch lintels. These cover
    # half-block landings too, which the coarse authoring light flood cannot see.
    for x,y,z,face in ((29,19,40,'east'),(40,18,38,'west'),(38,20,45,'north')):
        assert name(t,(x,y,z))=='minecraft:air',('wet moss would replace a fixture',x,y,z)
        t.put(x,y,z,'glow_lichen',waterlogged='false',**{face:'true'})
    # The outside lintel can land on a chunk border. Its wall-mounted lichen
    # may be validated before the north support slice exists; the independent
    # solid source keeps that half-step lit even if the decorative lichen drops.
    assert name(t,(38,20,44))=='minecraft:cracked_stone_bricks'
    t.put(38,20,44,'verdant_froglight',axis='y')
    # Dry old-floor secret, with a ladder back to its own crack.
    t.box((4,3,41),(10,9,48),'stone_bricks');t.box((5,4,42),(9,8,47),'air')
    t.box((6,9,43),(8,9,45),'orimod:cracked_ruin_floor')
    for y in range(4,10):t.put(9,y,44,'ladder',facing='west',waterlogged='false')
    t.put(7,4,45,'orimod:spirit_light_vessel')
    for p in ((6,10,13),(36,16,52)):t.put(*p,'orimod:spirit_light_vessel')
    # Independent baselines at each target, never dependent on harvestable fruit.
    for x,y,z in ((7,9,10),(9,9,12),(9,9,21),(13,9,23),(11,9,26),(12,14,35),(17,18,53),(21,18,56),(32,9,33),(35,14,40),(6,3,44),(8,3,46)):
        glow(t,x,y,z)
    for x,z in ((15,41),(19,41),(15,46),(19,46)):glow(t,x,9,z)
    nodes=[(21,17,4),(8,9,11),(7,9,22),(10,9,29),(16,9,41),(30,12,35),(21,18,56)]
    for p in nodes:
        x,y,z=p
        for yy in range(LAND.get((x,z),9)+1,y):
            if name(t,(x,yy,z))=='minecraft:air':t.put(x,yy,z,'orimod:great_spirit_root',light='none')
        t.put(*p,'orimod:great_spirit_root',light='knot')
    # Only the front of the arch marker is exposed; the rest is embedded in wood.
    for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1)):
        t.put(21+dx,17+dy,4+dz,'orimod:great_spirit_root',light='none')
    for x,y,z in ((5,14,22),(36,15,30)):
        for yy in range(LAND.get((x,z),9)+1,y):
            if name(t,(x,yy,z))=='minecraft:air':t.put(x,yy,z,'orimod:great_spirit_root',light='none')
        t.put(x,y,z,'orimod:great_spirit_root',light='none');shared.berries(t,x,y-1,z,2)
    assembly(t,'creek_ruins_shrine',SHRINE);assembly(t,'map_stone',MAP)
    # Keep all grounded architectural columns in the authored support mask.
    for x,z,base,_ in MEMBERS:
        if (x,z) not in LAND:foundation(t,x,z,base)
        for y in range(base):
            if name(t,(x,y,z))=='minecraft:air':t.put(x,y,z,'dirt')
    # Crown surfaces count too: non-harvestable lichen, rather than fruit,
    # lights the exposed leaf tops without adding any glyph nodes.
    crown_light=[]
    for (x,y,z),(n,_) in sorted(list(t.blocks.items()),key=lambda e:(e[0][1],e[0][2],e[0][0])):
        p=(x,y+1,z)
        if n.endswith('_leaves') and name(t,p)=='minecraft:air' and name(t,(x,y+2,z))=='minecraft:air':
            if not any(sum(abs(p[i]-q[i]) for i in range(3))<=4 for q in crown_light):
                t.put(*p,'glow_lichen',down='true',waterlogged='false');crown_light.append(p)
    # Never move a checkpoint's physical floor upward by a decorative carpet.
    for a,b in (((11,10,25),(13,12,27)),((11,15,34),(13,17,36)),((17,19,53),(19,21,55))):
        for x in range(a[0],b[0]+1):
            for y in range(a[1],b[1]+1):
                for z in range(a[2],b[2]+1):
                    if name(t,(x,y,z))=='minecraft:moss_carpet':t.put(x,y,z,'air')
    audit=audit_support(t)
    node_audit=exposed_node_faces(t,nodes)
    old_size=shared.SIZE;shared.SIZE=SIZE
    try:lighting=shared.light(t)
    finally:shared.SIZE=old_size
    # Unlit actor blocks do not supply the light guarantee.
    layout=dict(size=SIZE,shrine=SHRINE,safe=(12.5,10,25.5),entry=(22.5,10,.5),surface_y=10,approach_y=10,
        checkpoints=[dict(position=(12.5,15,35.5),action='bash',count=1),dict(position=(18.5,19,54.5),action='glide',count=1)],
        critical=[dict(position=p,block='orimod:'+n) for p,n in ((SHRINE,'creek_ruins_shrine'),((12,13,29),'bash_anchor'),((17,10,43),'updraft'))],
        clearances=[{'from':(20,10,0),'to':(24,12,2)},{'from':(11,10,25),'to':(13,12,27)},{'from':(11,15,34),'to':(13,17,36)},{'from':(16,11,42),'to':(18,22,45)},{'from':(17,19,53),'to':(19,21,55)}],
        cells=[dict(position=(20,19,55),kind='energy')],map_stone=MAP,updrafts=updrafts,
        gloom_water={'from':(30,9,46),'to':(32,9,49)},secret=dict(entrance=(7,9,44),facing='up',blocks='floor'),beetles=[(15,10,11),(35,15,49)],
        bridges=[dict(entry=(33,10,34),exit=(34,15,40),platforms=platforms,lanterns=[],escape=[(36,12,40),(36,9,38),(32,10,34),(33,10,34)],facing='south')])
    for box in layout['clearances']:
        a,b=box['from'],box['to']
        for x in range(a[0],b[0]+1):
            for y in range(a[1],b[1]+1):
                for z in range(a[2],b[2]+1):assert name(t,(x,y,z)) in ('minecraft:air','minecraft:glow_lichen','minecraft:moss_carpet'),('blocked clearance',x,y,z,name(t,(x,y,z)))
    for p in WATER|FLOW:assert name(t,p)=='minecraft:water' or dict(t.blocks[p][1]).get('waterlogged')=='true',('overwritten water',p,name(t,p))
    # Bound all source products; no silent writes outside the declared template.
    for p in t.blocks:assert all(0<=v<SIZE[i] for i,v in enumerate(p)),p
    palette=[];indices={};blocks=[]
    for p,s in sorted(t.blocks.items(),key=lambda e:(e[0][1],e[0][2],e[0][0])):
        if s not in indices:
            indices[s]=len(palette);v={'Name':nbt.String(s[0])}
            if s[1]:v['Properties']=nbt.Compound({k:nbt.String(v) for k,v in s[1]})
            palette.append(nbt.Compound(v))
        blocks.append(nbt.Compound(pos=nbt.List[nbt.Int](p),state=nbt.Int(indices[s])))
    f=nbt.File(dict(DataVersion=nbt.Int(3955),size=nbt.List[nbt.Int](SIZE),palette=nbt.List[nbt.Compound](palette),blocks=nbt.List[nbt.Compound](blocks),entities=nbt.List[nbt.Compound]()))
    buf=io.BytesIO();f.write(buf)
    dest=out/'src/main/resources/data/orimod/structure/ruins/creek_ruins_v1.nbt';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(compressed_nbt(buf.getvalue()))
    contract=out/'src/main/resources/orimod/ruin_layout/creek_ruins_v1.json';write(contract,layout)
    flow_file=out/'src/main/resources/orimod/ruin_layout/creek_ruins_flow_v1.json';write(flow_file,sorted(FLOW))
    terrain=out/'src/main/resources/orimod/ruin_layout/creek_ruins_terrain_v1.json'
    write(terrain,dict(size=SIZE,columns=[(x,z,y) for (x,z),y in sorted(LAND.items())],entrance=dict(x=22,half_width=2,floor_y=9),rear_from_z=50))
    src=out/'art/source/creek_ruins/v004'
    write(src/'manifest.json',dict(generator='scripts/art/build_creek_ruins.py',layoutVersion=1,artVersion=4,size=SIZE,sha256=hashlib.sha256(dest.read_bytes()).hexdigest(),blocks=len(blocks),palette=len(palette),water=sorted(WATER),flow=sorted(FLOW),weirs=WEIRS,gloom=sorted(GLOOM),roots=ROOTS,nodes=nodes,node_audit=node_audit,lighting=lighting,crown_light=crown_light,support_audit=audit,foundations=MEMBERS,wheel=wheel_info))
    def symbol(x,y,z):
        n=name(t,(x,y,z));return {'minecraft:air':' ','minecraft:water':'~','orimod:gloom_water':'g','orimod:creek_ruins_shrine':'S','orimod:map_stone':'M','orimod:bash_anchor':'B','orimod:updraft':'U','orimod:spirit_platform':'=','orimod:energy_cell_pedestal':'E','orimod:cracked_ruin_floor':'Q'}.get(n,'r' if n=='orimod:great_spirit_root' else '#' if not n.endswith(('lichen','carpet','vine')) else '.')
    (src/'plan.txt').write_text('Creek v1. Entry z0; +Z upstream. S shrine M map B Bash U dry wind E cell Q secret.\nPLAN y10\n'+'\n'.join(''.join(symbol(x,10,z) for x in range(45)) for z in range(67))+'\nLONG SECTION x22\n'+'\n'.join(''.join(symbol(22,y,z) for z in range(67)) for y in range(43,-1,-1))+'\nCROSS SECTION z43\n'+'\n'.join(''.join(symbol(x,y,43) for x in range(45)) for y in range(43,-1,-1))+'\n')
    return [dest,contract,terrain,flow_file,src/'manifest.json',src/'plan.txt']

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
    if a.check:
        with tempfile.TemporaryDirectory() as d:
            out=Path(d)
            for f in build(out):
                old=ROOT/f.relative_to(out)
                if not old.exists() or f.read_bytes()!=old.read_bytes():raise SystemExit('Mismatch: '+str(old))
        print('creek_ruins v1: all generated products match')
    else:
        for f in build(ROOT):print(f.relative_to(ROOT))
if __name__=='__main__':main()
