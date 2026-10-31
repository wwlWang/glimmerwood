package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.entity.OriMobs;
import cn.wwl.orimod.item.SpiritHammerItem;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.OriEntities;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.*;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class CrackedRuinGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(var kind:CrackedRuin.Kind.values()) {
            for(var source:CrackedRuin.Source.values()) add(tests,"ability_"+kind+"_"+source,(h,c)->{
                var p=c.player();var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,5));level.setBlock(pos,block(kind).defaultBlockState(),3);
                switch(source) {
                    case CHARGE_FLAME -> ChargeFlame.release(p);
                    case SMASH -> {p.setYRot(-90);SpiritHammerItem.smash(p);}
                    case LIGHT_BURST -> {var orb=new LightBurst(OriEntities.LIGHT_BURST.get(),level);orb.setOwner(p);orb.setPos(Vec3.atCenterOf(pos).add(-1,0,0));orb.detonate();}
                    case SPEAR -> {
                        var spear=new SpiritSpear(OriEntities.SPIRIT_SPEAR.get(),level);spear.setOwner(p);spear.setPos(Vec3.atCenterOf(pos).add(-2,0,0));spear.setDeltaMovement(2.2,0,0);
                        try{spear.tick();spear.tick();}finally{spear.discard();}
                    }
                    case BASHED_IMPACT -> {
                        var shot=OriMobs.GLOOM_SHOT.get().create(level);shot.setPos(Vec3.atCenterOf(pos).add(-2,0,0));shot.setDeltaMovement(-.5,0,0);
                        try {
                            var session=new BashSession(p,new SpiritTargets.Target(shot,null,shot.position(),false));session.release(new Vec3(-1,0,0),p);
                            shot.tick();shot.tick();
                        }finally{shot.discard();BashedImpact.reset();}
                    }
                    case STOMP -> {
                        p.setPos(Vec3.atBottomCenterOf(pos.above()));var s=p.getData(SpiritData.MOVEMENT);
                        s.active=true;s.lastInput=level.getGameTime();s.mode=MoveMode.STOMP;
                        AirMovement.fall(new LivingFallEvent(p,5,1));
                    }
                }
                h.assertTrue(level.getBlockState(pos).isAir()==CrackedRuin.breaks(kind,source),"Wrong actual ability result "+kind+"/"+source);
            });
            add(tests,"piston_wither_dragon_enderman_guards_"+kind,(h,c)->{
                var pos=h.absolutePos(new BlockPos(6,2,5));var state=block(kind).defaultBlockState();h.getLevel().setBlock(pos,state,3);
                h.assertTrue(state.getPistonPushReaction()==PushReaction.BLOCK,"Piston reaction");
                for(var d:Direction.values())h.assertFalse(PistonBaseBlock.isPushable(state,h.getLevel(),pos,d,true,d),"Piston can move/destroy block");
                h.assertFalse(WitherBoss.canDestroy(state),"Wither destroy predicate accepted block");
                h.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE),"Boss immunity tags missing");
                h.assertFalse(state.is(BlockTags.ENDERMAN_HOLDABLE),"Enderman can carry block");
                for(var type:List.of(EntityType.WITHER,EntityType.ENDER_DRAGON)) {
                    var entity=type.create(h.getLevel());h.assertFalse(state.canEntityDestroy(h.getLevel(),pos,entity),"Entity destruction bypassed immunity");entity.discard();
                }
            });
            for(var tool:List.of(Items.AIR,Items.DIAMOND_PICKAXE)) add(tests,"survival_mining_"+kind+"_"+(tool==Items.AIR?"hand":"pickaxe"),(h,c)->{
                var p=c.player();var pos=h.absolutePos(new BlockPos(6,2,5));h.getLevel().setBlock(pos,block(kind).defaultBlockState(),3);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
                p.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.WEST,h.getLevel().getMaxBuildHeight(),0);
                for(int i=0;i<240;i++)p.gameMode.tick();
                p.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,Direction.WEST,h.getLevel().getMaxBuildHeight(),1);
                h.assertTrue(h.getLevel().getBlockState(pos).is(block(kind)),"Survival mining broke sealed block");
                h.assertTrue(block(kind).defaultBlockState().getDestroyProgress(p,h.getLevel(),pos)==0,"Mining accumulates progress");
            });
            add(tests,"creative_can_break_"+kind,(h,c)->{
                var p=c.player();p.setGameMode(GameType.CREATIVE);var pos=h.absolutePos(new BlockPos(6,2,5));h.getLevel().setBlock(pos,block(kind).defaultBlockState(),3);
                p.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.WEST,h.getLevel().getMaxBuildHeight(),0);
                h.assertTrue(h.getLevel().isEmptyBlock(pos),"Creative removal refused");
            });
            for(boolean tnt:List.of(true,false)) add(tests,"explosion_"+kind+"_"+(tnt?"tnt":"charged_creeper"),(h,c)->{
                var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,3,6));level.setBlock(pos,block(kind).defaultBlockState(),3);
                Entity entity=tnt?EntityType.TNT.create(level):EntityType.CREEPER.create(level);entity.setPos(Vec3.atCenterOf(pos).add(0,0,-1.5));
                c.player().setPos(c.player().position().add(0,12,0));
                try {
                    var explosion=level.explode(entity,entity.getX(),entity.getY(),entity.getZ(),tnt?4:6,tnt?Level.ExplosionInteraction.TNT:Level.ExplosionInteraction.MOB);
                    h.assertFalse(explosion.getToBlow().contains(pos),"Explosion selected protected block");
                    h.assertTrue(level.getBlockState(pos).is(block(kind)),"Explosion broke seal");
                }finally{entity.discard();}
            });
        }
        timed(tests,"three_by_three_collapses_with_distance_delays_and_air_gap",(h,c)->{
            var start=h.absolutePos(new BlockPos(4,3,5));
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)h.getLevel().setBlock(start.offset(x,y,0),block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            var separate=start.offset(0,0,2);h.getLevel().setBlock(separate,block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            h.assertTrue(CrackedRuin.hit(h.getLevel(),start,CrackedRuin.Source.SMASH)==9,"Wrong component");
            h.assertTrue(h.getLevel().isEmptyBlock(start),"Root not broken immediately");
            h.runAfterDelay(1,safe(c,()->h.assertTrue(h.getLevel().getBlockState(start.offset(2,2,0)).getValue(CrackedRuinBlock.COLLAPSING),"Far block broke before distance delay")));
            h.runAfterDelay(4,safe(c,()->{
                for(int x=0;x<3;x++)for(int y=0;y<3;y++)h.assertTrue(h.getLevel().isEmptyBlock(start.offset(x,y,0)),"Connected block survived");
                h.assertTrue(h.getLevel().getBlockState(separate).is(block(CrackedRuin.Kind.WALL)),"Air gap bridged");finish(h,c);
            }));
        });
        timed(tests,"repeat_hit_and_later_placement_do_not_expand_or_reschedule",(h,c)->{
            var start=h.absolutePos(new BlockPos(4,3,5));
            for(int x=0;x<4;x++)h.getLevel().setBlock(start.east(x),block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            CrackedRuin.hit(h.getLevel(),start,CrackedRuin.Source.SMASH);
            var ticks=(LevelChunkTicks<Block>)h.getLevel().getChunkAt(start.east(2)).getBlockTicks();
            var before=ticks.getAll().filter(t->t.pos().equals(start.east(2))).toList();
            h.assertTrue(CrackedRuin.hit(h.getLevel(),start.east(2),CrackedRuin.Source.LIGHT_BURST)==0,"Collapsing hit restarted BFS");
            h.assertValueEqual(ticks.getAll().filter(t->t.pos().equals(start.east(2))).toList(),before,"Repeat changed scheduled tick");
            h.getLevel().setBlock(start.east(4),block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            h.getLevel().setBlock(start.east(2),Blocks.AIR.defaultBlockState(),3);
            h.getLevel().setBlock(start.east(2),block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            h.runAfterDelay(5,safe(c,()->{
                h.assertTrue(h.getLevel().getBlockState(start.east(4)).is(block(CrackedRuin.Kind.WALL)),"New neighbor joined old wave");
                h.assertTrue(h.getLevel().getBlockState(start.east(2)).is(block(CrackedRuin.Kind.WALL)),"Replacement inherited old tick");
                h.assertTrue(h.getLevel().isEmptyBlock(start.east(3)),"Snapshot stopped at replaced member");finish(h,c);
            }));
        });
        timed(tests,"wave_caps_at_sixty_four",(h,c)->{
            var origin=h.absolutePos(new BlockPos(6,3,6));
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)h.getLevel().setBlock(origin.offset(x,0,z),block(CrackedRuin.Kind.FLOOR).defaultBlockState(),3);
            h.assertTrue(CrackedRuin.hit(h.getLevel(),origin,CrackedRuin.Source.STOMP)==64,"Chain exceeds cap");
            h.runAfterDelay(9,safe(c,()->{
                int remaining=0;for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)if(h.getLevel().getBlockState(origin.offset(x,0,z)).is(block(CrackedRuin.Kind.FLOOR)))remaining++;
                h.assertTrue(remaining==17,"Scheduled ticks expanded the original snapshot: "+remaining);finish(h,c);
            }));
        });
        add(tests,"pending_chunk_ticks_and_state_survive_save_roundtrip",(h,c)->{
            var origin=h.absolutePos(new BlockPos(5,3,5));var next=origin.east();
            h.getLevel().setBlock(origin,block(CrackedRuin.Kind.WALL).defaultBlockState(),3);h.getLevel().setBlock(next,block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            CrackedRuin.hit(h.getLevel(),origin,CrackedRuin.Source.SMASH);long now=h.getLevel().getGameTime();
            var saved=h.getLevel().getChunkAt(next).getTicksForSerialization().blocks().save(now,b->BuiltInRegistries.BLOCK.getKey(b).toString());
            var restored=LevelChunkTicks.load((net.minecraft.nbt.ListTag)saved,id->BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)),new ChunkPos(next));restored.unpack(now);
            h.assertTrue(restored.getAll().anyMatch(t->t.pos().equals(next)&&t.triggerTick()==now+1),"Pending collapse lost on chunk save");
            var state=h.getLevel().getBlockState(next);var tag=net.minecraft.nbt.NbtUtils.writeBlockState(state);
            h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag).getValue(CrackedRuinBlock.COLLAPSING),"Saved state lost collapse membership");
        });
        add(tests,"missing_chunk_is_never_loaded",(h,c)->{
            var pos=new BlockPos(16_000_000,100,16_000_000);
            h.assertTrue(CrackedRuin.hit(h.getLevel(),pos,CrackedRuin.Source.SMASH)==0,"Missing chunk hit");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null,"Hit loaded a chunk");
        });
        add(tests,"stomp_diagonal_floor_but_not_three_blocks_away",(h,c)->{
            var p=c.player();var feet=p.position();var near=BlockPos.containing(feet).offset(1,-1,1);var far=BlockPos.containing(feet).offset(3,-1,0);
            h.getLevel().setBlock(near,block(CrackedRuin.Kind.FLOOR).defaultBlockState(),3);h.getLevel().setBlock(far,block(CrackedRuin.Kind.FLOOR).defaultBlockState(),3);
            var s=p.getData(SpiritData.MOVEMENT);s.mode=MoveMode.STOMP;s.active=true;s.lastInput=h.getLevel().getGameTime();AirMovement.fall(new LivingFallEvent(p,4,1));
            h.assertTrue(h.getLevel().isEmptyBlock(near),"Diagonal stomp missed");h.assertTrue(h.getLevel().getBlockState(far).is(block(CrackedRuin.Kind.FLOOR)),"Distant stomp hit");
        });
        timed(tests,"bashed_enemy_real_collision_breaks_wall",(h,c)->{
            var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,2,5));level.setBlock(pos,block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            // NoAI also disables LivingEntity.travel in 1.21.1. Empty goals retain real knockback physics.
            var zombie=new Zombie(level){@Override protected void registerGoals(){}};
            zombie.setNoGravity(true);zombie.setPos(Vec3.atBottomCenterOf(pos).add(-1.5,0,0));level.addFreshEntity(zombie);
            new BashSession(c.player(),new SpiritTargets.Target(zombie,null,zombie.position(),false)).release(new Vec3(-1,0,0),c.player());
            h.runAfterDelay(8,()->{try{h.assertTrue(level.isEmptyBlock(pos),"Bashed enemy collision did not break wall: position="+zombie.position()+", delta="+zombie.getDeltaMovement()+", wall="+pos+", horizontal="+zombie.horizontalCollision+", vertical="+zombie.verticalCollision+", tracked="+BashedImpact.tracked()+", ticks="+zombie.tickCount);finish(h,c);}catch(RuntimeException|Error e){c.close();throw e;}finally{zombie.discard();BashedImpact.reset();}});
        });
        add(tests,"cancelled_bash_and_unmarked_projectile_cannot_break_wall",(h,c)->{
            var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,5));level.setBlock(pos,block(CrackedRuin.Kind.WALL).defaultBlockState(),3);
            for(boolean cancel:List.of(false,true)) {
                var shot=OriMobs.GLOOM_SHOT.get().create(level);shot.setOwner(c.player());shot.setPos(Vec3.atCenterOf(pos).add(-2,0,0));shot.setDeltaMovement(1.6,0,0);
                try{if(cancel)new BashSession(c.player(),new SpiritTargets.Target(shot,null,shot.position(),false)).release(null,c.player());shot.tick();shot.tick();}
                finally{shot.discard();}
                h.assertTrue(level.getBlockState(pos).is(block(CrackedRuin.Kind.WALL)),"Non-launched projectile broke wall");
            }
        });
        return tests;
    }
    private static CrackedRuinBlock block(CrackedRuin.Kind kind) {return kind==CrackedRuin.Kind.WALL?OriBlocks.CRACKED_RUIN_WALL.get():OriBlocks.CRACKED_RUIN_FLOOR.get();}
    static void prepare(Fixtures.ConnectedPlayer c) {
        var p=c.player();p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,cn.wwl.orimod.command.OriCommands.allAbilities());p.setData(SpiritData.ENERGY,100f);
        p.setNoGravity(true);p.getAbilities().invulnerable=true;p.getData(SpiritData.COOLDOWNS).flameReadyAt=Long.MAX_VALUE;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run) {
        timed(tests,name,(h,c)->{try{run.accept(h,c);h.succeed();}finally{c.close();}});
    }
    private static void timed(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run) {
        tests.add(new TestFunction("cracked_ruin","cracked_ruin."+name.toLowerCase(Locale.ROOT),"orimod_gametest:room",50,0,true,h->{
            var c=Fixtures.connectedPlayer(h);prepare(c);
            try{run.accept(h,c);}catch(RuntimeException|Error e){c.close();throw e;}
        }));
    }
    private static Runnable safe(Fixtures.ConnectedPlayer c,Runnable run) {return ()->{try{run.run();}catch(RuntimeException|Error e){c.close();throw e;}};}
    private static void finish(GameTestHelper h,Fixtures.ConnectedPlayer c) {c.close();h.succeed();}
}
