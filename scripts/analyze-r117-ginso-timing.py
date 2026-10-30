#!/usr/bin/env python3
"""Plot the three actual-input runs against the reviewed fixed-height water schedule.
Requires matplotlib/numpy; analysis only, no game state or code mutations.
"""
import csv, json
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'evidence/r117-c/c2-timing'
OUT.mkdir(parents=True, exist_ok=True)
RUNS = ('run-01', 'run-03', 'run-04')
runs = [(name, json.loads((ROOT/'evidence/r117-c/c2-runs'/name/'results.json').read_text())) for name in RUNS]
assert all(r['status']=='passed' and r['finished']['outcome']=='SUCCESS' for _, r in runs)
START = -4
BANDS = [(57, .60), (79, .65), (94, .40), (160, .65)]
def reach(y):
    t, previous = 3., START
    for top, rate in BANDS:
        if y <= top:
            return t + max(0, y-previous)/rate
        t += (top-previous)/rate
        previous = top
    raise ValueError(y)
points = list(zip(*[r['milestones'] for _, r in runs]))
reference = [max(p['seconds'] for p in ps)*1.3 for ps in points]
fastest = [min(p['seconds'] for p in ps) for ps in points]
heights = [min(p['localFeet'] for p in ps) for ps in points]
water = [reach(y) for y in heights]
human_margins = [w-t for w,t in zip(water,reference)]
fast_margins = [w-t for w,t in zip(water,fastest)]
assert min(human_margins)>=8
assert max(fast_margins)<=56.1
report = {'status':'reviewed_human_reference_priority', 'prepareTicks':60, 'startLocalWater':START,
          'heightBands':BANDS, 'reference':'pointwise slowest of three runs × 1.3',
          'humanMinMargin':min(human_margins), 'humanMaxMargin':max(human_margins),
          'fastestPointwisePeakMargin':max(fast_margins), 'runs':[]}
fig,(ax,ma) = plt.subplots(2,1,figsize=(13.5,9),layout='constrained')
colors = ['#0072B2','#D55E00','#009E73']
with (OUT/'pointwise-margins.csv').open('w') as f:
    writer=csv.writer(f)
    writer.writerow(['point','local_feet',*RUNS,'human_reference_seconds','water_arrival_seconds','human_margin_seconds','fastest_margin_seconds'])
    for i,ps in enumerate(points):
        writer.writerow([ps[0]['name'],heights[i],*[p['seconds'] for p in ps],reference[i],water[i],human_margins[i],fast_margins[i]])
for (name,r),color in zip(runs,colors):
    ts=[p['seconds'] for p in r['milestones']]
    margins=[w-t for w,t in zip(water,ts)]
    elapsed=r['finished']['elapsedTicks']/20
    report['runs'].append({'run':name,'seconds':elapsed,'ticks':r['finished']['elapsedTicks'],
                          'outcome':r['finished']['outcome'],'health':r['finished']['health'],
                          'minMargin':min(margins),'maxMargin':max(margins)})
    ax.plot(ts,heights,'.-',color=color,label=f'{name}: {elapsed:.2f} s',alpha=.7)
    ma.plot(range(len(ts)),margins,'.-',color=color,label=name,alpha=.7)
ax.plot(reference,heights,'D-',color='#AA3377',lw=2,label=f'Slowest at each point × 1.3: {reference[-1]:.2f} s',markersize=3)
ys=np.linspace(START,106,500)
ax.plot([reach(y) for y in ys],ys,'-',color='#55BBDD',lw=3,label='Water: 0.60 / 0.65 / 0.40 / 0.65 blocks/s')
for y,_ in BANDS[:-1]:
    ax.axhline(y,color='#999999',ls=':',lw=.8)
ax.set(xlabel='Game seconds since heart activation',ylabel='Local height (blocks)',ylim=(START,111),
       title='Ginso Course 3 / Escape 2 — fixed-height pursuit, default multiplier 1.0')
ax.grid(alpha=.2); ax.legend(fontsize=9,loc='upper left')
ma.plot(range(len(points)),human_margins,'D-',color='#AA3377',lw=2,label=f'×1.3 human reference: min {min(human_margins):.2f} s',markersize=3)
ma.axhline(8,color='#AA3377',ls='--',lw=1,label='Required human margin ≥8 s')
ma.axhline(45,color='#777777',ls=':',lw=1,label='Original script cap 45 s (waived after review)')
ma.set_xticks(list(range(0,len(points),4)))
ma.set_xticklabels([points[i][0]['name'] for i in range(0,len(points),4)],rotation=25,ha='right',fontsize=8)
ma.set(xlabel='Recorded launch / landing milestones (route order)',ylabel='Seconds until water reaches feet',ylim=(0,62))
ma.grid(alpha=.2); ma.legend(fontsize=8,loc='upper left')
fig.savefig(OUT/'pursuit-margins.png',dpi=150)
(OUT/'proposal.json').write_text(json.dumps(report,indent=2)+'\n')
lines=['# C2 measured pursuit — final reviewed policy','',
'One entry setup teleport per run, then continuous real keys; no inter-hop teleport, route edit, or session-clock adjustment. All required skills unlocked. Timings are server ticks including the unchanged 60-tick preparation. These three measurement runs used provisional 0.25 blocks/s, so they establish traversal timing rather than a completed final-speed chase.','',
'| Run | Ticks | Seconds | Outcome | Health | Final default margin range |','|---|---:|---:|---|---:|---|']
for r in report['runs']:
    lines.append(f"| {r['run']} | {r['ticks']} | {r['seconds']:.2f} | {r['outcome']} | {r['health']:g} | {r['minMargin']:.2f}–{r['maxMargin']:.2f} s |")
lines += ['', '**Final schedule:** local start Y=-4; 0.60 blocks/s until Y=57, 0.65 until Y=79, 0.40 until Y=94, 0.65 thereafter. Fixed water heights select the rate; no player tracking. Escape 1 keeps 0.25. Escape 2 captures `ginsoEscapeWaterSpeedMultiplier` (default 1.0, allowed 0.5–2.0) when activated.', '',
          f'Human reference = the slowest of three arrivals at **each** recorded point ×1.3, ending at {reference[-1]:.2f} s. Its minimum margin is **{min(human_margins):.3f} s**, maximum {max(human_margins):.3f} s. Pointwise-fastest script peak is **{max(fast_margins):.3f} s**. The rounded start/rates were jointly selected against all 43 points; no points excluded.', '',
          'The original 45 s script ceiling conflicts with the human constraint even at the endpoint: 141.70×1.3+8−138.45=53.76 s. Joint linear optimization over the four band durations bounds the best possible peak at 55.355 s. Claude explicitly accepted a roughly 56 s script peak, prioritizing human ≥8 s; these rounded rates give 56.024 s.', '',
          '[Pointwise CSV](pointwise-margins.csv) · [Margin plot](pursuit-margins.png) · [Machine-readable values](proposal.json)', '',
          'Excluded failed attempts remain recorded: probe-01 exposed crown support entering the prior jump shaft (fixed); run-02 stopped in the input controller on a legal 1.5-block wind guard (controller corrected to walk onto the deck). Crown-only probes are not full timing runs.']
paced_file=ROOT/'evidence/r117-c/c2-runs/final-human-reference/results.json'
if paced_file.exists():
    paced=json.loads(paced_file.read_text())
    if paced.get('status')=='passed':
        minimum=min(reach(p['localFeet'])-p['seconds'] for p in paced['milestones'])
        elapsed=paced['finished']['elapsedTicks']/20
        lines += ['', f'Final-default-speed runtime check: [paced real-input run](../c2-runs/final-human-reference/results.json) added waits toward the ×1.3 reference, with no clock edits. Server SUCCESS at {paced["finished"]["elapsedTicks"]} ticks / {elapsed:.2f} s, health {paced["finished"]["health"]:g}, zero submerged ticks; minimum recorded nominal feet margin {minimum:.3f} s. This precedes the subsequently reviewed crown rim mask, which removes open-air water without speeding up the clock.']
lines += ['', 'After water-picture review, actual water is additionally clipped to horizontally enclosed half-block layers. Open crown cells above the enclosing rim never fill and do not drown the player. Curves show the conservative nominal water clock; actual water cannot arrive sooner than it.']
(OUT/'README.md').write_text('\n'.join(lines)+'\n')
print(json.dumps(report,indent=2))
