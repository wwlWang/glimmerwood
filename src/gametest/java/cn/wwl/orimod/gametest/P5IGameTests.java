package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.OriEntities;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_p5_i")
public final class P5IGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(var type:List.of(EntityType.PIGLIN,EntityType.PIGLIN_BRUTE,EntityType.SPIDER,EntityType.CAVE_SPIDER))
            for(String state:List.of("peaceful","target_owner","hurt_by_owner","target_other","hurt_by_other","anger_owner","anger_other"))
                add(tests,"conditional_"+BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()+"_"+state,h->conditional(h,type,state));
        add(tests,"zombie_remains_hostile",h->{
            var p=Fixtures.player(h);var zombie=EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(CombatTargets.canHit(p,zombie)&&CombatTargets.canHitWithoutOwner(zombie),"Unconditional enemy lost");zombie.discard();
        });
        for(var item:List.of(OriItems.SPIRIT_SPEAR.get(),OriItems.SPIRIT_BOW.get(),OriItems.SPIRIT_HAMMER.get(),OriItems.SPIRIT_BLADE.get()))
            for(boolean empty:List.of(false,true))add(tests,"interact_"+BuiltInRegistries.ITEM.getKey(item).getPath()+"_"+(empty?"empty":"ready"),h->interactions(h,item,empty));
        add(tests,"flame_reselects_after_occlusion_and_movement",h->{
            var level=h.getLevel();var p=Fixtures.player(h);var near=EntityType.ZOMBIE.create(level);var far=EntityType.ZOMBIE.create(level);
            near.setNoAi(true);far.setNoAi(true);near.setPos(p.position().add(3,0,0));far.setPos(p.position().add(0,0,5));
            level.addFreshEntity(near);level.addFreshEntity(far);var wall=p.blockPosition().east();var before=new HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
            try {
                for(int y=0;y<3;y++){var at=wall.above(y);before.put(at,level.getBlockState(at));level.setBlock(at,Blocks.STONE.defaultBlockState(),18);}
                h.assertTrue(SpiritFlame.target(p)==far,"Occluded nearest enemy blocked visible farther enemy");
                near.setPos(p.position().add(0,0,-3));
                h.assertTrue(SpiritFlame.target(p)==near,"Old target remained locked after enemy movement");
                near.setInvisible(true);h.assertTrue(SpiritFlame.target(p)==far,"Invisible target remained locked");
                far.discard();h.assertTrue(SpiritFlame.target(p)==null,"Removed target remained locked");
            }finally{near.discard();far.discard();before.forEach((at,state)->level.setBlock(at,state,18));}
        });
        add(tests,"report_creates_missing_parents",h->{
            try {
                var root=java.nio.file.Files.createTempDirectory("orimod-report-");var target=root.resolve("absent/nested/report.json");
                h.assertFalse(java.nio.file.Files.exists(target.getParent()),"Fixture parent already exists");
                java.nio.file.Files.writeString(ReportFiles.output(target),"{\"passed\":true}");
                h.assertTrue(java.nio.file.Files.readString(target).contains("true"),"Nested report not saved");
                java.nio.file.Files.delete(target);java.nio.file.Files.delete(target.getParent());java.nio.file.Files.delete(target.getParent().getParent());java.nio.file.Files.delete(root);
            } catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        return tests;
    }
    private static void conditional(GameTestHelper h,EntityType<?> type,String state) {
        var l=h.getLevel();var p=Fixtures.player(h);p.setData(SpiritData.FORM,true);
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.GOLDEN_CHESTPLATE));
        var other=Fixtures.player(h);l.addNewPlayer(p);l.addNewPlayer(other);var mob=(Mob)type.create(l);mob.setNoAi(true);mob.setPos(p.position().add(2,0,0));l.addFreshEntity(mob);
        try {
            boolean expected=state.endsWith("_owner");
            if(!state.equals("peaceful")) {
                l.tickNonPassenger(mob); // Vanilla revenge goals compare the hurt timestamp against their initial zero.
                var attacker=expected?p:other;
                h.assertTrue(mob.hurt(attacker.damageSources().playerAttack(attacker),1f),"Player hit was rejected");
                mob.invulnerableTime=0;
                h.assertTrue(mob.getLastHurtByMobTimestamp()>0 && mob.getLastHurtByMob()==attacker,"Vanilla damage history was not recorded");
                if(state.startsWith("target_")) {
                    if(mob instanceof AbstractPiglin piglin) {
                        // Real vanilla brain activities acquire the victim after the hit's anger flow.
                        mob.setNoAi(false);
                        for(int i=0;i<10 && piglin.getTarget()!=attacker;i++)l.tickNonPassenger(mob);
                    } else {
                        var retaliation=new HurtByTargetGoal((PathfinderMob)mob);
                        h.assertTrue(retaliation.canUse(),"Vanilla revenge goal did not activate");retaliation.start();
                    }
                    h.assertTrue(mob.getTarget()==attacker,"Vanilla retaliation failed to select attacker");
                    mob.setLastHurtByMob(null); // Isolate active target eligibility from damage history.
                } else if(state.startsWith("anger_")) {
                    if(mob instanceof AbstractPiglin) {
                        h.assertTrue(mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(attacker.getUUID()::equals).isPresent(),"Vanilla hit did not anger piglin");
                        mob.setLastHurtByMob(null); // Anger can precede ATTACK_TARGET by several ticks.
                    }
                }
                mob.setNoAi(true);
            }
            h.assertValueEqual(CombatTargets.canHit(p,mob),expected,"Owner eligibility "+state);
            h.assertFalse(CombatTargets.canHitWithoutOwner(mob),"Orphan guessed conditional hostility");
            float health=mob.getHealth();h.assertValueEqual(SpiritFlame.fire(p,mob),expected,"Flame path "+state);
            h.assertValueEqual(mob.getHealth()<health,expected,"Flame damaged bystander or missed attacker");
            mob.invulnerableTime=0;health=mob.getHealth();
            var burst=new LightBurst(OriEntities.LIGHT_BURST.get(),l);burst.setPos(mob.position());burst.detonate();
            h.assertValueEqual(mob.getHealth(),health,"Orphan burst damaged conditional mob");
        } finally {mob.discard();l.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);l.removePlayerImmediately(other,Entity.RemovalReason.DISCARDED);}
    }
    private static void interactions(GameTestHelper h,Item weapon,boolean empty) {
        try(var c=Fixtures.connectedPlayer(h)) {
            var p=c.player();var l=h.getLevel();p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,Arrays.stream(Ability.values()).mapToInt(a->a.bit).reduce(0,(a,b)->a|b));
            var stack=new ItemStack(weapon);p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.setShiftKeyDown(false);
            var at=p.blockPosition().east(2);var old=l.getBlockState(at);var upper=l.getBlockState(at.above());var floor=l.getBlockState(at.below());
            try {
                l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);
                for(var block:List.of(OriBlocks.GLADES_WELL.get(),Blocks.CHEST,Blocks.OAK_DOOR,Blocks.LEVER,Blocks.STONE_BUTTON,Blocks.CRAFTING_TABLE,OriBlocks.KEYSTONE_DOOR.get(),OriBlocks.FOREST_SHRINE.get())) {
                    p.closeContainer();p.setData(SpiritData.ENERGY,empty?0f:100f);p.getCooldowns().removeCooldown(weapon);
                    if(empty)p.getCooldowns().addCooldown(weapon,30);
                    var state=block.defaultBlockState();if(state.hasProperty(BlockStateProperties.ATTACH_FACE))state=state.setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR);
                    l.setBlock(at,state,2);
                    if(block==Blocks.OAK_DOOR)l.setBlock(at.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
                    var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.WEST,at,false);
                    var result=p.gameMode.useItemOn(p,l,stack,InteractionHand.MAIN_HAND,hit);
                    h.assertTrue(result.consumesAction(),"Block interaction declined: "+block+" holding "+weapon);
                    h.assertFalse(p.isUsingItem(),"Block interaction charged weapon: "+block);
                    if(block==Blocks.OAK_DOOR)h.assertTrue(l.getBlockState(at).getValue(DoorBlock.OPEN),"Door stayed closed");
                    if(block==Blocks.LEVER||block==Blocks.STONE_BUTTON)h.assertTrue(l.getBlockState(at).getValue(BlockStateProperties.POWERED),"Switch stayed off");
                    if(block==Blocks.CHEST||block==Blocks.CRAFTING_TABLE)h.assertTrue(p.containerMenu!=p.inventoryMenu,"Container did not open");
                    l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);l.setBlock(at,Blocks.AIR.defaultBlockState(),2);
                }
                for(var type:List.of(EntityType.VILLAGER,cn.wwl.orimod.entity.OriMobs.GLADES_MERCHANT.get())) {
                    var mob=(Mob)type.create(l);mob.setPos(Vec3.atBottomCenterOf(at));mob.setNoAi(true);l.addFreshEntity(mob);
                    if(mob instanceof Villager v)v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
                    try {p.setData(SpiritData.ENERGY,empty?0f:100f);h.assertTrue(p.interactOn(mob,InteractionHand.MAIN_HAND).consumesAction(),"Entity interaction declined: "+type);
                        h.assertFalse(p.isUsingItem(),"Entity interaction charged weapon");p.closeContainer();}
                    finally{mob.discard();}
                }
            } finally {p.closeContainer();p.stopUsingItem();l.setBlock(at,old,2);l.setBlock(at.above(),upper,2);l.setBlock(at.below(),floor,2);}
        }
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> body) {
        tests.add(new TestFunction("p5_i","p5_i."+name,"orimod_p5_i:platform_room",200,0,true,h->{body.accept(h);h.succeed();}));
    }
}
