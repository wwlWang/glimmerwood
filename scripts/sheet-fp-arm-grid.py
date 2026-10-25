#!/usr/bin/env python3
"""Build review sheets from original captures. All crops/scales are presentation only."""
import json,sys,subprocess
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
base=Path(sys.argv[1]);out=base/'sheets';out.mkdir(exist_ok=True)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)

def sheet(cells,cols,path,w=640,h=338):
 im=Image.new('RGB',(cols*w,((len(cells)+cols-1)//cols)*(h+32)),(26,28,35));d=ImageDraw.Draw(im)
 for i,(p,label) in enumerate(cells):
  pic=Image.open(p).convert('RGB');pic.thumbnail((w,h));x=(i%cols)*w;y=(i//cols)*(h+32)
  im.paste(pic,(x,y+32));d.text((x+6,y+5),label,font=font,fill='white')
 im.save(path)

for arm in [a for a in ['right','left'] if (base/(a+'-blade-idle.png')).exists()]:
 cells=[]
 for kind in ['empty','block','blade']:
  for human in [True,False]:
   name=('human-'if human else '')+arm+'-'+kind+'-idle';cells.append((base/(name+'.png'),name))
 if all(p.exists()for p,label in cells):sheet(cells,2,out/(arm+'-idle-comparison.png'))
 if (base/('human-'+arm+'-sword-idle.png')).exists():sheet([(base/('human-'+arm+'-sword-idle.png'),'Human iron sword'),(base/(arm+'-blade-idle.png'),'Spirit Blade + paw')],2,out/(arm+'-iron-axis-reference.png'))

(base/'measurements').mkdir(exist_ok=True)
results=json.loads((base/'results.json').read_text());report={}
for arm in [a for a in ['right','left'] if (base/(a+'-blade-idle.png')).exists()]:
 name=arm+'-blade-combo';folder=base/name;samples=json.loads((folder/'poses.json').read_text())['samples']
 timings=json.loads((folder/'frames.json').read_text())['frames'];cells=[];details=[]
 # PoseRecorder and FrameRecorder both use the very same RenderFrameEvent.Post nano timestamp.
 # Recover the exact origin by matching their delta sequence, not the later /state response.
 nanos={s['nanos']:s for s in samples};candidates=[]
 for s in samples[:60]:
  origin=s['nanos']-round(timings[0]['seconds']*1e9)
  matches=sum(origin+round(f['seconds']*1e9) in nanos for f in timings)
  candidates.append((matches,origin))
 matches,origin=max(candidates)
 if matches!=len(timings):raise RuntimeError(f'{arm}: video/pose clock match {matches}/{len(timings)}')
 captured=[(f,nanos[origin+round(f['seconds']*1e9)])for f in timings]
 # Select one complete real combo with distinct captured frames; never invent in-between poses.
 by_stage={}
 for stage in [1,2,3]:
  wind=[0,2,3,4][stage]
  targets=[('windup25',wind*.25),('windup50',wind*.5),('windup100',wind),('hit',wind+1),('recovery',wind+3)]
  active=[(f,s) for f,s in captured if s['combat']['mode']=='blade' and s['combat']['stage']==stage]
  options=[]
  for aid in sorted(set(s['combat']['actionId']for f,s in active)):
   available=[(f,s)for f,s in active if s['combat']['actionId']==aid]
   previous=[]
   for target_index,(label,target) in enumerate(targets):
    current=[]
    for i,(f,s) in enumerate(available):
     age=s['gameTime']-s['combat']['startedAt'];pick=(label,target,f,s,age)
     if target_index==0:current.append((abs(age-target),[pick]))
     else:
      earlier=[(cost,picks)for j,(cost,picks)in enumerate(previous)if j<i and picks and picks[-1][4]<age]
      if not earlier:current.append((float('inf'),[]));continue
      cost,picks=min(earlier,key=lambda v:v[0]);current.append((cost+abs(age-target),picks+[pick]))
    previous=current
   error,picks=min(previous,key=lambda v:v[0])
   if len(picks)==len(targets):options.append((error,aid,picks))
  if not options:raise RuntimeError(f'{arm} stage {stage}: insufficient distinct captured frames')
  by_stage[stage]={aid:(error,aid,picks)for error,aid,picks in options}
 combos=[(sum(by_stage[stage][aid+stage-1][0]for stage in [1,2,3]),aid)
         for aid in by_stage[1] if aid+1 in by_stage[2] and aid+2 in by_stage[3]]
 if not combos:raise RuntimeError(f'{arm}: no complete consecutive three-stage combo')
 _,combo_start=min(combos)
 for stage in [1,2,3]:
  error,aid,picks=by_stage[stage][combo_start+stage-1]
  for label,target,frame,s,age in picks:
   p=out/(arm+'-stage'+str(stage)+'-'+label+'.png')
   subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(folder/'capture.mp4'),
       '-vf',f"select=eq(n\\,{frame['slot']})",'-fps_mode','vfr','-frames:v','1',str(p)],check=True)
   cells.append((p,f'{arm} stage {stage} {label} (t={age:.2f})'))
   details.append({'stage':stage,'label':label,'age':age,'targetAge':target,'actionId':aid,'sourceSlot':frame['slot'],
       'poseFrame':s['frame'],'videoOriginNanos':origin,'clockErrorNanos':0,'ageErrorTicks':abs(age-target),'arms':s['arms'][arm]})
 sheet(cells,5,out/(arm+'-combo.png'),480,254)
 metrics=[s['arms'][arm]for s in samples if s['arms'].get(arm)]
 report[arm]={'sampleCount':len(metrics),'stages':sorted(set(s['combat']['stage']for s in samples if s['combat']['mode']=='blade')),
  'maxWrist':max(m['wristDegrees']for m in metrics),'maxAbsoluteTwist':max(abs(m['twistDegrees'])for m in metrics),
  'elbowRange':[min(m['elbowDegrees']for m in metrics),max(m['elbowDegrees']for m in metrics)],
  'maxGripDistance':max(m['gripError']for m in metrics),'maxNearPlaneVertices':max(m['nearPlaneVertices']for m in metrics),
  'maxCrosshairVertices':max(m['crosshairVertices']for m in metrics),'frames':details}
(base/'measurements/combo.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:{a:b for a,b in v.items()if a!='frames'}for k,v in report.items()},indent=2))
