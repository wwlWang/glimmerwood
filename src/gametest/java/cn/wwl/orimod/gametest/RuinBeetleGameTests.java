package cn.wwl.orimod.gametest;

import cn.wwl.orimod.entity.GloomBeetle;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class RuinBeetleGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var kind:RuinKind.values())for(var rotation:Rotation.values())tests.add(new TestFunction("ruin_beetles","ruin_beetles."+kind.id+"_"+rotation.name().toLowerCase(Locale.ROOT),"orimod_gametest:platform_room",100,0,true,h->{
            var level=h.getLevel();var placement=RuinPlacement.atMinimum(kind,h.absolutePos(new BlockPos(12,10,12)),rotation);
            var piece=new RuinPiece(level.getStructureManager(),kind,placement);var context=StructurePieceSerializationContext.fromLevel(level);
            var bounds=piece.getBoundingBox();var box=AABB.of(bounds).inflate(2);int count=kind==RuinKind.FOREST?0:2;
            try{
                h.assertValueEqual(piece.beetles().size(),count,"Guard contract count");
                var old=piece.createTag(context);old.remove("OriBeetles");old.remove("OriBeetlesSpawned");var legacy=new RuinPiece(level.getStructureManager(),old);
                place(h,legacy,bounds);h.assertTrue(mobs(h,box).isEmpty(),"Old piece invented guards");
                h.assertFalse(legacy.createTag(context).contains("OriBeetles"),"Old piece migrated guard contract");
                // Process one point's chunk, save/reload, then the whole template: other points must still spawn.
                if(count>0){var first=placement.block(piece.beetles().getFirst());var chunk=new ChunkPos(first);
                    place(h,piece,new BoundingBox(chunk.getMinBlockX(),bounds.minY(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),bounds.maxY(),chunk.getMaxBlockZ()));
                    int expected=(int)piece.beetles().stream().map(placement::block).filter(p->new ChunkPos(p).equals(chunk)).count();
                    h.assertValueEqual(mobs(h,box).size(),expected,"Chunk-scoped guards");
                }
                piece=new RuinPiece(level.getStructureManager(),piece.createTag(context));place(h,piece,bounds);
                var mobs=mobs(h,box);h.assertValueEqual(mobs.size(),count,"Missing or duplicate generated guards");
                for(var mob:mobs){h.assertTrue(mob.isPersistenceRequired(),"Ruin guard can despawn");h.assertTrue(level.noCollision(mob),"Guard clips geometry at "+placement.local(mob.position()));
                    h.assertTrue(level.getFluidState(mob.blockPosition()).isEmpty(),"Guard spawns in water");
                    h.assertTrue(piece.beetles().stream().map(placement::block).anyMatch(p->p.equals(mob.blockPosition())),"Wrong rotated guard point");}
                place(h,piece,bounds);h.assertValueEqual(mobs(h,box).size(),count,"Repeated postProcess duplicated guards");
                mobs.forEach(Entity::discard);piece=new RuinPiece(level.getStructureManager(),piece.createTag(context));place(h,piece,bounds);
                h.assertTrue(mobs(h,box).isEmpty(),"Killed guards respawn after save/reload");h.succeed();
            }finally{mobs(h,box).forEach(Entity::discard);}
        }));
        return tests;
    }
    private static List<GloomBeetle> mobs(GameTestHelper h,AABB box){return h.getLevel().getEntitiesOfClass(GloomBeetle.class,box);}
    private static void place(GameTestHelper h,RuinPiece piece,BoundingBox bounds){var l=h.getLevel();piece.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(105),bounds,new ChunkPos(piece.origin()),BlockPos.ZERO);}
}
