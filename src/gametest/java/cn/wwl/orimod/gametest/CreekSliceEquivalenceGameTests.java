package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.CreekSliceSettings;
import cn.wwl.orimod.world.RuinKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.*;
import java.util.function.Consumer;

/** Same coordinates and seeds: only the settings' palette selection may differ. */
@GameTestHolder("orimod_creek_rebuild")
public final class CreekSliceEquivalenceGameTests {
    private static final long SEED=0x435245454b534c49L;
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var rotation:Rotation.values())for(boolean reverse:new boolean[]{false,true}){
            tests.add(test("template_"+rotation.name().toLowerCase()+"_"+(reverse?"reverse":"forward"),h->actual(h,rotation,reverse)));
        }
        tests.add(test("nbt_fluids_mirror_pivot",h->miniature(h,false)));
        tests.add(test("processor_fallback",h->miniature(h,true)));
        tests.add(test("manager_prewarms_startup_and_reload",CreekSliceEquivalenceGameTests::prewarm));
        return tests;
    }
    private static void prewarm(GameTestHelper h){
        // Isolated manager: do not clear the live server's cache while other tests run.
        var server=h.getLevel().getServer();
        var path=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("template-prewarm-fixture");
        try(var storage=net.minecraft.world.level.storage.LevelStorageSource.createDefault(path).createAccess("manager")){
            var manager=new StructureTemplateManager(server.getResourceManager(),storage,
                    net.minecraft.util.datafix.DataFixers.getDataFixer(),h.getLevel().holderLookup(Registries.BLOCK));
            var first=manager.getOrCreate(RuinKind.CREEK.template(1));
            h.assertTrue(CreekSliceSettings.prepared(first),"Manager constructor did not prewarm creek");
            h.assertTrue(first==manager.getOrCreate(RuinKind.CREEK.template(1)),"Repeated lookup reloaded template");
            manager.onResourceManagerReload(server.getResourceManager());
            var after=manager.getOrCreate(RuinKind.CREEK.template(1));
            h.assertTrue(after!=first,"Reload did not replace stale template");
            h.assertTrue(CreekSliceSettings.prepared(after),"Reload returned before new palette was indexed");
            h.assertTrue(CreekSliceSettings.prepared(first),"Existing pieces lost their still-live old palette");
        }catch(java.io.IOException e){throw new RuntimeException(e);}
        h.succeed();
    }
    private static TestFunction test(String name,Consumer<GameTestHelper> body){
        return new TestFunction("creek_equivalence","creek_equivalence."+name,"orimod_creek_rebuild:room",Rotation.NONE,900,0,true,body);
    }

    private static void actual(GameTestHelper h,Rotation rotation,boolean reverse){
        var level=h.getLevel();var template=level.getStructureManager().getOrCreate(RuinKind.CREEK.template(1));
        // One compact template box + two-cell neighbour halo, not a full-height void world.
        var origin=h.absolutePos(new BlockPos(31000+rotation.ordinal()*240+(reverse?120:0),45,5600));
        var settings=settings(false,rotation,Mirror.NONE,BlockPos.ZERO,false);
        var bounds=template.getBoundingBox(settings,origin).inflatedBy(2);
        try(var area=new Area(level,bounds)){
            compare(h,template,origin,rotation,Mirror.NONE,BlockPos.ZERO,reverse,false,"template",bounds);
        }
        h.runAfterDelay(40,h::succeed);
    }

    private static void miniature(GameTestHelper h,boolean processor){
        var level=h.getLevel();var template=miniatureTemplate(level);
        var origin=h.absolutePos(new BlockPos(processor?32700:32500,45,5600));
        var pivot=new BlockPos(7,0,5);BoundingBox areaBounds=null;
        for(var rotation:Rotation.values())for(var mirror:Mirror.values()){
            var b=template.getBoundingBox(settings(false,rotation,mirror,pivot,processor),origin).inflatedBy(2);
            if(areaBounds==null)areaBounds=b;else areaBounds.encapsulate(b);
        }
        try(var area=new Area(level,Objects.requireNonNull(areaBounds))){
            for(var rotation:Rotation.values())for(var mirror:Mirror.values())for(boolean reverse:new boolean[]{false,true}){
                compare(h,template,origin,rotation,mirror,pivot,reverse,processor,"miniature",areaBounds);
            }
        }
        h.runAfterDelay(40,h::succeed);
    }

    private static StructurePlaceSettings settings(boolean optimized,Rotation rotation,Mirror mirror,BlockPos pivot,boolean processor){
        StructurePlaceSettings s=optimized?new CreekSliceSettings():new StructurePlaceSettings();
        s.setIgnoreEntities(true).setRotation(rotation).setMirror(mirror).setRotationPivot(pivot)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        if(processor)s.addProcessor(new BlockRotProcessor(.63f));
        return s;
    }

    private static void compare(GameTestHelper h,StructureTemplate template,BlockPos origin,Rotation rotation,Mirror mirror,
                                BlockPos pivot,boolean reverse,boolean processor,String fixture,BoundingBox resetBounds){
        var level=h.getLevel();String label=fixture+"/"+rotation+"/"+mirror+"/"+(reverse?"reverse":"forward")+"/processor="+processor;
        var standard=settings(false,rotation,mirror,pivot,processor);
        var optimized=settings(true,rotation,mirror,pivot,processor);
        var templateBounds=template.getBoundingBox(standard,origin);
        var chunks=new ArrayList<ChunkPos>();
        // Include empty chunks around the real template, exercising empty palette handling.
        for(int x=(templateBounds.minX()>>4)-1;x<=(templateBounds.maxX()>>4)+1;x++)
            for(int z=(templateBounds.minZ()>>4)-1;z<=(templateBounds.maxZ()>>4)+1;z++)chunks.add(new ChunkPos(x,z));
        if(reverse)Collections.reverse(chunks);
        var authoredAir=template.filterBlocks(origin,settings(false,rotation,mirror,pivot,false),Blocks.AIR);
        var airWitnesses=new ArrayList<BlockPos>();for(int i=0;i<authoredAir.size();i+=97)airWitnesses.add(authoredAir.get(i).pos().immutable());
        // A completely empty reset would fail to catch a bucket accidentally
        // omitting explicit template air. Seed identical sparse stone witnesses
        // inside that air, so each placement must actually clear old terrain.
        reset(level,resetBounds);seedAirWitnesses(level,airWitnesses);if(fixture.equals("miniature"))seedOutside(level,origin,standard);
        place(level,template,origin,standard,chunks);var before=snapshot(level,resetBounds);
        if(fixture.equals("miniature")&&!processor)assertOutsideConnected(h,level,origin,standard);
        reset(level,resetBounds);seedAirWitnesses(level,airWitnesses);if(fixture.equals("miniature"))seedOutside(level,origin,optimized);
        place(level,template,origin,optimized,chunks);var after=snapshot(level,resetBounds);
        if(fixture.equals("miniature")&&!processor)assertOutsideConnected(h,level,origin,optimized);
        var differences=new ArrayList<String>();int count=0,index=0;
        for(var pos:BlockPos.betweenClosed(resetBounds.minX(),resetBounds.minY(),resetBounds.minZ(),resetBounds.maxX(),resetBounds.maxY(),resetBounds.maxZ())){
            if(before.states[index]!=after.states[index]){
                if(differences.size()<16)differences.add("state "+pos.toShortString()+" original="+before.states[index]+" optimized="+after.states[index]);count++;
            }
            index++;
        }
        var allBe=new HashSet<>(before.blockEntities.keySet());allBe.addAll(after.blockEntities.keySet());
        for(var pos:allBe)if(!Objects.equals(before.blockEntities.get(pos),after.blockEntities.get(pos))){
            if(differences.size()<16)differences.add("BE "+pos.toShortString()+" original="+before.blockEntities.get(pos)+" optimized="+after.blockEntities.get(pos));count++;
        }
        if(!before.fluidTicks.equals(after.fluidTicks)){
            int limit=Math.max(before.fluidTicks.size(),after.fluidTicks.size());
            for(int i=0;i<limit;i++){
                var a=i<before.fluidTicks.size()?before.fluidTicks.get(i):null;
                var b=i<after.fluidTicks.size()?after.fluidTicks.get(i):null;
                if(!Objects.equals(a,b)){if(differences.size()<16)differences.add("fluid order="+i+" original="+a+" optimized="+b);count++;}
            }
        }
        long loot=before.blockEntities.values().stream().filter(t->t.contains("LootTableSeed",Tag.TAG_LONG)).count();
        System.out.println("CREEK_SLICE_EQ label="+label+" cells="+index+" airWitnesses="+airWitnesses.size()+" blockEntities="+before.blockEntities.size()+" lootSeeds="+loot+
                " fluidTicks="+before.fluidTicks.size()+" optimizedFluidTicks="+after.fluidTicks.size()+" mismatches="+count+" differences="+differences);
        h.assertTrue(count==0,"Palette slice changes "+label+": "+differences);
        if(fixture.equals("miniature")&&!processor){
            h.assertValueEqual(loot,3L,"Miniature must exercise three chest loot seeds, "+label);
            h.assertTrue(before.blockEntities.size()>=5,"Miniature lost chest/sign/marker BE before comparison "+label);
            h.assertFalse(before.fluidTicks.isEmpty(),"Miniature failed to schedule a fluid tick "+label);
        }
        if(fixture.equals("template"))h.assertFalse(before.blockEntities.isEmpty(),"Real template BE coverage missing");
    }

    private static void place(ServerLevel level,StructureTemplate template,BlockPos origin,StructurePlaceSettings settings,List<ChunkPos> chunks){
        level.setWeatherParameters(0,0,false,false);level.random.setSeed(SEED);
        for(var chunk:chunks){
            long seed=SEED^chunk.toLong();settings.setRandom(RandomSource.create(seed));
            settings.setBoundingBox(new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxBuildHeight()-1,chunk.getMaxBlockZ()));
            // Exactly the production flags and vanilla neighbour/fluid finalization.
            template.placeInWorld(level,origin,BlockPos.ZERO,settings,RandomSource.create(seed),Block.UPDATE_CLIENTS);
        }
    }

    private record FluidTickImage(BlockPos pos,String fluid,long delay,String priority) {}
    private record Snapshot(BlockState[] states,Map<BlockPos,CompoundTag> blockEntities,List<FluidTickImage> fluidTicks) {}
    private static Snapshot snapshot(ServerLevel level,BoundingBox bounds){
        var states=new BlockState[bounds.getXSpan()*bounds.getYSpan()*bounds.getZSpan()];var entities=new HashMap<BlockPos,CompoundTag>();int i=0;
        for(var pos:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ())){
            var state=level.getBlockState(pos);states[i++]=state;
            if(state.hasBlockEntity()){var be=level.getBlockEntity(pos);if(be!=null)entities.put(pos.immutable(),be.saveWithFullMetadata(level.registryAccess()));}
        }
        var ticks=new ArrayList<ScheduledTick<Fluid>>();
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){
            @SuppressWarnings("unchecked") var container=(LevelChunkTicks<Fluid>)level.getChunk(x,z).getFluidTicks();
            container.getAll().filter(t->bounds.isInside(t.pos())).forEach(ticks::add);
        }
        ticks.sort(Comparator.comparingLong((ScheduledTick<Fluid> t)->t.triggerTick()).thenComparingInt(t->t.priority().ordinal()).thenComparingLong(t->t.subTickOrder()));
        // Absolute subTickOrder is a global monotonic counter and cannot be reset.
        // List position preserves the complete relative execution order, including
        // same-time/same-priority ties, while delay is relative to this same server tick.
        var fluids=ticks.stream().map(t->new FluidTickImage(t.pos().immutable(),BuiltInRegistries.FLUID.getKey(t.type()).toString(),t.triggerTick()-level.getGameTime(),t.priority().name())).toList();
        return new Snapshot(states,Map.copyOf(entities),fluids);
    }

    private static void reset(ServerLevel level,BoundingBox bounds){
        level.getBlockTicks().clearArea(bounds);level.getFluidTicks().clearArea(bounds);
        for(var pos:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ())){
            var state=level.getBlockState(pos);if(state.isAir())continue;
            if(state.hasBlockEntity())level.removeBlockEntity(pos);
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
        }
        // Removal hooks can schedule work; a clean second pass gives both placements
        // exactly the same empty tick queues as well as the same empty voxel volume.
        level.getBlockTicks().clearArea(bounds);level.getFluidTicks().clearArea(bounds);
    }
    private static void seedAirWitnesses(ServerLevel level,List<BlockPos> witnesses){
        for(var pos:witnesses)level.setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    private static void seedOutside(ServerLevel level,BlockPos origin,StructurePlaceSettings s){
        var states=List.of(Blocks.OAK_FENCE.defaultBlockState(),Blocks.COBBLESTONE_WALL.defaultBlockState(),
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),
                Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST,true),Blocks.GLASS_PANE.defaultBlockState());
        for(int i=0;i<states.size();i++){
            var p=StructureTemplate.calculateRelativePosition(s,new BlockPos(-1,0,3+i*3)).offset(origin);
            level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(p,states.get(i).mirror(s.getMirror()).rotate(s.getRotation()),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }
    private static void assertOutsideConnected(GameTestHelper h,ServerLevel level,BlockPos origin,StructurePlaceSettings s){
        var p=StructureTemplate.calculateRelativePosition(s,new BlockPos(-1,0,3)).offset(origin);var state=level.getBlockState(p);
        h.assertTrue(state.is(Blocks.OAK_FENCE),"Outside fence removed "+p);
        h.assertTrue(state.getValue(FenceBlock.NORTH)||state.getValue(FenceBlock.SOUTH)||state.getValue(FenceBlock.EAST)||state.getValue(FenceBlock.WEST),
                "Reverse edge update did not connect pre-existing outside fence "+p);
        var v=StructureTemplate.calculateRelativePosition(s,new BlockPos(-1,0,12)).offset(origin);
        h.assertTrue(level.getBlockState(v).is(Blocks.VINE),"New wall did not support the outside vine "+v);
    }

    private static final class Area implements AutoCloseable{
        private final ServerLevel level;private final BoundingBox bounds;private final List<ChunkPos> tickets=new ArrayList<>();
        private final boolean rain,thunder;
        Area(ServerLevel level,BoundingBox bounds){
            this.level=level;this.bounds=bounds;rain=level.isRaining();thunder=level.isThundering();
            try{for(int x=(bounds.minX()>>4)-1;x<=(bounds.maxX()>>4)+1;x++)for(int z=(bounds.minZ()>>4)-1;z<=(bounds.maxZ()>>4)+1;z++){
                var chunk=new ChunkPos(x,z);if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(x,z,true);tickets.add(chunk);}level.getChunk(x,z);
            }}catch(RuntimeException e){close();throw e;}
        }
        @Override public void close(){
            level.getBlockTicks().clearArea(bounds);level.getFluidTicks().clearArea(bounds);
            level.setWeatherParameters(0,0,rain,thunder);CreekFixtureChunks.release(level,tickets);
        }
    }

    private static StructureTemplate miniatureTemplate(ServerLevel level){
        var blocks=new ArrayList<StructureTemplate.StructureBlockInfo>();
        for(int x=0;x<35;x++)for(int z=0;z<19;z++)blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x,0,z),Blocks.STONE_BRICKS.defaultBlockState(),null));
        for(int x:new int[]{3,17,32}){
            var nbt=new CompoundTag();nbt.putString("id","minecraft:chest");nbt.putString("LootTable","minecraft:chests/simple_dungeon");nbt.putLong("LootTableSeed",11);
            blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x,1,3),Blocks.CHEST.defaultBlockState(),nbt));
        }
        var sign=new CompoundTag();sign.putString("id","minecraft:sign");
        var text=new CompoundTag();var lines=new ListTag();for(var line:List.of("slice mirror", "pivot 7,0,5", "NBT stable", "line four"))lines.add(StringTag.valueOf("{\"text\":\""+line+"\"}"));
        text.put("messages",lines);text.putString("color","aqua");text.putBoolean("has_glowing_text",true);sign.put("front_text",text);
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(17,1,5),Blocks.OAK_SIGN.defaultBlockState(),sign));
        var marker=new CompoundTag();marker.putString("id","minecraft:structure_block");marker.putString("mode","DATA");marker.putString("metadata","creek equivalence marker");
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(6,1,5),Blocks.STRUCTURE_BLOCK.defaultBlockState(),marker));
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(15,1,10),Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true),null));
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(16,1,10),Blocks.WATER.defaultBlockState(),null));
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(17,1,10),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),null));
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(18,1,10),Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true),null));
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(2,1,6),Blocks.OAK_STAIRS.defaultBlockState(),null));
        // Explicit air is deliberately present and must not be discarded by a palette bucket.
        blocks.add(new StructureTemplate.StructureBlockInfo(new BlockPos(16,2,10),Blocks.AIR.defaultBlockState(),null));
        var palette=new ArrayList<BlockState>();var paletteTag=new ListTag();var blockTag=new ListTag();
        for(var block:blocks){
            int index=palette.indexOf(block.state());if(index<0){index=palette.size();palette.add(block.state());paletteTag.add(NbtUtils.writeBlockState(block.state()));}
            var tag=new CompoundTag();tag.put("pos",ints(block.pos().getX(),block.pos().getY(),block.pos().getZ()));tag.putInt("state",index);if(block.nbt()!=null)tag.put("nbt",block.nbt().copy());blockTag.add(tag);
        }
        var root=new CompoundTag();root.put("size",ints(35,4,19));root.put("palette",paletteTag);root.put("blocks",blockTag);root.put("entities",new ListTag());
        var template=new StructureTemplate();template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),root);return template;
    }
    private static ListTag ints(int... values){var list=new ListTag();for(int value:values)list.add(IntTag.valueOf(value));return list;}
}
