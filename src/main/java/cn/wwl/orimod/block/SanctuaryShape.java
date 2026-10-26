package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.*;

/** Hand-authored coarse stone/root shapes, at most four boxes in each occupied cell. No mesh voxelization. */
public final class SanctuaryShape {
    private SanctuaryShape(){}
    public record Cell(BlockPos offset,List<AABB> boxes,VoxelShape collision,VoxelShape outline){}
    private record Key(SanctuaryKind kind,Direction facing){}
    private static final Map<Key,List<Cell>> CACHE=new HashMap<>();
    static {for(var kind:SanctuaryKind.values())for(var facing:Direction.Plane.HORIZONTAL)CACHE.put(new Key(kind,facing),build(kind,facing));}
    public static List<Cell> cells(SanctuaryKind kind,Direction facing){return CACHE.get(new Key(kind,facing));}
    public static Cell cell(SanctuaryKind kind,Direction facing,BlockPos offset){return cells(kind,facing).stream().filter(c->c.offset.equals(offset)).findFirst()
            .orElseGet(()->new Cell(offset,List.of(),Shapes.empty(),Shapes.empty()));}
    public static BlockPos rotate(BlockPos p,Direction facing){return switch(facing){
        case EAST->new BlockPos(-p.getZ(),p.getY(),p.getX());case SOUTH->new BlockPos(-p.getX(),p.getY(),-p.getZ());
        case WEST->new BlockPos(p.getZ(),p.getY(),-p.getX());default->p;};}
    public static BlockPos unrotate(BlockPos p,Direction facing){return rotate(p,switch(facing){case EAST->Direction.WEST;case WEST->Direction.EAST;default->facing;});}
    private static AABB rotate(AABB b,Direction facing){var a=rotatePoint(b.minX-.5,b.minZ-.5,facing);var z=rotatePoint(b.maxX-.5,b.maxZ-.5,facing);
        return new AABB(Math.min(a[0],z[0])+.5,b.minY,Math.min(a[1],z[1])+.5,Math.max(a[0],z[0])+.5,b.maxY,Math.max(a[1],z[1])+.5);}
    private static double[] rotatePoint(double x,double z,Direction f){return switch(f){case EAST->new double[]{-z,x};case SOUTH->new double[]{-x,-z};case WEST->new double[]{z,-x};default->new double[]{x,z};};}
    private static List<Cell> build(SanctuaryKind kind,Direction facing){
        var out=new ArrayList<Cell>();int r=kind.radius();
        for(int y=0;y<3;y++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            var local=new BlockPos(x,y,z);var world=rotate(local,facing);var boxes=coarse(kind,x,y,z).stream().map(b->rotate(b,facing)).toList();
            if(boxes.size()>4)throw new IllegalStateException("Too many sanctuary boxes: "+kind+local);
            VoxelShape collision=Shapes.empty();for(var b:boxes)collision=Shapes.or(collision,Shapes.create(b));
            VoxelShape outline=collision;
            // Light is selectable without filling air. The chart has a thin stone collision surface; its overhang stays visual.
            if(kind==SanctuaryKind.MAP && y==2)outline=Shapes.create(new AABB(0,0,0,1,.29,1));
            if(kind==SanctuaryKind.HEART && x==0&&z==0 && y>=1)outline=Shapes.or(outline,Shapes.create(new AABB(.05,0,.05,.95,y==2?.89:1,.95)));
            if((kind==SanctuaryKind.GLOOM||kind==SanctuaryKind.STORM)&&x==0&&z==0&&y>=1)outline=Shapes.or(outline,Shapes.create(new AABB(.06,0,.06,.94,y==2?.72:1,.94)));
            out.add(new Cell(world,List.copyOf(boxes),collision,outline));
        }
        out.sort(Comparator.comparingInt(c->c.offset.equals(BlockPos.ZERO)?0:1));return List.copyOf(out);
    }
    /** Boxes use model-centred X/Z, then clip into this canonical NORTH-facing cell. */
    private static List<AABB> coarse(SanctuaryKind kind,int x,int y,int z){
        var boxes=new ArrayList<AABB>();
        if(kind==SanctuaryKind.MAP){
            if(y==0){add(boxes,x,y,z,-.5,0,-.5,.5,.22,.5);add(boxes,x,y,z,-.295,.22,-.295,.295,1.59,.295);}
            if(y==1){add(boxes,x,y,z,-.295,1,-.295,.295,1.59,.295);add(boxes,x,y,z,-.43,1.56,-.43,.43,1.72,.43);}
            // A stable thin surface around the animated disc's mean height (2.02), not a full air cell.
            if(y>=1)add(boxes,x,y,z,-.5,1.97,-.5,.5,2.07,.5);
        }else if(kind.basin()){
            if(y==0){
                // Octagonal low apron: corners are cut back instead of a 3x3 invisible slab.
                double reach=x!=0&&z!=0?1.10:1.42;
                add(boxes,x,y,z,-reach,0,-reach,reach,.18,reach);
                if(x==0&&z==0)add(boxes,x,y,z,-.5,.18,-.5,.5,.96,.5);
                else {double rim=x!=0&&z!=0?.78:1.08;add(boxes,x,y,z,-rim,.65,-rim,rim,1.055,rim);}
                // The rear arches have feet at X +/-1.24, Z +.35 in NORTH orientation.
                if(x!=0&&z>=0)add(boxes,x,y,z,x<0?-1.49:.87,.12,.10,x<0?-.87:1.49,1,.88);
            }else if(y==1){
                // Basin lip slightly crosses the next layer; above it the front stays open.
                double rim=x!=0&&z!=0?.78:1.08;add(boxes,x,y,z,-rim,1,-rim,rim,1.055,rim);
                for(int side:new int[]{-1,1})add(boxes,x,y,z,side<0?-1.19:.57,1,.48,side<0?-.57:1.19,2,.90);
            }else{
                add(boxes,x,y,z,-.88,2,.52,-.20,2.42,.88);add(boxes,x,y,z,.20,2,.52,.88,2.42,.88);
                add(boxes,x,y,z,-.44,2.42,.57,.44,2.80,.79);
            }
        }else if(kind==SanctuaryKind.GLOOM||kind==SanctuaryKind.STORM){
            if(y==0){
                double reach=x!=0&&z!=0?.88:1.24;add(boxes,x,y,z,-reach,0,-reach,reach,.22,reach);
                add(boxes,x,y,z,-.91,.22,-.91,.91,.42,.91);
                add(boxes,x,y,z,-.64,.42,-.64,.64,.88,.64);
                add(boxes,x,y,z,-.79,.88,-.79,.79,1.02,.79);
            }else if(y==1){
                // Four swept prongs remain physically present, with clear air between them.
                for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1})
                    add(boxes,x,y,z,sx<0?-.70:.42,1,sz<0?-.70:.42,sx<0?-.42:.70,1.55,sz<0?-.42:.70);
            }
        }else{
            // Great heart: five coarse root ribs and a low woven base, not a solid crystal bounding box.
            // Endpoints are authored approximations of the cage, not sampled mesh triangles.
            double[][] roots={{1.10,.34,.63,.42,.28,.25},{.02,1.15,-.18,.79,-.18,.30},
                    {-1.09,.37,-.81,.04,-.34,-.04},{-.69,-.92,-.23,-.78,-.04,-.34},{.66,-.94,.68,-.45,.28,-.18}};
            if(y==0){
                // Open woven ring, not a plate across the visible hollow centre.
                // Eight coarse rim segments clip to at most three per outer cell (+ one root foot).
                for(int side:new int[]{-1,1}){
                    add(boxes,x,y,z,side<0?-.92:.64,.13,-.5,side<0?-.64:.92,.38,.5);
                    add(boxes,x,y,z,-.5,.13,side<0?-.92:.64,.5,.38,side<0?-.64:.92);
                    for(int other:new int[]{-1,1})add(boxes,x,y,z,side<0?-.67:.45,.13,other<0?-.67:.45,
                            side<0?-.45:.67,.38,other<0?-.45:.67);
                }
            }
            if(y==2){
                add(boxes,x,y,z,-.27,2,.16,.37,2.75,.39);
                add(boxes,x,y,z,-.43,2,-.13,-.25,2.75,.05);
                add(boxes,x,y,z,-.13,2,-.43,.05,2.75,-.25);
                add(boxes,x,y,z,.19,2,-.27,.37,2.75,-.09);
            }
            for(var root:roots){
                if(y==2)break;
                double ax=y==0?root[0]:y==1?root[2]:root[4],az=y==0?root[1]:y==1?root[3]:root[5];
                double w=y==0?.16:y==1?.13:.09;
                add(boxes,x,y,z,ax-w,y==0?0:y,az-w,ax+w,y==2?2.75:y+1,az+w);
            }
        }
        return boxes;
    }
    private static void add(List<AABB> boxes,int x,int y,int z,double x0,double y0,double z0,double x1,double y1,double z1){
        double a=Math.max(x0+.5,x)-x,b=Math.max(y0,y)-y,c=Math.max(z0+.5,z)-z;
        double d=Math.min(x1+.5,x+1)-x,e=Math.min(y1,y+1)-y,f=Math.min(z1+.5,z+1)-z;
        if(d-a>1e-6&&e-b>1e-6&&f-c>1e-6)boxes.add(new AABB(a,b,c,d,e,f));
    }
}
