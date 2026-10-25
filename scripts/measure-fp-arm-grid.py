#!/usr/bin/env python3
"""Measure fixed-rig idle captures; masks are saved so thresholds and exclusions can be reviewed.
BBox area is not silhouette pixel count. A cropped sword is compared by its visible axis only.
"""
import json,math,sys
from pathlib import Path
from PIL import Image,ImageDraw
base=Path(sys.argv[1]);out=base/'measurements';out.mkdir(exist_ok=True)
rows={}
def stats(points):
 xs=[x for x,y in points];ys=[y for x,y in points];n=len(xs);mx=sum(xs)/n;my=sum(ys)/n
 xx=sum((x-mx)**2 for x in xs);yy=sum((y-my)**2 for y in ys);xy=sum((x-mx)*(y-my)for x,y in points)
 bb=[min(xs),min(ys),max(xs),max(ys)]
 return {'bbox':bb,'center':[(bb[0]+bb[2])/2,(bb[1]+bb[3])/2],'bboxArea':(bb[2]-bb[0])*(bb[3]-bb[1]),'pixels':n,'axis':math.degrees(.5*math.atan2(2*xy,xx-yy))}
for arm in ['right','left']:
 for kind in ['empty','block','blade']:
  for human in [True,False]:
   name=('human-' if human else '')+arm+'-'+kind+'-idle';p=base/(name+'.png')
   if not p.exists():continue
   im=Image.open(p).convert('RGB');px=im.load();points=[];mask=Image.new('1',im.size);mp=mask.load()
   # Fixed no-shader, noon, neutral-grey ground; outer HUD excluded. Full uncropped still accompanies each mask.
   for y in range(650 if kind=="empty" else 240,im.height):
    for x in (range(1240,im.width)if arm=='right' else range(0,680)):
     r,g,b=px[x,y]
     if arm=='left' and 790<=y<=845 and x<280:continue # legacy energy HUD
     ok=(r>b+20 and g>b+10)if kind=='block' else(min(r,g,b)>190 if not human or kind=='blade' else max(r,g,b)-min(r,g,b)>18)
     if ok:points.append((x,y));mp[x,y]=1
   rows[name]=stats(points);mask.save(out/(name+'-mask.png'))
comparisons={}
for arm in ['right','left']:
 for kind in ['empty','block','blade']:
  k=arm+'-'+kind+'-idle';h=rows.get('human-'+k);s=rows.get(k)
  if not h or not s:continue
  delta=abs(s['axis']-h['axis']);delta=min(delta,180-delta)
  comparisons[k]={'centerDelta':[s['center'][i]-h['center'][i] for i in range(2)],'bboxAreaPercent':100*(s['bboxArea']/h['bboxArea']-1),'axisDelta':delta,
    'comparisonScope':'hand+weapon union (diagnostic only)' if kind=='blade' else 'whole hand / item',
    'pass':all(abs(s['center'][i]-h['center'][i])<=60 for i in range(2))and abs(s['bboxArea']/h['bboxArea']-1)<=.15 and delta<=12}
result={'method':'fixed noon colour mask, bbox area; paw included for the blade union, separately labelled from weapon-only size','rows':rows,'comparisons':comparisons}
(out/'idle.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(comparisons,indent=2))
# Compare the unobscured upper blade, not the combined hand/guard silhouette.
axes={}
for arm in ['right','left']:
 for iron in [False,True]:
  name=('human-'+arm+'-sword-idle')if iron else arm+'-blade-idle'
  a=Image.open(base/(name+'.png')).convert('RGB');b=Image.open(base/('human-'+arm+'-empty-idle.png')).convert('RGB');pts=[]
  for y in range(240,735):
   for x in (range(1240,a.width)if arm=='right'else range(680)):
    c=a.getpixel((x,y));d=b.getpixel((x,y))
    if (max(abs(c[i]-d[i])for i in range(3))>50 if iron else min(c)>190):pts.append((x,y))
  axes[name]=stats(pts)
deltas={a:abs(axes[a+'-blade-idle']['axis']-axes['human-'+a+'-sword-idle']['axis'])for a in ['right','left']}
(out/'blade-axis.json').write_text(json.dumps({'method':'Upper visible blade y=240..734, excluding paw/guard; iron difference against human empty shot, Spirit Blade bright core. Original uncropped images retained.','measurements':axes,'delta':deltas},indent=2)+'\n')
