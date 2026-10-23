#!/usr/bin/env python3
"""Document/reproduce the runtime voxel artwork (no GLB or copied block textures).
The pure Java shape is the authoritative source, used unchanged by worldgen and this exporter.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/main/java/cn/wwl/orimod/world/GreatTreeShape.java'
OUT=ROOT/'art/source/great_spirit_tree/v003/manifest.json'
parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
java=Path(os.environ.get('JAVA_HOME',Path.home()/'.local/opt/jdk-21'))/'bin'
with tempfile.TemporaryDirectory(prefix='orimod-great-tree-') as temp:
    temp=Path(temp)
    (temp/'Report.java').write_text('''
import cn.wwl.orimod.world.GreatTreeShape;
public class Report {
 public static void main(String[] args) {
  for(long seed:new long[]{115115,115116}) {
   var s=new GreatTreeShape(seed);int[] n=new int[6];int low=999,high=-999,minX=999,maxX=-999,minZ=999,maxZ=-999;
   for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)for(int y=-12;y<=82;y++){
    byte c=s.cell(x,y,z);n[c]++;
    if(c==2){low=Math.min(low,y);high=Math.max(high,y);minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
   }
   System.out.printf("{\\"seed\\":%d,\\"wood\\":%d,\\"leaves\\":%d,\\"vines\\":%d,\\"crown_y\\":[%d,%d],\\"crown_x\\":[%d,%d],\\"crown_z\\":[%d,%d]}%n",seed,n[1]+n[5],n[2],n[3],low,high,minX,maxX,minZ,maxZ);
  }
 }
}
''')
    subprocess.run([str(java/'javac'),'-d',str(temp),str(SOURCE),str(temp/'Report.java')],check=True)
    metrics=[json.loads(line) for line in subprocess.check_output([str(java/'java'),'-cp',str(temp),'Report'],text=True).splitlines()]
manifest={'version':1,'format':'runtime voxel sculpture','source':SOURCE.relative_to(ROOT).as_posix(),
          'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
          'package':'data/orimod/worldgen/structure/great_spirit_tree.json',
          'palette':['orimod:great_spirit_wood','orimod:spirit_leaves','orimod:spirit_vine'],
          'recipe':'Nine tapered buttresses; bent hollow bole; nine curved and forked boughs; three separated tiers of flattened five-blob crowns; hanging light strands. Interior reserved only.',
          'reference_seeds':metrics}
text=json.dumps(manifest,ensure_ascii=False,indent=2)+'\n'
if args.check:
    assert OUT.read_text()==text,'Great tree manifest differs; regenerate after a deliberate shape revision'
    print('Great tree source/shape manifest passed; written=0')
else:
    OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(text);print(OUT.relative_to(ROOT))
