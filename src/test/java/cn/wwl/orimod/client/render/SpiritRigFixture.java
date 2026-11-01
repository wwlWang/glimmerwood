package cn.wwl.orimod.client.render;

import com.google.gson.*;
import com.wf.gemrender.gltf.NodeTable;
import org.joml.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual committed skin and inverse binds; no replacement skeleton or synthetic cylinder. */
final class SpiritRigFixture {
    final JsonObject json;
    final ByteBuffer data;
    final int binary;
    final NodeTable table;
    final int[] joints;
    final Matrix4f[] inverse;
    final float[][] positions, weights, indices;
    final int[] triangles;
    final Matrix4f[] rest;

    SpiritRigFixture() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("orimod.projectDir"), "src/main/resources/assets/orimod/models/entity/ori.glb"));
        data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int length = data.getInt(12); binary = 28 + length;
        json = JsonParser.parseString(new String(bytes, 20, length, StandardCharsets.UTF_8)).getAsJsonObject();
        var nodes = json.getAsJsonArray("nodes");
        String[] names = new String[nodes.size()]; int[] parents = new int[nodes.size()]; Arrays.fill(parents, -1);
        float[] trs = new float[nodes.size() * NodeTable.TRS_STRIDE];
        for (int i=0; i<nodes.size(); i++) {
            var n = nodes.get(i).getAsJsonObject(); names[i] = n.has("name") ? n.get("name").getAsString() : "node_"+i;
            if (n.has("matrix")) throw new IllegalStateException("Update reader for matrix nodes");
            for (int field=0; field<3; field++) {
                String key = new String[]{"translation","rotation","scale"}[field];
                int offset = new int[]{NodeTable.TRANSLATION,NodeTable.ROTATION,NodeTable.SCALE}[field];
                for (int k=0;k<(field==1?4:3);k++) trs[i*NodeTable.TRS_STRIDE+offset+k] = n.has(key)
                        ? n.getAsJsonArray(key).get(k).getAsFloat() : field==2 || field==1 && k==3 ? 1:0;
            }
            if(n.has("children")) for(var child:n.getAsJsonArray("children")) parents[child.getAsInt()]=i;
        }
        table=NodeTable.ofNodes(names,parents,trs); rest=world(null);
        var skin=json.getAsJsonArray("skins").get(0).getAsJsonObject();
        joints=new int[skin.getAsJsonArray("joints").size()];
        float[][] binds=values(skin.get("inverseBindMatrices").getAsInt()); inverse=new Matrix4f[joints.length];
        for(int i=0;i<joints.length;i++) {joints[i]=skin.getAsJsonArray("joints").get(i).getAsInt();inverse[i]=new Matrix4f().set(binds[i]);}
        var primitive=json.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();
        var attributes=primitive.getAsJsonObject("attributes");
        var topology=values(primitive.get("indices").getAsInt());triangles=new int[topology.length];for(int i=0;i<triangles.length;i++)triangles[i]=(int)topology[i][0];
        positions=values(attributes.get("POSITION").getAsInt()); weights=values(attributes.get("WEIGHTS_0").getAsInt());indices=values(attributes.get("JOINTS_0").getAsInt());
    }
    float[][] values(int id) {
        var a=json.getAsJsonArray("accessors").get(id).getAsJsonObject();
        var v=json.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        int components=switch(a.get("type").getAsString()){case "VEC2"->2;case "VEC3"->3;case "VEC4"->4;case "MAT4"->16;default->1;};
        int type=a.get("componentType").getAsInt(), size=type==5121?1:type==5123?2:4;
        int stride=v.has("byteStride")?v.get("byteStride").getAsInt():components*size;
        int offset=binary+(v.has("byteOffset")?v.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0);
        float[][] out=new float[a.get("count").getAsInt()][components];
        for(int i=0;i<out.length;i++)for(int k=0;k<components;k++) {int at=offset+i*stride+k*size;out[i][k]=switch(type){case 5121->Byte.toUnsignedInt(data.get(at));case 5123->Short.toUnsignedInt(data.getShort(at));case 5125->data.getInt(at);case 5126->data.getFloat(at);default->throw new IllegalStateException();};}
        return out;
    }
    Matrix4f[] world(SpiritPoseLayer pose) {
        float[] scratch=table.newScratch();table.resetToRest(scratch);
        if(pose!=null)for(int bone=0;bone<SpiritPoseLayer.BONES.length;bone++) {int slot=table.slotOfName(SpiritPoseLayer.BONES[bone]);if(slot>=0)table.setRotation(scratch,slot,pose.target(bone));}
        Matrix4f[] out=new Matrix4f[table.nodeCount()];
        for(int slot:table.evaluationOrder()) {var local=new Matrix4f();table.localTransform(scratch,slot,local);int parent=table.parentSlots()[slot];out[slot]=parent<0?local:new Matrix4f(out[parent]).mul(local);}
        return out;
    }
    Vector3f[] skin(Matrix4f[] world) {
        Matrix4f[] palette=new Matrix4f[joints.length];for(int i=0;i<joints.length;i++)palette[i]=new Matrix4f(world[joints[i]]).mul(inverse[i]);
        Vector3f[] out=new Vector3f[positions.length];
        for(int i=0;i<out.length;i++) {out[i]=new Vector3f();for(int k=0;k<4;k++)if(weights[i][k]>0)out[i].fma(weights[i][k],palette[(int)indices[i][k]].transformPosition(new Vector3f(positions[i])));}
        return out;
    }
    Vector3f point(Matrix4f[] world,String bone){return world[table.slotOfName(bone)].getTranslation(new Vector3f());}
    Vector3f tip(Matrix4f[] world,boolean left){return new Matrix4f().rotate(world[table.slotOfName(left?"grip_r":"grip_l")].getUnnormalizedRotation(new Quaternionf()))
            .rotateX((float)java.lang.Math.toRadians(SpiritItemGrip.attachmentPitch(1,1))).rotateY((float)java.lang.Math.PI)
            .rotateY(-(float)java.lang.Math.PI/2).rotateZ((float)java.lang.Math.toRadians(55)).transformDirection(new Vector3f(1,1,0)).normalize();}
}
