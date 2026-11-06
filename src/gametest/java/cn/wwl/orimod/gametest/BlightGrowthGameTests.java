package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_r117")
public final class BlightGrowthGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        add(tests,"natural_roots_nest_and_owner_core",h->{
            var level=h.getLevel();var at=sky(h);try(var f=new Edits(level)){
                for(var block:List.of(Blocks.MANGROVE_ROOTS,Blocks.MUDDY_MANGROVE_ROOTS,Blocks.MANGROVE_PROPAGULE,Blocks.BEE_NEST,OriBlocks.SPIRIT_VINE.get(),OriBlocks.BLIGHT_LEAVES.get())){
                    f.set(at,block.defaultBlockState());h.assertValueEqual(BlightProtection.artificialNearby(level,at,new BlightEcologyRules.Budget()),BlightProtection.Result.ALLOW,"Natural decoration created permanent shield: "+block);
                }
                f.set(at,OriBlocks.GLOOM_CORE.get().defaultBlockState());
                h.assertValueEqual(BlightProtection.artificialNearby(level,at,at,new BlightEcologyRules.Budget()),BlightProtection.Result.ALLOW,"Owner core blocks own expansion");
                h.assertValueEqual(BlightProtection.artificialNearby(level,at,at.east(),new BlightEcologyRules.Budget()),BlightProtection.Result.PROTECTED,"Another core BE ignored");
                h.assertFalse(BlightProtection.soil(Blocks.BEE_NEST.defaultBlockState()),"Nest became conversion target");
            }
        });
        add(tests,"double_flower_transaction_restores_exactly_and_water_is_untouched",h->{
            var level=h.getLevel();var center=sky(h);var floor=center.east(4).below();var d=new BlightData();
            d.register(center,new long[]{center.below().asLong()});d.initializeEcology(center,List.of(center.below()),true);
            try(var f=new Edits(level)){
                var lower=Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.LOWER);var upper=lower.setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER);
                f.set(floor,Blocks.ROOTED_DIRT.defaultBlockState());f.set(floor.above(),lower);f.set(floor.above(2),upper);
                var b=new BlightEcologyRules.Budget();var batch=BlightConversion.ground(level,floor,b,1);
                h.assertValueEqual(batch.size(),3,"Double flower is not one three-block transaction");
                h.assertFalse(BlightConversion.commit(level,d,center,floor,batch,false,new BlightEcologyRules.Budget(512,2)),"Partial double plant conversion");
                h.assertValueEqual(level.getBlockState(floor.above()),lower,"Rejected transaction changed plant");
                h.assertTrue(BlightConversion.commit(level,d,center,floor,batch,false,b),"Transaction failed");
                h.assertTrue(level.isEmptyBlock(floor.above())&&level.isEmptyBlock(floor.above(2)),"Flower upper half left behind");
                d.begin(level,center);for(int i=0;i<620;i++)d.advance(level);
                h.assertTrue(d.completed(center),"Wave did not finish");h.assertValueEqual(level.getBlockState(floor),Blocks.ROOTED_DIRT.defaultBlockState(),"Original soil lost");
                h.assertValueEqual(level.getBlockState(floor.above()),lower,"Lower flower not restored");h.assertValueEqual(level.getBlockState(floor.above(2)),upper,"Upper flower not restored");
                f.set(floor.above(),Blocks.WATER.defaultBlockState());h.assertTrue(BlightConversion.ground(level,floor,new BlightEcologyRules.Budget(),1).isEmpty(),"Underwater ground converted");
                h.assertFalse(BlightConversion.leaf(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true)),"Persistent leaf converts");
                h.assertTrue(BlightConversion.leaf(Blocks.OAK_LEAVES.defaultBlockState()),"Natural leaf rejected");
            }
        });
        add(tests,"flat_day_curve_and_restoration_cas",BlightGrowthGameTests::day);
        add(tests,"buried_soil_is_not_a_surface",h->{
            var level=h.getLevel();var floor=sky(h).below();try(var f=new Edits(level)){
                f.set(floor,Blocks.DIRT.defaultBlockState());
                for(var top:List.of(Blocks.GRASS_BLOCK,Blocks.STONE,Blocks.DIRT,Blocks.OAK_LEAVES,Blocks.WATER)){
                    f.set(floor.above(),top.defaultBlockState());h.assertTrue(BlightConversion.ground(level,floor,new BlightEcologyRules.Budget(),1).isEmpty(),"Buried soil selected under "+top);
                }
                for(var top:List.of(Blocks.AIR,Blocks.SHORT_GRASS,Blocks.SNOW,Blocks.OAK_LOG)){
                    f.set(floor.above(),top.defaultBlockState());h.assertFalse(BlightConversion.ground(level,floor,new BlightEcologyRules.Budget(),1).isEmpty(),"Valid surface rejected under "+top);
                }
                var leaves=OriBlocks.BLIGHT_LEAVES.get().defaultBlockState();f.set(floor,leaves);
                h.assertFalse(leaves.canOcclude(),"Leaves occlude neighboring faces");h.assertFalse(leaves.isSuffocating(level,floor),"Leaves suffocate");
                h.assertFalse(leaves.isValidSpawn(level,floor,net.minecraft.world.entity.EntityType.ZOMBIE),"Monster can spawn on leaves");h.assertFalse(leaves.isRandomlyTicking(),"Journal leaves decay");
            }
        });
        add(tests,"two_dimensions_share_one_write_budget",BlightGrowthGameTests::twoDimensions);
        add(tests,"unloadable_structure_ref_does_not_freeze_growth",BlightGrowthGameTests::backoff);
        add(tests,"leaves_and_scan_cursor_resume_after_save",BlightGrowthGameTests::leaves);
        tests.add(new TestFunction("r117_blight_unload","r117_blight_growth.real_unload_no_catchup","orimod_r117:room",600,0,true,BlightGrowthGameTests::unload));
        return tests;
    }
    private static void twoDimensions(GameTestHelper h){
        var a=h.getLevel();var b=a.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(b!=null,"Nether absent");
        var oldA=BlightData.get(a);var oldB=BlightData.get(b);var center=sky(h);var data=new ArrayList<BlightData>();
        BlightEcology.clear();
        try(var first=new Edits(a);var second=new Edits(b)){
            for(var edit:List.of(first,second)){
                var d=new BlightData();var positions=new ArrayList<Long>();
                for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){var p=center.offset(x,-1,z);edit.set(p,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());positions.add(p.asLong());}
                d.register(center,positions.stream().mapToLong(Long::longValue).toArray());d.begin(edit.level,center);
                var saved=d.save(new CompoundTag(),edit.level.registryAccess());saved.getList("regions",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("elapsed",599);
                d=BlightData.load(saved,edit.level.registryAccess());edit.level.getDataStorage().set("orimod_blight",d);data.add(d);
            }
            for(int tick=0;tick<40&&!data.stream().allMatch(d->d.completed(center));tick++){
                BlightEcology.step(a.getServer(),false);var m=BlightEcology.metrics();
                h.assertTrue((int)m.get("writes")<=32&&(int)m.get("reads")<=512,"Dimension multiplied budget");
                h.assertValueEqual(data.stream().mapToInt(d->d.lastWrites).sum(),m.get("writes"),"Global write accounting differs from dimension totals");
            }
            h.assertTrue(data.stream().allMatch(d->d.completed(center)),"One dimension starved");h.assertValueEqual(BlightEcology.metrics().get("maxWrites"),32,"Stress fixture did not reach shared ceiling");
            System.out.println("R117_BLIGHT_MULTIDIM "+new com.google.gson.Gson().toJson(BlightEcology.metrics()));
        }finally{a.getDataStorage().set("orimod_blight",oldA);b.getDataStorage().set("orimod_blight",oldB);BlightEcology.clear();}
    }
    private static void backoff(GameTestHelper h){
        var level=h.getLevel();var old=BlightData.get(level);var d=new BlightData();var center=sky(h);var chunk=level.getChunkAt(center);
        var refs=new HashMap<>(chunk.getAllReferences());var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(RuinKind.FOREST.location());
        var far=new net.minecraft.world.level.ChunkPos(999999,999999);level.getDataStorage().set("orimod_blight",d);BlightEcology.clear();
        try(var f=new Edits(level)){
            loadArea(level,center,40);for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)f.set(center.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
            f.set(center.below(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());f.set(center,OriBlocks.GLOOM_CORE.get().defaultBlockState());
            ((GloomCoreBlockEntity)level.getBlockEntity(center)).generatedTerrain(new long[]{center.below().asLong()});
            chunk.setAllReferences(new HashMap<>());chunk.addReferenceForStructure(structure,far.toLong());
            // Unloaded structure origins are skipped (not deferred forever), so setblock cores can grow in survival.
            for(int tick=0;tick<80;tick++)BlightEcology.step(level.getServer(),false);
            h.assertTrue(d.ecology(center).grownColumns()>0,"Unloadable structure ref froze growth");
            h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null,"Ecology must not force-load structure origins");
            System.out.println("R117_BLIGHT_BACKOFF unloadable structure refs skipped; growth proceeded without loading origin");
        }finally{chunk.setAllReferences(refs);level.getDataStorage().set("orimod_blight",old);BlightEcology.clear();}
    }
    private static void leaves(GameTestHelper h){
        var level=h.getLevel();var old=BlightData.get(level);var d=new BlightData();var center=sky(h);level.getDataStorage().set("orimod_blight",d);BlightEcology.clear();
        try(var f=new Edits(level)){
            loadArea(level,center,48);f.set(center.below(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());f.set(center,OriBlocks.GLOOM_CORE.get().defaultBlockState());
            ((GloomCoreBlockEntity)level.getBlockEntity(center)).generatedTerrain(new long[]{center.below().asLong()});d.initializeEcology(center,List.of(center.below()),true);
            var leafPositions=new ArrayList<BlockPos>();
            for(int x=4;x<12;x++){
                var floor=center.offset(x,-1,0);f.set(floor,Blocks.DIRT.defaultBlockState());
                h.assertTrue(BlightConversion.commit(level,d,center,floor,BlightConversion.ground(level,floor,new BlightEcologyRules.Budget(),1),false,new BlightEcologyRules.Budget()),"Seed new column");
                for(int y=1;y<=3;y++)f.set(floor.above(y),Blocks.OAK_LOG.defaultBlockState());
                for(int y=4;y<=6;y++){var p=floor.above(y);f.set(p,Blocks.OAK_LEAVES.defaultBlockState());leafPositions.add(p);}
            }
            for(int i=0;i<5;i++)BlightEcology.step(level.getServer(),false);
            var saved=d.save(new CompoundTag(),level.registryAccess());d=BlightData.load(saved,level.registryAccess());level.getDataStorage().set("orimod_blight",d);BlightEcology.clear();
            for(int i=0;i<200;i++)BlightEcology.step(level.getServer(),false);
            h.assertValueEqual(d.ecology(center).decoratedColumns(),8,"Leaf scan fell behind / lost saved cursor");
            for(var p:leafPositions)h.assertTrue(level.getBlockState(p).is(OriBlocks.BLIGHT_LEAVES),"Missed canopy at "+p);
            h.assertValueEqual(d.ecology(center).changes().stream().filter(c->c.expected().is(OriBlocks.BLIGHT_LEAVES)).count(),24L,"Leaves duplicated or missed after reload");
            d.begin(level,center);for(int i=0;i<650;i++)d.advance(level);
            for(var p:leafPositions)h.assertValueEqual(level.getBlockState(p),Blocks.OAK_LEAVES.defaultBlockState(),"Leaf species/properties not restored");
            System.out.println("R117_BLIGHT_LEAVES 24 leaves / 8 complete columns; partial scan reload; exact leaf state restored");
        }finally{level.getDataStorage().set("orimod_blight",old);BlightEcology.clear();}
    }
    private static void unload(GameTestHelper h){
        var level=h.getLevel();var old=BlightData.get(level);var d=new BlightData();var center=new BlockPos(2_700_008,180,2_700_008);
        level.getDataStorage().set("orimod_blight",d);BlightEcology.clear();var f=new Edits(level);var done=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{f.close();level.getDataStorage().set("orimod_blight",old);BlightEcology.clear();};
        try{
            loadArea(level,center,40);for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)f.set(center.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
            f.set(center.below(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());f.set(center,OriBlocks.GLOOM_CORE.get().defaultBlockState());
            ((GloomCoreBlockEntity)level.getBlockEntity(center)).generatedTerrain(new long[]{center.below().asLong()});
            for(int i=0;i<60;i++)BlightEcology.step(level.getServer(),false);h.assertTrue(d.ecology(center).grownColumns()>0,"Fixture never grew");
        }catch(Throwable t){done.set(true);cleanup.run();throw t;}
        h.onEachTick(()->{
            if(done.get()||level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)!=null)return;done.set(true);
            try{
                var copy=BlightData.load(d.save(new CompoundTag(),level.registryAccess()),level.registryAccess());level.getDataStorage().set("orimod_blight",copy);BlightEcology.clear();
                var journal=copy.ecology(center);long tick=journal.loadedTicks();int before=journal.grownColumns();var saved=journal.save();
                for(int i=0;i<2000;i++)BlightEcology.step(level.getServer(),false);
                h.assertValueEqual(journal.save(),saved,"Unloaded journal advanced");h.assertTrue(level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)==null,"Scheduler acquired chunk");
                loadArea(level,center,40);h.assertTrue(level.getBlockEntity(center) instanceof GloomCoreBlockEntity,"Actual saved core was lost");BlightEcology.wake(level,center);
                for(int i=0;i<40;i++)BlightEcology.step(level.getServer(),false);
                h.assertValueEqual(journal.loadedTicks(),tick+40,"Offline ticks were replayed");h.assertTrue(journal.grownColumns()>before&&journal.grownColumns()<=before+4,"Reload burst exceeded 40 loaded ticks");
                System.out.println("R117_BLIGHT_UNLOAD pausedSteps=2000 loadedDelta=40 growthDelta="+(journal.grownColumns()-before));h.succeed();
            }finally{cleanup.run();}
        });
        h.runAtTickTime(550,()->{if(done.compareAndSet(false,true)){cleanup.run();h.fail("Fixture did not actually unload within 550 ticks");}});
    }
    private static void loadArea(ServerLevel level,BlockPos center,int r){for(int x=(center.getX()-r)>>4;x<=(center.getX()+r)>>4;x++)for(int z=(center.getZ()-r)>>4;z<=(center.getZ()+r)>>4;z++)level.getChunk(x,z);}
    private static void day(GameTestHelper h){
        var level=h.getLevel();var center=sky(h);var previous=BlightData.get(level);var d=new BlightData();var points=new ArrayList<Map<String,Object>>();
        var report=new LinkedHashMap<String,Object>();report.put("kind","GameTest scheduler steps; actual client day remains a separate acceptance check");
        report.put("center",List.of(center.getX(),center.getY(),center.getZ()));
        level.getDataStorage().set("orimod_blight",d);BlightEcology.clear();
        try(var f=new Edits(level)){
            for(int cx=(center.getX()-96)>>4;cx<=(center.getX()+96)>>4;cx++)for(int cz=(center.getZ()-96)>>4;cz<=(center.getZ()+96)>>4;cz++)level.getChunk(cx,cz);
            var seeds=new ArrayList<BlockPos>();
            for(int x=-52;x<=52;x++)for(int z=-52;z<=52;z++){
                var p=center.offset(x,-1,z);boolean initial=x*x+z*z<=144;
                f.set(p,initial?OriBlocks.BLIGHTED_SOIL.get().defaultBlockState():Blocks.GRASS_BLOCK.defaultBlockState());if(initial)seeds.add(p);
            }
            f.set(center,OriBlocks.GLOOM_CORE.get().defaultBlockState());
            ((GloomCoreBlockEntity)level.getBlockEntity(center)).generatedTerrain(seeds.stream().mapToLong(BlockPos::asLong).toArray());
            for(int i=0;i<8&&d.ecology(center)==null;i++)BlightEcology.step(level.getServer(),false);
            h.assertTrue(d.ecology(center)!=null,"Initial terrain did not migrate");var region=d.ecology(center);
            points.add(point(0,region));BlightEcology.resetMetrics();
            for(int tick=1;tick<=24000;tick++){
                BlightEcology.step(level.getServer(),false);var m=BlightEcology.metrics();
                h.assertTrue((int)m.get("reads")<=512&&(int)m.get("writes")<=32&&(int)m.get("indices")<=128&&(int)m.get("candidates")<=32,"Shared terrain cap exceeded");
                if(tick%2000==0)points.add(point(tick,region));
            }
            report.put("area_curve",points);report.put("growth_metrics",BlightEcology.metrics());
            h.assertTrue(region.grownColumns()>=2000,"Flat day grew too little: "+points);
            h.assertTrue(region.grownColumns()<=2402,"Growth minted extra credits");
            var boundary=region.columns().stream().max(Comparator.comparingDouble(p->p.distSqr(center))).orElseThrow();
            h.assertTrue(boundary.distSqr(center)>32*32,"Fixture did not exercise footprint outside old sanctuary");
            f.set(boundary,Blocks.GOLD_BLOCK.defaultBlockState());var mined=region.columns().stream().filter(p->!seeds.contains(p)&&!p.equals(boundary)).findFirst().orElseThrow();f.set(mined,Blocks.AIR.defaultBlockState());
            var changes=List.copyOf(region.changes());int count=region.grownColumns();int duration=BlightEcologyRules.duration(region.radius());
            h.assertTrue(d.begin(level,center),"Destroy did not begin wave");level.removeBlock(center,false);
            h.assertTrue(d.ecologyProtects(boundary),"Neighbor core can recapture purifying footprint");
            int elapsed=0;BlightEcology.resetMetrics();
            while(!d.completed(center)&&elapsed<duration+200){BlightEcology.step(level.getServer(),false);elapsed++;}
            report.put("nominal_wave_ticks",duration);report.put("completion_ticks",elapsed);report.put("restoration_metrics",BlightEcology.metrics());
            h.assertTrue(d.completed(center),"Restoration did not complete: "+report);h.assertValueEqual(region.grownColumns(),count,"Core removal allowed growth");
            h.assertValueEqual(level.getBlockState(boundary),Blocks.GOLD_BLOCK.defaultBlockState(),"Player-built boundary overwritten");h.assertTrue(level.isEmptyBlock(mined),"Player-mined boundary filled");
            for(var c:changes)if(!c.pos().equals(boundary)&&!c.pos().equals(mined)&&!c.pos().equals(mined.above()))h.assertValueEqual(level.getBlockState(c.pos()),c.before(),"CAS restore at "+c.pos());
            var copy=BlightData.load(d.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(copy.ecologyProtects(boundary),"Reload lost permanent footprint");h.assertFalse(copy.protects(Vec3.atCenterOf(boundary)),"Ecology expanded the old Gloom Tide sanctuary");h.assertValueEqual(copy.ecologyRecords(),0,"Completed original-state records leaked");
            report.put("result","passed");
        }finally{
            System.out.println("R117_BLIGHT_DAY "+new com.google.gson.Gson().toJson(report));
            try{java.nio.file.Files.writeString(ReportFiles.output(java.nio.file.Path.of("../build/test-results/r117-d/latest-area.json")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(java.io.IOException e){throw new RuntimeException(e);}
            finally {level.getDataStorage().set("orimod_blight",previous);BlightEcology.clear();}
        }
    }
    private static Map<String,Object> point(int tick,BlightRegion r){return Map.of("tick",tick,"columns",r.columns().size(),"newColumns",r.grownColumns(),"radius",r.radius(),"records",r.size(),
            "leaves",r.changes().stream().filter(c->c.expected().is(OriBlocks.BLIGHT_LEAVES)).count(),"scannedColumns",r.decoratedColumns());}
    private static BlockPos sky(GameTestHelper h){var p=h.absolutePos(new BlockPos(4,0,4));return new BlockPos((p.getX()&~15)+8,180,(p.getZ()&~15)+8);}
    private static final class Edits implements AutoCloseable {
        final ServerLevel level;final Map<BlockPos,BlockState> before=new LinkedHashMap<>();Edits(ServerLevel level){this.level=level;}
        void set(BlockPos p,BlockState state){before.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,state,Block.UPDATE_CLIENTS);}
        public void close(){before.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_CLIENTS));}
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run){tests.add(new TestFunction("r117_blight_growth","r117_blight_growth."+name,"orimod_r117:room",200,0,true,h->{run.accept(h);h.succeed();}));}
}
