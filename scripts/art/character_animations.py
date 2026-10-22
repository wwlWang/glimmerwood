"""Sample anatomical poses at 30 Hz; the renderer interpolates at display rate.

World travel belongs to the game. Root motion here is limited to small vertical
body articulation. Foot contacts, effects and cancel timing remain editable data.
"""
import bpy
import math
import copy
import re
from mathutils import Vector, Quaternion, Matrix
from character_arms import Arms, bow_grip_rotation
import character_poses
import character_clips

FPS = 30
CLIPS = {
    'idle': (90, True), 'alert': (60, True), 'transform_in': (18, False), 'transform_out': (15, False), 'death': (27, False),
    'start': (9, False), 'walk': (30, True), 'run': (24, True), 'stop': (12, False),
    'turn_left': (15, False), 'turn_right': (15, False), 'crouch': (60, True),
    'jump_start': (9, False), 'rise': (24, True), 'apex': (18, True), 'fall': (24, True),
    'double_jump': (15, False), 'land_light': (9, False), 'land_heavy': (15, False),
    'wall_hold': (30, True), 'wall_slide': (30, True), 'wall_jump_l': (12, False), 'wall_jump_r': (12, False),
    'dash_start': (9, False), 'dash_end': (9, False), 'bash_aim': (30, True), 'bash_launch': (12, False),
    'glide_open': (9, False), 'glide': (36, True), 'glide_close': (9, False), 'grapple': (24, True), 'stomp': (18, True),
    'blade_1': (18, False), 'blade_2': (20, False), 'blade_3': (27, False),
    'bow_charge': (30, False), 'bow_hold': (30, True), 'bow_release': (9, False), 'hurt': (9, False), 'recover': (12, False),
    'hold': (30, True), 'use_item': (24, True), 'block': (30, True), 'swim': (30, True), 'climb': (30, True), 'sit': (60, True), 'sleep': (90, True),
    'bend': (60, True), 'tail_sway': (60, True),
}
CLIPS.update({name: (frames, loop) for name, (frames, loop, _) in character_clips.CLIPS.items()})

def smooth(t):
    t=max(0,min(1,t))
    return t*t*(3-2*t)

def pulse(t):
    return math.sin(math.pi*max(0,min(1,t)))

def author(rig, mesh):
    scene=bpy.context.scene
    scene.render.fps=FPS
    bones=rig.pose.bones
    arm_solver=Arms(rig)
    bow_rotation=bow_grip_rotation()
    rest={b.name:(rig.matrix_world@b.matrix).to_quaternion() for b in bones}
    ankle_rest={s:rig.matrix_world@bones['ankle_'+s].head for s in ('l','r')}
    hip_lengths={s:((rig.matrix_world@bones['knee_'+s].head-rig.matrix_world@bones['thigh_'+s].head).length,
                    (ankle_rest[s]-rig.matrix_world@bones['knee_'+s].head).length) for s in ('l','r')}
    max_ik_error=0
    clip_ik_error=0
    def reset():
        for b in bones:
            b.rotation_mode='QUATERNION'
            b.matrix_basis.identity()
    def rotate(name, angle, axis=(1,0,0)):
        b=bones[name]
        q=rest[name]
        b.rotation_quaternion = (q.inverted()@Quaternion(axis,angle)@q)@b.rotation_quaternion
    def shift(name, amount):
        b=bones[name]
        basis=b.bone.matrix_local
        if b.parent:
            basis=b.parent.matrix@b.parent.bone.matrix_local.inverted()@basis
        b.location+=(rig.matrix_world@basis).to_3x3().inverted()@Vector(amount)
    def world_turn(name, delta):
        b=bones[name]
        m=rig.matrix_world@b.matrix
        p=m.translation
        b.matrix=rig.matrix_world.inverted()@Matrix.Translation(p)@delta.to_matrix().to_4x4()@Matrix.Translation(-p)@m
        bpy.context.view_layer.update()
    def leg(side, target):
        nonlocal max_ik_error,clip_ik_error
        bpy.context.view_layer.update()
        hip=rig.matrix_world@bones['thigh_'+side].head
        delta=target-hip
        actual_d=delta.length
        l1,l2=hip_lengths[side]
        d=max(abs(l1-l2)+.001,min(l1+l2-.001,actual_d))
        max_ik_error=max(max_ik_error,abs(d-actual_d))
        clip_ik_error=max(clip_ik_error,abs(d-actual_d))
        direction=delta.normalized()
        k=(l1*l1-l2*l2+d*d)/(2*d)
        h=math.sqrt(max(0,l1*l1-k*k))
        bend=Vector((0,-1,0))
        bend=(bend-direction*bend.dot(direction)).normalized()
        knee=hip+direction*k+bend*h
        original=rig.matrix_world@bones['knee_'+side].head-hip
        world_turn('thigh_'+side,original.rotation_difference(knee-hip))
        current_knee=rig.matrix_world@bones['knee_'+side].head
        current_ankle=rig.matrix_world@bones['ankle_'+side].head
        world_turn('knee_'+side,(current_ankle-current_knee).rotation_difference(hip+direction*d-current_knee))
        current=(rig.matrix_world@bones['ankle_'+side].matrix).to_quaternion()
        world_turn('ankle_'+side,rest['ankle_'+side]@current.inverted())
    def arms(out=.20, forward=0, bend=0):
        rotate('upper_arm_l',-math.pi/2+out,(0,1,0))
        rotate('upper_arm_r',math.pi/2-out,(0,1,0))
        for s in ('l','r'):
            rotate('upper_arm_'+s,forward)
            elbow(s,bend)
    def elbow(side,angle):
        # The imported bind arms point along X. Their flexion hinge is Z;
        # using X twists the forearm instead of bending the elbow.
        rotate('forearm_'+side,-angle if side=='l' else angle,(0,0,1))
    def crouch(depth=.09):
        shift('root',(0,0,-depth))
        rotate('spine',.25)
        rotate('head',-.10)
        for s in ('l','r'):
            leg(s,ankle_rest[s])
    def secondary(t, strength=1, tucked=0):
        p=t*math.tau
        for i in range(1,6):
            rotate('tail_'+str(i),math.sin(p-i*.48)*.11*strength,(0,0,1))
            rotate('tail_'+str(i),-.035+math.sin(p*2-i*.6)*.05*strength+tucked*.08)
        for side,sign in [('l',-1),('r',1)]:
            for i in range(1,4):
                rotate(f'ear_{side}_{i}',sign*math.sin(p-i*.55)*.09*strength,(0,0,1))
                rotate(f'ear_{side}_{i}',math.sin(p-i*.65)*.06*strength+tucked*.18)
                rotate(f'antenna_{side}_{i}',math.sin(p-i*.65)*.035*strength+tucked*.07)
    def gripping(side, amount):
        strength=min(1,amount/.45)
        # Bind fingers lie in XY; the palm and thumb are below them in -Z.
        # Curl toward that palm, with the distal joints wrapping the handle.
        for i in range(1,5):
            rotate(f'finger_{side}_{i}',(1.1 if i in (1,3) else 1.6)*strength,
                   (0,-1,0) if side=='l' else (0,1,0))
        for i in range(1,3):
            rotate(f'thumb_{side}_{i}',.85*strength,(0,0,-1) if side=='l' else (0,0,1))
    def air(kind, t):
        wave=math.sin(t*math.tau)
        if kind=='rise':
            arms(.45,-1.15+.08*wave,-.30)
            rotate('spine',-.10); rotate('head',-.12)
            rotate('thigh_l',-.62); rotate('knee_l',1.05)
            rotate('thigh_r',.32); rotate('knee_r',.30)
        elif kind=='apex':
            arms(.70,-.65,-.75)
            rotate('spine',.16); rotate('head',.04)
            for s in ('l','r'):
                rotate('thigh_'+s,-.62+.04*wave); rotate('knee_'+s,1.0)
        else:
            arms(.95+.08*wave,-.45,-.70)
            rotate('spine',.04); rotate('head',-.24)
            rotate('thigh_l',.10+.05*wave); rotate('knee_l',.30)
            rotate('thigh_r',-.18-.05*wave); rotate('knee_r',.40)
    def glider(t):
        arms(2.45,-.20,-.30)
        rotate('spine',.12); rotate('head',-.10)
        rotate('thigh_l',.20); rotate('knee_l',.22)
        rotate('thigh_r',-.15); rotate('knee_r',.42)
    class Ctx: pass
    ctx=Ctx()
    ctx.rotate,ctx.shift,ctx.leg,ctx.elbow,ctx.gripping,ctx.ankle_rest=rotate,shift,leg,elbow,gripping,ankle_rest
    actions=[]
    metadata={}
    rig.animation_data_create()
    for name,(length,loop) in CLIPS.items():
        clip_ik_error=0
        action=bpy.data.actions.new(name)
        action.use_fake_user=True
        action['loop']=loop
        action['root_motion']='in_place'
        rig.animation_data.action=action
        contacts=[]
        events=[]
        for frame in range(length+1):
            scene.frame_set(frame)
            reset()
            t=frame/length
            phase=t*math.tau
            if name in character_clips.CLIPS:
                character_poses.apply(ctx,character_clips.CLIPS[name][2](t))
            elif name in {'idle','alert','hold','use_item','block','tail_sway','bend'}:
                breath=math.sin(phase)
                arms(.16+.03*breath,-.08,-.18)
                shift('root',(math.sin(phase)*.008,0,-.012+.006*breath))
                rotate('spine',.05+breath*.04)
                rotate('head',-.04+math.sin(phase+.6)*.035)
                rotate('head',math.sin(phase+.4)*.06,(0,0,1))  # whole cycles only: loops must close
                if name=='alert':
                    rotate('head',math.sin(phase)*.25,(0,0,1))
                    rotate('head',-.10)
                if name in {'hold','use_item','block'}:
                    rotate('upper_arm_r',-.65); elbow('r',-.45)
                    gripping('r',.45)
                if name=='use_item':
                    rotate('upper_arm_r',-.38*(.5+.5*math.sin(phase)))
                    elbow('r',-.25)
                    rotate('head',.07*(.5+.5*math.sin(phase)))
                if name=='block':
                    rotate('upper_arm_r',-.35);elbow('r',-.50)
                    rotate('upper_arm_r',-.25,(0,0,1))
                if name=='bend':
                    rotate('spine',.65*(.5-.5*math.cos(phase)))
                    rotate('head',-.20*(.5-.5*math.cos(phase)))
            elif name in {'walk','run'}:
                run=name=='run'
                duty=.24 if run else .60
                stride=2.0 if run else .80
                bounce=.05*abs(math.sin(phase)) if run else .012*(1-math.cos(phase*2))
                shift('root',(0,0,(-.095 if run else -.058)+bounce))
                # v013: Ori runs low and leaning into the stride (in game the v012 run read upright at a close camera).
                rotate('root',.22 if run else .03)
                rotate('spine',.50 if run else .12)
                rotate('head',-.52 if run else -.10)
                rotate('spine',math.sin(phase)*(.16 if run else .07),(0,0,1))
                rotate('head',-math.sin(phase)*(.10 if run else .04),(0,0,1))
                arms(.26 if run else .16,0,-1.05 if run else -.25)
                for side,offset in [('l',0),('r',.5)]:
                    p=(t+offset)%1
                    if p<duty:
                        y=-stride*duty/2+stride*p
                        z=0
                    else:
                        f=(p-duty)/(1-duty)
                        y=stride*duty/2-stride*duty*smooth(f)
                        z=math.sin(math.pi*f)*(.24 if run else .09)
                    target=ankle_rest[side].copy()
                    target.y=.036+y
                    target.z+=z
                    leg(side,target)
                    rotate('upper_arm_'+side,-math.sin(p*math.tau)*(1.3 if run else .40))
                if frame in (0,length//2):
                    contacts.append({'frame':frame,'foot':'l' if frame==0 else 'r'})
            elif name=='crouch':
                arms(.25,-.35,-.18)
                crouch(.12+.003*math.sin(phase))
            elif name in {'transform_in','transform_out'}:
                f=smooth(t if name=='transform_in' else 1-t)
                arms(.20+.45*(1-f),-.25*(1-f),-.2)
                crouch(.10*(1-f))
                rotate('head',-.15*pulse(t))
            elif name in {'start','stop','jump_start','land_light','land_heavy'}:
                f=pulse(t)
                if name=='start':
                    arms(.25,-.25,-.30)
                    crouch(.06*(1-smooth(t)))
                    rotate('spine',.22*smooth(t))
                elif name=='stop':
                    arms(.4,.20*f,-.3)
                    crouch(.09*f)
                    rotate('spine',-.25*f)
                elif name=='jump_start':
                    # Played over ~3 ticks as the jump fires (the jump force is immediate): a push-off from
                    # a loaded crouch into the stretched rise pose, never a crouch in mid-air.
                    lift=smooth(t)
                    arms(.25+.20*lift,.70*(1-lift)-1.15*lift,-.35+.05*lift)
                    crouch(.13*(1-lift))
                    rotate('spine',.30*(1-lift)-.10*lift)
                    rotate('thigh_l',-.62*lift); rotate('knee_l',1.05*lift)
                    rotate('thigh_r',.32*lift); rotate('knee_r',.30*lift)
                else:
                    f=smooth(t/.22) if t<.22 else 1-smooth((t-.22)/.78)
                    heavy=name=='land_heavy'
                    arms(.35+(.45 if heavy else .2)*f,-(.6 if heavy else .3)*f,-.25-.3*f)
                    crouch((.20 if heavy else .09)*f)
                    rotate('spine',(.40 if heavy else .18)*f)
                    rotate('head',(.10 if heavy else .05)*f)
            elif name.startswith('turn_'):
                f=pulse(t)
                sign=1 if name.endswith('left') else -1
                arms(.25,-.15,-.2)
                rotate('spine',sign*.18*f,(0,0,1))
                rotate('head',sign*.3*f,(0,0,1))
                rotate('root',sign*.07*f,(0,1,0))
            elif name in {'rise','apex','fall'}:
                air(name,t)
            elif name=='double_jump':
                # Tuck fast, hold it through the flip, open late for the fall.
                f=smooth(min(1,t*3.5))*(1-smooth((t-.72)/.28))
                arms(.45,-1.35*f,-1.0*f)
                rotate('spine',.35*f); rotate('head',.20*f)
                for s in ('l','r'):
                    rotate('thigh_'+s,-1.45*f); rotate('knee_'+s,1.7*f)
                rotate('root',-math.tau*smooth(t))
                shift('root',(0,0,.14*f))
            elif name in {'wall_hold','wall_slide'}:
                arms(.42,-1.85,-.2)
                for s in ('l','r'):
                    rotate('thigh_'+s,-.70); rotate('knee_'+s,1.1)
                rotate('head',-.18)
                if name=='wall_slide':
                    rotate('spine',.05*math.sin(phase))
                    rotate('ear_l_1',.08); rotate('ear_r_1',.08)
            elif name.startswith('wall_jump'):
                air('rise',t)
                sign=-1 if name.endswith('_l') else 1
                rotate('root',sign*.35*(1-smooth(t)),(0,1,0))
                rotate('spine',sign*.32*(1-smooth(t)),(0,0,1))
            elif name in {'dash_start','dash_end','bash_launch'}:
                f=smooth(t) if name=='dash_start' else 1-smooth(t)
                if name=='bash_launch':
                    f=1-.25*smooth(t)
                arms(.30,1.25*f,-.20)
                rotate('spine',.65*f); rotate('head',-.55*f)
                rotate('thigh_l',-.75*f); rotate('knee_l',1.25*f)
                rotate('thigh_r',.60*f); rotate('knee_r',.25*f)
                rotate('root',.60*f)
            elif name=='bash_aim':
                arms(.6,-.65,-.8)
                rotate('spine',.20)
                for s in ('l','r'):
                    rotate('thigh_'+s,-.65); rotate('knee_'+s,.85)
            elif name in {'glide','glide_open','glide_close'}:
                glider(t)
                f=1 if name=='glide' else smooth(t) if name=='glide_open' else 1-smooth(t)
                if f<1:
                    for b in bones:
                        b.rotation_quaternion=Quaternion().slerp(b.rotation_quaternion,f)
                    # Blend from an airborne pose rather than the bind arms.
                    rotate('upper_arm_l',(-math.pi/2+.75)*(1-f),(0,1,0))
                    rotate('upper_arm_r',(math.pi/2-.75)*(1-f),(0,1,0))
            elif name=='grapple':
                arms(2.50,-.30,-.10)
                rotate('spine',-.18); rotate('head',-.15)
                rotate('thigh_l',-.25); rotate('knee_l',.35)
                rotate('thigh_r',.25); rotate('knee_r',.45)
                gripping('l',.35); gripping('r',.35)
            elif name=='stomp':
                arms(.1,.20,-.55)
                rotate('spine',.2); rotate('head',.20)
                for s in ('l','r'):
                    rotate('thigh_'+s,-.15); rotate('knee_'+s,.20)
                gripping('l',.5); gripping('r',.5)
            elif name in ('blade_1','blade_2','blade_3'):
                stage=int(name[-1])
                # Shared server windows: prep / hit / recovery (ticks at 20 Hz).
                prep=[2,3,4][stage-1]/20
                hit=2/20
                duration=[12,13,18][stage-1]/20
                seconds=t*duration
                if seconds<prep:
                    wind=smooth(seconds/prep); strike=0; recover=0
                elif seconds<prep+hit:
                    wind=1; strike=smooth((seconds-prep)/hit); recover=0
                else:
                    wind=1; strike=1; recover=smooth((seconds-prep-hit)/max(.05,duration-prep-hit))
                active=1-recover
                arms(.30,-.20,-.40)
                sign=-1 if stage==2 else 1
                twist=sign*(.85*wind-1.75*strike)*active
                lunge=(.20+.25*strike)*active if stage<3 else (.10+.45*strike)*active
                rotate('spine',lunge)
                rotate('spine',twist,(0,0,1))
                rotate('head',-twist*.55,(0,0,1))
                rotate('head',-lunge*.6)
                rotate('upper_arm_r',(-1.5*wind+2.5*strike)*active)
                rotate('upper_arm_r',sign*(1.1*wind-2.0*strike)*active,(0,0,1))
                elbow('r',-.95*(1-strike)*active)
                if stage==3:
                    rotate('upper_arm_r',-2.2*wind*(1-strike)*active)
                    shift('root',(0,0,.05*pulse(min(1,(wind+strike)/2))*active))
                rotate('upper_arm_l',-.55*active)
                rotate('upper_arm_l',-sign*.7*strike*active,(0,0,1))
                elbow('l',-.6*active)
                gripping('r',.55)
                crouch((.05+.06*strike)*active)
            elif name in {'bow_charge','bow_hold','bow_release'}:
                f=smooth(t) if name=='bow_charge' else 1 if name=='bow_hold' else 1-smooth(t)
                arms(.14,-.05,-.06)
                rotate('upper_arm_l',-.65);elbow('l',-.45)
                rotate('spine',-.12*f,(0,0,1));rotate('head',.12*f,(0,0,1))
                gripping('r',.45);gripping('l',.45)
                bpy.context.view_layer.update()
                starts={s:rig.matrix_world@bones['grip_'+s].matrix for s in ('l','r')}
                for side,target,pole in [('l',(-.16,-.36,.76),(-1,0,-.4)),('r',(-.16,-.075,.76),(1,0,-.25))]:
                    point=starts[side].translation.lerp(Vector(target),f)
                    if side=='r' and name=='bow_release':point.y+=.045*pulse(t)
                    rotation=starts[side].to_quaternion().slerp(bow_rotation,f)
                    arm_solver.contact(side,point,rotation,pole)
            elif name in {'hurt','recover'}:
                f=pulse(t) if name=='hurt' else 1-smooth(t)
                arms(.45,-.2,-.4)
                rotate('spine',-.30*f); rotate('head',-.15*f)
                rotate('root',-.12*f)
                crouch(.05*f)
            elif name=='swim':
                arms(1.10,-.5,-.25)
                rotate('root',math.pi/2)
                shift('root',(0,0,.08))
                for s,sign in [('l',1),('r',-1)]:
                    rotate('upper_arm_'+s,sign*.7*math.sin(phase))
                    rotate('thigh_'+s,sign*.28*math.sin(phase))
                    rotate('knee_'+s,.2+max(0,sign*math.sin(phase))*.30)
            elif name=='climb':
                arms(.45,-1.65,-.25)
                for s,sign in [('l',1),('r',-1)]:
                    rotate('upper_arm_'+s,sign*.35*math.sin(phase))
                    rotate('thigh_'+s,-.40-sign*.30*math.sin(phase))
                    rotate('knee_'+s,.65+sign*.30*math.sin(phase))
            elif name=='sit':
                arms(.2,-.45,-.20)
                for s in ('l','r'):
                    rotate('thigh_'+s,-1.35); rotate('knee_'+s,1.4)
                shift('root',(0,0,-.12))
                rotate('tail_1',.3)
            elif name in {'sleep','death'}:
                arms(.35,-.5,-.5)
                f=1 if name=='sleep' else smooth(t)
                rotate('root',-math.pi/2*f)
                shift('root',(0,0,-.28*f))
                rotate('spine',.12*f)
                rotate('head',.12*f)
                for s in ('l','r'):
                    rotate('thigh_'+s,-.20*f); rotate('knee_'+s,.40*f)
            secondary(t, .3 if name in {'sleep','death'} else 1.8 if name=='tail_sway' else 1,
                      .8 if name in {'run','dash_start','dash_end','bash_launch'} else 0)
            for b in bones:
                b.keyframe_insert('rotation_quaternion',frame=frame,group=b.name)
                b.keyframe_insert('location',frame=frame,group=b.name)
                b.keyframe_insert('scale',frame=frame,group=b.name)
        if name in ('blade_1','blade_2','blade_3'):
            stage=int(name[-1])
            duration=[12,13,18][stage-1]
            events=[{'frame':length*[2,3,4][stage-1]/duration,'event':'blade_hit_start'},
                    {'frame':length*[4,5,6][stage-1]/duration,'event':'blade_recovery'}]
        elif name in {'land_light','land_heavy'}:
            events=[{'frame':0,'event':'foot_contact'}]
        elif name in {'dash_start','double_jump','bash_launch','bow_release'}:
            events=[{'frame':0,'event':name}]
        for contact in contacts:
            action.pose_markers.new('contact_'+contact['foot']).frame=contact['frame']
        for event in events:
            action.pose_markers.new(event['event']).frame=round(event['frame'])
        # Uniform samples interpolate linearly; Bezier handles would overshoot contacts.
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for key in curve.keyframe_points:
                            key.interpolation='LINEAR'
        metadata[name]={'frames':length,'seconds':length/FPS,'loop':loop,'contacts':contacts,'events':events,
                        'max_ik_target_clamp':clip_ik_error}
        if name in ('blade_1','blade_2','blade_3'):
            metadata[name]['logic_seconds']=[12,13,18][int(name[-1])-1]/20
        if name in {'walk','run'}:
            metadata[name]['metres_per_cycle']=.80 if name=='walk' else 2.0
        actions.append(action)
    # Author asymmetric interactions once, then reflect their world-space pose
    # deltas through the rest skeleton. The imported left/right bone rolls differ,
    # so negating quaternion components would not produce a valid opposite hand.
    reflection=Matrix.Diagonal((-1,1,1,1))
    world_rest={b.name:rig.matrix_world@b.matrix_local for b in rig.data.bones}
    def opposite(name):
        return re.sub(r'_(l|r)(?=_|$)',lambda m:'_r' if m[1]=='l' else '_l',name)
    for name in ('hold','use_item','block','blade_1','blade_2','blade_3','bow_charge','bow_hold','bow_release',
                 'blade_air','throw_ready','spear_throw','hammer_charge','hammer_smash','mine','throw','drink','horn','brush','grapple','place','hurt_side'):
        original=next(a for a in actions if a.name==name)
        length,loop=CLIPS[name]
        rig.animation_data.action=original
        samples=[]
        for frame in range(length+1):
            scene.frame_set(frame)
            bpy.context.view_layer.update()
            source={b.name:rig.matrix_world@b.matrix for b in bones}
            samples.append({b.name:rig.matrix_world.inverted()@reflection@source[opposite(b.name)]
                            @world_rest[opposite(b.name)].inverted()@reflection@world_rest[b.name] for b in bones})
        # One-handed clips were authored on +X (anatomical left); bows already
        # put the held bow in -X (anatomical right) and draw with the other hand.
        original_left=not name.startswith('bow_')
        original.name=name+'_left' if original_left else name
        mirrored=bpy.data.actions.new(name if original_left else name+'_left')
        mirrored.use_fake_user=True
        mirrored['loop']=loop
        mirrored['root_motion']='in_place'
        rig.animation_data.action=mirrored
        for frame,pose in enumerate(samples):
            scene.frame_set(frame)
            for b in bones:
                matrix=b.bone.matrix_local.inverted()
                if b.parent:
                    matrix=matrix@b.parent.bone.matrix_local@pose[b.parent.name].inverted()
                b.matrix_basis=matrix@pose[b.name]
                for prop in ('rotation_quaternion','location','scale'):
                    b.keyframe_insert(prop,frame=frame,group=b.name)
        for layer in mirrored.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for key in curve.keyframe_points:
                            key.interpolation='LINEAR'
        for marker in original.pose_markers:
            mirrored.pose_markers.new(marker.name).frame=marker.frame
        metadata[name+'_left']=copy.deepcopy(metadata[name])
        metadata[name]['hand']='right'
        metadata[name+'_left']['hand']='left'
        actions.append(mirrored)
    rig.animation_data.action=actions[0]
    scene.frame_start=0
    scene.frame_end=CLIPS['idle'][0]
    scene.frame_set(0)
    return {'fps':FPS,'clips':metadata,'max_ik_target_clamp':max_ik_error},actions
