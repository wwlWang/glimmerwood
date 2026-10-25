#!/usr/bin/env python3
"""Measure isolated weapon pixels and exact-frame arm geometry; compose unmodified review screenshots."""
import json,sys,math
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1]
base=Path(sys.argv[1]);human=root/'evidence/playtest-b3-weapons-before-20261025-v002'
out=base/'measurements';out.mkdir(exist_ok=True)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)
def pixels(path,spirit,left):
 im=Image.open(path).convert('RGB');points=[]
 # Same visible y region; exclude labels/hotbar below y=945. The vanilla reticle is chromatic dark,
 # so mask its central 280px before measuring the held item (neither vanilla item reaches that region).
 # Spirit inputs are explicitly recorded with paws suppressed, never the review stills.
 for y in range(150,945):
  for x in (range(0,820 if not spirit else 960) if left else range(1100 if not spirit else 960,1920)):
   r,g,b=im.getpixel((x,y))
   hit=(r>=200 and g>=235 and b>=240) if spirit else (max(r,g,b)-min(r,g,b)>8 and r<145 and g<165 and min(r,g,b)<110)
   if hit:points.append((x,y))
 if not points:raise ValueError(path)
 xs,ys=zip(*points);cx=sum(xs)/len(xs);cy=sum(ys)/len(ys)
 xx=sum((x-cx)**2 for x in xs);yy=sum((y-cy)**2 for y in ys);xy=sum((x-cx)*(y-cy)for x,y in points)
 box=[min(xs),min(ys),max(xs),max(ys)]
 mask=Image.new('RGB',im.size,(20,22,28));px=mask.load()
 for p in points:px[p]=(220,245,255)
 d=ImageDraw.Draw(mask);d.rectangle(box,outline='yellow',width=3)
 mask.save(out/(path.stem+'-mask.png'))
 return {'bbox':box,'bboxArea':(box[2]-box[0])*(box[3]-box[1]),'pixelPcaAxis':math.degrees(.5*math.atan2(2*xy,xx-yy)),'pixels':len(points)}
def delta(a,b):
 d=abs(a-b)%180;return min(d,180-d)
def grid(cells,path,cols=3):
 im=Image.new('RGB',(cols*640,((len(cells)+cols-1)//cols)*370),(26,28,35));d=ImageDraw.Draw(im)
 for i,(p,label)in enumerate(cells):
  a=Image.open(p).convert('RGB');a.thumbnail((640,338));x=i%cols*640;y=i//cols*370
  im.paste(a,(x,y+32));d.text((x+5,y+5),label,font=font,fill='white')
 im.save(path)
report={'pixelRule':'Same 1920x1015 viewport, y=150..944 and held half. Human chromatic dark item pixels, excluding the central reticle region (x=820..1100); spirit bright energy pixels from item-only diagnostic stills. Masks attached. Unmodified hand+weapon images used in grids.','static':{},'actions':{}}
for hand in ['right','left']:
 cells=[]
 for kind,vanilla in [('bow','bow'),('hammer','mace'),('spear','trident')]:
  hp=human/f'human-{hand}-{vanilla}-idle.png';sp=base/f'{hand}-{kind}-idle.png'
  if not sp.exists():continue
  cells += [(hp,'Vanilla '+hand+' '+vanilla),(sp,'Spirit '+hand+' '+kind),(base/f'{hand}-{kind}-item-only.png','Measurement only (paws hidden)')]
  if kind!='bow':
   h=pixels(hp,False,hand=='left');s=pixels(base/f'{hand}-{kind}-item-only.png',True,hand=='left')
   report['static'][f'{hand}-{kind}']={'vanilla':h,'spirit':s,'areaDeltaPercent':100*(s['bboxArea']/h['bboxArea']-1),'pixelAxisDelta':delta(h['pixelPcaAxis'],s['pixelPcaAxis']),'centreDelta':[sum(s['bbox'][i::2])/2-sum(h['bbox'][i::2])/2 for i in range(2)]}
 grid(cells,out/(hand+'-vanilla-comparison.png'))
for folder in sorted(base.glob('*-action')):
 if not(folder/'poses.json').exists():continue
 samples=json.loads((folder/'poses.json').read_text())['samples'];main='left'if folder.name.startswith('left')else'right';allarms=[];mainarms=[];intersections=0
 for f in samples:
  for arm in f['arms'].values():
   if 'screenMin'not in arm:continue
   allarms.append(arm);lo=arm['screenMin'];hi=arm['screenMax']
   if lo[0]<80/960 and hi[0]>-80/960 and lo[1]<80/507.5 and hi[1]>-80/507.5:intersections+=1
  a=f['arms'].get(main,{})
  if 'weapon'in a:mainarms.append(a)
 report['actions'][folder.name]={'frames':len(samples),'arms':len(allarms),'maxGripError':max(a['gripError']for a in allarms),'maxWristDegrees':max(a['wristDegrees']for a in allarms),'maxTwistDegrees':max(abs(a['twistDegrees'])for a in allarms),'elbowRange':[min(a['elbowDegrees']for a in allarms),max(a['elbowDegrees']for a in allarms)],'upperLengthRange':[min(a['upperLength']for a in allarms),max(a['upperLength']for a in allarms)],'forearmLengthRange':[min(a['forearmLength']for a in allarms),max(a['forearmLength']for a in allarms)],'nearPlaneVertices':sum(a['nearPlaneVertices']for a in allarms),'armBoundingBoxesIntersectingCrosshair80':intersections,'minWeaponNearClearance':min(a['weapon']['nearClearance']for a in mainarms)}
for r in report['static'].values():
 r['pass']=abs(r['areaDeltaPercent'])<=15 and r['pixelAxisDelta']<=12 and all(abs(d)<=60 for d in r['centreDelta'])
rows=json.loads((base/'sheets/frames.json').read_text())
for name,phases in rows.items():
 full=next(p for p in phases if p['label'] in ('windup100','released'));arm=full['arms']['left'if name.startswith('left')else'right'];report['actions'][name]['fullWindupVisible']=arm['weapon']['visible'];report['actions'][name]['phaseSamples']=[{k:p[k]for k in ['label','sourceSlot','nanos','clockErrorNanos','age','targetAge','phaseErrorTicks','phaseSource']}for p in phases]
for hand in ['right','left']:
 for kind,vanilla in [('bow','bow'),('spear','trident')]:
  if not(base/f'{hand}-{kind}-idle.png').exists():continue
  cells=[]
  for prefix,folder,item in [('Vanilla',human,vanilla),('Spirit',base,kind)]:
   cells.append((folder/(('human-'if prefix=='Vanilla'else'')+hand+'-'+item+'-idle.png'),prefix+' idle'))
   for phase in (['followFirst','followMid','released','follow'] if kind=='spear' and prefix=='Spirit' and (folder/'sheets'/f'{hand}-spear-action-released.png').exists() else ['windup25','windup50','windup100','hit' if kind=='spear' and prefix=='Spirit' else 'release']):
    p=folder/'sheets'/(('human-'if prefix=='Vanilla'else'')+hand+'-'+item+'-action-'+phase+'.png')
    cells.append((p,prefix+' '+phase))
  grid(cells,out/(hand+'-'+kind+'-phase-comparison.png'),5)
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
text=['# First-person weapon comparison','',report['pixelRule'],'','| Hand / weapon | Vanilla bbox area | Spirit bbox area | Difference | Pixel PCA delta | Centre delta px |','|---|---:|---:|---:|---:|---:|']
for name,r in report['static'].items():text.append(f"| {name} | {r['vanilla']['bboxArea']} | {r['spirit']['bboxArea']} | {r['areaDeltaPercent']:+.2f}% | {r['pixelAxisDelta']:.2f}° | {r['centreDelta'][0]:+.1f}, {r['centreDelta'][1]:+.1f} |")
text+=['','| Clip | Frames | Grip error (blocks) | Wrist / twist max | Weapon near clearance | Full windup visible | Arm bbox intersects ±80px |','|---|---:|---:|---:|---:|---:|---:|']
for n,r in report['actions'].items():text.append(f"| {n} | {r['frames']} | {r['maxGripError']:.6f} | {r['maxWristDegrees']:.2f}° / {r['maxTwistDegrees']:.2f}° | {r['minWeaponNearClearance']:.3f} | {r['fullWindupVisible']:.1%} | {r['armBoundingBoxesIntersectingCrosshair80']} |")
(out/'README.md').write_text('\n'.join(text)+'\n');print('\n'.join(text))
