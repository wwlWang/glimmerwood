package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

/** Native aiStep/travel/collision on both forms. Ori adapters are called explicitly as the local controller does. */
@GameTestHolder("orimod_p5")
public final class P5MotionGameTests {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests,"speed_attributes_exclude_sprint",h->{
            var p=player(h,true,0); var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
            p.setSprinting(true); near(h,SpiritPhysics.speedMultiplier(p),1,1e-7,"sprint counted twice");
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,1000,2));
            near(h,SpiritPhysics.speedMultiplier(p),1.6,1e-6,"Speed III");
            p.removeAllEffects();p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1000,2));
            near(h,SpiritPhysics.speedMultiplier(p),.55,1e-6,"Slowness III");
            p.removeAllEffects();speed.setBaseValue(0);near(h,SpiritPhysics.speedMultiplier(p),.15,0,"zero floor");
            speed.setBaseValue(.1);p.setSprinting(false);
            speed.addTransientModifier(new AttributeModifier(ResourceLocation.parse("orimod:test_speed"),.2,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            near(h,SpiritPhysics.speedMultiplier(p),1.2,1e-7,"other modifier lost");
        });
        for(double gravity:List.of(.04,.08,.16))for(boolean slow:List.of(false,true))add(tests,"gravity_"+gravity+"_slow_"+slow,h->{
            var human=player(h,false,60);var spirit=player(h,true,60);
            for(var p:List.of(human,spirit)){p.getAttribute(Attributes.GRAVITY).setBaseValue(gravity);if(slow)p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,1000));p.setDeltaMovement(0,-.2,0);}
            double start=human.getY();for(int i=0;i<20;i++){
                step(human,false);step(spirit,true);
                if(i==3 && !slow)h.assertTrue(spirit.getY()<human.getY(),"early Ori descent should retain heavier feel");
            }
            double hd=start-human.getY(),sd=start-spirit.getY();
            if(slow)near(h,sd,hd,1e-6,"slow fall bypasses heavy descent");
            else {h.assertTrue(sd<=20.5,"ordinary fall terminal lost");h.assertTrue(sd/hd<1.3,"gravity double applied");}
            LOG.info("P5_G gravity={} slow={} humanFall={} spiritFall={}",gravity,slow,hd,sd);
        });
        for(int pitch:List.of(30,60,80))add(tests,"riptide_native_full_flight_"+pitch,h->riptide(h,pitch));
        for(double gravity:List.of(.04,.08,.16))for(String action:List.of("drop","stomp","hammer"))add(tests,"slime_"+action+"_gravity_"+gravity,h->slime(h,action,gravity));
        for(var block:List.of(Blocks.STONE,Blocks.ICE,Blocks.BLUE_ICE))add(tests,"surface_"+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath(),h->surface(h,block));
        for(boolean boost:List.of(false,true))add(tests,"jump_attribute_"+boost,h->{
            var p=player(h,true,0);floor(h,Blocks.STONE,5);p.setOnGround(true);
            if(boost)p.addEffect(new MobEffectInstance(MobEffects.JUMP,1000,2));
            double base=p.getY(),peak=base;p.jumpFromGround();p.setOnGround(false);
            for(int t=0;t<100;t++){p.tickCount++;SpiritPhysics.prepareVertical(p,true,false,true,Double.NEGATIVE_INFINITY);p.aiStep();peak=Math.max(peak,p.getY());}
            near(h,peak-base,boost?4.115:1.623,.04,"held jump regression");
        });
        add(tests,"bounce_skill_handoff_and_late_lease",h->{
            var p=player(h,true,30);var m=p.getData(SpiritData.MOVEMENT);
            h.assertTrue(SlimeBounce.receiveStomp(p,p.getY(),123),"first stomp rejected");
            p.setDeltaMovement(0,.8,0);SlimeBounce.bounced(p);
            h.assertTrue(SlimeBounce.active(p),"bounce not active");
            for(var mode:List.of(MoveMode.IDLE,MoveMode.STOMP_LAND)){
                SlimeBounce.takeover(m,mode);h.assertTrue(SlimeBounce.active(p),"landing receipt cleared bounce");
            }
            h.assertFalse(SlimeBounce.receiveStomp(p,p.getY(),123),"late lease replaced bounce");
            for(var mode:List.of(MoveMode.DASH,MoveMode.BASH_AIM,MoveMode.BASH_LAUNCH,MoveMode.GRAPPLE,
                    MoveMode.GLIDE,MoveMode.LAUNCH,MoveMode.WALL_JUMP)){
                p.setDeltaMovement(0,.8,0);SlimeBounce.bounced(p);SlimeBounce.takeover(m,mode);
                h.assertFalse(SlimeBounce.active(p),"new skill retained bounce gravity: "+mode);
                h.assertFalse(SlimeBounce.receiveStomp(p,p.getY(),123),"late lease revived after takeover");
            }
            h.assertTrue(SlimeBounce.receiveStomp(p,p.getY(),124),"new stomp rejected");
        });
        add(tests,"deep_breath_lifecycle",h->{
            var p=player(h,true,0);p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withOwned(Shard.DEEP_BREATH.bit).withEquipped(Shard.DEEP_BREATH.bit));
            DeepBreath.update(p);near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"pre-unlock swim");
            p.setData(SpiritData.ABILITIES,Ability.WATER_DASH.bit);DeepBreath.update(p);DeepBreath.update(p);
            near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1.25,0,"one modifier only");
            p.setData(SpiritData.FORM,false);DeepBreath.update(p);near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"human modifier leak");
            p.setData(SpiritData.FORM,true);DeepBreath.update(p);
            DeepBreath.logout(new PlayerEvent.PlayerLoggedOutEvent(p));near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"logout");
            DeepBreath.login(new PlayerEvent.PlayerLoggedInEvent(p));near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1.25,0,"relogin rebuild");
            DeepBreath.death(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p,p.damageSources().generic()));
            near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"death cleanup");
            DeepBreath.update(p);var clone=player(h,true,0);DeepBreath.clone(new PlayerEvent.Clone(clone,p,true));
            near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"retired life");near(h,clone.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"new life starts clean");
            p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY);DeepBreath.update(p);near(h,p.getAttributeValue(NeoForgeMod.SWIM_SPEED),1,0,"unequip");
        });
        add(tests,"deep_breath_dynamic_description",h->{var p=player(h,false,0);
            h.assertTrue(Shard.DEEP_BREATH.description(p).equals(Shard.DEEP_BREATH.description()),"pre-unlock text");
            p.setData(SpiritData.ABILITIES,Ability.WATER_DASH.bit);
            h.assertFalse(Shard.DEEP_BREATH.description(p).equals(Shard.DEEP_BREATH.description()),"post-unlock text remains air");
        });
        return tests;
    }
    private static ServerPlayer player(GameTestHelper h,boolean spirit,double height){
        var p=Fixtures.player(h);p.setData(SpiritData.FORM,spirit);p.setInvulnerable(true);p.refreshDimensions();
        var base=h.absolutePos(new BlockPos(6,80,6));p.setPos(base.getX()+.5,base.getY()+height,base.getZ()+.5);
        p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);p.setYRot(0);p.setSpeed(.1f);return p;
    }
    private static void step(ServerPlayer p,boolean ori){
        p.tickCount++;
        if(ori && !VanillaMotion.riptide(p))SpiritPhysics.prepareVertical(p,false,false,true,Double.NEGATIVE_INFINITY);
        p.xxa=p.zza=0;p.aiStep();
    }
    private static void floor(GameTestHelper h,Block block,int length){
        var at=h.absolutePos(new BlockPos(6,79,6));
        for(int x=-2;x<=2;x++)for(int z=-2;z<length;z++)h.getLevel().setBlock(at.offset(x,0,z),block.defaultBlockState(),2);
    }
    private static void surface(GameTestHelper h,Block block){
        floor(h,block,50);var human=player(h,false,0);var spirit=player(h,true,0);
        human.setOnGround(true);spirit.setOnGround(true);human.setSprinting(true);human.setSpeed((float)human.getAttributeValue(Attributes.MOVEMENT_SPEED));
        float friction=block.defaultBlockState().getFriction(h.getLevel(),human.blockPosition().below(),human),drag=friction*.91f;
        double hr=0,sr=0,hs=0,ss=0;
        for(int t=0;t<160;t++){
            human.tickCount++;human.zza=t<60?1:0;human.xxa=0;double h0=human.getZ();human.aiStep();
            var v=spirit.getDeltaMovement();var wish=t<60?new Vec3(0,0,1):Vec3.ZERO;
            var flat=new Vec3(v.x,0,v.z);
            flat=FeelTuning.slippery(friction)?FeelTuning.slipperyHorizontal(flat,wish,1,SpiritPhysics.speedMultiplier(spirit),friction)
                    :FeelTuning.horizontal(flat.scale(1/drag),wish,true,1,1,SpiritPhysics.speedMultiplier(spirit));
            spirit.setDeltaMovement(flat.x,v.y,flat.z);double s0=spirit.getZ();step(spirit,true);
            if(t==59){hr=human.getZ();sr=spirit.getZ();hs=hr-h0;ss=sr-s0;}
        }
        double hc=human.getZ()-hr,sc=spirit.getZ()-sr;
        if(block==Blocks.STONE){near(h,ss,.32,1e-7,"stone speed regression");near(h,sc,0,0,"stone braking regression");}
        else{h.assertTrue(sc>=hc*.7,"ice coast too short "+sc+" vs "+hc);h.assertTrue(ss<=.320001,"ice speed cap");}
        LOG.info("P5_G surface={} humanSpeed={} spiritSpeed={} humanCoast={} spiritCoast={}",block,hs,ss,hc,sc);
    }
    private static void riptide(GameTestHelper h,int pitch){
        floor(h,Blocks.STONE,45);var human=player(h,false,1.2);var spirit=player(h,true,1.2);
        double angle=Math.toRadians(pitch);
        var impulse=new Vec3(0,3*Math.sin(angle),3*Math.cos(angle));
        for(var p:List.of(human,spirit)){p.setDeltaMovement(impulse);p.startAutoSpinAttack(20,8,new ItemStack(Items.TRIDENT));}
        near(h,spirit.getDeltaMovement().distanceTo(human.getDeltaMovement()),0,0,"first impulse differs");
        h.assertTrue(VanillaMotion.riptide(spirit),"start hook not armed");
        h.assertFalse(SpiritMovement.canMove(spirit),"Ori controller stole riptide");
        double start=human.getZ(),postHuman=0,postSpirit=0;boolean landed=false;
        for(int i=0;i<180;i++){
            step(human,false);step(spirit,true);
            if(i==20){postHuman=human.getZ();postSpirit=spirit.getZ();h.assertTrue(VanillaMotion.riptide(spirit),"post-spin flight lost");}
            if(human.onGround())landed=true;
        }
        double hd=human.getZ()-start,sd=spirit.getZ()-start;
        h.assertTrue(landed,"flight never landed");near(h,sd,hd,hd*.1,"full riptide range");
        near(h,spirit.getZ()-postSpirit,human.getZ()-postHuman,.01,"post-spin inertia");
        h.assertFalse(VanillaMotion.riptide(spirit),"landing did not release ownership");
        LOG.info("P5_G riptide pitch={} human={} spirit={} postSpinHuman={} postSpinSpirit={}",pitch,hd,sd,human.getZ()-postHuman,spirit.getZ()-postSpirit);
    }
    private static void slime(GameTestHelper h,String action,double gravity){
        floor(h,Blocks.SLIME_BLOCK,5);double height=action.equals("drop")?3:30;
        var human=player(h,false,height);var spirit=player(h,true,height);double base=human.getY()-height;
        for(var p:List.of(human,spirit))p.getAttribute(Attributes.GRAVITY).setBaseValue(gravity);
        if(!action.equals("drop")){
            if(action.equals("hammer"))SlimeBounce.observeHammer(spirit,new cn.wwl.orimod.combat.CombatView(1,"hammer_slam",0,0,spirit.getY()));
            else SlimeBounce.beginSlam(spirit,spirit.getY());
        }
        int hb=0,sb=0;double hp=0,sp=0,lastH=0,lastS=0;
        for(int t=0;t<360;t++){
            if(!action.equals("drop") && sb==0)spirit.setDeltaMovement(cn.wwl.orimod.combat.HammerSlam.velocity(spirit.getDeltaMovement()));
            step(human,false);
            // Slam owns descent, exactly as the controller; after collision SlimeBounce owns vertical travel.
            step(spirit,action.equals("drop")||sb>0);
            double hy=human.getDeltaMovement().y,sy=spirit.getDeltaMovement().y;
            if(hy>0&&lastH<=0)hb++;if(sy>0&&lastS<=0)sb++;
            if(hb>0)hp=Math.max(hp,human.getY()-base);if(sb>0)sp=Math.max(sp,spirit.getY()-base);
            lastH=hy;lastS=sy;
        }
        h.assertTrue(hb>0&&sb>0,"no slime collision");
        h.assertTrue(human.onGround()&&spirit.onGround(),"bounce failed to settle in 360 ticks");
        near(h,spirit.getY(),base,1e-6,"spirit stopped above slime");
        h.assertTrue(sp<=height+.001,"bounce exceeded real start height");
        if(!action.equals("drop"))near(h,sp,hp,Math.max(.25,hp*.1),"slam not equivalent to native fall");
        LOG.info("P5_G slime action={} gravity={} humanBounces={} spiritBounces={} humanPeak={} spiritPeak={} finalY={}",action,gravity,hb,sb,hp,sp,spirit.getY()-base);
    }
    private static void near(GameTestHelper h,double actual,double expected,double tolerance,String message){h.assertTrue(Math.abs(actual-expected)<=tolerance,message+": "+actual+" vs "+expected);}
    private static void add(List<TestFunction> out,String name,Consumer<GameTestHelper> body){out.add(new TestFunction("p5_motion","p5_motion."+name,"orimod_p5:platform_room",100,0,true,h->{body.accept(h);h.succeed();}));}
}
