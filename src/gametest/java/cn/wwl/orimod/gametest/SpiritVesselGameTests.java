package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.item.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.OriEntities;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class SpiritVesselGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(boolean blade:List.of(false,true)) add(tests,"survival_click_"+(blade?"blade":"hand"),(h,c)->{
            var p=c.player();var pos=place(h,new BlockPos(6,2,5));p.setItemInHand(InteractionHand.MAIN_HAND,blade?new ItemStack(OriItems.SPIRIT_BLADE.get()):ItemStack.EMPTY);
            p.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.WEST,h.getLevel().getMaxBuildHeight(),0);
            drops(h,pos);
        });
        add(tests,"player_arrow_breaks_vessel",(h,c)->{
            var pos=place(h,new BlockPos(8,2,5));var arrow=new Arrow(h.getLevel(),c.player(),new ItemStack(Items.ARROW),null);
            arrow.setPos(Vec3.atBottomCenterOf(pos).add(-2,.3,0));arrow.setNoGravity(true);arrow.setDeltaMovement(2.5,0,0);
            try{arrow.tick();arrow.tick();drops(h,pos);}finally{arrow.discard();}
        });
        for(var source:List.of(CrackedRuin.Source.CHARGE_FLAME,CrackedRuin.Source.SMASH,CrackedRuin.Source.LIGHT_BURST,CrackedRuin.Source.SPEAR,CrackedRuin.Source.STOMP))
            add(tests,"ability_"+source.name().toLowerCase(Locale.ROOT),(h,c)->{
                var pos=place(h,new BlockPos(7,2,5));var p=c.player();
                switch(source) {
                    case CHARGE_FLAME -> ChargeFlame.release(p);
                    case SMASH -> {p.setYRot(-90);SpiritHammerItem.smash(p);}
                    case LIGHT_BURST -> {var orb=new LightBurst(OriEntities.LIGHT_BURST.get(),h.getLevel());orb.setOwner(p);orb.setPos(Vec3.atCenterOf(pos));orb.detonate();}
                    case SPEAR -> {
                        var spear=new SpiritSpear(OriEntities.SPIRIT_SPEAR.get(),h.getLevel());spear.setOwner(p);spear.setPos(Vec3.atBottomCenterOf(pos).add(-1,.3,0));spear.setDeltaMovement(2.2,0,0);
                        try{spear.tick();spear.tick();}finally{spear.discard();}
                    }
                    case STOMP -> {
                        var s=p.getData(SpiritData.MOVEMENT);s.active=true;s.mode=cn.wwl.orimod.movement.MoveMode.STOMP;s.lastInput=h.getLevel().getGameTime();
                        cn.wwl.orimod.movement.AirMovement.fall(new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p,4,1));
                    }
                    default -> throw new AssertionError(source);
                }
                drops(h,pos);
            });
        add(tests,"enemy_has_priority_over_visible_vessel",(h,c)->{
            var pos=place(h,new BlockPos(7,2,5));var zombie=new Zombie(h.getLevel());zombie.setNoAi(true);zombie.setNoGravity(true);zombie.setPos(c.player().position().add(0,0,2));h.getLevel().addFreshEntity(zombie);
            try {
                var s=c.player().getData(SpiritData.COOLDOWNS);s.flameReadyAt=0;float before=zombie.getHealth();SpiritFlame.tick(new PlayerTickEvent.Post(c.player()));
                h.assertTrue(zombie.getHealth()<before,"Enemy was not attacked");h.assertTrue(h.getLevel().getBlockState(pos).is(OriBlocks.SPIRIT_LIGHT_VESSEL),"Vessel stole enemy priority");
                zombie.discard();s.flameReadyAt=0;SpiritFlame.tick(new PlayerTickEvent.Post(c.player()));drops(h,pos);
            }finally{zombie.discard();}
        });
        add(tests,"flame_cannot_see_vessel_through_cracked_wall",(h,c)->{
            var pos=place(h,new BlockPos(8,2,5));
            for(int y=2;y<=4;y++)for(int z=4;z<=6;z++)h.setBlock(new BlockPos(7,y,z),OriBlocks.CRACKED_RUIN_WALL.get());
            c.player().getData(SpiritData.COOLDOWNS).flameReadyAt=0;SpiritFlame.tick(new PlayerTickEvent.Post(c.player()));
            h.assertTrue(h.getLevel().getBlockState(pos).is(OriBlocks.SPIRIT_LIGHT_VESSEL),"Spirit Flame reached sealed secret through a wall");
            for(int y=2;y<=4;y++)h.setBlock(new BlockPos(7,y,5),Blocks.AIR);
            SpiritFlame.tick(new PlayerTickEvent.Post(c.player()));drops(h,pos);
        });
        add(tests,"flame_uses_existing_six_block_radius",(h,c)->{
            c.player().setPos(c.player().position().add(0,.5,0));var edge=place(h,new BlockPos(11,2,5));var far=place(h,new BlockPos(12,2,5));
            var s=c.player().getData(SpiritData.COOLDOWNS);s.flameReadyAt=0;SpiritFlame.tick(new PlayerTickEvent.Post(c.player()));
            h.assertTrue(h.getLevel().isEmptyBlock(edge),"Six-block boundary excluded");
            s.flameReadyAt=0;SpiritFlame.tick(new PlayerTickEvent.Post(c.player()));h.assertTrue(h.getLevel().getBlockState(far).is(OriBlocks.SPIRIT_LIGHT_VESSEL),"Seven-block vessel targeted");
        });
        add(tests,"one_reward_and_once_line_even_for_repeated_same_tick_hits",(h,c)->{
            var pos=place(h,new BlockPos(6,2,5));h.assertTrue(SpiritLightVesselBlock.burst(c.player(),pos),"Initial hit refused");
            for(int i=0;i<5;i++)h.assertFalse(SpiritLightVesselBlock.burst(c.player(),pos),"Repeated hit paid again");drops(h,pos);
            var progress=c.player().getData(SpiritData.PROGRESS);h.assertFalse(progress.rewards().containsKey("line/first_vessel"),"Unsent line recorded");
            var s=c.player().getData(SpiritData.ENERGY_STATE);s.lastCompanionHintAt=Long.MIN_VALUE;
            h.assertTrue(CompanionLines.flush(c.player()),"Vessel line not sent");h.assertTrue(c.player().getData(SpiritData.PROGRESS).rewards().containsKey("line/first_vessel"),"Once record missing");
            SpiritLightVesselBlock.burst(c.player(),place(h,new BlockPos(6,2,7)));h.assertTrue(s.lines.pending==null,"Second vessel repeated line");
        });
        add(tests,"orbs_follow_existing_pickup_and_magnet_path",(h,c)->{
            var pos=place(h,new BlockPos(7,2,5));SpiritLightVesselBlock.burst(c.player(),pos);
            h.assertTrue(SpiritProgression.pullLight(c.player())>0,"R35 magnet did not pull vessel orbs");
            var orb=orbs(h,pos).getFirst();int before=c.player().getData(SpiritData.PROGRESS).light();
            SpiritProgression.pickup(new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre(c.player(),orb));
            h.assertTrue(c.player().getData(SpiritData.PROGRESS).light()==before+1 && orb.isRemoved(),"Vessel orb bypassed collectLight");
        });
        add(tests,"shape_light_and_no_item_loot",(h,c)->{
            var pos=place(h,new BlockPos(7,2,5));var state=h.getLevel().getBlockState(pos);
            h.assertTrue(state.getLightEmission(h.getLevel(),pos)==10,"Vessel light");Fixtures.near(h,state.getShape(h.getLevel(),pos).bounds().getYsize(),10/16.0,"Vessel height");
            h.assertTrue(net.minecraft.world.level.block.Block.getDrops(state,h.getLevel(),pos,null,c.player(),ItemStack.EMPTY).isEmpty(),"Vessel has block-item loot");
        });
        for(var kind:CrackedRuin.Kind.values())add(tests,"crack_hint_"+kind,(h,c)->{
            var pos=h.absolutePos(new BlockPos(7,2,5));h.getLevel().setBlock(pos,(kind==CrackedRuin.Kind.WALL?OriBlocks.CRACKED_RUIN_WALL:OriBlocks.CRACKED_RUIN_FLOOR).get().defaultBlockState(),3);
            CompanionHints.check(c.player());String id=kind==CrackedRuin.Kind.WALL?"cracked_wall":"cracked_floor";
            h.assertTrue(c.player().getData(SpiritData.PROGRESS).rewards().containsKey("hint/"+id),"Cracked hint missing");
        });
        return tests;
    }
    private static BlockPos place(GameTestHelper h,BlockPos local) {h.setBlock(local,OriBlocks.SPIRIT_LIGHT_VESSEL.get());return h.absolutePos(local);}
    private static List<ItemEntity> orbs(GameTestHelper h,BlockPos pos) {return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1),e->e.getItem().is(OriItems.SPIRIT_LIGHT));}
    private static void drops(GameTestHelper h,BlockPos pos) {
        h.assertTrue(h.getLevel().isEmptyBlock(pos),"Vessel not removed");int total=orbs(h,pos).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(total==SpiritLightVesselBlock.amount(h.getLevel().getSeed(),pos) && total>=25 && total<=40,"Wrong vessel total "+total);
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run) {
        tests.add(new TestFunction("spirit_vessel","spirit_vessel."+name,"orimod_gametest:room",40,0,true,h->{
            try(var c=Fixtures.connectedPlayer(h)) {CrackedRuinGameTests.prepare(c);run.accept(h,c);}h.succeed();
        }));
    }
}
