package cn.wwl.orimod.gametest;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;
@GameTestHolder("orimod_gametest")
public final class FeatherWellGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        add(tests,"per_form_flight_wear_and_foreign_item",(h,p)->{
            var f=new ItemStack(Items.ELYTRA);p.setItemSlot(EquipmentSlot.CHEST,f);p.setOnGround(false);
            h.assertTrue(f.canElytraFly(p),"Human elytra denied");h.assertTrue(p.tryToStartFallFlying(),"Human flight denied");
            FeatherGlide.migrate(new PlayerTickEvent.Pre(p));h.assertTrue(p.isFallFlying(),"Human saved flight was converted");
            f.elytraFlightTick(p,19);h.assertValueEqual(f.getDamageValue(),1,"Human vanilla durability changed");
            p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,4);
            h.assertFalse(f.canElytraFly(p),"Spirit still grants FallFlying");
            f.setDamageValue(f.getMaxDamage()-1);h.assertTrue(FeatherGlide.permitted(p),"Broken feather stopped spirit glide");
            h.assertFalse(f.elytraFlightTick(p,19),"Spirit ran vanilla durability hook");h.assertValueEqual(f.getDamageValue(),f.getMaxDamage()-1,"Feather wore out");
            var other=new ItemStack(OriGameTests.OTHER_ELYTRA.get());h.assertTrue(other.canElytraFly(p),"Other ElytraItem lost its hook");
            p.setItemSlot(EquipmentSlot.CHEST,other);p.startFallFlying();FeatherGlide.migrate(new PlayerTickEvent.Pre(p));h.assertTrue(p.isFallFlying(),"Foreign flight was converted");
        });
        add(tests,"human_to_spirit_handoff_and_return_to_fall",(h,p)->{
            air(h,p);p.startFallFlying();p.setDeltaMovement(1.5,-.2,0);
            h.assertTrue(cn.wwl.orimod.spirit.SpiritForm.toggle(p),"Form switch failed");
            h.assertFalse(p.isFallFlying(),"Old flight retained");h.assertTrue(FeatherGlide.gliding(p),"No handoff grace");
            h.assertValueEqual(p.getDeltaMovement(),new Vec3(1.5,-.2,0),"Form switch changed velocity");
            h.assertTrue(FeatherGlide.velocity(p,Vec3.ZERO).x>1,"Handoff stopped forward speed");
            h.assertTrue(cn.wwl.orimod.spirit.SpiritForm.toggle(p),"Return to human failed");
            h.assertFalse(p.isFallFlying(),"Human return auto-started elytra");h.assertFalse(FeatherGlide.gliding(p),"Human kept feather glide");
            h.assertValueEqual(p.getDeltaMovement(),new Vec3(1.5,-.2,0),"Human return stopped velocity");
        });
        add(tests,"handoff_release_creative_and_skill_boundary",(h,p)->{
            air(h,p);p.setData(SpiritData.FORM,true);p.startFallFlying();FeatherGlide.startHandoff(p);
            FeatherGlide.input(p,true,true,false);FeatherGlide.input(p,true,false,false);
            h.assertFalse(FeatherGlide.migrating(p),"Release did not stop handoff");h.assertFalse(FeatherGlide.permitted(p),"Handoff granted permanent skill");
            p.setData(SpiritData.ABILITIES,4);h.assertTrue(FeatherGlide.permitted(p),"Skill denied");
            p.getAbilities().flying=true;h.assertFalse(FeatherGlide.permitted(p),"Creative flight overridden");
        });
        add(tests,"firework_human_consumes_spirit_preserves",(h,p)->{
            air(h,p);p.startFallFlying();var rocket=new ItemStack(Items.FIREWORK_ROCKET,3);p.setItemInHand(InteractionHand.MAIN_HAND,rocket);
            Items.FIREWORK_ROCKET.use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertValueEqual(rocket.getCount(),2,"Human rocket did not behave as vanilla");
            p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,4);FeatherGlide.startHandoff(p);
            Items.FIREWORK_ROCKET.use(h.getLevel(),p,InteractionHand.MAIN_HAND);h.assertValueEqual(rocket.getCount(),2,"Spirit spent rocket");
            h.assertTrue(p.getData(SpiritData.FEATHER_STATE).fireworkNotified,"Missing hint");
            p.setOnGround(true);h.assertFalse(FeatherGlide.refuseFirework(p),"Ground fireworks blocked");
        });
        add(tests,"updraft_lifts_spirit_feather",(h,p)->{
            air(h,p);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,4);
            var vent=p.blockPosition().below(2);h.getLevel().setBlock(vent,OriBlocks.UPDRAFT.get().defaultBlockState(),2);
            p.setDeltaMovement(0,-.01,0);h.assertTrue(FeatherGlide.velocity(p,Vec3.ZERO).y>0,"Feather not lifted");
        });
        for(boolean spirit:List.of(false,true))add(tests,"death_preserves_form_"+spirit,(h,p)->{
            p.setData(SpiritData.FORM,spirit);p.getInventory().add(new ItemStack(Items.DIAMOND));p.experienceLevel=8;
            var clone=Fixtures.player(h);clone.restoreFrom(p,false);
            h.assertValueEqual(SpiritData.isSpirit(clone),spirit,"Death changed form");
            h.assertTrue(clone.getInventory().isEmpty(),"Death preserved inventory");h.assertValueEqual(clone.experienceLevel,0,"Death preserved XP");
        });
        for(boolean blade:List.of(true,false))add(tests,"no_weapon_wear_"+blade,(h,p)->{
            var stack=new ItemStack(blade?OriItems.SPIRIT_BLADE.get():OriItems.SPIRIT_BOW.get());stack.setDamageValue(17);
            stack.hurtAndBreak(100,p,EquipmentSlot.MAINHAND);h.assertValueEqual(stack.getDamageValue(),17,"Energy weapon wore out or erased legacy damage");
            if(blade){stack.mineBlock(h.getLevel(),Blocks.STONE.defaultBlockState(),p.blockPosition(),p);h.assertValueEqual(stack.getDamageValue(),17,"Mining wore blade");}
        });
        add(tests,"well_selection_checks_discovery_dimension_range_and_safety",(h,p)->{
            var l=h.getLevel();var a=h.absolutePos(new BlockPos(3,2,4));var b=a.east(6);String dim=l.dimension().location().toString();
            for(var at:List.of(a,b)){l.setBlock(at,OriBlocks.GLADES_WELL.get().defaultBlockState(),2);l.setBlock(at.north().below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(at.north(),Blocks.AIR.defaultBlockState(),2);l.setBlock(at.north().above(),Blocks.AIR.defaultBlockState(),2);}
            p.setPos(Vec3.atBottomCenterOf(a.north()));var rewards=new HashMap<String,Integer>();rewards.put("discover/"+ChallengeSession.key(l.dimension(),a),1);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withRewards(rewards));
            h.assertFalse(SpiritWell.travel(p,a,b,dim),"Undiscovered well accepted");rewards.put("discover/"+ChallengeSession.key(l.dimension(),b),1);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withRewards(rewards));
            h.assertFalse(SpiritWell.travel(p,a,b,"minecraft:the_nether"),"Cross-dimension selection accepted");
            p.setPos(Vec3.atBottomCenterOf(a.west(8)));h.assertFalse(SpiritWell.travel(p,a,b,dim),"Remote source accepted");p.setPos(Vec3.atBottomCenterOf(a.north()));
            for(var at:BlockPos.betweenClosed(b.offset(-1,0,-1),b.offset(1,3,1)))if(!at.equals(b))l.setBlock(at,Blocks.STONE.defaultBlockState(),2);
            h.assertFalse(SpiritWell.travel(p,a,b,dim),"Blocked arrival accepted");
            l.setBlock(b.north(),Blocks.AIR.defaultBlockState(),2);l.setBlock(b.north().above(),Blocks.AIR.defaultBlockState(),2);
            h.assertTrue(SpiritWell.travel(p,a,b,dim),"Safe selected well failed");h.assertValueEqual(p.blockPosition(),b.north(),"Wrong selected destination");
        });
        return tests;
    }
    private static void air(GameTestHelper h,ServerPlayer p){p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));p.setPos(h.absoluteVec(new Vec3(5.5,5,5.5)));p.setOnGround(false);p.setDeltaMovement(0,-.2,0);p.fallDistance=10;}
    private static void add(Collection<TestFunction> tests,String name,BiConsumer<GameTestHelper,ServerPlayer> f){tests.add(new TestFunction("feather_well","feather_well."+name,"orimod_gametest:room",40,0,true,h->{try(var c=Fixtures.connectedPlayer(h)){f.accept(h,c.player());h.succeed();}}));}
}
