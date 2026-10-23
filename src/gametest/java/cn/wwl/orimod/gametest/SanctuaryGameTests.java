package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class SanctuaryGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        var blocks=List.of(OriBlocks.GLADES_WELL,OriBlocks.FOREST_SHRINE,OriBlocks.CREEK_SHRINE,OriBlocks.CAVE_SHRINE,
                OriBlocks.MAP_STONE,OriBlocks.GLOOM_ALTAR,OriBlocks.STORM_ALTAR,OriBlocks.KEYSTONE_DOOR);
        for(var block:blocks) add(tests,"legacy_"+block.getId().getPath(),h->{
            var p=h.absolutePos(new BlockPos(5,3,5));var state=block.get().defaultBlockState();var level=h.getLevel();
            level.setBlock(p,state,3);var chunk=level.getChunkAt(p);
            // The pre-R115 format: palette contains the same block, but no block_entity entry in the chunk NBT.
            chunk.removeBlockEntity(p);
            h.assertTrue(chunk.getBlockEntity(p,LevelChunk.EntityCreationType.CHECK)==null,"Fixture still has BE");
            h.assertTrue(SanctuaryMigration.upgrade(chunk)>=1,"Old palette was not upgraded");
            var b=(SanctuaryBlockEntity)chunk.getBlockEntity(p);
            h.assertTrue(b!=null && b.getType().isValid(state),"Migrated BE type mismatch");
            h.assertValueEqual(SanctuaryMigration.upgrade(chunk),0,"Migration recreated existing BEs");
            var saved=b.saveWithFullMetadata(level.registryAccess());
            h.assertValueEqual(saved.getString("id"),"orimod:sanctuary","Missing chunk network/save identity");
            h.assertTrue(b.getUpdatePacket()!=null,"No incremental update packet");
            h.assertTrue(!Shapes.joinIsNotEmpty(state.getCollisionShape(level,p),Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"Old collision changed");
            h.assertTrue(level.isEmptyBlock(p.above()) && level.isEmptyBlock(p.east()),"Visual wrote extra blocks");
        });
        add(tests,"map_activation_and_victory_survive_save_and_client_tag",h->{
            var p=h.absolutePos(new BlockPos(5,3,5));h.getLevel().setBlock(p,OriBlocks.MAP_STONE.get().defaultBlockState(),3);
            SanctuaryBlockEntity.activate(h.getLevel(),p);
            var b=(SanctuaryBlockEntity)h.getLevel().getBlockEntity(p);
            var client=new SanctuaryBlockEntity(p,b.getBlockState());client.loadWithComponents(b.getUpdateTag(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(client.activated() && client.phase()==SanctuaryBlockEntity.Phase.ACTIVE,"Client activation missing");
            SanctuaryBlockEntity.victory(h.getLevel(),p);
            var reload=new SanctuaryBlockEntity(p,b.getBlockState());reload.loadWithComponents(b.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(reload.phase()==SanctuaryBlockEntity.Phase.DEFEATED,"Victory lost on reload");
            h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(h.getLevel().getBlockEntity(p)==null,"Removed sanctuary retained BE");
        });
        for(boolean storm:List.of(false,true))add(tests,"altar_summon_and_death_"+storm,h->{
            try(var c=Fixtures.connectedPlayer(h)) {
                var p=h.absolutePos(new BlockPos(5,3,5));h.getLevel().setBlock(p,(storm?OriBlocks.STORM_ALTAR:OriBlocks.GLOOM_ALTAR).get().defaultBlockState(),3);
                var boss=storm?StormAltarBlock.summon(c.player(),p):GloomAltarBlock.summon(c.player(),p);
                try {
                    h.assertTrue(boss!=null,"Summon failed");
                    var b=(SanctuaryBlockEntity)h.getLevel().getBlockEntity(p);
                    h.assertTrue(b.phase()==SanctuaryBlockEntity.Phase.ACTIVE,"Summon did not activate architecture");
                    boss.hurt(h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
                    h.assertTrue(b.phase()==SanctuaryBlockEntity.Phase.DEFEATED,"Boss victory did not update altar");
                } finally {if(boss!=null)boss.discard();}
            }
        });
        add(tests,"door_keeps_shared_slots_and_immediate_air",h->{
            try(var c=Fixtures.connectedPlayer(h)) {
                var p=h.absolutePos(new BlockPos(5,3,5));var cells=List.of(p,p.above(),p.east(),p.east().above());
                for(var pos:cells)h.getLevel().setBlock(pos,OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(),3);
                var keys=new ItemStack(OriItems.KEYSTONE.get(),2);
                h.assertValueEqual(KeystoneDoorBlock.insert(c.player(),p,keys),1,"First key refused");
                for(var pos:cells)h.assertValueEqual(h.getLevel().getBlockState(pos).getValue(KeystoneDoorBlock.FILLED),1,"Slot not shared");
                h.assertValueEqual(KeystoneDoorBlock.insert(c.player(),p,keys),0,"Second key refused");
                for(var pos:cells)h.assertTrue(h.getLevel().isEmptyBlock(pos) && h.getLevel().getBlockEntity(pos)==null,"Animation delayed collision removal");
            }
        });
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("sanctuary","sanctuary."+name,"orimod_gametest:room",80,0,true,h->{run.accept(h);h.succeed();}));
    }
}
