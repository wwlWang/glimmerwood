package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class GreatTreeScaleGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(
        new TestFunction("great_tree_core_bloom","great_tree.dense_backing_blooms_and_resets_with_outer_skin","orimod_gametest:room",180,0,true,h->{
            var level=h.getLevel();var server=level.getServer();var at=h.absolutePos(new BlockPos(3,2,3));
            var old=level.getBlockState(at);var core=OriBlocks.GREAT_TREE_LEAF_CORE.get().defaultBlockState();
            cn.wwl.orimod.progression.Restoration.reset(server);level.setBlock(at,core,2);
            h.runAfterDelay(10,()->{RestorationData.get(server).complete();RestorationBloom.refresh(server);});
            h.runAfterDelay(70,()->{
                try{
                    h.assertTrue(level.getBlockState(at).getValue(SpiritLeavesBlock.BLOOMED),"Opaque backing stayed dark");
                    h.assertValueEqual(level.getBlockState(at).getLightEmission(),12,"Bloom light mismatch");
                }catch(Throwable e){level.setBlock(at,old,2);cn.wwl.orimod.progression.Restoration.reset(server);throw e;}
                cn.wwl.orimod.progression.Restoration.reset(server);
            });
            h.runAfterDelay(140,()->{try{h.assertFalse(level.getBlockState(at).getValue(SpiritLeavesBlock.BLOOMED),"Backing not reversible");h.assertValueEqual(level.getBlockState(at).getLightEmission(),9,"Reset light mismatch");h.succeed();}finally{level.setBlock(at,old,2);cn.wwl.orimod.progression.Restoration.reset(server);}});
        }),
        new TestFunction("great_tree_scale","great_tree.dense_backing_occludes_but_exposed_leaf_skin_stays_cutout","orimod_gametest:room",40,0,true,h->{
            var pos=h.absolutePos(new BlockPos(3,2,3));var level=h.getLevel();
            var core=OriBlocks.GREAT_TREE_LEAF_CORE.get().defaultBlockState();
            var leaf=OriBlocks.SPIRIT_LEAVES.get().defaultBlockState().setValue(SpiritLeavesBlock.GREAT_TREE,true);
            h.assertTrue(core.canOcclude()&&core.isSolidRender(level,pos)&&core.emissiveRendering(level,pos),"Backing not opaque/fullbright");
            h.assertFalse(leaf.canOcclude(),"Outer leaves became opaque");
            for(var face:net.minecraft.core.Direction.values())h.assertTrue(leaf.skipRendering(core,face),"Hidden skin/core face retained "+face);
            h.succeed();
        }),
        new TestFunction("great_tree_scale","great_tree.adjacent_leaf_faces_are_culled_only_for_new_landmarks","orimod_gametest:room",40,0,true,h->{
            var normal=OriBlocks.SPIRIT_LEAVES.get().defaultBlockState();
            var great=normal.setValue(SpiritLeavesBlock.GREAT_TREE,true);
            var bloomed=great.setValue(SpiritLeavesBlock.BLOOMED,true);
            for(var face:net.minecraft.core.Direction.values()){
                h.assertTrue(great.skipRendering(great,face)&&great.skipRendering(bloomed,face)&&bloomed.skipRendering(great,face),"Internal face retained "+face);
                h.assertTrue(!normal.skipRendering(normal,face)&&!great.skipRendering(normal,face)&&!normal.skipRendering(great,face),"Changed old/Ancestral foliage "+face);
                h.assertTrue(!great.skipRendering(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),face),"Outer cutout face hidden "+face);
            }
            h.assertTrue(bloomed.getValue(SpiritLeavesBlock.GREAT_TREE),"Restoration lost rendering flag");h.succeed();
        }),
        new TestFunction("great_tree_scale","great_tree.scale_versions_keep_distinct_saved_bounds","orimod_gametest:room",40,0,true,h->{
            for(int version=1;version<=2;version++){
                var site=new GreatTreeSite.Site(new BlockPos(600,70,8),BlockPos.ZERO,115115,version);
                var p=new GreatTreePiece(site);var tag=p.createTag(StructurePieceSerializationContext.fromLevel(h.getLevel()));
                var loaded=new GreatTreePiece(tag);var box=loaded.getBoundingBox();
                h.assertValueEqual(box.getXSpan(),2*GreatTreeShape.radius(version)+1,"Saved horizontal extent");
                h.assertValueEqual(box.maxY(),70+GreatTreeShape.maxY(version),"Saved crown height");
                h.assertValueEqual(tag.getInt("ShapeVersion"),version,"Shape version");
            }h.succeed();
        }),
        new TestFunction("great_tree_scale","great_tree.natural_hostile_backup_leaves_other_spawn_paths_alone","orimod_gametest:room",40,0,true,h->{
            var at=h.absolutePos(new BlockPos(3,2,3));var site=new GreatTreeSite.Site(at.below(),BlockPos.ZERO,1,2);
            var zombie=EntityType.ZOMBIE.create(h.getLevel());var cow=EntityType.COW.create(h.getLevel());
            try{
                zombie.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);cow.setPos(zombie.position());
                for(var reason:MobSpawnType.values()){
                    var event=new MobSpawnEvent.PositionCheck(zombie,h.getLevel(),reason,null);GreatTreeSanctuary.check(event,site);
                    h.assertTrue((event.getResult()==MobSpawnEvent.PositionCheck.Result.FAIL)==(reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION),"Changed unrelated spawn path "+reason);
                }
                var passive=new MobSpawnEvent.PositionCheck(cow,h.getLevel(),MobSpawnType.NATURAL,null);GreatTreeSanctuary.check(passive,site);
                h.assertTrue(passive.getResult()!=MobSpawnEvent.PositionCheck.Result.FAIL,"Passive animals blocked");
                zombie.setPos(at.getX()+85,at.getY(),at.getZ());var outside=new MobSpawnEvent.PositionCheck(zombie,h.getLevel(),MobSpawnType.NATURAL,null);GreatTreeSanctuary.check(outside,site);
                h.assertTrue(outside.getResult()!=MobSpawnEvent.PositionCheck.Result.FAIL,"Protection escaped footprint");h.succeed();
            }finally{zombie.discard();cow.discard();}
        }),
        new TestFunction("great_tree_scale","great_tree.ground_knots_cast_real_light","orimod_gametest:room",100,0,true,h->{
            var at=new BlockPos(3,2,3);var world=h.absolutePos(at);var before=h.getLevel().getBlockState(world);
            h.setBlock(at,OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(GreatSpiritRootBlock.LIGHT,GreatSpiritRootBlock.Light.KNOT));
            h.runAfterDelay(20,()->{
                try{h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,world.above())>=13,"Only emissive, no real ground light");h.succeed();}
                finally{h.getLevel().setBlock(world,before,2);}
            });
        })
    );}
}
