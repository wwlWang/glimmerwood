package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("orimod_forest_freeze")
public final class ForestFreezeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(int v:new int[]{0,1})for(var r:Rotation.values()){
            int version=v;out.add(new TestFunction("forest_freeze","forest_freeze.v"+v+"_"+r.name().toLowerCase(),"orimod_forest_freeze:room",Rotation.NONE,500,0,true,h->freeze(h,version,r)));
        }return out;
    }
    private static void freeze(GameTestHelper h,int version,Rotation rotation){
        var l=h.getLevel();var origin=new BlockPos(35000+rotation.ordinal()*256,60,8000+version*256);
        var placement=RuinPlacement.atMinimum(RuinKind.FOREST.layout(version),origin,rotation);
        int[] approach=version==0?new int[0]:new int[45];Arrays.fill(approach,64);
        var context=StructurePieceSerializationContext.fromLevel(l);net.minecraft.nbt.CompoundTag old;
        String resource="/r116/forest-terrain-starts/forest-v"+version+"-"+rotation+"-start.snbt";
        try(var in=ForestFreezeGameTests.class.getResourceAsStream(resource)){
            if(in==null)throw new IllegalStateException("Missing immutable old start "+resource);
            old=net.minecraft.nbt.TagParser.parseTag(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }catch(Exception e){throw new IllegalStateException("Old start fixture",e);}
        h.assertFalse(old.contains("ForestTerrainVersion"),"Saved fixture acquired new policy field");
        var piece=new RuinPiece(l.getStructureManager(),old.copy());
        var bounds=piece.getBoundingBox().inflatedBy(13);var tickets=new ArrayList<ChunkPos>();
        try{
            for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){
                var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);tickets.add(cp);}l.getChunk(x,z);
            }
            reset(l,bounds,placement);var slices=slices(piece.getBoundingBox(),l);
            var baseline=new RuinPiece(l.getStructureManager(),old.copy());for(var cp:slices)write(l,baseline,cp);
            var before=RuinFreezeSnapshot.capture(l,bounds);
            RuinFreezeSnapshot.save(before,bounds,"forest-v"+version+"-"+rotation);
            RuinFreezeSnapshot.verifyPinned(before,bounds,"forest-v"+version+"-"+rotation);RuinFreezeSnapshot.tag("forest-v"+version+"-"+rotation+"-start",old);
            reset(l,bounds,placement);var restored=new RuinPiece(l.getStructureManager(),old.copy());int half=slices.size()/2;
            for(int i=0;i<half;i++)write(l,restored,slices.get(i));
            var partial=restored.createTag(context);h.assertFalse(partial.contains("ForestTerrainVersion"),"Baseline unexpectedly has forest terrain version");
            RuinFreezeSnapshot.tag("forest-v"+version+"-"+rotation+"-partial",partial);
            restored=new RuinPiece(l.getStructureManager(),partial);for(int i=half;i<slices.size();i++)write(l,restored,slices.get(i));
            var after=RuinFreezeSnapshot.capture(l,bounds);
            h.assertValueEqual(restored.layoutVersion(),version,"Frozen layout changed");
            int different=0;var witnesses=new ArrayList<String>();for(int i=0;i<before.states().size();i++)if(!before.states().get(i).equals(after.states().get(i))){different++;if(witnesses.size()<8)witnesses.add(i+":"+before.states().get(i)+" -> "+after.states().get(i));}
            h.assertTrue(different==0,"Saved start altered remaining neighbour slices: "+different+" "+witnesses);
            h.assertValueEqual(after.entities(),before.entities(),"Saved start BE changed");h.assertValueEqual(after.fluids(),before.fluids(),"Saved start scheduled fluids changed");
            System.out.println("FOREST_FREEZE version="+version+" rotation="+rotation+" cells="+before.states().size()+" BE="+before.entities().size()+" slices="+slices.size()+" stateDiff=0 beDiff=0 fluidDiff=0");h.succeed();
        }finally{CreekFixtureChunks.release(l,tickets);}
    }
    static List<ChunkPos> slices(BoundingBox b,ServerLevel l){var out=new ArrayList<ChunkPos>();for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)out.add(new ChunkPos(x,z));return out;}
    static void write(ServerLevel l,RuinPiece p,ChunkPos cp){p.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116^cp.toLong()),new BoundingBox(cp.getMinBlockX(),l.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),l.getMaxBuildHeight()-1,cp.getMaxBlockZ()),cp,BlockPos.ZERO);}
    static void reset(ServerLevel l,BoundingBox b,RuinPlacement placement){
        l.getBlockTicks().clearArea(b);l.getFluidTicks().clearArea(b);
        for(var p:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())){
            var local=placement.local(net.minecraft.world.phys.Vec3.atLowerCornerOf(p));int ground=placement.origin().getY()+2+(Math.floorMod((int)local.x/5+(int)local.z/7,3));
            if(l.getBlockState(p).hasBlockEntity())l.removeBlockEntity(p);
            l.setBlock(p,(p.getY()<ground?Blocks.DIRT:p.getY()==ground?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
        }l.getBlockTicks().clearArea(b);l.getFluidTicks().clearArea(b);l.random.setSeed(116);
    }
}
