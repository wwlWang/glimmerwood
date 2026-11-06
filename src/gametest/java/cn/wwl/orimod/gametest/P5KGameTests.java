package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.item.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_p5")
public final class P5KGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var out = new ArrayList<TestFunction>();
        for (boolean creative : List.of(false, true)) add(out,"energy_"+creative,h->{
            var p=player(h);p.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);p.setData(SpiritData.ENERGY,0f);
            long spent=p.getData(SpiritData.ENERGY_STATE).lastSpent;
            h.assertTrue(SpiritProgression.spendEnergy(p,12)==creative,"Wrong affordability");
            Fixtures.near(h,SpiritProgression.energy(p),0,"Energy changed");
            h.assertValueEqual(p.getData(SpiritData.ENERGY_STATE).lastSpent,spent,"Free/refused action delayed regeneration");
            for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})h.assertFalse(SpiritProgression.spendEnergy(p,invalid),"Invalid amount accepted");
            for(var item:List.of(OriItems.SPIRIT_BOW.get(),OriItems.SPIRIT_SPEAR.get(),OriItems.SPIRIT_HAMMER.get())) {
                h.assertTrue(SpiritWeaponUse.blocked(p,new ItemStack(item))!=creative,"Client/server precheck disagrees");
                p.getCooldowns().addCooldown(item,30);h.assertTrue(SpiritWeaponUse.blocked(p,new ItemStack(item)),"Creative bypassed cooldown");
            }
        });
        for(boolean shard:List.of(false,true))for(boolean crouch:List.of(false,true))for(int energy:List.of(12,20))
            add(out,"splinter_"+shard+"_crouch_"+crouch+"_energy_"+energy,h->{
                var p=player(h);p.setShiftKeyDown(crouch);p.setData(SpiritData.ENERGY,(float)energy);
                if(shard)p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withOwned(Shard.SPLINTER.bit).withEquipped(Shard.SPLINTER.bit));
                var bow=new ItemStack(OriItems.SPIRIT_BOW.get());p.setItemInHand(InteractionHand.MAIN_HAND,bow);
                var before=h.getLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(5)).size();
                h.assertTrue(SpiritCombat.beginBow(p,bow),"Bow refused");p.getData(SpiritData.COMBAT).startedAt-=20;
                SpiritCombat.releaseBow(p,bow);
                var arrows=h.getLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(5),a->a.getOwner()==p);
                h.assertValueEqual(arrows.size(),shard&&energy==20?3:1,"Volley still depends on crouch");
                Fixtures.near(h,SpiritProgression.energy(p),energy-(shard&&energy==20?20:12),"Volley charge");arrows.forEach(Entity::discard);
            });
        add(out,"creative_zero_bow_and_spear_retain_cooldowns",h->{
            var p=player(h);p.setGameMode(GameType.CREATIVE);p.setData(SpiritData.ENERGY,0f);
            p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withOwned(Shard.SPLINTER.bit).withEquipped(Shard.SPLINTER.bit));
            var bow=new ItemStack(OriItems.SPIRIT_BOW.get());p.setItemInHand(InteractionHand.MAIN_HAND,bow);
            h.assertTrue(SpiritCombat.beginBow(p,bow),"Creative bow refused");p.getData(SpiritData.COMBAT).startedAt-=20;SpiritCombat.releaseBow(p,bow);
            var arrows=h.getLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(5),a->a.getOwner()==p);
            h.assertValueEqual(arrows.size(),3,"Creative Splinter refused");arrows.forEach(Entity::discard);
            h.assertFalse(SpiritCombat.canUseBow(p),"Bow cooldown lost");
            var spear=SpiritSpear.thrown(p);h.assertTrue(spear!=null,"Creative spear refused");spear.discard();
            h.assertTrue(SpiritSpear.thrown(p)==null,"Spear cooldown lost");Fixtures.near(h,SpiritProgression.energy(p),0,"Creative paid energy");
        });
        for(var type:List.of(EntityType.COW,EntityType.PIGLIN,EntityType.SPIDER,EntityType.VILLAGER))
            add(out,"direct_and_collateral_"+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(),h->{
                var p=player(h);var mob=(LivingEntity)type.create(h.getLevel());mob.setPos(p.position().add(0,0,2));h.getLevel().addFreshEntity(mob);
                try {h.assertTrue(CombatTargets.canHitDirect(p,mob),"Direct neutral/passive target rejected");h.assertFalse(CombatTargets.canHit(p,mob),"Bystander protection lost");
                    var spear=new SpiritSpear(OriEntities.SPIRIT_SPEAR.get(),h.getLevel());spear.setOwner(p);float health=mob.getHealth();h.assertTrue(spear.pierce(mob),"Spear direct failed");h.assertTrue(mob.getHealth()<health,"No direct damage");h.assertTrue(mob.getLastHurtByMob()==p,"Vanilla retaliation lost");spear.discard();
                } finally {mob.discard();}
            });
        add(out,"blade_primary_cow_and_cow_bystander",h->{
            var p=player(h);var target=EntityType.COW.create(h.getLevel());var bystander=EntityType.COW.create(h.getLevel());
            target.setPos(p.position().add(0,0,2));bystander.setPos(p.position().add(1.2,0,1.8));
            h.getLevel().addFreshEntity(target);h.getLevel().addFreshEntity(bystander);
            try {aim(p,target.getBoundingBox().getCenter());h.assertTrue(CombatTargets.aimed(p,3)==target,"Fixture ray missed cow");
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_BLADE.get()));SpiritCombat.attack(p);
                var s=p.getData(SpiritData.COMBAT);s.startedAt=p.level().getGameTime()-SpiritCombat.windup(1);
                SpiritCombat.tick(new PlayerTickEvent.Post(p));h.assertTrue(target.getHealth()<target.getMaxHealth(),"Primary cow protected");Fixtures.near(h,bystander.getHealth(),bystander.getMaxHealth(),"Collateral cow hurt");
            }finally{SpiritCombat.cancel(p,"test");target.discard();bystander.discard();}
        });
        add(out,"hammer_primary_cow_and_cow_bystander",h->{
            var p=player(h);p.setOnGround(true);var target=EntityType.COW.create(h.getLevel());var bystander=EntityType.COW.create(h.getLevel());
            target.setPos(p.position().add(0,0,2));bystander.setPos(p.position().add(1.2,0,1.8));h.getLevel().addFreshEntity(target);h.getLevel().addFreshEntity(bystander);
            try {aim(p,target.getBoundingBox().getCenter());h.assertTrue(CombatTargets.aimed(p,4)==target,"Fixture ray missed cow");
                SpiritHammerItem.smash(p);h.assertTrue(target.getHealth()<target.getMaxHealth(),"Primary cow protected");Fixtures.near(h,bystander.getHealth(),bystander.getMaxHealth(),"Collateral cow hurt");
            }finally{target.discard();bystander.discard();}
        });
        add(out,"native_spirit_weapon_attack_protects_own_pet",h->{
            var p=player(h);var pet=EntityType.WOLF.create(h.getLevel());pet.tame(p);
            try {for(var item:List.of(OriItems.SPIRIT_BLADE.get(),OriItems.SPIRIT_SPEAR.get(),OriItems.SPIRIT_HAMMER.get())){
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));var event=new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p,pet);SpiritCombat.attackEntity(event);h.assertTrue(event.isCanceled(),"Native direct attack bypassed pet protection");
            }}finally{pet.discard();}
        });
        add(out,"friendly_pet_occludes_direct_pick",h->{
            var p=player(h);var pet=EntityType.WOLF.create(h.getLevel());pet.tame(p);pet.setPos(p.position().add(0,0,1.2));
            var cow=EntityType.COW.create(h.getLevel());cow.setPos(p.position().add(0,0,2.4));h.getLevel().addFreshEntity(pet);h.getLevel().addFreshEntity(cow);
            try {aim(p,pet.getBoundingBox().getCenter());h.assertTrue(CombatTargets.aimed(p,3)==pet,"Ray skipped friendly pet");h.assertFalse(CombatTargets.canHitDirect(p,pet),"Own pet allowed");
                var spear=new SpiritSpear(OriEntities.SPIRIT_SPEAR.get(),h.getLevel());spear.setOwner(p);h.assertFalse(spear.pierce(pet),"Spear damaged own pet");spear.discard();
            }finally{pet.discard();cow.discard();}
        });
        add(out,"core_blade_one_hit_per_swing",h->{
            var p=player(h);var at=p.blockPosition().south(2);h.getLevel().setBlock(at,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
            try {aim(p,Vec3.atCenterOf(at));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_BLADE.get()));SpiritCombat.attack(p);var s=p.getData(SpiritData.COMBAT);
                s.startedAt=p.level().getGameTime()-SpiritCombat.windup(1);SpiritCombat.tick(new PlayerTickEvent.Post(p));SpiritCombat.tick(new PlayerTickEvent.Post(p));
                h.assertValueEqual(((GloomCoreBlockEntity)h.getLevel().getBlockEntity(at)).health(),37,"Two hit ticks double-counted core");
            }finally{SpiritCombat.cancel(p,"test");h.getLevel().removeBlock(at,false);}
        });
        add(out,"core_three_spirit_arrows_and_immune_feedback",h->{
            try(var c=Fixtures.connectedPlayer(h)) {var p=c.player();var at=p.blockPosition().south(2);var l=h.getLevel();l.setBlock(at,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
                try {for(int i=0;i<3;i++){var a=new Arrow(l,p,new ItemStack(Items.ARROW),null);a.addTag("orimod_spirit_arrow");l.getBlockState(at).onProjectileHit(l,l.getBlockState(at),new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false),a);a.discard();}
                    h.assertValueEqual(((GloomCoreBlockEntity)l.getBlockEntity(at)).health(),28,"Volley arrows not individually counted");
                    GloomCoreBlock.reject(l,at,p);boolean found=false;Object packet;
                    while((packet=c.channel().readOutbound())!=null)if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat && chat.overlay() && chat.content().toString().contains("message.orimod.blight.ineffective"))found=true;
                    h.assertTrue(found,"Immune hit had no visible feedback");
                }finally{l.removeBlock(at,false);}
            }
        });
        add(out,"core_burst_collision_no_false_immune_feedback",h->{
            try(var c=Fixtures.connectedPlayer(h)) {var p=c.player();var l=h.getLevel();var at=p.blockPosition().south(2);
                l.setBlock(at,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
                var orb=new LightBurst(OriEntities.LIGHT_BURST.get(),l);orb.setOwner(p);orb.setPos(Vec3.atCenterOf(at).add(0,0,-1));orb.setDeltaMovement(0,0,1.5);l.addFreshEntity(orb);
                try {orb.tick();h.assertTrue(orb.isRemoved(),"Real projectile did not collide/detonate");
                    h.assertValueEqual(((GloomCoreBlockEntity)l.getBlockEntity(at)).health(),32,"Direct burst collision damaged core incorrectly");
                    Object packet;while((packet=c.channel().readOutbound())!=null)
                        if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat && chat.overlay())
                            h.assertFalse(chat.content().toString().contains("message.orimod.blight.ineffective"),"Supported burst reported immune before damaging core");
                }finally{orb.discard();l.removeBlock(at,false);}
            }
        });
        add(out,"escape_logout_codec_resume_and_first_clear",h->{
            try(var c=Fixtures.connectedPlayer(h)){var p=c.player();EscapeSequence.start(p,p.position());
                var original=EscapeSequence.session(p);EscapeSequence.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
                h.assertTrue(EscapeSequence.session(p)==null,"Logout retained runtime session");
                var saved=p.getData(SpiritData.GLOOM_ESCAPE);var tag=GloomEscapeProgress.CODEC.encodeStart(NbtOps.INSTANCE,saved).getOrThrow();
                p.setData(SpiritData.GLOOM_ESCAPE,GloomEscapeProgress.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow());
                EscapeSequence.login(new PlayerEvent.PlayerLoggedInEvent(p));var resumed=EscapeSequence.session(p);
                h.assertTrue(resumed!=null&&resumed.origin().equals(original.origin()),"Saved escape not resumed");
                h.assertValueEqual((int)(p.level().getGameTime()-resumed.start()),saved.elapsed(),"Offline elapsed changed");
                h.assertTrue(EscapeSequence.victory(p),"First-clear reward lost");h.assertFalse(p.getData(SpiritData.GLOOM_ESCAPE).pending(),"Reward retained pending escape");
                h.assertFalse(EscapeSequence.victory(p),"Duplicate reward");
                EscapeSequence.login(new PlayerEvent.PlayerLoggedInEvent(p));h.assertTrue(EscapeSequence.session(p)==null,"Completed escape resumed");
            }
        });
        for(boolean running:List.of(false,true))add(out,"ginso_forbids_boat_"+running,h->{
            try(var c=Fixtures.connectedPlayer(h)){var p=c.player();var l=h.getLevel();var heart=h.absolutePos(new BlockPos(4,5,4));var piece=new GinsoPiece(heart.below(5),115117,0,0);
                var original=l.getBlockState(heart);var boat=EntityType.BOAT.create(l);
                try {l.setBlock(heart,OriBlocks.GINSO_WATER_HEART.get().defaultBlockState(),2);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,Arrays.stream(Ability.values()).mapToInt(a->a.bit).reduce(0,(a,b)->a|b));p.setPos(Vec3.atBottomCenterOf(piece.point(piece.escapeSafe())));boat.setPos(p.position());l.addFreshEntity(boat);
                    h.assertTrue(p.startRiding(boat,true),"Initial boat refused");h.assertTrue(GinsoEscape.start(p,piece),"Start refused passenger");h.assertFalse(p.isPassenger(),"Start did not dismount");
                    if(running)GinsoEscape.session(p).phase=GinsoEscape.Phase.RUNNING;
                    h.assertFalse(p.startRiding(boat,true),"Active escape allowed forced boarding");
                    GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);h.assertTrue(p.startRiding(boat,true),"Finished escape still blocks boarding");
                }finally{p.stopRiding();boat.discard();GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);l.setBlock(heart,original,2);}
            }
        });
        out.add(new TestFunction("p5_k_ecology","p5_k.command_remove_core_cancels_without_purify","orimod_p5:platform_room",200,0,true,h->{
            var l=h.getLevel();var core=h.absolutePos(new BlockPos(8,3,8));
            var oldData=BlightData.get(l);l.getDataStorage().set("orimod_blight",new BlightData());BlightEcology.clear();
            var before=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(var q:BlockPos.betweenClosed(core.offset(-2,-1,-2),core.offset(2,2,2))){var p=q.immutable();before.put(p,l.getBlockState(p));l.setBlock(p,(p.getY()<core.getY()?Blocks.DIRT:p.getY()==core.getY()-1?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),2);}
            try {
                l.setBlock(core,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
                ((cn.wwl.orimod.block.GloomCoreBlockEntity)l.getBlockEntity(core)).registerDefaultFootprint();
                var data=BlightData.get(l);h.assertTrue(data.ecology(core)!=null,"Command core missing ecology");
                l.setBlock(core,Blocks.AIR.defaultBlockState(),3); // setblock-style replace via command removal path
                h.assertTrue(data.ecology(core)==null,"Region kept after command remove");
                h.assertFalse(data.completed(core),"Command remove granted purify-complete");
                var status=data.status(net.minecraft.world.phys.Vec3.atCenterOf(core));
                h.assertFalse("purifying".equals(status.get("state")),"Command remove entered purifying");
                h.succeed();
            } finally {before.forEach((p,state)->l.setBlock(p,state,2));l.getDataStorage().set("orimod_blight",oldData);BlightEcology.clear();}
        }));
        out.add(new TestFunction("p5_k_ecology","p5_k.command_core_grows_and_protects_building","orimod_p5:platform_room",360,0,true,h->{
            var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,0,8));center=new BlockPos((center.getX()&~15)+8,248,(center.getZ()&~15)+8);final var core=center;
            var oldData=BlightData.get(l);l.getDataStorage().set("orimod_blight",new BlightData());BlightEcology.clear();
            var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(var q:BlockPos.betweenClosed(core.offset(-10,-3,-10),core.offset(10,6,10))){var p=q.immutable();before.put(p,l.getBlockState(p));l.setBlock(p,(p.getY()<247?Blocks.DIRT:p.getY()==247?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),2);}
            var planks=core.east(5);l.setBlock(planks,Blocks.OAK_PLANKS.defaultBlockState(),2);
            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withLevel(l).withSuppressedOutput(),"setblock "+core.getX()+" "+core.getY()+" "+core.getZ()+" orimod:gloom_core");
            h.runAfterDelay(240,()->{try{var region=BlightData.get(l).ecology(core);h.assertTrue(region!=null&&region.columns().size()>1,"Command core did not grow in 240 ticks");
                h.assertTrue(l.getBlockState(core.east(4).below()).is(Blocks.GRASS_BLOCK),"Building protection ignored");h.assertTrue(l.getBlockState(planks).is(Blocks.OAK_PLANKS),"Building replaced");
                h.succeed();
            }finally{before.forEach((p,state)->l.setBlock(p,state,2));l.getDataStorage().set("orimod_blight",oldData);BlightEcology.clear();}});
        }));
        for(var rotation:Rotation.values())for(int view=0;view<3;view++){
            final int angle=view;
            add(out,"creek_v2_anchor_"+rotation+"_view_"+view,h->{
                var l=h.getLevel();var placement=new RuinPlacement(h.absolutePos(new BlockPos(6,90,6)),rotation);
                var template=l.getStructureManager().getOrCreate(RuinKind.CREEK.template(2));
                var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation)
                    .setBoundingBox(placement.blocks(new BlockPos(8,8,24),new BlockPos(16,20,34))).setIgnoreEntities(true);
                var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();var bounds=settings.getBoundingBox();
                for(var at:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))before.put(at.immutable(),l.getBlockState(at));
                try {template.placeInWorld(l,placement.origin(),placement.origin(),settings,net.minecraft.util.RandomSource.create(117),2);
                    var p=player(h);var feet=angle==0?new Vec3(12.5,10,27.5):angle==1?new Vec3(12.5,10,25.5):new Vec3(12.5,11,27.5);
                    p.setPos(placement.point(feet));var anchor=placement.block(new BlockPos(12,13,29));
                    var ray=l.clip(new ClipContext(p.getEyePosition(),Vec3.atCenterOf(anchor),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
                    h.assertValueEqual(ray.getBlockPos(),anchor,"Root/terrain still blocks spirit eye ray");
                }finally{before.forEach((at,state)->l.setBlock(at,state,2));}
            });
        }
        return out;
    }
    private static ServerPlayer player(GameTestHelper h){var p=Fixtures.player(h);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,Arrays.stream(Ability.values()).mapToInt(a->a.bit).reduce(0,(a,b)->a|b));p.refreshDimensions();return p;}
    private static void aim(ServerPlayer p,Vec3 at){var v=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-v.x,v.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(v.y,Math.hypot(v.x,v.z))));}
    private static void add(List<TestFunction> out,String name,Consumer<GameTestHelper> body){out.add(new TestFunction("p5_k","p5_k."+name,"orimod_p5:platform_room",200,0,true,h->{body.accept(h);h.succeed();}));}
}
