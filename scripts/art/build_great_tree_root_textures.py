#!/usr/bin/env python3
"""Interior-only 16px timber and narrow sap inlays; exterior art stays byte-for-byte intact."""
from pathlib import Path
import argparse,hashlib,json,math,struct,zlib
ROOT=Path(__file__).resolve().parents[2];PACK=ROOT/'src/main/resources/assets/orimod';SOURCE=ROOT/'art/source/great_tree_interior/v002'
def encoded(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
def png(pixel):
 def chunk(k,v):return struct.pack('!I',len(v))+k+v+struct.pack('!I',zlib.crc32(k+v)&0xffffffff)
 raw=b''.join(b'\0'+bytes(c for x in range(16) for c in pixel(x,y)) for y in range(16))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
def bark(x,y):
 grain=5*math.sin(x*1.4+.35*math.sin(y*.3))+(((x*53+y*37)%17)-8)*.5
 if (x+y//8)%7==0:grain-=7
 return tuple(round(v+grain) for v in (87,78,65))+(255,)
def sap(x,y):
 # A single texel-width, low contrast line. No crossing lightning or white halo.
 return (64,129,145,255) if x==7+int(math.sin(y*.4)) else (0,0,0,0)
def knot(x,y):
 d=((x-7.5)/3.3)**2+((y-7.5)/4.6)**2
 if d<.3:return (193,232,219,255)
 if .65<d<1:return (92,160,153,255)
 return (0,0,0,0)
files={}
for name,pixel in [('great_root_bark',bark),('great_root_sap',sap),('great_root_knot',knot)]:
 data=png(pixel);files[PACK/'textures/block'/f'{name}.png']=data;files[SOURCE/f'{name}.png']=data
faces=['north','south','east','west','up','down']
for kind in ['none','vein','knot']:
 model={'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':{'particle':'orimod:block/great_root_bark','bark':'orimod:block/great_root_bark'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{f:{'texture':'#bark','cullface':f} for f in faces}}]}
 if kind!='none':
  model['textures']['glow']='orimod:block/great_root_'+('sap' if kind=='vein' else 'knot')
  model['elements'].append({'from':[-.006]*3,'to':[16.006]*3,'shade':False,'neoforge_data':{'block_light':15,'sky_light':15,'ambient_occlusion':False},'faces':{f:{'texture':'#glow','uv':[0,0,16,16],'cullface':f} for f in faces}})
 files[PACK/'models/block'/f'great_spirit_root_{kind}.json']=encoded(model)
files[PACK/'blockstates/great_spirit_root.json']=encoded({'variants':{f'light={k}':{'model':f'orimod:block/great_spirit_root_{k}'} for k in ['none','vein','knot']}})
files[PACK/'models/item/great_spirit_root.json']=encoded({'parent':'orimod:block/great_spirit_root_none'})
files[SOURCE/'root_textures.json']=encoded({'generator':'scripts/art/build_great_tree_root_textures.py','texels_per_block':16,'emission_levels':{'none':0,'vein':6,'knot':14},'files':{str(p.relative_to(ROOT)):hashlib.sha256(v).hexdigest() for p,v in files.items()}})
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
for path,data in files.items():
 if args.check:assert path.exists() and path.read_bytes()==data,f'Stale {path}'
 else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
print(f'Interior root textures: {len(files)} files, written={0 if args.check else len(files)}')
