package cn.wwl.orimod.world;

import java.util.*;
import static cn.wwl.orimod.world.GreatTreeInterior.*;

/** Version-two architecture. The accepted small-tree layout remains byte-for-byte independent. */
public final class GreatTreeScaleInterior {
    public static final byte WIND=16, RAIL=17, VENT=18, OCULUS=19;
    public static final int LOOKOUT_Y=218, MIN_X=-20, MAX_X=22, MIN_Z=-27, MAX_Z=22, MIN_Y=-9, MAX_Y=224;
    public static final Point ENTRY=new Point(0,0,-24), LOOKOUT=new Point(4,LOOKOUT_Y,0), WIND_BASE=new Point(4,25,3);
    private static final GreatTreeScaleInterior INSTANCE=new GreatTreeScaleInterior();
    private final Map<Point,Byte> blocks=new LinkedHashMap<>();
    private final byte[] cells=new byte[(MAX_X-MIN_X+1)*(MAX_Z-MIN_Z+1)*(MAX_Y-MIN_Y+1)];
    private final List<Point> upper=new ArrayList<>();
    public static GreatTreeScaleInterior layout(){return INSTANCE;}
    public Map<Point,Byte> blocks(){return Collections.unmodifiableMap(blocks);}
    public List<Point> upper(){return List.copyOf(upper);}
    public byte cell(int x,int y,int z){return x<MIN_X||x>MAX_X||z<MIN_Z||z>MAX_Z||y<MIN_Y||y>MAX_Y?NONE:cells[index(x,y,z)];}
    private static int index(int x,int y,int z){return ((x-MIN_X)*(MAX_Z-MIN_Z+1)+z-MIN_Z)*(MAX_Y-MIN_Y+1)+y-MIN_Y;}
    private void put(int x,int y,int z,byte c){cells[index(x,y,z)]=c;blocks.put(new Point(x,y,z),c);}
    private void box(int x,int y,int z,int ex,int ey,int ez,byte c){for(int a=x;a<=ex;a++)for(int b=z;b<=ez;b++)for(int h=y;h<=ey;h++)put(a,h,b,c);}
    private GreatTreeScaleInterior(){
        var old=GreatTreeInterior.layout();old.blocks().forEach((p,c)->put(p.x(),p.y(),p.z(),c));
        // The old three-turn lower route, heart and root-cave receipts keep their coordinates.
        // Widen its foyer and hall; restore the treads/headroom after carving the rooms.
        for(int x=-8;x<=10;x++)for(int z=-9;z<=9;z++)if(Math.hypot(x-1,z)<=8.7){
            put(x,-1,z,INNER);box(x,0,z,x,12,z,AIR);
        }
        // A tall tapered root arch, with a flared open-air approach rather than a flat lintel.
        box(-9,0,-27,9,19,-7,AIR);
        for(int z=-24;z<=-7;z++)for(int x=-6;x<=6;x++)put(x,-1,z,INNER);
        for(int z=-17;z<=-15;z++)for(int x=-9;x<=9;x++){
            int roof=3+(int)Math.round(12*Math.sqrt(Math.max(0,1-x*x/81.0)));
            box(x,roof,z,x,roof+1,z,INNER);
            if(Math.abs(x)>=8)box(x,0,z,x,roof,z,INNER);
        }
        for(int x:new int[]{-7,7}){put(x,3,-16,KNOT);put(x,1,-10,KNOT);}
        // Broad 19-block vault with the existing root-caged crystal on its raised dais.
        for(int x=-8;x<=12;x++)for(int z=-9;z<=11;z++){
            double d=Math.hypot(x-2,z-1);if(d>9.6)continue;
            int roof=31+(int)Math.round(12*Math.sqrt(Math.max(0,1-d*d/92.16)));
            put(x,23,z,INNER);box(x,24,z,x,roof-1,z,AIR);put(x,roof,z,INNER);
            if(d>8.6)box(x,24,z,x,roof,z,INNER);
        }
        for(int t=-6;t<=6;t++){put(2+t,23,1,FINE_VEIN);put(2,23,1+t,FINE_VEIN);}
        for(int[] p:new int[][]{{-4,27,5},{8,27,-3},{2,29,8},{-5,28,-4}})put(p[0],p[1],p[2],KNOT);
        old.blocks().forEach((p,c)->{if(c>=NORTH&&c<=WEST)put(p.x(),p.y(),p.z(),c);});
        for(int i=0;i<old.ascent().size();i++){
            var p=old.ascent().get(i);int leg=(i/6)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            for(int w=0;w<2;w++){
                int x=p.x()-dz*w,z=p.z()+dx*w;
                put(x,p.y()-1,z,old.cell(x,p.y()-1,z));box(x,p.y(),z,x,p.y()+2,z,AIR);
            }
            if(i%6==2)put(p.x()+dz,p.y()+1,p.z()-dx,KNOT);
        }
        for(int x=1;x<=3;x++)for(int z=0;z<=2;z++)put(x,24,z,INNER);
        put(2,24,-1,SOUTH);put(HEART_POS.x(),HEART_POS.y(),HEART_POS.z(),HEART);
        for(int turn=0;turn<3;turn++){
            int y=turn*8;
            box(0,y+2,-20,2,y+4,-3,AIR);box(6,y+4,-1,22,y+6,1,AIR);box(1,y+6,5,3,y+8,20,AIR);
        }
        box(-20,28,0,-7,33,2,AIR);box(11,28,0,22,33,2,AIR);
        // Three daylight slits run above the vault; they do not cut the stair treads.
        for(int[] p:new int[][]{{2,1},{-1,-2},{5,4}})box(p[0],43,p[1],p[0],MAX_Y,p[1],AIR);
        // The continuous windway is separate from a two-wide stair. Landing pauses occur at every corner.
        for(int y=44;y<=LOOKOUT_Y+3;y++)for(int x=-5;x<=9;x++)for(int z=-6;z<=8;z++)
            if(Math.hypot(x-2,z-1)<8.8)put(x,y,z,AIR);
        int[][] corners={{-4,-5},{8,-5},{8,7},{-4,7}};
        int floor=23,index=0;
        outer:for(int turn=0;turn<12;turn++)for(int leg=0;leg<4;leg++){
            int[] a=corners[leg],b=corners[(leg+1)%4];int dx=Integer.signum(b[0]-a[0]),dz=Integer.signum(b[1]-a[1]);
            byte stair=dx>0?EAST:dx<0?WEST:dz>0?SOUTH:NORTH;
            for(int step=0;step<12;step++,index++){
                boolean rise=step>=3&&step%2==1;if(rise)floor++;
                int x=a[0]+dx*step,z=a[1]+dz*step;
                for(int w=0;w<2;w++){
                    int px=x-dz*w,pz=z+dx*w;byte previous=cell(px,floor,pz);
                    put(px,floor,pz,previous>=NORTH&&previous<=WEST?previous:rise?stair:INNER);
                    box(px,floor+1,pz,px,floor+3,pz,AIR);
                }
                // Inner rail prevents accidental falls onto the hall; never occupies a tread.
                if(step>=2&&step<=9){put(x-dz*2,floor,z+dx*2,INNER);put(x-dz*2,floor+1,z+dx*2,RAIL);}
                if(index%6==2)put(x+dz,floor+2,z-dx,KNOT);
                upper.add(new Point(x,floor+1,z));
                if(step==6&&leg<3){
                    int y=floor+3;
                    if(leg==0)box(x-1,y,-20,x+1,y+2,-6,AIR);
                    else if(leg==1)box(9,y,z-1,22,y+2,z+1,AIR);
                    else box(x-1,y,8,x+1,y+2,20,AIR);
                }
                if(floor==LOOKOUT_Y-1)break outer;
            }
        }
        var topTreads=new LinkedHashMap<Point,Byte>();
        for(int i=upper.size()-16;i<upper.size();i++){
            var p=upper.get(i);int leg=(i/12)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            for(int w=0;w<2;w++){var q=p.add(-dz*w,-1,dx*w);topTreads.put(q,cell(q.x(),q.y(),q.z()));}
        }
        // A small open-air root-railed platform above the top lobes. The stair remains the descent route.
        box(-5,LOOKOUT_Y,-6,9,MAX_Y,8,AIR);
        box(-5,LOOKOUT_Y-1,-6,9,LOOKOUT_Y-1,8,INNER);
        for(int x=-5;x<=9;x++){put(x,LOOKOUT_Y,-6,RAIL);put(x,LOOKOUT_Y,8,RAIL);}
        for(int z=-6;z<=8;z++){put(-5,LOOKOUT_Y,z,RAIL);put(9,LOOKOUT_Y,z,RAIL);}
        for(int[] p:new int[][]{{-5,-6},{9,-6},{-5,8},{9,8}})put(p[0],LOOKOUT_Y-1,p[1],KNOT);
        topTreads.forEach((p,c)->put(p.x(),p.y(),p.z(),c));
        // Preserve the final stairwell through the lookout floor.
        for(int i=upper.size()-16;i<upper.size();i++){
            var p=upper.get(i);int leg=(i/12)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            for(int w=0;w<2;w++)box(p.x()-dz*w,p.y(),p.z()+dx*w,p.x()-dz*w,p.y()+2,p.z()+dx*w,AIR);
        }
        // Not a tall-stack of collision plates: invisible wind cells use the existing glide lift rules.
        box(3,25,2,5,LOOKOUT_Y-1,4,WIND);box(3,24,2,5,24,4,VENT);
        box(3,LOOKOUT_Y,2,5,LOOKOUT_Y+2,4,AIR);
        for(int x=2;x<=6;x++){put(x,LOOKOUT_Y,1,RAIL);put(x,LOOKOUT_Y,5,RAIL);}
        for(int z=2;z<=4;z++){put(2,LOOKOUT_Y,z,RAIL);put(6,LOOKOUT_Y,z,RAIL);}
        // Glazed oculi carry direct sky all the way down to the vault without holes to fall through.
        // Shift the central oculus one block west so even the wind-mouth corner rail misses it.
        for(int[] p:new int[][]{{1,1},{-1,-2},{5,-2}}){
            box(p[0],31,p[1],p[0],MAX_Y,p[1],AIR);put(p[0],LOOKOUT_Y-1,p[1],OCULUS);
        }
        // The legacy root stair and receipt are kept intact, including their ceiling/headroom.
        old.blocks().forEach((p,c)->{if(p.y()<0)put(p.x(),p.y(),p.z(),c);});
        put(REWARD_POS.x(),REWARD_POS.y(),REWARD_POS.z(),REWARD);
        // Install lamps last: daylight tunnels must not erase the nearest light at a bend.
        for(int i=2;i<old.ascent().size();i+=6){
            var p=old.ascent().get(i);int leg=(i/6)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            put(p.x()+dz,p.y()+1,p.z()-dx,KNOT);
        }
        for(int i=2;i<upper.size();i+=6){
            var p=upper.get(i);int leg=(i/12)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            put(p.x()+dz,p.y()+1,p.z()-dx,KNOT);
        }
        protectWalkingEdges();
    }

    /** Guard the whole two-wide walking surface, including both sides of every corner.
     * Supports continue down to an existing solid cell; they are not floating fence posts.
     * Reserve all three blocks of route headroom before adding anything, so a guard cannot
     * close the next flight's entrance. Level hall / lookout floors remain open. */
    private void protectWalkingEdges(){
        var decks=new LinkedHashSet<Point>();
        for(var route:List.of(GreatTreeInterior.layout().ascent(),upper)){
            int legLength=route==upper?12:6;
            for(int i=0;i<route.size();i++){
                var p=route.get(i);int leg=i/legLength%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
                decks.add(p);decks.add(p.add(-dz,0,dx));
            }
        }
        var headroom=new HashSet<Point>();
        for(var p:decks)for(int h=0;h<3;h++)headroom.add(p.add(0,h,0));
        // Connections through the foyer, heart hall and lookout are level walking surfaces too.
        for(int y:new int[]{0,24,LOOKOUT_Y})for(int x=MIN_X;x<=MAX_X;x++)for(int z=MIN_Z;z<=MAX_Z;z++){
            var p=new Point(x,y,z);
            if(solid(p.add(0,-1,0))&&cell(x,y,z)==AIR&&cell(x,y+1,z)==AIR)
                for(int h=0;h<3;h++)headroom.add(p.add(0,h,0));
        }
        // Back the outer edge right up to the tread, including beneath lamp/window openings.
        for(var route:List.of(GreatTreeInterior.layout().ascent(),upper))for(int i=0;i<route.size();i++){
            var p=route.get(i);int leg=i/(route==upper?12:6)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            var q=p.add(dz,0,-dx);support(q,headroom);
            if(!service(q)&&!headroom.contains(q)&&!headroom.contains(q.add(0,1,0))&&!solid(q)&&!solid(q.add(0,1,0)))put(q.x(),q.y(),q.z(),RAIL);
        }
        for(var p:decks)for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            var q=p.add(d[0],0,d[1]);
            if(service(q)||headroom.contains(q)||headroom.contains(q.add(0,1,0))||solid(q)||solid(q.add(0,1,0)))continue;
            boolean supported=false;
            for(int drop=1;drop<=4;drop++)if(solid(q.add(0,-drop,0))){supported=true;break;}
            if(supported)continue;
            support(q,headroom);
            put(q.x(),q.y(),q.z(),RAIL);
        }
    }
    private void support(Point q,Set<Point> headroom){
        for(int y=q.y()-1;y>=MIN_Y&&!solid(new Point(q.x(),y,q.z()));y--){
            if(service(new Point(q.x(),y,q.z()))||headroom.contains(new Point(q.x(),y,q.z())))break;
            put(q.x(),y,q.z(),INNER);
        }
    }
    private boolean service(Point p){byte c=cell(p.x(),p.y(),p.z());return c==WIND||c==VENT||c==OCULUS
        ||p.y()>=31&&(p.x()==1&&p.z()==1||p.x()==-1&&p.z()==-2||p.x()==5&&p.z()==-2);}
    private boolean solid(Point p){byte c=cell(p.x(),p.y(),p.z());return c!=NONE&&c!=AIR&&c!=WIND;}
}
