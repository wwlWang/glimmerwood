package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class CaveAltarLayoutTest {
    @Test void vaultAirHasASolidAuthoredBoundaryAgainstAquifers() throws Exception {
        var root=Path.of(System.getProperty("orimod.projectDir","."));
        var nbt=net.minecraft.nbt.NbtIo.readCompressed(root.resolve("src/main/resources/data/orimod/structure/ruins/cave_altar_v1.nbt"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var palette=nbt.getList("palette",10);var cells=new java.util.HashMap<BlockPos,String>();
        for(var value:nbt.getList("blocks",10)){var b=(net.minecraft.nbt.CompoundTag)value;var p=b.getList("pos",3);cells.put(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.getCompound(b.getInt("state")).getString("Name"));}
        var leaks=new java.util.ArrayList<BlockPos>();
        for(var e:cells.entrySet())if(e.getValue().equals("minecraft:air"))
            for(var d:net.minecraft.core.Direction.values()){
                var q=e.getKey().relative(d);
                if(q.getZ()==-1||q.getY()==32&&e.getKey().getZ()<6)continue; // surface mouth
                if(!cells.containsKey(q))leaks.add(q);
            }
        assertTrue(leaks.isEmpty(),"Unsealed room boundary: "+leaks.size()+" "+leaks.stream().limit(12).toList());
    }
    @Test void sparseRootsAreContinuousAttachedAndTheirKnotsDoNotTile() throws Exception {
        var root=Path.of(System.getProperty("orimod.projectDir","."));
        var manifest=JsonParser.parseString(Files.readString(root.resolve("art/source/cave_altar/v002/manifest.json"))).getAsJsonObject();
        var nbt=net.minecraft.nbt.NbtIo.readCompressed(root.resolve("src/main/resources/data/orimod/structure/ruins/cave_altar_v1.nbt"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var palette=nbt.getList("palette",10);var cells=new java.util.HashMap<BlockPos,String>();
        for(var value:nbt.getList("blocks",10)){var b=(net.minecraft.nbt.CompoundTag)value;var p=b.getList("pos",3);cells.put(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.getCompound(b.getInt("state")).getString("Name"));}
        long rootCount=cells.values().stream().filter("orimod:great_spirit_root"::equals).count();
        assertTrue(rootCount<=322,"Root wood must be at least 60% below the v001 count of 805");
        var knots=new java.util.ArrayList<BlockPos>();
        for(var value:manifest.getAsJsonArray("roots")){
            var record=value.getAsJsonObject();BlockPos previous=null,first=null,last=null;
            for(var step:record.getAsJsonArray("path")){
                var p=jsonPosition(step);
                assertTrue(java.util.Set.of("orimod:great_spirit_root","minecraft:dark_oak_fence").contains(cells.get(p)),"Overwritten root at "+p);
                if(previous!=null)assertEquals(1,p.distManhattan(previous),"Disconnected root segment");else first=p;
                previous=p;last=p;
            }
            assertEquals(5,first.getY(),"Root floats above the room floor");
            assertNotEquals("minecraft:air",cells.get(last.above()),"Root tip floats below the vault");
            assertNotNull(cells.get(last.above()),"Root tip outside authored ceiling");
            assertTrue(record.getAsJsonArray("knots").size()<=2,"Tiled knot root");
            for(var k:record.getAsJsonArray("knots")){var p=jsonPosition(k);for(var other:knots)assertTrue(p.distSqr(other)>=16,"Adjacent decorative knot textures");knots.add(p);}
        }
        assertEquals(7,manifest.getAsJsonArray("roots").size());
        var framing=manifest.getAsJsonObject("lighting").getAsJsonArray("grapple_framing_knots");
        assertTrue(framing.size()>=1 && framing.size()<=2,"Keep gallery framing sparse");
        for(var k:framing){
            var p=jsonPosition(k);assertEquals("orimod:great_spirit_root",cells.get(p));
            for(var other:knots)assertTrue(p.distSqr(other)>=16,"Framing became a tiled knot wall");
            knots.add(p);
        }
    }
    private static BlockPos jsonPosition(com.google.gson.JsonElement e){var a=e.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    @Test void legacyBytesAreThePreEditBaseline() throws Exception {
        var root=Path.of(System.getProperty("orimod.projectDir","."));
        var baseline=JsonParser.parseString(Files.readString(root.resolve("src/test/resources/ruins/cave_altar_legacy.json"))).getAsJsonObject();
        for(var e:baseline.getAsJsonObject("files").entrySet())assertEquals(e.getValue().getAsString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(e.getKey())))),e.getKey());
    }
    @Test void contractsAndResourcesStaySeparate() {
        assertEquals(new BlockPos(23,19,39),RuinKind.CAVE.layout().size());
        assertEquals(new BlockPos(47,32,71),RuinKind.CAVE.layout(1).size());
        assertNotEquals(RuinKind.CAVE.template(0),RuinKind.CAVE.template(1));
        assertEquals(1,RuinKind.CAVE.currentVersion());assertEquals(0,RuinKind.FOREST.currentVersion());
        assertThrows(IllegalArgumentException.class,()->RuinKind.CAVE.layout(2));
    }
    @ParameterizedTest @EnumSource(Rotation.class)
    void newFootprintRefusesAnExploredEdgeWithoutLoading(Rotation rotation) {
        var l=RuinKind.CAVE.layout(1);var p=RuinPlacement.atMinimum(l,new BlockPos(-47,0,97),rotation);
        var b=p.blocks(BlockPos.ZERO,l.size().offset(-1,-1,-1));
        var clear=GinsoGenerationGuard.scanBounds(b,(x,z)->null);assertTrue(clear.allowed());
        assertEquals(((b.maxX()>>4)-(b.minX()>>4)+1)*((b.maxZ()>>4)-(b.minZ()>>4)+1),clear.checked());
        var blocked=GinsoGenerationGuard.scanBounds(b,(x,z)->x==(b.maxX()>>4)&&z==(b.maxZ()>>4)?"region-header":null);
        assertFalse(blocked.allowed());assertEquals("region-header",blocked.source());
    }
    @Test void threeRoomsReserveSeparatePhysicalFootprints() {
        var l=RuinKind.CAVE.layout(1);
        assertTrue(l.shrine().distSqr(l.mapStone())>100);
        assertTrue(l.bossAltar().distSqr(l.shrine())>400);
        assertEquals(2,l.keystones().size());assertEquals(3,l.checkpoints().size());
        assertEquals("grapple",l.checkpoints().getFirst().action());
        assertEquals("blade",l.checkpoints().getLast().action());
    }
    @ParameterizedTest @EnumSource(Rotation.class)
    void versionedFootprintAndDoorwayRotateTogether(Rotation rotation) {
        var l=RuinKind.CAVE.layout(1);var min=new BlockPos(100,20,200);
        var p=RuinPlacement.atMinimum(l,min,rotation);var b=p.blocks(BlockPos.ZERO,l.size().offset(-1,-1,-1));
        assertEquals(min.getX(),b.minX());assertEquals(min.getZ(),b.minZ());
        assertEquals(p.block(new BlockPos(23,29,0)),RuinApproachPiece.doorway(RuinKind.CAVE,l,p).origin());
        for(var check:l.checkpoints())assertTrue(b.isInside(BlockPos.containing(p.point(check.position()))));
    }
}
