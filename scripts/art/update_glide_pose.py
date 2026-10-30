"""Rebake only R117's three glide clips from the existing editable character.

Run with Blender --background --python scripts/art/update_glide_pose.py.
Writes a new version; promote_character.py handles the explicit packaging step.
"""
import bpy
import copy
import hashlib
import json
import shutil
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(Path(__file__).parent))
import character_animations, character_export
source=json.loads((ROOT/'art/source/character/manifest.json').read_text())
root=ROOT/'art/source/character'
revision=1+max(int(p.name[1:]) for p in root.glob('v[0-9][0-9][0-9]'))
out=root/f'v{revision:03}';out.mkdir()
bpy.ops.wm.open_mainfile(filepath=str(ROOT/source['blend']))
rig=bpy.data.objects['Ori_Rig'];mesh=bpy.data.objects['Ori_Body']
rig.animation_data.action=None
for bone in rig.pose.bones:bone.matrix_basis.identity()
bpy.context.view_layer.update()
names={'glide','glide_open','glide_close'}
for action in list(bpy.data.actions):
    if action.name in names:bpy.data.actions.remove(action,do_unlink=True)
metadata,actions=character_animations.author(rig,mesh,only=names)
for entry in source['textures'].values():shutil.copy2(ROOT/entry['path'],out/Path(entry['path']).name)
bpy.ops.wm.save_as_mainfile(filepath=str(out/'ori.blend'),compress=True)
export,textures=character_export.export_character(bpy.context.scene,rig,mesh,out)
manifest=copy.deepcopy(source)
for key in ('player_export','player_export_sha256'):manifest.pop(key,None)
manifest.update(stage='R117 horizontal feather grip; runtime review pending',revision=revision,
                blend=str((out/'ori.blend').relative_to(ROOT)),blend_sha256=hashlib.sha256((out/'ori.blend').read_bytes()).hexdigest(),
                export=str(export.relative_to(ROOT)),export_sha256=hashlib.sha256(export.read_bytes()).hexdigest(),
                blender=bpy.app.version_string,textures=textures,
                updated_clips=sorted(names),previous_revision=source['revision'])
manifest['animation']['clips'].update(metadata['clips'])
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('R117_GLIDE_MANIFEST='+str((out/'manifest.json').relative_to(ROOT)))
