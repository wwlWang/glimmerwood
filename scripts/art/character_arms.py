"""Author wrist contacts with a two-segment arm solve; bake the result into clips."""
import bpy
import math
from mathutils import Matrix, Vector

class Arms:
    def __init__(self, rig):
        self.rig=rig
        self.bones=rig.pose.bones
        self.rest={b.name:rig.matrix_world@b.matrix_local for b in rig.data.bones}
        self.max_error=0

    def turn(self, name, delta):
        bone=self.bones[name]
        world=self.rig.matrix_world@bone.matrix
        point=world.translation
        bone.matrix=self.rig.matrix_world.inverted()@Matrix.Translation(point)@delta.to_matrix().to_4x4()@Matrix.Translation(-point)@world
        bpy.context.view_layer.update()

    def contact(self, side, target, grip_rotation, pole):
        upper,lower,hand,grip=[name+'_'+side for name in ('upper_arm','forearm','hand','grip')]
        rest_hand=self.rest[hand];rest_grip=self.rest[grip]
        local_rotation=rest_hand.to_quaternion().inverted()@rest_grip.to_quaternion()
        hand_rotation=grip_rotation@local_rotation.inverted()
        local_offset=rest_hand.to_quaternion().inverted()@(rest_grip.translation-rest_hand.translation)
        wrist=Vector(target)-hand_rotation@local_offset
        bpy.context.view_layer.update()
        shoulder=self.rig.matrix_world@self.bones[upper].head
        a=(self.rest[lower].translation-self.rest[upper].translation).length
        b=(self.rest[hand].translation-self.rest[lower].translation).length
        delta=wrist-shoulder
        distance=max(abs(a-b)+.001,min(a+b-.001,delta.length))
        self.max_error=max(self.max_error,abs(distance-delta.length))
        direction=delta.normalized()
        k=(a*a-b*b+distance*distance)/(2*distance)
        height=math.sqrt(max(0,a*a-k*k))
        bend=Vector(pole);bend=(bend-direction*bend.dot(direction)).normalized()
        elbow=shoulder+direction*k+bend*height
        current=self.rig.matrix_world@self.bones[lower].head-shoulder
        self.turn(upper,current.rotation_difference(elbow-shoulder))
        current_elbow=self.rig.matrix_world@self.bones[lower].head
        current_wrist=self.rig.matrix_world@self.bones[hand].head
        self.turn(lower,(current_wrist-current_elbow).rotation_difference(shoulder+direction*distance-current_elbow))
        current_rotation=(self.rig.matrix_world@self.bones[hand].matrix).to_quaternion()
        self.turn(hand,hand_rotation@current_rotation.inverted())

def bow_grip_rotation():
    # The bow's diagonal limbs become vertical and its arrow points Blender -Y.
    s=math.sqrt(.5)
    desired=Matrix(((0,0,1),(s,-s,0),(s,s,0)))
    rx=lambda a:Matrix.Rotation(math.radians(a),3,'X')
    ry=lambda a:Matrix.Rotation(math.radians(a),3,'Y')
    rz=lambda a:Matrix.Rotation(math.radians(a),3,'Z')
    vanilla=rx(-90)@ry(180)@rx(-80)@ry(260)@rz(-40)
    return (desired@vanilla.inverted()).to_quaternion()
