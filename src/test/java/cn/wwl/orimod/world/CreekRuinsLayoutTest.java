package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CreekRuinsLayoutTest {
    private static Path root(){return Path.of(System.getProperty("orimod.projectDir","."));}
    private static Map<BlockPos,CompoundTag> cells() throws Exception {
        var n=NbtIo.readCompressed(root().resolve("src/main/resources/data/orimod/structure/ruins/creek_ruins_v1.nbt"),NbtAccounter.unlimitedHeap());
        var palette=n.getList("palette",10);var cells=new HashMap<BlockPos,CompoundTag>();
        for(var v:n.getList("blocks",10)){var b=(CompoundTag)v;var p=b.getList("pos",3);cells.put(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.getCompound(b.getInt("state")));}return cells;
    }
    @Test void legacyBytesFrozenBeforeEditing() throws Exception {
        var baseline=JsonParser.parseString(Files.readString(root().resolve("src/test/resources/ruins/creek_ruins_legacy.json"))).getAsJsonObject();
        for(var e:baseline.getAsJsonObject("files").entrySet())assertEquals(e.getValue().getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root().resolve(e.getKey())))),e.getKey());
    }
    @ParameterizedTest @EnumSource(Rotation.class)
    void savedVersionUsesItsOwnContract(Rotation r){
        var old=RuinKind.CREEK.layout();var l=RuinKind.CREEK.layout(1);
        assertEquals(new BlockPos(21,30,35),old.size());assertEquals(new BlockPos(45,44,67),l.size());
        var p=RuinPlacement.atMinimum(l,new BlockPos(100,20,200),r);
        assertEquals(p.block(new BlockPos(22,10,0)),RuinApproachPiece.doorway(RuinKind.CREEK,l,p).origin());
        assertEquals(List.of("bash","glide"),l.checkpoints().stream().map(RuinKind.Checkpoint::action).toList());
        assertEquals(6,RuinKind.CREEK.abilities);assertNull(l.vault());assertNull(l.bossAltar());
    }
    @Test void ownedObjectsAreSeparatedAndLegacyNeverRewritten()throws Exception{
        var counts=new HashMap<String,Integer>();for(var s:cells().values())if(s.getString("Name").equals("orimod:sanctuary_part"))counts.merge(s.getCompound("Properties").getString("kind"),1,Integer::sum);
        assertEquals(Map.of("creek_ruins_shrine",26,"map_stone",2),counts);
        var l=RuinKind.CREEK.layout(1);assertTrue(l.shrine().distSqr(l.mapStone())>100);
    }
    @Test void windColumnsAreDryClearAndSupported()throws Exception{
        var cells=cells();for(var p:RuinKind.CREEK.layout(1).updrafts()){
            assertEquals("orimod:updraft",cells.get(p).getString("Name"));
            for(int dy=1;dy<=12;dy++)assertTrue(Set.of("minecraft:air","minecraft:glow_lichen").contains(cells.get(p.above(dy)).getString("Name")),p+" "+dy);
        }
    }
    @Test void bashLandingHasForgivingNearEdgeAndSupportStaysBelowTheLantern()throws Exception{
        var c=cells();
        for(int x=10;x<=15;x++)for(int z=31;z<=38;z++)
            assertNotEquals("minecraft:air",c.get(new BlockPos(x,14,z)).getString("Name"),"C1 footprint "+x+":"+z);
        assertEquals("orimod:great_spirit_root",c.get(new BlockPos(12,12,29)).getString("Name"));
        for(int x=11;x<=13;x++)for(int y=14;y<=16;y++)for(int z=28;z<=30;z++)
            assertTrue(Set.of("minecraft:air","minecraft:glow_lichen").contains(c.get(new BlockPos(x,y,z)).getString("Name")),"Overhead root blocks Bash "+x+":"+y+":"+z);
    }
    @Test void dropsAreSingleNotchesAndAllOtherWaterIsSource()throws Exception{
        var cells=cells();var m=JsonParser.parseString(Files.readString(root().resolve("art/source/creek_ruins/v004/manifest.json"))).getAsJsonObject();var flow=new HashSet<BlockPos>();
        for(var v:m.getAsJsonArray("flow")){var a=v.getAsJsonArray();flow.add(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}
        assertEquals(7,flow.size());int water=0;
        for(var e:cells.entrySet())if(e.getValue().getString("Name").equals("minecraft:water")){
            water++;assertEquals(flow.contains(e.getKey())?"8":"0",e.getValue().getCompound("Properties").getString("level"));
            for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN)){
                var q=e.getKey().relative(d);var state=cells.get(q);assertNotNull(state,"Unwritten seal "+q);
                assertFalse(Set.of("minecraft:air","minecraft:glow_lichen").contains(state.getString("Name")),"Leak "+q);
            }
        }assertTrue(water>200);
        for(var v:m.getAsJsonArray("weirs")){
            var w=v.getAsJsonObject();int z=w.get("z").getAsInt(),x=w.get("x").getAsInt(),y=w.get("upper").getAsInt();
            for(int dx=-2;dx<=2;dx++)if(dx!=0)assertEquals("minecraft:mossy_stone_bricks",cells.get(new BlockPos(x+dx,y+1,z)).getString("Name"));
        }
    }
    @Test void leavesPersistentAndNodesSparse()throws Exception{
        var all=cells();int nodes=0,faces=0;for(var e:all.entrySet()){
            var s=e.getValue();
            var n=s.getString("Name");var p=s.getCompound("Properties");
            if(n.endsWith("_leaves"))assertEquals("true",p.getString("persistent"));
            if(n.equals("orimod:great_spirit_root")&&p.getString("light").equals("knot")){
                nodes++;int exposed=0;
                for(var d:Direction.values()){
                    var neighbour=all.get(e.getKey().relative(d));var name=neighbour==null?"minecraft:air":neighbour.getString("Name");
                    if(Set.of("minecraft:air","minecraft:water","minecraft:glow_lichen","minecraft:moss_carpet","minecraft:vine","minecraft:cave_vines","minecraft:cave_vines_plant").contains(name)
                            ||name.endsWith("_leaves")||name.endsWith("_slab")||name.endsWith("_stairs")||name.endsWith("_wall")||name.endsWith("_fence")
                            ||name.startsWith("orimod:")&&!name.equals("orimod:great_spirit_root"))exposed++;
                }
                assertTrue(exposed<=2,"Glyph faces on "+e.getKey()+": "+exposed);faces+=exposed;
            }
        }assertEquals(7,nodes);assertTrue(faces<=14);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"c1-north","c1-east","c1-south","c1-west","tower-north","tower-east","tower-south","tower-west"})
    void exteriorCrestsHaveAtLeastThreeHeightChanges(String edge) throws Exception {
        var c=cells();boolean tower=edge.startsWith("tower");
        int x0=tower?30:8,x1=tower?39:17,z0=tower?32:30,z1=tower?44:39;
        String side=edge.substring(edge.indexOf('-')+1);var heights=new ArrayList<Integer>();
        boolean acrossX=side.equals("north")||side.equals("south");
        for(int i=acrossX?x0:z0;i<=(acrossX?x1:z1);i++){
            int x=acrossX?i:side.equals("east")?(tower?39:16):(tower?30:9);
            int z=acrossX?(side.equals("north")?z0:z1):i;
            int h=0;
            for(int y=tower?21:9;y<=29;y++){
                var state=c.get(new BlockPos(x,y,z));if(state==null)continue;
                String n=state.getString("Name");
                // Actual masonry crest, ignoring plants and lighting decoration.
                if(!n.contains("stone_brick")&&!n.contains("tuff_brick"))continue;
                int top=2*(y+1);
                if(n.endsWith("_slab")&&state.getCompound("Properties").getString("type").equals("bottom"))top--;
                h=Math.max(h,top);
            }
            heights.add(h);
        }
        int changes=0;for(int i=1;i<heights.size();i++)if(!heights.get(i).equals(heights.get(i-1)))changes++;
        assertTrue(changes>=3,edge+" has only "+changes+" height changes: "+heights);
        assertTrue(Collections.max(heights)-Collections.min(heights)>=4,edge+" crest variation under two blocks: "+heights);
    }
    @Test void artRevisionPreservesEveryGameplayState() throws Exception {
        int[][] boxes={{10,14,31,15,20,38},{16,18,51,22,24,57},{10,10,25,15,20,30},{16,10,42,18,24,45},{30,9,32,39,21,44},{12,15,51,14,19,54},{9,12,50,11,16,53},{6,9,49,8,13,52}};
        var lines=new TreeMap<String,String>();
        for(var e:cells().entrySet()){
            var p=e.getKey();boolean protectedCell=false;
            for(var b:boxes)if(p.getX()>=b[0]&&p.getX()<=b[3]&&p.getY()>=b[1]&&p.getY()<=b[4]&&p.getZ()>=b[2]&&p.getZ()<=b[5])protectedCell=true;
            if(!protectedCell)continue;
            String pos=p.getX()+","+p.getY()+","+p.getZ();var properties=e.getValue().getCompound("Properties");
            var values=new TreeMap<String,String>();for(String k:properties.getAllKeys())values.put(k,properties.getString(k));
            lines.put(pos,pos+"="+e.getValue().getString("Name")+"["+String.join(",",values.entrySet().stream().map(v->v.getKey()+"="+v.getValue()).toList())+"]\n");
        }
        assertEquals(3125,lines.size());
        assertEquals("b8102aa2d9aa6c599939f1a12dcd17fe381cef6f37dc0ca4bf0e42a5c4bab3fe",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("",lines.values()).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test void wheelMatchesTheNinePixelTwoRimDrawing() throws Exception {
        var c=cells();String[] rows={"...XXX...",".XX...XX.",".X..|..X.","X...|...X","X---O---X","X...|...X",".X..|..X.",".XX...XX.","...XXX..."};
        for(int x:new int[]{22,24})for(int row=0;row<9;row++)for(int col=0;col<9;col++){
            var p=new BlockPos(x,16-row,20+col);var s=c.get(p);String n=s==null?"minecraft:air":s.getString("Name");char want=rows[row].charAt(col);
            if(want=='X')assertEquals("minecraft:dark_oak_"+((row==1||row==2||row==6||row==7)?"stairs":"planks"),n,"Rim "+p);
            else if(want=='O')assertEquals("minecraft:stripped_dark_oak_log",n,"Axle "+p);
            else if(want=='|'||want=='-')assertEquals("minecraft:dark_oak_fence",n,"Radial spoke "+p);
            else assertTrue(n.equals("minecraft:air")||n.equals("minecraft:water"),"Wheel gap filled "+p+"="+n);
            if(want=='X'&&(row==1||row==2||row==6||row==7))assertEquals(col<4?"south":"north",s.getCompound("Properties").getString("facing"),"Rounded stair high side must face inward "+p);
        }
        for(int x=22;x<=24;x++)for(int y=8;y<=16;y++)for(int z=20;z<=28;z++)assertNotEquals("minecraft:glow_lichen",c.get(new BlockPos(x,y,z)).getString("Name"),"No lichen on wheel/axle");
        for(int z=20;z<=28;z++)for(int x:new int[]{20,27})for(int y=7;y<=9;y++)
            assertTrue(c.get(new BlockPos(x,y,z)).getString("Name").endsWith("stone_bricks"),"Bare earth mill pit "+x+":"+y+":"+z);
    }
    @Test void wheelLowerRimSharesStillWaterAndLandingTopsStayWhole()throws Exception{
        var c=cells();
        for(int x:new int[]{22,24})for(int z=23;z<=25;z++)assertEquals("minecraft:dark_oak_planks",c.get(new BlockPos(x,8,z)).getString("Name"));
        for(int x:new int[]{22,24})for(int z:new int[]{21,22,26,27}){
            var s=c.get(new BlockPos(x,9,z));assertEquals("minecraft:dark_oak_stairs",s.getString("Name"));
            assertEquals("true",s.getCompound("Properties").getString("waterlogged"));
        }
        for(int[] b:new int[][]{{10,15,31,38,14},{16,22,51,57,18}})
            for(int x=b[0];x<=b[1];x++)for(int z=b[2];z<=b[3];z++){
            var n=c.get(new BlockPos(x,b[4],z)).getString("Name");
            assertFalse(n.equals("minecraft:air")||n.endsWith("_stairs")||n.endsWith("_slab"),"Landing floor changed "+x+":"+z);
        }
        for(int x=16;x<=22;x++)for(int z=51;z<=57;z++){
            var n=c.get(new BlockPos(x,18,z)).getString("Name");
            assertFalse(n.equals("minecraft:air")||n.endsWith("_stairs")||n.endsWith("_slab"),"C2 floor changed "+x+":"+z);
        }
    }
}
