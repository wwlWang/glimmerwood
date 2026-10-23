package cn.wwl.orimod.world;

import java.util.*;

/** Pure architectural overlay. Exterior sculpture stays independently reproducible. */
public final class GreatTreeInterior {
    public static final byte NONE=0, WOOD=1, AIR=4, VEIN=5, HEART=6, REWARD=7,
            NORTH=8, EAST=9, SOUTH=10, WEST=11, INNER=12, FINE_VEIN=13, KNOT=14;
    public record Point(int x,int y,int z) {
        public Point add(int dx,int dy,int dz){return new Point(x+dx,y+dy,z+dz);}
    }
    public static final Point HEART_POS=new Point(2,25,1), REWARD_POS=new Point(-1,-8,3);
    private final Map<Point,Byte> blocks=new LinkedHashMap<>();
    private final byte[] cells=new byte[25*35*92];
    private final List<Point> ascent=new ArrayList<>(),descent=new ArrayList<>();
    private static final GreatTreeInterior INSTANCE=new GreatTreeInterior();
    public static GreatTreeInterior layout(){return INSTANCE;}
    public byte cell(int x,int y,int z){return x< -12||x>12||z< -22||z>12||y< -9||y>82?NONE:cells[((x+12)*35+z+22)*92+y+9];}
    public Map<Point,Byte> blocks(){return Collections.unmodifiableMap(blocks);}
    public List<Point> ascent(){return List.copyOf(ascent);}
    public List<Point> descent(){return List.copyOf(descent);}
    private void put(int x,int y,int z,byte cell){blocks.put(new Point(x,y,z),cell);cells[((x+12)*35+z+22)*92+y+9]=cell;}
    private void box(int x1,int y1,int z1,int x2,int y2,int z2,byte c){
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)for(int y=y1;y<=y2;y++)put(x,y,z,c);
    }
    private void room(int cx,int cz,double r,int floor,int ceiling){
        for(int x=(int)Math.floor(cx-r);x<=cx+r;x++)for(int z=(int)Math.floor(cz-r);z<=cz+r;z++)
            if((x-cx)*(x-cx)+(z-cz)*(z-cz)<=r*r){
                put(x,floor,z,INNER);
                for(int y=floor+1;y<=ceiling;y++)put(x,y,z,AIR);
            }
    }
    private GreatTreeInterior(){
        room(1,0,5.1,-1,9);
        // A short, flared root arch: the outer half is open sky, not a roofed tunnel.
        for(int z=-21;z<=-2;z++)for(int x=-5;x<=5;x++){
            int width=z< -8?5:3;
            if(Math.abs(x)>width)continue;
            int top=z< -9?14:7-Math.abs(x)/2;
            box(x,0,z,x,top,z,AIR);
            if(z>=-14)put(x,-1,z,INNER);
        }
        for(int z=-8;z<=-7;z++)for(int x=-5;x<=5;x++){
            int roof=8-(x*x)/7;
            put(x,roof,z,INNER);
            if(Math.abs(x)==5)box(x,0,z,x,roof,z,INNER);
        }
        for(int x:new int[]{-4,4})put(x,2,-8,KNOT);
        // A two-wide, three-turn staircase: one stair then two level treads. No ability gate.
        int[][] corners={{-1,-2},{5,-2},{5,4},{-1,4}};
        int floor=-1,index=0;
        for(int turn=0;turn<3;turn++)for(int leg=0;leg<4;leg++){
            int[] a=corners[leg],b=corners[(leg+1)%4];int dx=Integer.signum(b[0]-a[0]),dz=Integer.signum(b[1]-a[1]);
            int ix=-dz,iz=dx;byte stair=dx>0?EAST:dx<0?WEST:dz>0?SOUTH:NORTH;
            for(int step=0;step<6;step++,index++){
                int x=a[0]+dx*step,z=a[1]+dz*step;boolean rises=index%3==2;
                if(rises)floor++;
                for(int width=0;width<2;width++){
                    int px=x+ix*width,pz=z+iz*width;
                    box(px,floor+1,pz,px,floor+3,pz,AIR);
                    byte existing=cell(px,floor,pz);
                    // Inner corner overlaps the incoming leg: retain its stair rather than
                    // replacing the last riser with a full block (which would require jumping).
                    put(px,floor,pz,existing>=NORTH&&existing<=WEST?existing:rises?stair:INNER);
                }
                if(step==2)put(x-ix,floor+2,z-iz,KNOT);
                ascent.add(new Point(x,floor+1,z));
            }
        }
        var risers=new LinkedHashMap<Point,Byte>();
        blocks.forEach((p,c)->{if(c>=NORTH&&c<=WEST)risers.put(p,c);});
        room(2,1,4.9,23,32);
        // A vaulted, slightly asymmetric crown of roots, not a flat wooden box.
        for(int x=-3;x<=7;x++)for(int z=-4;z<=6;z++){
            double d=Math.hypot(x-2,z-1);
            if(d>5.5)continue;
            int roof=29+(int)Math.round(6*Math.sqrt(Math.max(0,1-d*d/31)))+((x+z)%3==0?1:0);
            box(x,24,z,x,roof-1,z,AIR);put(x,roof,z,INNER);
            if(d>4.9)box(x,24,z,x,roof,z,INNER);
        }
        // Sparse fine lines converge only on the hall's floor.
        for(int t=-4;t<=4;t++){put(2+t,23,1,FINE_VEIN);put(2,23,1+t,FINE_VEIN);}
        risers.forEach((p,c)->put(p.x,p.y,p.z,c));
        // The hall slab must leave the last rising leg's stairwell open, including full headroom.
        for(int i=0;i<ascent.size();i++){
            var p=ascent.get(i);int leg=(i/6)%4;int dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            for(int width=0;width<2;width++)box(p.x-dz*width,p.y,p.z+dx*width,p.x-dz*width,p.y+2,p.z+dx*width,AIR);
        }
        // Broad raised root dais, with a normal stair at its front lip.
        for(int x=1;x<=3;x++)for(int z=0;z<=2;z++)put(x,24,z,INNER);
        put(2,24,-1,SOUTH);put(HEART_POS.x,HEART_POS.y,HEART_POS.z,HEART);
        // Three clear daylight apertures per turn, facing three actual stair legs.
        // The sill stays above the feet; window tunnels reach beyond the thick trunk.
        for(int turn=0;turn<3;turn++){
            int y=turn*8;
            box(0,y+2,-12,2,y+4,-3,AIR);
            box(6,y+4,-1,12,y+6,1,AIR);
            box(1,y+6,5,3,y+8,12,AIR);
        }
        box(-12,27,0,-3,30,2,AIR);box(7,27,0,12,30,2,AIR);
        // Three slender shafts pierce the roof AND the crown, so sunlight reaches the dais.
        box(1,33,0,3,82,2,AIR);
        box(-1,31,-2,0,82,-1,AIR);box(4,31,3,5,82,4,AIR);
        for(int[] p:new int[][]{{-2,26,3},{6,26,-1},{2,28,5}})put(p[0],p[1],p[2],KNOT);
        // Below the foyer: a small illuminated chamber and a reversible descending stair.
        // Root lining keeps natural caves/water from opening into the reward room.
        for(int x=-7;x<=5;x++)for(int z=-3;z<=8;z++){
            double d=Math.hypot(x+1,z-3);
            if(d<=5.2){put(x,-9,z,INNER);put(x,-2,z,INNER);}
            if(d>4.2&&d<=5.2)box(x,-9,z,x,-3,z,INNER);
        }
        room(-1,3,4.2,-9,-3);
        box(-5,0,-8,-3,3,-5,AIR);box(-5,-1,-8,-3,-1,-7,INNER);
        for(int i=0;i<10;i++){
            int z=-7+i,y=i==0?0:1-i;
            for(int x=-5;x<=-4;x++){
                box(x,y,z,x,y+3,z,AIR);put(x,y-1,z,i==0?INNER:NORTH);
            }
            descent.add(new Point(-4,y,z));
            if(i%3==1)put(-6,y+1,z,KNOT);
        }
        put(2,-6,5,KNOT);put(-3,-6,6,KNOT);
        // Line only exposed inner walls. Outside bark and the approved root-to-crown veins stay intact.
        var air=blocks.entrySet().stream().filter(e->e.getValue()==AIR).map(Map.Entry::getKey).toList();
        for(var p:air)if(p.y>=0&&p.y<=35&&p.z>=-7)for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
            var q=p.add(d[0],0,d[1]);
            if(Math.abs(q.x)>8||q.z>8||q.z< -8)continue;
            if(cell(q.x,q.y,q.z)==NONE)put(q.x,q.y,q.z,INNER);
        }
        // Lighting is installed after windows/headroom carving so none of the knots gets erased.
        for(int i=2;i<ascent.size();i+=6){
            var p=ascent.get(i);int leg=(i/6)%4,dx=leg==0?1:leg==2?-1:0,dz=leg==1?1:leg==3?-1:0;
            put(p.x+dz,p.y+1,p.z-dx,KNOT);
        }
        put(REWARD_POS.x,REWARD_POS.y,REWARD_POS.z,REWARD);
    }
}
