"""Editable colour layers baked to one 1024 px material with PBR surface data."""
import bpy
import math

def build(mesh, output):
    # Welding leaves the body plus two separate eyeballs.
    parents = list(range(len(mesh.data.vertices)))
    def root(i):
        while parents[i] != i:
            parents[i] = parents[parents[i]]
            i = parents[i]
        return i
    for edge in mesh.data.edges:
        a,b = edge.vertices
        parents[root(a)] = root(b)
    components = {}
    for i in range(len(parents)):
        components.setdefault(root(i), []).append(i)
    body = max(components, key=lambda k: len(components[k]))
    eyes = {i for k,indices in components.items() if k != body for i in indices}
    colour = mesh.data.color_attributes.new(name='Pearl colour', type='FLOAT_COLOR', domain='POINT')
    glow = mesh.data.color_attributes.new(name='Spirit emission', type='FLOAT_COLOR', domain='POINT')
    surface = mesh.data.color_attributes.new(name='Surface ORM', type='FLOAT_COLOR', domain='POINT')
    for i, v in enumerate(mesh.data.vertices):
        x,y,z = mesh.matrix_world @ v.co
        if i in eyes:
            highlight = math.exp(-(((abs(x)-0.065)/0.009)**2 + ((z-0.927)/0.012)**2 + ((y+0.125)/0.027)**2)*2)
            c = tuple(min(1,b+highlight*0.92) for b in (0.007,0.022,0.036))
            e = tuple(0.006+highlight*0.6 for _ in range(3))
        else:
            front = max(0,min(1,(0.09-y)/0.18))
            c = [0.61+0.24*front,0.79+0.13*front,0.91+0.055*front]
            # Pale blue undersides keep the silhouette legible in white daylight.
            shade = max(0,1-z/0.19)*0.19
            c = [c[0]-shade,c[1]-shade*0.55,c[2]-shade*0.2]
            if z>1.01 and y<0:
                inner = max(0,1-abs(abs(x)-0.075)/0.04)*min(1,(z-1.01)/0.045)
                c = [c[j]*(1-inner*0.45)+[0.15,0.63,0.88][j]*inner*0.45 for j in range(3)]
            if 0.78<z<0.90 and abs(x)>0.14:
                inner = max(0,1-abs(z-0.835)/0.065)*0.33
                c = [c[j]*(1-inner)+[0.24,0.65,0.87][j]*inner for j in range(3)]
            nose = abs(x)<0.013 and y<-.183 and .851<z<.874
            if nose:
                c = [.045,.14,.19]
            strength = 0.045 + max(0,(z-1.08)/0.12)*0.15 + max(0,(y-0.43)/0.13)*0.18
            e = [c[j]*strength for j in range(3)]
        colour.data[i].color = (*c,1)
        glow.data[i].color = (*e,1)
        surface.data[i].color = (1, .16 if i in eyes else .62, 0, 1)
    mesh.data.materials.clear()
    mats=[]
    for name in ['Ori Pearl']:
        m=bpy.data.materials.new(name)
        m.use_nodes=True
        mesh.data.materials.append(m)
        mats.append(m)
    for poly in mesh.data.polygons:
        poly.material_index = 0
    bpy.context.view_layer.objects.active=mesh
    bpy.ops.object.select_all(action='DESELECT')
    mesh.select_set(True)
    # Keep the source UVs for editing and use a packed, overlap-free atlas at runtime.
    uv=mesh.data.uv_layers.new(name='Runtime atlas')
    mesh.data.uv_layers.active=uv
    uv.active_render=True
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(66),island_margin=0.015,area_weight=0.4)
    bpy.ops.object.mode_set(mode='OBJECT')
    scene=bpy.context.scene
    scene.render.engine='CYCLES'
    scene.cycles.samples=1
    scene.render.bake.margin=8
    images={}
    for name,layer in [('ori_base','Pearl colour'),('ori_emission','Spirit emission'),('ori_surface','Surface ORM')]:
        image=bpy.data.images.new(name,width=1024,height=1024,alpha=True)
        if name=='ori_surface':
            image.colorspace_settings.name='Non-Color'
        image.filepath_raw=str(output / (name+'.png'))
        image.file_format='PNG'
        images[name]=image
        for m in mats:
            nodes=m.node_tree.nodes
            nodes.clear()
            attr=nodes.new('ShaderNodeVertexColor')
            attr.layer_name=layer
            emit=nodes.new('ShaderNodeEmission')
            out=nodes.new('ShaderNodeOutputMaterial')
            target=nodes.new('ShaderNodeTexImage')
            target.image=image
            nodes.active=target
            m.node_tree.links.new(attr.outputs['Color'],emit.inputs['Color'])
            m.node_tree.links.new(emit.outputs[0],out.inputs['Surface'])
        bpy.ops.object.bake(type='EMIT')
        image.save()
        image.pack()
    for m in mats:
        nodes=m.node_tree.nodes
        nodes.clear()
        shader=nodes.new('ShaderNodeBsdfPrincipled')
        out=nodes.new('ShaderNodeOutputMaterial')
        base=nodes.new('ShaderNodeTexImage')
        base.image=images['ori_base']
        emission=nodes.new('ShaderNodeTexImage')
        emission.image=images['ori_emission']
        surface=nodes.new('ShaderNodeTexImage')
        surface.image=images['ori_surface']
        channels=nodes.new('ShaderNodeSeparateColor')
        channels.mode='RGB'
        m.node_tree.links.new(surface.outputs['Color'],channels.inputs['Color'])
        m.node_tree.links.new(channels.outputs['Green'],shader.inputs['Roughness'])
        m.node_tree.links.new(channels.outputs['Blue'],shader.inputs['Metallic'])
        shader.inputs['Emission Strength'].default_value=1
        m.node_tree.links.new(base.outputs['Color'],shader.inputs['Base Color'])
        m.node_tree.links.new(emission.outputs['Color'],shader.inputs['Emission Color'])
        m.node_tree.links.new(shader.outputs[0],out.inputs['Surface'])
    return {'texture_size':1024,'images':list(images),'eye_vertices':len(eyes)}
