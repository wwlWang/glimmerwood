package cn.wwl.orimod.gametest;

import cn.wwl.orimod.entity.RecoveryOrb;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class RecoveryGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var list = new ArrayList<TestFunction>();
        add(list,"spirit_food_fields_frozen_under_exhaustion_eating_and_ticks",(h,p)->{
            p.setHealth(10);var f=p.getFoodData();f.setFoodLevel(7);f.setSaturation(2);f.addExhaustion(3);
            var before=food(p);
            for(int i=0;i<200;i++){p.causeFoodExhaustion(1);f.tick(p);}
            p.eat(p.level(),new ItemStack(Items.BREAD),Foods.BREAD);
            h.assertValueEqual(food(p),before,"Spirit mutated saved human food"); Fixtures.near(h,p.getHealth(),10,"Food healed spirit");
        });
        add(list,"plain_food_refused_and_kept_human_can_eat",(h,p)->{
            p.getFoodData().setFoodLevel(7);var bread=new ItemStack(Items.BREAD,2);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bread);
            var result=Items.BREAD.use(p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertFalse(result.getResult().consumesAction(),"Spirit started eating bread");h.assertFalse(p.isUsingItem(),"Bread use began");h.assertValueEqual(bread.getCount(),2,"Bread lost");
            p.setData(SpiritData.FORM,false);h.assertTrue(Items.BREAD.use(p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND).getResult().consumesAction(),"Human cannot eat");
        });
        add(list,"golden_apple_effects_survive_frozen_food",(h,p)->{
            p.getFoodData().setFoodLevel(7);var before=food(p);var apple=new ItemStack(Items.GOLDEN_APPLE,2);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,apple);
            h.assertTrue(Items.GOLDEN_APPLE.use(p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND).getResult().consumesAction(),"Always edible food refused");
            apple.finishUsingItem(p.level(),p);h.assertValueEqual(apple.getCount(),1,"Apple not consumed");
            h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)&&p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),"Apple effects dropped");h.assertValueEqual(food(p),before,"Apple changed spirit food");
        });
        add(list,"bindings_server_validates_moves_and_copies_on_death",(h,p)->{
            p.setData(SpiritData.ABILITIES,Ability.FLASH.bit);SpiritSkills.tick(p);
            h.assertFalse(SpiritSkills.bind(p,1,WheelSkill.LIGHT_BURST),"Unlearned bound");
            h.assertTrue(SpiritSkills.bind(p,1,WheelSkill.FLASH),"Learned move refused");
            h.assertValueEqual(p.getData(SpiritData.SKILL_BINDINGS).slots(),List.of("regenerate","flash",""),"Duplicate binding survived");
            var clone=Fixtures.player(h);clone.restoreFrom(p,false);h.assertValueEqual(clone.getData(SpiritData.SKILL_BINDINGS),p.getData(SpiritData.SKILL_BINDINGS),"Death lost bindings");
        });
        list.add(new TestFunction("recovery","recovery.gamerule_disables_only_passive_life","orimod_gametest:room",40,0,true,h->{
            var c=Fixtures.connectedPlayer(h);var p=c.player();p.setData(SpiritData.FORM,true);p.setHealth(10);
            h.runAfterDelay(20-h.getLevel().getGameTime()%20,()->{
                var rule=h.getLevel().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION);boolean before=rule.get();
                try{rule.set(false,h.getLevel().getServer());var state=p.getData(SpiritData.ENERGY_STATE);state.recoveryHurtAt=p.level().getGameTime()-200;SpiritRecovery.tick(p);
                    Fixtures.near(h,p.getHealth(),10,"Gamerule ignored");p.setData(SpiritData.ENERGY,20f);Regenerate.keepAlive(p);complete(p);Fixtures.near(h,p.getHealth(),16,"Gamerule blocked active healing");h.succeed();
                }finally{rule.set(before,h.getLevel().getServer());Regenerate.release(p);c.close();}
            });
        }));
        add(list,"starving_spirit_safe_human_hunger_resumes",(h,p)->{
            vulnerable(p);
            var level=h.getLevel();var old=level.getDifficulty();boolean natural=level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
            try {
                level.getServer().setDifficulty(Difficulty.HARD,true);level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true,level.getServer());
                p.setHealth(10);p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);
                for(int i=0;i<160;i++)p.getFoodData().tick(p);
                Fixtures.near(h,p.getHealth(),10,"Spirit starved");
                p.setData(SpiritData.FORM,false);for(int i=0;i<80;i++)p.getFoodData().tick(p);
                h.assertTrue(p.getHealth()<10,"Human starvation disabled");
                p.invulnerableTime=0;p.setHealth(10);p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(5);
                for(int i=0;i<10;i++)p.getFoodData().tick(p);
                h.assertTrue(p.getHealth()>10,"Human food healing disabled");
            } finally {level.getServer().setDifficulty(old,true);level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(natural,level.getServer());}
        });
        add(list,"peaceful_spirit_does_not_bypass_cap_or_refill_food",(h,p)->{
            var level=h.getLevel();var old=level.getDifficulty();boolean natural=level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
            try {
                level.getServer().setDifficulty(Difficulty.PEACEFUL,true);level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true,level.getServer());
                p.setHealth(17);p.getFoodData().setFoodLevel(7);p.getFoodData().setSaturation(2);var before=food(p);
                p.tickCount=20;p.aiStep();Fixtures.near(h,p.getHealth(),17,"Peaceful bypassed spirit cap");h.assertValueEqual(food(p),before,"Peaceful changed food");
                p.setData(SpiritData.FORM,false);p.tickCount=40;p.aiStep();h.assertTrue(p.getHealth()>17,"Human peaceful healing changed");
            }finally{level.getServer().setDifficulty(old,true);level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(natural,level.getServer());}
        });
        add(list,"regenerate_old_awakened_save_without_ability_bit",(h,p)->{
            p.setData(SpiritData.ABILITIES,0);p.setHealth(17);p.setData(SpiritData.ENERGY,20f);
            h.assertTrue(Regenerate.keepAlive(p),"Baseline heal unavailable to old save");complete(p);
            Fixtures.near(h,p.getHealth(),20,"Did not heal above passive cap");Fixtures.near(h,SpiritProgression.energy(p),10,"Wrong cast cost");
            h.assertFalse(Regenerate.keepAlive(p),"Full health started paid channel");
        });
        add(list,"release_and_expired_lease_cancel_without_charge",(h,p)->{
            p.setHealth(10);p.setData(SpiritData.ENERGY,20f);Regenerate.keepAlive(p);Regenerate.release(p);
            complete(p);Fixtures.near(h,p.getHealth(),10,"Release healed");Fixtures.near(h,SpiritProgression.energy(p),20,"Release charged");
            Regenerate.keepAlive(p);p.getData(SpiritData.ENERGY_STATE).regenerate.until=p.level().getGameTime();Regenerate.tick(p);
            h.assertFalse(Regenerate.active(p),"Expired channel survived");
        });
        add(list,"damage_interrupt_requires_release_then_retry",(h,p)->{
            vulnerable(p);
            p.setHealth(10);p.setData(SpiritData.ENERGY,20f);Regenerate.keepAlive(p);
            p.hurt(p.damageSources().generic(),1);h.assertFalse(Regenerate.active(p),"Damage failed to interrupt");
            h.assertFalse(Regenerate.keepAlive(p),"Late keepalive restarted damaged channel");
            Fixtures.near(h,SpiritProgression.energy(p),20,"Interrupted channel charged");
            Regenerate.release(p);h.assertTrue(Regenerate.keepAlive(p),"Released channel stayed locked");complete(p);Fixtures.near(h,p.getHealth(),15,"Retry heal");
        });
        add(list,"form_switch_cancels_and_nonspirit_cannot_cast",(h,p)->{
            p.setHealth(10);Regenerate.keepAlive(p);cn.wwl.orimod.spirit.SpiritForm.toggle(p);
            h.assertFalse(Regenerate.active(p),"Form change retained channel");h.assertFalse(Regenerate.keepAlive(p),"Human cast accepted");
        });
        for(boolean life:List.of(true,false))add(list,"orb_full_kept_then_one_consumer_"+life,(h,p)->{
            var orb=orb(p,life);p.setHealth(20);p.setData(SpiritData.ENERGY,100f);
            h.assertFalse(orb.collect(p),"Full resource consumed orb");h.assertFalse(orb.isRemoved(),"Full orb disappeared");
            p.setHealth(19);p.setData(SpiritData.ENERGY,98f);h.assertTrue(orb.collect(p),"Needed orb rejected");
            Fixtures.near(h,life?p.getHealth():SpiritProgression.energy(p),life?20:100,"Orb cap");
            try(var other=Fixtures.connectedPlayer(h)){var q=other.player();q.setData(SpiritData.FORM,true);q.setHealth(10);q.setData(SpiritData.ENERGY,10f);
                h.assertFalse(orb.collect(q),"Second player consumed removed orb");Fixtures.near(h,q.getHealth(),10,"Duplicate heal");Fixtures.near(h,SpiritProgression.energy(q),10,"Duplicate energy");}
        });
        add(list,"orb_saved_age_expires_and_kind_survives",(h,p)->{
            var orb=orb(p,false);var t=new CompoundTag();orb.saveWithoutId(t);t.putInt("age",RecoveryTuning.ORB_LIFETIME-1);
            var restored=new RecoveryOrb(OriEntities.RECOVERY_ORB.get(),p.level());restored.load(t);h.assertFalse(restored.life(),"Kind lost");restored.tick();h.assertTrue(restored.isRemoved(),"Orb did not expire");
        });
        add(list,"well_still_restores_both_to_full",(h,p)->{
            p.setHealth(17);p.setData(SpiritData.ENERGY,20f);h.assertTrue(SpiritWell.refill(p),"Well refused");
            Fixtures.near(h,p.getHealth(),p.getMaxHealth(),"Well life");Fixtures.near(h,SpiritProgression.energy(p),SpiritProgression.maxEnergy(p),"Well energy");
        });
        add(list,"absorption_damage_also_interrupts",(h,p)->{
            vulnerable(p);
            p.setHealth(10);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION,100,0));
            h.assertTrue(p.getAbsorptionAmount()>=4,"Fixture lacks absorption attribute");Regenerate.keepAlive(p);p.hurt(p.damageSources().generic(),1);
            h.assertFalse(Regenerate.active(p),"Absorbed hit did not interrupt");Fixtures.near(h,p.getHealth(),10,"Absorption lost real health");
        });
        list.add(new TestFunction("recovery","recovery.passive_caps_and_human_energy_floor","orimod_gametest:room",60,0,true,h->{
            var c=Fixtures.connectedPlayer(h);var p=c.player();p.setData(SpiritData.FORM,true);p.setHealth(15.9f);p.setData(SpiritData.ENERGY,19.9f);
            var s=p.getData(SpiritData.ENERGY_STATE);s.recoveryHurtAt=p.level().getGameTime()-160;s.lastSpent=p.level().getGameTime()-80;
            // Embedded network players do not receive the normal connection-driven doTick. Drive the real event entry.
            var closed=new java.util.concurrent.atomic.AtomicBoolean();
            Runnable close=()->{if(closed.compareAndSet(false,true))c.close();};
            h.onEachTick(()->{if(!closed.get())SpiritProgression.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));});
            h.runAfterDelay(22,()->{try{
                Fixtures.near(h,p.getHealth(),16,"Passive cap");Fixtures.near(h,SpiritProgression.energy(p),20,"Energy floor");
                p.setHealth(19);p.heal(1);Fixtures.near(h,p.getHealth(),20,"Explicit healing was capped");
                p.setData(SpiritData.FORM,false);p.setData(SpiritData.ENERGY,50f);
            }catch(Throwable t){close.run();throw t;}});
            h.runAfterDelay(44,()->{try{Fixtures.near(h,SpiritProgression.energy(p),50,"Human energy bypassed floor or drained surplus");h.succeed();}finally{close.run();}});
        }));
        return list;
    }
    private static CompoundTag food(ServerPlayer p){var t=new CompoundTag();p.getFoodData().addAdditionalSaveData(t);return t;}
    private static void vulnerable(ServerPlayer p){for(int i=0;i<61;i++)p.tick();p.invulnerableTime=0;}
    private static void complete(ServerPlayer p){var s=p.getData(SpiritData.ENERGY_STATE).regenerate;if(!Regenerate.active(p))return;s.began=p.level().getGameTime()-20;s.lastTick=Long.MIN_VALUE;Regenerate.tick(p);}
    private static RecoveryOrb orb(ServerPlayer p,boolean life){var e=new RecoveryOrb(OriEntities.RECOVERY_ORB.get(),p.level());var t=new CompoundTag();t.putBoolean("life",life);t.putInt("age",10);e.load(t);e.setPos(p.position().add(0,10,0));return e;}
    private static void add(Collection<TestFunction> tests,String name,BiConsumer<GameTestHelper,ServerPlayer> run){tests.add(new TestFunction("recovery","recovery."+name,"orimod_gametest:room",40,0,true,h->{try(var c=Fixtures.connectedPlayer(h)){var p=c.player();p.setData(SpiritData.FORM,true);try{run.accept(h,p);h.succeed();}finally{Regenerate.release(p);}}}));}
}
