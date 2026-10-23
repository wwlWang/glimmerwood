#!/usr/bin/env python3
"""Reproduce the original architectural overlay independently of the accepted exterior."""
import argparse,hashlib,json,os,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
source=root/'src/main/java/cn/wwl/orimod/world/GreatTreeInterior.java'
out=root/'art/source/great_tree_interior/v002'
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
java=Path(os.environ.get('JAVA_HOME',Path.home()/'.local/opt/jdk-21'))/'bin'
with tempfile.TemporaryDirectory(prefix='ori-interior-') as d:
 d=Path(d);runner=d/'Export.java';runner.write_text('''import cn.wwl.orimod.world.GreatTreeInterior; public class Export { public static void main(String[] a){var s=GreatTreeInterior.layout();s.blocks().forEach((p,c)->System.out.println("["+p.x()+","+p.y()+","+p.z()+","+c+"]"));}}''')
 subprocess.run([str(java/'javac'),'-d',str(d),str(source),str(runner)],check=True)
 cells=sorted(json.loads(line) for line in subprocess.check_output([str(java/'java'),'-cp',str(d),'Export'],text=True).splitlines())
layout=(json.dumps({'format':'x,y,z,palette','cells':cells},separators=(',',':'))+'\n').encode()
manifest={'version':1,'generator':'scripts/art/build_great_tree_interior.py','source':str(source.relative_to(root)),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'layout_sha256':hashlib.sha256(layout).hexdigest(),'palette':{'1':'great_spirit_wood','4':'air','5':'great_spirit_wood[vein=true]','6':'great_tree_heart','7':'energy_cell_pedestal','8':'dark_oak_stairs[north]','9':'dark_oak_stairs[east]','10':'dark_oak_stairs[south]','11':'dark_oak_stairs[west]','12':'great_spirit_root[light=none]','13':'great_spirit_root[light=vein]','14':'great_spirit_root[light=knot]'},'entry':[0,0,-18],'heart':[2,25,1],'root_cave_reward':[-1,-8,3],'recipe':'Two-wide three-turn root staircase, 24 block rise. Three-high headroom, open stairwell through hall slab. Short flared root arch, nine stair windows, light-14 knots every six treads, domed hall with three sky shafts; fine interior sap lines, lined root cave with reversible stair.'}
outputs={out/'layout.json':layout,out/'manifest.json':(json.dumps(manifest,indent=2)+'\n').encode()}
for path,data in outputs.items():
 if args.check:
  assert path.exists() and path.read_bytes()==data,f'Mismatch: {path}'
 else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'Great tree interior: {len(cells)} cells; '+('reproduction passed, written=0' if args.check else 'generated'))
