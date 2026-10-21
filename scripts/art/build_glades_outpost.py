"""Original R107 treehouse outpost. Deterministic NBT and layout; --check never writes repo assets."""
import argparse, io, json, tempfile
from pathlib import Path
import nbtlib as nbt
from build_ruins import Template, compressed_nbt
ROOT=Path(__file__).resolve().parents[2]
SIZE=(31,18,31)
LAYOUT={'size':SIZE,'entry':[15,3,0],'well':[15,3,19],'merchants':[[7,3,13],[23,3,13]],'surface_y':3}

def build(root):
    t=Template(root,'glades_outpost',SIZE)
    t.box((0,0,0),(30,1,30),'dirt');t.box((0,2,0),(30,2,30),'grass_block')
    t.box((0,3,0),(30,17,30),'air')
    t.box((13,2,0),(17,2,26),'mossy_stone_bricks')
    t.box((4,2,10),(26,2,15),'mossy_stone_bricks')
    for cx in (8,22):
        t.box((cx-1,3,17),(cx+1,13,19),'orimod:spirit_log',axis='y')
        t.box((cx-4,8,12),(cx+4,8,21),'birch_planks')
        for x in (cx-4,cx+4):t.box((x,9,12),(x,11,21),'birch_fence')
        t.box((cx-4,9,21),(cx+4,10,21),'birch_fence')
        for dy,r in [(12,5),(13,4),(14,3),(15,1)]:t.box((cx-r,dy,17-r),(cx+r,dy,17+r),'orimod:spirit_leaves')
        # Open front doorway, round-eared residents' ground stalls beside the trunk.
        mx=7 if cx==8 else 23
        for x in (mx-2,mx+2):t.box((x,3,11),(x,6,14),'birch_fence')
        t.box((mx-2,6,11),(mx+2,6,14),'birch_slab',type='bottom',waterlogged='false')
        t.box((mx-1,3,11),(mx+1,3,11),'birch_slab',type='bottom',waterlogged='false')
        t.put(mx,5,12,'orimod:bash_anchor',lit='false')
    # A broad stair leads from the ground path to both houses; the bridge has railings.
    for n in range(5):
        t.box((14,3+n,5+n),(16,3+n,5+n),'birch_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
        if n:t.box((14,3,5+n),(16,2+n,5+n),'orimod:spirit_log',axis='y')
    t.box((4,8,10),(26,8,11),'birch_planks')
    t.box((13,8,9),(17,8,10),'birch_planks')
    t.box((12,8,16),(18,8,18),'birch_planks')
    for z in (15,19):t.box((12,9,z),(18,9,z),'birch_fence')
    for x in (12,18):
        t.box((x,10,17),(x,12,17),'chain',axis='y',waterlogged='false')
        t.put(x,9,17,'orimod:bash_anchor',lit='false')
    t.put(15,3,23,'campfire',facing='north',lit='true',signal_fire='false',waterlogged='false')
    for x,z in [(14,23),(16,23),(15,24)]:t.put(x,3,z,'mossy_cobblestone_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
    t.put(*LAYOUT['well'],'orimod:glades_well',facing='north')
    palette=[];indexes={};blocks=[]
    for pos,state in sorted(t.blocks.items(),key=lambda e:(e[0][1],e[0][2],e[0][0])):
        if state not in indexes:
            indexes[state]=len(palette);v={'Name':nbt.String(state[0])}
            if state[1]:v['Properties']=nbt.Compound({k:nbt.String(val) for k,val in state[1]})
            palette.append(nbt.Compound(v))
        blocks.append(nbt.Compound({'pos':nbt.List[nbt.Int](pos),'state':nbt.Int(indexes[state])}))
    f=nbt.File({'DataVersion':nbt.Int(3955),'size':nbt.List[nbt.Int](SIZE),'palette':nbt.List[nbt.Compound](palette),'blocks':nbt.List[nbt.Compound](blocks),'entities':nbt.List[nbt.Compound]()})
    stream=io.BytesIO();f.write(stream)
    return {'src/main/resources/data/orimod/structure/glades_outpost.nbt':compressed_nbt(stream.getvalue()),'src/main/resources/orimod/glades_outpost.json':(json.dumps(LAYOUT,indent=2)+'\n').encode()}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    for name,data in build(ROOT).items():
        out=ROOT/name
        if args.check:assert out.exists() and out.read_bytes()==data,f'Generated asset changed: {name}'
        else:out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
        print(('Verified ' if args.check else 'Wrote ')+name)
