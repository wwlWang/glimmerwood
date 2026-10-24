package cn.wwl.orimod.world;

import java.util.*;

/** Disjoint rectangles of the exact box union at a height: no overlapping transparent water layers. */
public final class GinsoFloodSurface {
    public record Rect(double x0,double z0,double x1,double z1){}
    public static List<Rect> at(List<GinsoEscapeLayout.Box> boxes,double y){
        var active=boxes.stream().filter(b->y>=b.y0()&&y<b.y1()).toList();
        var zs=new TreeSet<Double>();for(var b:active){zs.add(b.z0());zs.add(b.z1());}
        var out=new ArrayList<Rect>();Double previous=null;
        for(double z:zs){if(previous!=null){double low=previous,mid=(low+z)*.5;
            var row=active.stream().filter(b->b.z0()<=mid&&b.z1()>mid).sorted(Comparator.comparingDouble(GinsoEscapeLayout.Box::x0)).toList();
            double start=Double.NaN,end=0;
            for(var b:row){if(Double.isNaN(start)){start=b.x0();end=b.x1();}else if(b.x0()<=end)end=Math.max(end,b.x1());else{out.add(new Rect(start,low,end,z));start=b.x0();end=b.x1();}}
            if(!Double.isNaN(start))out.add(new Rect(start,low,end,z));
        }previous=z;}
        return List.copyOf(out);
    }
    private GinsoFloodSurface(){}
}
