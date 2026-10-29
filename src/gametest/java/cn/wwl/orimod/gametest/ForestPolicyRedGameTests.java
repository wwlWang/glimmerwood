package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Gate-two behavioral regressions, now run against ForestTerrainVersion=1. */
@GameTestHolder("orimod_forest_red")
public final class ForestPolicyRedGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        tests.add(test("soil_slope_route_clearance",ForestPolicyRedGameTests::soil));
        tests.add(test("soil_slope_permanent_light",ForestSpringLayoutGameTests::slopeLighting));
        for(String type:List.of("log","block_entity","unbreakable"))tests.add(test("protected_"+type,h->protectedCell(h,type)));
        for(var r:Rotation.values())tests.add(test("late_columns_"+r.name().toLowerCase(),h->lateColumns(h,r)));
        for(String type:List.of("bottom_slab","stairs","snow"))tests.add(test("standing_"+type,h->standing(h,type)));
        for(String type:List.of("pit","bump"))for(var r:Rotation.values())tests.add(test("rim_"+type+"_"+r.name().toLowerCase(),h->rim(h,type,r)));
        return tests;
    }
    private static TestFunction test(String name,Consumer<GameTestHelper> body){return new TestFunction("forest_policy_red","forest_policy_red."+name,"orimod_forest_red:room",Rotation.NONE,900,0,true,body);}
    static void set(ServerLevel l,BlockPos p,BlockState s){l.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);}
    static void slope(ForestSpringLayoutGameTests.Site s){
        for(int x=0;x<=20;x++)for(int z=10;z<=28;z++)if(x<=10||z>=18&&z<=25)
            for(int y=5;y<=11;y++)set(s.h.getLevel(),s.p.block(new BlockPos(x,y,z)),(y==11?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState());
    }
    private static void soil(GameTestHelper h){
        try(var s=new ForestSpringLayoutGameTests.Site(h,Rotation.NONE,16000,9000,false,1)){
            slope(s);s.place();var nav=new CaveAltarLayoutGameTests.Navigation(h,s.p,s.piece.layout().size());
            for(var target:List.of(new Vec3(7.5,6,14.5),new Vec3(31.5,6,14.5),new Vec3(19.5,6,42.5),new Vec3(19.5,8,18.5)))h.assertTrue(nav.reachable(s.piece.layout().entry(),target),"+6 soil slope obstructed route "+target);
            var b=s.piece.layout().bridges().getFirst();for(var p:b.platforms())set(h.getLevel(),s.p.block(p),cn.wwl.orimod.block.OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState().setValue(cn.wwl.orimod.block.SpiritPlatformBlock.LIT,true));
            h.assertTrue(nav.reachable(Vec3.atBottomCenterOf(b.entry()),Vec3.atBottomCenterOf(b.exit())),"+6 soil slope blocks lit bridge");
            System.out.println("FOREST_RED_SOIL authored_air=true slope=+6 route=true bridge=true");h.succeed();
        }
    }
    private static void protectedCell(GameTestHelper h,String type){
        int i=List.of("log","block_entity","unbreakable").indexOf(type);
        try(var s=new ForestSpringLayoutGameTests.Site(h,Rotation.NONE,16400+i*160,9000,false,1)){
            var l=h.getLevel();var p=s.p.block(new BlockPos(19,7,6));var expected=switch(type){case "log"->Blocks.OAK_LOG;case "block_entity"->Blocks.CHEST;default->Blocks.BEDROCK;};
            if(type.equals("log"))for(int y=5;y<=14;y++)set(l,p.atY(s.p.origin().getY()+y),expected.defaultBlockState());
            set(l,p,expected.defaultBlockState());CompoundTag before=l.getBlockEntity(p)==null?null:l.getBlockEntity(p).saveWithFullMetadata(l.registryAccess());
            s.place();System.out.println("FOREST_RED_PROTECTION type="+type+" world="+p+" before="+expected+" after="+l.getBlockState(p)+" beBefore="+before+" beAfter="+l.getBlockEntity(p));
            if(type.equals("log")){h.assertTrue(l.isEmptyBlock(p),"Explicit template AIR must clear internal trunk");h.succeed();return;}
            h.assertTrue(l.getBlockState(p).is(expected),"Template overwrote protected "+type+" at "+p+" with "+l.getBlockState(p));
            if(before!=null)h.assertValueEqual(l.getBlockEntity(p).saveWithFullMetadata(l.registryAccess()),before,"Protected BE changed");h.succeed();
        }
    }
    private static final List<BlockPos> LATE=List.of(new BlockPos(10,0,51),new BlockPos(13,0,3),new BlockPos(25,0,3),new BlockPos(28,0,51),new BlockPos(29,0,50),new BlockPos(30,0,49),new BlockPos(36,0,22),new BlockPos(37,0,21));
    private static void lateColumns(GameTestHelper h,Rotation r){
        try(var s=new ForestSpringLayoutGameTests.Site(h,r,17200+r.ordinal()*160,9000,false,1)){
            var l=h.getLevel();for(var q:LATE)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=6;y<=11;y++)set(l,s.p.block(q.offset(dx,y,dz)),Blocks.DIRT.defaultBlockState());
            s.place();var buried=new ArrayList<String>();
            for(var q:LATE){var left=new ArrayList<Integer>();for(int y=6;y<=11;y++)if(l.getBlockState(s.p.block(q.atY(y))).is(Blocks.DIRT))left.add(y);if(!left.isEmpty())buried.add(q+" y="+left);}
            System.out.println("FOREST_RED_LATE rotation="+r+" untouchedSoil="+buried);
            h.assertTrue(buried.isEmpty(),"Late foundation columns retain +6 hillside: "+buried);h.succeed();
        }
    }
    private static Object terrain(ForestSpringLayoutGameTests.Site s){try{var f=RuinPiece.class.getDeclaredField("gradedForest");f.setAccessible(true);return f.get(s.piece);}catch(Exception e){throw new IllegalStateException(e);}}
    private static double actualHeight(Object t,ServerLevel l,BlockPos p){try{
        var m=t.getClass().getSuperclass().getDeclaredMethod("surface",WorldGenLevel.class,BlockPos.class,int.class,boolean.class);m.setAccessible(true);
        var surface=m.invoke(t,l,p,p.getY()-24,true);var value=surface.getClass().getDeclaredMethod("standingTop");value.setAccessible(true);return (double)value.invoke(surface);
    }catch(Exception e){throw new IllegalStateException(e);}}
    private static void standing(GameTestHelper h,String type){
        int i=List.of("bottom_slab","stairs","snow").indexOf(type);
        try(var s=new ForestSpringLayoutGameTests.Site(h,Rotation.NONE,18000+i*160,9000,false,1)){
            var l=h.getLevel();var p=s.p.block(new BlockPos(-2,8,20));for(int y=-11;y<=9;y++)set(l,s.p.block(new BlockPos(-2,y,20)),(y<=5?Blocks.STONE:Blocks.AIR).defaultBlockState());
            BlockState state=switch(type){case "bottom_slab"->Blocks.STONE_BRICK_SLAB.defaultBlockState();case "stairs"->Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH);default->Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,5);};
            if(type.equals("snow")){p=p.below(2);set(l,p.below(),Blocks.STONE.defaultBlockState());}
            // Every witness has continuous stone support, not a floating slab/stair.
            for(int y=s.p.origin().getY()+5;y<p.getY();y++)set(l,p.atY(y),Blocks.STONE.defaultBlockState());
            set(l,p,state);double expected=CreekTerrainSurface.standingTop(state,l,p),old=actualHeight(terrain(s),l,p);
            var adjacent=p.east();int neighbourGround=(int)old-2;
            for(int y=s.p.origin().getY()-10;y<=s.p.origin().getY()+10;y++)set(l,adjacent.atY(y),(y<=neighbourGround?Blocks.STONE:Blocks.AIR).defaultBlockState());
            double neighbour=CreekTerrainSurface.standingTop(l.getBlockState(adjacent.atY(neighbourGround)),l,adjacent.atY(neighbourGround));
            System.out.println("FOREST_RED_DROP type="+type+" supported=true oldDropHalf="+CreekTerrainSurface.dropHalfUnits(old,neighbour)+" actualDropHalf="+CreekTerrainSurface.dropHalfUnits(expected,neighbour));
            System.out.println("FOREST_RED_STANDING type="+type+" state="+state+" oldTop="+old+" collisionTop="+expected+" error="+(old-expected));
            h.assertTrue(Math.abs(old-expected)<1e-7,"Forest standing scanner differs from collision top: "+type+" old="+old+" expected="+expected);h.succeed();
        }
    }
    @SuppressWarnings("unchecked") private static void rim(GameTestHelper h,String type,Rotation r){
        try(var s=new ForestSpringLayoutGameTests.Site(h,r,19000+r.ordinal()*160+(type.equals("bump")?800:0),9000,true,1)){
            var l=h.getLevel();Object t=terrain(s);Map<BlockPos,Integer> ring;
            ring=new HashMap<>();var land=new HashSet<BlockPos>();
            var template=l.getStructureManager().getOrCreate(RuinKind.FOREST.template(1));
            for(var cell:template.filterBlocks(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.DIRT))if(cell.pos().getY()==0)land.add(cell.pos());
            for(var q:land)for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++){int d=Math.abs(dx)+Math.abs(dz);var at=q.offset(dx,0,dz);
                if(d>0&&d<=8&&!land.contains(at))ring.merge(s.p.block(at).atY(0),d,Math::min);}
            var witness=s.p.block(new BlockPos(3,0,30)).atY(0);
            // Locate a genuine mid-rim cell, avoiding authored decorations and entrance.
            witness=ring.entrySet().stream().filter(e->e.getValue()==4).map(Map.Entry::getKey).min(Comparator.comparingDouble(p->p.distSqr(witnessPlaceholder(s)))).orElseThrow();
            var b=s.piece.getBoundingBox().inflatedBy(13);
            for(int x=b.minX();x<=b.maxX();x++)for(int z=b.minZ();z<=b.maxZ();z++)for(int y=s.p.origin().getY()-6;y<=s.p.origin().getY()+15;y++)set(l,new BlockPos(x,y,z),(y<s.p.origin().getY()+1?Blocks.DIRT:y==s.p.origin().getY()+1?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState());
            int ground=s.p.origin().getY()+1,changed=type.equals("pit")?ground-4:ground+4;
            for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)for(int y=ground-4;y<=ground+4;y++)set(l,witness.offset(dx,y,dz),(y<changed?Blocks.DIRT:y==changed?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState());
            s.place();var bad=new ArrayList<String>();int pairs=0;
            for(var p:ring.keySet())for(var d:List.of(Direction.EAST,Direction.SOUTH)){var q=p.relative(d);if(!ring.containsKey(q))continue;double a=top(l,p,s.p.origin().getY()+15,s.p.origin().getY()-8),c=top(l,q,s.p.origin().getY()+15,s.p.origin().getY()-8);if(!Double.isFinite(a)||!Double.isFinite(c))continue;pairs++;
                if(CreekTerrainSurface.dropHalfUnits(Math.max(a,c),Math.min(a,c))>2&&!guard(l,p,a)&&!guard(l,q,c))bad.add(p+"="+a+" -> "+q+"="+c);
            }
            System.out.println("FOREST_RED_RIM type="+type+" rotation="+r+" origin="+s.p.origin()+" witness="+witness+" pairs="+pairs+" bad="+bad.size()+" examples="+bad.stream().limit(10).toList());
            h.assertTrue(bad.isEmpty(),"Rim unguarded collision-top drops: "+bad.stream().limit(8).toList());h.succeed();
        }
    }
    private static BlockPos witnessPlaceholder(ForestSpringLayoutGameTests.Site s){return s.p.block(new BlockPos(-3,0,30)).atY(0);}
    private static double top(ServerLevel l,BlockPos p,int max,int min){for(int y=max;y>=min;y--){var at=p.atY(y);var state=l.getBlockState(at);if(CreekTerrainSurface.standingCandidate(state,l,at))return CreekTerrainSurface.standingTop(state,l,at);}return Double.NaN;}
    private static boolean guard(ServerLevel l,BlockPos p,double top){for(int y=(int)Math.floor(top)-2;y<=(int)Math.ceil(top);y++)if(l.getBlockState(p.atY(y)).getBlock() instanceof WallBlock)return true;return false;}
}
