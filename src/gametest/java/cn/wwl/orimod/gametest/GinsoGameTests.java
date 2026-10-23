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
        new TestFunction("ginso","ginso_moss_hangs_from_own_leaf_tag","orimod_gametest:room",40,0,true,h->{
            var p=h.absolutePos(new BlockPos(2,3,2));var level=h.getLevel();var old=level.getBlockState(p);var upper=level.getBlockState(p.above());
            try{level.setBlock(p.above(),OriBlocks.GINSO_LEAVES.get().defaultBlockState(),2);var moss=OriBlocks.GINSO_VINE.get().defaultBlockState();h.assertTrue(moss.canSurvive(level,p),"Ginso moss lacks support tag");h.assertTrue(moss.getCollisionShape(level,p).isEmpty(),"Hanging moss blocks traversal");h.succeed();}
            finally{level.setBlock(p,old,2);level.setBlock(p.above(),upper,2);}
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
