package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_r117")
public final class BlightEcologyGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        add(tests,"natural_palette_costs_zero_block_reads",h->{
            var at=sky(h);var b=new BlightEcologyRules.Budget();
            h.assertValueEqual(BlightProtection.artificialNearby(h.getLevel(),at,b),BlightProtection.Result.ALLOW,"Natural neighborhood rejected");
            h.assertValueEqual(b.reads(),0,"Natural palette scanned blocks");
            h.assertTrue(b.indices()>0,"Did not inspect sections");
        });
        add(tests,"suspicious_palette_outside_box_costs_exactly_441_reads",h->{
            var at=sky(h);try(var f=new Edits(h.getLevel())){
                f.set(at.offset(5,0,0),Blocks.OAK_PLANKS.defaultBlockState());var b=new BlightEcologyRules.Budget();
                h.assertValueEqual(BlightProtection.artificialNearby(h.getLevel(),at,b),BlightProtection.Result.ALLOW,"Out-of-range planks protect column");
                h.assertValueEqual(b.reads(),441,"Did not scan full intersection of suspicious section");
                var shortBudget=new BlightEcologyRules.Budget(440,0);
                h.assertValueEqual(BlightProtection.artificialNearby(h.getLevel(),at,shortBudget),BlightProtection.Result.DEFER,"Partial protection scan authorized writes");
                h.assertValueEqual(shortBudget.reads(),0,"Incomplete scan retained partial work");
            }
        });
        for(var offset:List.of(new BlockPos(3,6,3),new BlockPos(-3,-2,-3),new BlockPos(4,0,0),new BlockPos(0,7,0),new BlockPos(0,-3,0)))
            add(tests,"artificial_boundary_"+offset.toShortString().replace(", ","_"),h->{
                var at=sky(h);try(var f=new Edits(h.getLevel())){
                    f.set(at.offset(offset),Blocks.GLASS.defaultBlockState());var b=new BlightEcologyRules.Budget();
                    boolean inside=Math.abs(offset.getX())<=3&&offset.getY()>=-2&&offset.getY()<=6;
                    h.assertValueEqual(BlightProtection.artificialNearby(h.getLevel(),at,b),inside?BlightProtection.Result.PROTECTED:BlightProtection.Result.ALLOW,"7x7x9 boundary");
                    h.assertTrue(b.reads()<=441,"Read cap");
                }
            });
        add(tests,"natural_and_artificial_tags_obey_be_and_persistent_leaf_override",h->{
            for(var state:List.of(Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState(),Blocks.DIRT.defaultBlockState(),Blocks.OAK_LOG.defaultBlockState(),Blocks.OAK_LEAVES.defaultBlockState(),OriBlocks.GLOOM_CORE.get().defaultBlockState())){
                // A core is in the observation tag but its BE still overrides that tag.
                h.assertValueEqual(BlightProtection.artificial(state),state.hasBlockEntity(),"Natural tag / BE override "+state);
            }
            for(var state:List.of(Blocks.CHEST.defaultBlockState(),Blocks.FARMLAND.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState(),Blocks.WHEAT.defaultBlockState(),Blocks.TORCH.defaultBlockState(),Blocks.OAK_FENCE.defaultBlockState(),Blocks.OAK_DOOR.defaultBlockState(),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),Blocks.DIAMOND_BLOCK.defaultBlockState()))
                h.assertTrue(BlightProtection.artificial(state),"Artificial fallback omitted "+state);
            h.assertTrue(BlightProtection.soil(Blocks.DIRT.defaultBlockState()),"Plain dirt cannot grow");
            for(var state:List.of(Blocks.OAK_LOG.defaultBlockState(),Blocks.STONE.defaultBlockState(),Blocks.FARMLAND.defaultBlockState(),Blocks.WATER.defaultBlockState()))h.assertFalse(BlightProtection.soil(state),"Observation tag became conversion whitelist");
        });
        add(tests,"unloaded_neighbor_never_loads",h->{
            var at=new BlockPos(16_000_000,100,16_000_000);var source=h.getLevel().getChunkSource();
            h.assertTrue(source.getChunkNow(at.getX()>>4,at.getZ()>>4)==null,"Fixture loaded");
            h.assertValueEqual(BlightProtection.artificialNearby(h.getLevel(),at,new BlightEcologyRules.Budget()),BlightProtection.Result.DEFER,"Unloaded treated as air");
            h.assertValueEqual(BlightProtection.hearts(h.getLevel(),at,new BlightEcologyRules.Budget()),BlightProtection.Result.DEFER,"Missing heart index treated as clear");
            h.assertTrue(source.getChunkNow(at.getX()>>4,at.getZ()>>4)==null,"Protection loaded chunk");
        });
        add(tests,"cross_section_and_chunk_boundary_artificial",h->{
            var base=sky(h);var at=new BlockPos((base.getX()&~15)+15,239,(base.getZ()&~15)+15);
            // Fixture setup loads the neighborhood. The production query is loaded-only.
            for(int x=at.getX()>>4;x<=(at.getX()+3)>>4;x++)for(int z=at.getZ()>>4;z<=(at.getZ()+3)>>4;z++)h.getLevel().getChunk(x,z);
            try(var f=new Edits(h.getLevel())){f.set(at.offset(3,6,3),Blocks.OAK_PLANKS.defaultBlockState());var b=new BlightEcologyRules.Budget();
                h.assertValueEqual(BlightProtection.artificialNearby(h.getLevel(),at,b),BlightProtection.Result.PROTECTED,"Neighbor section skipped");h.assertTrue(b.reads()<=441,"Read ceiling");}
        });
        add(tests,"tree_heart_32_boundary_cache_and_dynamic_invalidation",h->{
            var at=sky(h);var level=h.getLevel();
            for(int x=(at.getX()-33)>>4;x<=(at.getX()+33)>>4;x++)for(int z=(at.getZ()-33)>>4;z<=(at.getZ()+33)>>4;z++)level.getChunk(x,z);
            BlightProtection.clear(level);
            try(var f=new Edits(level)){
                f.set(at.offset(32,0,0),OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState());
                h.assertValueEqual(heartResult(h,at),BlightProtection.Result.PROTECTED,"Inclusive 32-block heart protection");
                f.set(at.offset(32,0,0),Blocks.AIR.defaultBlockState());
                f.set(at.offset(33,0,0),OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState());
                h.assertValueEqual(heartResult(h,at),BlightProtection.Result.ALLOW,"33-block heart / stale cache");
                f.set(at,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState());
                h.assertValueEqual(heartResult(h,at),BlightProtection.Result.PROTECTED,"Dynamic new heart not indexed");
            }finally{BlightProtection.clear(level);}
        });
        add(tests,"structure_reference_waits_for_unloaded_start",h->{
            var at=sky(h);var level=h.getLevel();var chunk=level.getChunkAt(at);var refs=new HashMap<>(chunk.getAllReferences());
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.FOREST.location());var far=new ChunkPos(999_999,999_999);
            try{
                chunk.setAllReferences(new HashMap<>());chunk.addReferenceForStructure(structure,far.toLong());
                h.assertValueEqual(BlightProtection.structures(level,at,new BlightEcologyRules.Budget()),BlightProtection.Result.DEFER,"Unknown start treated as empty");
                h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null,"Loaded remote structure origin");
            }finally{chunk.setAllReferences(refs);}
        });
        add(tests,"large_piece_list_resumes_within_index_budget",h->{
            var level=h.getLevel();var origin=sky(h).below(80);var kind=RuinKind.CAVE;
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
            var piece=new RuinPiece(level.getStructureManager(),kind,new RuinPlacement(origin,Rotation.NONE));var box=piece.getBoundingBox();
            var at=new BlockPos(box.minX()-3,box.minY(),box.minZ());
            for(int x=(at.getX()-2)>>4;x<=(at.getX()+2)>>4;x++)for(int z=(at.getZ()-2)>>4;z<=(at.getZ()+2)>>4;z++)level.getChunk(x,z);
            var originChunk=level.getChunkAt(origin);var chunk=level.getChunkAt(at);var starts=new HashMap<>(originChunk.getAllStarts());var refs=new HashMap<>(chunk.getAllReferences());
            try{
                var pieces=new ArrayList<StructurePiece>(Collections.nCopies(180,piece));originChunk.setStartForStructure(structure,new StructureStart(structure,originChunk.getPos(),0,new PiecesContainer(pieces)));
                chunk.setAllReferences(new HashMap<>());chunk.addReferenceForStructure(structure,originChunk.getPos().toLong());
                var first=new BlightEcologyRules.Budget();h.assertValueEqual(BlightProtection.structures(level,at,first),BlightProtection.Result.DEFER,"Large scan did not honor index cap");
                var second=new BlightEcologyRules.Budget();h.assertValueEqual(BlightProtection.structures(level,at,second),BlightProtection.Result.ALLOW,"Scan restarts at piece zero forever");
                h.assertTrue(first.indices()<=128&&second.indices()<=128,"Piece scan exceeded index cap");
            }finally{originChunk.setAllStarts(starts);chunk.setAllReferences(refs);BlightProtection.clear(level);}
        });
        for(var kind:RuinKind.values())add(tests,"structure_box_"+kind.id,h->{
            var level=h.getLevel();var origin=sky(h).below(40);
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
            var piece=new RuinPiece(level.getStructureManager(),kind,new RuinPlacement(origin,Rotation.NONE));var box=piece.getBoundingBox();
            var point=new BlockPos(box.minX()-2,box.minY(),box.minZ());
            // Pin/load the complete local read area before installing temporary structure metadata.
            for(int x=(point.getX()-2)>>4;x<=(box.maxX()+2)>>4;x++)for(int z=(point.getZ()-2)>>4;z<=(box.maxZ()+2)>>4;z++)level.getChunk(x,z);
            var chunk=level.getChunkAt(origin);var current=level.getChunkAt(point);var starts=new HashMap<>(chunk.getAllStarts());var refs=new HashMap<>(current.getAllReferences());
            try{
                chunk.setStartForStructure(structure,new StructureStart(structure,chunk.getPos(),0,new PiecesContainer(List.of(piece))));
                current.setAllReferences(new HashMap<>());current.addReferenceForStructure(structure,chunk.getPos().toLong());
                h.assertValueEqual(BlightProtection.structures(level,point,new BlightEcologyRules.Budget()),BlightProtection.Result.PROTECTED,"Ruin padded edge not protected");
                h.assertValueEqual(BlightProtection.structures(level,point.west(),new BlightEcologyRules.Budget()),BlightProtection.Result.ALLOW,"Third block outside piece still protected by start padding");
                var above=new BlockPos(box.minX(),box.maxY()+1,box.minZ());
                // Same local reference: a ground column above an underground piece must remain eligible.
                var overhead=level.getChunkAt(above);var aboveRefs=new HashMap<>(overhead.getAllReferences());
                try{overhead.setAllReferences(new HashMap<>());overhead.addReferenceForStructure(structure,chunk.getPos().toLong());
                    h.assertValueEqual(BlightProtection.structures(level,above,new BlightEcologyRules.Budget()),BlightProtection.Result.ALLOW,"Piece protects surface above its ceiling");
                }finally{overhead.setAllReferences(aboveRefs);}
            }finally{chunk.setAllStarts(starts);current.setAllReferences(refs);}
        });
        add(tests,"journal_roundtrip_properties_owner_and_cas",h->{
            var center=sky(h);var floor=center.offset(4,-1,0);var d=journal(center);
            var before=Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY,true);var expected=OriBlocks.BLIGHTED_SOIL.get().defaultBlockState();
            h.assertTrue(d.recordGrowth(center,floor,List.of(new BlightRegion.Change(floor,before,expected))),"Record failed");
            d.protectColumn(center,center.offset(8,0,0));var r=d.ecology(center);for(int i=0;i<27;i++)r.loadedTick();r.nextRandom();r.restoreCursor(1);
            var saved=d.save(new CompoundTag(),h.getLevel().registryAccess());var copy=BlightData.load(saved,h.getLevel().registryAccess());
            h.assertValueEqual(copy.save(new CompoundTag(),h.getLevel().registryAccess()),saved,"Journal changed on load");
            h.assertValueEqual(copy.columnOwner(floor),Optional.of(center),"Owner lost");var change=copy.ecology(center).changes().iterator().next();
            h.assertValueEqual(change.before(),before,"Original state property lost");h.assertTrue(change.matches(expected),"CAS rejects own write");
            h.assertFalse(change.matches(Blocks.AIR.defaultBlockState()),"CAS overwrites mined air");h.assertFalse(change.matches(Blocks.GOLD_BLOCK.defaultBlockState()),"CAS overwrites player edit");
            h.assertTrue(change.matches(expected),"Same-state replacement is intentionally indistinguishable");
            var other=center.offset(8,0,0);copy.register(other,new long[]{other.below().asLong()});copy.initializeEcology(other,List.of(other.below()),true);
            h.assertFalse(copy.recordGrowth(other,floor,List.of(new BlightRegion.Change(floor,before,expected))),"Second core stole owner");
            h.assertValueEqual(copy.ecology(other).size(),0,"Failed ownership transaction leaked records");
        });
        add(tests,"journal_transaction_rejects_duplicate_be_and_cross_column",h->{
            var center=sky(h);var d=journal(center);var floor=center.offset(4,-1,0);
            var good=new BlightRegion.Change(floor,Blocks.DIRT.defaultBlockState(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());
            for(var bad:List.of(good,new BlightRegion.Change(floor.above(),Blocks.CHEST.defaultBlockState(),Blocks.AIR.defaultBlockState()),new BlightRegion.Change(floor.east(),Blocks.DIRT.defaultBlockState(),Blocks.STONE.defaultBlockState()))){
                h.assertFalse(d.recordGrowth(center,floor,List.of(good,bad)),"Invalid transaction accepted");h.assertValueEqual(d.ecology(center).size(),0,"Partial transaction stored");h.assertTrue(d.columnOwner(floor).isEmpty(),"Partial owner stored");
            }
            d.protectColumn(center,floor);h.assertFalse(d.recordGrowth(center,floor,List.of(good)),"Permanently protected column claimed");
        });
        for(String corrupt:List.of("future_version","unknown_block","unknown_property","bad_palette_index"))add(tests,"journal_preserves_"+corrupt,h->{
            var center=sky(h);var d=journal(center);var floor=center.offset(4,-1,0);
            d.recordGrowth(center,floor,List.of(new BlightRegion.Change(floor,Blocks.GRASS_BLOCK.defaultBlockState(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState())));
            var tag=d.ecology(center).save();
            switch(corrupt){
                case "future_version"->tag.putInt("version",999);
                case "unknown_block"->tag.getList("palette",Tag.TAG_COMPOUND).getCompound(0).putString("Name","missing:soil");
                case "unknown_property"->tag.getList("palette",Tag.TAG_COMPOUND).getCompound(0).getCompound("Properties").putString("snowy","unknown");
                case "bad_palette_index"->tag.getList("changes",Tag.TAG_COMPOUND).getCompound(0).putInt("before",999);
            }
            var copy=BlightRegion.load(center,tag,h.getLevel().registryAccess());h.assertTrue(copy.readOnly(),"Unsafe journal remained writable");
            copy.loadedTick();copy.nextRandom();copy.restoreCursor(99);copy.protect(floor.east());
            h.assertValueEqual(copy.save(),tag,"Opaque damaged/future journal was destroyed");
        });
        add(tests,"core_new_marker_is_optional_and_survives_reload",h->{
            var at=sky(h);var state=OriBlocks.GLOOM_CORE.get().defaultBlockState();var core=new GloomCoreBlockEntity(at,state);var registry=h.getLevel().registryAccess();
            core.terrain(new long[]{at.below().asLong()});var old=core.saveWithoutMetadata(registry);
            h.assertFalse(old.contains("natural_ecology"),"Old BE contract changed");
            core.generatedTerrain(new long[]{at.below().asLong()});var fresh=core.saveWithoutMetadata(registry);
            h.assertTrue(fresh.getBoolean("natural_ecology"),"Fresh core marker missing");
            var copy=new GloomCoreBlockEntity(at,state);copy.loadWithComponents(fresh,registry);h.assertValueEqual(copy.saveWithoutMetadata(registry),fresh,"Marker did not roundtrip");
            copy.loadWithComponents(old,registry);h.assertValueEqual(copy.saveWithoutMetadata(registry),old,"Legacy load inherited fresh marker");
        });
        return tests;
    }
    private static BlightData journal(BlockPos center){var d=new BlightData();d.register(center,new long[]{center.below().asLong()});d.initializeEcology(center,List.of(center.below()),true);return d;}
    private static BlockPos sky(GameTestHelper h){var p=h.absolutePos(new BlockPos(4,0,4));return new BlockPos((p.getX()&~15)+8,248,(p.getZ()&~15)+8);}
    private static BlightProtection.Result heartResult(GameTestHelper h,BlockPos at){
        for(int attempt=0;attempt<32;attempt++){
            var b=new BlightEcologyRules.Budget();var result=BlightProtection.hearts(h.getLevel(),at,b);
            h.assertTrue(b.reads()<=512&&b.indices()<=128,"Heart cache exceeded budget");if(result!=BlightProtection.Result.DEFER)return result;
        }
        throw new GameTestAssertException("Loaded heart scan failed to resume within 32 budget turns");
    }
    private static final class Edits implements AutoCloseable {
        final ServerLevel level;final Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        Edits(ServerLevel level){this.level=level;}
        void set(BlockPos p,BlockState s){before.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,s,Block.UPDATE_CLIENTS);}
        public void close(){before.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_CLIENTS));}
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run){tests.add(new TestFunction("r117_blight_ledger","r117_blight_ledger."+name,"orimod_r117:room",100,0,true,h->{run.accept(h);h.succeed();}));}
}
