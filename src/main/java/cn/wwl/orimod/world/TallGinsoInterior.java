package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.Ability;
import net.minecraft.core.BlockPos;
import java.util.*;
import static cn.wwl.orimod.world.GinsoInterior.*;

/** Course v2: short ability hops inside a tall tree; enclosed, deliberately entered descent lane. */
public final class TallGinsoInterior {
    public static final byte CUSHION=14, RAIL=15, DOOR=16, DOOR_TOP=17, WINDOW=18, STEP=19;
    public static final int HEART_FLOOR=93;
    private static final GinsoCourses.Layout INSTANCE=new TallGinsoInterior().build();
    private final Map<BlockPos,Byte> blocks=new LinkedHashMap<>();
    private final List<GinsoCourses.Hop> hops=new ArrayList<>();
    private final List<BlockPos> retreat=new ArrayList<>();
    private final List<Section> sections=new ArrayList<>();
    public static GinsoCourses.Layout layout(){return INSTANCE;}
    private void put(int x,int y,int z,byte c){blocks.put(new BlockPos(x,y,z),c);}
    private void box(int x,int y,int z,int xx,int yy,int zz,byte c){for(int a=x;a<=xx;a++)for(int b=y;b<=yy;b++)for(int d=z;d<=zz;d++)put(a,b,d,c);}
    private byte cell(int x,int y,int z){return blocks.getOrDefault(new BlockPos(x,y,z),(byte)0);}
    public static boolean shaft(int x,int z,int side){return x*side>=2&&x*side<=4&&Math.abs(z)<=1;}
    private void section(String id,int[] floors,Ability ability,byte kind){
        int first=hops.size();
        for(int i=1;i<floors.length;i++){
            int side=(hops.size()%2==0?1:-1),bottom=floors[i-1],top=floors[i];
            var from=new BlockPos(3*side,bottom+1,0);var goal=new BlockPos(0,top+1,0);
            BlockPos target=switch(kind){case LAMP->new BlockPos(4*side,bottom+5,0);
                case FLOWER->new BlockPos(2*side,top+1,0);case WIND->from;default->null;};
            hops.add(new GinsoCourses.Hop(id,bottom,top,side,from,goal,target,kind));
        }
        var firstHop=hops.get(first);sections.add(new Section(id,floors[0],floors[floors.length-1],firstHop.side(),ability,firstHop.start(),hops.getLast().goal()));
    }
    private GinsoCourses.Layout build(){
        section("wall",new int[]{4,12,20},null,WOOD);
        section("bash",new int[]{20,26,32,38},Ability.BASH,LAMP);
        section("grapple",new int[]{38,44,50,56},Ability.GRAPPLE,FLOWER);
        section("wind",new int[]{56,64,72,78},Ability.GLIDE,WIND);
        section("jump",new int[]{78,81,84,87,90,93},null,AIR);
        // An inner root lining connects to the actual bark via radial root ribs. It also
        // seals the wandering gap between the old straight course and the v3 hollow.
        box(-5,4,-5,5,98,5,WOOD);box(-4,5,-4,4,98,4,AIR);
        box(-4,4,-4,4,4,4,CUSHION);
        for(var h:hops){
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(!shaft(x,z,h.side()))put(x,h.top(),z,CUSHION);
            // Guard the completed floor from its incoming shaft. An airborne arrival
            // crosses the low root rail; walking on the landing cannot enter the hole.
            if(!h.section().equals("jump"))guard(h.side(),h.top());
            if(h.kind()==FLOWER){
                // The flower delivers the player through a two-wide landing mouth,
                // not into a fence that needs an unrelated jump/mantle to clear.
                put(h.side(),h.top()+1,0,AIR);put(h.side(),h.top()+1,1,AIR);
            }
            if(h.section().equals("jump"))for(int z=-1;z<=1;z++)put(h.side(),h.top(),z,STEP);
            // Continuous solid wall behind both the climbing face and supported anchors.
            box(5*h.side(),h.floor()+1,-2,5*h.side(),h.top()+3,2,WOOD);
            for(int y=h.floor()+2;y<=h.top();y+=4)for(int z:new int[]{-3,3})put(4*h.side(),y,z,LIGHT);
            if(h.interaction()!=null){var p=h.interaction();put(p.getX(),p.getY(),p.getZ(),h.kind());}
            // A lateral root holds the flower beside the arrival mouth. Putting the
            // root directly behind it made a rising player's head clip its underside
            // at slightly off-centre starts, cancelling the grapple before arrival.
            if(h.kind()==FLOWER){
                int ceiling=hops.stream().filter(next->next.floor()==h.top()).findFirst().orElseThrow().top();
                // Continue into the next root deck: a free-standing low shelf here would
                // let someone step around the arrival guard into the previous lesson.
                for(int x=2;x<=5;x++)box(x*h.side(),h.top()+1,-1,x*h.side(),ceiling,-1,WOOD);
            }
            // Root ribs reach the v3 wall even where its centre bends away from the course.
            for(int side:new int[]{-1,1})box(side<0?-10:5,h.floor(),-2,side<0?-5:10,h.floor(),2,WOOD);
            // Glass in deep bark openings gives daylight without a fall-through window.
            box(-1,h.floor()+2,5,1,h.floor()+3,11,AIR);
            box(-1,h.floor()+2,5,1,h.floor()+3,5,WINDOW);
        }
        // Retreat is separated by a full wall and closed doors. Alternating holes have
        // an immune catch six blocks below, so no open column runs down the whole tower.
        box(-4,4,-9,4,98,-5,WOOD);box(-3,5,-8,3,97,-6,AIR);
        var floors=new TreeSet<Integer>();floors.add(4);for(var h:hops)floors.add(h.top());
        int parity=0;
        for(int floor:floors){
            int gap=(parity++%2==0?-2:2);
            for(int x=-3;x<=3;x++)for(int z=-8;z<=-6;z++)if(floor==4||x!=gap)put(x,floor,z,CUSHION);
            // A wall niche at every landing makes returning a conscious use of the door.
            put(0,floor+1,-5,DOOR);put(0,floor+2,-5,DOOR_TOP);
            put(0,floor,-6,CUSHION);put(0,floor+2,-8,LIGHT);
            retreat.add(new BlockPos(gap,floor+1,-7));
        }
        // The exit of the last descent landing rejoins the low, ordinary root stair.
        box(-3,1,-14,3,8,-4,AIR);
        for(int i=0;i<8;i++)for(int x=-1;x<=1;x++){
            int y=(i+1)/2,z=-12+i;put(x,y,z,i%2==0?WOOD:STAIR_SOUTH);
            for(int yy=y+1;yy<=y+3;yy++)put(x,yy,z,AIR);
        }
        box(-4,4,-4,4,4,-3,CUSHION);
        // The graded foyer must not erase the final descent catches beside its stair.
        for(int x:new int[]{-3,-2,2,3})for(int z=-8;z<=-6;z++)put(x,4,z,CUSHION);
        // The low foyer has a deliberate door too: an accidental step from the first
        // lesson cannot fall back down the approach stair.
        box(-3,5,-4,3,7,-4,WOOD);put(0,5,-4,DOOR);put(0,6,-4,DOOR_TOP);
        // The wind exit is a gate at floor height. Its short next lesson has no room
        // to fly over a 1.5-high fence; opening the gate leaves a full player-height exit.
        put(1,79,0,DOOR);put(1,80,0,DOOR_TOP);
        // Close the top around the water-heart dais, leaving the future fork escape above.
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(Math.abs(x)==4||Math.abs(z)==4)put(x,94,z,RAIL);
        // The closed retreat door is the guard here. Leave its supported approach
        // reachable from the top deck instead of fencing off the door itself.
        put(0,94,-4,AIR);
        // Bind before the last grapple. Its open arrival mouth catches at y=51;
        // the following wind lesson may fall back to this well, never below it.
        var wells=List.of(new BlockPos(0,21,3),new BlockPos(0,51,3),new BlockPos(0,94,3));
        for(var well:wells){put(well.getX(),well.getY(),well.getZ(),WELL);put(0,well.getY()-1,3,WOOD);}
        put(0,93,0,LIGHT);
        return new GinsoCourses.Layout(blocks,sections,wells,new BlockPos(0,94,0),hops,retreat);
    }
    private void guard(int side,int floor){
        for(int z=-2;z<=2;z++)put(side,floor+1,z,RAIL);
        for(int x=2;x<=4;x++)for(int z:new int[]{-2,2})put(x*side,floor+1,z,RAIL);
    }
    private TallGinsoInterior(){}
}
