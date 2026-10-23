package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import static cn.wwl.orimod.world.GreatTreeInterior.*;
import static cn.wwl.orimod.world.GreatTreeScaleInterior.*;

@GameTestHolder("orimod_gametest")
public final class GreatTreeScaleInteriorGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(
        safeFall("lookout",218),safeFall("middle_corner",120),
        new TestFunction("great_tree_wind_water","great_tree.wind_resists_water_and_placement","orimod_gametest:room",80,0,true,h->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(5,2,5));var wind=OriBlocks.GREAT_TREE_WIND.get().defaultBlockState();
            level.setBlock(at,wind,3);level.setBlock(at.above(),Blocks.WATER.defaultBlockState(),3);
            h.assertFalse(wind.canBeReplaced(),"A held block can overwrite the wind");
            h.runAfterDelay(30,()->{h.assertTrue(level.getBlockState(at).is(OriBlocks.GREAT_TREE_WIND),"Water washed away the wind");h.succeed();});
        }),
        new TestFunction("great_tree_scale_interior","great_tree.scale_routes_ritual_and_guidance","orimod_gametest:room",160,0,true,h->{
            var level=h.getLevel();var near=h.absolutePos(new BlockPos(160,0,0));var base=new BlockPos(near.getX(),70,near.getZ());
            var old=new HashMap<BlockPos,BlockState>();var pinned=new ArrayList<ChunkPos>();
            var data=GreatTreeSite.get(level);var previous=data.site();boolean begun=data.begun();
            try{
                var site=new GreatTreeSite.Site(base,BlockPos.ZERO,115115,2);var piece=new GreatTreePiece(site);
                for(int x=(base.getX()+MIN_X)>>4;x<=(base.getX()+MAX_X)>>4;x++)for(int z=(base.getZ()+MIN_Z)>>4;z<=(base.getZ()+MAX_Z)>>4;z++){
                    if(level.setChunkForced(x,z,true))pinned.add(new ChunkPos(x,z));level.getChunk(x,z);
                }
                for(int x=MIN_X;x<=MAX_X;x++)for(int z=MIN_Z;z<=MAX_Z;z++)for(int y=MIN_Y-1;y<=MAX_Y;y++){
                    var p=base.offset(x,y,z);old.put(p,level.getBlockState(p));
                }
                for(int x=(base.getX()+MIN_X)>>4;x<=(base.getX()+MAX_X)>>4;x++)for(int z=(base.getZ()+MIN_Z)>>4;z<=(base.getZ()+MAX_Z)>>4;z++){
                    var chunk=new ChunkPos(x,z);var clip=new BoundingBox(Math.max(chunk.getMinBlockX(),base.getX()+MIN_X),base.getY()+MIN_Y-1,Math.max(chunk.getMinBlockZ(),base.getZ()+MIN_Z),
                            Math.min(chunk.getMaxBlockX(),base.getX()+MAX_X),base.getY()+MAX_Y,Math.min(chunk.getMaxBlockZ(),base.getZ()+MAX_Z));
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(1),clip,chunk,base);
                }
                var lower=new ArrayList<Point>(List.of(ENTRY,new Point(0,0,-8),new Point(0,0,-3)));
                lower.addAll(GreatTreeInterior.layout().ascent());lower.add(new Point(0,24,-2));lower.add(new Point(2,24,-2));
                both(h,base,lower);
                var upper=new ArrayList<Point>(List.of(new Point(2,24,-2),new Point(-4,24,-2)));
                upper.addAll(GreatTreeScaleInterior.layout().upper());upper.add(new Point(-2,LOOKOUT_Y,7));upper.add(new Point(-2,LOOKOUT_Y,0));upper.add(LOOKOUT);
                both(h,base,upper);
                var cave=new ArrayList<Point>(List.of(new Point(0,0,-8),new Point(-4,0,-8)));
                cave.addAll(GreatTreeInterior.layout().descent());cave.add(new Point(-1,-8,2));both(h,base,cave);
                try(var connected=Fixtures.connectedPlayer(h)){
                    var p=connected.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,0);
                    p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withWisps(7));
                    data.choose(site);p.setPos(Vec3.atBottomCenterOf(base.offset(0,0,-24)));
                    h.assertTrue(GreatTreeDiscovery.guide(p),"Map stone cannot guide to rescaled entrance");
                    // A nearby target ray through a real loaded piece must still discover the landmark.
                    p.setPos(Vec3.atBottomCenterOf(base.offset(0,0,-26)));
                    h.assertTrue(GreatTreeDiscovery.visible(p)!=null,"Rescaled arch hides first-sight discovery");
                    var reward=base.offset(-1,-8,3);p.setPos(Vec3.atBottomCenterOf(reward.north()));
                    h.assertTrue(((CellPedestalBlock)OriBlocks.ENERGY_CELL_PEDESTAL.get()).claim(p,reward),"Root reward inaccessible");
                    h.assertFalse(((CellPedestalBlock)OriBlocks.ENERGY_CELL_PEDESTAL.get()).claim(p,reward),"Root reward repeated");
                    var heart=base.offset(2,25,1);p.setPos(Vec3.atBottomCenterOf(heart.offset(0,-1,-3)));
                    Restoration.reset(p.server);h.assertTrue(Restoration.start(p,heart),"Rescaled heart refused zero-ability player");
                    h.assertTrue(Restoration.finish(p),"Rescaled ritual did not finish");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),500,"Ritual reward changed");
                    h.assertFalse(Restoration.start(p,heart),"Repeated restoration");
                }
                h.succeed();
            }finally{Restoration.reset(level.getServer());data.choose(previous);data.begun(begun);old.forEach((p,s)->level.setBlock(p,s,2));for(var c:pinned)level.setChunkForced(c.x,c.z,false);}
        }),
        new TestFunction("great_tree_wind","great_tree.wind_is_collisionless_and_continuous","orimod_gametest:room",80,0,true,h->{
            var level=h.getLevel();var wind=OriBlocks.GREAT_TREE_WIND.get().defaultBlockState();var at=h.absolutePos(new BlockPos(5,2,5));
            var old=new HashMap<BlockPos,BlockState>();
            try(var c=Fixtures.connectedPlayer(h)){
                var p=c.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,0);
                // The standard room has a ceiling; clear and restore it so this checks the wind range.
                for(int dy=0;dy<=15;dy++){var q=at.above(dy);old.put(q,level.getBlockState(q));level.setBlock(q,Blocks.AIR.defaultBlockState(),2);}
                level.setBlock(at,wind,2);p.setPos(Vec3.atBottomCenterOf(at));
                h.assertTrue(wind.getCollisionShape(level,at).isEmpty()&&wind.getShape(level,at).isEmpty(),"Wind has a platform/selection box");
                h.assertTrue(UpdraftBlock.lifting(level,p),"Wind not visible to shared prediction/server lift query");
                for(int dy:new int[]{1,7,12}){p.setPos(Vec3.atBottomCenterOf(at.above(dy)));h.assertTrue(UpdraftBlock.lifting(level,p),"Wind lift gap "+dy);}
                p.setPos(Vec3.atBottomCenterOf(at.above(13)));h.assertFalse(UpdraftBlock.lifting(level,p),"Wind overshoots bounded query");
                level.setBlock(at.above(),Blocks.STONE.defaultBlockState(),2);p.setPos(Vec3.atBottomCenterOf(at.above(2)));h.assertFalse(UpdraftBlock.lifting(level,p),"Wind travels through a solid cap");
                h.assertFalse(Ability.GLIDE.unlocked(p),"Fixture unexpectedly learned Glide");
                h.succeed();
            }finally{old.forEach((p,s)->level.setBlock(p,s,2));}
        })
    );}
    private static TestFunction safeFall(String name,int height){
        return new TestFunction("great_tree_fall","great_tree.wind_fall_"+name+"_is_fully_safe","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var near=h.absolutePos(new BlockPos(176,0,0));var base=new BlockPos(near.getX(),70,near.getZ());
            var saved=new HashMap<BlockPos,BlockState>();var pins=new ArrayList<ChunkPos>();
            try(var connected=Fixtures.connectedPlayer(h)){
                for(int x=(base.getX()-4)>>4;x<=(base.getX()+6)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+6)>>4;z++){
                    if(level.setChunkForced(x,z,true))pins.add(new ChunkPos(x,z));level.getChunk(x,z);
                }
                for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int y=23;y<=221;y++){
                    var q=base.offset(x,y,z);saved.put(q,level.getBlockState(q));
                    level.setBlock(q,y==23?Blocks.STONE.defaultBlockState():y==24?OriBlocks.GREAT_TREE_LANDING.get().defaultBlockState():OriBlocks.GREAT_TREE_WIND.get().defaultBlockState(),2);
                }
                var p=connected.player();p.setData(SpiritData.FORM,false);p.setData(SpiritData.ABILITIES,0);p.setHealth(20);
                // Expire the real player's initial 60-tick login invulnerability; do not bypass it
                // with a special damage source. The stone negative control below must be lethal.
                for(int i=0;i<61;i++)p.tick();
                p.setPos(Vec3.atBottomCenterOf(base.offset(1,height,1)));p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);
                double last=p.getY();int ticks=0;float maxFall=0;
                // ServerPlayer receives movement from its client normally. Drive vanilla travel here;
                // gravity, collision, fall-distance accumulation and the block's fallOn are real.
                while(!p.onGround()&&ticks++<160){
                    p.travel(Vec3.ZERO);p.doCheckFallDamage(0,p.getY()-last,0,p.onGround());maxFall=Math.max(maxFall,p.fallDistance);
                    h.assertTrue(p.getY()<=last+.00001,"Wind fall bounced or jittered upward");last=p.getY();
                    h.assertTrue(p.isAlive()&&p.getHealth()==20,"Fall damaged/killed the human player");
                }
                h.assertTrue(p.onGround(),"Player never reached the root cushion");
                h.assertTrue(maxFall>height-30,"Fixture did not execute the full fall");
                Fixtures.near(h,p.getY(),base.getY()+24+.25,"Cushion landing height");
                for(int i=0;i<8;i++){p.travel(Vec3.ZERO);h.assertTrue(p.onGround()&&p.getHealth()==20,"Landing bounces or causes delayed damage");}
                h.assertTrue(UpdraftBlock.lifting(level,p),"Cushion blocks takeoff lift");
                cn.wwl.orimod.OriMod.LOGGER.info("GREAT_TREE_FALL {}: height={}, travelTicks={}, maxFall={}, health={}",name,height,ticks,maxFall,p.getHealth());
                // Negative control: the identical human + gravity path must hurt on ordinary stone.
                level.setBlock(base.offset(1,24,1),Blocks.STONE.defaultBlockState(),2);
                p.setPos(Vec3.atBottomCenterOf(base.offset(1,height,1)));p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
                last=p.getY();ticks=0;
                while(!p.onGround()&&ticks++<160){p.travel(Vec3.ZERO);p.doCheckFallDamage(0,p.getY()-last,0,p.onGround());last=p.getY();}
                h.assertTrue(p.getHealth()<20,"Negative control: fixture accidentally ignores ordinary fall damage");
                cn.wwl.orimod.OriMod.LOGGER.info("GREAT_TREE_FALL_CONTROL {}: stone health={}, alive={}",name,p.getHealth(),p.isAlive());
                h.succeed();
            }finally{saved.forEach((p,s)->level.setBlock(p,s,2));for(var c:pins)level.setChunkForced(c.x,c.z,false);}
        });
    }
    private static void both(GameTestHelper h,BlockPos base,ArrayList<Point> route){GreatTreeInteriorGameTests.walk(h,base,route);Collections.reverse(route);GreatTreeInteriorGameTests.walk(h,base,route);}
}
