package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Actual late vanilla feature placement, and registry-wide fill classification. */
@GameTestHolder("orimod_creek_rebuild")
public final class CreekNaturalFeaturesGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        return List.of(test("soft_fillable_protected_never_replaced",CreekNaturalFeaturesGameTests::ignored),
                test("late_forest_rock_inside",h->rock(h,0)),test("forest_rock_outside",h->rock(h,1)),
                test("forest_rock_legacy",h->rock(h,2)),test("forest_rock_straddles_exact_boundary",h->rock(h,3)),
                test("other_blob_feature_unchanged",h->rock(h,4)),test("mushroom_fill_column",CreekNaturalFeaturesGameTests::mushroom),
                test("fill_two_above_roots_preserves_trunk",CreekNaturalFeaturesGameTests::buriedTree),
                test("standing_height_all_collision_states",CreekNaturalFeaturesGameTests::standingHeights),
                test("water_exception_requires_unchanged_baseline",CreekNaturalFeaturesGameTests::waterBaseline));
    }
    private static TestFunction test(String name,java.util.function.Consumer<GameTestHelper> body){
        return new TestFunction("creek_natural","creek_natural."+name,"orimod_creek_rebuild:room",Rotation.NONE,700,0,true,body);
    }
    private static Object terrain(ServerLevel l) throws ReflectiveOperationException {
        var c=Class.forName("cn.wwl.orimod.world.CreekRuinTerrain").getDeclaredConstructor(StructureTemplate.class,BlockPos.class,StructurePlaceSettings.class,int[].class);c.setAccessible(true);
        return c.newInstance(l.getStructureManager().getOrCreate(RuinKind.CREEK.template(1)),BlockPos.ZERO,new StructurePlaceSettings(),new int[0]);
    }
    private static boolean fillable(Object terrain,BlockState state,ServerLevel l,BlockPos p) throws ReflectiveOperationException {
        var m=terrain.getClass().getSuperclass().getDeclaredMethod("gradeable",BlockState.class,WorldGenLevel.class,BlockPos.class);m.setAccessible(true);
        return (boolean)m.invoke(terrain,state,l,p);
    }
    private static void ignored(GameTestHelper h){
        try{
            var l=h.getLevel();var at=h.absolutePos(new BlockPos(1,3,1));var terrain=terrain(l);
            var rejected=new ArrayList<String>();int checked=0,protectedCount=0;
            for(var block:BuiltInRegistries.BLOCK)for(var state:block.getStateDefinition().getPossibleStates()){
                boolean ignore=state.is(CreekTerrainSurface.IGNORE)||!CreekTerrainSurface.natural(state)&&!CreekTerrainSurface.building(state,l,at,false);
                if(!ignore)continue;
                boolean mutable=CreekTerrainSurface.mutable(state,l,at),allowed=fillable(terrain,state,l,at);
                if(!mutable||state.is(CreekTerrainSurface.PROTECTED)){
                    protectedCount++;h.assertFalse(allowed,"Protected block fill/cut allowed: "+state);
                    var guard=terrain.getClass().getSuperclass().getDeclaredMethod("guardSpace",BlockState.class,WorldGenLevel.class,BlockPos.class);guard.setAccessible(true);
                    h.assertFalse((boolean)guard.invoke(null,state,l,at),"Protected block replaced by guard: "+state);
                    continue;
                }
                checked++;if(!allowed)rejected.add(state.toString());
            }
            System.out.println("CREEK_IGNORE_FILL states="+checked+" protected="+protectedCount+" rejected="+rejected.size()+" examples="+rejected.stream().limit(30).toList());
            h.assertTrue(rejected.isEmpty(),"IGNORE/fill classification mismatch: "+rejected.size()+" states; "+rejected.stream().limit(8).toList());
            h.succeed();
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void mushroom(GameTestHelper h){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,Rotation.NONE,28000,10500,true)){
            // Saved natural y7, P>7 near the front bank; use the real grade.
            var l=h.getLevel();var at=s.p.block(new BlockPos(1,7,32));
            for(int x=-3;x<=3;x++)for(int z=29;z<=35;z++){
                set(l,s.p.block(new BlockPos(x,7,z)),Blocks.GRASS_BLOCK.defaultBlockState());
                for(int y=8;y<24;y++)set(l,s.p.block(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState());
            }
            set(l,at.above(),Blocks.BROWN_MUSHROOM.defaultBlockState());
            s.place(true);
            h.assertTrue(CreekTerrainSurface.natural(l.getBlockState(at.above())),"Mushroom interrupted actual fill at "+at.above()+": "+l.getBlockState(at.above()));
            h.succeed();
        }
    }
    private static void buriedTree(GameTestHelper h){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,Rotation.NONE,30500,10500,true)){
            var l=h.getLevel();
            for(int x=-3;x<=3;x++)for(int z=29;z<=37;z++){
                set(l,s.p.block(new BlockPos(x,7,z)),Blocks.GRASS_BLOCK.defaultBlockState());
                for(int y=8;y<24;y++)set(l,s.p.block(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState());
            }
            for(int y=8;y<=12;y++)set(l,s.p.block(new BlockPos(1,y,33)),Blocks.OAK_LOG.defaultBlockState());
            s.place(true);
            for(int y=8;y<=12;y++)h.assertTrue(l.getBlockState(s.p.block(new BlockPos(1,y,33))).is(Blocks.OAK_LOG),"Buried trunk cut at "+y);
            h.assertTrue(l.getBlockState(s.p.block(new BlockPos(1,7,33))).is(Blocks.GRASS_BLOCK),"Tree root soil removed");
            var beside=s.p.block(new BlockPos(1,9,34));
            h.assertTrue(CreekTerrainSurface.ground(l.getBlockState(beside),l,beside),"Neighbour did not fill two above root soil: "+l.getBlockState(beside));
            System.out.println("CREEK_BURIED_TREE natural=7 adjacentGrade=9 trunk8..12=unchanged rootSoil=unchanged");
            h.succeed();
        }
    }
    private static void waterBaseline(GameTestHelper h){
        var before=new BlockState[73];Arrays.fill(before,Blocks.AIR.defaultBlockState());
        for(int y=-32;y<=7;y++)before[y+32]=Blocks.STONE.defaultBlockState();
        for(int y=8;y<=10;y++)before[y+32]=Blocks.WATER.defaultBlockState();
        var after=before.clone();
        h.assertTrue(CreekNaturalBaseline.unchangedWet(before,7,7,true,y->after[y+32]),"Unchanged natural submerged floor rejected");
        h.assertFalse(CreekNaturalBaseline.unchangedWet(before,7,7,false,y->after[y+32]),"Unknown pre-feature history waived");
        h.assertFalse(CreekNaturalBaseline.unchangedWet(before,7,8,true,y->after[y+32]),"Changed floor height waived");
        after[8+32]=Blocks.DIRT.defaultBlockState();
        h.assertFalse(CreekNaturalBaseline.unchangedWet(before,7,7,true,y->after[y+32]),"Filled water waived");
        after[8+32]=Blocks.WATER.defaultBlockState();after[7+32]=Blocks.GRAVEL.defaultBlockState();
        h.assertFalse(CreekNaturalBaseline.unchangedWet(before,7,7,true,y->after[y+32]),"Changed riverbed material waived");
        h.succeed();
    }
    private static void standingHeights(GameTestHelper h){
        try{
            var l=h.getLevel();var at=h.absolutePos(new BlockPos(1,3,1));var terrain=terrain(l);
            var scan=terrain.getClass().getSuperclass().getDeclaredMethod("surface",WorldGenLevel.class,BlockPos.class,int.class,boolean.class);scan.setAccessible(true);
            BlockState[] current={Blocks.AIR.defaultBlockState()};
            var view=(WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,m,args)->{
                if(m.getName().equals("getBlockState"))return at.equals(args[0])?current[0]:Blocks.AIR.defaultBlockState();
                if(m.getName().equals("getMinBuildHeight"))return at.getY();
                if(m.getName().equals("getHeight"))return at.getY()+1;
                return m.invoke(l,args);
            });
            int checked=0,scanned=0;var families=new TreeMap<String,Integer>();
            for(var block:BuiltInRegistries.BLOCK)for(var state:block.getStateDefinition().getPossibleStates()){
                current[0]=state;var shape=state.getCollisionShape(view,at);if(shape.isEmpty())continue;
                checked++;double expected=at.getY()+shape.max(Direction.Axis.Y);
                h.assertValueEqual(CreekTerrainSurface.standingTop(state,view,at),expected,"Physical collision height differs "+state);
                String id=BuiltInRegistries.BLOCK.getKey(block).toString();
                if(id.matches(".*(slab|stairs|snow|carpet|fence|wall|door)$"))families.merge(id,1,Integer::sum);
                // Ignore/protected filtering is orthogonal to physical height.
                // Every actually selected scanner surface must pass exactly that
                // height to the guard calculation, never its containing block Y.
                var found=scan.invoke(terrain,view,at,at.getY(),true);
                if(!CreekTerrainSurface.standingCandidate(state,view,at)){
                    h.assertTrue(found==null,"Ignored decoration entered standing scan "+state);continue;
                }
                h.assertTrue(found!=null,"Collidable building/terrain omitted "+state);scanned++;
                var height=found.getClass().getDeclaredMethod("standingTop");height.setAccessible(true);
                h.assertValueEqual((double)height.invoke(found),expected,"Guard input differs from scanner/collision "+state);
                for(double drop:new double[]{.125,.5,1,1.125,1.5,2})
                    h.assertValueEqual(CreekTerrainSurface.dropHalfUnits(expected,expected-drop),(int)Math.ceil(drop*2),"Drop rounding differs "+state);
            }
            System.out.println("CREEK_STANDING_HEIGHT collisionStates="+checked+" scannerGuardStates="+scanned+" families="+families+" mismatches=0");
            h.assertTrue(checked>10000&&scanned>1000,"Incomplete state enumeration "+checked+"/"+scanned);h.succeed();
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void rock(GameTestHelper h,int variant){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,Rotation.NONE,29000+variant*180,10500)){
            var l=h.getLevel();
            if(variant==2)s.piece=new RuinPiece(l.getStructureManager(),RuinKind.CREEK,s.p,0,new int[0],new int[0]);
            s.publish();
            // A real neighbour FEATURES pass occurs after the source rim slice.
            // Use the saved piece bounds, including its graded skirt, not template LAND.
            var b=s.piece.getBoundingBox();var center=new BlockPos(b.minX()+(variant==1?-12:variant==3?0:4),s.p.origin().getY()+10,b.minZ()+20);
            for(int x=-5;x<=4;x++)for(int z=-5;z<=4;z++){
                set(l,center.offset(x,-1,z),Blocks.DIRT.defaultBlockState());
                for(int y=0;y<=4;y++)set(l,center.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            // Earlier slice's final soil is present before neighbour decoration.
            int before=count(l,center);var region=new ReadyRegion(l,new ChunkPos(center));
            Feature<BlockStateConfiguration> feature=variant==4?new BlockBlobFeature(BlockStateConfiguration.CODEC):Feature.FOREST_ROCK;
            boolean placed=feature.place(new FeaturePlaceContext<>(Optional.empty(),region,l.getChunkSource().getGenerator(),RandomSource.create(73),center.above(3),new BlockStateConfiguration(Blocks.MOSSY_COBBLESTONE.defaultBlockState())));
            int after=count(l,center);
            System.out.println("CREEK_LATE_ROCK variant="+variant+" pieceVersion="+s.piece.layoutVersion()+" center="+center+" before="+before+" after="+after+" result="+placed);
            if(variant==0)h.assertValueEqual(after,before,"Late neighbouring forest_rock changed registered creek v1 rim");
            else h.assertTrue(after>before,"Outside/legacy/other forest_rock was blocked");
            if(variant==3)for(var q:BlockPos.betweenClosed(center.offset(-5,-3,-5),center.offset(4,4,4)))
                if(q.getX()>=b.minX()&&q.getX()<=b.maxX()&&q.getZ()>=b.minZ()&&q.getZ()<=b.maxZ())
                    h.assertFalse(l.getBlockState(q).is(Blocks.MOSSY_COBBLESTONE),"Straddling feature wrote inside protected boundary "+q);
            h.succeed();
        }
    }
    private static int count(ServerLevel l,BlockPos p){int n=0;for(var q:BlockPos.betweenClosed(p.offset(-5,-3,-5),p.offset(4,4,4)))if(l.getBlockState(q).is(Blocks.MOSSY_COBBLESTONE))n++;return n;}
    private static void set(ServerLevel l,BlockPos p,BlockState s){l.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
    /** Only preloaded leased chunks, no synthetic tickets/futures or generation. */
    static final class ReadyRegion extends WorldGenRegion {
        private final ServerLevel level;private final ChunkPos center;
        ReadyRegion(ServerLevel l,ChunkPos p){super(l,null,ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES),l.getChunk(p.x,p.z));level=l;center=p;}
        @Override public boolean hasChunk(int x,int z){return center.getChessboardDistance(x,z)<=8&&level.getChunkSource().getChunkNow(x,z)!=null;}
        @Override public ChunkAccess getChunk(int x,int z,ChunkStatus status,boolean required){
            var chunk=hasChunk(x,z)?level.getChunkSource().getChunkNow(x,z):null;
            if(chunk==null&&required)throw new IllegalStateException("Unleased fixture chunk "+x+","+z);
            return chunk;
        }
    }
}
