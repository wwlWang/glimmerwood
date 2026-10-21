package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.world.OriEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;
import static cn.wwl.orimod.block.SpiritPlatformBlock.*;

@GameTestHolder("orimod_gametest")
public final class SpiritPlatformGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        timed(tests,"lantern_six_not_seven_and_hundred_ticks_after_extinction",(h,c)->{
            var at=site(h);var near=at.east(6);var far=at.east(7);platform(h,near);platform(h,far);
            h.getLevel().setBlock(at,OriBlocks.BASH_ANCHOR.get().defaultBlockState(),3);SpiritLanternBlock.ignite(h.getLevel(),at,20);
            h.assertTrue(lit(h,near),"Six-block boundary did not light");h.assertFalse(lit(h,far),"Seven-block platform lit");
            later(h,c,21,()->h.assertFalse(h.getLevel().getBlockState(at).getValue(SpiritLanternBlock.LIT),"Lamp failed to expire"));
            later(h,c,119,()->h.assertTrue(lit(h,near),"Platform lost hundred-tick grace"));
            later(h,c,121,()->{h.assertFalse(lit(h,near),"Platform did not expire after lamp");h.assertTrue(PlatformTimers.get(h.getLevel()).get(near.asLong())==null,"Expired timer retained");finish(h,c);});
        });
        timed(tests,"renewal_cancels_warning_without_changing_light",(h,c)->{
            var at=site(h);platform(h,at);burst(h,c,at);boolean[] first={false};
            later(h,c,71,()->{var s=h.getLevel().getBlockState(at);first[0]=s.getValue(BLINK);h.assertTrue(s.getValue(FADING),"No last-30 warning");h.assertTrue(s.getLightEmission(h.getLevel(),at)==12,"Warning changed block light");});
            later(h,c,76,()->{
                var s=h.getLevel().getBlockState(at);h.assertTrue(s.getValue(BLINK)!=first[0],"Blink field");
                h.assertTrue(s.getLightEmission(h.getLevel(),at)==12,"Blink changed light");burst(h,c,at);
                h.assertFalse(h.getLevel().getBlockState(at).getValue(FADING),"Renewal kept warning");
            });
            later(h,c,105,()->{h.assertTrue(lit(h,at),"Old expiry won over renewal");h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);finish(h,c);});
        });
        for(boolean heartFirst:List.of(true,false)) timed(tests,"tree_ten_not_eleven_constant_"+heartFirst,(h,c)->{
            var at=site(h);var near=at.east(10);var far=at.east(11);
            if(heartFirst)h.getLevel().setBlock(at,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),3);
            platform(h,near);platform(h,far);
            if(!heartFirst)h.getLevel().setBlock(at,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),3);
            h.assertTrue(lit(h,near),"Tree placement order failed");h.assertFalse(lit(h,far),"Tree range leaked");
            later(h,c,210,()->{h.assertTrue(lit(h,near),"Constant tree expired");h.assertFalse(h.getLevel().getBlockState(near).getValue(FADING),"Constant tree warns");h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);});
            later(h,c,309,()->h.assertTrue(lit(h,near),"Tree removal grace shortened"));
            later(h,c,311,()->{h.assertFalse(lit(h,near),"Tree removal timer never expired");finish(h,c);});
        });
        add(tests,"light_burst_five_boundary_not_six",(h,c)->{
            var at=site(h);platform(h,at.east(5));platform(h,at.east(6));burst(h,c,at);
            h.assertTrue(lit(h,at.east(5)),"Burst boundary missing");h.assertFalse(lit(h,at.east(6)),"Burst radius leaked");
        });
        add(tests,"flash_real_tick_lights_successive_platforms",(h,c)->{
            var at=site(h);platform(h,at);platform(h,at.east(10));c.player().setPos(Vec3.atCenterOf(at.west(4)));
            h.assertTrue(Flash.keepAlive(c.player()),"Flash refused");Flash.tick(new PlayerTickEvent.Post(c.player()));
            h.assertTrue(lit(h,at),"Flash first platform missing");h.assertFalse(lit(h,at.east(10)),"Flash outside range");
            c.player().setPos(Vec3.atCenterOf(at.east(6)));var s=c.player().getData(cn.wwl.orimod.spirit.SpiritData.ENERGY_STATE);
            s.flashActiveSince=h.getLevel().getGameTime()-10;s.flashLastTick=Long.MIN_VALUE;
            Flash.tick(new PlayerTickEvent.Post(c.player()));h.assertTrue(lit(h,at.east(10)),"Flash second platform missing");Flash.stop(c.player());
        });
        for(String who:List.of("player","mob","item"))timed(tests,"overlap_waits_then_materializes_"+who,(h,c)->{
            var at=site(h);platform(h,at);Entity entity;
            if(who.equals("player"))entity=c.player();else if(who.equals("mob")){var mob=EntityType.ZOMBIE.create(h.getLevel());mob.setNoAi(true);entity=mob;}
            else entity=new ItemEntity(h.getLevel(),at.getX()+.5,at.getY()+.1,at.getZ()+.5,new ItemStack(Items.STICK));
            entity.setNoGravity(true);entity.setDeltaMovement(Vec3.ZERO);entity.setPos(Vec3.atBottomCenterOf(at).add(0,.1,0));if(entity!=c.player())h.getLevel().addFreshEntity(entity);
            burst(h,c,at);h.assertFalse(lit(h,at),"Occupied platform became solid");
            later(h,c,12,()->{h.assertFalse(lit(h,at),"Retry forced occupied block");if(entity==c.player())entity.setPos(entity.position().add(0,5,0));else entity.discard();});
            later(h,c,18,()->{h.assertTrue(lit(h,at),"Did not materialize within five ticks after leaving");finish(h,c);});
        });
        timed(tests,"occupied_without_light_expires_and_cleans_ledger",(h,c)->{
            var at=site(h);platform(h,at);c.player().setPos(Vec3.atBottomCenterOf(at));burst(h,c,at);
            later(h,c,105,()->{h.assertFalse(lit(h,at),"Occupied pulse forced solid");h.assertTrue(PlatformTimers.get(h.getLevel()).get(at.asLong())==null,"Expired pending entry leaked");c.player().setPos(c.player().position().add(0,5,0));});
            later(h,c,112,()->{h.assertFalse(lit(h,at),"Expired pending pulse materialized later");finish(h,c);});
        });
        timed(tests,"tree_waits_over_hundred_ticks_without_forcing_then_lights",(h,c)->{
            var at=site(h);platform(h,at);c.player().setPos(Vec3.atBottomCenterOf(at));h.getLevel().setBlock(at.east(3),OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),3);
            later(h,c,120,()->{h.assertFalse(lit(h,at),"Persistent source forced materialization");c.player().setPos(c.player().position().add(0,5,0));});
            later(h,c,127,()->{h.assertTrue(lit(h,at),"Persistent retry was abandoned at a fixed limit");h.getLevel().setBlock(at.east(3),Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);finish(h,c);});
        });
        timed(tests,"standing_on_top_falls_when_platform_expires",(h,c)->{
            var at=site(h);platform(h,at);burst(h,c,at);var p=c.player();p.setPos(Vec3.atBottomCenterOf(at.above()));
            h.assertFalse(PlatformLight.occupied(h.getLevel(),at),"Top face counts as interior");
            later(h,c,101,()->{h.assertFalse(lit(h,at),"Still lit");double y=p.getY();p.move(MoverType.SELF,new Vec3(0,-.4,0));h.assertTrue(p.getY()<y-.39,"Ghost collision kept player afloat");finish(h,c);});
        });
        add(tests,"light_remove_platform_returns_ledger_to_baseline",(h,c)->{
            var at=site(h);var data=PlatformTimers.get(h.getLevel());int before=data.size();platform(h,at);burst(h,c,at);
            h.assertTrue(data.size()==before+1,"Timer not stored");h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);h.assertTrue(data.size()==before,"Removed timer leaked");
        });
        timed(tests,"light_expire_removes_own_saved_entry",(h,c)->{
            var at=site(h);platform(h,at);burst(h,c,at);
            later(h,c,102,()->{h.assertFalse(lit(h,at),"Pulse still lit");h.assertTrue(PlatformTimers.get(h.getLevel()).get(at.asLong())==null,"Expired timer leaked");finish(h,c);});
        });
        add(tests,"load_prunes_stale_coordinates_and_never_loads_neighbors",(h,c)->{
            var at=site(h);var data=PlatformTimers.get(h.getLevel());data.put(at.asLong(),new PlatformTimers.Lease(100,100));
            h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);var chunk=h.getLevel().getChunkAt(at);
            PlatformLight.initializeChunk(h.getLevel(),chunk);h.assertTrue(data.get(at.asLong())==null,"Absent saved platform was not pruned");
            var far=new BlockPos(16_000_000,80,16_000_000);h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Fixture chunk loaded");
            PlatformLight.push(h.getLevel(),Vec3.atCenterOf(far),PlatformRules.Source.TREE);
            PlatformLight.initialize(h.getLevel(),far);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Platform query loaded chunk");
        });
        add(tests,"push_cap_is_256",(h,c)->{
            var at=site(h);var all=new ArrayList<BlockPos>();
            for(int x=-4;x<=4;x++)for(int y=-4;y<=4;y++)for(int z=-4;z<=4;z++)if(x*x+y*y+z*z<=25){var p=at.offset(x,y,z);platform(h,p);all.add(p);}
            int n=PlatformLight.push(h.getLevel(),Vec3.atCenterOf(at),PlatformRules.Source.BURST);
            h.assertTrue(n==256 && all.stream().filter(p->lit(h,p)).count()==256,"Push cap was not exact");
            all.forEach(p->h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3));
        });
        add(tests,"collision_pathfinding_and_light_are_state_specific",(h,c)->{
            var at=site(h);platform(h,at);var off=h.getLevel().getBlockState(at);
            h.assertTrue(off.getCollisionShape(h.getLevel(),at).isEmpty(),"Ghost collides");h.assertFalse(off.isSuffocating(h.getLevel(),at),"Ghost suffocates");h.assertFalse(off.isViewBlocking(h.getLevel(),at),"Ghost blocks view");
            h.assertTrue(off.isPathfindable(PathComputationType.LAND) && off.isPathfindable(PathComputationType.AIR),"Ghost is not passable");h.assertFalse(off.isPathfindable(PathComputationType.WATER),"Ghost counts as water");
            h.assertTrue(off.getLightEmission(h.getLevel(),at)==2,"Ghost light");burst(h,c,at);var on=h.getLevel().getBlockState(at);
            h.assertTrue(on.isCollisionShapeFullBlock(h.getLevel(),at),"Solid collision missing");h.assertFalse(on.isPathfindable(PathComputationType.LAND),"Pathfinder passes through solid platform");
            for(boolean fade:List.of(false,true))for(boolean blink:List.of(false,true))h.assertTrue(on.setValue(FADING,fade).setValue(BLINK,blink).getLightEmission(h.getLevel(),at)==12,"Warning changed light level");
        });
        timed(tests,"warning_does_not_notify_observer_or_relight_block",(h,c)->{
            var at=site(h);platform(h,at);var observer=at.east();
            h.getLevel().setBlock(observer,Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING,net.minecraft.core.Direction.WEST),3);
            burst(h,c,at);
            for(int delay:new int[]{73,78,83,88,93,98})later(h,c,delay,()->{
                h.assertFalse(h.getLevel().getBlockState(observer).getValue(ObserverBlock.POWERED),"Warning notified neighboring observer");
                h.assertFalse(h.getLevel().getBlockTicks().hasScheduledTick(observer,Blocks.OBSERVER),"Warning scheduled observer pulse");
                h.assertTrue(h.getLevel().getBlockState(at).getLightEmission(h.getLevel(),at)==12,"Warning relit block");
            });
            later(h,c,99,()->{h.getLevel().setBlock(observer,Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);finish(h,c);});
        });
        timed(tests,"chunk_initialization_preserves_deadline_and_scans_no_neighbor",(h,c)->{
            var at=site(h);platform(h,at);burst(h,c,at);long until=PlatformTimers.get(h.getLevel()).get(at.asLong()).until();
            later(h,c,75,()->{
                PlatformLight.initializeChunk(h.getLevel(),h.getLevel().getChunkAt(at));
                h.assertTrue(PlatformTimers.get(h.getLevel()).get(at.asLong()).until()==until,"Load extended pulse deadline");
            });
            later(h,c,102,()->{h.assertFalse(lit(h,at),"Reload restarted hundred-tick timer");finish(h,c);});
        });
        add(tests,"loaded_edge_initialization_never_loads_missing_neighbor",(h,c)->{
            var at=new BlockPos(15_900_015,80,15_900_015);var level=h.getLevel();var chunk=level.getChunkAt(at);
            int neighborX=(at.getX()>>4)+1,neighborZ=(at.getZ()>>4)+1;
            h.assertTrue(level.getChunkSource().getChunkNow(neighborX,neighborZ)==null,"Neighbor already full");
            platform(h,at);PlatformLight.initializeChunk(level,chunk);PlatformLight.initialize(level,at);
            h.assertTrue(level.getChunkSource().getChunkNow(neighborX,neighborZ)==null,"Initialization loaded adjacent full chunk");
            level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        });
        add(tests,"platform_hint_priority_after_lantern",(h,c)->{
            var p=c.player();var at=BlockPos.containing(p.position().add(2,0,0));platform(h,at);
            cn.wwl.orimod.progression.CompanionHints.check(p);
            h.assertTrue(p.getData(cn.wwl.orimod.spirit.SpiritData.PROGRESS).rewards().containsKey("hint/spirit_platform"),"Platform hint not delivered");
            var seen=java.util.Set.<String>of();var options=java.util.EnumSet.of(cn.wwl.orimod.progression.CompanionHints.Hint.BASH_ANCHOR,cn.wwl.orimod.progression.CompanionHints.Hint.SPIRIT_PLATFORM);
            h.assertValueEqual(cn.wwl.orimod.progression.CompanionHints.pick(seen,options,0,0,Long.MIN_VALUE),cn.wwl.orimod.progression.CompanionHints.Hint.BASH_ANCHOR,"Platform outranks lamp");
            h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),3);
        });
        add(tests,"removing_lit_source_keeps_tide_cache_invalidation_immediate",(h,c)->{
            var at=site(h);platform(h,at);var source=at.east(3);var level=h.getLevel();
            for(var block:List.of(OriBlocks.BASH_ANCHOR.get(),OriBlocks.SPIRIT_TREE_HEART.get())) {
                level.setBlock(source,block.defaultBlockState(),3);
                if(block==OriBlocks.BASH_ANCHOR.get())SpiritLanternBlock.ignite(level,source,100);
                h.assertTrue(cn.wwl.orimod.world.GloomTideDefense.blocksProtect(level,Vec3.atCenterOf(at)),"Defense fixture absent");
                level.setBlock(source,Blocks.AIR.defaultBlockState(),3);
                h.assertFalse(cn.wwl.orimod.world.GloomTideDefense.blocksProtect(level,Vec3.atCenterOf(at)),"Platform recheck repopulated removed defense source");
            }
            level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        });
        return tests;
    }
    static BlockPos site(GameTestHelper h){return h.absolutePos(new BlockPos(32,32,32));}
    static void platform(GameTestHelper h,BlockPos p){h.getLevel().setBlock(p,OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),3);}
    static boolean lit(GameTestHelper h,BlockPos p){var s=h.getLevel().getBlockState(p);return s.is(OriBlocks.SPIRIT_PLATFORM)&&s.getValue(LIT);}
    static void burst(GameTestHelper h,Fixtures.ConnectedPlayer c,BlockPos p){var orb=new LightBurst(OriEntities.LIGHT_BURST.get(),h.getLevel());orb.setOwner(c.player());orb.setPos(Vec3.atCenterOf(p));orb.detonate();}
    static void later(GameTestHelper h,Fixtures.ConnectedPlayer c,long delay,Runnable r){h.runAfterDelay(delay,()->{try{r.run();}catch(Throwable e){c.close();throw e;}});}
    static void finish(GameTestHelper h,Fixtures.ConnectedPlayer c){c.close();h.succeed();}
    static void add(List<TestFunction> list,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run){
        list.add(new TestFunction("spirit_platform","spirit_platform."+name,"orimod_gametest:platform_room",100,0,true,h->{try(var c=Fixtures.connectedPlayer(h)){CrackedRuinGameTests.prepare(c);run.accept(h,c);}h.succeed();}));
    }
    static void timed(List<TestFunction> list,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run){
        list.add(new TestFunction("spirit_platform","spirit_platform."+name,"orimod_gametest:platform_room",360,0,true,h->{var c=Fixtures.connectedPlayer(h);try{CrackedRuinGameTests.prepare(c);run.accept(h,c);}catch(Throwable e){c.close();throw e;}}));
    }
}
