"""Reopen a saved Blender revision and export to a new directory, without saving the source."""
import bpy
import hashlib
import json
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(Path(__file__).parent))
from character_export import export_character

args=sys.argv[sys.argv.index('--')+1:]
manifest=json.loads((ROOT/args[0]).read_text(encoding='utf-8'))
source=ROOT/manifest['blend']
assert hashlib.sha256(source.read_bytes()).hexdigest()==manifest['blend_sha256']
output=ROOT/args[1]
output.mkdir(parents=True,exist_ok=False)
bpy.ops.wm.open_mainfile(filepath=str(source))
for name in ('ori_base','ori_emission','ori_surface'):
    image=bpy.data.images[name]
    image.filepath_raw=str(output/(name+'.png'))
    image.save()
rig=bpy.data.objects['Ori_Rig']
mesh=bpy.data.objects['Ori_Body']
export,resources=export_character(bpy.context.scene,rig,mesh,output)
digest=hashlib.sha256(export.read_bytes()).hexdigest()
report={'source':str(source),'original_export':manifest['export_sha256'],'reopened_export':digest,
        'same_glb':digest==manifest['export_sha256'],
        'same_textures':all(resources[k]['sha256']==v['sha256'] for k,v in manifest['textures'].items()),
        'source_unchanged':hashlib.sha256(source.read_bytes()).hexdigest()==manifest['blend_sha256']}
(output/'results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
assert report['same_glb'] and report['same_textures'] and report['source_unchanged'],report
print(json.dumps(report))
