#!/usr/bin/env python3
"""Original Ginso voxel sculpture + 16px wet-bark/foliage art. Deterministic, stdlib; --check writes nothing."""
from pathlib import Path
import argparse,hashlib,json,math,os,struct,subprocess,tempfile,zlib
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'art/source/ginso_tree/v005'
ASSET=ROOT/'src/main/resources/assets/orimod'
JAVA=ROOT/'src/main/java/cn/wwl/orimod/world/GinsoShape.java'
def noise(x,y,s=0):return ((x*374761393 ^ y*668265263 ^ s*982451653)*1274126177&0xffffffff)/0xffffffff
def bark(x,y,moss=False):
 g=5*math.sin(x*1.7+.5*math.sin(y*.4))+(noise(x,y)-.5)*15
 if (x+y//7)%6==0:g-=18
 patch=moss and noise(x//2,y//3,5)>.39
 color=(76,103,70) if patch else (106,98,78)
 if not patch and x in (3,12) and noise(x,y//4,2)>.43:color=(128,136,108)
 return tuple(max(0,round(v+g)) for v in color)+(255,)
def sap(x,y,moss=False):
 d=abs(x-7-.8*math.sin(y*.5))
 if (3<=y<=13 and d<.85) or (x-7)**2+(y-13)**2<=3:return(77,184,161,255)
 if noise(x//2,y//2,31)>.66 and (moss or abs(x-7)<5):
  n=round(noise(x,y,42)*23);return(40+n,130+n,103+n,255)
 return(0,0,0,0)
def fungus(x,y,glow=False):
 dx,dy=x-7.5,y-7.5;r=math.hypot(dx,dy);a=math.atan2(dy,dx)
 edge=6.5+.65*math.sin(3*a)+.35*math.cos(5*a)
 if r>edge:return(0,0,0,0)
 if glow:
  return (62,154,126,255) if r>edge-1.1 and noise(x,y,39)>.2 else (0,0,0,0)
 n=round((noise(x,y,40)-.5)*15)
 return(69+n,98+n,72+n,255)
def fungus_edge(x,y):
 return(56,145,118,255) if y==6+int(noise(x//3,1,41)*2) else (0,0,0,0)
def leaves(x,y):
 if noise(x,y,13)<.25:return(0,0,0,0)
 n=round((noise(x,y,8)-.5)*23)
 return(43+n,76+n,55+n,255)
def leaf_core(x,y):
 n=round((noise(x,y,8)-.5)*23)
 return (43+n,76+n,55+n,255)
def vine(x,y,tip,glow=False):
 stem=7+round(1.2*math.sin(y*.6))
 if glow:
  drop=(y in (4,5,12,13) and abs(x-stem)<=1) or (tip and y>12 and abs(x-stem)<2)
  return (69,173,149,255) if drop else (0,0,0,0)
 leaf=(y%5 in (1,2) and abs(x-stem)<=3)
 if abs(x-stem)==0 or leaf:
  if tip and y>13:return(65,128,112,255)
  n=round(noise(x,y,18)*15);return(32+n,64+n,44+n,255)
 return(0,0,0,0)
def png(f):
 def ch(k,b):return struct.pack('!I',len(b))+k+b+struct.pack('!I',zlib.crc32(k+b)&0xffffffff)
 raw=b''.join(b'\0'+bytes(c for x in range(16) for c in f(x,y)) for y in range(16))
 return b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('!2I5B',16,16,8,6,0,0,0))+ch(b'IDAT',zlib.compress(raw,9))+ch(b'IEND',b'')
files={}
def js(path,obj):files[path]=(json.dumps(obj,indent=2)+'\n').encode()
for name,f in [('ginso_bark',bark),('ginso_bark_moss',lambda x,y:bark(x,y,True)),
               ('ginso_sap',sap),('ginso_moss_glow',lambda x,y:sap(x,y,True)),('ginso_leaves',leaves),('ginso_leaf_core',leaf_core),
               ('ginso_fungus',fungus),('ginso_fungus_glow',lambda x,y:fungus(x,y,True)),('ginso_fungus_edge',fungus_edge),
               ('ginso_vine',lambda x,y:vine(x,y,False)),('ginso_vine_tip',lambda x,y:vine(x,y,True)),
               ('ginso_vine_glow',lambda x,y:vine(x,y,False,True)),('ginso_vine_tip_glow',lambda x,y:vine(x,y,True,True))]:
 b=png(f);files[ASSET/f'textures/block/{name}.png']=b;files[SRC/f'{name}.png']=b
fullbright={'block_light':15,'sky_light':15,'ambient_occlusion':False}
faces=('north','south','east','west','up','down')
variants={}
for moss in (False,True):
 for lit in (False,True):
  name='ginso_wood'+('_moss' if moss else '')+('_sap' if lit else '')
  base='orimod:block/ginso_bark'+('_moss' if moss else '')
  model={'render_type':'minecraft:cutout','textures':{'bark':base,'north':'orimod:block/ginso_bark_moss','particle':base,
         'vein':'orimod:block/ginso_moss_glow' if moss else 'orimod:block/ginso_sap'},'elements':[{
         'from':[0,0,0],'to':[16,16,16],'faces':{d:{'texture':'#north' if d=='north' else '#bark','cullface':d} for d in faces}}]}
  if lit:
   model['elements'].append({'from':[-.006]*3,'to':[16.006]*3,'shade':False,'neoforge_data':fullbright,
       'faces':{d:{'texture':'#vein','uv':[0,0,16,16],'cullface':d} for d in faces}})
  js(ASSET/f'models/block/{name}.json',model)
  variants[f'moss={str(moss).lower()},vein={str(lit).lower()}']={'model':'orimod:block/'+name}
js(ASSET/'blockstates/ginso_wood.json',{'variants':variants})
js(ASSET/'models/block/ginso_leaves.json',{'parent':'minecraft:block/cube_all','render_type':'minecraft:cutout_mipped','textures':{'all':'orimod:block/ginso_leaves'}})
js(ASSET/'blockstates/ginso_leaves.json',{'variants':{'':{'model':'orimod:block/ginso_leaves'}}})
for name,layer,texture in [('ginso_canopy','cutout_mipped','ginso_leaves'),('ginso_leaf_core','solid','ginso_leaf_core')]:
 js(ASSET/f'models/block/{name}.json',{'parent':'minecraft:block/cube_all','render_type':'minecraft:'+layer,'textures':{'all':'orimod:block/'+texture}})
 js(ASSET/f'blockstates/{name}.json',{'variants':{'':{'model':'orimod:block/'+name}}})
 js(ASSET/f'models/item/{name}.json',{'parent':'orimod:block/'+name})
 # Same leaf drop as the old tree, rather than introducing a separate material economy.
 files[ROOT/f'src/main/resources/data/orimod/loot_table/blocks/{name}.json']=(ROOT/'src/main/resources/data/orimod/loot_table/blocks/ginso_leaves.json').read_bytes()

js(ASSET/'blockstates/ginso_vine.json',{'variants':{f'tip={str(t).lower()}':{'model':'orimod:block/ginso_vine'+('_tip' if t else '')} for t in (False,True)}})
for suffix in ('','_tip'):
 elements=[]
 for glow in (False,True):
  for x_plane in (False,True):
   off=.006 if glow else 0
   e={'from':[8+off,0,.8] if x_plane else [.8,0,8+off], 'to':[8+off,16,15.2] if x_plane else [15.2,16,8+off],
      'rotation':{'origin':[8,8,8],'axis':'y','angle':45,'rescale':True},'shade':not glow,
      'faces':{d:{'texture':'#glow' if glow else '#cross','uv':[0,0,16,16]} for d in (('east','west') if x_plane else ('north','south'))}}
   if glow:e['neoforge_data']=fullbright
   elements.append(e)
 js(ASSET/f'models/block/ginso_vine{suffix}.json',{'render_type':'minecraft:cutout','textures':{'cross':f'orimod:block/ginso_vine{suffix}','glow':f'orimod:block/ginso_vine{suffix}_glow','particle':f'orimod:block/ginso_vine{suffix}'},'elements':elements})
# Overlapping thin steps give the shelf an irregular lip; only its blue-green ring emits.
elements=[]
for lo,hi in [([1,4,1],[15,7,15]),([3,7,3],[13,8,13])]:
 elements.append({'from':lo,'to':hi,'faces':{d:{'texture':'#base','uv':[0,0,16,16]} for d in faces}})
 elements.append({'from':[v-.006 for v in lo],'to':[v+.006 for v in hi],'shade':False,'neoforge_data':fullbright,
                  'faces':{d:{'texture':'#glow' if d=='up' else '#edge','uv':[0,0,16,16]} for d in faces if d!='down'}})
js(ASSET/'models/block/ginso_fungus.json',{'render_type':'minecraft:cutout','textures':{'base':'orimod:block/ginso_fungus','glow':'orimod:block/ginso_fungus_glow','edge':'orimod:block/ginso_fungus_edge','particle':'orimod:block/ginso_fungus'},'elements':elements})
js(ASSET/'blockstates/ginso_fungus.json',{'variants':{'':{'model':'orimod:block/ginso_fungus'}}})
js(ASSET/'models/item/ginso_fungus.json',{'parent':'orimod:block/ginso_fungus'})
for name in ('ginso_wood','ginso_leaves','ginso_vine'):
 js(ASSET/f'models/item/{name}.json',{'parent':f'orimod:block/{name}'})
 table={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'orimod:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
 if name=='ginso_vine':
  table=json.loads((ROOT/'src/main/resources/data/orimod/loot_table/blocks/spirit_vine.json').read_text().replace('orimod:spirit_vine','orimod:ginso_vine'))
 js(ROOT/f'src/main/resources/data/orimod/loot_table/blocks/{name}.json',table)
java=Path(os.environ.get('JAVA_HOME',Path.home()/'.local/opt/jdk-21'))/'bin'
with tempfile.TemporaryDirectory(prefix='ginso-art-') as temp:
 temp=Path(temp)
 (temp/'Report.java').write_text('''import cn.wwl.orimod.world.GinsoShape;
public class Report {public static void main(String[] args){for(long seed:new long[]{115117,115118}){
 var s=new GinsoShape(seed);int[] n=new int[7];int low=99,high=-99,mx=99,Mx=-99,mz=99,Mz=-99;
 for(int x=-GinsoShape.RADIUS;x<=GinsoShape.RADIUS;x++)for(int z=-GinsoShape.RADIUS;z<=GinsoShape.RADIUS;z++)for(int y=GinsoShape.MIN_Y;y<=GinsoShape.MAX_Y;y++){byte c=s.cell(x,y,z);n[c]++;if(c==1||c==2||c==5){low=Math.min(low,y);high=Math.max(high,y);mx=Math.min(mx,x);Mx=Math.max(Mx,x);mz=Math.min(mz,z);Mz=Math.max(Mz,z);}}
 System.out.printf("{\\"seed\\":%d,\\"height\\":%d,\\"wood\\":%d,\\"leaves\\":%d,\\"vines\\":%d,\\"sap\\":%d,\\"x\\":[%d,%d],\\"y\\":[%d,%d],\\"z\\":[%d,%d]}%n",seed,s.height,n[1],n[2],n[3],n[5],mx,Mx,low,high,mz,Mz);
}}}''')
 subprocess.run([str(java/'javac'),'-d',str(temp),str(JAVA),str(JAVA.with_name('LegacyGinsoShape.java')),str(JAVA.with_name('TallGinsoShape.java')),str(temp/'Report.java')],check=True)
 metrics=[json.loads(l) for l in subprocess.check_output([str(java/'java'),'-cp',str(temp),'Report'],text=True).splitlines()]
js(SRC/'manifest.json',{'revision':'v005','generator':'scripts/art/build_ginso_tree.py','shape_source':str(JAVA.relative_to(ROOT)),'shape_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (JAVA,JAVA.with_name('LegacyGinsoShape.java'),JAVA.with_name('TallGinsoShape.java'))},'shape_version':3,'course_version':2,'legacy_baseline':'src/test/resources/cn/wwl/orimod/world/ginso-v2-42a50ec/baseline.json','recipe':'108–118-block twisted hollow wet cypress, 20–22-wide base, stilt feet at radius 22–24, crown within 70 blocks; three unequal leaning broken forks; three offset fork-aligned curved moss skirts, 11-block height steps, thick saddles and 5–8-block bent hems with bare broken tips and 12–25-block vine curtains; eight thinner connected root arches with rounded walk-through openings, unchanged support endpoints; exposed low snags; world-lit brown-green bark with north/low moss; emissive crack patches, fungus shelves and water drips. World-lit textures deliberately unchanged from v002; shape 3 binds to course 2; released shape 2 retains course 1 byte for byte.','canopy_rendering':'v3-only cutout ginso_canopy culls adjacent canopy faces; fully surrounded leaves become opaque world-lit ginso_leaf_core. Legacy ginso_leaves unchanged. Sparse interior drips and long outer curtains.', 'texture_size':[16,16],'filter':'nearest','metrics':metrics,'assets':{str(p.relative_to(ROOT)):hashlib.sha256(b).hexdigest() for p,b in files.items()}})
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
for path,b in files.items():
 if a.check:assert path.read_bytes()==b,f'Stale {path}'
 else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b)
print(f'Ginso v005: {len(files)} assets; written={0 if a.check else len(files)}; metrics={metrics}')
