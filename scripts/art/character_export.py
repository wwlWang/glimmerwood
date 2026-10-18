"""One GLB export contract for freshly built and reopened Blender characters."""
import bpy
import hashlib
import json
import struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def export_character(scene, rig, mesh, output, filename='ori.glb'):
    bpy.ops.object.select_all(action='DESELECT')
    for obj in scene.objects:
        if obj.type in {'ARMATURE','EMPTY'} or obj==mesh:
            obj.select_set(True)
    bpy.context.view_layer.objects.active=rig
    export=output/filename
    bpy.ops.export_scene.gltf(filepath=str(export),export_format='GLB',use_selection=True,use_active_scene=True,
                              export_animations=True,export_animation_mode='ACTIONS',export_force_sampling=True,
                              export_frame_range=False,export_extras=True,
                              export_copyright='Ori Rigged by Random modeler / Seea_Rooster, CC BY 4.0. OriMod: welded topology, runtime rig, materials and animations.')
    data=export.read_bytes()
    size=struct.unpack_from('<I',data,12)[0]
    gltf=json.loads(data[20:20+size])
    # GemRender intentionally carries a single UV set. The Blender source retains
    # its original UVs and editable colour layers; the runtime carries the bake only.
    for gltf_mesh in gltf['meshes']:
        for primitive in gltf_mesh['primitives']:
            attributes=primitive['attributes']
            attributes['TEXCOORD_0']=attributes.pop('TEXCOORD_1')
            for key in list(attributes):
                if key.startswith('COLOR_'):
                    del attributes[key]
    def texture_coordinates(value):
        if isinstance(value,dict):
            if 'texCoord' in value:
                assert value['texCoord']==1
                value['texCoord']=0
            for child in value.values():
                texture_coordinates(child)
        elif isinstance(value,list):
            for child in value:
                texture_coordinates(child)
    texture_coordinates(gltf['materials'])
    resources={}
    for image in gltf['images']:
        name=image['name']
        assert name in {'ori_base','ori_emission','ori_surface'}, name
        resource='orimod:textures/entity/'+name+'.png'
        image.setdefault('extras',{})['resourceLocation']=resource
        resources[resource]={'path':str((output/(name+'.png')).relative_to(ROOT).as_posix()),
                             'sha256':hashlib.sha256((output/(name+'.png')).read_bytes()).hexdigest()}
    encoded=json.dumps(gltf,separators=(',',':'),ensure_ascii=False).encode('utf-8')
    encoded+=b' '*(-len(encoded)%4)
    remaining=data[20+size:]
    export.write_bytes(struct.pack('<III',0x46546C67,2,20+len(encoded)+len(remaining))+struct.pack('<II',len(encoded),0x4E4F534A)+encoded+remaining)
    return export,resources
