package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("orimod_forest_approach")
public final class ForestApproachGuardGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(var r:Rotation.values())out.add(new TestFunction("forest_approach","forest_approach.cave_edge_"+r.name().toLowerCase(),
                "orimod_forest_approach:room",Rotation.NONE,900,0,true,h->check(h,r)));
        for(var r:Rotation.values())out.add(new TestFunction("forest_approach","forest_approach.combined_order_"+r.name().toLowerCase(),
                "orimod_forest_approach:room",Rotation.NONE,1800,0,true,h->CreekConcurrentSliceGameTests.forestApproach(h,r)));
        return out;
    }
    private static void check(GameTestHelper h,Rotation r){
        var l=h.getLevel();var forced=new ArrayList<ChunkPos>();
        var p=new RuinPlacement(h.absolutePos(new BlockPos(400+r.ordinal()*96,35,700)),r);
        var box=p.blocks(new BlockPos(-5,-12,-11),new BlockPos(5,8,1));
        try{
            for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++)for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++){
                var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
            }
            for(int x=-5;x<=5;x++)for(int z=-11;z<=1;z++)for(int y=-12;y<=8;y++){
                int ground=x<=-3&&z>=-9&&z<=-1?-9:-1;
                ForestPolicyRedGameTests.set(l,p.block(new BlockPos(x,y,z)),(y<=ground?Blocks.STONE:Blocks.AIR).defaultBlockState());
            }
            int[] terrain=new int[45];Arrays.fill(terrain,p.origin().getY());
            var a=new RuinApproachPiece(p,terrain,false,true);
            var tag=a.createTag(StructurePieceSerializationContext.fromLevel(l));
            // Explicit immutable side profile: N=P=road floor, graded neighbour;
            // the old code ignores this future field and fails at the first missing wall.
            int[] sides=new int[54];for(int i=0;i<18;i++){sides[i*3]=p.origin().getY()-1;sides[i*3+1]=p.origin().getY()-1;sides[i*3+2]=1;}
            tag.putIntArray("ForestApproachSides",sides);a=new RuinApproachPiece(tag);
            var b=a.getBoundingBox();var chunks=new ArrayList<ChunkPos>();
            for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)chunks.add(new ChunkPos(x,z));
            RuinFreezeSnapshot.Image expected=null;
            for(int round=0;round<3;round++){
                if(round==1)Collections.reverse(chunks);
            for(var cp:chunks){var clip=new BoundingBox(cp.getMinBlockX(),l.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),l.getMaxBuildHeight()-1,cp.getMaxBlockZ());
                a.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),clip,cp,BlockPos.ZERO);}
            for(int z=-9;z<=-1;z++){
                var at=p.block(new BlockPos(-2,0,z));var state=l.getBlockState(at);
                h.assertTrue(state.is(Blocks.MOSSY_COBBLESTONE_WALL)&&state.getCollisionShape(l,at).max(Direction.Axis.Y)>=1.5,
                        "Missing approach cave guard rotation="+r+" local=(-2,0,"+z+") world="+at+" state="+state+" drop=8");
                h.assertTrue(l.getBlockState(at.below()).isFaceSturdy(l,at.below(),Direction.UP),"Guard unsupported "+at);
                for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)h.assertTrue(l.getBlockState(p.block(new BlockPos(x,y,z))).getCollisionShape(l,p.block(new BlockPos(x,y,z))).isEmpty(),"Three-wide axis blocked");
                for(int y=-8;y<=1;y++)h.assertTrue(l.getBlockState(p.block(new BlockPos(-3,y,z))).isAir(),"Cave filled by approach");
            }

                var image=RuinFreezeSnapshot.capture(l,box);
                if(expected==null)expected=image;else {
                    h.assertValueEqual(image.states(),expected.states(),"Repeat/reload changed block states round="+round);
                    h.assertValueEqual(image.entities(),expected.entities(),"Repeat/reload changed BE round="+round);
                }
                var saved=a.createTag(StructurePieceSerializationContext.fromLevel(l));
                h.assertValueEqual(saved.getLongArray("ForestApproachGuardCells").length,9,"Side ownership count");
                h.assertTrue(Arrays.equals(saved.getIntArray("ForestApproachSides"),sides),"Immutable side profile changed");
                a=new RuinApproachPiece(saved);
            }
            h.succeed();
        }finally{CreekFixtureChunks.release(l,forced);}
    }
}
