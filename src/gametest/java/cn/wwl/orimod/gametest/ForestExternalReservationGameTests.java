package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.*;

/** Real overworld noise fixtures without loading/generating remote chunks. */
@GameTestHolder("orimod_forest_red")
public final class ForestExternalReservationGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        tests.add(new TestFunction("forest_external","forest_external.real_village_rejects_new_forest",
                "orimod_forest_red:room",Rotation.NONE,200,0,true,ForestExternalReservationGameTests::village));
        tests.add(new TestFunction("forest_external","forest_external.qualifications_match_saved_starts",
                "orimod_forest_red:room",Rotation.NONE,200,0,true,ForestExternalReservationGameTests::savedStarts));
        tests.add(new TestFunction("forest_external","forest_external.cached_qualification_performs_no_new_height_queries",
                "orimod_forest_red:room",Rotation.NONE,200,0,true,ForestExternalReservationGameTests::cache));
        return tests;
    }
    static final class Environment {
        final ServerLevel level;final long seed;final NoiseBasedChunkGenerator generator;final RandomState random;
        Environment(ServerLevel level,long seed){
            this.level=level;this.seed=seed;
            var settings=level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var biomes=MultiNoiseBiomeSource.createFromPreset(level.registryAccess().registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            generator=new NoiseBasedChunkGenerator(biomes,settings);
            random=RandomState.create(settings.value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
        }
        Structure structure(String id){return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(id));}
        Structure.GenerationContext context(Structure s,ChunkPos cp){
            return new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),random,
                    level.getStructureManager(),seed,cp,LevelHeightAccessor.create(-64,384),s.biomes()::contains);
        }
    }
    private static void village(GameTestHelper h){
        var env=new Environment(h.getLevel(),116120L);var village=env.structure("minecraft:village_plains");
        h.assertTrue(village.findValidGenerationPoint(env.context(village,new ChunkPos(-89,-47))).isPresent(),"Fixture village must be valid");
        var forest=(RuinStructure)env.structure("orimod:forest_spring");
        RuinStructure.SiteResult result;
        try{
            // This fixture supplies pure noise, not an active world RandomState. Exercise the actual
            // evaluate path; GENERATED is a separate dynamic guard and would invalidate this fixture.
            var evaluate=RuinStructure.class.getDeclaredMethod("evaluate",Structure.GenerationContext.class,int.class);
            evaluate.setAccessible(true);
            result=(RuinStructure.SiteResult)evaluate.invoke(forest,env.context(forest,new ChunkPos(-89,-52)),1);
        }catch(ReflectiveOperationException e){throw new AssertionError("Fixture could not enter production siting",e);}
        System.out.println("FOREST_EXTERNAL_RED seed=116120 forest=-89,-52 village=-89,-47 reason="+result.reason()+" site="+result.site());
        h.assertTrue(result.site()==null&&result.reason().equals("STRUCTURE_VILLAGES"),
                "New forest must reject the actual saved village overlap; production returned "+result.reason());
        h.succeed();
    }
    private static void savedStarts(GameTestHelper h){
        try(var in=ForestExternalReservationGameTests.class.getResourceAsStream("/r116/forest-external-qualifications.json")){
            if(in==null)throw new AssertionError("Missing saved FULL-chunk evidence");
            var rows=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            var type=Class.forName("cn.wwl.orimod.world.ForestStructureReservations");
            var qualify=type.getDeclaredMethod("qualify",Structure.GenerationContext.class,net.minecraft.core.Holder.class,ChunkPos.class);qualify.setAccessible(true);
            int valid=0,invalid=0;
            for(var row:rows){
                var r=row.getAsJsonObject();h.assertValueEqual(r.get("chunkStatus").getAsString(),"minecraft:full","Archive did not generate this chunk");
                var env=new Environment(h.getLevel(),r.get("seed").getAsLong());var xy=r.getAsJsonArray("chunk");var cp=new ChunkPos(xy.get(0).getAsInt(),xy.get(1).getAsInt());
                var key=net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET,ResourceLocation.parse(r.get("set").getAsString()));
                var set=h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(key);
                var expected=new ArrayList<String>();var saved=r.getAsJsonObject("starts");
                for(var entry:set.value().structures()){
                    String id=entry.structure().unwrapKey().orElseThrow().location().toString();if(!saved.has(id))continue;
                    var tag=net.minecraft.nbt.TagParser.parseTag(saved.get(id).getAsString());
                    if(!tag.getString("id").equals("INVALID"))expected.add(tag.getString("id"));
                }
                h.assertTrue(expected.size()<=1,"Vanilla structure set selected multiple winners");
                var result=(Optional<?>)qualify.invoke(null,env.context(set.value().structures().getFirst().structure().value(),cp),set,cp);
                h.assertValueEqual(result.isPresent(),!expected.isEmpty(),"Qualification vs saved start: "+r.get("seed")+" "+cp);
                if(result.isPresent()){
                    valid++;var selected=result.get().getClass().getDeclaredMethod("structure");selected.setAccessible(true);
                    h.assertValueEqual(selected.invoke(result.get()).toString(),expected.getFirst(),"Vanilla weighted winner differs");
                }else invalid++;
            }
            h.assertValueEqual(valid,1,"Expected actual 116120 village");h.assertValueEqual(invalid,4,"Expected five-failure village and three relief-rejected glades");
            h.succeed();
        }catch(Exception e){throw new AssertionError("Qualification archive check failed",e);}
    }
    @SuppressWarnings("unchecked") private static void cache(GameTestHelper h){
        try{
            var env=new Environment(h.getLevel(),116120);var forest=env.structure("orimod:forest_spring");var context=env.context(forest,new ChunkPos(-89,-52));
            var own=new RuinDecision(RuinKind.FOREST,1,new RuinPlacement(new net.minecraft.core.BlockPos(-1386,62,-778),Rotation.CLOCKWISE_180),Collections.nCopies(45,68),Map.of());
            var type=Class.forName("cn.wwl.orimod.world.ForestStructureReservations");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var instance=ctor.newInstance();
            var reject=type.getDeclaredMethod("rejection",Structure.GenerationContext.class,RuinDecision.class);reject.setAccessible(true);
            h.assertValueEqual(reject.invoke(instance,context,own),"STRUCTURE_VILLAGES","Cold qualification");
            var active=ForestCheapExternal.class.getDeclaredField("ACTIVE");active.setAccessible(true);var counter=(ThreadLocal<long[]>)active.get(null);long[] counts=new long[5];
            counter.set(counts);try{for(int i=0;i<4;i++)h.assertValueEqual(reject.invoke(instance,context,own),"STRUCTURE_VILLAGES","Cached qualification");}finally{counter.remove();}
            h.assertTrue(Arrays.stream(counts).sum()==0,"Cached rejection re-queried terrain/chunks/builder: "+Arrays.toString(counts));h.succeed();
        }catch(Exception e){throw new AssertionError("Qualification cache check failed",e);}
    }
}
