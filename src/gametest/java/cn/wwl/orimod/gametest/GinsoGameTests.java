package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class GinsoGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(
        new TestFunction("ginso","ginso_generated_footprint_rejected_without_loading","orimod_gametest:room",40,0,true,h->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(2,3,2));
            int before=level.getChunkSource().getLoadedChunksCount();
            var result=GinsoGenerationGuard.check(level.getChunkSource().randomState(),at,3);
            h.assertTrue(!result.allowed(),"Generated room was accepted as a new tree footprint");
            h.assertTrue(!result.source().equals("level-unavailable"),"Guard lifecycle not installed");
            h.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),before,"Read-only guard loaded chunks");h.succeed();
        }),
        new TestFunction("ginso","ginso.saved_shape_and_clip_boundaries","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,150,3));
            var original=new GinsoPiece(origin,115117);var piece=new GinsoPiece(original.createTag(StructurePieceSerializationContext.fromLevel(level)));
            h.assertValueEqual(piece.origin(),origin,"Origin after load");h.assertTrue(piece.shapeSeed()==115117,"Seed after load");
            var clip=new BoundingBox(origin.getX()+6,origin.getY()+20,origin.getZ(),origin.getX()+9,origin.getY()+23,origin.getZ()+3);
            var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(var p:BlockPos.betweenClosed(clip.minX()-1,clip.minY()-1,clip.minZ()-1,clip.maxX()+1,clip.maxY()+1,clip.maxZ()+1))before.put(p.immutable(),level.getBlockState(p));
            for(int x=clip.minX()>>4;x<=clip.maxX()>>4;x++)for(int z=clip.minZ()>>4;z<=clip.maxZ()>>4;z++){
                var chunk=level.getChunk(x,z);if(!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
            }
            try{
                piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(origin),origin);
                int writes=0;for(var p:before.keySet())if(clip.isInside(p)){if(level.getBlockState(p).is(OriBlocks.GINSO_WOOD))writes++;}
                else h.assertValueEqual(level.getBlockState(p),before.get(p),"Write escaped clip "+p);
                h.assertTrue(writes>0,"Slice missed bole");h.succeed();
            }finally{before.forEach((p,s)->level.setBlock(p,s,2));}
        }),
        new TestFunction("ginso","ginso_legacy_piece_keeps_v2_v1_after_save_and_chunk_write","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,150,3));
            var context=StructurePieceSerializationContext.fromLevel(level);
            var tag=new GinsoPiece(origin,115117,0,0,2,1).createTag(context);
            var loaded=new GinsoPiece(tag);var saved=loaded.createTag(context);
            h.assertValueEqual(saved.getInt("ShapeVersion"),2,"Old shape version upgraded during save");
            h.assertValueEqual(saved.getInt("CourseVersion"),1,"Old course version upgraded during save");
            h.assertValueEqual(loaded.getBoundingBox().getYSpan(),73,"Old piece bounds expanded");
            var clip=new BoundingBox(origin.getX()+6,origin.getY()+20,origin.getZ(),origin.getX()+9,origin.getY()+23,origin.getZ()+3);
            var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(var at:BlockPos.betweenClosed(clip.minX(),clip.minY(),clip.minZ(),clip.maxX(),clip.maxY(),clip.maxZ()))before.put(at.immutable(),level.getBlockState(at));
            try{
                for(int x=clip.minX()>>4;x<=clip.maxX()>>4;x++)for(int z=clip.minZ()>>4;z<=clip.maxZ()>>4;z++){
                    var chunk=level.getChunk(x,z);if(!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                }
                loaded.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(origin),origin);
                var field=new GinsoShape(115117,2);
                for(var at:before.keySet()){
                    var local=at.subtract(origin);byte old=field.cell(local.getX(),local.getY(),local.getZ());
                    byte inside=loaded.course().cell(local.getX(),local.getY(),local.getZ());if(inside!=0)old=inside;
                    if(GinsoShape.timber(old)){
                        var expected=OriBlocks.GINSO_WOOD.get().defaultBlockState()
                            .setValue(cn.wwl.orimod.block.GinsoWoodBlock.VEIN,old==GinsoShape.SAP)
                            .setValue(cn.wwl.orimod.block.GinsoWoodBlock.MOSS,field.moss(local.getX(),local.getY(),local.getZ()));
                        h.assertValueEqual(level.getBlockState(at),expected,"Legacy state mismatch at "+local);
                    }else if(old==GinsoShape.AIR)h.assertTrue(level.getBlockState(at).isAir(),"Legacy air filled");
                    else if(old==GinsoShape.EMPTY)h.assertValueEqual(level.getBlockState(at),before.get(at),"Legacy empty cell written");
                }
                h.succeed();
            }finally{before.forEach((p,state)->level.setBlock(p,state,2));}
        }),
        new TestFunction("ginso_support","ginso_stilt_feet_extend_to_each_sloped_floor","orimod_gametest:room",200,0,true,h->{
            var level=h.getLevel();var origin=h.absolutePos(new BlockPos(160,140,0));var piece=new GinsoPiece(origin,115117);
            var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();var pinned=new ArrayList<ChunkPos>();
            var feet=new ArrayList<BlockPos>();var floors=new ArrayList<Integer>();
            try{
                for(int x=(origin.getX()-28)>>4;x<=(origin.getX()+28)>>4;x++)for(int z=(origin.getZ()-28)>>4;z<=(origin.getZ()+28)>>4;z++){
                    if(level.setChunkForced(x,z,true))pinned.add(new ChunkPos(x,z));level.getChunk(x,z);
                }
                int i=0;
                for(var foot:GinsoSiteRules.rootFeet(115117)){
                    BlockPos toe=null;
                    for(int x=foot[0]-1;x<=foot[0]+1&&toe==null;x++)for(int z=foot[1]-1;z<=foot[1]+1&&toe==null;z++)
                        for(int y=-5;y<=0;y++)if(GinsoShape.timber(piece.sculpture().cell(x,y,z))){toe=origin.offset(x,y,z);break;}
                    h.assertTrue(toe!=null,"Root toe missing from shape");int floor=origin.getY()-4-(i++%3)*4;
                    feet.add(toe);floors.add(floor);
                    for(var p:BlockPos.betweenClosed(toe.getX()-2,floor-1,toe.getZ()-2,toe.getX()+2,origin.getY()+3,toe.getZ()+2)){
                        before.putIfAbsent(p.immutable(),level.getBlockState(p));
                        level.setBlock(p,p.getY()<=floor?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                    }
                }
                for(var chunk:pinned)Heightmap.primeHeightmaps(level.getChunk(chunk.x,chunk.z),EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                for(int f=0;f<feet.size();f++){
                    var toe=feet.get(f);int floor=floors.get(f);
                    var clip=new BoundingBox(toe.getX()-2,floor-1,toe.getZ()-2,toe.getX()+2,origin.getY()+3,toe.getZ()+2);
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(toe),origin);
                    h.assertTrue(level.getBlockState(new BlockPos(toe.getX(),floor,toe.getZ())).is(net.minecraft.world.level.block.Blocks.STONE),"Root removed real supporting floor");
                    for(int y=floor+1;y<=toe.getY();y++)h.assertTrue(level.getBlockState(new BlockPos(toe.getX(),y,toe.getZ())).is(OriBlocks.GINSO_WOOD),"Floating root over slope at "+toe+" y="+y);
                }
                h.succeed();
            }finally{before.forEach((p,state)->level.setBlock(p,state,2));for(var chunk:pinned)level.setChunkForced(chunk.x,chunk.z,false);}
        }),
        new TestFunction("ginso","ginso_moss_hangs_from_own_leaf_tag","orimod_gametest:room",40,0,true,h->{
            var p=h.absolutePos(new BlockPos(2,3,2));var level=h.getLevel();var old=level.getBlockState(p);var upper=level.getBlockState(p.above());
            try{for(var leaves:List.of(OriBlocks.GINSO_LEAVES.get(),OriBlocks.GINSO_CANOPY.get(),OriBlocks.GINSO_LEAF_CORE.get())){
                level.setBlock(p.above(),leaves.defaultBlockState(),2);var moss=OriBlocks.GINSO_VINE.get().defaultBlockState();h.assertTrue(moss.canSurvive(level,p),"Ginso moss lacks support tag: "+leaves);h.assertTrue(moss.getCollisionShape(level,p).isEmpty(),"Hanging moss blocks traversal");}h.succeed();}
            finally{level.setBlock(p,old,2);level.setBlock(p.above(),upper,2);}
        }),
        new TestFunction("ginso","ginso_new_canopy_keeps_legacy_state_and_foliage_behaviour","orimod_gametest:room",40,0,true,h->{
            var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));
            var old=OriBlocks.GINSO_LEAVES.get().defaultBlockState();var leaf=OriBlocks.GINSO_CANOPY.get().defaultBlockState();var core=OriBlocks.GINSO_LEAF_CORE.get().defaultBlockState();
            h.assertTrue(old.getValues().isEmpty(),"Added a property to frozen v2 leaves");
            for(var state:List.of(old,leaf,core)){
                h.assertTrue(state.is(net.minecraft.tags.BlockTags.LEAVES),"Missing leaves tag");
                h.assertValueEqual(state.getLightEmission(level,p),0,"Canopy must retain ambient wet moss lighting");
                h.assertTrue(!state.emissiveRendering(level,p)&&!state.isSuffocating(level,p)&&!state.isViewBlocking(level,p),"Changed foliage behaviour");
                for(var tag:List.of(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE,net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE))
                    h.assertValueEqual(state.is(tag),old.is(tag),"Mineable tag differs from legacy leaves");
            }
            h.assertTrue(core.canOcclude()&&core.isSolidRender(level,p),"Backing must be opaque");
            for(var face:net.minecraft.core.Direction.values()){
                h.assertTrue(leaf.skipRendering(leaf,face)&&leaf.skipRendering(core,face),"Internal cutout face retained");
                h.assertTrue(!old.skipRendering(old,face)&&!core.skipRendering(leaf,face),"Legacy changed or opaque backdrop hidden");
            }h.succeed();
        }),
        new TestFunction("ginso","ginso_piece_writes_versioned_leaf_blocks","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,80,3));var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            try{for(int version:new int[]{2,3}){
                var piece=new GinsoPiece(origin,115117,0,0,version,GinsoCourses.forShape(version));var shape=piece.sculpture();
                var samples=new LinkedHashMap<Boolean,BlockPos>();
                for(int x=-GinsoShape.radius(version);x<=GinsoShape.radius(version);x++)for(int z=-GinsoShape.radius(version);z<=GinsoShape.radius(version);z++)for(int y=41;y<=GinsoShape.maxY(version);y++)
                    if(shape.cell(x,y,z)==GinsoShape.LEAF)samples.putIfAbsent(shape.coreLeaf(x,y,z),new BlockPos(x,y,z));
                h.assertValueEqual(samples.size(),version==2?1:2,"Missing skin/core fixture samples");
                for(var sample:samples.entrySet()){
                    var local=sample.getValue();var at=origin.offset(local);before.putIfAbsent(at,level.getBlockState(at));
                    var chunk=level.getChunk(at);Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),new BoundingBox(at),new ChunkPos(at),origin);
                    var expected=version==2?OriBlocks.GINSO_LEAVES.get():sample.getKey()?OriBlocks.GINSO_LEAF_CORE.get():OriBlocks.GINSO_CANOPY.get();
                    h.assertValueEqual(level.getBlockState(at),expected.defaultBlockState(),"Wrong versioned leaf write");
                }
            }h.succeed();}finally{before.forEach((p,state)->level.setBlock(p,state,2));}
        }),
        new TestFunction("ginso","ginso_dim_growth_does_not_light_whole_bark_or_block_route","orimod_gametest:room",40,0,true,h->{
            var p=h.absolutePos(new BlockPos(2,3,2));var level=h.getLevel();
            var wood=OriBlocks.GINSO_WOOD.get().defaultBlockState();
            h.assertValueEqual(wood.getLightEmission(level,p),0,"Unlit wet bark");
            h.assertValueEqual(wood.setValue(cn.wwl.orimod.block.GinsoWoodBlock.MOSS,true).getLightEmission(level,p),0,"Ordinary moss must not emit");
            h.assertValueEqual(wood.setValue(cn.wwl.orimod.block.GinsoWoodBlock.VEIN,true).getLightEmission(level,p),8,"Crack light");
            var shelf=OriBlocks.GINSO_FUNGUS.get().defaultBlockState();
            h.assertValueEqual(shelf.getLightEmission(level,p),8,"Shelf light");
            h.assertTrue(shelf.getCollisionShape(level,p).isEmpty(),"Fungus blocks the route");
            h.assertValueEqual(OriBlocks.GINSO_VINE.get().defaultBlockState().getLightEmission(level,p),4,"Dim water drips");h.succeed();
        })
    );}
}
