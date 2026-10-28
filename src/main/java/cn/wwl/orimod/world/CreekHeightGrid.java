package cn.wwl.orimod.world;

import java.util.HashMap;
import java.util.function.IntBinaryOperator;

/** Pure noise interpolation; only the grading rim and its outside seam request corners. */
final class CreekHeightGrid {
    static final int STEP=8;
    private final IntBinaryOperator height;
    private final HashMap<Long,Integer> corners=new HashMap<>();
    CreekHeightGrid(IntBinaryOperator height){this.height=height;}
    int at(int x,int z){
        int step=STEP;
        int x0=Math.floorDiv(x,step)*step,z0=Math.floorDiv(z,step)*step;
        int dx=x-x0,dz=z-z0;
        long sum=(long)corner(x0,z0)*(step-dx)*(step-dz)
                +(dx==0?0:(long)corner(x0+step,z0)*dx*(step-dz))
                +(dz==0?0:(long)corner(x0,z0+step)*(step-dx)*dz)
                +(dx==0||dz==0?0:(long)corner(x0+step,z0+step)*dx*dz);
        return (int)Math.round((double)sum/(step*step));
    }
    private int corner(int x,int z){return corners.computeIfAbsent(((long)x<<32)^(z&0xffffffffL),key->height.applyAsInt(x,z));}
    int samples(){return corners.size();}
}
