package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.concurrent.*;

@GameTestHolder("orimod_forest_red")
public final class ForestSitingOptimizationGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        return List.of(test("early_xz_equals_actual_builder_all_rotations",ForestSitingOptimizationGameTests::boxes),
                test("owner_yield_does_not_poison_neighbour_base",ForestSitingOptimizationGameTests::yield),
                test("surface_cache_single_flight_scope_and_clear",ForestSitingOptimizationGameTests::cache));
    }
    private interface Check {void run(GameTestHelper h)throws Exception;}
    private static TestFunction test(String name,Check check){return new TestFunction("forest_optimization","forest_optimization."+name,
            "orimod_forest_red:room",Rotation.NONE,200,0,true,h->{try{check.run(h);}catch(Exception e){throw new AssertionError(e);}});}
    private static List<List<Integer>> xz(List<RuinDecision.Box> boxes){return boxes.stream().map(b->List.of(b.minX(),b.minZ(),b.maxX(),b.maxZ())).toList();}
    private static void boxes(GameTestHelper h){
        var level=h.getLevel();var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.resources.ResourceLocation.parse("orimod:forest_spring"));
        for(var rotation:Rotation.values()){
            var provisional=RuinPlacement.atMinimum(RuinKind.FOREST.layout(1),new BlockPos(3200,0,-3200),rotation);
            var actual=new RuinPlacement(provisional.origin().atY(62),rotation);
            var decision=new RuinDecision(RuinKind.FOREST,1,actual,Collections.nCopies(45,71),Map.of());
            var source=level.getChunkSource();var generator=source.getGenerator();
            var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),source.randomState(),level.getStructureManager(),
                    level.getSeed(),new ChunkPos(actual.origin()),level,structure.biomes()::contains);
            var built=decision.stub(context).getPiecesBuilder().build().pieces().stream().map(p->RuinDecision.Box.of(p.getBoundingBox())).toList();
            h.assertValueEqual(xz(RuinDecision.boxes(RuinKind.FOREST,1,provisional)),xz(built),"Early XZ differs from builder "+rotation);
        }
        h.succeed();
    }
    @SuppressWarnings("unchecked") private static void yield(GameTestHelper h)throws Exception{
        var env=new ForestExternalReservationGameTests.Environment(h.getLevel(),116118);
        var forest=(RuinStructure)env.structure("orimod:forest_spring");var cp=new ChunkPos(60,24);
        // Give this pure-noise fixture the same read-only generated-chunk guard
        // runtime as its test world. No remote chunk is loaded or requested.
        var field=GinsoGenerationGuard.class.getDeclaredField("ACTIVE");field.setAccessible(true);var active=(Map<RandomState,Object>)field.get(null);
        var runtime=active.get(h.getLevel().getChunkSource().randomState());h.assertTrue(runtime!=null,"Missing generated guard fixture");active.put(env.random,runtime);
        try{
            var method=RuinSitingSites.class.getDeclaredMethod("owner",RuinStructure.class,Structure.GenerationContext.class,int.class);method.setAccessible(true);
            var own=(RuinStructure.SiteResult)method.invoke(null,forest,env.context(forest,cp),1);
            h.assertValueEqual(own.reason(),"R2_YIELD","Fixture must reject owner by neighbour");
            var predicted=forest.decision(env.context(forest,cp),1);
            h.assertTrue(predicted.site()!=null&&predicted.reason().equals("PASS"),"Owner yield poisoned independent base cache: "+predicted.reason());
        }finally{active.remove(env.random);}
        h.succeed();
    }
    @SuppressWarnings("unchecked") private static void cache(GameTestHelper h)throws Exception{
        var env=new ForestExternalReservationGameTests.Environment(h.getLevel(),116118);
        var outer=Class.forName("cn.wwl.orimod.world.CreekSurfaceSample");var field=outer.getDeclaredField("WORLDS");field.setAccessible(true);var worlds=(Map<RandomState,?>)field.get(null);
        var type=Class.forName("cn.wwl.orimod.world.CreekSurfaceSample$Cells");var ctor=type.getDeclaredConstructor(net.minecraft.world.level.chunk.ChunkGenerator.class,LevelHeightAccessor.class,RandomState.class);ctor.setAccessible(true);
        var at=type.getDeclaredMethod("at",int.class,int.class);at.setAccessible(true);var range=LevelHeightAccessor.create(-64,384);var release=new CountDownLatch(1);Object first;
        try(var pool=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<Object>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->{var local=ctor.newInstance(env.generator,range,env.random);release.await();return at.invoke(local,1728,688);}));
            release.countDown();first=futures.getFirst().get(20,TimeUnit.SECONDS);
            for(var f:futures)h.assertTrue(first==f.get(20,TimeUnit.SECONDS),"Concurrent cell factory produced duplicate surfaces");
        }
        var other=new ForestExternalReservationGameTests.Environment(h.getLevel(),116118);
        Object separate=at.invoke(ctor.newInstance(env.generator,range,other.random),1728,688);
        h.assertTrue(first!=separate&&first.equals(separate),"RandomState not isolated or exact values drifted");
        Object shorter=at.invoke(ctor.newInstance(env.generator,LevelHeightAccessor.create(0,128),env.random),1728,688);
        h.assertTrue(first!=shorter,"Height accessors share a cache entry");
        worlds.remove(env.random);Object rebuilt=at.invoke(ctor.newInstance(env.generator,range,env.random),1728,688);
        h.assertTrue(first!=rebuilt&&first.equals(rebuilt),"Cold clearing must rebuild without changing values");
        h.succeed();
    }
}
