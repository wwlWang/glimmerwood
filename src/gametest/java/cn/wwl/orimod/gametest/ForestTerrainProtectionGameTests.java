package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("orimod_forest_red")
public final class ForestTerrainProtectionGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();for(var r:Rotation.values())out.add(new TestFunction("forest_protection","forest_protection.all_paths_"+r.name().toLowerCase(),
                "orimod_forest_red:room",Rotation.NONE,900,0,true,h->check(h,r)));return out;
    }
    private static void check(GameTestHelper h,Rotation r){
        try(var s=new ForestSpringLayoutGameTests.Site(h,r,24800+r.ordinal()*160,9000,true,1)){
            var l=h.getLevel();var cases=new LinkedHashMap<BlockPos,Block>();
            cases.put(new BlockPos(19,7,6),Blocks.CHEST); // explicit air
            cases.put(new BlockPos(18,7,6),Blocks.BEDROCK);
            cases.put(new BlockPos(17,5,6),Blocks.BEDROCK); // authored solid
            cases.put(new BlockPos(16,-2,0),Blocks.CHEST); // foundation
            cases.put(new BlockPos(-2,3,20),Blocks.CHEST); // grading rim
            cases.put(new BlockPos(10,8,51),Blocks.CHEST); // missing-cell supplement
            cases.put(new BlockPos(28,9,51),Blocks.BEDROCK);
            cases.put(new BlockPos(20,8,6),Blocks.OAK_WALL_SIGN); // support gets cleared; edge update must not delete BE
            cases.put(new BlockPos(19,5,-4),Blocks.CHEST); // separate approach
            cases.put(new BlockPos(20,7,-4),Blocks.OAK_LOG); // outside LAND: keep protected trunk
            cases.put(new BlockPos(-2,4,22),Blocks.OAK_LOG); // graded rim: keep protected trunk
            var entities=new HashMap<BlockPos,CompoundTag>();
            for(var e:cases.entrySet()){
                var p=s.p.block(e.getKey());
                if(e.getValue()==Blocks.OAK_WALL_SIGN)ForestPolicyRedGameTests.set(l,p.south(),Blocks.STONE.defaultBlockState());
                ForestPolicyRedGameTests.set(l,p,e.getValue().defaultBlockState());
                if(l.getBlockEntity(p)!=null)entities.put(p,l.getBlockEntity(p).saveWithFullMetadata(l.registryAccess()));
            }
            for(var e:cases.entrySet())h.assertTrue(l.getBlockState(s.p.block(e.getKey())).is(e.getValue()),"Fixture overwrote its own witness "+e.getKey());
            var context=StructurePieceSerializationContext.fromLevel(l);var piece=s.piece;
            int[] samples=new int[45];Arrays.fill(samples,s.p.origin().getY()+4);
            var approach=new RuinApproachPiece(RuinApproachPiece.doorway(RuinKind.FOREST,piece.layout(),s.p),samples,false,true,piece.forestApproachSideProfile());
            var approachTag=approach.createTag(context);h.assertValueEqual(approachTag.getInt("ForestTerrainVersion"),1,"Approach lost protection version");approach=new RuinApproachPiece(approachTag);
            String expected=null;RuinFreezeSnapshot.Image first=null;
            for(int round=0;round<3;round++){
                var b=piece.getBoundingBox();var chunks=new ArrayList<ChunkPos>();for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)chunks.add(new ChunkPos(x,z));
                if(round%2==1)Collections.reverse(chunks);
                for(var cp:chunks){var clip=new BoundingBox(cp.getMinBlockX(),l.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),l.getMaxBuildHeight()-1,cp.getMaxBlockZ());
                    piece.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),clip,cp,BlockPos.ZERO);
                    approach.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),clip,cp,BlockPos.ZERO);
                }
                for(var e:cases.entrySet())h.assertTrue(l.getBlockState(s.p.block(e.getKey())).is(e.getValue()),"Hard protected "+e.getKey()+" overwritten round "+round);
                for(var e:entities.entrySet())h.assertValueEqual(l.getBlockEntity(e.getKey()).saveWithFullMetadata(l.registryAccess()),e.getValue(),"Protected BE changed round "+round);
                var image=RuinFreezeSnapshot.capture(l,b);
                if(first==null)first=image;else {
                    int difference=-1;for(int i=0;i<first.states().size();i++)if(!first.states().get(i).equals(image.states().get(i))){difference=i;break;}
                    String detail="";if(difference>=0){int w=b.getXSpan(),height=b.getYSpan();
                        var at=new BlockPos(b.minX()+difference%w,b.minY()+difference/w%height,b.minZ()+difference/w/height);
                        detail=" at "+at+" before="+first.states().get(difference)+" after="+image.states().get(difference)+" round="+round;
                        System.out.println("FOREST_REPLAY_DIFF rotation="+r+detail);
                        RuinFreezeSnapshot.save(first,b,"forest-replay-"+r+"-before");RuinFreezeSnapshot.save(image,b,"forest-replay-"+r+"-after");}
                    h.assertTrue(difference<0,"Repeated/reloaded forest write changed cell index "+difference+detail);
                    h.assertValueEqual(image.entities(),first.entities(),"Repeated forest write changed BE");
                }
                var tag=piece.createTag(context);h.assertValueEqual(tag.getInt("ForestTerrainVersion"),1,"Version missing");
                h.assertValueEqual(tag.getIntArray("ForestNaturalTerrain").length,79*95,"Noise snapshot missing");
                String signature=Arrays.toString(tag.getIntArray("ForestNaturalTerrain"));
                if(expected==null)expected=signature;else h.assertValueEqual(signature,expected,"Saved noise snapshot changed on replay");
                piece=new RuinPiece(l.getStructureManager(),tag);
            }
            System.out.println("FOREST_HARD_PROTECTION rotation="+r+" cells="+cases.size()+" rounds=3 reload=2 snapshot=immutable");h.succeed();
        }
    }
}
