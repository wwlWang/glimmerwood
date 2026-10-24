#!/usr/bin/env python3
"""Original faceted heart and curved root cage, in glTF metres/Y-up.

The deterministic mesh source is retained alongside GLB/PBR atlases and a manifest.
No imported art, downloaded models, or edits to the accepted sanctuary assets.
--check regenerates every packaged byte in memory without writing.
"""
import argparse,hashlib,json,math,struct,zlib
from sanctuary_items import model as item_model
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];PACK=ROOT/'src/main/resources/assets/orimod';SOURCE=ROOT/'art/source/great_tree_heart/v002'
TILE=64;WIDTH=512;HEIGHT=64
PALETTE=[(83,73,60),(52,52,48),(60,130,149),(86,178,193),(133,218,224),(213,250,235),(231,218,147),(76,150,166)]
def enc(o):return (json.dumps(o,ensure_ascii=False,indent=2)+'\n').encode()
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def cross(a,b):return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def norm(v):
 d=math.sqrt(sum(x*x for x in v));return tuple(x/d for x in v)
def png(suffix):
 def chunk(k,v):return struct.pack('!I',len(v))+k+v+struct.pack('!I',zlib.crc32(k+v)&0xffffffff)
 def pixel(x,y):
  tile=x//TILE;rgb=PALETTE[tile]
  if suffix=='base' and tile<2:
   u=x%TILE;grain=5*math.sin(u*.75+.3*math.sin(y*.3))+((u*53+y*37)%17-8)*.5
   if (u+y//8)%9==0:grain-=12
   rgb=tuple(round(v+grain) for v in rgb)
  elif suffix=='emission':rgb=tuple(round(v*({2:.18,3:.3,4:.45,5:.72,6:.42,7:.25}.get(tile,0))) for v in rgb)
  elif suffix=='surface':rgb=(255,205 if tile<2 else 85,8)
  return (*rgb,255)
 raw=b''.join(b'\0'+bytes(c for x in range(WIDTH) for c in pixel(x,y)) for y in range(HEIGHT))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',WIDTH,HEIGHT,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
class Mesh:
 def __init__(self):self.vertices=[];self.faces=[]
 def face(self,points,colour):
  ids=[];n=norm(cross(sub(points[1],points[0]),sub(points[2],points[0])))
  axis=max(range(3),key=lambda i:abs(n[i]))
  for p in points:
   if colour<2:
    a,b=(p[0],p[2]) if axis==1 else (p[0],p[1]) if axis==2 else (p[2],p[1])
    u,v=32+a*16,(32 if axis==1 else 8)+b*16
   else:u=v=32
   assert 0<=u<64 and 0<=v<64,(colour,p,u,v)
   ids.append(len(self.vertices));self.vertices.append((*p,*n,(colour*TILE+u)/WIDTH,1-v/HEIGHT))
  for i in range(1,len(ids)-1):self.faces.append((ids[0],ids[i],ids[i+1]))
 def tube(self,points,radii,colour,sides=8):
  rings=[]
  for i,p in enumerate(points):
   t=norm(sub(points[min(i+1,len(points)-1)],points[max(0,i-1)]));ref=(0,1,0) if abs(t[1])<.95 else (1,0,0)
   u=norm(cross(t,ref));v=cross(t,u);r=radii[i] if isinstance(radii,list) else radii
   rings.append([tuple(p[k]+r*(u[k]*math.cos(n*math.tau/sides)+v[k]*math.sin(n*math.tau/sides)) for k in range(3)) for n in range(sides)])
  self.face(list(reversed(rings[0])),colour);self.face(rings[-1],colour)
  for a,b in zip(rings,rings[1:]):
   for n in range(sides):j=(n+1)%sides;self.face([a[n],a[j],b[j],b[n]],colour)
 def shard(self,center,size,tilt=0,phase=0):
  # Unequal six-sided rings and pointed ends; no rectangular prism or cube stack.
  def pt(x,y,z):return(center[0]+x*size[0]+y*tilt,center[1]+y*size[1],center[2]+z*size[2])
  def facet(points,colour):
   normal=cross(sub(points[1],points[0]),sub(points[2],points[0]));out=sub(tuple(sum(p[i] for p in points)/len(points) for i in range(3)),center)
   if sum(a*b for a,b in zip(normal,out))<0:points=list(reversed(points))
   self.face(points,colour)
  bottom=pt(0,-1,0);top=pt(.09,1.14,-.03)
  rings=[]
  for h,r in [(-.58,.67),(.15,1),(.66,.53)]:
   rings.append([pt(math.cos(n*math.tau/6+phase)*r*(1+.09*math.sin(n*3)),h,math.sin(n*math.tau/6+phase)*r) for n in range(6)])
  for n in range(6):
   j=(n+1)%6;facet([bottom,rings[0][j],rings[0][n]],2+n%3)
   facet([rings[2][n],rings[2][j],top],3+n%3)
   for k in range(2):facet([rings[k][n],rings[k][j],rings[k+1][j],rings[k+1][n]],2+(n+k)%4)
 def glb(self,name,images):
  binary=bytearray();views=[];accessors=[]
  def view(data,target=None):
   while len(binary)%4:binary.append(0)
   v={'buffer':0,'byteOffset':len(binary),'byteLength':len(data)}
   if target:v['target']=target
   views.append(v);binary.extend(data);return len(views)-1
  def accessor(values,n,component,kind):
   data=b''.join(struct.pack('<'+('f' if component==5126 else 'I')*n,*v) for v in values)
   a={'bufferView':view(data,34963 if kind=='SCALAR' else 34962),'componentType':component,'count':len(values),'type':kind}
   if kind=='VEC3':a.update(min=[min(v[i] for v in values) for i in range(n)],max=[max(v[i] for v in values) for i in range(n)])
   accessors.append(a);return len(accessors)-1
  pos=accessor([v[:3] for v in self.vertices],3,5126,'VEC3');normal=accessor([v[3:6] for v in self.vertices],3,5126,'VEC3');uv=accessor([v[6:8] for v in self.vertices],2,5126,'VEC2');indices=accessor([(i,) for f in self.faces for i in f],1,5125,'SCALAR')
  imgs=[{'name':name+'_'+s,'mimeType':'image/png','bufferView':view(data),'extras':{'resourceLocation':f'orimod:textures/sanctuary/{name}_{s}.png'}} for s,data in images.items()]
  gltf={'asset':{'version':'2.0','generator':'OriMod original procedural great heart v002'},'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'name':name,'mesh':0}],'meshes':[{'name':name,'primitives':[{'attributes':{'POSITION':pos,'NORMAL':normal,'TEXCOORD_0':uv},'indices':indices,'material':0}]}],
        'materials':[{'name':name,'doubleSided':True,'pbrMetallicRoughness':{'baseColorTexture':{'index':0},'metallicRoughnessTexture':{'index':2},'metallicFactor':1,'roughnessFactor':1},'emissiveTexture':{'index':1},'emissiveFactor':[1,1,1]}],
        'textures':[{'source':i,'sampler':0} for i in range(3)],'samplers':[{'magFilter':9728,'minFilter':9728,'wrapS':33071,'wrapT':33071}],'images':imgs,'buffers':[{'byteLength':len(binary)}],'bufferViews':views,'accessors':accessors}
  js=json.dumps(gltf,separators=(',',':')).encode();js+=b' '*(-len(js)%4);binary+=b'\0'*(-len(binary)%4)
  return struct.pack('<III',0x46546c67,2,28+len(js)+len(binary))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(binary),0x004e4942)+binary

def roots():
 m=Mesh()
 for n in range(5):
  a=n*math.tau/5+.3
  points=[(r*math.cos(a+b),y,r*math.sin(a+b)) for r,y,b in [(1.15,.08,0),(.88,.35,.05),(.82,.9,.17),(.83,1.6,.27),(.64,2.25,.3),(.34,2.65,.35)]]
  m.tube(points,[.20,.18,.14,.095,.065,.015],0)
  m.tube([(x*.94,y+.025,z*.94) for x,y,z in points],[.016]*6,4,6)
 # A thin woven base connects the roots, without a square slab.
 m.tube([(.8*math.cos(n*math.tau/40),.19,.8*math.sin(n*math.tau/40)) for n in range(41)],.12,0)
 return m

def crystal():
 m=Mesh();m.shard((0,0,0),(.43,.91,.37),.10,.24)
 for n in range(3):
  a=n*math.tau/3+.8;r=.46
  m.shard((r*math.cos(a),-.37,r*math.sin(a)),(.16,.38,.15),.08*math.cos(a),a)
 # Delicate bright seams along the main face; golden-white central seed near the front.
 m.tube([(-.17,-.48,-.23),(-.03,-.04,-.36),(.08,.43,-.22),(.11,.87,-.05)],.016,5,6)
 m.shard((.01,.03,-.365),(.065,.11,.027),0,.1)
 return m

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');args=ap.parse_args();outputs={};assets=[]
 for name,mesh in [('great_heart_roots',roots()),('great_heart_crystal',crystal())]:
  images={s:png(s) for s in ['base','emission','surface']};files={name+'.glb':mesh.glb(name,images),**{name+'_'+s+'.png':b for s,b in images.items()}}
  outputs[SOURCE/(name+'.mesh.json')]=enc({'coordinates':'Y up/metres/origin at root base or crystal pivot','vertices_position_normal_uv':mesh.vertices,'triangles':mesh.faces})
  for filename,data in files.items():
   outputs[SOURCE/filename]=data;outputs[PACK/('models/sanctuary' if filename.endswith('.glb') else 'textures/sanctuary')/filename]=data
  assets.append({'name':name,'vertices':len(mesh.vertices),'triangles':len(mesh.faces),'bounds':[[min(v[i] for v in mesh.vertices) for i in range(3)],[max(v[i] for v in mesh.vertices) for i in range(3)]]})
 # A small, cheap block/item fallback while the asynchronous GLB is unavailable.
 for bloomed in [False,True]:
  name='great_tree_heart'+('_bloomed' if bloomed else '')
  def cube(lo,hi,tex,angle=0):
   d={'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
   if tex=='heart':d.update(shade=False,neoforge_data={'block_light':15})
   if angle:d['rotation']={'origin':[8,8,8],'axis':'y','angle':angle}
   return d
  model={'ambientocclusion':False,'textures':{'particle':'orimod:block/spirit_tree_heart','bark':'orimod:block/great_root_bark','heart':'orimod:block/spirit_tree_heart'+('_bloomed' if bloomed else '')},'elements':[cube([1,0,1],[15,3,15],'bark'),cube([5,3,5],[11,21,11],'heart',45)]}
  outputs[PACK/'models/block'/f'{name}.json']=enc(model);outputs[SOURCE/f'{name}.json']=enc(model)
 outputs[PACK/'blockstates/great_tree_heart.json']=enc({'variants':{'bloomed=false':{'model':'orimod:block/great_tree_heart'},'bloomed=true':{'model':'orimod:block/great_tree_heart_bloomed'}}})
 outputs[PACK/'models/item/great_tree_heart.json']=enc(item_model("great_tree_heart"))
 outputs[SOURCE/'manifest.json']=enc({'version':2,'generator':'scripts/art/build_great_heart.py','style':'Faceted pointed crystal with three satellite shards, five curved tapering roots; 16 texels/block on wood, nearest-filtered PBR/emission','runtime':'Sanctuary BE, crystal pivot at y=1.75, slight float and slow rotation; full AABB; same one-block collision','assets':assets,'files':{str(p.relative_to(ROOT)):hashlib.sha256(v).hexdigest() for p,v in outputs.items()}})
 for path,data in outputs.items():
  if args.check:assert path.exists() and path.read_bytes()==data,f'Mismatch: {path}'
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 print('Great heart v002: '+str(sum(a['triangles'] for a in assets))+' triangles; '+('reproduction passed, written=0' if args.check else 'generated source/package/manifest'))
if __name__=='__main__':main()
