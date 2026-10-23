#!/usr/bin/env python3
"""Reproduce the enlarged architectural overlay; the accepted legacy overlay remains separate."""
import argparse,hashlib,json,os,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'src/main/java/cn/wwl/orimod/world/GreatTreeScaleInterior.java'
out=root/'art/source/great_tree_interior/v003'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
java=Path(os.environ.get('JAVA_HOME',Path.home()/'.local/opt/jdk-21'))/'bin'
with tempfile.TemporaryDirectory(prefix='ori-interior-') as d:
 d=Path(d);runner=d/'Export.java';runner.write_text('''import cn.wwl.orimod.world.GreatTreeScaleInterior; public class Export { public static void main(String[] a){var s=GreatTreeScaleInterior.layout();s.blocks().forEach((p,c)->System.out.println("["+p.x()+","+p.y()+","+p.z()+","+c+"]"));}}''')
 subprocess.run([str(java/'javac'),'-d',str(d),str(source),str(source.with_name('GreatTreeInterior.java')),str(runner)],check=True)
 cells=sorted(json.loads(line) for line in subprocess.check_output([str(java/'java'),'-cp',str(d),'Export'],text=True).splitlines())
layout=(json.dumps({'format':'x,y,z,palette','cells':cells},separators=(',',':'))+'\n').encode()
manifest={'version':2,'generator':'scripts/art/build_great_tree_interior.py','source':str(source.relative_to(root)),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'layout_sha256':hashlib.sha256(layout).hexdigest(),'palette':{'1':'great_spirit_wood','4':'air','5':'great_spirit_wood[vein=true]','6':'great_tree_heart','7':'energy_cell_pedestal','8':'dark_oak_stairs[north]','9':'dark_oak_stairs[east]','10':'dark_oak_stairs[south]','11':'dark_oak_stairs[west]','12':'great_spirit_root[light=none]','13':'great_spirit_root[light=vein]','14':'great_spirit_root[light=knot]','16':'great_tree_wind','17':'dark_oak_fence','18':'great_tree_landing (dry updraft, zero fall damage)','19':'glass skylight'},'entry':[0,0,-24],'lookout':[4,218,0],'wind_base':[4,25,3],'heart':[2,25,1],'root_cave_reward':[-1,-8,3],'recipe':'Tall flared root arch, legacy three-turn lower stair and root cave, enlarged 19-block heart vault, real light every six treads and daylight windows. Two-wide reversible root staircase with corner landings climbs to y218 lookout; separate 3x3 continuous glide windway, fenced lookout and shaft. ShapeVersion1 keeps the original overlay.'}
landing={'parent':'minecraft:block/block','textures':{'top':'orimod:block/updraft_top','side':'orimod:block/updraft_side','particle':'orimod:block/updraft_top'},'elements':[{'from':[0,0,0],'to':[16,4,16],'faces':{'up':{'texture':'#top'},'down':{'texture':'#side','cullface':'down'},**{d:{'texture':'#side','uv':[0,12,16,16]} for d in ['north','south','east','west']}}}]}
landing_bytes=(json.dumps(landing,indent=2)+'\n').encode()
manifest['landing_model_sha256']=hashlib.sha256(landing_bytes).hexdigest()
manifest['safety']='Continuous root supports and rails preserve three-block route headroom. Wind 3x3 is reserved from supports; dry quarter-block root cushion nullifies fall damage without bounce or blocking lift.'
outputs={out/'landing_model.json':landing_bytes,root/'src/main/resources/assets/orimod/models/block/great_tree_landing.json':landing_bytes,out/'layout.json':layout,out/'manifest.json':(json.dumps(manifest,indent=2)+'\n').encode()}
for path,data in outputs.items():
 if args.check:
  assert path.exists() and path.read_bytes()==data,f'Mismatch: {path}'
 else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'Great tree interior: {len(cells)} cells; '+('reproduction passed, written=0' if args.check else 'generated'))
