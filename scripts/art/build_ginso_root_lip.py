#!/usr/bin/env python3
"""Gate2 root lips: irregular interwoven horizontal roots, fixed collision handled by the block.
Only visual JSON changes. Geometry v003 and its archived art are left byte-for-byte intact.
"""
import argparse,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'art/source/ginso_escape/v005';PACK=ROOT/'src/main/resources/assets/orimod'
def enc(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
def model(variant):
 elements=[]
 def cube(lo,hi,angle=0,axis='y',origin=None):
  e={'from':lo,'to':hi,'faces':{f:{'texture':'#bark'} for f in ('up','down','north','south','east','west')}}
  if angle:e['rotation']={'origin':origin or [8,12,8],'axis':axis,'angle':angle,'rescale':False}
  elements.append(e)
 # Continuous roots reach all neighbouring voxel faces. A stepped round cross-section
 # leaves no cap-shaped gaps at the cell boundary; only a 1-texel crown variation.
 cube([0,0,0],[16,10,16])
 h=[21,22,21,22][variant]
 for width,low,high in [(16,9,15),(14,14,18),(10,17,20),(5,19,h)]:
  edge=(16-width)/2
  cube([0,low,edge],[16,high,16-edge])
  cube([edge,low-1,0],[16-edge,high-1,16])
 # A quieter asymmetric bark ridge, below the rounded lip rather than a raised post.
 if variant%2:cube([1,10,0],[5,17,16])
 else:cube([0,10,10],[16,16,14])
 return {'textures':{'bark':'orimod:block/ginso_bark_moss','particle':'orimod:block/ginso_bark_moss'},'elements':elements}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');args=ap.parse_args();outputs={}
 variants=[]
 for i in range(4):
  name='ginso_root_lip'+('' if i==0 else '_'+str(i));v=enc(model(i));outputs[OUT/(name+'.json')]=v;outputs[PACK/'models/block'/(name+'.json')]=v
  for rotation in (0,90,180,270):variants.append({'model':'orimod:block/'+name,'y':rotation,'weight':1})
 state=enc({'variants':{'':variants}});item=enc({'parent':'orimod:block/ginso_root_lip','display':{'gui':{'rotation':[30,225,0],'translation':[0,-1,0],'scale':[.46,.46,.46]}}})
 outputs[PACK/'blockstates/ginso_root_lip.json']=state;outputs[OUT/'blockstate.json']=state
 outputs[PACK/'models/item/ginso_root_lip.json']=item;outputs[OUT/'item.json']=item
 outputs[OUT/'manifest.json']=enc({'version':5,'geometry':'unchanged v003 / EscapeVersion1; GUI only fit v005','generator':'scripts/art/build_ginso_root_lip.py','collision':'explicit full voxel width x 1.5 blocks high, unchanged','visual':'Four deterministic positional variants x four rotations. Continuous rounded horizontal roots reach voxel boundaries. One-texel height variation, asymmetrical bark ridges, no caps or isolated posts.','files':{str(p.relative_to(ROOT)):hashlib.sha256(v).hexdigest() for p,v in outputs.items()}})
 for p,v in outputs.items():
  if args.check:assert p.exists() and p.read_bytes()==v,f'Mismatch: {p}'
  else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(v)
 print('Ginso root lip v005: '+('check passed, written=0' if args.check else 'generated'))
if __name__=='__main__':main()
