#!/usr/bin/env python3
"""Frame-accurate weapon sheets. Video/pose pairs must match the same Post-event nanosecond."""
import json,sys,subprocess,statistics
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

base=Path(sys.argv[1]);out=base/'sheets';out.mkdir(exist_ok=True)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',17)
report={}
def sheet(cells,path,cols=5):
 w,h=480,254;im=Image.new('RGB',(w*cols,(h+30)*((len(cells)+cols-1)//cols)),(26,28,35));d=ImageDraw.Draw(im)
 for i,(p,label)in enumerate(cells):
  a=Image.open(p).convert('RGB');a.thumbnail((w,h));x=i%cols*w;y=i//cols*(h+30);im.paste(a,(x,y+30));d.text((x+4,y+5),label,font=font,fill='white')
 im.save(path)
for folder in sorted(base.glob('*-action')):
 if not(folder/'poses.json').exists():continue
 name=folder.name;kind=name.split('-')[-2]
 poses=json.loads((folder/'poses.json').read_text())['samples'];timings=json.loads((folder/'frames.json').read_text())['frames']
 ns={s['nanos']:s for s in poses}
 matches,origin=max((sum(s['nanos']-round(timings[0]['seconds']*1e9)+round(f['seconds']*1e9)in ns for f in timings),s['nanos']-round(timings[0]['seconds']*1e9))for s in poses[:60])
 assert matches==len(timings),(name,matches,len(timings))
 captured=[(f,ns[origin+round(f['seconds']*1e9)])for f in timings]
 motion=[s for s in json.loads((folder/'motion.json').read_text())['samples']if s['side']=='client']
 using=[s for s in motion if s['state']['combat']['usingItem']]
 precise = not name.startswith('human-') and any(s.get('firstPerson',{}).get('timing') for f,s in captured)
 if precise:
  def elapsed(f,s):
   fp=s['firstPerson'];now=fp['timing']['now']
   if kind=='spear':return now-fp['actionAt']
   if kind=='bow':return now-fp['timing']['bowStart']
   return fp['useTicks']+(now-int(now))
  full=12 if kind=='hammer' else 5.5 if kind=='spear' else 20
  start=0;targets=[('windup25',full*.25),('windup50',full*.5),('windup100',full)]
  if kind=='spear':targets=[('followFirst',1),('followMid',1.5),('released',2.1),('follow',3),('recovery',8),('reform',23)]
  elif kind=='hammer':targets += [('release',1),('impact',4),('recovery',8)]
  else:targets += [('release',1),('recovery',5)]
  timefield=elapsed
 elif using:
  start=statistics.median(s['tick']-s['state']['combat']['useTicks']for s in using)
  end=next(s['tick']for s in motion if s['tick']>using[-1]['tick']and not s['state']['combat']['usingItem'])
  full=12 if kind=='hammer'else 10 if kind=='trident'else 20
  targets=[('windup25',start+full*.25),('windup50',start+full*.5),('windup100',start+full),('release',end+1),('recovery',end+5)]
  timefield=lambda f,s:s['gameTime']
 else:
  # Pre-migration instant throws had no per-frame event age. Preserve the exact frame clock,
  # but label this baseline's action-phase estimate explicitly; new captures expose FP age.
  start=1;targets=[('windup25',1.3),('windup50',1.6),('windup100',2.1),('hit',2.6),('recovery',4)]
  timefield=lambda f,s:f['seconds']
 cells=[];rows=[];used=set()
 for label,target in targets:
  eligible=[(f,s) for f,s in captured if f['slot'] not in used]
  phasefield=timefield
  if precise:
   if kind in ('bow','hammer') and label in ('release','impact','recovery'):
    def phasefield(f,s):
     fp=s['firstPerson'];return fp['timing']['now']-(fp['timing']['bowRelease'] if kind=='bow' else fp['hammerReleasedAt'])
    eligible=[(f,s) for f,s in eligible if not s['firstPerson']['using'] and 0<=phasefield(f,s)<15]
   elif kind in ('bow','hammer'):
    eligible=[(f,s) for f,s in eligible if s['firstPerson']['using']]
   else:eligible=[(f,s) for f,s in eligible if s['firstPerson']['action']=='SPEAR_THROW' and 0<=phasefield(f,s)<35]
  f,s=min(eligible,key=lambda pair:abs(phasefield(*pair)-target))
  used.add(f['slot']);p=out/(name+'-'+label+'.png')
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(folder/'capture.mp4'),'-vf',f"select=eq(n\\,{f['slot']})",'-fps_mode','vfr','-frames:v','1',str(p)],check=True)
  age=phasefield(f,s)-start
  if precise and (label.startswith('windup') or (kind=='spear' and label in ('followFirst','followMid','released','follow'))):assert abs(age-target)<=.6,(name,label,age,target,'insufficient captured phases')
  cells.append((p,f'{kind} {label} t={age:.2f}'))
  rows.append({'label':label,'sourceSlot':f['slot'],'poseFrame':s['frame'],'nanos':s['nanos'],'clockErrorNanos':0,'age':age,'targetAge':target-start,'phaseErrorTicks':abs(age-(target-start)),'phaseSource':'render event timing'if precise else 'client use ticks'if using else'estimated real input onset (baseline only)', 'firstPerson':s.get('firstPerson',{}),'arms':s['arms']})
 sheet(cells,out/(name+'.png'));report[name]=rows
(out/'frames.json').write_text(json.dumps(report,indent=2)+'\n')
for arm in ['right','left']:
 cells=[]
 for human,kinds in [(True,['bow','mace','trident']),(False,['bow','hammer','spear'])]:
  for kind in kinds:
   p=base/(('human-'if human else'')+arm+'-'+kind+'-idle.png')
   if p.exists():cells.append((p,p.stem))
 if cells:sheet(cells,out/(arm+'-idle.png'),3)
