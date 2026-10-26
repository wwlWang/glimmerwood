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

class ForestSpringLayoutTest {
    private static Path root(){return Path.of(System.getProperty("orimod.projectDir","."));}
    private static Map<BlockPos,CompoundTag> cells() throws Exception {
        var n=NbtIo.readCompressed(root().resolve("src/main/resources/data/orimod/structure/ruins/forest_spring_v1.nbt"),NbtAccounter.unlimitedHeap());
        var palette=n.getList("palette",10);var cells=new HashMap<BlockPos,CompoundTag>();
        for(var v:n.getList("blocks",10)){var b=(CompoundTag)v;var p=b.getList("pos",3);cells.put(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.getCompound(b.getInt("state")));}return cells;
    }
    @Test void legacyBytesFrozenBeforeEditing() throws Exception {
        var baseline=JsonParser.parseString(Files.readString(root().resolve("src/test/resources/ruins/forest_spring_legacy.json"))).getAsJsonObject();
        for(var e:baseline.getAsJsonObject("files").entrySet())assertEquals(e.getValue().getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root().resolve(e.getKey())))),e.getKey());
    }
    @Test void surfaceLayoutsAreNotBuriedLikeCaves(){
        assertEquals(74,RuinSiteRules.templateSurface(false,1,6,32,70,74));
        assertEquals(74,RuinSiteRules.templateSurface(false,0,6,28,70,74));
        assertEquals(65,RuinSiteRules.templateSurface(true,1,29,32,70,74));
        assertEquals(67,RuinSiteRules.templateSurface(true,0,18,19,70,74));
        assertTrue(RuinSiteRules.footprintOk(70,74));assertFalse(RuinSiteRules.footprintOk(70,75));
    }
    @ParameterizedTest @EnumSource(Rotation.class)
    void versionedDoorwayAndSiteBounds(Rotation rotation){
        var old=RuinKind.FOREST.layout();var l=RuinKind.FOREST.layout(1);assertEquals(new BlockPos(39,32,55),l.size());assertEquals(new BlockPos(19,28,31),old.size());
        var p=RuinPlacement.atMinimum(l,new BlockPos(100,20,200),rotation);
        assertEquals(p.block(new BlockPos(19,6,0)),RuinApproachPiece.doorway(RuinKind.FOREST,l,p).origin());
        assertEquals(p.block(new BlockPos(9,1,0)),RuinApproachPiece.doorway(RuinKind.FOREST,old,p).origin());
        var b=p.blocks(BlockPos.ZERO,l.size().offset(-1,-1,-1));assertEquals(100,b.minX());assertEquals(200,b.minZ());
        assertTrue(GinsoGenerationGuard.scanBounds(b,(x,z)->null).allowed());
        assertFalse(GinsoGenerationGuard.scanBounds(b,(x,z)->x==(b.maxX()>>4)&&z==(b.maxZ()>>4)?"existing":null).allowed());
    }
    @Test void sealedPoolHasAuthoredBedAndRim() throws Exception {
        var cells=cells();int water=0;
        for(var e:cells.entrySet())if(e.getValue().getString("Name").equals("minecraft:water")){
            water++;assertEquals("0",e.getValue().getCompound("Properties").getString("level"));
            for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN)){
                var n=cells.get(e.getKey().relative(d));assertNotNull(n,"Unwritten pool rim "+e.getKey());
                assertFalse(Set.of("minecraft:air","minecraft:glow_lichen").contains(n.getString("Name")),"Pool leaks "+e.getKey()+" "+d);
            }
        }assertTrue(water>400);
    }
    @Test void rootsRemainConnectedAndSparse() throws Exception {
        var m=JsonParser.parseString(Files.readString(root().resolve("art/source/forest_spring/v002/manifest.json"))).getAsJsonObject();var cells=cells();var knots=new ArrayList<BlockPos>();
        for(var value:m.getAsJsonArray("roots")){
            var r=value.getAsJsonObject();BlockPos prev=null;
            for(var v:r.getAsJsonArray("path")){var a=v.getAsJsonArray();var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                assertEquals("orimod:great_spirit_root",cells.get(p).getString("Name"),"Root overwritten "+p);if(prev!=null)assertEquals(1,prev.distManhattan(p));prev=p;}
            assertTrue(r.getAsJsonArray("knots").size()<=2);
            for(var v:r.getAsJsonArray("knots")){var a=v.getAsJsonArray();var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());for(var k:knots)assertTrue(p.distSqr(k)>=16);knots.add(p);}
        }
    }
    @Test void groundedPiersAndRootFeetBelongToContinuousFoundations() throws Exception {
        var cells=cells();var members=Set.of("orimod:great_spirit_root","minecraft:chiseled_stone_bricks",
                "minecraft:mossy_stone_bricks","minecraft:cracked_stone_bricks","minecraft:stone_bricks");
        for(var e:cells.entrySet())if(e.getKey().getY()==5&&members.contains(e.getValue().getString("Name"))){
            var base=cells.get(e.getKey().atY(0));assertNotNull(base,"Unfounded ground member "+e.getKey());
            assertEquals("minecraft:dirt",base.getString("Name"),"Member outside grading footprint "+e.getKey());
        }
        // Portal piers and the six widened arch/root feet previously had no
        // authored blocks beneath them. Keep every column supported, not exempt.
        for(var column:List.of(new BlockPos(13,0,3),new BlockPos(25,0,3),new BlockPos(10,0,51),
                new BlockPos(28,0,51),new BlockPos(29,0,50),new BlockPos(30,0,49),new BlockPos(36,0,22),new BlockPos(37,0,21))){
            for(int y=0;y<5;y++){
                var at=column.atY(y);assertNotNull(cells.get(at),"Foundation gap "+at);
                assertEquals("minecraft:dirt",cells.get(at).getString("Name"),"Foundation gap "+at);
            }
        }
    }
    @Test void decorativeFruitIsSupportedAndFoliagePersistent() throws Exception {
        var cells=cells();int nodes=0,fruit=0;
        for(var e:cells.entrySet()){
            var s=e.getValue();var n=s.getString("Name");var p=s.getCompound("Properties");
            if(n.equals("orimod:great_spirit_root")&&p.getString("light").equals("knot"))nodes++;
            if(n.startsWith("minecraft:")&&n.endsWith("_leaves"))assertEquals("true",p.getString("persistent"),e.getKey().toString());
            if(n.equals("minecraft:cave_vines")||n.equals("minecraft:cave_vines_plant")){
                fruit++;var support=cells.get(e.getKey().above());assertNotNull(support);var above=support.getString("Name");
                assertFalse(above.equals("minecraft:air")||above.endsWith("_leaves")||above.endsWith("_slab"),"Unstable fruit "+e.getKey());
            }
        }
        assertTrue(fruit>0);assertTrue(nodes<=8,"Glyphs are sparse focus markers: "+nodes);
    }
    @Test void rewardCourtsHaveOpenBrokenRoofs() throws Exception {
        var cells=cells();
        for(var box:List.of(new int[]{3,9,36,40},new int[]{30,36,37,44})){
            int open=0,total=0;
            for(int x=box[0];x<=box[1];x++)for(int z=box[2];z<=box[3];z++){
                total++;boolean sky=true;
                for(int y=10;y<32;y++){var s=cells.get(new BlockPos(x,y,z));if(s!=null&&!s.getString("Name").equals("minecraft:air")&&!s.getString("Name").equals("minecraft:glow_lichen"))sky=false;}
                if(sky)open++;
            }
            assertTrue(open>=total*.6,"Roof resealed: "+open+"/"+total);
        }
    }
    @Test void distinctOwnedObjectsAndOldGameplayRemain() throws Exception {
        var l=RuinKind.FOREST.layout(1);assertTrue(l.shrine().distSqr(l.mapStone())>400);assertTrue(l.stormAltar().distSqr(l.shrine())>400);
        assertEquals(List.of("wall_jump","jump","dash"),l.checkpoints().stream().map(RuinKind.Checkpoint::action).toList());assertEquals(1,l.bridges().size());assertNotNull(l.secret());assertEquals(2,l.keystones().size());
        var parts=new TreeMap<String,Integer>();int door=0;
        for(var s:cells().values()){
            if(s.getString("Name").equals("orimod:sanctuary_part"))parts.merge(s.getCompound("Properties").getString("kind"),1,Integer::sum);
            if(s.getString("Name").equals("orimod:keystone_door")){door++;assertNotEquals("legacy",s.getCompound("Properties").getString("part"));}
        }assertEquals(Map.of("forest_spring_shrine",26,"map_stone",2,"storm_altar",26),parts);assertEquals(9,door);
    }
}
