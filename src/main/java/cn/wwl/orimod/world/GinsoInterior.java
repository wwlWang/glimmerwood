package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.Ability;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Independent, deterministic course overlay. Coordinates face the entrance (north) before rotation. */
public final class GinsoInterior {
    public static final byte NONE=0, WOOD=1, AIR=4, LIGHT=7, LAMP=8, FLOWER=9, WIND=10, WELL=11, STAIR=12, STAIR_SOUTH=13;
    public static final int VERSION=1;
    public record Section(String id,int floor,int top,int side,Ability required,BlockPos start,BlockPos goal) {}
    public static final List<Section> SECTIONS=List.of(
        new Section("wall",0,8,1,null,new BlockPos(3,1,0),new BlockPos(1,9,0)),
        new Section("bash",8,14,-1,Ability.BASH,new BlockPos(-3,9,0),new BlockPos(-1,15,0)),
        new Section("grapple",14,20,1,Ability.GRAPPLE,new BlockPos(3,15,0),new BlockPos(1,21,0)),
        new Section("wind",20,28,-1,Ability.GLIDE,new BlockPos(-3,21,0),new BlockPos(-1,29,0)),
        new Section("jump",28,31,1,null,new BlockPos(2,29,0),new BlockPos(1,32,0)));
    public static final List<BlockPos> WELLS=List.of(new BlockPos(0,15,3),new BlockPos(0,38,3));
    public static final BlockPos HEART=new BlockPos(0,38,0);
    public static final BlockPos LANTERN=new BlockPos(-3,13,0), GRAPPLE=new BlockPos(3,21,0), VENT=new BlockPos(-3,21,0);
    private final Map<BlockPos,Byte> cells=new LinkedHashMap<>();
    private static final GinsoInterior INSTANCE=new GinsoInterior();
    public static GinsoInterior layout(){return INSTANCE;}
    public byte cell(int x,int y,int z){return cells.getOrDefault(new BlockPos(x,y,z),NONE);}
    public Map<BlockPos,Byte> blocks(){return Collections.unmodifiableMap(cells);}
    public static BlockPos rotate(BlockPos p,int turns){return switch(Math.floorMod(turns,4)){
        case 1->new BlockPos(-p.getZ(),p.getY(),p.getX());case 2->new BlockPos(-p.getX(),p.getY(),-p.getZ());
        case 3->new BlockPos(p.getZ(),p.getY(),-p.getX());default->p;};}
    public static boolean opening(int x,int z,int side){return x*side>=2&&x*side<=4&&Math.abs(z)<=1;}
    public static Section section(double feetY){for(var s:SECTIONS)if(feetY<s.top+1)return s;return null;}
    public static String hint(Section s,int abilities){return "ginso_"+s.id+(s.required!=null&&(abilities&s.required.bit)==0?"_locked":"");}
    /** Lowest earthwork cost wins; deterministic north/east/south/west tie order. */
    public static int entrance(int base,int[][] heights){
        int best=0;long score=Long.MAX_VALUE;
        for(int t=0;t<4;t++){long n=0;for(int h:heights[t])n+=Math.abs(h-base)+2L*Math.max(0,h-base);
            if(n<score){best=t;score=n;}}
        return best;
    }
    /** Ground-level approach, at most one block per step. All columns share a saved noise endpoint. */
    public static int approachFloor(int z,int outerOffset){
        double t=Math.clamp((-z-8)/14.0,0,1);return (int)Math.round(t*Math.clamp(outerOffset,-6,10));
    }
    private void put(int x,int y,int z,byte c){cells.put(new BlockPos(x,y,z),c);}
    private void box(int x,int y,int z,int xx,int yy,int zz,byte c){for(int a=x;a<=xx;a++)for(int b=y;b<=yy;b++)for(int d=z;d<=zz;d++)put(a,b,d,c);}
    private GinsoInterior(){
        // A consistent clear shaft: every failed attempt lands on the immediately preceding floor.
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)if(x*x+z*z<=29){
            put(x,0,z,WOOD);for(int y=1;y<=42;y++)put(x,y,z,AIR);
        }
        for(var s:SECTIONS){
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)
                if(x*x+z*z<=29&&!opening(x,z,s.side))put(x,s.top,z,WOOD);
            // Real, broad climb face and catching rim. Holes alternate, so none line up vertically.
            box(s.side==1?5:-5,s.floor+1,-2,s.side==1?5:-5,s.top+2,2,WOOD);
            for(int z:new int[]{-3,3})put(4*s.side,s.floor+3,z,LIGHT);
            // Small daylight slots with a solid sill, beside (never in) the traversal face.
            box(-1,s.floor+3,-11,1,s.floor+5,-5,AIR);
            put(-2,s.floor+3,-5,LIGHT);put(2,s.floor+3,-5,LIGHT);
        }
        put(LANTERN.getX(),LANTERN.getY(),LANTERN.getZ(),LAMP);
        put(GRAPPLE.getX(),GRAPPLE.getY(),GRAPPLE.getZ(),FLOWER);
        put(VENT.getX(),VENT.getY(),VENT.getZ(),WIND);
        // A short final root stair reaches the open water-heart balcony at y38.
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)if(x*x+z*z<=29)put(x,37,z,WOOD);
        for(int i=0;i<12;i++)for(int width=0;width<2;width++){
            int x=i<8?-4+i:4-width,z=i<8?-3+width:-3+i-8,y=31+(i+1)/2;
            box(x,y+1,z,x,y+3,z,AIR);byte previous=cell(x,y,z);
            put(x,y,z,previous==STAIR||previous==STAIR_SOUTH?previous:i%2==0?WOOD:i<8?STAIR:STAIR_SOUTH);
        }
        // Balcony root rail (one block), open sky above. No cubic new canopy.
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)if(x*x+z*z>22&&x*x+z*z<=29 && !(x>=3&&z>=-3&&z<=1))put(x,38,z,WOOD);
        for(var p:WELLS)put(p.getX(),p.getY(),p.getZ(),WELL);
        for(int x:new int[]{-3,3})put(x,38,2,LIGHT);
        // Water-heart dais is deliberately empty until the escape checkpoint is accepted.
        put(0,37,0,LIGHT);
        // Flared, short root arch; outer approach grading is performed from persisted terrain data.
        box(-3,1,-10,3,7,-4,AIR);
        for(int z=-9;z<=-8;z++)for(int x=-4;x<=4;x++){
            int roof=7-x*x/5;put(x,roof,z,WOOD);if(Math.abs(x)==4)box(x,1,z,x,roof,z,WOOD);
        }
        put(-4,2,-8,LIGHT);put(4,2,-8,LIGHT);
    }
}
