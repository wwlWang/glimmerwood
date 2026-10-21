package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class RuinSecretsGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(var kind:RuinKind.values())for(var rotation:Rotation.values())add(tests,kind.id+"_"+rotation.name(),(h,c)->{
            var level=h.getLevel();var placement=RuinPlacement.atMinimum(kind,h.absolutePos(new BlockPos(32,30,32)),rotation);
            var piece=new RuinPiece(level.getStructureManager(),kind,placement);
            var restored=new RuinPiece(level.getStructureManager(),piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
            h.assertValueEqual(restored.secret(),kind.layout().secret(),"Saved secret metadata changed");
            restored.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(103),restored.getBoundingBox(),new ChunkPos(placement.origin()),BlockPos.ZERO);
            var entry=placement.block(restored.secret().entrance());
            var seal=level.getBlockState(entry);
            h.assertTrue(seal.is(kind==RuinKind.CREEK?OriBlocks.CRACKED_RUIN_FLOOR:OriBlocks.CRACKED_RUIN_WALL),"Missing generated secret seal");
            var vessels=new ArrayList<BlockPos>();var bounds=restored.getBoundingBox();
            for(var pos:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))
                if(level.getBlockState(pos).is(OriBlocks.SPIRIT_LIGHT_VESSEL))vessels.add(pos.immutable());
            int expected=kind==RuinKind.FOREST?4:3; // R104 forest island adds one ordinary reward, not a second secret.
            h.assertTrue(vessels.size()==expected,"Expected one secret, two decorations and forest bridge reward, got "+vessels.size());
            c.player().setPos(placement.point(kind.layout().safe()));
            var map=placement.block(kind.layout().mapStone());var before=MapStoneBlock.survey(c.player(),map);
            vessels.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),2));
            h.assertValueEqual(MapStoneBlock.survey(c.player(),map),before,"Vessels changed map-stone collectible survey");
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
            var origin=level.getChunkAt(placement.origin());var current=level.getChunkAt(c.player().blockPosition());
            var starts=new HashMap<>(origin.getAllStarts());var refs=copyRefs(current);
            try {
                origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(restored))));
                current.addReferenceForStructure(structure,origin.getPos().toLong());
                var result=RuinSecrets.find(c.player());
                h.assertTrue(result.inRuin() && result.secrets().size()==1,"Loaded piece secret lookup failed");
                var found=result.secrets().getFirst();h.assertValueEqual(found.entrance(),entry,"Entrance rotation/origin");
                h.assertValueEqual(found.facing(),rotation.rotate(kind.layout().secret().facing()),"Facing rotation");
                h.assertValueEqual(found.blocks(),kind==RuinKind.CREEK?"floor":"wall","Secret block kind");
                h.assertValueEqual(result.debug().getFirst(),Map.of("kind",kind.id,"entrance",List.of(entry.getX(),entry.getY(),entry.getZ()),
                        "facing",found.facing().getName(),"blocks",found.blocks()),"Devtools schema mismatch");
                h.assertTrue(command(c,"ori ruin secrets")==1,"Secrets command did not return success");
            }finally{origin.setAllStarts(starts);current.setAllReferences(refs);}
            // The template contract excludes this chamber from the existing challenge clearance/anchors.
            for(var anchor:kind.layout().critical())h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(placement.block(anchor.position())).getBlock()),anchor.block(),"Secret overwrote critical anchor");
        });
        add(tests,"legacy_piece_has_no_secret_even_with_current_templates",(h,c)->{
            var kind=RuinKind.CAVE;var level=h.getLevel();var placement=new RuinPlacement(h.absolutePos(new BlockPos(32,30,32)),Rotation.NONE);
            var tag=new RuinPiece(level.getStructureManager(),kind,placement).createTag(StructurePieceSerializationContext.fromLevel(level));tag.remove("OriSecret");
            var legacy=new RuinPiece(level.getStructureManager(),tag);h.assertTrue(legacy.secret()==null,"Current layout invented old secret");
            h.assertFalse(legacy.createTag(StructurePieceSerializationContext.fromLevel(level)).contains("OriSecret"),"Saving legacy piece migrated it");
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());var chunk=level.getChunkAt(placement.origin());
            var old=new HashMap<>(chunk.getAllStarts());c.player().setPos(Vec3.atBottomCenterOf(placement.origin().offset(2,2,2)));
            var current=level.getChunkAt(c.player().blockPosition());var refs=copyRefs(current);
            try {
                chunk.setStartForStructure(structure,new StructureStart(structure,chunk.getPos(),0,new PiecesContainer(List.of(legacy))));
                current.addReferenceForStructure(structure,chunk.getPos().toLong());
                var result=RuinSecrets.find(c.player());h.assertTrue(result.inRuin(),"Legacy start/reference not found");h.assertTrue(result.debug().isEmpty(),"Legacy lookup has invented secrets");
                h.assertTrue(command(c,"ori ruin secrets")==0,"Legacy command claims a secret exists");
            }finally{chunk.setAllStarts(old);current.setAllReferences(refs);}
        });
        add(tests,"outside_and_missing_reference_return_empty_without_loading",(h,c)->{
            var level=h.getLevel();var chunk=level.getChunkAt(c.player().blockPosition());var refs=copyRefs(chunk);
            var distant=new ChunkPos(1_000_000,1_000_000);var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.FOREST.location());
            h.assertTrue(level.getChunkSource().getChunkNow(distant.x,distant.z)==null,"Reference fixture was loaded");
            try {
                chunk.addReferenceForStructure(structure,distant.toLong());
                var result=RuinSecrets.find(c.player());h.assertFalse(result.inRuin(),"Outside player considered inside");h.assertTrue(result.debug().isEmpty(),"Outside devtools secrets not empty");
                h.assertTrue(command(c,"ori ruin secrets")==0,"Outside command succeeded");
                h.assertTrue(level.getChunkSource().getChunkNow(distant.x,distant.z)==null,"Secrets lookup loaded start chunk");
            }finally{chunk.setAllReferences(refs);}
        });
        return tests;
    }
    private static int command(Fixtures.ConnectedPlayer c,String command) {
        try {return c.player().server.getCommands().getDispatcher().execute(command,c.player().createCommandSourceStack().withPermission(2));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new RuntimeException(e);}
    }
    private static Map<Structure,it.unimi.dsi.fastutil.longs.LongSet> copyRefs(LevelChunk chunk) {
        var result=new HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();chunk.getAllReferences().forEach((k,v)->result.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));return result;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run) {
        tests.add(new TestFunction("ruin_secrets","ruin_secrets."+name.toLowerCase(Locale.ROOT),"orimod_gametest:room",100,0,true,h->{
            try(var c=Fixtures.connectedPlayer(h)){CrackedRuinGameTests.prepare(c);run.accept(h,c);}h.succeed();
        }));
    }
}
