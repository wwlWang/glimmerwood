"""Offline execution-cost model. No acceptance claims, no game or production writes."""
from pathlib import Path
from collections import defaultdict,Counter
import json,math,statistics,csv,sys
P=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parent;PAR=P.parent
load=lambda p:json.loads(p.read_text())
OLD=load(PAR/'cave-order-optimization-v001/after-input-grid/cave-input-grid.json')
REF=load(PAR/'approved-grid-reference.json')
ACTUAL=load(PAR/'cold-all-diagnostic-v001/annotated-rounds.json')['rows']
SUP=load(P/'run-02/simulation-inputs.json');assert SUP['status']=='complete'
K=['forest_spring','creek_ruins','cave_altar']
def key(cp,k):return f'{cp[0]},{cp[1]}:{k}'
def frames(r):return r['sampling']['evaluations'] if 'sampling' in r else r['evaluations']
D={key(r['chunk'],r['kind']):r for r in OLD['rows']}
for r in SUP['decisions']:D[key(r['chunk'],r['kind'])]=r
META={tuple(r['chunk']):r for r in SUP['metadata']}
BUILD={key(r['chunk'],r['kind']):r for r in SUP['builders']}
NEIGH={tuple(map(int,k.split(','))):list(map(tuple,v)) for k,v in SUP['neighbours'].items()}
# Recorded height is noise-pure and independent of evaluation/recording order.
HEIGHT={};CH=defaultdict(list)
for r in D.values():
 for e in frames(r):
  if e['kind'] not in K:continue
  for s in e['samples']:
   q=(s['x'],s['z']);h=s['height'];assert q not in HEIGHT or HEIGHT[q]==h,(q,HEIGHT.get(q),h);HEIGHT[q]=h;CH[q[0]//4,q[1]//4].append(h)
for ss in SUP['supplemental'].values():
 for s in ss:
  q=(s['x'],s['z']);h=s['height'];assert q not in HEIGHT or HEIGHT[q]==h,(q,HEIGHT.get(q),h);HEIGHT[q]=h;CH[q[0]//4,q[1]//4].append(h)
CELLH={q:statistics.median(h) for q,h in CH.items()}
def intersects(a,b):return any(x['maxX']>=y['minX'] and x['minX']<=y['maxX'] and x['maxZ']>=y['minZ'] and x['minZ']<=y['maxZ'] for x in a for y in b)
def pct(a,p):return sorted(a)[min(len(a)-1,math.ceil(len(a)*p)-1)]
# Three physically interpretable cost terms: exact cell, extra vertical distance,
# vanilla height/column query; small wrapper cost is calibrated separately.
def features_from_actual(r):
 cells=[];evals=0
 for e in r['sampling']['evaluations']:
  if e['kind'] not in K:continue
  evals+=1
  for s in e['samples']:
   if s['newNoiseCells']:
    assert s['newNoiseCells']==1;cells.append((s['x']//4,s['z']//4))
 n=r['sampling']['noiseTotals'];assert len(cells)==n['noiseCells']
 return [len(cells),sum((80-CELLH[q])/8 for q in cells),n['baseHeight']+n['baseColumn'],1]
def solve(A,b):
 a=[list(row)+[value] for row,value in zip(A,b)]
 for i in range(len(b)):
  pivot=max(range(i,len(b)),key=lambda j:abs(a[j][i]));a[i],a[pivot]=a[pivot],a[i];assert abs(a[i][i])>1e-10
  v=a[i][i];a[i]=[x/v for x in a[i]]
  for j in range(len(b)):
   if j==i:continue
   v=a[j][i];a[j]=[x-v*y for x,y in zip(a[j],a[i])]
 return [row[-1] for row in a]
def fit(rows):
 X=[features_from_actual(r) for r in rows];y=[r['totalWallMs'] for r in rows];n=len(X[0]);return solve([[sum(x[i]*x[j] for x in X) for j in range(n)] for i in range(n)],[sum(x[i]*v for x,v in zip(X,y)) for i in range(n)])
COEF=fit(ACTUAL)
def predict(f,coef=COEF):return max(0,sum(x*y for x,y in zip(f,coef)))
CAL=[]
for r in ACTUAL:
 f=features_from_actual(r);cp=tuple(r['chunk']);cv=fit([x for x in ACTUAL if tuple(x['chunk'])!=cp]);m=predict(f);v=predict(f,cv);a=r['totalWallMs'];CAL.append({'chunk':r['chunk'],'round':r['round'],'actualMs':a,'modelMs':m,'residualMs':m-a,'residualPct':100*(m/a-1),'leavePointOutMs':v,'leavePointOutResidualMs':v-a,'leavePointOutResidualPct':100*(v/a-1),'features':f})
# Foreign qualification factories are cached by set+chunk; use cold recorded
# nested calls, never the warm tiny wrapper time from the chronological grid.
def foreign_groups(r):
 groups={}
 for e in frames(r):
  if not e['kind'].startswith('qualification:'):continue
  identifier=e['kind'].split(':',1)[1];setid='minecraft:villages' if identifier.startswith('minecraft:village_') else 'minecraft:pillager_outposts' if identifier=='minecraft:pillager_outpost' else 'orimod:glades_outpost'
  k=(setid,*e['chunk']);n=e.get('exclusiveNoise',{});v=groups.setdefault(k,{'queries':0,'frames':[]});v['queries']+=n.get('baseHeight',0)+n.get('baseColumn',0);v['frames'].append(e)
  for child in frames(r):
   if child['kind']=='glades_terrain' and child.get('parent')==e['kind'] and child['chunk']==e['chunk']:
    nn=child['exclusiveNoise'];v['queries']+=nn.get('baseHeight',0)+nn.get('baseColumn',0)
 return groups
class Sim:
 def __init__(self,mode):
  self.mode=mode;self.A=mode!='current';self.B=mode in ['A+B','A+B+C'];self.C=mode=='A+B+C';self.base={};self.selections={};self.globalcells=set();self.cells=[];self.queries=0;self.foreign=set();self.trace=[];self.yields=[];self.basecalls=0;self.heightRequests=0;self.newR2=0;self.early=[]
 def shape(self,cp,k):return META[cp]['shapes'][k]['boxes']
 def sample(self,q,seen,local):
  q=tuple(q);self.heightRequests+=1
  if q in seen:return HEIGHT[q]
  seen.add(q);cell=(q[0]//4,q[1]//4)
  if cell not in local:
   local.add(cell)
   if not self.B or cell not in self.globalcells:self.cells.append(cell)
   self.globalcells.add(cell)
  return HEIGHT[q]
 def evaluate(self,cp,k,owner=False):
  ky=key(cp,k)
  if ky in self.base:return self.base[ky],False
  r=D[ky];fs=[e for e in frames(r) if e['kind']==k];e=fs[0] if fs else None;samples=e['samples'] if e else []
  startcells=len(self.cells);startqueries=self.queries;self.basecalls+=1;seen=set();local=set();early_yield=False;early_reason=None
  s=lambda q:self.sample(q,seen,local)
  core=[p for p in samples if p['stage']=='forest_core']
  if self.A and k=='forest_spring' and core:
   lo=10**9;hi=-10**9
   for q in META[cp]['shapes'][k]['entry']:
    h=s(q);lo=min(lo,h);hi=max(hi,h)
    if hi-lo>4 or lo<63 or hi>145:early_reason='ENTRY_RELIEF';break
   if early_reason is None:
    for p in core:
     h=s((p['x'],p['z']));lo=min(lo,h);hi=max(hi,h)
     if hi-lo>4 or lo<63 or hi>145:early_reason='CORE_ENTRY_RELIEF';break
   if early_reason is None:
    assert len(core)==54,(ky,r['reason'],len(core))
    if owner and self.C:
     # Four existing outer corner samples; not a new/looser predicate.
     cl=min(p['height'] for p in core);ch=max(p['height'] for p in core)
     for q in META[cp]['shapes'][k]['corners']:
      h=s(q);cl=min(cl,h);ch=max(ch,h)
      if ch-cl>8 or cl<63 or ch>145:early_reason='OUTER_CORNER';break
     if early_reason is None:
      if r['reason']!='PASS':self.newR2+=1
      if self.yielding(cp,k):early_yield=True
    if early_reason is None and not early_yield:
     for p in samples:s((p['x'],p['z']))
  else:
   for p in samples:s((p['x'],p['z']))
  if early_reason is not None:assert not r['accepted'],('Changed acceptance',ky,early_reason)
  if early_yield:
   self.early.append({'chunk':list(cp),'baseReason':r['reason'],'kind':k})
  elif early_reason is None:
   for q,v in foreign_groups(r).items():
    if q not in self.foreign:self.foreign.add(q);self.queries+=v['queries']
  accepted=r['accepted'] and early_reason is None
  if not early_yield:self.base[ky]=accepted
  self.trace.append({'chunk':list(cp),'kind':k,'owner':owner,'baseReason':r['reason'],'earlyReason':early_reason,'yieldSkippedTail':early_yield,'inclusiveCells':len(self.cells)-startcells,'inclusiveQueries':self.queries-startqueries})
  return accepted,early_yield
 def collision(self,cp,boxes):
  if cp in self.selections:return self.selections[cp]
  order=META[cp]['order'];relevant=[intersects(boxes,self.shape(cp,k)) for k in order]
  for i,k in enumerate(order):
   if not relevant[i] or not self.evaluate(cp,k)[0]:continue
   winner=k
   for j in range(i):
    if not relevant[j] and self.evaluate(cp,order[j])[0]:winner=order[j];break
   self.selections[cp]=winner;return winner
  return None
 def yielding(self,cp,k):
  boxes=self.shape(cp,k);queried=[]
  for n in NEIGH[cp]:
   if not any(intersects(boxes,self.shape(n,kind)) for kind in K):continue
   other=self.collision(n,boxes);denied=other is not None and intersects(boxes,self.shape(n,other));queried.append({'chunk':list(n),'selected':other,'denied':denied})
   if denied:self.yields.append({'owner':list(cp),'kind':k,'denied':True,'queries':queried});return True
  self.yields.append({'owner':list(cp),'kind':k,'denied':False,'queries':queried});return False
 def start(self,cp):
  selected='NONE';tries=[]
  for k in META[cp]['order']:
   tries.append(k);accepted,early=self.evaluate(cp,k,True)
   if early or not accepted:continue
   # C may have already checked R2; its cached answer is unchanged. Avoid double
   # wrappers (zero modeled sampling cost either way).
   if k!='cave_altar':
    previous=next((y for y in reversed(self.yields) if y['owner']==list(cp) and y['kind']==k),None)
    if previous is None or not(self.C and k=='forest_spring'):
     if self.yielding(cp,k):continue
    elif previous['denied']:continue
   selected=k
   b=BUILD[key(cp,k)];n=b['noise']['noiseTotals'];assert not n['noiseCells'];self.queries+=n['baseHeight']+n['baseColumn'];break
  f=[len(self.cells),sum((80-CELLH[q])/8 for q in self.cells),self.queries,1]
  return {'chunk':list(cp),'kind':selected,'tries':tries,'predictedMs':predict(f),'features':f,'cellBuilds':len(self.cells),'distinctCells':len(set(self.cells)),'vanillaQueries':self.queries,'evaluateCalls':self.basecalls,'heightRequests':self.heightRequests,'newEarlyR2':self.newR2,'earlyYield':self.early,'r2':self.yields,'trace':self.trace}
MODES=['current','A','A+B','A+B+C'];OUT={};DRIFT=[]
for mode in MODES:
 rows=[]
 for ref in REF['rows']:
  cp=tuple(ref['chunk']);s=Sim(mode);r=s.start(cp);rows.append(r)
  if r['kind']!=ref['kind']:DRIFT.append({'mode':mode,'chunk':list(cp),'expected':ref['kind'],'actual':r['kind']})
 OUT[mode]=rows
 (P/('simulation-'+mode.replace('+','-')+'.json')).write_text(json.dumps(rows,indent=2))
 print(mode,Counter(r['kind'] for r in rows),'max',max(r['predictedMs'] for r in rows),'sum',sum(r['predictedMs'] for r in rows),flush=True)
assert not DRIFT,DRIFT[:10]
# Validate the execution model against exact raw calls/cell identities for all 87
# observed current starts. This catches caches, weighted fallback, R2 priority.
CURRENT={tuple(r['chunk']):r for r in OUT['current']};WORK=[]
for actual in ACTUAL:
 cp=tuple(actual['chunk']);model=CURRENT[cp];af=features_from_actual(actual);mf=model['features']
 calls=[(e['kind'],e['chunk']) for e in actual['sampling']['evaluations'] if e['kind'] in K];mc=[(e['kind'],e['chunk']) for e in model['trace']]
 WORK.append({'chunk':list(cp),'round':actual['round'],'actualFeatures':af,'modelFeatures':mf,'equal':af==mf,'actualCalls':calls,'modelCalls':mc,'callsEqual':calls==mc})
assert all(x['equal'] and x['callsEqual'] for x in WORK),[x for x in WORK if not(x['equal'] and x['callsEqual'])][:3]
base=sum(r['predictedMs'] for r in OUT['current']);summary={}
for mode,rows in OUT.items():
 total=sum(r['predictedMs'] for r in rows);top=sorted(rows,key=lambda r:r['predictedMs'],reverse=True)[:30]
 summary[mode]={'maxMs':top[0]['predictedMs'],'maxChunk':top[0]['chunk'],'coldSumMs':total,'changePct':100*(total/base-1),'over1800':sum(r['predictedMs']>1800 for r in rows),'over2000':sum(r['predictedMs']>2000 for r in rows),'newR2Count':sum(r['newEarlyR2'] for r in rows),'counts':dict(Counter(r['kind'] for r in rows)),'top30':top}
res=lambda name:{'meanMs':statistics.mean(r[name] for r in CAL),'maeMs':statistics.mean(abs(r[name]) for r in CAL),'rmseMs':math.sqrt(statistics.mean(r[name]**2 for r in CAL)),'absP90Ms':pct([abs(r[name]) for r in CAL],.9),'absMaxMs':max(abs(r[name]) for r in CAL)}
result={'status':'complete','mode':'offline prediction, not measured acceptance','coefficients':dict(zip(['exactCellAtY80Ms','additional8BlocksDepthMs','vanillaQueryMs','startInterceptMs'],COEF)),'calibration':res('residualMs'),'leavePointOut':res('leavePointOutResidualMs'),'coverage':{'originalPoints':2025,'metadataPoints':len(META),'independentDecisions':len(D),'supplementedDecisions':len(SUP['decisions']),'all87WorkloadsEqual':True},'options':summary,'gate':{'max1800':summary['A+B+C']['maxMs']<=1800,'sumPlus5Pct':summary['A+B+C']['changePct']<=5},'noProductionChanges':True}
(P/'summary.json').write_text(json.dumps(result,indent=2));(P/'calibration.json').write_text(json.dumps(CAL,indent=2));(P/'workload-verification.json').write_text(json.dumps(WORK,indent=2))
def tsv(name,rows):
 with (P/name).open('w') as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]),delimiter='\t');w.writeheader();w.writerows({k:json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for k,v in r.items()} for r in rows)
tsv('calibration.tsv',CAL)
table=[]
for i,ref in enumerate(REF['rows']):
 r={'chunkX':ref['chunk'][0],'chunkZ':ref['chunk'][1],'kind':ref['kind']}
 for mode in MODES:
  for field in ['predictedMs','cellBuilds','vanillaQueries','evaluateCalls','newEarlyR2']:r[mode+'_'+field]=OUT[mode][i][field]
 table.append(r)
tsv('all-2025.tsv',table)
print(json.dumps({k:v for k,v in result.items() if k!='options'},indent=2))
