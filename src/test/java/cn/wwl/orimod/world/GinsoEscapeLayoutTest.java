package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.wwl.orimod.world.GinsoInterior.*;
import static cn.wwl.orimod.world.TallGinsoInterior.*;
import static org.junit.jupiter.api.Assertions.*;

class GinsoEscapeLayoutTest {
    private static byte cell(GinsoShape shape,GinsoEscapeLayout.Layout e,int turn,int x,int y,int z){
        byte c=e.cell(x,y,z);if(c!=0)return c;
        c=GinsoCourses.layout(2).cell(x,y,z);if(c!=0)return c;
        var q=GinsoInterior.rotate(new BlockPos(x,y,z),turn);return shape.cell(q.getX(),q.getY(),q.getZ());
    }
    private static double collision(byte c){return switch(c){
        case 0,AIR,GinsoShape.VINE,FLOWER,LAMP ->0;
        case STEP->.5;case RAIL,GinsoEscapeLayout.ROOT_LIP->1.5;case WIND->.1875;default->1;
    };}
    private static boolean clear(GinsoShape shape,GinsoEscapeLayout.Layout e,int turn,double x,double feet,double z,double body){
        for(int xx=(int)Math.floor(x-.3+1e-6);xx<=(int)Math.floor(x+.3-1e-6);xx++)
            for(int zz=(int)Math.floor(z-.3+1e-6);zz<=(int)Math.floor(z+.3-1e-6);zz++)
                for(int y=(int)Math.floor(feet)-1;y<feet+body-1e-6;y++){
                    double h=collision(cell(shape,e,turn,xx,y,zz));
                    if(h>0&&y+h>feet+1e-6)return false;
                }return true;
    }
    @Test void savedEscapeVersionIsOptInAndNeverMigratesAnExistingTree(){
        for(int shape:new int[]{2,3}){
            var t=new CompoundTag();t.putLong("Origin",0);t.putLong("ShapeSeed",115117);t.putInt("ShapeVersion",shape);
            t.putInt("CourseVersion",shape==2?1:2);t.putInt("O",-1);t.putIntArray("BB",new int[]{-35,-20,-35,35,124,35});
            var old=new GinsoPiece((context,nbt)->null,t);assertEquals(0,old.escapeVersion());assertTrue(old.escape().blocks().isEmpty());
            var saved=new CompoundTag();old.addAdditionalSaveData(null,saved);assertFalse(saved.contains("EscapeVersion"));
            t.putInt("EscapeVersion",1);
            if(shape==2)assertThrows(IllegalArgumentException.class,()->new GinsoPiece((context,nbt)->null,t));
            else {var fresh=new GinsoPiece((context,nbt)->null,t);assertEquals(1,fresh.escapeVersion());assertFalse(fresh.escape().blocks().isEmpty());}
        }
        assertThrows(IllegalArgumentException.class,()->GinsoEscapeLayout.checked(3,2,2));
    }
    private static final Map<String,GinsoEscapeLayout.Layout> SAMPLES=new HashMap<>();
    static synchronized GinsoEscapeLayout.Layout layout(long seed,int turn){return SAMPLES.computeIfAbsent(seed+":"+turn,k->GinsoEscapeLayout.forTree(seed,turn,new GinsoShape(seed,3)));}
    @Test void allSeedsAndRotationsHaveThreeBlockHeadroomAndAnOrdinaryStepRoute(){
        for(int seed=0;seed<64;seed++){
            var shape=new GinsoShape(seed,3);
            for(int turn=0;turn<4;turn++){
                var e=layout(seed,turn);
                assertTrue(e.path().getLast().distance()>=65&&e.path().getLast().distance()<=200);
                assertEquals(GinsoEscapeLayout.exitHeight(shape.height),e.path().getLast().feet());
                assertTrue(e.path().getLast().feet()<=shape.height-6,"Tallest fork must dominate the walk");
                assertFalse(e.blocks().containsValue(WINDOW));assertFalse(e.blocks().containsValue(LIGHT));
                for(int i=0;i<e.path().size();i++){
                    var p=e.path().get(i);String at="seed="+seed+" turn="+turn+" index="+i+" "+p;
                    assertTrue(clear(shape,e,turn,p.x()+.5,p.feet(),p.z()+.5,3),"Headroom "+at);
                    assertEquals(p.feet(),landing(shape,e,turn,p.x(),p.z(),p.feet()),1e-6,"Support "+at);
                    if(i==0)continue;var before=e.path().get(i-1);
                    assertTrue(Math.abs(p.feet()-before.feet())<=.5,"Step "+at);
                    for(int sample=0;sample<=4;sample++){
                        double f=sample/4.0,feet=Math.max(before.feet(),p.feet());
                        double xx=before.x()+.5+(p.x()-before.x())*f,zz=before.z()+.5+(p.z()-before.z())*f;
                        if(!clear(shape,e,turn,xx,feet,zz,1.8))fail("Swept body "+at+" from "+before+" f="+f+" "+obstructions(shape,e,turn,xx,feet,zz));
                    }
                }
                var end=e.path().getLast();
                for(int y=(int)end.feet()+2;y<=124;y++)assertEquals(0,collision(cell(shape,e,turn,end.x(),y,end.z())),"Exit must see the sky");
            }
        }
    }
    private static String obstructions(GinsoShape shape,GinsoEscapeLayout.Layout e,int turn,double x,double feet,double z){
        var found=new ArrayList<String>();
        for(int xx=(int)Math.floor(x-.3+1e-6);xx<=(int)Math.floor(x+.3-1e-6);xx++)
            for(int zz=(int)Math.floor(z-.3+1e-6);zz<=(int)Math.floor(z+.3-1e-6);zz++)
                for(int y=(int)Math.floor(feet)-1;y<feet+1.8-1e-6;y++){
                    byte c=cell(shape,e,turn,xx,y,zz);double h=collision(c);
                    if(h>0&&y+h>feet+1e-6)found.add(xx+","+y+","+zz+"="+c);
                }
        return found.toString();
    }
    private static double landing(GinsoShape shape,GinsoEscapeLayout.Layout e,int turn,int x,int z,double feet){
        for(int y=(int)Math.floor(feet);y>=0;y--){double h=collision(cell(shape,e,turn,x,y,z));if(h>0&&y+h<=feet+1e-6)return y+h;}return 0;
    }
    private record Standing(int x,double feet,int z){}
    @Test void everyWalkingReachableSideNeighbourHasACatchWithinThreeAndNeverBelowNinetyFour(){
        long total=0;double maxDrop=0,minLanding=999;
        for(int seed=0;seed<64;seed++){
            var shape=new GinsoShape(seed,3);
            for(int turn=0;turn<4;turn++){
                var e=layout(seed,turn);var visited=new HashSet<Standing>();var queue=new ArrayDeque<Standing>();
                var first=new Standing(0,94,0);queue.add(first);visited.add(first);
                while(!queue.isEmpty()){
                    var p=queue.remove();total++;
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                        if(dx==0&&dz==0)continue;int x=p.x()+dx,z=p.z()+dz;
                        if(dx!=0&&dz!=0&&(!clear(shape,e,turn,x+.5,p.feet(),p.z()+.5,1.8)||!clear(shape,e,turn,p.x()+.5,p.feet(),z+.5,1.8)))continue;
                        double feet=p.feet();boolean same=clear(shape,e,turn,x+.5,feet,z+.5,1.8);
                        if(!same){feet+=.5;if(!clear(shape,e,turn,x+.5,feet,z+.5,1.8))continue;}
                        double catchY=landing(shape,e,turn,x,z,feet);
                        if(!clear(shape,e,turn,x+.5,catchY,z+.5,1.8))continue;
                        maxDrop=Math.max(maxDrop,p.feet()-catchY);minLanding=Math.min(minLanding,catchY);
                        assertTrue(catchY>=94&&p.feet()-catchY<=3,"Walk fall seed="+seed+" turn="+turn+" "+p+" -> "+x+","+catchY+","+z);
                        var q=new Standing(x,catchY,z);if(visited.add(q))queue.add(q);
                    }
                }
                for(var p:e.path())assertTrue(visited.contains(new Standing(p.x(),p.feet(),p.z())),"Disconnected walk seed="+seed+" turn="+turn+" "+p);
            }
        }
        System.out.println("GINSO_ESCAPE_FALL reachable="+total+" lowest="+minLanding+" maxDrop="+maxDrop+"; deliberate double-jump over 1.5-high fences excluded");
    }
    @Test void floodContainsTheOuterForkAndExitIsOpenAndDry(){
        for(int seed=0;seed<64;seed++)for(int turn=0;turn<4;turn++){
            var e=layout(seed,turn);var end=e.path().getLast();assertTrue(e.inFlood(0,92,0));
            for(var p:e.path())assertTrue(e.inFlood(p.x()+.5,Math.min(p.feet()-.01,end.feet()-.01),p.z()+.5),p.toString());
            assertFalse(e.inFlood(34,99,34));assertFalse(e.inRoute(34,99,34));
            assertFalse(e.inFlood(end.x()+.5,end.feet(),end.z()+.5));
            assertTrue(e.exit().contains(end.x()+.5,end.feet(),end.z()+.5));
        }
    }
    @Test void naturalCaptureForkDoesNotLeaveFloatingWoodAboveItsHeadroomCut(){
        long seed=1387583691837622L;int turn=2;var shape=new GinsoShape(seed,3);var e=layout(seed,turn);
        var solid=new HashSet<BlockPos>();var connected=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();
        for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++)for(int y=93;y<=124;y++){
            var p=new BlockPos(x,y,z);if(collision(cell(shape,e,turn,x,y,z))==0)continue;
            solid.add(p);if(y==93){connected.add(p);queue.add(p);}
        }
        while(!queue.isEmpty()){
            var p=queue.remove();for(var direction:net.minecraft.core.Direction.values()){
                var q=p.relative(direction);if(solid.contains(q)&&connected.add(q))queue.add(q);
            }
        }
        var removed=new ArrayList<BlockPos>();
        for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++)for(int y=shape.height-2;y<=124;y++){
            var p=new BlockPos(x,y,z);var q=GinsoInterior.rotate(p,turn);
            if(!GinsoShape.timber(shape.cell(q.getX(),q.getY(),q.getZ())))continue;
            if(e.cell(x,y,z)==AIR)removed.add(p);
            else assertTrue(connected.contains(p),"Floating native fork tip "+p);
        }
        assertTrue(removed.isEmpty(),"Native terminal wood is preserved by the revised saddle");
        assertTrue(e.cleftIndex()<e.branchIndex());
        System.out.println("GINSO_ESCAPE_TRIM native tip cleared="+removed+" cleft="+e.cleftIndex()+" branch="+e.branchIndex());
    }
    @Test void nativeCutsStayInsideActualThreeBlockHeadroomAndNoComponentsAreDeleted(){
        int maximumCuts=0,maximumBrace=0;
        for(int seed=0;seed<64;seed++)for(int turn=0;turn<4;turn++){
            var shape=new GinsoShape(seed,3);var e=layout(seed,turn);int cuts=0;
            for(var entry:e.blocks().entrySet()){
                var p=entry.getKey();var q=GinsoInterior.rotate(p,turn);
                if(entry.getValue()!=AIR||!GinsoShape.timber(shape.cell(q.getX(),q.getY(),q.getZ())))continue;
                assertTrue(e.clearance().stream().anyMatch(b->b.contains(p.getX()+.5,p.getY()+.5,p.getZ()+.5)),"Native cut outside clearance "+seed+"/"+turn+" "+p);cuts++;
            }
            assertEquals(cuts,e.audit().clearanceRemoved());assertEquals(0,e.audit().trimmed());assertEquals(0,e.audit().maximumTrim());
            maximumCuts=Math.max(maximumCuts,cuts);maximumBrace=Math.max(maximumBrace,e.audit().bracedBlocks());
        }
        System.out.println("GINSO_ESCAPE_NATIVE clearanceMax="+maximumCuts+" trimmed=0 maximumTrim=0 braceMax="+maximumBrace);
    }
    @Test void allNativeForkAndHookTerminalsRetainTheirVoxels(){
        for(int seed=0;seed<64;seed++){
            var shape=new GinsoShape(seed,3);var random=new Random(seed);random.nextInt(11);double phase=random.nextDouble()*.4;
            for(int turn=0;turn<4;turn++){
                var e=layout(seed,turn);assertEquals(0,e.audit().trimmed());assertEquals(0,e.audit().maximumTrim());
                for(int fork=0;fork<3;fork++)for(boolean hook:new boolean[]{false,true}){
                    double a=phase+new double[]{-.35,1.65,3.75}[fork],start=shape.height-43,end=shape.height-new double[]{0,9,17}[fork];
                    double r=7+new double[]{15,12,11}[fork],cx=TallGinsoShape.centerX(start),cz=TallGinsoShape.centerZ(start);
                    if(hook){a-=.46;r=6+(15+fork)*Math.sin(Math.PI*.57);end-=6;cx=TallGinsoShape.centerX(start+13);cz=TallGinsoShape.centerZ(start+13);}
                    int x=(int)Math.round(cx+Math.cos(a)*r),z=(int)Math.round(cz+Math.sin(a)*r),y=(int)Math.round(end);int checked=0;
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-2;dy<=2;dy++){
                        if(!GinsoShape.timber(shape.cell(x+dx,y+dy,z+dz)))continue;
                        var p=GinsoInterior.rotate(new BlockPos(x+dx,y+dy,z+dz),-turn);
                        assertTrue(collision(cell(shape,e,turn,p.getX(),p.getY(),p.getZ()))>=1,"Native terminal changed seed="+seed+" turn="+turn+" fork="+fork+" hook="+hook+" "+p);checked++;
                    }
                    assertTrue(checked>0,"Missing native terminal sample");
                }
            }
        }
    }
    @Test void heartWellAndRetreatAreReservedAndTheOldCourseBytesStayTheSame(){
        for(int seed=0;seed<64;seed++)for(int turn=0;turn<4;turn++){
            var e=layout(seed,turn);
            for(var well:GinsoCourses.layout(2).wells())assertEquals(0,e.cell(well.getX(),well.getY(),well.getZ()),"Well seed="+seed+" turn="+turn);
            assertEquals(0,e.cell(0,94,0));assertEquals(0,e.cell(0,94,-5));
            assertTrue(e.blocks().keySet().stream().allMatch(p->p.getY()>=93&&p.getY()<=124&&Math.abs(p.getX())<=35&&Math.abs(p.getZ())<=35));
        }
    }
    @Test void arrivalHasALandingBeforeManualDoorsAndAnOpenDoorFailsAtTheBoundaryBeforeTheFall(){
        for(int seed=0;seed<64;seed++)for(int turn=0;turn<4;turn++){
            var e=layout(seed,turn);var shape=new GinsoShape(seed,3);
            for(int z=0;z<=1;z++){
                assertEquals(STEP,e.cell(-2,93,z));assertEquals(WOOD,e.cell(-1,93,z));
                assertEquals(DOOR,e.cell(-1,94,z));assertEquals(DOOR_TOP,e.cell(-1,95,z));assertEquals(AIR,e.cell(-1,96,z));
                assertFalse(e.inRoute(-1.01,94,z+.5),"Open-gate exit must fail before the fall");
                double catchY=landing(shape,e,turn,-3,z,93.5);assertEquals(91,catchY);assertTrue(93.5-catchY<=3);
                assertFalse(e.inRoute(-2.5,catchY,z+.5));
            }
            for(int z=-3;z>=-7;z--)for(int y=94;y<=95;y++)assertEquals(0,e.cell(0,y,z),"Retreat blocked");
        }
    }
    @Test void exportedSourceUsesSeedAndRotationAndTheRealBlockPosLayout()throws Exception{
        var root=java.nio.file.Path.of(System.getProperty("orimod.projectDir","."),"art/source/ginso_escape/v003");
        var hashes=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("manifest.json"))).getAsJsonObject().getAsJsonObject("all_geometry_sha256");
        for(int seed=0;seed<64;seed++)for(int turn=0;turn<4;turn++){
            var e=layout(seed,turn);var canonical=new StringBuilder();
            e.blocks().entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<BlockPos,Byte> a)->a.getKey().getX()).thenComparingInt(a->a.getKey().getY()).thenComparingInt(a->a.getKey().getZ())).forEach(a->{
                var p=a.getKey();canonical.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append(' ').append(a.getValue()).append('\n');
            });
            for(var p:e.path())canonical.append("R ").append(p.x()).append(' ').append(Math.round(p.feet()*2)).append(' ').append(p.z()).append(' ').append(p.corners()).append('\n');
            var digest=java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(hashes.get(seed+":"+turn).getAsString(),HexFormat.of().formatHex(digest),"Offline/real coordinate parity "+seed+":"+turn);
        }
        for(long seed:new long[]{0,59,115117,115118})for(int turn=0;turn<4;turn++){
            var e=layout(seed,turn);
            var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("seed-"+seed+"-turn-"+turn+".json"))).getAsJsonObject();
            assertEquals(e.blocks().size(),json.getAsJsonArray("cells").size());
            for(var row:json.getAsJsonArray("cells")){var p=row.getAsJsonArray();assertEquals(p.get(3).getAsByte(),e.cell(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()));}
            assertEquals(GinsoEscapeRules.plan(e).startWater(),json.getAsJsonObject("plan").get("start_water").getAsDouble(),1e-9);
        }
        assertNotEquals(layout(0,0).blocks(),layout(0,1).blocks());assertNotEquals(layout(0,0).blocks(),layout(5,0).blocks());
    }
}
