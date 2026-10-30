package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_r117")
public final class GinsoFloodVolumeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return List.of(new TestFunction("r117_ginso", "r117_ginso.volume_respects_live_walls_slabs_and_rotation", "orimod_r117:room", 100,0,true,h -> {
            var origin=h.absolutePos(new BlockPos(4,3,4));
            for(int turn=0;turn<4;turn++){
                var cell=new GinsoFloodVolume.Cell(1,0,0);var pos=origin.offset(GinsoInterior.rotate(new BlockPos(1,0,0),turn));
                var before=h.getLevel().getBlockState(pos);var below=h.getLevel().getBlockState(pos.below());
                try{
                    h.getLevel().setBlock(pos.below(),Blocks.AIR.defaultBlockState(),2);
                    h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                    h.assertTrue(GinsoFloodVolume.passable(h.getLevel(),origin,turn,cell),"Air rejected");
                    h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                    h.assertFalse(GinsoFloodVolume.passable(h.getLevel(),origin,turn,cell),"Water painted over new wall");
                    h.getLevel().setBlock(pos,Blocks.OAK_SLAB.defaultBlockState(),2);
                    h.assertFalse(GinsoFloodVolume.passable(h.getLevel(),origin,turn,cell),"Water inside slab");
                    h.assertTrue(GinsoFloodVolume.passable(h.getLevel(),origin,turn,new GinsoFloodVolume.Cell(1,1,0)),"Air above bottom slab rejected");
                    h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                    h.getLevel().setBlock(pos.below(),Blocks.OAK_FENCE.defaultBlockState(),2);
                    h.assertFalse(GinsoFloodVolume.passable(h.getLevel(),origin,turn,cell),"Fence extension ignored");
                }finally{h.getLevel().setBlock(pos,before,2);h.getLevel().setBlock(pos.below(),below,2);}
            }
            h.succeed();
        }));
    }
}
