package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.item.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class BlightGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var source:BlightRules.Source.values())add(tests,"core_matrix_"+source.name().toLowerCase(Locale.ROOT),(h,c)->{
            var p=core(h,new BlockPos(8,3,8));var be=(GloomCoreBlockEntity)h.getLevel().getBlockEntity(p);
            BlightInteractions.hit(c.player(),p,source);h.assertValueEqual(be.health(),40-BlightRules.damage(source),"Wrong core damage");
            h.assertValueEqual(h.getLevel().getBlockState(p).getValue(GloomCoreBlock.DAMAGE),BlightRules.crack(be.health()),"Wrong cracks");
            var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());var copy=new GloomCoreBlockEntity(p,h.getLevel().getBlockState(p));copy.loadWithComponents(tag,h.getLevel().registryAccess());h.assertValueEqual(copy.health(),be.health(),"HP lost on save");
        });
        for(var source:List.of(BlightRules.Source.CHARGE_FLAME,BlightRules.Source.SMASH,BlightRules.Source.LIGHT_BURST,BlightRules.Source.SPEAR,BlightRules.Source.FLASH))add(tests,"core_"+source.name().toLowerCase(Locale.ROOT)+"_destruction_never_drops_items",(h,c)->{
            var level=h.getLevel();var pos=core(h,new BlockPos(20,3,20));var state=level.getBlockState(pos);
            h.assertValueEqual(state.getBlock().getLootTable(),net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY,"Core has a loot table");
            h.assertTrue(Block.getDrops(state,level,pos,level.getBlockEntity(pos)).isEmpty(),"Core block loot is not empty");
            h.assertFalse(state.getBlock().asItem()==Items.AIR,"Creative test item removed");
            for(int health=40;health>0;health-=BlightRules.damage(source))BlightInteractions.hit(c.player(),pos,source);
            h.assertTrue(level.isEmptyBlock(pos),"Ability did not remove core");
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)).isEmpty(),"Ability produced item drops: "+source);
            // Even an explicit vanilla loot-requesting destruction must remain empty.
            level.setBlock(pos,state,2);level.destroyBlock(pos,true);
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)).isEmpty(),"Vanilla destruction produced core loot");
        });
        add(tests,"real_charge_hammer_burst_hit_adapters",(h,c)->{
            var player=c.player();var pos=core(h,new BlockPos(6,2,7));player.setPos(Vec3.atBottomCenterOf(pos).add(0,0,-2));
            h.assertTrue(ChargeFlame.release(player)>=0,"Charge refused");h.assertValueEqual(hp(h,pos),28,"Charge hook missing");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_HAMMER.get()));
            SpiritHammerItem.smash(player);h.assertValueEqual(hp(h,pos),18,"Hammer hook missing");
            var orb=new LightBurst(cn.wwl.orimod.world.OriEntities.LIGHT_BURST.get(),h.getLevel());orb.setOwner(player);orb.setPos(Vec3.atCenterOf(pos));orb.detonate();h.assertValueEqual(hp(h,pos),10,"Burst hook missing");
        });
        timed(tests,"real_spear_collision_damages_once",(h,c)->{
            var p=core(h,new BlockPos(8,3,10));var spear=new SpiritSpear(cn.wwl.orimod.world.OriEntities.SPIRIT_SPEAR.get(),h.getLevel());
            spear.setOwner(c.player());spear.setPos(Vec3.atCenterOf(p).add(0,0,-2));spear.setDeltaMovement(0,0,1);spear.setNoGravity(true);h.getLevel().addFreshEntity(spear);
            h.runAfterDelay(5,()->{try{h.assertValueEqual(hp(h,p),34,"Spear missed or damaged twice");h.succeed();}finally{c.close();spear.discard();}});
        });
        timed(tests,"flash_only_deals_four_each_twenty_active_ticks",(h,c)->{
            var pos=core(h,new BlockPos(7,2,7));var player=c.player();player.setPos(Vec3.atBottomCenterOf(pos).add(0,0,-2));
            h.assertTrue(Flash.keepAlive(player),"Flash did not start");
            for(int tick=1;tick<=40;tick++)h.runAfterDelay(tick,()->{h.assertTrue(Flash.keepAlive(player),"Flash refused during hold");Flash.tick(new PlayerTickEvent.Post(player));});
            h.runAfterDelay(18,()->h.assertValueEqual(hp(h,pos),40,"Flash damaged before one second"));
            h.runAfterDelay(42,()->{try{h.assertValueEqual(hp(h,pos),32,"Flash cadence or damage wrong");h.succeed();}finally{Flash.stop(player);c.close();}});
        });
        add(tests,"feature_flat_has_all_parts_and_atomic_obstruction_rejection",(h,c)->site(h,0,base->{
            var level=h.getLevel();var check=BlightedGroundsFeature.preflight(level,base,4);h.assertValueEqual(check.reason(),BlightedGroundsFeature.Reason.OK,"Flat preflight rejected");
            h.assertTrue(place(h,base,4),"Feature failed");h.assertTrue(level.getBlockState(base).is(OriBlocks.GLOOM_CORE),"Missing core");h.assertTrue(level.getChunk(base).getData(FeatureSites.BLIGHTS).contains(base.asLong()),"Generated core absent from durable journal");h.assertValueEqual(FeatureSites.get(level).nearest(level,FeatureSites.Kind.BLIGHT,Vec3.atCenterOf(base),p->true).orElseThrow(),base,"Core index missing");
            for(var block:List.of(OriBlocks.BLIGHTED_SOIL,OriBlocks.GLOOM_THORNS,OriBlocks.WITHERED_SPIRIT_LOG,OriBlocks.GLOOM_WATER))h.assertTrue(check.writes().values().stream().anyMatch(s->s.is(block)),"Missing "+block.getId());
            h.assertValueEqual(BlightData.get(level).status(Vec3.atCenterOf(base)).get("state"),"active","Feature failed to register ledger");
        }));
        for(String obstruction:List.of("water","steep","chest","heart","log"))add(tests,"preflight_"+obstruction+"_writes_nothing",(h,c)->site(h,10,base->{
            var level=h.getLevel();
            switch(obstruction){
                case "water"->level.setBlock(base.below(),Blocks.WATER.defaultBlockState(),2);
                case "steep"->{for(int y=0;y<7;y++)level.setBlock(base.offset(1,y,0),Blocks.DIRT.defaultBlockState(),2);}
                case "chest"->level.setBlock(base,Blocks.CHEST.defaultBlockState(),2);
                case "heart"->level.setBlock(base.offset(5,0,0),OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),2);
                case "log"->level.setBlock(base,Blocks.OAK_LOG.defaultBlockState(),2);
            }
            var before=snapshot(h,base);h.assertFalse(place(h,base,4),"Invalid feature placed");h.assertValueEqual(snapshot(h,base),before,"Failed preflight wrote blocks");
        }));
        add(tests,"tree_and_blight_exclusion_is_bidirectional",(h,c)->site(h,15,base->{
            h.getLevel().setBlock(base.offset(6,0,0),OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
            h.assertValueEqual(SpiritTreeFeature.preflight(h.getLevel(),base).reason(),SpiritTreeFeature.Reason.BLIGHT_GUARD,"Later tree ignored core");
        }));
        add(tests,"wave_finishes_in_600_effective_ticks_pays_once_and_preserves_builds",(h,c)->site(h,20,base->{
            var level=h.getLevel();var data=BlightData.get(level);var player=c.player();player.setPos(Vec3.atCenterOf(base));
            h.assertTrue(place(h,base,5),"Feature failed");var before=player.getData(SpiritData.PROGRESS).light();
            var positions=snapshot(h,base);var preserved=positions.entrySet().stream().filter(e->e.getValue().is(OriBlocks.BLIGHTED_SOIL)).findFirst().orElseThrow().getKey();
            level.setBlock(preserved,Blocks.GOLD_BLOCK.defaultBlockState(),2);
            for(int i=0;i<4;i++)BlightInteractions.hit(player,base,BlightRules.Source.SMASH);
            h.assertTrue(level.isEmptyBlock(base),"Core not removed");
            for(int i=0;i<599;i++){data.advance(level);h.assertTrue(data.lastReads<=128 && data.lastWrites<=32,"Wave exceeded budget");}
            h.assertFalse(data.completed(base),"Finished before 30 seconds");data.advance(level);h.assertTrue(data.completed(base),"Not finished at 600 effective ticks");
            for(var e:snapshot(h,base).entrySet())h.assertFalse(e.getValue().is(OriBlocks.BLIGHTED_SOIL)||e.getValue().is(OriBlocks.GLOOM_THORNS)||e.getValue().is(OriBlocks.WITHERED_SPIRIT_LOG)||e.getValue().is(OriBlocks.GLOOM_WATER),"Unconverted block "+e.getKey());
            h.assertTrue(level.getBlockState(preserved).is(Blocks.GOLD_BLOCK),"Player building overwritten");
            h.assertValueEqual(player.getData(SpiritData.PROGRESS).light(),before+60,"Reward incorrect");h.assertValueEqual(data.pendingCount(),0,"Paid recipients retained");
            var key="purify/"+level.dimension().location()+"/"+base.asLong();h.assertTrue(player.getData(SpiritData.PROGRESS).rewards().containsKey(key),"No claim receipt");
            h.assertTrue(GloomTideDefense.protects(level,Vec3.atCenterOf(base).add(32,0,0)),"Completed border unprotected");h.assertFalse(data.protects(Vec3.atCenterOf(base).add(32.1,0,0)),"Sanctuary too wide");
            level.setBlock(base,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);((GloomCoreBlockEntity)level.getBlockEntity(base)).terrain(new long[]{base.below().asLong()});
            for(int i=0;i<4;i++)BlightInteractions.hit(player,base,BlightRules.Source.SMASH);for(int i=0;i<610;i++)data.advance(level);
            h.assertValueEqual(player.getData(SpiritData.PROGRESS).light(),before+60,"Replacement core paid again");
            var saved=data.save(new CompoundTag(),level.registryAccess());h.assertTrue(saved.getList("regions",Tag.TAG_COMPOUND).stream().noneMatch(v->((CompoundTag)v).getLong("center")==base.asLong()),"Completed work retained");
        }));
        add(tests,"log_axis_and_all_restoration_mappings",(h,c)->{
            for(var axis:Direction.Axis.values()){
                var state=OriBlocks.WITHERED_SPIRIT_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis);var restored=BlightData.restored(state,0);
                h.assertTrue(restored.is(OriBlocks.SPIRIT_LOG),"Wrong wood");h.assertValueEqual(restored.getValue(RotatedPillarBlock.AXIS),axis,"Lost axis");h.assertValueEqual(restored.getLightEmission(),4,"Wood stayed dark");
            }
            h.assertTrue(BlightData.restored(OriBlocks.BLIGHTED_SOIL.get().defaultBlockState(),0).is(Blocks.GRASS_BLOCK),"Wrong soil");
            h.assertTrue(BlightData.restored(OriBlocks.GLOOM_WATER.get().defaultBlockState(),0).is(Blocks.WATER),"Wrong water");
        });
        add(tests,"unloaded_partition_survives_save_and_resumes_without_force_load",(h,c)->{
            var level=h.getLevel();var far=new BlockPos(2200000,180,2200000);var center=core(h,new BlockPos(8,3,8));var near=center.east();
            level.setBlock(near,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState(),2);var data=new BlightData();data.register(center,new long[]{near.asLong(),far.asLong()});data.begin(level,center);
            int loaded=level.getChunkSource().getLoadedChunksCount();for(int i=0;i<610;i++)data.advance(level);
            h.assertFalse(data.completed(center),"Unloaded work dropped");h.assertTrue(level.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Wave force-loaded far chunk");h.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),loaded,"Wave loaded terrain");
            data=BlightData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            // Simulate the player subsequently visiting: the test (not the ledger) explicitly loads the remaining partition.
            level.setBlock(far,OriBlocks.WITHERED_SPIRIT_LOG.get().defaultBlockState(),2);
            for(int i=0;i<610;i++)data.advance(level);
            h.assertTrue(data.completed(center),"Reloaded partition failed to finish");h.assertTrue(level.getBlockState(far).is(OriBlocks.SPIRIT_LOG),"Remaining block skipped");level.removeBlock(far,false);
        });
        add(tests,"multiple_waves_share_global_budget",(h,c)->{
            var level=h.getLevel();var data=new BlightData();
            for(int i=0;i<4;i++){
                var center=h.absolutePos(new BlockPos(10+i*10,4,30));var positions=new ArrayList<Long>();
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var p=center.offset(x,0,z);level.setBlock(p,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState(),2);positions.add(p.asLong());}
                data.register(center,positions.stream().mapToLong(Long::longValue).toArray());data.begin(level,center);
            }
            for(int i=0;i<700;i++){data.advance(level);h.assertTrue(data.lastReads<=128 && data.lastWrites<=32,"Global budget exceeded");}
            for(int i=0;i<4;i++)h.assertTrue(data.completed(h.absolutePos(new BlockPos(10+i*10,4,30))),"Round robin starved wave");
        });
        add(tests,"manual_unregistered_core_cannot_farm_region_rewards",(h,c)->{
            var pos=core(h,new BlockPos(8,3,8));int light=c.player().getData(SpiritData.PROGRESS).light();for(int i=0;i<4;i++)BlightInteractions.hit(c.player(),pos,BlightRules.Source.SMASH);
            h.assertTrue(h.getLevel().isEmptyBlock(pos),"Unregistered core indestructible");for(int i=0;i<610;i++)BlightData.get(h.getLevel()).advance(h.getLevel());h.assertValueEqual(c.player().getData(SpiritData.PROGRESS).light(),light,"Manual core paid region reward");
        });
        add(tests,"core_denies_tools_projectiles_and_entity_destruction",(h,c)->{
            var p=core(h,new BlockPos(8,3,8));var state=h.getLevel().getBlockState(p);state.attack(h.getLevel(),p,c.player());
            var arrow=new Arrow(h.getLevel(),c.player(),new ItemStack(Items.ARROW),null);state.onProjectileHit(h.getLevel(),state,new BlockHitResult(Vec3.atCenterOf(p),Direction.NORTH,p,false),arrow);
            h.assertValueEqual(hp(h,p),40,"Arrow or hand damaged core");h.assertTrue(state.getDestroySpeed(h.getLevel(),p)<0,"Core mineable");
            h.assertTrue(state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE)&&state.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE),"Missing boss immunity");h.assertValueEqual(state.getPistonPushReaction(),net.minecraft.world.level.material.PushReaction.BLOCK,"Piston bypass");
        });
        add(tests,"thorns_have_no_collision_shear_drop_and_ability_removal",(h,c)->{
            var p=h.absolutePos(new BlockPos(8,2,8));var level=h.getLevel();level.setBlock(p,OriBlocks.GLOOM_THORNS.get().defaultBlockState(),2);
            h.assertTrue(level.getBlockState(p).getCollisionShape(level,p).isEmpty(),"Thorns collide");c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));level.getBlockState(p).attack(level,p,c.player());h.assertTrue(level.isEmptyBlock(p),"Shears failed");
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(p).inflate(1),e->e.getItem().is(OriBlocks.GLOOM_THORNS.get().asItem())).size()==1,"Wrong shear loot");
            var active=EnumSet.of(BlightRules.Source.BLADE,BlightRules.Source.ARROW,BlightRules.Source.CHARGE_FLAME,BlightRules.Source.LIGHT_BURST,BlightRules.Source.SMASH,BlightRules.Source.SPEAR,BlightRules.Source.STOMP);
            for(var source:BlightRules.Source.values()){level.setBlock(p,OriBlocks.GLOOM_THORNS.get().defaultBlockState(),2);BlightInteractions.hit(c.player(),p,source);h.assertValueEqual(level.isEmptyBlock(p),active.contains(source),"Wrong thorn clearing source "+source);}
            c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);level.getBlockState(p).attack(level,p,c.player());
            h.assertTrue(level.getBlockState(p).is(OriBlocks.GLOOM_THORNS),"Bare hand attack removed thorns");h.assertValueEqual(level.getBlockState(p).getDestroyProgress(c.player(),level,p),0f,"Bare hand can mine thorns");
            c.player().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_BLADE.get()));level.getBlockState(p).attack(level,p,c.player());h.assertTrue(level.isEmptyBlock(p),"Blade click failed");
        });
        timed(tests,"thorns_damage_spirit_only_once_per_fifteen_ticks",(h,c)->{
            var p=c.player();p.getAbilities().invulnerable=false;for(int i=0;i<61;i++)p.tick();p.setHealth(p.getMaxHealth());
            var pos=h.absolutePos(new BlockPos(5,2,5));var state=OriBlocks.GLOOM_THORNS.get().defaultBlockState();
            state.entityInside(h.getLevel(),pos,p);float after=p.getHealth();h.assertTrue(after<p.getMaxHealth(),"Spirit immune to thorns");
            p.invulnerableTime=0;state.entityInside(h.getLevel(),pos.east(),p);h.assertValueEqual(p.getHealth(),after,"Adjacent bush bypassed cooldown");
            h.runAfterDelay(15,()->{try{p.invulnerableTime=0;state.entityInside(h.getLevel(),pos,p);h.assertTrue(p.getHealth()<after,"Thorns never rearmed");h.succeed();}finally{c.close();}});
        });
        timed(tests,"automatic_flame_breakable_targets_are_only_light_vessels",(h,c)->{
            var level=h.getLevel();var p=c.player();var at=h.absolutePos(new BlockPos(20,3,20));p.setPos(Vec3.atBottomCenterOf(at));
            var thorn=at.south(3);var core=at.east(3);var wall=at.west(3);var floor=at.north(3);var vessel=at.south(4).east(2);
            level.setBlock(thorn,OriBlocks.GLOOM_THORNS.get().defaultBlockState(),2);level.setBlock(core,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
            level.setBlock(wall,OriBlocks.CRACKED_RUIN_WALL.get().defaultBlockState(),2);level.setBlock(floor,OriBlocks.CRACKED_RUIN_FLOOR.get().defaultBlockState(),2);
            level.setBlock(vessel,OriBlocks.SPIRIT_LIGHT_VESSEL.get().defaultBlockState(),2);p.getData(SpiritData.COOLDOWNS).flameReadyAt=0;
            for(int tick=1;tick<=30;tick++)h.runAfterDelay(tick,()->SpiritFlame.tick(new PlayerTickEvent.Post(p)));
            h.runAfterDelay(31,()->{try{
                h.assertTrue(level.isEmptyBlock(vessel),"Companion stopped targeting vessels");
                h.assertTrue(level.getBlockState(thorn).is(OriBlocks.GLOOM_THORNS),"Automatic flame cleared nearby thorns");
                h.assertValueEqual(hp(h,core),40,"Automatic flame damaged core");
                h.assertFalse(level.getBlockState(wall).getValue(CrackedRuinBlock.COLLAPSING),"Automatic flame opened cracked wall");
                h.assertFalse(level.getBlockState(floor).getValue(CrackedRuinBlock.COLLAPSING),"Automatic flame opened cracked floor");h.succeed();
            }finally{c.close();}});
        });
        for(String kind:List.of("vanilla_arrow","spirit_arrow","spear"))timed(tests,"active_"+kind+"_clears_collision_free_thorns",(h,c)->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(20,3,23));level.setBlock(at,OriBlocks.GLOOM_THORNS.get().defaultBlockState(),2);
            net.minecraft.world.entity.projectile.Projectile projectile;
            if(kind.equals("spear"))projectile=new SpiritSpear(cn.wwl.orimod.world.OriEntities.SPIRIT_SPEAR.get(),level);
            else{projectile=new Arrow(level,c.player(),new ItemStack(Items.ARROW),null);if(kind.equals("spirit_arrow"))projectile.addTag("orimod_spirit_arrow");}
            projectile.setOwner(c.player());projectile.setPos(Vec3.atCenterOf(at).add(0,0,-2));projectile.setNoGravity(true);projectile.setDeltaMovement(0,0,1);level.addFreshEntity(projectile);
            h.runAfterDelay(5,()->{try{h.assertTrue(level.isEmptyBlock(at),"Active projectile ignored thorns: "+kind);h.succeed();}finally{projectile.discard();c.close();}});
        });
        add(tests,"automatic_flame_enemy_hit_does_not_clear_adjacent_thorns",(h,c)->{
            var level=h.getLevel();var p=c.player();var at=h.absolutePos(new BlockPos(20,3,20));p.setPos(Vec3.atBottomCenterOf(at));
            var thorn=at.south(3);level.setBlock(thorn,OriBlocks.GLOOM_THORNS.get().defaultBlockState(),2);
            var enemy=EntityType.ZOMBIE.create(level);enemy.setPos(Vec3.atBottomCenterOf(thorn).add(.6,0,0));enemy.setNoAi(true);level.addFreshEntity(enemy);
            try{p.getData(SpiritData.COOLDOWNS).flameReadyAt=0;float health=enemy.getHealth();SpiritFlame.tick(new PlayerTickEvent.Post(p));
                h.assertTrue(enemy.getHealth()<health,"Companion never hit hostile");h.assertTrue(level.getBlockState(thorn).is(OriBlocks.GLOOM_THORNS),"Enemy hit splashed onto thorns");
            }finally{enemy.discard();}
        });
        timed(tests,"spirit_standing_in_thorns_loses_health_with_companion_enabled",(h,c)->{
            var level=h.getLevel();var p=c.player();p.getAbilities().invulnerable=false;for(int i=0;i<61;i++)p.tick();p.invulnerableTime=0;p.setHealth(p.getMaxHealth());
            var at=h.absolutePos(new BlockPos(20,3,20));level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(at,OriBlocks.GLOOM_THORNS.get().defaultBlockState(),2);
            p.setPos(Vec3.atBottomCenterOf(at));p.getData(SpiritData.COOLDOWNS).flameReadyAt=0;float initial=p.getHealth();
            for(int tick=1;tick<=30;tick++)h.runAfterDelay(tick,()->{
                SpiritFlame.tick(new PlayerTickEvent.Post(p));
                // A tiny real movement runs Entity's block contact detection; no direct entityInside call.
                p.move(MoverType.SELF,new Vec3(.001,0,0));
            });
            h.runAfterDelay(31,()->{try{
                h.assertTrue(SpiritData.isSpirit(p),"Fixture left spirit form");h.assertTrue(level.getBlockState(at).is(OriBlocks.GLOOM_THORNS),"Companion erased contact hazard");
                h.assertTrue(p.getHealth()<initial,"Spirit standing in thorns took no damage in thirty ticks");h.succeed();
            }finally{c.close();}});
        });
        add(tests,"search_and_command_are_loaded_only_and_bound_radius",(h,c)->{
            var pos=core(h,new BlockPos(8,3,8));var origin=Vec3.atCenterOf(pos).add(0,0,-1);h.assertValueEqual(BlightSearch.nearest(h.getLevel(),origin,1).orElseThrow(),pos,"Inclusive radius missing");
            var far=new BlockPos(3200000,100,3200000);int before=h.getLevel().getChunkSource().getLoadedChunksCount();h.assertTrue(BlightSearch.nearest(h.getLevel(),Vec3.atCenterOf(far),1024).isEmpty(),"Found core in unloaded terrain");h.assertValueEqual(h.getLevel().getChunkSource().getLoadedChunksCount(),before,"Search loaded terrain");
            var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();cn.wwl.orimod.command.OriCommands.register(dispatcher);var source=c.player().createCommandSourceStack().withPermission(2).withPosition(origin);
            try{h.assertValueEqual(dispatcher.execute("ori blight nearest 1",source),1,"Found command failed");h.getLevel().removeBlock(pos,false);h.assertValueEqual(dispatcher.execute("ori blight nearest 1",source),0,"Missing command succeeded");for(String radius:List.of("0","1025")){boolean rejected=false;try{dispatcher.execute("ori blight nearest "+radius,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}h.assertTrue(rejected,"Invalid radius accepted");}}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new RuntimeException(e);}
        });
        add(tests,"core_hint_and_first_purify_line_follow_existing_switch_pipeline",(h,c)->{
            var pos=core(h,new BlockPos(8,2,8));CompanionHints.core(c.player(),pos);h.assertTrue(c.player().getData(SpiritData.PROGRESS).rewards().containsKey("hint/gloom_core"),"Core hint absent");
            h.assertTrue(CompanionLines.offer(c.player(),CompanionLines.Line.FIRST_PURIFY),"Purify line refused");
        });
        add(tests,"participants_capped_at_sixteen_and_snapshot_does_not_grow",(h,c)->{
            var level=h.getLevel();var center=h.absolutePos(new BlockPos(20,3,20));var data=new BlightData();var others=new ArrayList<Fixtures.ConnectedPlayer>();
            try {
                c.player().setPos(Vec3.atCenterOf(center));
                for(int n=0;n<17;n++){var other=Fixtures.connectedPlayer(h);other.player().setPos(Vec3.atCenterOf(center).add(1+n*.1,0,0));others.add(other);}
                data.register(center,new long[]{center.below().asLong()});h.assertTrue(data.begin(level,center),"First begin failed");
                h.assertValueEqual(data.status(Vec3.atCenterOf(center)).get("participants"),16,"Participant cap missing");
                h.assertFalse(data.begin(level,center),"Repeated begin accepted");
                h.assertValueEqual(data.status(Vec3.atCenterOf(center)).get("participants"),16,"Snapshot changed");
            } finally {for(var other:others)other.close();}
        });
        add(tests,"offline_reward_load_delivers_once_and_deletes_missing_players",(h,c)->{
            var level=h.getLevel();var center=h.absolutePos(new BlockPos(30,3,30));var previous=BlightData.get(level);var tag=previous.save(new CompoundTag(),level.registryAccess());
            try {
            var completed=java.util.Arrays.copyOf(tag.getLongArray("completed"),tag.getLongArray("completed").length+1);completed[completed.length-1]=center.asLong();tag.putLongArray("completed",completed);
            var entry=new CompoundTag();entry.putLong("center",center.asLong());var ids=new ListTag();ids.add(NbtUtils.createUUID(c.player().getUUID()));ids.add(NbtUtils.createUUID(UUID.randomUUID()));entry.put("players",ids);tag.getList("pending",Tag.TAG_COMPOUND).add(entry);
            var restored=BlightData.load(tag,level.registryAccess());level.getDataStorage().set("orimod_blight",restored);
            int light=c.player().getData(SpiritData.PROGRESS).light();var login=new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(c.player());
            BlightData.login(login);h.assertValueEqual(c.player().getData(SpiritData.PROGRESS).light(),light+60,"Offline pending reward missing");h.assertValueEqual(restored.pendingCount(),0,"Paid/deleted player entry retained");
            // Even replaying an old ledger cannot pay a player with a durable personal receipt twice.
            var replay=BlightData.load(tag,level.registryAccess());level.getDataStorage().set("orimod_blight",replay);BlightData.login(login);
            h.assertValueEqual(c.player().getData(SpiritData.PROGRESS).light(),light+60,"Replayed pending reward paid twice");h.assertValueEqual(replay.pendingCount(),0,"Replay not retired");
            } finally {level.getDataStorage().set("orimod_blight",previous);}
        });
        add(tests,"online_participant_can_move_away_before_completion",(h,c)->{
            var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var data=new BlightData();c.player().setPos(Vec3.atCenterOf(center));data.register(center,new long[]{center.below().asLong()});data.begin(level,center);
            c.player().setPos(c.player().position().add(80,0,0));int light=c.player().getData(SpiritData.PROGRESS).light();for(int n=0;n<600;n++)data.advance(level);
            h.assertValueEqual(c.player().getData(SpiritData.PROGRESS).light(),light+60,"Snapshot lost departing participant");
        });
        add(tests,"core_destroy_without_ability_retires_active_region",(h,c)->{
            var p=core(h,new BlockPos(8,3,8));var data=BlightData.get(h.getLevel());data.register(p,new long[]{p.below().asLong()});h.getLevel().removeBlock(p,false);
            h.assertTrue(data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("regions",Tag.TAG_COMPOUND).stream().noneMatch(e->((CompoundTag)e).getLong("center")==p.asLong()),"Removed core retained active work");
        });
        timed(tests,"defender_cooldown_cap_and_unload_ownership",BlightGameTests::defenderCooldown);
        timed(tests,"actual_chunk_unload_preserves_wave_and_reload_resumes",(h,c)->{
            var level=h.getLevel();var center=new BlockPos(2300000,180,2300000);var positions=new ArrayList<Long>();
            for(int x=0;x<=10;x++){var p=center.offset(x,0,0);level.setBlock(p,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState(),2);positions.add(p.asLong());}
            var data=new BlightData();data.register(center,positions.stream().mapToLong(Long::longValue).toArray());data.begin(level,center);for(int i=0;i<150;i++)data.advance(level);
            h.assertTrue(level.getBlockState(center).is(Blocks.GRASS_BLOCK),"Wave did not start");h.assertTrue(level.getBlockState(center.east(10)).is(OriBlocks.BLIGHTED_SOIL),"Wave raced ahead");
            var saved=data.save(new CompoundTag(),level.registryAccess());var done=new java.util.concurrent.atomic.AtomicBoolean();
            h.onEachTick(()->{
                if(done.get() || level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)!=null)return;
                done.set(true);
                try {
                    var reloaded=BlightData.load(saved,level.registryAccess());for(int i=0;i<100;i++)reloaded.advance(level);
                    h.assertValueEqual(reloaded.status(Vec3.atCenterOf(center)).get("elapsed"),150,"Unloaded wave advanced");
                    h.assertTrue(level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)==null,"Wave reloaded chunk");
                    // Visiting explicitly reloads the real saved chunk; the ledger itself only uses getChunkNow.
                    h.assertTrue(level.getBlockState(center.east(10)).is(OriBlocks.BLIGHTED_SOIL),"Unloaded terrain not persisted");
                    for(int i=0;i<450;i++)reloaded.advance(level);h.assertTrue(reloaded.completed(center),"Real chunk reload did not resume");h.assertTrue(level.getBlockState(center.east(10)).is(Blocks.GRASS_BLOCK),"Far edge still blighted");h.succeed();
                } finally {for(long p:positions)level.removeBlock(BlockPos.of(p),false);c.close();}
            });
        });
        return tests;
    }
    private static void defenderCooldown(GameTestHelper h, Fixtures.ConnectedPlayer c) {
        var level=h.getLevel();
        var base=h.absolutePos(new BlockPos(160,100,0));
        var old=snapshot(h,base);
        var difficulty=level.getDifficulty();
        var guards=new ArrayList<net.minecraft.world.entity.Mob>();
        var arena=new ArrayList<ChunkPos>();
        var pinned=new ArrayList<ChunkPos>();
        // setPos on an embedded player does not establish player chunk tickets. This arena is
        // outside the GameTest template's forced area; keep the entire inflate(13) search loaded.
        // Only release tickets acquired here, preserving any already-forced neighbouring tests.
        var cleaned=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{
            if(!cleaned.compareAndSet(false,true))return;
            try {
                for(var mob:guards)mob.discard();
                old.forEach((p,state)->level.setBlock(p,state,18));
            } finally {
                try {for(var chunk:pinned)level.setChunkForced(chunk.x,chunk.z,false);}
                finally {try {level.getServer().setDifficulty(difficulty,true);}finally {c.close();}}
            }
        };
        java.util.function.Consumer<Runnable> checked=action->{
            try {action.run();}catch(RuntimeException|Error failure){cleanup.run();throw failure;}
        };
        checked.accept(()->{
            for(int x=(base.getX()-13)>>4;x<=(base.getX()+13)>>4;x++)
                for(int z=(base.getZ()-13)>>4;z<=(base.getZ()+13)>>4;z++)
                    {
                        var chunk=new ChunkPos(x,z);arena.add(chunk);
                        if(level.setChunkForced(x,z,true))pinned.add(chunk);
                    }
            for(var p:old.keySet())level.setBlock(p,p.getY()<base.getY()?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            c.player().setPos(Vec3.atCenterOf(base).add(0,1,-2));
            level.setBlock(base,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
            var data=BlightData.get(level);data.register(base,new long[]{base.below().asLong()});
            Runnable spawn=()->{
                for(var chunk:arena)h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)!=null
                        && level.areEntitiesLoaded(chunk.toLong()) && level.isNaturalSpawningAllowed(chunk),"Defender arena not entity-ticking");
                data.defend(level,base,c.player());
                for(var mob:level.getEntitiesOfClass(cn.wwl.orimod.entity.GloomSpitter.class,new AABB(base).inflate(13))){
                    mob.setNoAi(true);mob.setPersistenceRequired();if(!guards.contains(mob))guards.add(mob);
                }
            };
            // Forced tickets are asynchronous: block data can already be available while entity
            // sections are still HIDDEN. addFreshEntity succeeds there, but AABB queries see nothing.
            // Start the unchanged cooldown assertions only once ALL arena sections are ready.
            long[] began={-1};
            long waitingSince=level.getGameTime();
            h.onEachTick(()->{
                if(cleaned.get())return;
                checked.accept(()->{
                    if(began[0]<0){
                        h.assertTrue(level.getGameTime()-waitingSince<100,"Defender arena did not become entity-ticking");
                        if(arena.stream().anyMatch(chunk->level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null
                                || !level.areEntitiesLoaded(chunk.toLong()) || !level.isNaturalSpawningAllowed(chunk)))return;
                        began[0]=level.getGameTime();
                    }
                    switch((int)(level.getGameTime()-began[0])){
                        case 0 -> {
                            spawn.run();h.assertValueEqual(data.status(Vec3.atCenterOf(base)).get("defenders"),1,"First defender missing");
                            spawn.run();h.assertValueEqual(guards.size(),1,"Cooldown ignored");
                        }
                        case 100 -> {spawn.run();h.assertValueEqual(data.status(Vec3.atCenterOf(base)).get("defenders"),2,"100 tick cooldown too long");}
                        case 200 -> {spawn.run();h.assertValueEqual(data.status(Vec3.atCenterOf(base)).get("defenders"),3,"Third defender missing");}
                        case 300 -> {
                            try {
                                spawn.run();h.assertValueEqual(guards.size(),3,"More than three defenders");
                                var first=guards.getFirst();var saved=first.saveWithoutId(new CompoundTag());first.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                                h.assertValueEqual(data.status(Vec3.atCenterOf(base)).get("defenders"),3,"Unloading freed a guard slot");
                                var restored=cn.wwl.orimod.entity.OriMobs.GLOOM_SPITTER.get().create(level);restored.load(saved);level.addFreshEntity(restored);guards.add(restored);restored.discard();
                                h.assertValueEqual(data.status(Vec3.atCenterOf(base)).get("defenders"),2,"Discarded defender did not free slot");
                                spawn.run();h.assertValueEqual(data.status(Vec3.atCenterOf(base)).get("defenders"),3,"Freed slot cannot refill");h.succeed();
                            } finally {cleanup.run();}
                        }
                    }
                });
            });
        });
    }
    private static int hp(GameTestHelper h,BlockPos pos){return ((GloomCoreBlockEntity)h.getLevel().getBlockEntity(pos)).health();}
    private static BlockPos core(GameTestHelper h,BlockPos local){var p=h.absolutePos(local);h.getLevel().setBlock(p,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);return p;}
    private static boolean place(GameTestHelper h,BlockPos base,long seed){return h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.parse("orimod:blighted_grounds")).place(h.getLevel(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),base);}
    private static Map<BlockPos,BlockState> snapshot(GameTestHelper h,BlockPos base){var map=new LinkedHashMap<BlockPos,BlockState>();for(var p:BlockPos.betweenClosed(base.offset(-15,-2,-15),base.offset(15,9,15)))map.put(p.immutable(),h.getLevel().getBlockState(p));return map;}
    private static void site(GameTestHelper h,int index,java.util.function.Consumer<BlockPos> run){var base=h.absolutePos(new BlockPos(128+index*64,100,0));var old=snapshot(h,base);try{for(var p:old.keySet())h.getLevel().setBlock(p,p.getY()<base.getY()?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),18);run.accept(base);}finally{old.forEach((p,s)->h.getLevel().setBlock(p,s,18));}}
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run){tests.add(new TestFunction("blight","blight."+name,"orimod_gametest:platform_room",100,0,true,h->{try(var c=Fixtures.connectedPlayer(h)){CrackedRuinGameTests.prepare(c);run.accept(h,c);}h.succeed();}));}
    private static void timed(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Fixtures.ConnectedPlayer> run){tests.add(new TestFunction("blight","blight."+name,"orimod_gametest:platform_room",450,0,true,h->{var c=Fixtures.connectedPlayer(h);try{CrackedRuinGameTests.prepare(c);run.accept(h,c);}catch(Throwable t){c.close();throw t;}}));}
}
