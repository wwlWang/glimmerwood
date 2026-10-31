package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_p5")
public final class P5IntegrityGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var block : BuiltInRegistries.BLOCK) {
            boolean functional = block instanceof ProtectedBlock || block instanceof GinsoVineBlock;
            boolean wood = block == OriBlocks.GINSO_WOOD.get() || block == OriBlocks.GREAT_SPIRIT_WOOD.get() || block == OriBlocks.GREAT_SPIRIT_ROOT.get();
            if (!functional && !wood) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            add(tests, "environment_" + id, h -> environment(h, block, functional), false);
        }
        add(tests,"platform_lit_and_assembled_fluid_states",h->{
            var pos=h.absolutePos(new BlockPos(5,3,5));
            for(var block:List.of(OriBlocks.SPIRIT_PLATFORM.get(),OriBlocks.SANCTUARY_PART.get(),OriBlocks.GLADES_WELL.get(),OriBlocks.SPIRIT_SAND.get()))
                for(var state:block.getStateDefinition().getPossibleStates())for(var fluid:List.of(Fluids.WATER,Fluids.LAVA)) {
                    h.assertFalse(((LiquidBlockContainer)block).canPlaceLiquid(null,h.getLevel(),pos,state,fluid),"Flow accepted "+state);
                    h.assertFalse(state.canBeReplaced(fluid),"Bucket replacement accepted "+state);
                    h.assertValueEqual(state.getPistonPushReaction(),PushReaction.BLOCK,"State allows piston "+state);
                }
        },true);
        for (var type : List.of(EntityType.ZOMBIFIED_PIGLIN,EntityType.ENDERMAN,EntityType.WOLF,EntityType.IRON_GOLEM,EntityType.VILLAGER,EntityType.ZOMBIE))
            for (String path : List.of("flame","burst","orphan_burst","stomp","flash"))
                add(tests,"bystander_"+BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()+"_"+path,h->combat(h,type,path),true);
        for(var type:List.of(EntityType.ZOMBIFIED_PIGLIN,EntityType.ENDERMAN,EntityType.WOLF,EntityType.IRON_GOLEM))
            add(tests,"provoked_"+BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(),h->{
                var p=Fixtures.player(h);var mob=(Mob)type.create(h.getLevel());mob.setTarget(p);
                h.assertTrue(CombatTargets.canHit(p,mob),"Attacking neutral excluded");
                h.assertFalse(CombatTargets.canHitWithoutOwner(mob),"Orphan effect inferred neutral anger");mob.discard();
            },true);
        for(String mode:List.of("nbt","death","dimension"))add(tests,"persistent_cooldowns_"+mode,h->cooldowns(h,mode),true);
        add(tests,"cooldowns_use_one_dimension_clock",h->{
            var p=Fixtures.player(h);var nether=p.server.getLevel(net.minecraft.world.level.Level.NETHER);
            var other=new net.neoforged.neoforge.common.util.FakePlayer(nether,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"clock-test"));
            h.assertValueEqual(SpiritCooldowns.now(p),SpiritCooldowns.now(other),"Dimension-specific cooldown clock");
            p.getData(SpiritData.COOLDOWNS).wellReadyAt=SpiritCooldowns.now(p)+1200;
            other.restoreFrom(p,true);other.setData(SpiritData.ENERGY,0f);
            h.assertFalse(SpiritWell.refill(other),"Dimension clone reset refill");
        },true);
        for(String action:List.of("charge_wall","stomp_floor","purify_core","keystone_door"))add(tests,"legal_"+action,h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var p=c.player();var l=h.getLevel();var at=p.blockPosition().east(2);
                p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,java.util.Arrays.stream(Ability.values()).mapToInt(v->v.bit).reduce(0,(a,b)->a|b));p.setData(SpiritData.ENERGY,100f);
                switch(action){
                    case "charge_wall"->{l.setBlock(at,OriBlocks.CRACKED_RUIN_WALL.get().defaultBlockState(),2);ChargeFlame.release(p);}
                    case "stomp_floor"->{l.setBlock(at,OriBlocks.CRACKED_RUIN_FLOOR.get().defaultBlockState(),2);p.setPos(Vec3.atBottomCenterOf(at.above()));p.getData(SpiritData.MOVEMENT).mode=MoveMode.STOMP;p.getData(SpiritData.MOVEMENT).active=true;p.getData(SpiritData.MOVEMENT).lastInput=l.getGameTime();AirMovement.fall(new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p,5,1));}
                    case "purify_core"->{l.setBlock(at,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);for(int i=0;i<4;i++)BlightInteractions.hit(p,at,BlightRules.Source.SMASH);}
                    case "keystone_door"->{
                        l.setBlock(at,OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(),2);
                        var keys=new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.KEYSTONE.get(),2);
                        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,keys);
                        for(int i=0;i<2;i++)l.getBlockState(at).useItemOn(keys,l,p,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at),Direction.WEST,at,false));
                        h.assertTrue(keys.isEmpty(),"Door did not consume two keys");
                    }
                }
                h.assertTrue(l.isEmptyBlock(at),"Environmental protection blocked legal interaction: "+action);
            }
        },true);
        for(boolean running:List.of(false,true))add(tests,"well_escape_"+(running?"running":"prepare"),h->escape(h,running),true);
        add(tests,"peaceful_altar_does_not_activate",h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var l=h.getLevel();var difficulty=l.getDifficulty();var at=h.absolutePos(new BlockPos(4,3,4));
                try{l.setBlock(at,OriBlocks.GLOOM_ALTAR.get().defaultBlockState(),2);l.getServer().setDifficulty(Difficulty.PEACEFUL,true);
                    h.assertTrue(GloomAltarBlock.summon(c.player(),at)==null,"Peaceful boss created");
                    var be=(SanctuaryBlockEntity)l.getBlockEntity(at);h.assertFalse(be.activated(),"Peaceful altar consumed");
                    h.assertValueEqual(be.phase(),SanctuaryBlockEntity.Phase.DORMANT,"Peaceful altar phase");
                }finally{l.getServer().setDifficulty(difficulty,true);}
            }
        },true);
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run,boolean immediate){
        tests.add(new TestFunction("p5_integrity","p5_integrity."+name,"orimod_p5:platform_room",160,0,true,h->{run.accept(h);if(immediate)h.succeed();}));
    }
    private static void environment(GameTestHelper h,Block block,boolean functional){
        var l=h.getLevel();var state=block.defaultBlockState();var p=Fixtures.player(h);
        for(var s:block.getStateDefinition().getPossibleStates()){
            h.assertValueEqual(s.getPistonPushReaction(),PushReaction.BLOCK,"Push reaction "+s);
            h.assertTrue(block.getExplosionResistance()>=3600000,"Blast resistance "+s);
        }
        // Three independent cells: water/lava flow sideways; a real piston; a real TNT explosion.
        var samples=new ArrayList<BlockPos>();
        if(functional)for(int i=0;i<2;i++){
            var at=h.absolutePos(new BlockPos(3+i*6,3,3));samples.add(at);
            for(int x=-1;x<=2;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                l.setBlock(at.offset(x,y,z),Blocks.OBSIDIAN.defaultBlockState(),2);
            l.setBlock(at,state,2);l.setBlock(at.east(),(i==0?Blocks.WATER:Blocks.LAVA).defaultBlockState(),3);
        }
        var pushed=h.absolutePos(new BlockPos(4,3,10));samples.add(pushed);
        l.setBlock(pushed.above(),Blocks.OBSIDIAN.defaultBlockState(),2);l.setBlock(pushed,state,2);
        l.setBlock(pushed.west(),Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.EAST),3);
        l.setBlock(pushed.west(2),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        var blasted=h.absolutePos(new BlockPos(10,3,10));samples.add(blasted);
        l.setBlock(blasted.above(),Blocks.OBSIDIAN.defaultBlockState(),2);l.setBlock(blasted,state,2);
        var tnt=new PrimedTnt(l,blasted.getX()+1.5,blasted.getY()+.2,blasted.getZ()+.5,null);tnt.setFuse(4);tnt.setNoGravity(true);tnt.setDeltaMovement(Vec3.ZERO);l.addFreshEntity(tnt);
        h.runAfterDelay(65,()->{
            for(var at:samples)h.assertTrue(l.getBlockState(at).is(block),"Environment erased "+block+" at "+at);
            h.assertFalse(l.getBlockState(pushed.west()).getValue(PistonBaseBlock.EXTENDED),"Piston moved protected block");
            // Use vanilla START/STOP mining rather than destroyBlock (which deliberately bypasses hardness).
            var mined=h.absolutePos(new BlockPos(7,3,7));l.setBlock(mined.above(),Blocks.OBSIDIAN.defaultBlockState(),2);l.setBlock(mined,state,2);p.setPos(Vec3.atBottomCenterOf(mined.south()));
            l.setBlock(mined.south().below(),Blocks.STONE.defaultBlockState(),2);p.setOnGround(true);
            if(block instanceof GinsoVineBlock)p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS));
            if(block==OriBlocks.SPIRIT_LIGHT_VESSEL.get()) {
                // Its attack hook deliberately breaks the reward vessel, even with hardness -1.
                h.assertTrue(SpiritLightVesselBlock.burst(p,mined),"Protected vessel lost legal burst");
            } else {
                p.gameMode.handleBlockBreakAction(mined,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.SOUTH,l.getMaxBuildHeight(),0);
            }
            if(block!=OriBlocks.SPIRIT_LIGHT_VESSEL.get()){
                if(state.getDestroySpeed(l,mined)<0)h.assertTrue(l.getBlockState(mined).is(block),"Unobtainable block mined");
                else {
                    h.assertTrue(state.getDestroyProgress(p,l,mined)>0,"Obtainable block made unbreakable");
                    for(int tick=0;tick<65;tick++)p.gameMode.tick();
                    p.gameMode.handleBlockBreakAction(mined,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,Direction.SOUTH,l.getMaxBuildHeight(),1);
                    h.assertTrue(l.isEmptyBlock(mined),"Obtainable block could not be mined");
                    h.assertFalse(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(mined).inflate(2),e->e.getItem().is(block.asItem())).isEmpty(),"Mined block did not drop itself");
                }
            }
            h.succeed();
        });
    }
    private static void combat(GameTestHelper h,EntityType<?> type,String path){
        var p=Fixtures.player(h);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,Ability.FLASH.bit);p.setData(SpiritData.ENERGY,100f);
        var mob=(Mob)type.create(h.getLevel());mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(p.position().add(0,0,1.5));h.getLevel().addFreshEntity(mob);
        float hp=mob.getHealth();
        try{
            switch(path){
                case "flame"->SpiritFlame.tick(new PlayerTickEvent.Post(p));
                case "burst","orphan_burst"->{var b=new LightBurst(OriEntities.LIGHT_BURST.get(),h.getLevel());b.setPos(p.position());if(path.equals("burst"))b.setOwner(p);b.detonate();}
                case "stomp"->{var s=p.getData(SpiritData.MOVEMENT);s.mode=MoveMode.STOMP;s.stompUntil=h.getLevel().getGameTime()+80;s.stompStartY=p.getY()+5;AirMovement.tickOwned(p,s,h.getLevel().getGameTime(),true);}
                case "flash"->{h.assertTrue(Flash.keepAlive(p),"Flash rejected");var s=p.getData(SpiritData.ENERGY_STATE);s.flashActiveSince=h.getLevel().getGameTime()-19;Flash.tick(new PlayerTickEvent.Post(p));}
            }
            if(type==EntityType.ZOMBIE)h.assertTrue(mob.getHealth()<hp,"Hostile not damaged by "+path);
            else {h.assertValueEqual(mob.getHealth(),hp,"Calm mob damaged by "+path);h.assertTrue(mob.getTarget()==null,"Calm mob acquired target");if(mob instanceof NeutralMob n)h.assertFalse(n.isAngryAt(p),"Calm mob angered");}
        }finally{Flash.stop(p);mob.discard();}
    }
    private static void cooldowns(GameTestHelper h,String mode){
        var p=Fixtures.player(h);p.setData(SpiritData.ENERGY,0f);h.assertTrue(SpiritWell.refill(p),"Initial refill refused");
        var original=p.getData(SpiritData.COOLDOWNS);original.spearReadyAt=SpiritCooldowns.now(p)+24;original.burstReadyAt=SpiritCooldowns.now(p)+40;
        p.getData(SpiritData.ENERGY_STATE).chargeFlameUntil=Long.MAX_VALUE;
        var clone=Fixtures.player(h);
        if(mode.equals("nbt")){var tag=p.saveWithoutId(new CompoundTag());clone.load(tag);}else clone.restoreFrom(p,mode.equals("dimension"));
        clone.setData(SpiritData.ENERGY,0f);
        var copied=clone.getData(SpiritData.COOLDOWNS);
        h.assertValueEqual(copied.wellReadyAt,original.wellReadyAt,"Well deadline lost");h.assertValueEqual(copied.spearReadyAt,original.spearReadyAt,"Spear deadline lost");
        h.assertTrue(copied!=original,"Clone aliases cooldowns");h.assertFalse(SpiritWell.refill(clone),"Reload/clone bypassed refill");
        h.assertValueEqual(clone.getData(SpiritData.ENERGY_STATE).chargeFlameUntil,Long.MIN_VALUE,"Active cast persisted");
        clone.setData(SpiritData.ABILITIES,Ability.LIGHT_BURST.bit);SpiritSkills.tick(clone);
        h.assertTrue(clone.getData(SpiritData.SKILL_READY).contains(copied.burstReadyAt),"Derived cooldown UI missing");
        copied.wellReadyAt=SpiritCooldowns.now(clone);h.assertTrue(SpiritWell.refill(clone),"Deadline never expires");
    }
    private static void escape(GameTestHelper h,boolean running){
        try(var c=Fixtures.connectedPlayer(h)){
            var p=c.player();var l=h.getLevel();var heart=h.absolutePos(new BlockPos(4,5,4));var piece=new GinsoPiece(heart.below(5),115117,0,0);
            l.setBlock(heart,OriBlocks.GINSO_WATER_HEART.get().defaultBlockState(),2);p.setData(SpiritData.FORM,true);p.refreshDimensions();p.setData(SpiritData.ABILITIES,GinsoClimbEscape.REQUIRED.stream().mapToInt(a->a.bit).reduce(0,(a,b)->a|b));
            p.setPos(Vec3.atBottomCenterOf(piece.point(piece.escapeSafe())));p.setOnGround(true);
            var from=p.blockPosition().east();var to=from.east(5);var rewards=new HashMap<String,Integer>();
            for(var at:List.of(from,to)){l.setBlock(at,OriBlocks.GLADES_WELL.get().defaultBlockState(),2);rewards.put("discover/"+ChallengeSession.key(l.dimension(),at),1);}
            p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withRewards(rewards));
            h.assertTrue(SpiritWell.open(p,from),"Well unavailable before escape");
            try{
                h.assertTrue(GinsoEscape.start(p,piece),"Escape fixture refused");
                if(running)for(int i=0;i<60;i++)GinsoEscape.tick(p);
                h.assertValueEqual(GinsoEscape.session(p).phase,running?GinsoEscape.Phase.RUNNING:GinsoEscape.Phase.PREPARE,"Escape phase");
                var before=p.position();h.assertFalse(SpiritWell.open(p,from),"Escape opened travel screen");
                h.assertFalse(SpiritWell.travel(p,from,to,l.dimension().location().toString()),"Stale screen bypassed escape");
                h.assertValueEqual(p.position(),before,"Denied travel moved player");h.assertTrue(GinsoEscape.active(p),"Denied travel failed escape");
            }finally{GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);}
        }
    }
}
