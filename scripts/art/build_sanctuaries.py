"""R115 original sanctuary architecture. Blender 5: source -> GLB/PBR pixel atlas -> manifest.

Run with --python ... -- --version v001. Coordinates here are Blender Z-up; glTF is Y-up.
Stone has world-scale pixel grain; water/runes emit, and detached cores export at their own pivot.
No downloaded meshes, textures or changes to the ruin templates.
"""
import argparse
import hashlib
import json
import math
import shutil
import struct
import sys
from pathlib import Path

import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ap = argparse.ArgumentParser()
ap.add_argument('--version', default='v002')
args = ap.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
OUT = ROOT / 'art/source/sanctuaries' / args.version
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = ROOT / 'src/main/resources/assets/orimod'
PARTS = []
# stone, shadow, edge, roots/trim, water, rune, gold, crystal. Linear colours, separately authored emission.
PALETTES = {
    'well': [( .18,.26,.28),(.055,.08,.09),(.38,.46,.43),(.13,.22,.16),(.045,.32,.37),(.34,.75,.85),(.65,.48,.21),(.14,.36,.42)],
    'forest':[(.22,.26,.20),(.075,.09,.065),(.36,.40,.30),(.15,.21,.10),(.035,.27,.28),(.28,.68,.51),(.54,.39,.15),(.10,.29,.23)],
    'creek':[(.14,.24,.31),(.045,.08,.13),(.30,.44,.51),(.12,.24,.25),(.035,.25,.43),(.3,.65,.9),(.49,.47,.27),(.08,.24,.43)],
    'cave':[(.095,.10,.16),(.025,.025,.045),(.21,.24,.32),(.16,.10,.25),(.10,.16,.35),(.48,.42,.85),(.48,.35,.19),(.20,.09,.37)],
}

def add(o, colour, bevel=0):
    if bevel:
        mod=o.modifiers.new('worn edges','BEVEL'); mod.width=bevel;mod.segments=2
        bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=mod.name)
    o['palette']=colour;PARTS.append(o);return o

def box(name, p, scale, c=0, bevel=.045):
    bpy.ops.mesh.primitive_cube_add(size=1,location=p);o=bpy.context.object;o.name=name;o.scale=scale
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return add(o,c,bevel)

def disc(name, p, radius, depth, c=0, vertices=16):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices,radius=radius,depth=depth,location=p)
    o=bpy.context.object;o.name=name;return add(o,c,.025 if depth>.08 else 0)

def tube(name, points, radius, c, sides=8):
    verts=[];faces=[]
    for i,p in enumerate(points):
        tangent=Vector(points[min(i+1,len(points)-1)])-Vector(points[max(0,i-1)])
        tangent.normalize(); ref=Vector((0,0,1)) if abs(tangent.z)<.95 else Vector((1,0,0))
        u=tangent.cross(ref).normalized();v=tangent.cross(u)
        r=radius[i] if isinstance(radius,list) else radius
        for n in range(sides):
            a=n*math.tau/sides;verts.append(Vector(p)+r*(u*math.cos(a)+v*math.sin(a)))
    for i in range(len(points)-1):
        for n in range(sides):
            a=i*sides+n;b=i*sides+(n+1)%sides;faces.append((a,b,b+sides,a+sides))
    faces.extend([tuple(reversed(range(sides))),tuple(range((len(points)-1)*sides,len(points)*sides))])
    m=bpy.data.meshes.new(name);m.from_pydata(verts,[],faces);m.update()
    o=bpy.data.objects.new(name,m);bpy.context.collection.objects.link(o);return add(o,c)

def ring(name,z,radius,width,c=2):
    return tube(name,[(radius*math.cos(a*math.tau/48),radius*math.sin(a*math.tau/48),z) for a in range(49)],width,c)

def basin(theme):
    # The central one-block pedestal corresponds to the old collision. The skirt is only 0.18 high.
    disc('low octagonal apron',(0,0,.09),1.42,.18,0,12)
    box('original core',(0,0,.44),(.99,.99,.88),0,.07)
    disc('shallow bowl',(0,0,.79),1.05,.28,0,20)
    ring('carved rim',.96,1.01,.105,2)
    disc('dark recessed basin',(0,0,.946),.91,.022,1,48)
    disc('luminous water',(0,0,.968),.87,.014,4,48)
    ring('water ripple',.983,.64,.013,5);ring('water ripple inner',.984,.29,.011,5)
    # Open front (-Y in Blender): curved roots rise behind the basin, leaving the approach legible.
    for sign in [-1,1]:
        points=[(sign*1.24,.35,.15),(sign*1.14,.55,.65),(sign*.98,.68,1.45),
                (sign*.72,.70,2.18),(sign*.38,.68,2.60),(sign*.13,.66,2.74)]
        tube('curved root arch',points,[.27,.24,.20,.16,.115,.06],3 if theme in ['well','forest'] else 0)
        tube('arch vein',[(x,y-r,z+.025) for (x,y,z),r in zip(points[1:],[.24,.20,.16,.115,.06])],.021,5)
        for k in range(3):
            box('rim runic inlay',(sign*(.3+.24*k),-.82,.98),(.1,.035,.02),5,0)
    for n in range(9):
        a=n*math.tau/9;box('apron chisels',(1.28*math.cos(a),1.28*math.sin(a),.19),(.12,.09,.025),2,.01)

def altar(storm):
    disc('lower step',(0,0,.11),1.24,.22,0,8)
    disc('second step',(0,0,.32),.98,.20,2,8)
    disc('central plinth',(0,0,.66),.72,.48,0,8)
    disc('offering face',(0,0,.95),.79,.14,2,12)
    ring('inlaid circuit',1.025,.60,.022,5)
    for n in range(4):
        a=n*math.pi/2+math.pi/4;x,y=math.cos(a),math.sin(a)
        tube('swept prong',[(x*.66,y*.66,.70),(x*.86,y*.86,1.12),(x*.67,y*.67,1.53)], [.12,.10,.035],3)
        tube('plinth channel',[(x*.71,y*.71,.42),(x*.65,y*.65,.82),(x*.53,y*.53,1.03)],.018,5)

def crystal():
    for n,(x,y,z,s) in enumerate([(0,0,0,1),(-.30,.10,-.10,.5),(.26,-.04,-.19,.42)]):
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=1,location=(x,y,z));o=bpy.context.object
        o.name='cracked floating crystal';o.scale=(.30*s,.25*s,.67*s);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);add(o,7)
    tube('bright branching fracture',[(-.12,-.217,-.37),(.035,-.247,-.07),(-.06,-.239,.16),(.09,-.17,.43)],.024,5)
    tube('forked fracture',[(.035,-.247,-.07),(.22,-.12,.06)],.019,5)

def feather_ring():
    # A tilted wreath, its separated feathers make the rotation readable.
    ring('thin feather halo',0,.55,.035,6)
    for n in range(8):
        a=n*math.tau/8
        points=[(.53*math.cos(a),.53*math.sin(a),0),(.74*math.cos(a+.22),.74*math.sin(a+.22),.08),
                (.85*math.cos(a+.40),.85*math.sin(a+.40),.02)]
        start,mid,end=map(Vector,points)
        tangent=(end-start).normalized();side=Vector((-tangent.y,tangent.x,0))
        verts=[]
        for i in range(9):
            t=i/8;centre=(1-t)**2*start+2*(1-t)*t*mid+t*t*end
            width=.12*math.sin(math.pi*t)**.75*(.78 if i in (3,6) else 1)
            verts.extend([centre-side*width,centre+side*width])
        faces=[(i*2,i*2+1,i*2+3,i*2+2) for i in range(8)]
        me=bpy.data.meshes.new('feather vane');me.from_pydata(verts,[],faces);me.update()
        o=bpy.data.objects.new('curved feather vane',me);bpy.context.collection.objects.link(o);add(o,5)
        tube('feather quill',[(p.x,p.y,p.z+.012) for p in (start,mid,end)],.009,6,6)
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2,radius=.17);add(bpy.context.object,5)

def pillar():
    disc('pillar foot',(0,0,.11),.61,.22,0,8)
    box('carved shaft',(0,0,.85),(.59,.59,1.48),0,.065)
    disc('capital',(0,0,1.64),.43,.16,2,8)
    for face in range(4):
        a=face*math.pi/2
        for j in range(4):
            z=.38+j*.28
            pts=[(-.13,-.302,z),(.02,-.307,z+.11),(.15,-.302,z+.03)]
            tube('etched route',[(x*math.cos(a)-y*math.sin(a),x*math.sin(a)+y*math.cos(a),h) for x,y,h in pts],.018,5)

def map_disc():
    disc('floating chart',(0,0,0),.69,.07,1,24)
    ring('chart rim',.05,.67,.022,5)
    for r in [.24,.44]:ring('contour',.046,r,.01,5)
    for a in [0,2.2,4.5]:
        tube('charted rivers',[(r*math.cos(a+r),r*math.sin(a+r),.052) for r in [.1,.3,.5,.62]],.016,5)
    for x,y in [(.30,.22),(-.24,.39),(-.36,-.19)]:
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=.055,location=(x,y,.10));add(bpy.context.object,6)

def door():
    # Unit door, up Z, facing -Y. Runtime fits its silhouette inside the original door cells.
    for n in range(16):
        a=(n+.5)*math.tau/16
        o=box('voussoir',(math.sin(a)*.44,0,.5+math.cos(a)*.44),(.19,.32,.13),0,.013)
        o.rotation_euler.y=a
    # Paired leaves: curved carved panels, little gap down the middle. No full cube covering the ring.
    for side in [-1,1]:
        points=[(side*(.008+.383*math.sin(i*math.pi/16)),-.018,.5+.39*math.cos(i*math.pi/16)) for i in range(17)]
        n=len(points);verts=points+[(x,y+.15,z) for x,y,z in points]
        faces=[tuple(range(n)),tuple(reversed(range(n,2*n)))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
        mesh=bpy.data.meshes.new('leaf');mesh.from_pydata(verts,[],faces);mesh.update()
        o=bpy.data.objects.new('sealed leaf',mesh);bpy.context.collection.objects.link(o);add(o,3,.01)
        for face_y in [-.029,.143]:
            tube('engraved branch',[(side*.04,face_y,.23),(side*.18,face_y,.40),(side*.09,face_y,.55),(side*.04,face_y,.76)],.009,5,5)
    for side in [-1,1]:
        for face_y in [-.08,.195]:
            box('keystone socket',(side*.15,face_y,.5),(.13,.05,.18),1,.014)

def key():
    o=box('inset keystone',(0,0,0),(.085,.045,.125),5,.014);o.rotation_euler.y=.17

# Eight 64x64 material tiles. Geometry projects in metres, one texel = 1/16 block.
# No noise in the emissive material tiles (water/runes/crystal retain their original colours).
TILE = 64
TEXELS = 16

def noise(x, y, salt=0):
    n=(x*374761393+y*668265263+salt*1442695041)&0xffffffff
    n=((n^(n>>13))*1274126177)&0xffffffff
    return (n^(n>>16))/0xffffffff

def stone_pixel(theme, c, x, y):
    base=PALETTES[theme][c]
    coarse=noise(x//3,y//3,19+c)
    grain=noise(x,y,41+c)
    factor=.79+.30*coarse+.18*grain
    # Chisel-cut joints are irregular and broken, rather than a perfectly tiled brick grid.
    row=y//16; joint=(x+(row%2)*9)%24
    groove=(y%16==0 and noise(x//5,row,7)>.22) or (joint==0 and y%16<12)
    crack=(x+2*(y//4)+int(noise(y//8,0,15)*7))%37==0 and noise(x//2,y//3,8)>.35
    if groove or crack:factor*=.53
    elif y%16==1 or joint==1:factor*=1.12
    rgb=tuple(min(1,v*factor) for v in base)
    # Sparse 2-3px moss patches on forest stone; never on emissive parts.
    if theme=='forest' and coarse>.72 and noise(x//2,y//2,97)>.47:
        rgb=tuple(a*.42+b*.58 for a,b in zip(rgb,(.17,.27,.055)))
    return rgb

def assign_uv(o, name):
    for old in list(o.data.uv_layers):o.data.uv_layers.remove(old)
    uv=o.data.uv_layers.new(name='stone_pixel_atlas')
    c=o['palette'];density=32 if name=='keystone_gate' else TEXELS
    for face in o.data.polygons:
        normal=o.matrix_world.to_3x3()@face.normal
        axis=max(range(3),key=lambda i:abs(normal[i]))
        for li in face.loop_indices:
            if c>=4:  # unmodified uniform water, glyph, metal and core colour
                u=v=TILE/2
            else:
                p=o.matrix_world@o.data.vertices[o.data.loops[li].vertex_index].co
                a,b=(p.x,p.y) if axis==2 else ((p.x,p.z) if axis==1 else (p.y,p.z))
                # Give each material an entire four-metre tile; all surfaces remain inside it.
                shift_a,shift_b=(1,.5 if axis!=2 else 1) if name=='keystone_gate' else (2,2 if axis==2 else .5)
                u=(a+shift_a)*density;v=(b+shift_b)*density
                assert .5<=u<TILE-.5 and .5<=v<TILE-.5,(name,o.name,u,v)
            uv.data[li].uv=((c*TILE+u)/(8*TILE),v/TILE)

def export(name,theme,build):
    bpy.ops.wm.read_factory_settings(use_empty=True);PARTS.clear();build()
    # World-scale box projection gives stone surfaces Minecraft-sized nearest-filtered pixels.
    material=bpy.data.materials.new(name);material.use_nodes=True
    palette=PALETTES[theme];images={}
    for suffix in ['base','emission','surface']:
        img=bpy.data.images.new(name+'_'+suffix,TILE*8,TILE,alpha=True)
        if suffix=='surface':img.colorspace_settings.name='Non-Color'
        pixels=[]
        for y in range(TILE):
            for x in range(TILE*8):
                c=x//TILE;colour=stone_pixel(theme,c,x%TILE,y) if suffix=='base' and c<4 else palette[c]
                if suffix=='emission':colour=tuple(v*(.45 if c==4 else .8) for v in colour) if c in (4,5) else (0,0,0)
                if suffix=='surface':colour=(1,.88 if c<4 else .3,.03)
                pixels.extend((*colour,1))
        img.pixels=pixels;img.filepath_raw=str(OUT/(name+'_'+suffix+'.png'));img.file_format='PNG';img.save();img.pack();images[suffix]=img
    nodes=material.node_tree.nodes;nodes.clear();links=material.node_tree.links
    bs=nodes.new('ShaderNodeBsdfPrincipled');out=nodes.new('ShaderNodeOutputMaterial');links.new(bs.outputs[0],out.inputs['Surface'])
    for suffix,input_name in [('base','Base Color'),('emission','Emission Color')]:
        node=nodes.new('ShaderNodeTexImage');node.image=images[suffix];node.interpolation='Closest';links.new(node.outputs['Color'],bs.inputs[input_name])
    bs.inputs['Emission Strength'].default_value=1;bs.inputs['Roughness'].default_value=.8
    surface=nodes.new('ShaderNodeTexImage');surface.image=images['surface'];surface.interpolation='Closest';sep=nodes.new('ShaderNodeSeparateColor')
    links.new(surface.outputs['Color'],sep.inputs['Color']);links.new(sep.outputs['Green'],bs.inputs['Roughness']);links.new(sep.outputs['Blue'],bs.inputs['Metallic'])
    for o in PARTS:
        o.data.materials.clear();o.data.materials.append(material)
        assign_uv(o,name)
    bpy.ops.object.select_all(action='DESELECT')
    for o in PARTS:o.select_set(True)
    bpy.context.view_layer.objects.active=PARTS[0];bpy.ops.object.join();body=bpy.context.object;body.name=name
    bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
    # Restore a zero node transform: runtime can directly draw the static geometry.
    bpy.context.scene.cursor.location=(0,0,0);bpy.ops.object.origin_set(type='ORIGIN_CURSOR')
    glb=OUT/(name+'.glb')
    bpy.ops.export_scene.gltf(filepath=str(glb),export_format='GLB',use_selection=True,export_extras=True,export_copyright='OriMod original procedural architecture.')
    data=glb.read_bytes();size=struct.unpack_from('<I',data,12)[0];gltf=json.loads(data[20:20+size])
    for sampler in gltf.get('samplers',[]):sampler.update(magFilter=9728,minFilter=9728)
    for image in gltf['images']:image.setdefault('extras',{})['resourceLocation']='orimod:textures/sanctuary/'+image['name']+'.png'
    enc=json.dumps(gltf,separators=(',',':')).encode();enc+=b' '*(-len(enc)%4);rest=data[20+size:]
    glb.write_bytes(struct.pack('<III',0x46546c67,2,20+len(enc)+len(rest))+struct.pack('<II',len(enc),0x4e4f534a)+enc+rest)
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/(name+'.blend')),compress=True)
    files={}
    for source in [glb]+[OUT/(name+'_'+s+'.png') for s in ['base','emission','surface']]:
        dest=PACKAGE/('models/sanctuary' if source.suffix=='.glb' else 'textures/sanctuary')/source.name
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
        files[source.name]={'package':str(dest.relative_to(ROOT)),'sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
    return {'name':name,'theme':theme,'texture_size':[TILE*8,TILE],'texels_per_block':TEXELS,'door_reference_size':2 if name=='keystone_gate' else None,'vertices':len(body.data.vertices),'bounds_blender':[[min(v.co[i] for v in body.data.vertices) for i in range(3)],[max(v.co[i] for v in body.data.vertices) for i in range(3)]],'files':files}

assets=[]
for theme in PALETTES:assets.append(export('basin_'+theme,theme,lambda t=theme:basin(t)))
assets.append(export('altar_storm','creek',lambda:altar(True)))
assets.append(export('altar_gloom','cave',lambda:altar(False)))
assets.append(export('storm_ring','creek',feather_ring))
assets.append(export('gloom_crystal','cave',crystal))
assets.append(export('map_pillar','well',pillar))
assets.append(export('map_disc','well',map_disc))
assets.append(export('keystone_gate','creek',door))
assets.append(export('keystone_inset','creek',key))
(OUT/'manifest.json').write_text(json.dumps({'version':args.version,'generator':'scripts/art/build_sanctuaries.py','coordinates':'metres, glTF Y up; bases at 0; floating components at pivot','assets':assets},indent=2)+'\n')
print('Sanctuaries:',len(assets),'assets ->',OUT)
