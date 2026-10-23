package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class GinsoCourseGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){var tests=new ArrayList<TestFunction>();
        for(int rotation=0;rotation<4;rotation++){
            final int turn=rotation;
            tests.add(new TestFunction("ginso_course","ginso.course_targets_checkpoints_rotation_"+turn,"orimod_gametest:room",180,0,true,h->{
                var level=h.getLevel();var base=h.absolutePos(new BlockPos(160,100,0));
                var old=new HashMap<BlockPos,BlockState>();var pinned=new ArrayList<ChunkPos>();
                var references=new HashMap<net.minecraft.world.level.chunk.LevelChunk,Map<Structure,it.unimi.dsi.fastutil.longs.LongSet>>();
                var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("orimod:ginso_tree"));
                var piece=new GinsoPiece(base,115117,turn,3);
                Map<Structure,StructureStart> starts=null;net.minecraft.world.level.chunk.LevelChunk startChunk=null;
                try(var connected=Fixtures.connectedPlayer(h)){
                    for(int x=(base.getX()-12)>>4;x<=(base.getX()+12)>>4;x++)for(int z=(base.getZ()-12)>>4;z<=(base.getZ()+12)>>4;z++){
                        if(level.setChunkForced(x,z,true))pinned.add(new ChunkPos(x,z));var chunk=level.getChunk(x,z);
                        references.put(chunk,new HashMap<>(chunk.getAllReferences()));
                        chunk.setAllReferences(new HashMap<>(chunk.getAllReferences()));
                        chunk.addReferenceForStructure(structure,new ChunkPos(base).toLong());
                        if(!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                    }
                    startChunk=level.getChunkAt(base);starts=new HashMap<>(startChunk.getAllStarts());
                    startChunk.setStartForStructure(structure,new StructureStart(structure,new ChunkPos(base),0,new PiecesContainer(List.of(piece))));
                    var clip=new BoundingBox(base.getX()-12,base.getY(),base.getZ()-12,base.getX()+12,base.getY()+43,base.getZ()+12);
                    for(var at:BlockPos.betweenClosed(clip.minX(),clip.minY(),clip.minZ(),clip.maxX(),clip.maxY(),clip.maxZ()))old.put(at.immutable(),level.getBlockState(at));
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(base),base);
                    var p=connected.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);
                    p.setData(SpiritData.ABILITIES,Ability.BASH.bit|Ability.GRAPPLE.bit|Ability.GLIDE.bit);
                    for(var section:GinsoInterior.SECTIONS){
                        var start=piece.point(section.start());p.setPos(Vec3.atBottomCenterOf(start));
                        var goal=piece.point(section.goal());
                        h.assertTrue(level.noCollision(new AABB(goal).setMaxY(goal.getY()+1.8).deflate(.1)),"Landing blocked "+section.id());
                        h.assertTrue(level.getBlockState(goal.below()).isFaceSturdy(level,goal.below(),Direction.UP),"Landing unsupported "+section.id());
                        if(section.id().equals("bash")||section.id().equals("grapple")){
                            var at=piece.point(section.id().equals("bash")?GinsoInterior.LANTERN:GinsoInterior.GRAPPLE);
                            var v=Vec3.atCenterOf(at).subtract(p.getEyePosition());
                            float yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z)),pitch=(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalDistance()));
                            var target=SpiritTargets.find(p,yaw,pitch);
                            h.assertTrue(target!=null&&at.equals(target.block()),"Course target not selectable: "+section.id()+" distance="+v.length());
                        }
                    }
                    var stair=new ArrayList<>(List.of(piece.point(new BlockPos(-4,32,-3)),piece.point(new BlockPos(3,36,-3)),piece.point(new BlockPos(4,36,-3)),piece.point(new BlockPos(4,38,1))));
                    walk(h,stair);Collections.reverse(stair);walk(h,stair);
                    var wind=piece.point(GinsoInterior.VENT);p.setPos(Vec3.atBottomCenterOf(wind.above(6)));
                    h.assertTrue(UpdraftBlock.lifting(level,p),"Wind column obstructed");
                    for(var local:GinsoInterior.WELLS){var well=piece.point(local);p.setPos(Vec3.atBottomCenterOf(piece.point(local.north(2))));
                        h.assertTrue(GinsoCourse.checkpoint(p,well),"Well failed to bind");
                        var link=p.getData(SpiritData.SOUL_LINK).orElseThrow();
                        h.assertTrue(SoulLink.usable(level,link.pos()),"Checkpoint unsafe");
                        h.assertTrue(level.getBlockEntity(well)!=null,"Well shrine has no BE");
                    }
                    var tag=piece.createTag(StructurePieceSerializationContext.fromLevel(level));var loaded=new GinsoPiece(tag);
                    h.assertTrue(loaded.hasCourse()&&loaded.entrance()==turn&&loaded.approach()==3,"Course contract lost on reload");
                    tag.remove("CourseVersion");h.assertFalse(new GinsoPiece(tag).hasCourse(),"Old exterior piece invented a course");
                    h.succeed();
                }finally{
                    old.forEach((p,s)->level.setBlock(p,s,2));if(startChunk!=null&&starts!=null)startChunk.setAllStarts(starts);
                    references.forEach((c,r)->c.setAllReferences(r));for(var c:pinned)level.setChunkForced(c.x,c.z,false);
                }
            }));
        }
        tests.add(new TestFunction("ginso_course_hints","ginso.locked_hint_and_learned_hint_have_separate_receipts","orimod_gametest:room",40,0,true,h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var p=c.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,0);
                h.assertTrue(CompanionHints.offer(p,CompanionHints.Hint.GINSO_BASH,p.blockPosition()),"Missing locked hint");
                h.assertTrue(p.getData(SpiritData.PROGRESS).rewards().containsKey("hint/ginso_bash_locked"),"Missing locked receipt");
                p.setData(SpiritData.ABILITIES,Ability.BASH.bit);p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt=Long.MIN_VALUE;
                h.assertTrue(CompanionHints.offer(p,CompanionHints.Hint.GINSO_BASH,p.blockPosition()),"Locked receipt ate learned lesson");
                h.assertTrue(p.getData(SpiritData.PROGRESS).rewards().containsKey("hint/ginso_bash"),"Missing lesson receipt");h.succeed();
            }
        }));
        tests.add(new TestFunction("ginso_course_refs","ginso.course_lookup_never_loads_missing_start","orimod_gametest:room",40,0,true,h->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(2,2,2));var chunk=level.getChunkAt(at);
            var before=new HashMap<>(chunk.getAllReferences());var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("orimod:ginso_tree"));
            var far=new ChunkPos(1_000_000,1_000_000);
            try{chunk.setAllReferences(new HashMap<>());chunk.addReferenceForStructure(structure,far.toLong());
                h.assertTrue(GinsoCourse.at(level,at)==null,"Missing start invented course");
                h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null,"Course lookup loaded a chunk");h.succeed();
            }finally{chunk.setAllReferences(before);}
        }));return tests;
    }
    private static void walk(GameTestHelper h,List<BlockPos> route){
        var level=h.getLevel();double y=route.getFirst().getY();
        for(int i=1;i<route.size();i++){
            var a=route.get(i-1);var b=route.get(i);int samples=(int)Math.ceil(Math.hypot(b.getX()-a.getX(),b.getZ()-a.getZ())*12);
            for(int j=0;j<=samples;j++){
                double t=j/(double)samples,x=.5+a.getX()+(b.getX()-a.getX())*t,z=.5+a.getZ()+(b.getZ()-a.getZ())*t,floor=Double.NEGATIVE_INFINITY;
                for(var at:BlockPos.betweenClosed(BlockPos.containing(x-.29,y-1.1,z-.29),BlockPos.containing(x+.29,y+.6,z+.29)))
                    for(var shape:level.getBlockState(at).getCollisionShape(level,at).toAabbs()){
                        var box=shape.move(at);if(box.maxX>x-.29&&box.minX<x+.29&&box.maxZ>z-.29&&box.minZ<z+.29&&box.maxY<=y+.60001)floor=Math.max(floor,box.maxY);
                    }
                h.assertTrue(Double.isFinite(floor)&&Math.abs(floor-y)<=.60001,"Final root stair needs a jump at "+x+","+y+","+z);
                y=floor;h.assertTrue(level.noCollision(new AABB(x-.29,y+.001,z-.29,x+.29,y+1.799,z+.29)),"Root stair headroom blocked at "+x+","+y+","+z);
            }
        }
    }
}
