#!/usr/bin/env python3
"""Original water-seed crystal, wet root cradle, deterministic mesh -> GLB/PBR -> manifest.
Shared exporter only; no modification of the accepted Great Heart or imported artwork.
"""
import argparse,hashlib,json,math
from pathlib import Path
import build_great_heart as meshlib
ROOT=Path(__file__).resolve().parents[2];PACK=ROOT/'src/main/resources/assets/orimod';SOURCE=ROOT/'art/source/ginso_water_heart/v001'
meshlib.PALETTE=[(55,70,51),(40,64,65),(25,100,142),(40,154,181),(99,211,222),(192,248,239),(73,197,213),(34,132,159)]
def enc(o):return (json.dumps(o,ensure_ascii=False,indent=2)+'\n').encode()
def roots():
 m=meshlib.Mesh()
 for n in range(5):
  a=n*math.tau/5
  pts=[(r*math.cos(a+t),y,r*math.sin(a+t)) for r,y,t in [(.78,.04,-.3),(.55,.2,0),(.49,.42,.25),(.34,.62,.5),(.21,.72,.8)]]
  m.tube(pts,[.13,.13,.1,.06,.018],0,7)
  m.tube([(x*.99,y+.055,z*.99) for x,y,z in pts],[.012,.012,.01,.008,.002],4,5)
 # An irregular bowl of concentric intertwined roots, with a luminous water-seed bed.
 m.tube([(.43*math.cos(n*math.tau/28),.24+.04*math.sin(n*.7),.43*math.sin(n*math.tau/28)) for n in range(29)],.09,1,7)
 m.tube([(.24*math.cos(n*math.tau/24),.27,.24*math.sin(n*math.tau/24)) for n in range(25)],.025,5,6)
 return m

def crystal():
 m=meshlib.Mesh()
 # A droplet profile: full lower shoulders, very narrow tilted upper tip; irregular faceted skin.
 rings=[];segments=9
 for y,r in [(-.44,.045),(-.31,.25),(-.04,.34),(.23,.22),(.48,.10),(.75,.006)]:
  rings.append([(r*math.cos(n*math.tau/segments+.12)*(1+.06*math.sin(n*4)),y,r*math.sin(n*math.tau/segments+.12)+y*.09) for n in range(segments)])
 for k,(a,b) in enumerate(zip(rings,rings[1:])):
  for n in range(segments):j=(n+1)%segments;m.face([a[n],b[n],b[j],a[j]],2+(n+k)%4)
 for phase in [0,math.pi]:
  pts=[(.36*math.cos(phase+t*math.tau),-.3+t*.63,.36*math.sin(phase+t*math.tau)) for t in [n/24 for n in range(25)]]
  m.tube(pts,.012,6,5)
 m.shard((.16,-.02,-.27),(.035,.085,.025),.01,.2)
 return m

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');args=ap.parse_args();outputs={};assets=[]
 for name,mesh in [('ginso_water_roots',roots()),('ginso_water_crystal',crystal())]:
  images={s:meshlib.png(s) for s in ['base','emission','surface']};files={name+'.glb':mesh.glb(name,images),**{name+'_'+s+'.png':v for s,v in images.items()}}
  outputs[SOURCE/(name+'.mesh.json')]=enc({'units':'metres, Y up; cradle at block base; crystal pivot runtime y=1.12','vertices_position_normal_uv':mesh.vertices,'triangles':mesh.faces})
  for filename,data in files.items():outputs[SOURCE/filename]=data;outputs[PACK/('models/sanctuary' if filename.endswith('.glb') else 'textures/sanctuary')/filename]=data
  assets.append({'name':name,'triangles':len(mesh.faces),'vertices':len(mesh.vertices),'bounds':[[min(v[i] for v in mesh.vertices) for i in range(3)],[max(v[i] for v in mesh.vertices) for i in range(3)]]})
 faces={f:{'texture':'#crystal'} for f in ['north','south','east','west','up','down']}
 model={'ambientocclusion':False,'textures':{'particle':'orimod:block/spirit_tree_heart','crystal':'orimod:block/spirit_tree_heart'},'elements':[{'from':[5,4,5],'to':[11,20,11],'shade':False,'neoforge_data':{'block_light':15},'rotation':{'origin':[8,8,8],'axis':'y','angle':45},'faces':faces}]}
 outputs[PACK/'models/block/ginso_water_heart.json']=enc(model);outputs[SOURCE/'ginso_water_heart.json']=enc(model)
 outputs[PACK/'blockstates/ginso_water_heart.json']=enc({'variants':{'':{'model':'orimod:block/ginso_water_heart'}}})
 outputs[PACK/'models/item/ginso_water_heart.json']=enc({'parent':'orimod:block/ginso_water_heart'})
 outputs[SOURCE/'manifest.json']=enc({'version':1,'generator':'scripts/art/build_ginso_water_heart.py','style':'Original clear-blue faceted droplet in woven wet roots; two cyan winding filaments. Shared exporter, no imported meshes.','assets':assets,'runtime':'Shared Sanctuary BE, always visible; owner-only pickup motes; float pivot y=1.12','files':{str(p.relative_to(ROOT)):hashlib.sha256(v).hexdigest() for p,v in outputs.items()}})
 for p,v in outputs.items():
  if args.check:assert p.exists() and p.read_bytes()==v,f'Mismatch: {p}'
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(v)
 print('Water heart v001: '+str(sum(x['triangles'] for x in assets))+' triangles; '+('check passed, written=0' if args.check else 'source/package/manifest generated'))
if __name__=='__main__':main()
