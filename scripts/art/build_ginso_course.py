#!/usr/bin/env python3
"""Export the saved v2 course recipe and cushioning-root resource without Minecraft bootstrapping.
The tiny data stubs only supply immutable coordinates/ability names; the layout builder is the
production Java source. JUnit additionally compares this export against real Minecraft BlockPos.
"""
import argparse,hashlib,json,os,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'src/main/java/cn/wwl/orimod/world'
out=root/'art/source/ginso_course/v002'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
java=Path(os.environ.get('JAVA_HOME',Path.home()/'.local/opt/jdk-21'))/'bin'
inputs=[source/name for name in ('GinsoInterior.java','GinsoCourses.java','TallGinsoInterior.java')]
with tempfile.TemporaryDirectory(prefix='ori-ginso-course-') as temp:
 temp=Path(temp)
 stubs={
 'BlockPos.java':'package net.minecraft.core; public record BlockPos(int x,int y,int z){public static final BlockPos ZERO=new BlockPos(0,0,0);public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}}',
 'Ability.java':'package cn.wwl.orimod.progression; public enum Ability {BASH,GRAPPLE,GLIDE;public final int bit=1<<ordinal();}',
 'GinsoShape.java':'package cn.wwl.orimod.world; public final class GinsoShape{public static int checkedVersion(int v){if(v!=2&&v!=3)throw new IllegalArgumentException();return v;}}',
 'Export.java':'''import cn.wwl.orimod.world.GinsoCourses; public class Export {public static void main(String[] a){var c=GinsoCourses.layout(2);c.blocks().forEach((p,b)->System.out.println("["+p.getX()+","+p.getY()+","+p.getZ()+","+b+"]"));}}'''}
 for name,text in stubs.items():(temp/name).write_text(text)
 subprocess.run([str(java/'javac'),'-d',str(temp),*[str(p) for p in inputs],*[str(temp/n) for n in stubs]],check=True)
 cells=sorted(json.loads(s) for s in subprocess.check_output([str(java/'java'),'-cp',str(temp),'Export'],text=True).splitlines())
def data(v):return (json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode()
layout=(json.dumps({'format':'x,y,z,palette','cells':cells},separators=(',',':'))+'\n').encode()
assets={
 'assets/orimod/blockstates/ginso_landing.json':{'variants':{'':{'model':'orimod:block/ginso_landing'}}},
 'assets/orimod/models/block/ginso_landing.json':{'parent':'orimod:block/ginso_wood_moss'},
 'assets/orimod/models/item/ginso_landing.json':{'parent':'orimod:block/ginso_landing'},
 'data/orimod/loot_table/blocks/ginso_landing.json':{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'orimod:ginso_landing'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}}
manifest={'version':2,'generator':'scripts/art/build_ginso_course.py','shape_course_pairs':{'2':1,'3':2},
 'sources':{str(s.relative_to(root)):hashlib.sha256(s.read_bytes()).hexdigest() for s in inputs},'layout_sha256':hashlib.sha256(layout).hexdigest(),
 'palette':{'1':'ginso_wood','4':'air','7':'great_spirit_root[light=knot]','8':'bash_anchor','9':'grapple_flower','10':'updraft','11':'glades_well','12':'dark_oak_stairs[east]','13':'dark_oak_stairs[south]','14':'ginso_landing','15':'connected dark_oak_fence','16':'dark_oak_door[half=lower]','17':'dark_oak_door[half=upper]','18':'cyan_stained_glass','19':'dark_oak_slab[type=bottom]'},
 'sections':{'wall':[4,20],'bash':[20,38],'grapple':[38,56],'wind':[56,78],'jump':[78,93]},'well_positions':[[0,21,3],[0,51,3],[0,94,3]],'heart_reserved':[0,94,0],
 'recipe':'Alternating supported root landings, short unmodified ability hops, illuminated bark windows; closed-door descent lane with alternating cushioning catches. The water-heart and upper escape route remain reserved; escape is not implemented.',
 'resource_sha256':{k:hashlib.sha256(data(v)).hexdigest() for k,v in assets.items()}}
outputs={out/'layout.json':layout,out/'manifest.json':data(manifest),**{root/'src/main/resources'/k:data(v) for k,v in assets.items()}}
for path,content in outputs.items():
 if args.check:assert path.exists() and path.read_bytes()==content,f'Mismatch: {path}'
 else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
print(f'Ginso course v2: {len(cells)} cells; written={0 if args.check else len(outputs)}')
