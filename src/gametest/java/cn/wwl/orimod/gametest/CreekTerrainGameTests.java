package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real block/shape grading checks, separate from the authored course's traversal tests. */
@GameTestHolder("orimod_creek_rebuild")
public final class CreekTerrainGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        tests.add(test("foreign_drop_requires_observed_zero_writes",CreekForeignTerrainTrace::proofContract));
        tests.add(test("scan_reaches_saved_natural_below_old_depth",CreekTerrainGameTests::deepScan));
        tests.add(test("ground_scan_includes_solid_outcrops",CreekTerrainGameTests::groundScan));
        tests.add(test("bedrock_and_foreign_moss_wall",CreekTerrainGameTests::bedrockAndWall));
        tests.add(test("rim_decorations_preserve_solid_support",CreekTerrainGameTests::decorationProtection));
        for(var r:Rotation.values()){
            tests.add(test("graded_banks_"+r.name().toLowerCase(),h->graded(h,r)));
            tests.add(test("slice_order_"+r.name().toLowerCase(),h->sliceOrder(h,r)));
            tests.add(test("deep_cave_preserved_"+r.name().toLowerCase(),h->exceptionColumn(h,r,false)));
            tests.add(test("foreign_building_preserved_"+r.name().toLowerCase(),h->exceptionColumn(h,r,true)));
            tests.add(test("foreign_bottom_slab_edge_"+r.name().toLowerCase(),h->bottomSlab(h,r)));
        }
        return tests;
    }
    private static TestFunction test(String name,java.util.function.Consumer<GameTestHelper> run){
        return new TestFunction("creek_terrain","creek_terrain."+name,"orimod_creek_rebuild:room",Rotation.NONE,700,0,true,run);
    }
    private record Terrain(Map<BlockPos,Integer> land,Map<BlockPos,Integer> distance,Set<BlockPos> rim,int rearFromZ) {}
    private static final class Data { static final Terrain TERRAIN=read(); }
    private static Terrain read(){
        try(var in=RuinKind.class.getResourceAsStream("/orimod/ruin_layout/creek_ruins_terrain_v1.json")){
            if(in==null)throw new IllegalStateException("Missing terrain contract");
            var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var size=json.getAsJsonArray("size");int sx=size.get(0).getAsInt(),sz=size.get(2).getAsInt();
            var land=new HashMap<BlockPos,Integer>();
            for(var v:json.getAsJsonArray("columns")){var a=v.getAsJsonArray();land.put(new BlockPos(a.get(0).getAsInt(),0,a.get(1).getAsInt()),a.get(2).getAsInt());}
            var exterior=new HashSet<BlockPos>();var todo=new ArrayDeque<BlockPos>();
            var start=new BlockPos(-21,0,-21);exterior.add(start);todo.add(start);
            while(!todo.isEmpty()){
                var p=todo.remove();
                for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);
                    if(q.getX() < -21||q.getX()>sx+20||q.getZ() < -21||q.getZ()>sz+20||land.containsKey(q)||!exterior.add(q))continue;
                    todo.add(q);
                }
            }
            var distances=new HashMap<BlockPos,Integer>();todo.addAll(land.keySet());land.keySet().forEach(p->distances.put(p,0));
            while(!todo.isEmpty()){
                var p=todo.remove();int n=distances.get(p)+1;if(n>21)continue;
                for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);if(distances.putIfAbsent(q,n)==null)todo.add(q);}
            }
            distances.keySet().removeIf(p->!exterior.contains(p));
            var rim=new HashSet<BlockPos>();distances.forEach((p,d)->{if(d<=8)rim.add(p);});
            return new Terrain(Map.copyOf(land),Map.copyOf(distances),Set.copyOf(rim),json.get("rear_from_z").getAsInt());
        }catch(Exception e){throw new IllegalStateException("Creek grading fixture contract",e);}
    }
    static int natural(BlockPos p){
        // A local back-left ravine needs retaining stone; the rest joins gently.
        if(p.getX()<=3&&p.getZ()>=48&&p.getZ()<=61)return 0;
        int base=p.getZ()>=50?9:7;
        // Natural high shoulder against the fixed front-right y9 template bank.
        // The first rim column must be cut rather than inherit y11 unchanged.
        return Math.max(Math.max(base,11-Math.max(0,Math.abs(p.getX()-34)-2)-Math.max(0,Math.abs(p.getZ()-7)-2)),
                11-Math.abs(p.getX()-25)-Math.abs(p.getZ()+3));
    }
    private static void prepare(CreekRuinsLayoutGameTests.Site s){
        var l=s.h.getLevel();var terrain=Data.TERRAIN;
        for(var p:terrain.distance.keySet()){
            int y=natural(p);
            // A thin but genuinely solid natural surface is enough; everything
            // above it is cleared so a random pre-existing tree cannot mask a gap.
            set(l,s.p.block(p.atY(y-1)),Blocks.DIRT.defaultBlockState());
            set(l,s.p.block(p.atY(y)),Blocks.GRASS_BLOCK.defaultBlockState());
            for(int yy=y+1;yy<=23;yy++)set(l,s.p.block(p.atY(yy)),Blocks.AIR.defaultBlockState());
            if(terrain.distance.get(p)==8)set(l,s.p.block(p.atY(y+1)),Blocks.AZURE_BLUET.defaultBlockState());
        }
        // Every named portal/axle/root foot is also tested on a carver pocket.
        // The template owns y=0 upwards; the terrain helper must bridge below it.
        for(var p:List.of(new BlockPos(16,0,3),new BlockPos(27,0,3),new BlockPos(18,0,22),new BlockPos(19,0,23),new BlockPos(28,0,22),new BlockPos(29,0,23))){
            s.h.assertTrue(terrain.land.containsKey(p),"Ground member missing terrain footprint "+p);
            set(l,s.p.block(p.atY(-5)),Blocks.STONE.defaultBlockState());
            for(int y=-4;y<0;y++)set(l,s.p.block(p.atY(y)),Blocks.AIR.defaultBlockState());
        }
    }
    private static void set(ServerLevel l,BlockPos p,BlockState state){
        if(l.getBlockState(p)!=state)l.setBlock(p,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    private static void approach(CreekRuinsLayoutGameTests.Site s){
        var l=s.h.getLevel();int[] samples=new int[RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH];Arrays.fill(samples,s.p.origin().getY()+8);
        var a=new RuinApproachPiece(RuinApproachPiece.doorway(RuinKind.CREEK,s.piece.layout(),s.p),samples);
        a.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),a.getBoundingBox(),new ChunkPos(s.p.origin()),BlockPos.ZERO);
    }
    private static boolean ground(BlockState state,ServerLevel l,BlockPos p){
        return CreekTerrainSurface.ground(state,l,p);
    }
    private static void deepScan(GameTestHelper h){
        // A raised fixture/structure can be >24 blocks above its saved N. The
        // scan must reach N-6 rather than inventing a cave at the old depth cap.
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,0,1));int bottom=l.getMinBuildHeight();
        for(int y=bottom;y<=p.getY()+100;y++)set(l,p.atY(y),(y==bottom?Blocks.BEDROCK:Blocks.AIR).defaultBlockState());
        try{
            var type=Class.forName("cn.wwl.orimod.world.CreekRuinTerrain");
            var t=l.getStructureManager().getOrCreate(RuinKind.CREEK.template(1));
            var ctor=type.getDeclaredConstructor(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.class,BlockPos.class,
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings.class,int[].class);ctor.setAccessible(true);
            var terrain=ctor.newInstance(t,BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),new int[0]);
            var scan=type.getDeclaredMethod("surface",WorldGenLevel.class,BlockPos.class,int.class,boolean.class);scan.setAccessible(true);
            var found=scan.invoke(terrain,l,p.atY(bottom+70),bottom,true);
            h.assertTrue(found!=null,"Old fixed-depth limit misses saved natural surface");
            var y=found.getClass().getDeclaredMethod("y");y.setAccessible(true);
            var building=found.getClass().getDeclaredMethod("building");building.setAccessible(true);
            h.assertValueEqual(y.invoke(found),bottom,"Wrong natural floor");
            h.assertValueEqual(building.invoke(found),false,"Natural bedrock became building");
            // A tree/bee-nest column is vegetation, not a foreign building. The
            // scanner must see through it, while the BE remains unmodifiable.
            var tree=p.atY(bottom+4);set(l,tree,Blocks.OAK_LOG.defaultBlockState());
            set(l,tree.above(),Blocks.BEE_NEST.defaultBlockState());
            set(l,tree.east(),Blocks.OAK_LOG.defaultBlockState());
            found=scan.invoke(terrain,l,p.atY(bottom+70),bottom,true);
            h.assertValueEqual(building.invoke(found),false,"Bee nest beside tree classified whole column as a building");
            h.assertValueEqual(y.invoke(found),bottom,"Bee nest raised ground");
            h.assertFalse(CreekTerrainSurface.mutable(l.getBlockState(tree.above()),l,tree.above()),"Bee nest BE lost hard protection");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        h.succeed();
    }
    private static void groundScan(GameTestHelper h){
        var p=h.absolutePos(new BlockPos(1,2,1));var l=h.getLevel();
        for(var block:List.of(Blocks.BEDROCK,Blocks.DEEPSLATE,Blocks.COAL_ORE,Blocks.TUFF,Blocks.AMETHYST_BLOCK,Blocks.MOSS_BLOCK,Blocks.CLAY,Blocks.GRAVEL,Blocks.MUD,Blocks.ROOTED_DIRT,Blocks.SMOOTH_BASALT,Blocks.DRIPSTONE_BLOCK,Blocks.MAGMA_BLOCK))
        {   h.assertTrue(ground(block.defaultBlockState(),l,p),"Solid outcrop skipped: "+block);
            h.assertTrue(CreekTerrainSurface.natural(block.defaultBlockState()),"Natural outcrop cannot be graded: "+block);
        }
        for(var block:List.of(Blocks.BIRCH_PLANKS,Blocks.COBBLESTONE,Blocks.STONE_BRICKS,Blocks.DIRT_PATH,Blocks.OAK_STAIRS,Blocks.COBBLESTONE_WALL,Blocks.MOSSY_COBBLESTONE_WALL))
            h.assertFalse(CreekTerrainSurface.natural(block.defaultBlockState()),"Building permitted to be graded: "+block);
        for(var block:List.of(Blocks.OAK_LOG,Blocks.OAK_LEAVES,Blocks.SHORT_GRASS,Blocks.SNOW,Blocks.WATER,Blocks.MOSSY_COBBLESTONE_WALL))
            h.assertFalse(ground(block.defaultBlockState(),l,p),"Decoration mistaken for ground: "+block);
        h.assertFalse(ground(cn.wwl.orimod.block.OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState(),l,p),"Overhanging authored root mistaken for terrain");
        for(var block:net.minecraft.core.registries.BuiltInRegistries.BLOCK){
            var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            if(id.getNamespace().equals("orimod")&&!id.getPath().equals("blighted_soil"))
                h.assertTrue(block.defaultBlockState().is(CreekTerrainSurface.IGNORE),"Missing live terrain exclusion: "+id);
        }
        h.assertTrue(Blocks.POINTED_DRIPSTONE.defaultBlockState().is(CreekTerrainSurface.IGNORE),"Pointed dripstone must not raise ground");
        h.assertFalse(CreekTerrainSurface.building(Blocks.PUMPKIN.defaultBlockState(),l,p,false),"Natural pumpkin treated as building");
        h.assertTrue(CreekTerrainSurface.natural(Blocks.POWDER_SNOW.defaultBlockState()),"Powder snow classification");
        h.assertFalse(CreekTerrainSurface.mutable(Blocks.BEDROCK.defaultBlockState(),l,p),"Bedrock cut permission");
        h.assertFalse(CreekTerrainSurface.mutable(Blocks.BARRIER.defaultBlockState(),l,p),"Barrier cut permission");
        h.assertFalse(CreekTerrainSurface.mutable(Blocks.CHEST.defaultBlockState(),l,p),"BE cut permission");
        for(var block:List.of(Blocks.SUSPICIOUS_SAND,Blocks.SUSPICIOUS_GRAVEL)){
            h.assertTrue(CreekTerrainSurface.natural(block.defaultBlockState()),"Archaeological terrain classification");
            h.assertFalse(CreekTerrainSurface.mutable(block.defaultBlockState(),l,p),"Archaeological BE cut");
        }
        for(var block:List.of(Blocks.COBBLESTONE_WALL,Blocks.MOSSY_COBBLESTONE_WALL,Blocks.BIRCH_PLANKS))
            h.assertTrue(CreekTerrainSurface.building(block.defaultBlockState(),l,p,false),"Foreign building ignored "+block);
        h.assertTrue(CreekTerrainSurface.building(Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),l,p,false),"Waterlogged foreign wall ignored");
        h.assertFalse(CreekTerrainSurface.building(Blocks.BEDROCK.defaultBlockState(),l,p,false),"Bedrock treated as building");
        for(var block:List.of(Blocks.INFESTED_STONE,Blocks.INFESTED_DEEPSLATE,Blocks.BONE_BLOCK,Blocks.RAW_IRON_BLOCK,Blocks.RAW_COPPER_BLOCK,Blocks.FIRE_CORAL_BLOCK,Blocks.DEAD_FIRE_CORAL_BLOCK))
            h.assertTrue(CreekTerrainSurface.natural(block.defaultBlockState()),"Natural vein/fossil/coral omitted: "+block);
        for(var block:List.of(Blocks.BEE_NEST,Blocks.BAMBOO,Blocks.BIG_DRIPLEAF,Blocks.BIG_DRIPLEAF_STEM,Blocks.MOSS_CARPET,Blocks.LILY_PAD,Blocks.SEA_PICKLE,Blocks.COCOA,Blocks.CHORUS_PLANT,Blocks.CHORUS_FLOWER,Blocks.PITCHER_PLANT,Blocks.PITCHER_CROP,Blocks.SMALL_DRIPLEAF,Blocks.PINK_PETALS)){
            h.assertFalse(CreekTerrainSurface.building(block.defaultBlockState(),l,p,false),"Plant classified as building: "+block);
            h.assertFalse(ground(block.defaultBlockState(),l,p),"Plant raises ground: "+block);
        }
        for(var block:List.of(Blocks.SCAFFOLDING,Blocks.DIRT_PATH,Blocks.FARMLAND,Blocks.PACKED_MUD,Blocks.MUD_BRICKS,Blocks.CRYING_OBSIDIAN,Blocks.HAY_BLOCK,Blocks.CARVED_PUMPKIN,Blocks.JACK_O_LANTERN,Blocks.INFESTED_COBBLESTONE,Blocks.INFESTED_STONE_BRICKS))
            h.assertTrue(CreekTerrainSurface.building(block.defaultBlockState(),l,p,false),"Crafted structure block ignored: "+block);
        var categories=new TreeMap<String,Object>();
        for(var block:net.minecraft.core.registries.BuiltInRegistries.BLOCK){var state=block.defaultBlockState();
            categories.put(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString(),Map.of(
                    "natural",CreekTerrainSurface.natural(state),"ignore",state.is(CreekTerrainSurface.IGNORE),
                    "scanSubcategory",CreekTerrainSurface.category(state,l,p).name(),
                    "building",CreekTerrainSurface.building(state,l,p,false),
                    "ground",CreekTerrainSurface.ground(state,l,p),"blockEntity",state.hasBlockEntity(),
                    "mutable",CreekTerrainSurface.mutable(state,l,p),"destroySpeed",state.getDestroySpeed(l,p),
                    "category",state.is(CreekTerrainSurface.IGNORE)?"IGNORE":CreekTerrainSurface.natural(state)?"NATURAL":
                            CreekTerrainSurface.building(state,l,p,false)?"BUILDING":"IGNORE"));
        }
        try {var out=java.nio.file.Path.of("../build/test-results/creek-terrain-categories.json");java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(categories));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        h.succeed();
    }
    private static void bedrockAndWall(GameTestHelper h){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,Rotation.NONE,26500,10500,true)){
            prepare(s);var l=h.getLevel();var bed=s.p.block(new BlockPos(-4,7,20));
            set(l,bed,Blocks.BEDROCK.defaultBlockState());
            var wall=s.p.block(new BlockPos(-4,8,40));set(l,wall,Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
            var before=new HashMap<BlockPos,BlockState>();
            for(int y=-8;y<=23;y++){var at=wall.atY(s.p.origin().getY()+y);before.put(at,l.getBlockState(at));}
            s.place(true);approach(s);
            h.assertTrue(l.getBlockState(bed).is(Blocks.BEDROCK),"Natural bedrock was cut");
            h.assertFalse(l.getBlockState(bed.above()).is(Blocks.MOSSY_COBBLESTONE_WALL),"Bedrock created a false guard");
            h.assertFalse(s.piece.ownsCreekGuard(wall,l.getBlockState(wall)),"Foreign wall claimed by this piece");
            before.forEach((p,state)->h.assertValueEqual(l.getBlockState(p),state,"Foreign moss-wall column filled/cut "+p));
            var restored=new RuinPiece(l.getStructureManager(),s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)));
            h.assertFalse(restored.ownsCreekGuard(wall,l.getBlockState(wall)),"Reload claimed foreign wall");
            System.out.println("CREEK_CLASSIFICATION bedrockNatural=true bedrockCut=false falseGuard=false foreignMossWallProtected=true");
            settleThenSucceed(h);
        }
    }
    private static void decorationProtection(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(1,3,1));
        var template=level.getStructureManager().getOrCreate(RuinKind.CREEK.template(1));
        var palettes=((cn.wwl.orimod.mixin.StructureTemplatePaletteAccessor)template).orimod$palettes();
        int checked=0;
        for(var raw:palettes.getFirst().blocks()){
            if(!Data.TERRAIN.rim.contains(raw.pos().atY(0)))continue;
            h.assertTrue(raw.state().is(CreekTerrainSurface.IGNORE),"Rim template writes non-decoration "+raw);
            var placed=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(at,raw.state(),raw.nbt());
            var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            for(var block:List.of(Blocks.STONE,Blocks.GRASS_BLOCK,Blocks.COAL_ORE,Blocks.BIRCH_PLANKS,Blocks.COBBLESTONE,Blocks.WATER)){
                set(level,at,block.defaultBlockState());
                h.assertTrue(CreekRimDecorationProcessor.INSTANCE.processBlock(level,BlockPos.ZERO,BlockPos.ZERO,raw,placed,settings)==null,
                        "Rim decoration overwrites solid ground/building/water: "+raw.pos()+" "+block);
                h.assertValueEqual(level.getBlockState(at),block.defaultBlockState(),"Processor mutated terrain");
            }
            for(var block:List.of(Blocks.AIR,Blocks.SHORT_GRASS,Blocks.FERN)){
                set(level,at,block.defaultBlockState());
                h.assertTrue(CreekRimDecorationProcessor.INSTANCE.processBlock(level,BlockPos.ZERO,BlockPos.ZERO,raw,placed,settings)!=null,
                        "Rim decoration cannot replace air/vegetation "+block);
            }
            checked++;
        }
        h.assertValueEqual(checked,243,"Packaged rim-decoration inventory changed");
        System.out.println("CREEK_RIM_DECORATION entries="+checked+" solidGroundOverwrites=0 foreignBuildingOverwrites=0 waterOverwrites=0");
        h.succeed();
    }
    private static Map<BlockPos,Integer> tops(CreekRuinsLayoutGameTests.Site s){
        var out=new HashMap<BlockPos,Integer>();var l=s.h.getLevel();
        for(var p:Data.TERRAIN.distance.keySet())if(Data.TERRAIN.distance.get(p)<=9){
            int y=23;for(;y>=-6;y--){var at=s.p.block(p.atY(y));if(!s.piece.ownsCreekGuard(at,l.getBlockState(at))&&CreekTerrainSurface.standingCandidate(l.getBlockState(at),l,at))break;}out.put(p,y);
        }
        return out;
    }
    private static void graded(GameTestHelper h,Rotation rotation){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,rotation,20000+rotation.ordinal()*240,5000,true)){
            var l=h.getLevel();var terrain=Data.TERRAIN;prepare(s);s.place(true);approach(s);var tops=tops(s);
            int worst=0,step45=0,guards=0,boundary=0,rearBoundary=0,rearGuards=0,frontContacts=0,rearContacts=0;
            String worstPair="none";int largestRetained=0;
            for(var p:terrain.rim){
                int y=tops.get(p);var at=s.p.block(p.atY(y));double here=CreekTerrainSurface.standingTop(l.getBlockState(at),l,at);var lip=at.above();var state=l.getBlockState(lip);
                boolean guard=state.is(Blocks.MOSSY_COBBLESTONE_WALL),entry=p.getZ()<0&&p.getX()>=20&&p.getX()<=24;
                boolean edge=Direction.Plane.HORIZONTAL.stream().anyMatch(d->{var q=p.relative(d);return !terrain.land.containsKey(q)&&!terrain.rim.contains(q);});
                if(edge){boundary++;if(p.getZ()>=terrain.rearFromZ)rearBoundary++;}
                if(guard){
                    guards++;if(p.getZ()>=terrain.rearFromZ)rearGuards++;
                    h.assertTrue(edge,"Retaining lip away from outer cliff "+p);
                    h.assertTrue(state.getCollisionShape(l,lip).max(Direction.Axis.Y)>=1.5,"Cosmetic instead of real guard "+p);
                    h.assertFalse(Blocks.AZURE_BLUET.defaultBlockState().canSurvive(l,lip),"Later vegetation can replace retaining lip "+p);
                    h.assertFalse(l.getBlockState(at).is(BlockTags.MOSS_REPLACEABLE),"Moss can replace guard base "+p);
                    for(int tick=0;tick<32;tick++)l.getBlockState(at).randomTick(l,at,RandomSource.create(tick));
                    h.assertValueEqual(l.getBlockState(lip),state,"Random ticks displaced guard "+p);
                    h.assertFalse(Blocks.AZURE_BLUET.defaultBlockState().canSurvive(l,lip),"Random ticks changed base to soil "+p);
                }
                h.assertFalse(entry&&guard,"Main entry fenced off "+p);
                boolean actualCliff=false;
                for(var d:Direction.Plane.HORIZONTAL){
                    var q=p.relative(d);Integer neighbour=terrain.land.containsKey(q)?terrain.land.get(q):tops.get(q);
                    if(neighbour==null)continue;
                    var other=s.p.block(q.atY(neighbour));double there=CreekTerrainSurface.standingTop(l.getBlockState(other),l,other);
                    h.assertTrue(Double.isFinite(here)&&Double.isFinite(there),"Missing collision surface "+p+" -> "+q);
                    int drop=CreekTerrainSurface.dropHalfUnits(here,there),rise=CreekTerrainSurface.dropHalfUnits(there,here);
                    if(terrain.land.containsKey(q)){
                        if(q.getZ()>=terrain.rearFromZ)rearContacts++;else if(q.getZ()<15)frontContacts++;
                    }
                    if(terrain.distance.get(p)==4&&terrain.distance.getOrDefault(q,0)==5)step45=Math.max(step45,Math.max(drop,rise));
                    boolean outside=!terrain.land.containsKey(q)&&!terrain.rim.contains(q);
                    if(outside&&drop>2){
                        actualCliff=true;largestRetained=Math.max(largestRetained,drop);
                        h.assertTrue(guard,"Unprotected outer creek cliff "+p.atY(y)+" -> "+q.atY(neighbour)+" lip="+state);
                        for(int yy=neighbour+1;yy<=y;yy++){
                            var rock=l.getBlockState(s.p.block(p.atY(yy)));
                            h.assertTrue(rock.is(Blocks.STONE)||rock.is(Blocks.ANDESITE)||rock.is(Blocks.MOSSY_COBBLESTONE)||rock.is(Blocks.MOSS_BLOCK)||rock.is(Blocks.SMOOTH_BASALT),"Bare dirt retaining face "+p.atY(yy)+" "+rock);
                        }
                        continue;
                    }
                    if(Math.max(drop,rise)>worst){worst=Math.max(drop,rise);worstPair=p.atY(y)+" "+l.getBlockState(at)+" -> "+q.atY(neighbour)+" "+l.getBlockState(other)+" feet="+here+"->"+there;}
                }
                h.assertFalse(guard&&!actualCliff,"Guard without an actual drop "+p);
                var floor=l.getBlockState(at);
                h.assertTrue(floor.is(Blocks.GRASS_BLOCK)||(p.getZ()>=50&&(floor.is(Blocks.MOSSY_COBBLESTONE)||floor.is(Blocks.MOSS_BLOCK)||floor.is(Blocks.ANDESITE)||floor.is(Blocks.GRAVEL)||floor.is(Blocks.COARSE_DIRT)))||guard&&(floor.is(Blocks.MOSSY_COBBLESTONE)||floor.is(Blocks.MOSS_BLOCK)||floor.is(Blocks.SMOOTH_BASALT))||entry&&
                        (floor.is(Blocks.MOSSY_STONE_BRICKS)||floor.is(Blocks.MOSSY_STONE_BRICK_STAIRS)
                                ||floor.is(Blocks.DIRT)&&l.getBlockState(at.above()).is(Blocks.MOSSY_STONE_BRICK_STAIRS)),"Unexpected outer bank surface "+p.atY(y)+" "+floor);
            }
            System.out.println("CREEK_RIM rotation="+rotation+" columns="+terrain.rim.size()+" worstStep="+(worst/2.0)+" fourthToFifth="+(step45/2.0)+" frontContacts="+frontContacts+" rearContacts="+rearContacts+" worstPair="+worstPair);
            System.out.println("CREEK_RIM_GUARDS rotation="+rotation+" guards="+guards+" boundary="+boundary+" ratio="+(double)guards/boundary+" rearGuards="+rearGuards+" rearBoundary="+rearBoundary+" rearRatio="+(double)rearGuards/rearBoundary+" retainedDrop="+(largestRetained/2.0));
            h.assertTrue(worst<=2,"Deep creek bank trench: "+(worst/2.0)+" "+worstPair);h.assertTrue(step45<=2,"Fourth/fifth transition "+(step45/2.0));
            h.assertTrue(frontContacts>10&&rearContacts>10,"Scan missed variable-height banks");
            h.assertTrue(guards>0&&guards<boundary/2,"Missing cliff protection or encircling wall "+guards+"/"+boundary);
            for(var p:List.of(new BlockPos(16,0,3),new BlockPos(27,0,3),new BlockPos(18,0,22),new BlockPos(19,0,23),new BlockPos(28,0,22),new BlockPos(29,0,23)))for(int y=-5;y<terrain.land.get(p);y++){
                var at=s.p.block(p.atY(y));h.assertTrue(l.getBlockState(at).isFaceSturdy(l,at,Direction.UP),"Member foundation gap "+p.atY(y)+" "+l.getBlockState(at));
            }
            var restored=new RuinPiece(l.getStructureManager(),s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)));
            h.assertValueEqual(restored.getBoundingBox(),s.piece.getBoundingBox(),"Creek grading bounds lost on save");
            h.assertValueEqual(restored.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("CreekApproachTerrain").length,45,"Missing saved creek approach samples");
            h.assertTrue(Arrays.equals(restored.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("CreekApproachTerrain"),
                    s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("CreekApproachTerrain")),"Saved approach floor lost");
            var beforeSamples=s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("CreekNaturalTerrain");
            h.assertTrue(beforeSamples.length>0,"Missing original terrain snapshot");
            h.assertTrue(Arrays.equals(beforeSamples,restored.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("CreekNaturalTerrain")),"Original terrain lost on reload");
            // Untouched natural ground outside the full eight-block skirt.
            var shifted=new RuinPlacement(s.p.block(new BlockPos(0,0,-12)),rotation);
            var nav=new CaveAltarLayoutGameTests.Navigation(h,shifted,new BlockPos(45,44,79));
            h.assertTrue(nav.reachable(new Vec3(22.5,8,.5),s.piece.layout().entry().add(0,0,12)),"Natural ground cannot walk into entry "+rotation);
            System.out.println("CREEK_RIM_ENTRY rotation="+rotation+" naturalZ=-12 to=entry true abilities=none");settleThenSucceed(h);
        }
    }
    static Set<BlockPos> scanColumns(){var out=new HashSet<BlockPos>();Data.TERRAIN.distance.forEach((p,d)->{if(d<=9)out.add(p);});return Set.copyOf(out);}
    /** Read the real generated ground, rather than judging the interpolated plan. */
    static Map<String,Object> naturalScan(ServerLevel level,RuinPiece piece){return naturalScan(level,piece,null);}
    static Map<String,Object> naturalScan(ServerLevel level,RuinPiece piece,CreekNaturalBaseline baseline){
        var t=Data.TERRAIN;var placement=piece.placement();var tops=new HashMap<BlockPos,Integer>();var feet=new HashMap<BlockPos,Double>();
        for(var entry:t.distance.entrySet())if(entry.getValue()<=9){
            var p=entry.getKey();int y=40;
            for(;y>=-32;y--){var at=placement.block(p.atY(y));var state=level.getBlockState(at);
                if(!piece.ownsCreekGuard(at,state)&&CreekTerrainSurface.standingCandidate(state,level,at))break;}
            tops.put(p,y);
            var at=placement.block(p.atY(y));double height=CreekTerrainSurface.standingTop(level.getBlockState(at),level,at);
            feet.put(p,Double.isFinite(height)?height-placement.origin().getY():-32.0);
        }
        var untouchedCaves=new HashSet<BlockPos>();var caveColumns=new ArrayList<Map<String,Object>>();
        if(baseline!=null)for(var p:t.rim){var at=placement.block(p.atY(tops.get(p)));
            if(CreekForeignTerrainTrace.cave(level,piece,at)){untouchedCaves.add(p);
                var proof=new LinkedHashMap<String,Object>(CreekForeignTerrainTrace.caveProof(at));
                proof.put("local",List.of(p.getX(),tops.get(p),p.getZ()));caveColumns.add(proof);}}
        var caveBoundary=new ArrayList<Map<String,Object>>();int missingCaveGuards=0;
        for(var p:untouchedCaves)for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);
            if(untouchedCaves.contains(q)||(!t.rim.contains(q)&&!t.land.containsKey(q)))continue;
            int y=t.land.containsKey(q)?t.land.get(q):tops.get(q);var at=placement.block(q.atY(y));
            double higher=CreekTerrainSurface.standingTop(level.getBlockState(at),level,at)-placement.origin().getY();
            double lower=feet.get(p);if(CreekTerrainSurface.dropHalfUnits(higher,lower)<=2)continue;
            boolean protectedEdge=crossingBlocked(level,placement,q,higher);
            caveBoundary.add(Map.of("rim",List.of(q.getX(),y,q.getZ()),"cave",List.of(p.getX(),tops.get(p),p.getZ()),
                    "fromFeet",higher,"toFeet",lower,"guarded",protectedEdge,"guardState",level.getBlockState(at.above()).toString()));
            if(!protectedEdge)missingCaveGuards++;
        }
        int worst=0,step45=0,guards=0,boundary=0,rearGuards=0,rearBoundary=0,violations=0,uphill=0,treeBarriers=0,caves=0,buildings=0;
        var saved=piece.createTag(StructurePieceSerializationContext.fromLevel(level)).getIntArray("CreekNaturalTerrain");
        var examples=new ArrayList<String>();var underwater=new ArrayList<Map<String,Object>>();var foreign=new ArrayList<Map<String,Object>>();var caveInternal=new ArrayList<Map<String,Object>>();var exceptions=new ArrayList<Map<String,Object>>();int rawWorst=0,rawViolations=0;
        for(var p:t.rim){
            int y=tops.get(p);var lip=placement.block(p.atY(y+1));
            // The saved approach is authored masonry, not an untouched foreign
            // building. It is owned by its separate piece and is not a grading exception.
            boolean approach=p.getZ()>=-9&&p.getZ()<0&&Math.abs(p.getX()-22)<=2;
            if(!approach&&saved.length==85*107&&y<saved[(p.getZ()+20)*85+p.getX()+20]-6){caves++;exceptions.add(Map.of("kind","cave_or_submerged","local",List.of(p.getX(),y,p.getZ())));}
            if(!approach&&!CreekTerrainSurface.natural(level.getBlockState(lip.below()))&&!piece.ownsCreekSupport(lip.below(),level.getBlockState(lip.below()))){buildings++;exceptions.add(Map.of("kind","building","local",List.of(p.getX(),y,p.getZ()),"block",level.getBlockState(lip.below()).toString()));}
            boolean guard=level.getBlockState(lip).is(Blocks.MOSSY_COBBLESTONE_WALL);
            boolean trunk=level.getBlockState(lip).isFaceSturdy(level,lip,Direction.UP)
                    &&(level.getBlockState(lip.above()).isFaceSturdy(level,lip.above(),Direction.UP)||level.getBlockState(lip.above()).is(Blocks.MOSSY_COBBLESTONE_WALL));
            if(trunk)treeBarriers++;
            boolean edge=Direction.Plane.HORIZONTAL.stream().anyMatch(d->!t.land.containsKey(p.relative(d))&&!t.rim.contains(p.relative(d)));
            if(edge){boundary++;if(p.getZ()>=t.rearFromZ)rearBoundary++;}
            if(guard){guards++;if(p.getZ()>=t.rearFromZ)rearGuards++;}
            for(var d:Direction.Plane.HORIZONTAL){
                var q=p.relative(d);Integer other=t.land.containsKey(q)?t.land.get(q):tops.get(q);if(other==null)continue;
                double here=feet.get(p),there;
                if(t.land.containsKey(q)){
                    var at=placement.block(q.atY(other));there=CreekTerrainSurface.standingTop(level.getBlockState(at),level,at)-placement.origin().getY();
                    if(!Double.isFinite(there))throw new IllegalStateException("Recorded LAND support has no collision at "+at+": "+level.getBlockState(at));
                }else there=feet.get(q);
                int drop=CreekTerrainSurface.dropHalfUnits(here,there),rise=CreekTerrainSurface.dropHalfUnits(there,here);
                boolean outside=!t.land.containsKey(q)&&!t.rim.contains(q);
                if(t.distance.get(p)==4&&t.distance.getOrDefault(q,0)==5)step45=Math.max(step45,Math.max(drop,rise));
                if(outside&&rise>2)uphill++;
                if(drop>2&&(guard||trunk||crossingBlocked(level,placement,q,here)))continue;
                rawWorst=Math.max(rawWorst,drop);if(drop>2)rawViolations++;
                if(drop>2&&baseline!=null&&baseline.unchangedSubmergedStep(level,p,y,q,other)){
                    underwater.add(baseline.exception(p,q));continue;
                }
                if(drop>2&&baseline!=null&&!t.land.containsKey(p)&&!t.land.containsKey(q)&&!approach
                        &&CreekForeignTerrainTrace.unchanged(level,piece,placement.block(p.atY(y)),placement.block(q.atY(other)))){
                    foreign.add(CreekForeignTerrainTrace.proof(placement.block(p.atY(y)),placement.block(q.atY(other))));continue;
                }
                if(drop>2&&untouchedCaves.contains(p)&&untouchedCaves.contains(q)){
                    caveInternal.add(Map.of("from",List.of(p.getX(),y,p.getZ()),"to",List.of(q.getX(),other,q.getZ()),"fromFeet",here,"toFeet",there));continue;
                }
                worst=Math.max(worst,drop);
                if(drop>2){violations++;if(examples.size()<24)examples.add(p.atY(y)+" -> "+q.atY(other)+" feet="+here+"->"+there+" / "+level.getBlockState(lip.below())+" / "+level.getBlockState(placement.block(q.atY(other))));}
            }
        }
        var out=new LinkedHashMap<String,Object>();out.put("columns",t.rim.size());
        out.put("caveInternalExceptions",caveInternal);out.put("caveProofColumns",caveColumns);out.put("caveBoundary",caveBoundary);out.put("missingCaveGuards",missingCaveGuards);
        if(missingCaveGuards>0)violations+=missingCaveGuards;out.put("worstUnprotectedStep",worst/2.0);out.put("fourthToFifth",step45/2.0);
        out.put("rawUnprotectedSteps",rawViolations);out.put("rawWorstStep",rawWorst/2.0);out.put("underwaterNaturalExceptions",underwater);out.put("foreignStructureInternalExceptions",foreign);out.put("exceptionColumns",exceptions);
        out.put("violations",violations);out.put("examples",examples);out.put("guards",guards);out.put("boundary",boundary);out.put("rearGuards",rearGuards);out.put("rearBoundary",rearBoundary);out.put("outwardSteepUphill",uphill);out.put("treeBarriers",treeBarriers);out.put("caveExceptions",caves);out.put("buildingColumns",buildings);return out;
    }
    private static boolean crossingBlocked(ServerLevel level,RuinPlacement placement,BlockPos neighbour,double feet){
        // A preserved foreign roof can be higher than our safe ground. Its guard
        // is on our side of the boundary; test real collision at the departing
        // player's feet, not merely the block directly above the source floor.
        var foot=placement.block(neighbour.atY((int)Math.floor(feet)));
        for(var at:List.of(foot,foot.below())){
            double top=CreekTerrainSurface.standingTop(level.getBlockState(at),level,at);
            if(Double.isFinite(top)&&top>placement.origin().getY()+feet+.6)return true;
        }
        return false;
    }
    private static void sliceOrder(GameTestHelper h,Rotation rotation){
        // 288 preserves chunk alignment and all coordinate-based moss/rock salts.
        try(var a=new CreekRuinsLayoutGameTests.Site(h,rotation,23000+rotation.ordinal()*700,5000,true);
            var b=new CreekRuinsLayoutGameTests.Site(h,rotation,23288+rotation.ordinal()*700,5000,true)){
            prepare(a);prepare(b);narrowSeamDip(a);narrowSeamDip(b);
            int dips=internalDips(a);h.assertValueEqual(internalDips(b),dips,"Internal dip fixtures differ");
            int bumps=internalBumps(a);h.assertValueEqual(internalBumps(b),bumps,"Internal bump fixtures differ");
            a.place(true);placeForward(b);approach(a);approach(b);
            for(var site:List.of(a,b))for(int z:new int[]{15,16}){
                var lip=site.p.block(new BlockPos(49,8,z));
                h.assertTrue(h.getLevel().getBlockState(lip).is(Blocks.MOSSY_COBBLESTONE_WALL),"Narrow ninth-column dip lacks guard "+lip);
                h.assertTrue(h.getLevel().getBlockState(site.p.block(new BlockPos(50,4,z))).is(Blocks.GRASS_BLOCK),"Read-only ninth column was rewritten");
            }
            for(var site:List.of(a,b)){
                var scan=naturalScan(h.getLevel(),site.piece);
                System.out.println("CREEK_RIM_INTERNAL_SCAN rotation="+rotation+" scan="+scan);
                h.assertValueEqual(scan.get("violations"),0,"Internal rim step max="+scan.get("worstUnprotectedStep")+" first="+((List<?>)scan.get("examples")).stream().findFirst().orElse(null));
                h.assertTrue(lip(h,site,-4,32,17).is(Blocks.MOSSY_COBBLESTONE_WALL),"Over-six bump lacks cap guard");
                h.assertTrue(lip(h,site,-4,30,11).is(Blocks.OAK_LOG),"Tree trunk removed");
                h.assertTrue(lip(h,site,-4,30,10).is(Blocks.GRASS_BLOCK),"Tree soil cut away");
                h.assertTrue(lip(h,site,-4,34,7).is(Blocks.SAND),"Cut sand surface changed biome material");
            }
            int checked=0;var l=h.getLevel();var image=new HashMap<BlockPos,BlockState>();
            for(var p:Data.TERRAIN.rim)for(int y=natural(p)-1;y<=23;y++){
                var pa=a.p.block(p.atY(y));var pb=b.p.block(p.atY(y));
                var aa=l.getBlockState(pa);var bb=l.getBlockState(pb);
                h.assertValueEqual(aa,bb,"Slice order changes creek bank "+p.atY(y)+" a="+pa+" b="+pb);checked++;image.put(pa,aa);
            }
            for(int pass=2;pass<=3;pass++){
                placeForward(a);approach(a);
                for(var e:image.entrySet())h.assertValueEqual(l.getBlockState(e.getKey()),e.getValue(),"Owned-column write #"+pass+" changes guard/cap/clearance "+e.getKey());
            }
            System.out.println("CREEK_RIM_SLICE_ORDER rotation="+rotation+" compared="+checked+" differing=0 internalDipPatches="+dips+" internalBumpPatches="+bumps+" unprotectedSteps=0 idempotent=true consecutiveWrites=3");settleThenSucceed(h);
        }
    }
    private static BlockState lip(GameTestHelper h,CreekRuinsLayoutGameTests.Site s,int x,int z,int y){return h.getLevel().getBlockState(s.p.block(new BlockPos(x,y,z)));}
    private static void bottomSlab(GameTestHelper h,Rotation rotation){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,rotation,32000+rotation.ordinal()*240,10500,true)){
            prepare(s);var l=h.getLevel();var slab=s.p.block(new BlockPos(1,8,32));
            set(l,slab,Blocks.STONE_BRICK_SLAB.defaultBlockState());
            s.place(true);
            h.assertValueEqual(l.getBlockState(slab),Blocks.STONE_BRICK_SLAB.defaultBlockState(),"Foreign half slab overwritten");
            var high=s.p.block(new BlockPos(1,9,33));
            h.assertTrue(CreekTerrainSurface.ground(l.getBlockState(high),l,high),"Fixture high neighbour missing actual grade "+l.getBlockState(high));
            h.assertTrue(l.getBlockState(high.above()).is(Blocks.MOSSY_COBBLESTONE_WALL),
                    "Half-slab drop 10.0 -> 8.5 lacks guard; grid Y alone hides the extra half block "+rotation);
            h.succeed();
        }
    }
    private static void exceptionColumn(GameTestHelper h,Rotation rotation,boolean building){
        int dz=building?9000:8000;
        try(var a=new CreekRuinsLayoutGameTests.Site(h,rotation,23000+rotation.ordinal()*700,dz,true);
            var b=new CreekRuinsLayoutGameTests.Site(h,rotation,23288+rotation.ordinal()*700,dz,true)){
            var preserved=new HashMap<BlockPos,BlockState>();
            for(var s:List.of(a,b)){
                prepare(s);
                for(int z=25;z<=28;z++)for(int x=-4;x<=-2;x++)for(int y=-8;y<=23;y++){
                    var p=new BlockPos(x,y,z);
                    BlockState state=(y==-8?Blocks.STONE:building&&y>=3&&y<=10?((x+z)%2==0?Blocks.BIRCH_PLANKS:Blocks.COBBLESTONE):Blocks.AIR).defaultBlockState();
                    // Soil-capped masonry must already be classified foreign
                    // before any cut; it cannot become a building only afterwards.
                    if(building&&y>=11&&y<=12)state=(y==12?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState();
                    set(h.getLevel(),s.p.block(p),state);preserved.put(p,state);
                }
            }
            a.place(true);placeForward(b);approach(a);approach(b);
            for(var s:List.of(a,b)){
                for(var e:preserved.entrySet())h.assertValueEqual(h.getLevel().getBlockState(s.p.block(e.getKey())),e.getValue(),
                        (building?"Foreign structure overwritten":"Deep cave filled/sealed")+" at "+e.getKey());
                var scan=naturalScan(h.getLevel(),s.piece);
                h.assertValueEqual(scan.get("violations"),0,"Exception edge unprotected "+scan);
            }
            var image=new HashMap<BlockPos,BlockState>();
            for(var p:Data.TERRAIN.rim)for(int y=-9;y<=23;y++){
                var aa=h.getLevel().getBlockState(a.p.block(p.atY(y)));var bb=h.getLevel().getBlockState(b.p.block(p.atY(y)));
                h.assertValueEqual(aa,bb,"Exception protection depends on slice order "+p.atY(y));image.put(a.p.block(p.atY(y)),aa);
            }
            placeForward(a);approach(a);
            for(var e:image.entrySet())h.assertValueEqual(h.getLevel().getBlockState(e.getKey()),e.getValue(),"Exception rerun changes "+e.getKey());
            System.out.println("CREEK_EXCEPTION rotation="+rotation+" kind="+(building?"building":"cave")+" preserved="+preserved.size()+" differing=0");
            settleThenSucceed(h);
        }
    }
    private static int internalBumps(CreekRuinsLayoutGameTests.Site s){
        var t=Data.TERRAIN;int patches=0;
        for(int gz=-8;gz<75;gz+=8)for(int gx=-8;gx<53;gx+=8){
            int height=2+Math.floorMod(gx/8+gz/8,3);var patch=new ArrayList<BlockPos>();
            for(int z=gz+6;z<gz+8;z++)for(int x=gx+3;x<gx+5;x++)patch.add(new BlockPos(x,0,z));
            if(patch.stream().anyMatch(p->t.distance.getOrDefault(p,100)<2||t.distance.getOrDefault(p,100)>7||p.getZ()<0&&p.getX()>=20&&p.getX()<=24))continue;
            for(var p:patch){int ground=natural(p);for(int y=ground;y<ground+height;y++)set(s.h.getLevel(),s.p.block(p.atY(y)),Blocks.DIRT.defaultBlockState());
                set(s.h.getLevel(),s.p.block(p.atY(ground+height)),Blocks.GRASS_BLOCK.defaultBlockState());}
            patches++;
        }
        for(int[] spec:new int[][]{{30,10},{32,16},{34,11}}){
            var p=new BlockPos(-4,0,spec[0]);s.h.assertTrue(t.rim.contains(p),"Bump witness outside rim");
            for(int y=natural(p);y<=spec[1];y++)set(s.h.getLevel(),s.p.block(p.atY(y)),(spec[0]==34?Blocks.SAND:y==spec[1]?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
            if(spec[0]==30)for(int y=11;y<=13;y++)set(s.h.getLevel(),s.p.block(p.atY(y)),Blocks.OAK_LOG.defaultBlockState());
        }
        // A two-high exposed ore protrusion is supporting terrain, not decoration.
        for(int y=7;y<=9;y++)set(s.h.getLevel(),s.p.block(new BlockPos(-4,y,36)),Blocks.COAL_ORE.defaultBlockState());
        s.h.assertTrue(patches>=8,"Too few internal bump witnesses "+patches);return patches;
    }
    /** Every eligible 8x8 cell gets a 2–3-wide, 3–4-deep hollow between samples. */
    private static int internalDips(CreekRuinsLayoutGameTests.Site s){
        var t=Data.TERRAIN;int patches=0;
        for(int gz=-8;gz<75;gz+=8)for(int gx=-8;gx<53;gx+=8){
            int width=2+Math.floorMod(gx/8+gz/8,2),depth=3+Math.floorMod(gx/8-gz/8,2);
            var patch=new ArrayList<BlockPos>();
            for(int z=gz+3;z<gz+5;z++)for(int x=gx+3;x<gx+3+width;x++)patch.add(new BlockPos(x,0,z));
            if(patch.stream().anyMatch(p->t.distance.getOrDefault(p,100)<2||t.distance.getOrDefault(p,100)>7||p.getZ()<0&&p.getX()>=20&&p.getX()<=24))continue;
            for(var p:patch){
                int bottom=natural(p)-depth;
                set(s.h.getLevel(),s.p.block(p.atY(bottom-1)),Blocks.DIRT.defaultBlockState());
                set(s.h.getLevel(),s.p.block(p.atY(bottom)),Blocks.GRASS_BLOCK.defaultBlockState());
                for(int y=bottom+1;y<=23;y++)set(s.h.getLevel(),s.p.block(p.atY(y)),Blocks.AIR.defaultBlockState());
                set(s.h.getLevel(),s.p.block(p.atY(bottom+1)),Blocks.WATER.defaultBlockState());
                set(s.h.getLevel(),s.p.block(p.atY(bottom+2)),Blocks.SHORT_GRASS.defaultBlockState());
                set(s.h.getLevel(),s.p.block(p.atY(bottom+3)),Blocks.SNOW.defaultBlockState());
            }
            patches++;
        }
        s.h.assertTrue(patches>=8,"Too few internal coarse-cell witnesses: "+patches);
        return patches;
    }
    private static void narrowSeamDip(CreekRuinsLayoutGameTests.Site s){
        for(int z:new int[]{15,16}){
            var p=new BlockPos(50,0,z);
            s.h.assertValueEqual(Data.TERRAIN.distance.get(p),9,"Dip must be on untouched ninth column");
            // The saved coarse snapshot intentionally still says seven. Features
            // above ground must not conceal a narrow real three-block depression.
            for(int y=4;y<=23;y++)set(s.h.getLevel(),s.p.block(p.atY(y)),Blocks.AIR.defaultBlockState());
            set(s.h.getLevel(),s.p.block(p.atY(3)),Blocks.DIRT.defaultBlockState());
            set(s.h.getLevel(),s.p.block(p.atY(4)),Blocks.GRASS_BLOCK.defaultBlockState());
            set(s.h.getLevel(),s.p.block(p.atY(8)),Blocks.OAK_LOG.defaultBlockState());
            set(s.h.getLevel(),s.p.block(p.atY(9)),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
        }
    }
    private static void placeForward(CreekRuinsLayoutGameTests.Site s){
        var l=s.h.getLevel();var b=s.piece.getBoundingBox();
        var accessor=(WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,method,args)->{
            if(method.getName().equals("setBlock")&&args.length>=3)args[2]=(int)args[2]|Block.UPDATE_KNOWN_SHAPE;
            try{return method.invoke(l,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
        var active=s.piece;int written=0;
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++){
            active.postProcess(accessor,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),
                    new BoundingBox(x*16,l.getMinBuildHeight(),z*16,x*16+15,l.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
            if(++written%4==0)active=new RuinPiece(l.getStructureManager(),active.createTag(StructurePieceSerializationContext.fromLevel(l)));
        }
        // The reloaded piece is the current start, including its coordinate ledger.
        // Later scans/repeated writes must not reuse the pre-reload fixture object.
        s.piece=active;
    }
    private static void settleThenSucceed(GameTestHelper h){
        // Completion stays deferred until the enclosing try-with-resources has
        // drained generation and released its tickets. No guessed 40-tick wait.
        h.runAfterDelay(1,h::succeed);
    }
}
