package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class GreatTreeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        return List.of(new TestFunction("great_tree","great_tree.clips_every_write_and_keeps_adjacent_terrain","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var origin=h.absolutePos(new BlockPos(5,150,5));
            var site=new GreatTreeSite.Site(origin,BlockPos.ZERO,115115,1);var piece=new GreatTreePiece(site);
            var clip=new BoundingBox(origin.getX()+4,origin.getY()+20,origin.getZ(),origin.getX()+6,origin.getY()+24,origin.getZ()+2);
            var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            // This high slice lies in the fixture's own already-loaded chunks, away from its floor and other tests.
            for(var p:BlockPos.betweenClosed(clip.minX()-1,clip.minY()-1,clip.minZ()-1,clip.maxX()+1,clip.maxY()+1,clip.maxZ()+1))before.put(p.immutable(),level.getBlockState(p));
            // Direct postProcess uses FULL chunks here, unlike normal worldgen's ProtoChunks.
            // Supply the WG heightmap explicitly rather than invoking its IDE-only error fallback.
            for(int cx=clip.minX()>>4;cx<=clip.maxX()>>4;cx++)for(int cz=clip.minZ()>>4;cz<=clip.maxZ()>>4;cz++){
                var chunk=level.getChunk(cx,cz);
                if(!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))
                    Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
            }
            try{
                piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(origin),origin);
                int wood=0;
                for(var p:before.keySet())if(clip.isInside(p)){if(level.getBlockState(p).is(OriBlocks.GREAT_SPIRIT_WOOD)||level.getBlockState(p).is(OriBlocks.GREAT_SPIRIT_ROOT))wood++;}
                else h.assertValueEqual(level.getBlockState(p),before.get(p),"Write escaped slice: "+p);
                h.assertTrue(wood>0,"Fixture did not intersect the trunk");h.succeed();
            }finally{before.forEach((p,s)->level.setBlock(p,s,2));}
        }),new TestFunction("great_tree","great_tree.saved_choice_latches_after_generation","orimod_gametest:room",80,0,true,h->{
            var data=new GreatTreeSite();var site=new GreatTreeSite.Site(new BlockPos(600,74,8),new BlockPos(0,64,0),42,1);
            data.choose(site);data.begun(true);
            var loaded=GreatTreeSite.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(loaded.begun()&&loaded.site().equals(site),"Unique site lost on reload");h.succeed();
        }));
    }
}
