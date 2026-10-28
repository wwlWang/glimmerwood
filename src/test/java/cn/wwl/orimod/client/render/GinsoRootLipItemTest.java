package cn.wwl.orimod.client.render;

import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GinsoRootLipItemTest {
    @Test void guiVerticesStayInsideSlotWithOnePixelMargin() throws Exception {
        var item = BuildingItemModelTest.read("item/ginso_root_lip");
        var block = BuildingItemModelTest.read("block/ginso_root_lip");
        var matrix = new Matrix4f();
        JsonObject display = item.has("display") ? item.getAsJsonObject("display").getAsJsonObject("gui") : null;
        if (display != null) {
            var t = vector(display, "translation", 0); var r = vector(display, "rotation", 0); var s = vector(display, "scale", 1);
            matrix.translate(t).rotateXYZ((float)Math.toRadians(r.x), (float)Math.toRadians(r.y), (float)Math.toRadians(r.z)).scale(s);
        }
        float minX=Float.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;
        for (var e : block.getAsJsonArray("elements")) {
            var lo=vector(e.getAsJsonObject(),"from",0);var hi=vector(e.getAsJsonObject(),"to",0);
            for(int c=0;c<8;c++) {
                var v=new Vector3f((c&1)==0?lo.x:hi.x,(c&2)==0?lo.y:hi.y,(c&4)==0?lo.z:hi.z).sub(8,8,8);
                matrix.transformPosition(v);minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);
            }
        }
        assertTrue(minX>=-7 && maxX<=7 && minY>=-7 && maxY<=7,"GUI pixel bounds: "+minX+","+minY+" .. "+maxX+","+maxY);
        assertTrue(maxX-minX>=8,"Icon became too small to read");
    }
    private static Vector3f vector(JsonObject j,String key,float fallback) {
        if(!j.has(key))return new Vector3f(fallback);
        var a=j.getAsJsonArray(key);return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
}
