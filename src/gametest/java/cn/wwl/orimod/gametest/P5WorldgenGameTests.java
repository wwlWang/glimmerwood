package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_p5_worldgen")
public final class P5WorldgenGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var kind:FeatureSiteStructure.Site.values())add(tests,"legacy_"+kind.id()+"_loads_and_places",h->fixture(h,base->{
            var level=h.getLevel();var context=StructurePieceSerializationContext.fromLevel(level);
            var old=new FeatureSitePiece(kind,base).createTag(context);
            old.remove("PlacementVersion");old.remove("PlacementSeed");
            var loaded=new FeatureSitePiece(old);
            h.assertValueEqual(loaded.origin(),base,"Legacy origin lost");
            h.assertFalse(loaded.createTag(context).contains("PlacementVersion"),"Legacy piece silently upgraded");
            loaded.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(5),loaded.getBoundingBox(),new ChunkPos(base),base);
            var heart=kind==FeatureSiteStructure.Site.SPIRIT_TREE?base.offset(SpiritTreeShape.HEART):base;
            h.assertTrue(level.getBlockState(heart).is(kind==FeatureSiteStructure.Site.SPIRIT_TREE?OriBlocks.SPIRIT_TREE_HEART:OriBlocks.GLOOM_CORE),"Deserialized legacy piece failed to place");
        }));
        add(tests,"accepted_plan_adapts_natural_decoration",h->fixture(h,base->{
            var level=h.getLevel();var plan=plan(level,base);
            h.assertTrue((boolean)call(plan,"allowed",new Class<?>[0]),"Fixture plan invalid: "+call(plan,"reason",new Class<?>[0]));
            level.setBlock(base,Blocks.OAK_LOG.defaultBlockState(),18);
            level.setBlock(base.above(),Blocks.BEE_NEST.defaultBlockState(),18);
            level.setBlock(base.offset(0,-1,-4),Blocks.AIR.defaultBlockState(),18);
            level.setBlock(base.offset(0,-3,-4),Blocks.STONE.defaultBlockState(),18);
            var piece=plannedPiece(base,plan);
            var tag=piece.createTag(StructurePieceSerializationContext.fromLevel(level));
            h.assertValueEqual(tag.getLong("PlacementSeed"),5L,"Stable seed not persisted");
            piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(999),piece.getBoundingBox(),new ChunkPos(base),base);
            h.assertTrue(level.getBlockState(base.offset(SpiritTreeShape.HEART)).is(OriBlocks.SPIRIT_TREE_HEART),"Accepted plan lost to decoration");
            h.assertTrue(level.getBlockState(base.offset(0,-2,-4)).is(OriBlocks.SPIRIT_LOG),"Carved foot left unsupported");
        }));
        for(var decoration:List.of(Blocks.MOSSY_COBBLESTONE,Blocks.COBBLESTONE,Blocks.SNOW_BLOCK,Blocks.POWDER_SNOW))
            add(tests,"accepted_plan_adapts_"+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(decoration).getPath(),h->fixture(h,base->{
                var level=h.getLevel();var plan=plan(level,base);
                h.assertTrue((boolean)call(plan,"allowed",new Class<?>[0]),"Invalid dry noise fixture");
                level.setBlock(base,decoration.defaultBlockState(),18);
                level.setBlock(base.offset(0,-1,-4),decoration.defaultBlockState(),18);
                var untouched=base.offset(10,-1,0);level.setBlock(untouched,Blocks.SNOW_BLOCK.defaultBlockState(),18);
                var result=call(plan,"place",new Class<?>[]{net.minecraft.world.level.WorldGenLevel.class,FeatureSiteStructure.Site.class},level,FeatureSiteStructure.Site.SPIRIT_TREE);
                h.assertValueEqual(result,"OK","Surface decoration invalidated accepted plan");
                h.assertTrue(level.getBlockState(base.offset(SpiritTreeShape.HEART)).is(OriBlocks.SPIRIT_TREE_HEART),"Heart missing");
                h.assertTrue(level.getBlockState(untouched).is(Blocks.SNOW_BLOCK),"Clearing decoration excavated snowy ground outside writes");
            }));
        add(tests,"glades_adaptation_padding_reserved_before_registration",h->{
            var env=new ForestExternalReservationGameTests.Environment(h.getLevel(),7007);
            var feature=env.structure("orimod:blighted_grounds");
            try {
                var type=Class.forName("cn.wwl.orimod.world.FeatureSiteReservations");
                var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var reservation=ctor.newInstance();
                var pt=Class.forName("cn.wwl.orimod.world.FeatureSitePlan");
                var pc=pt.getDeclaredConstructor(String.class,BlockPos.class,Map.class);pc.setAccessible(true);
                var at=new BlockPos(-1412,65,-1780);var plan=pc.newInstance("OK",at,Map.of());
                var reason=call(reservation,"rejection",new Class<?>[]{net.minecraft.world.level.levelgen.structure.Structure.GenerationContext.class,pt,FeatureSiteStructure.Site.class},env.context(feature,new ChunkPos(at)),plan,FeatureSiteStructure.Site.BLIGHTED_GROUNDS);
                h.assertValueEqual(reason,"STRUCTURE_orimod:glades_outpost","Missed padded outpost boundary from actual seed 7007");
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        add(tests,"planned_site_reserves_surface_lake_but_not_deep_cavity",h->fixture(h,base->{
            var plan=plan(h.getLevel(),base);var piece=plannedPiece(base,plan);
            var box=new net.minecraft.world.level.levelgen.structure.BoundingBox(base.getX()-6,base.getY()-4,base.getZ()+6,base.getX()+9,base.getY()+3,base.getZ()+21);
            h.assertTrue(piece.protectsLake(box),"Surface lake can invalidate registered start");
            var saved=new FeatureSitePiece(piece.createTag(StructurePieceSerializationContext.fromLevel(h.getLevel())));
            h.assertTrue(saved.protectsLake(box),"Reservation was lost on reload");
            h.assertFalse(piece.protectsLake(box.moved(0,-16,0)),"Deep lake was unnecessarily rejected");
            h.assertFalse(piece.protectsLake(box.moved(32,0,0)),"Distant lake was unnecessarily rejected");
            h.assertFalse(new FeatureSitePiece(FeatureSiteStructure.Site.SPIRIT_TREE,base).protectsLake(box),"Legacy strict feature changed rules");
        }));
        add(tests,"unexpected_chest_rejects_plan_atomically",h->fixture(h,base->{
            var level=h.getLevel();var plan=plan(level,base);
            h.assertTrue((boolean)call(plan,"allowed",new Class<?>[0]),"Invalid fixture");
            level.setBlock(base,Blocks.CHEST.defaultBlockState(),18);
            var tree=base.offset(6,2,0);level.setBlock(tree,Blocks.OAK_LOG.defaultBlockState(),18);
            h.assertTrue(((String)call(plan,"place",new Class<?>[]{net.minecraft.world.level.WorldGenLevel.class,FeatureSiteStructure.Site.class},level,FeatureSiteStructure.Site.SPIRIT_TREE)).startsWith("UNEXPECTED_BLOCK"),"Chest overwritten");
            h.assertTrue(level.getBlockState(tree).is(Blocks.OAK_LOG),"Failed plan cleared vegetation");
            h.assertTrue(level.getBlockState(base).is(Blocks.CHEST),"Failed plan changed chest");
        }));
        add(tests,"search_state_roundtrip_and_legacy_retry",h->{
            var lookup=h.getLevel().registryAccess();var legacy=GreatTreeSite.load(new CompoundTag(),lookup);
            h.assertTrue(legacy.needsSearch(),"Legacy null choice must search once");
            legacy.searched(800);var partial=GreatTreeSite.load(legacy.save(new CompoundTag(),lookup),lookup);
            h.assertTrue(partial.needsSearch()&&partial.completedRange()==800,"Interrupted phase lost");
            partial.searched(2500);var none=GreatTreeSite.load(partial.save(new CompoundTag(),lookup),lookup);
            h.assertTrue(none.noSite()&&!none.needsSearch(),"NO_SITE not durable");
            var changed=none.save(new CompoundTag(),lookup);changed.putInt("SearchVersion",0);
            h.assertTrue(GreatTreeSite.load(changed,lookup).needsSearch(),"Old algorithm failure latched forever");
            var site=new GreatTreeSite.Site(new BlockPos(10,71,20),BlockPos.ZERO,42,1);none.choose(site);none.begun(true);
            var chosen=none.save(new CompoundTag(),lookup);chosen.remove("SearchVersion");
            var reload=GreatTreeSite.load(chosen,lookup);
            h.assertTrue(!reload.needsSearch()&&reload.site().equals(site)&&reload.begun(),"Existing tree was reconsidered");
        });
        add(tests,"single_traversal_matches_vanilla_and_full_columns",h->{
            var level=h.getLevel();var settings=level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var generator=new NoiseBasedChunkGenerator(level.getChunkSource().getGenerator().getBiomeSource(),settings);
            for(long seed:new long[]{5555,116555,5005,7007}){
                var random=RandomState.create(settings.value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
                var terrain=noise(generator,random,level);
                for(int i=0;i<8;i++){
                    int x=i*137-421,z=i*251-850;var sample=NoiseSurfaceSample.at(generator,random,level,x,z);
                    h.assertValueEqual(sample.floor(),generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random),"Floor changed seed="+seed);
                    h.assertValueEqual(sample.surface(),generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random),"Surface changed seed="+seed);
                    h.assertValueEqual((int)call(terrain,"getHeight",new Class<?>[]{Heightmap.Types.class,int.class,int.class},Heightmap.Types.OCEAN_FLOOR_WG,x,z),sample.floor(),"Full-column floor differs");
                    h.assertValueEqual((int)call(terrain,"getHeight",new Class<?>[]{Heightmap.Types.class,int.class,int.class},Heightmap.Types.WORLD_SURFACE_WG,x,z),sample.surface(),"Full-column surface differs");
                    var original=generator.getBaseColumn(x,z,level,random);
                    for(int y=sample.floor()+24;y>=sample.floor()-16;y--)
                        h.assertValueEqual(((net.minecraft.world.level.BlockGetter)terrain).getBlockState(new BlockPos(x,y,z)),original.getBlock(y),"Native cell changed column seed="+seed+" y="+y);
                }
                h.assertValueEqual((int)call(terrain,"sampledColumns",new Class<?>[0]),8,"Repeated height queries resampled columns");
            }
        });
        add(tests,"chunk_batch_revisits_slices_without_changing_blocks",h->{
            var level=h.getLevel();
            for(long seed:new long[]{5555,116555}){
                var env=new ForestExternalReservationGameTests.Environment(level,seed);
                var terrain=(net.minecraft.world.level.BlockGetter)noise(env.generator,env.random,level);
                Object surfaceCells;
                try{
                    var ctor=Class.forName("cn.wwl.orimod.world.CreekSurfaceSample$Cells").getDeclaredConstructor(net.minecraft.world.level.chunk.ChunkGenerator.class,net.minecraft.world.level.LevelHeightAccessor.class,RandomState.class);
                    ctor.setAccessible(true);surfaceCells=ctor.newInstance(env.generator,level,env.random);
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
                for(var cp:List.of(new ChunkPos(-5776,-2800),new ChunkPos(11677,4157)))
                    for(int dx:new int[]{13,1,9,5,14,2,10,6})for(int dz=15;dz>=0;dz--){
                        int x=cp.getMinBlockX()+dx,z=cp.getMinBlockZ()+dz;
                        var reference=env.generator.getBaseColumn(x,z,level,env.random);
                        int floor=env.generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,env.random);
                        h.assertValueEqual((int)call(terrain,"getHeight",new Class<?>[]{Heightmap.Types.class,int.class,int.class},Heightmap.Types.OCEAN_FLOOR_WG,x,z),floor,"Revisited X slice changed height");
                        var sampled=call(surfaceCells,"at",new Class<?>[]{int.class,int.class},x,z);
                        h.assertValueEqual((int)call(sampled,"floor",new Class<?>[0]),floor,"Lazy surface sampling changed floor");
                        int surface=env.generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,env.random);
                        h.assertValueEqual((int)call(sampled,"surface",new Class<?>[0]),surface,"Lazy surface sampling changed surface");
                        boolean dry=reference.getBlock(floor).getFluidState().isEmpty()&&reference.getBlock(floor-1).getFluidState().isEmpty()&&!reference.getBlock(floor-1).isAir();
                        h.assertValueEqual((boolean)call(sampled,"dry",new Class<?>[0]),dry,"Lazy surface sampling changed dry verdict");
                        for(int y=floor-9;y<=floor+24;y++)h.assertValueEqual(terrain.getBlockState(new BlockPos(x,y,z)),reference.getBlock(y),"Chunk batch changed seed="+seed+" pos="+new BlockPos(x,y,z));
                    }
            }
        });
        add(tests,"third_anchor_retains_real_5555_tree",h->{
            var env=new ForestExternalReservationGameTests.Environment(h.getLevel(),5555);
            var tree=(FeatureSiteStructure)env.structure("orimod:spirit_tree");
            var stub=tree.findValidGenerationPoint(env.context(tree,new ChunkPos(-7,-103)));
            h.assertTrue(stub.isPresent(),"Viable third legacy retry was discarded");
            int top=Integer.MIN_VALUE;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)top=Math.max(top,env.generator.getBaseHeight(-108+dx,-1636+dz,Heightmap.Types.WORLD_SURFACE_WG,h.getLevel(),env.random));
            h.assertValueEqual(stub.orElseThrow().position(),new BlockPos(-108,top,-1636),"Third anchor must use the native 5x5 maximum, before surface decoration");
        });
        add(tests,"locate_and_piece_share_cached_accepted_plan",h->{
            var env=new ForestExternalReservationGameTests.Environment(h.getLevel(),5555);
            var tree=(FeatureSiteStructure)env.structure("orimod:spirit_tree");
            var cp=new ChunkPos(-7,-103);
            var types=new Class<?>[]{net.minecraft.world.level.levelgen.structure.Structure.GenerationContext.class};
            var first=(Optional<?>)call(tree,"choose",types,env.context(tree,cp));
            h.assertTrue(first.isPresent(),"Expected viable third anchor");
            // Distinct contexts model StructureCheck/locate and subsequent real structure generation.
            var second=(Optional<?>)call(tree,"choose",types,env.context(tree,cp));
            h.assertTrue(first==second,"Same chunk recomputed the preflight instead of returning its cached result");
            var plan=call(first.orElseThrow(),"plan",new Class<?>[0]);
            var pieces=tree.findValidGenerationPoint(env.context(tree,cp)).orElseThrow().getPiecesBuilder().build().pieces();
            try{
                var field=FeatureSitePiece.class.getDeclaredField("plan");field.setAccessible(true);
                h.assertTrue(field.get(pieces.getFirst())==plan,"Generated piece did not use the exact plan located earlier");
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        add(tests,"village_grid_with_valid_biome_reserves_envelope",h->{
            var env=new ForestExternalReservationGameTests.Environment(h.getLevel(),116120);
            var tree=env.structure("orimod:spirit_tree");
            try{
                var type=Class.forName("cn.wwl.orimod.world.FeatureSiteReservations");
                var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
                var reservations=constructor.newInstance();
                var cp=new ChunkPos(-89,-47);
                var result=call(reservations,"quickRejection",new Class<?>[]{net.minecraft.world.level.levelgen.structure.Structure.GenerationContext.class,BlockPos.class},env.context(tree,cp),cp.getMiddleBlockPosition(70));
                h.assertValueEqual(result,"STRUCTURE_minecraft:villages","Known village placement/biome not reserved");
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        return tests;
    }
    private static Object call(Object target,String name,Class<?>[] types,Object... args){
        try{var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static Object plan(net.minecraft.server.level.ServerLevel level,BlockPos base){
        try{
            var terrain=Class.forName("cn.wwl.orimod.world.FeatureTerrain");
            var world=terrain.getDeclaredMethod("world",net.minecraft.world.level.WorldGenLevel.class);world.setAccessible(true);
            var check=Class.forName("cn.wwl.orimod.world.FeatureSitePlan").getDeclaredMethod("check",FeatureSiteStructure.Site.class,terrain,BlockPos.class,long.class);check.setAccessible(true);
            return check.invoke(null,FeatureSiteStructure.Site.SPIRIT_TREE,world.invoke(null,level),base,5L);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static FeatureSitePiece plannedPiece(BlockPos base,Object plan){
        try{var c=FeatureSitePiece.class.getDeclaredConstructor(FeatureSiteStructure.Site.class,BlockPos.class,long.class,plan.getClass());c.setAccessible(true);return c.newInstance(FeatureSiteStructure.Site.SPIRIT_TREE,base,5L,plan);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static Object noise(NoiseBasedChunkGenerator generator,RandomState random,net.minecraft.server.level.ServerLevel level){
        try{var c=Class.forName("cn.wwl.orimod.world.FeatureTerrain$Noise").getDeclaredConstructor(net.minecraft.world.level.chunk.ChunkGenerator.class,RandomState.class,net.minecraft.world.level.LevelHeightAccessor.class,BlockPos.class);c.setAccessible(true);return c.newInstance(generator,random,level,BlockPos.ZERO);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void fixture(GameTestHelper h,Consumer<BlockPos> test){
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(128,100,0));var old=new LinkedHashMap<BlockPos,BlockState>();
        for(var p:BlockPos.betweenClosed(base.offset(-15,-18,-15),base.offset(15,45,15)))old.put(p.immutable(),level.getBlockState(p));
        try{
            for(var p:old.keySet())level.setBlock(p,(p.getY()==base.getY()-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            test.accept(base);
        }finally{old.forEach((p,s)->level.setBlock(p,s,18));}
    }
    private static void add(Collection<TestFunction> tests,String name,Consumer<GameTestHelper> run){
        tests.add(new TestFunction("p5_worldgen","p5_worldgen."+name,"orimod_p5_worldgen:platform_room",100,0,true,h->{run.accept(h);h.succeed();}));
    }
}
