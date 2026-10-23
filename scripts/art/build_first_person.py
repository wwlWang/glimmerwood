"""Derive editable, separate forearm/paw meshes from the preserved character rig."""
import bpy
import bmesh
import hashlib
import json
import shutil
import struct
from pathlib import Path
from mathutils import Matrix, Vector

ROOT=Path(__file__).resolve().parents[2]
source_manifest=json.loads((ROOT/'art/source/character/manifest.json').read_text(encoding='utf-8'))
source=ROOT/source_manifest['blend']
assert hashlib.sha256(source.read_bytes()).hexdigest()==source_manifest['blend_sha256']
base=ROOT/'art/source/first-person'
# Continue after the highest existing revision (v001.. may have been pruned).
revision=max([int(p.name[1:]) for p in base.glob('v[0-9][0-9][0-9]')]+[0])+1
output=base/f'v{revision:03}'
output.mkdir(parents=True)
bpy.ops.wm.open_mainfile(filepath=str(source))
rig=bpy.data.objects['Ori_Rig']
body=bpy.data.objects['Ori_Body']
scene=bpy.context.scene
meshes=[]
entries={}
# GLB converts Blender Z-up into Y-up; write that inverse here so the exported
# hand has vanilla arm axes: +Y toward fingers and +Z along the back of the hand.
to_blender=Matrix(((1,0,0,0),(0,0,-1,0),(0,1,0,0),(0,0,0,1)))
for side,coordinate in [('right','l'),('left','r')]:
    sign=-1 if coordinate=='l' else 1
    groups={g.index for g in body.vertex_groups if g.name in {'forearm_'+coordinate,'hand_'+coordinate}
            or g.name.startswith('finger_'+coordinate+'_') or g.name.startswith('thumb_'+coordinate+'_')}
    selected={v.index for v in body.data.vertices if (body.matrix_world@v.co).x*sign>.265
              and sum(g.weight for g in v.groups if g.group in groups)>.05}
    for pose in ('open','grip'):
        rig.animation_data.action=bpy.data.actions['idle' if pose=='open' else 'hold' if side=='right' else 'hold_left']
        scene.frame_set(0)
        bpy.context.view_layer.update()
        evaluated=body.evaluated_get(bpy.context.evaluated_depsgraph_get())
        data=bpy.data.meshes.new_from_object(evaluated,preserve_all_data_layers=True,depsgraph=bpy.context.evaluated_depsgraph_get())
        bm=bmesh.new();bm.from_mesh(data)
        bmesh.ops.delete(bm,geom=[v for v in bm.verts if v.index not in selected],context='VERTS')
        bm.to_mesh(data);bm.free();data.update()
        data.normals_split_custom_set([(0,0,0)]*len(data.loops))
        for face in data.polygons: face.use_smooth=True
        point,rotation,_=(rig.matrix_world@rig.pose.bones['grip_'+coordinate].matrix).decompose()
        palm=(Matrix.Translation(point)@rotation.to_matrix().to_4x4()).inverted()
        transform=to_blender@palm@body.matrix_world
        data.transform(transform)
        extent=max(v.co.length for v in data.vertices)
        assert .1<extent<.4,(side,pose,extent)
        # A straight wrist cut and a tapered forearm continue below the camera.
        # Removing source vertices alone leaves a serrated open edge in view.
        bm=bmesh.new();bm.from_mesh(data)
        bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),dist=1e-6,
            plane_co=(0,0,-.09),plane_no=(0,0,1),clear_inner=True,clear_outer=False)
        bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-6)
        boundary=[e for e in bm.edges if e.is_boundary]
        assert boundary
        assert all(abs(v.co.z+.09)<1e-5 for e in boundary for v in e.verts)
        ring={v for e in boundary for v in e.verts}
        connected={next(iter(ring))}
        pending=list(connected)
        while pending:
            for edge in pending.pop().link_edges:
                if not edge.is_boundary: continue
                for vertex in edge.verts:
                    if vertex not in connected: connected.add(vertex);pending.append(vertex)
        assert connected==ring,'Wrist cut must have one ring and must not cut folded fingers'
        center=sum((v.co for v in ring),Vector())/len(ring)
        old_faces=set(bm.faces)
        skin_face=boundary[0].link_faces[0]
        skin_uv={layer:sum((loop[layer].uv for loop in skin_face.loops),Vector((0,0)))/len(skin_face.loops)
                 for layer in bm.loops.layers.uv.values()}
        # Several rings allow the held forearm to bend back below the camera
        # while its wrist follows vanilla eating, shielding and bow motion.
        previous_width=1
        for segment in range(1,6):
            t=segment/5
            width=1+.35*t
            extension=bmesh.ops.extrude_edge_only(bm,edges=boundary)['geom']
            for v in extension:
                if isinstance(v,bmesh.types.BMVert):
                    v.co.x=center.x+(v.co.x-center.x)*width/previous_width
                    v.co.y=center.y+(v.co.y-center.y)*width/previous_width
                    v.co.z=-.09-.56*t
            boundary=[e for e in bm.edges if e.is_boundary]
            previous_width=width
        cap=[e for e in bm.edges if e.is_boundary and all(abs(v.co.z+.65)<1e-5 for v in e.verts)]
        bmesh.ops.holes_fill(bm,edges=cap,sides=0)
        for face in bm.faces:
            if face in old_faces: continue
            for loop in face.loops:
                for layer,uv_coordinate in skin_uv.items(): loop[layer].uv=uv_coordinate
        assert all(e.is_manifold for e in bm.edges),'Forearm must be a closed surface'
        bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
        bm.to_mesh(data);bm.free();data.update()
        data.normals_split_custom_set([(0,0,0)]*len(data.loops))
        for face in data.polygons: face.use_smooth=True
        data.calc_loop_triangles()
        obj=bpy.data.objects.new('paw_'+side+'_'+pose,data)
        scene.collection.objects.link(obj)
        meshes.append(obj)
        entries[obj.name]={'triangles':len(data.loop_triangles),'vertices':len(data.vertices)}
for obj in list(scene.objects):
    if obj not in meshes: bpy.data.objects.remove(obj,do_unlink=True)
bpy.ops.wm.save_as_mainfile(filepath=str(output/'paws.blend'),compress=True)
for obj in meshes:
    bpy.ops.object.select_all(action='DESELECT');obj.select_set(True)
    bpy.context.view_layer.objects.active=obj
    path=output/(obj.name+'.glb')
    bpy.ops.export_scene.gltf(filepath=str(path),export_format='GLB',use_selection=True,export_animations=False,
        export_copyright='Ori Rigged by Random modeler / Seea_Rooster, CC BY 4.0. OriMod: derived forearm/paw geometry and materials.')
    raw=path.read_bytes();size=struct.unpack_from('<I',raw,12)[0];gltf=json.loads(raw[20:20+size])
    for mesh in gltf['meshes']:
        for primitive in mesh['primitives']:
            attr=primitive['attributes'];attr['TEXCOORD_0']=attr.pop('TEXCOORD_1')
            for k in list(attr):
                if k.startswith('COLOR_'): del attr[k]
    def uv(value):
        if isinstance(value,dict):
            if 'texCoord' in value: value['texCoord']=0
            for child in value.values(): uv(child)
        elif isinstance(value,list):
            for child in value: uv(child)
    uv(gltf['materials'])
    for image in gltf['images']:
        image.setdefault('extras',{})['resourceLocation']='orimod:textures/entity/'+image['name']+'.png'
    encoded=json.dumps(gltf,separators=(',',':'),ensure_ascii=False).encode();encoded+=b' '*(-len(encoded)%4)
    rest=raw[20+size:]
    path.write_bytes(struct.pack('<III',0x46546C67,2,20+len(encoded)+len(rest))+struct.pack('<II',len(encoded),0x4E4F534A)+encoded+rest)
    target=ROOT/'src/main/resources/assets/orimod/models/entity'/(obj.name+'.glb')
    shutil.copy2(path,target)
    entries[obj.name].update(source=str(path.relative_to(ROOT).as_posix()),packaged=str(target.relative_to(ROOT).as_posix()),sha256=hashlib.sha256(path.read_bytes()).hexdigest())
manifest={'status':'candidate; first-person runtime review pending','revision':revision,'derived_from':source_manifest['blend'],
    'derived_from_sha256':source_manifest['blend_sha256'],'blend':str((output/'paws.blend').relative_to(ROOT).as_posix()),
    'blend_sha256':hashlib.sha256((output/'paws.blend').read_bytes()).hexdigest(),'models':entries}
(base/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
assert hashlib.sha256(source.read_bytes()).hexdigest()==source_manifest['blend_sha256']
print(json.dumps(manifest))
