package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class P5KRuinLayoutTest {
    private static Map<BlockPos,String> blocks(String kind,int version) throws Exception {
        var nbt=NbtIo.readCompressed(Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/data/orimod/structure/ruins/"+kind+"_v"+version+".nbt"),NbtAccounter.unlimitedHeap());
        var out=new HashMap<BlockPos,String>();var palette=nbt.getList("palette",Tag.TAG_COMPOUND);
        for(var v:nbt.getList("blocks",Tag.TAG_COMPOUND)){var b=(CompoundTag)v;var p=b.getList("pos",Tag.TAG_INT);out.put(new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2)),palette.getCompound(b.getInt("state")).getString("Name"));}return out;
    }
    @Test void onlyRewardRoomsChangeAndAllRoofCellsAreSolid() throws Exception {
        var old=blocks("forest_spring",1);var fresh=blocks("forest_spring",2);
        int[][] rooms={{2,35,10,41},{29,36,37,45}};
        for(var r:rooms)for(int x=r[0];x<=r[2];x++)for(int z=r[1];z<=r[3];z++){
            assertTrue(Set.of("minecraft:mossy_stone_bricks","minecraft:cracked_stone_bricks").contains(fresh.get(new BlockPos(x,12,z))));
            if(x==r[0]||x==r[2]||z==r[1]||z==r[3])for(int y=9;y<12;y++)assertEquals("minecraft:mossy_stone_bricks",fresh.get(new BlockPos(x,y,z)));
        }
        for(var e:old.entrySet())if(!Objects.equals(fresh.get(e.getKey()),e.getValue())) {
            var p=e.getKey();boolean inside=false;for(var r:rooms)inside|=p.getX()>=r[0]&&p.getX()<=r[2]&&p.getZ()>=r[1]&&p.getZ()<=r[3]&&p.getY()>=9&&p.getY()<=12;
            assertTrue(inside,"Public ruin or low door changed: "+p);
        }
        assertEquals("orimod:cracked_ruin_wall",fresh.get(new BlockPos(10,7,38)));
        assertEquals("orimod:keystone_door",fresh.get(new BlockPos(29,7,40)));
        assertEquals("orimod:spirit_light_vessel",fresh.get(new BlockPos(5,6,38)));
        assertEquals("orimod:energy_cell_pedestal",fresh.get(new BlockPos(34,6,40)));
    }
    @Test void versionedRoutesAndAnchorsRemainTheSame() {
        for(var k:List.of(RuinKind.FOREST,RuinKind.CREEK)) {
            assertEquals(2,k.currentVersion());var old=k.layout(1);var fresh=k.layout(2);
            assertEquals(old.size(),fresh.size());assertEquals(old.surfaceY(),fresh.surfaceY());assertEquals(old.entry(),fresh.entry());assertEquals(old.checkpoints(),fresh.checkpoints());assertEquals(old.critical(),fresh.critical());assertEquals(old.cells(),fresh.cells());
        }
        assertEquals(1,RuinKind.CAVE.currentVersion());
    }
}
