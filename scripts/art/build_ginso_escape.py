#!/usr/bin/env python3
"""Export the production Java escape geometry, rules and review sections. No Minecraft startup.
Only immutable coordinate/name stubs are supplied; geometry and chase arithmetic are not duplicated.
"""
import argparse, csv, hashlib, json, os, subprocess, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/java/cn/wwl/orimod/world'
OUT = ROOT / 'art/source/ginso_escape/v003'
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
parser.add_argument('--report', type=Path)
args = parser.parse_args()
java = Path(os.environ.get('JAVA_HOME', Path.home()/'.local/opt/jdk-21'))/'bin'
inputs = [SOURCE/n for n in ('GinsoInterior.java','GinsoCourses.java','TallGinsoInterior.java',
                             'GinsoEscapeLayout.java','GinsoEscapeRules.java','GinsoShape.java','LegacyGinsoShape.java','TallGinsoShape.java')]
stubs = {
 'BlockPos.java': '''package net.minecraft.core; public record BlockPos(int x,int y,int z) {
 public static final BlockPos ZERO=new BlockPos(0,0,0); public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}}''',
 'Ability.java': 'package cn.wwl.orimod.progression; public enum Ability {BASH,GRAPPLE,GLIDE;public final int bit=1<<ordinal();}',
 'Export.java': r'''import cn.wwl.orimod.world.*; public class Export {
 static void box(String k,String id,GinsoEscapeLayout.Box b){System.out.println(k+" "+id+" "+b.x0()+" "+b.y0()+" "+b.z0()+" "+b.x1()+" "+b.y1()+" "+b.z1());}
 public static void main(String[] args){for(String arg:args){long seed=Long.parseLong(arg);var shape=new GinsoShape(seed,3);
 for(int turn=0;turn<4;turn++){String id=seed+":"+turn;long started=System.nanoTime();var e=GinsoEscapeLayout.forTree(seed,turn,shape);
 System.err.println("Escape "+id+" H="+shape.height+" cold="+(System.nanoTime()-started)/1e6+" ms");var p=GinsoEscapeRules.plan(e);
 System.out.println("P "+id+" "+shape.height+" "+e.cleftIndex()+" "+e.branchIndex()+" "+p.startWater()+" "+p.limitTicks()+" "+p.referenceSeconds()+" "+GinsoEscapeRules.stationaryFailureSeconds(p,.85)+" "+GinsoEscapeRules.stationaryFailureSeconds(p,1.62));
 System.out.println("M "+id+" "+e.audit().clearanceRemoved()+" "+e.audit().trimmed()+" "+e.audit().maximumTrim()+" "+e.audit().bracedBlocks()+" "+e.audit().maximumSevered());
 e.blocks().forEach((q,b)->System.out.println("C "+id+" "+q.getX()+" "+q.getY()+" "+q.getZ()+" "+b));
 for(var q:e.path())System.out.println("R "+id+" "+q.x()+" "+q.feet()+" "+q.z()+" "+q.distance()+" "+q.corners());
 for(var b:e.route())box("A",id,b);for(var b:e.flood())box("W",id,b);box("E",id,e.exit());
 for(var q:GinsoEscapeRules.samples(e))System.out.println("S "+id+" "+q.index()+" "+q.feet()+" "+q.referenceArrival()+" "+q.waterAtFeet()+" "+q.waterAtSpiritEyes()+" "+q.waterAtHumanEyes()+" "+q.feetMargin());
 }}}}''' }
with tempfile.TemporaryDirectory(prefix='ori-ginso-escape-') as folder:
    folder=Path(folder)
    for name,text in stubs.items(): (folder/name).write_text(text)
    subprocess.run([str(java/'javac'),'-d',str(folder),*[str(p) for p in inputs],*[str(folder/n) for n in stubs]],check=True)
    seeds=list(range(64))+[115117,115118]
    result=subprocess.run([str(java/'java'),'-cp',str(folder),'Export',*map(str,seeds)],check=False,text=True,capture_output=True)
    if result.returncode:
        print(result.stderr,flush=True);result.check_returncode()
layouts={}
keys={'C':'cells','R':'path','A':'route_union','W':'flood_union','S':'samples'}
for line in result.stdout.splitlines():
    k,id,*numbers=line.split();seed,turn=map(int,id.split(':'))
    e=layouts.setdefault(id,{'seed':seed,'turn':turn,'cells':[],'path':[],'route_union':[],'flood_union':[],'samples':[]})
    values=[int(n) if k=='C' else float(n) for n in numbers]
    if k in keys:e[keys[k]].append(values)
    elif k=='M':e['native_wood']=dict(zip(('clearance_removed','trimmed','maximum_trim_component','braced_blocks','maximum_severed_component'),map(int,values)))
    elif k=='E':e['exit']=values
    elif k=='P':
        e['height'],e['cleft_index'],e['branch_index']=map(int,values[:3]);e['plan']=dict(zip(('start_water','limit_ticks','reference_seconds','idle_spirit_seconds','idle_human_seconds'),values[3:]))
for e in layouts.values():e['cells'].sort()
def data(v):return (json.dumps(v,ensure_ascii=False,separators=(',',':'))+'\n').encode()
outputs={OUT/f'seed-{e["seed"]}-turn-{e["turn"]}.json':data(e) for e in layouts.values() if e['seed'] in (0,59,115117,115118)}
def geometry_digest(e):
    lines=[' '.join(map(str,p)) for p in e['cells']]
    lines += [f'R {int(p[0])} {round(p[1]*2)} {int(p[2])} {int(p[4])}' for p in e['path']]
    return hashlib.sha256(('\n'.join(lines)+'\n').encode()).hexdigest()
# A tapered wet-bark lip replaces the escape-only fence rails. Existing course
# rails and their saved states are untouched. Collision is an explicit 1.5 blocks.
def root_model():
    elements=[]
    for low,high in [([0,0,0],[16,8,16]),([1,8,1],[15,16,15]),([3,16,2],[13,24,14])]:
        elements.append({'from':low,'to':high,'faces':{face:{'texture':'#bark'} for face in ('up','down','north','south','east','west')}})
    return {'textures':{'bark':'orimod:block/ginso_bark_moss','particle':'orimod:block/ginso_bark_moss'},'elements':elements}
root_resources={
 'assets/orimod/models/block/ginso_root_lip.json':root_model(),
 'assets/orimod/models/item/ginso_root_lip.json':{'parent':'orimod:block/ginso_root_lip'},
 'assets/orimod/blockstates/ginso_root_lip.json':{'variants':{'':{'model':'orimod:block/ginso_root_lip'}}}}
for name,value in root_resources.items():
    content=(json.dumps(value,indent=2)+'\n').encode()
    outputs[ROOT/'src/main/resources'/name]=content
    outputs[OUT/Path(name).parent.name/Path(name).name]=content
manifest={'version':3,'shape_version':3,'course_version':2,'escape_version':1,'generator':'scripts/art/build_ginso_escape.py',
 'phase':'geometry only; no interaction or water runtime',
 'recipe':'Compact root helix inside the fork arms; curved asymmetric branch saddle beside the original tips. Native headroom cuts only, zero component deletion, supporting roots reconnect severed wood. Tapered wet-bark lips with 1.5-block collision; no glass or roof.',
 'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs},
 'all_geometry_sha256':{id:geometry_digest(e) for id,e in layouts.items()},
 'layout_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(b).hexdigest() for p,b in outputs.items()}}
outputs[OUT/'manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
for path,content in outputs.items():
    # v003's frozen manifest still documents its original block-model bytes.
    # Packaged root lips are now owned by build_ginso_root_lip.py (v004).
    if path.is_relative_to(ROOT/'src/main/resources'):continue
    if args.check:assert path.exists() and path.read_bytes()==content,f'Mismatch: {path}'
    else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
print(f'Ginso escape v3: {len(layouts)} sampled layouts; written={0 if args.check else len(outputs)}')
summary=[]
for id,e in layouts.items():
    s=e['samples'];n=len(s)
    summary.append({'seed':e['seed'],'turn':e['turn'],'height':e['height'],'cleft_index':e['cleft_index'],'branch_index':e['branch_index'],'distance':e['path'][-1][3],**e['native_wood'],**e['plan'],
      'minimum_margin':min(p[-1] for p in s),'front_max_eye_margin':max(p[5]-p[2] for p in s[:n//3+1]),
      'late_max_eye_margin':max(p[5]-p[2] for p in s[n*2//3:]),'exit_margin':s[-1][-1]})
print('ranges', {k:[round(min(e[k] for e in summary),3),round(max(e[k] for e in summary),3)] for k in ('distance','minimum_margin','idle_human_seconds','front_max_eye_margin','late_max_eye_margin')})
if args.report:
    out=args.report;out.mkdir(parents=True,exist_ok=True);(out/'layout-cold.txt').write_text(result.stderr)
    (out/'seed-curves.json').write_text(json.dumps(summary,indent=2))
    ranked=sorted(summary,key=lambda r:r['late_max_eye_margin'])
    for label,row in zip(('best','median','worst'),(ranked[0],ranked[len(ranked)//2],ranked[-1])):
        e=layouts[f'{row["seed"]}:{row["turn"]}'];name=f'{label}-seed-{e["seed"]}-turn-{e["turn"]}'
        with (out/f'chase-{name}.csv').open('w') as f:
            w=csv.writer(f);w.writerow(('index','feet_y','reference_arrival_seconds','water_feet_seconds','water_spirit_eyes_seconds','water_human_eyes_seconds','feet_margin_seconds'));w.writerows(e['samples'])
        cells={(x,y,z):b for x,y,z,b in e['cells']};marks={1:'#',4:' ',5:'*',14:'=',15:'|',16:'D',17:'D',19:'-',20:'|'}
        lines=[name+'; # bark, = full tread, - half tread, | real root fence, * sap, D manual door, @ route', 'XZ top (x/z=-30..30)']
        for z in range(-30,31):
            row=''
            for x in range(-30,31):
                glyph=' '
                for y in range(93,125):
                    b=cells.get((x,y,z),0)
                    if b and b!=4:glyph=marks.get(b,'?')
                if any(p[0]==x and p[2]==z for p in e['path']):glyph='@'
                row+=glyph
            lines.append(f'{z:3} '+row)
        for title,axis,fixed in [('XY z=0','x',0),('ZY x=0','z',0)]:
            lines.append(title)
            for y in range(124,91,-1):
                row=''
                for a in range(-30,31):
                    x,z=(a,fixed) if axis=='x' else (fixed,a);glyph=marks.get(cells.get((x,y,z),0),' ')
                    if any(p[0]==x and p[2]==z and int(p[1])==y for p in e['path']):glyph='@'
                    row+=glyph
                lines.append(f'{y:3} '+row)
        (out/f'route-{name}.txt').write_text('\n'.join(lines)+'\n')
