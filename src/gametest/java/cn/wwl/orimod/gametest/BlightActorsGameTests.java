package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_r117")
public final class BlightActorsGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        add(tests,"snow_transaction_resnow_legacy_maintenance_and_restore_cas",h->{try(var f=new Site(h)){
            var floor=f.center.east(4).below();var snow=Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,4);
            f.set(floor,Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY,true));f.set(floor.above(),snow);
            var b=new BlightEcologyRules.Budget();var batch=BlightConversion.ground(f.level,floor,b,1);
            h.assertValueEqual(batch.size(),2,"Snow omitted from ground transaction");
            h.assertTrue(BlightConversion.commit(f.level,f.data,f.center,floor,batch,false,b),"Snow ground commit failed");
            h.assertTrue(f.level.isEmptyBlock(floor.above()),"Snow still hides new corruption");var j=f.data.ecology(f.center);int records=j.size();
            f.set(floor.above(),snow.setValue(SnowLayerBlock.LAYERS,7));
            var legacy=f.center.north(2).below();var edited=f.center.west(2).below();var unsupported=f.center.south(2).below();
            for(var at:List.of(legacy,edited,unsupported))f.set(at.above(),snow);
            var box=new net.minecraft.world.phys.AABB(f.center).inflate(10);int drops=f.level.getEntitiesOfClass(ItemEntity.class,box).size();
            for(int i=0;i<100;i++)BlightEcology.step(f.level.getServer(),false);
            for(var at:List.of(floor,legacy,edited,unsupported))h.assertTrue(f.level.isEmptyBlock(at.above()),"Snowfall / initial snow survived maintenance");
            h.assertValueEqual(j.size(),records+3,"Repeated snow appended duplicate undo entries");
            h.assertValueEqual(j.changeAt(floor.above()).before(),snow,"Fresh snowfall replaced original snow depth");
            h.assertValueEqual(f.level.getEntitiesOfClass(ItemEntity.class,box).size(),drops,"Snow clearing spawned loot");
            f.set(edited.above(),Blocks.GOLD_BLOCK.defaultBlockState());f.set(unsupported,Blocks.AIR.defaultBlockState());
            f.data.begin(f.level,f.center);for(int i=0;i<700;i++)f.data.advance(f.level);
            h.assertValueEqual(f.level.getBlockState(floor.above()),snow,"Original four-layer snow not restored");
            h.assertValueEqual(f.level.getBlockState(legacy.above()),snow,"Initial snow not restored");
            h.assertTrue(f.level.getBlockState(edited.above()).is(Blocks.GOLD_BLOCK),"Snow restoration overwrote player edit");
            h.assertTrue(f.level.isEmptyBlock(unsupported.above()),"Snow restored without support");
        }});
        add(tests,"expired_protection_rearms_consumed_frontier_and_rechecks_building",h->{try(var f=new Site(h)){
            var floor=f.center.east(4).below();var building=floor.east(3).above();f.set(floor,Blocks.GRASS_BLOCK.defaultBlockState());f.set(building,Blocks.OAK_PLANKS.defaultBlockState());
            for(int i=0;i<200;i++)BlightEcology.step(f.level.getServer(),false);var j=f.data.ecology(f.center);
            h.assertTrue(j.protectedAt(floor),"Building fixture never protected");var first=j.protectionAt(floor);
            while(j.loadedTicks()<first.registeredTick()+24000)j.loadedTick();
            for(int i=0;i<200;i++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(j.protectedAt(floor)&&j.protectionAt(floor).registeredTick()>first.registeredTick(),"Expired direction was not inspected again");
            h.assertTrue(f.level.getBlockState(floor).is(Blocks.GRASS_BLOCK),"Expiry bypassed existing building");
            var second=j.protectionAt(floor);f.set(building,Blocks.AIR.defaultBlockState());
            while(j.loadedTicks()<second.registeredTick()+23999)j.loadedTick();h.assertTrue(j.protectedAt(floor),"Cache expired before a loaded day");
            for(int i=0;i<200;i++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(f.level.getBlockState(floor).is(OriBlocks.BLIGHTED_SOIL),"Removed building leaves consumed frontier permanently stuck");
            h.assertTrue((int)BlightEcology.metrics().get("maxReads")<=512&&(int)BlightEcology.metrics().get("maxWrites")<=32,"Expiry used extra terrain budget");
        }});
        add(tests,"summaries_four_only_no_block_reads_and_reloaded_wave",h->{try(var f=new Site(h)){
            for(int i=1;i<7;i++){var c=f.center.east(i*3);f.data.register(c,new long[]{c.below().asLong()});f.data.initializeEcology(c,List.of(c.below()),true);}
            var list=f.data.nearbySummaries(Vec3.atCenterOf(f.center));h.assertValueEqual(list.size(),4,"More than four summaries");
            h.assertValueEqual(list.getFirst().center(),f.center.asLong(),"Actual occupied core not first");long revision=f.data.atmosphereRevision();
            f.data.begin(f.level,f.center);h.assertTrue(f.data.atmosphereRevision()>revision,"Core death not immediately observable");
            var loaded=BlightData.load(f.data.save(new CompoundTag(),f.level.registryAccess()),f.level.registryAccess());
            h.assertTrue(loaded.nearbySummaries(Vec3.atCenterOf(f.center)).stream().anyMatch(c->c.center()==f.center.asLong()&&c.purifying()),"Reload lost purification summary");
            h.assertTrue(f.data.nearbySummaries(Vec3.atCenterOf(f.center.east(200))).isEmpty(),"Distant world cores sent");
        }});
        add(tests,"summary_heartbeat_boundary_death_and_dimension_packets",h->{try(var f=new Site(h);var c=Fixtures.connectedPlayer(h)){
            var p=c.player();p.setPos(Vec3.atCenterOf(f.center));BlightAtmosphereSync.clear();c.channel().outboundMessages().clear();
            java.util.function.Supplier<List<cn.wwl.orimod.network.BlightAtmospherePayload>> packets=()->c.channel().outboundMessages().stream()
                    .filter(o->o instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet&&packet.payload() instanceof cn.wwl.orimod.network.BlightAtmospherePayload)
                    .map(o->(cn.wwl.orimod.network.BlightAtmospherePayload)((net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)o).payload()).toList();
            BlightAtmosphereSync.sync(p,0);for(int i=1;i<100;i++)BlightAtmosphereSync.sync(p,i);h.assertValueEqual(packets.get().size(),1,"Unchanged state spam");
            BlightAtmosphereSync.sync(p,100);h.assertValueEqual(packets.get().size(),2,"Heartbeat absent");
            p.setPos(Vec3.atCenterOf(f.center.east(8)));BlightAtmosphereSync.sync(p,101);h.assertValueEqual(packets.get().size(),3,"Boundary waits for 20 ticks");
            f.data.begin(f.level,f.center);BlightAtmosphereSync.sync(p,102);h.assertTrue(packets.get().getLast().cores().getFirst().purifying(),"Core death packet delayed");
            p.setPos(Vec3.atCenterOf(f.center.east(200)));BlightAtmosphereSync.sync(p,103);h.assertTrue(packets.get().getLast().cores().isEmpty(),"Far teleport retains fog");
            p.setPos(Vec3.atCenterOf(f.center.above(40)));BlightAtmosphereSync.sync(p,104);h.assertFalse(packets.get().getLast().cores().isEmpty(),"Flying teleport waits for ordinary cadence");
            var firstGeneration=packets.get().getLast().generation();p.setServerLevel(f.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER));BlightAtmosphereSync.sync(p,105);
            h.assertTrue(packets.get().getLast().generation()>firstGeneration&&packets.get().getLast().cores().isEmpty(),"Dimension stale summary");p.setServerLevel(f.level);BlightAtmosphereSync.clear();
        }});
        add(tests,"ecology_mob_caps_unload_and_core_cleanup",h->{try(var f=new Site(h)){
            var p=Fixtures.player(h);p.setPos(Vec3.atCenterOf(f.center.east(16)));var old=f.level.getDifficulty();f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);BlightMobs.clear();
            var spawning=f.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);boolean oldSpawning=spawning.get();
            try{
                spawning.set(false,f.level.getServer());h.assertFalse(BlightMobs.attempt(p,f.center,new BlightEcologyRules.Budget(),0),"Ignores doMobSpawning");spawning.set(true,f.level.getServer());
                int added=0;for(int i=0;i<100&&added<3;i++)if(BlightMobs.attempt(p,f.center,new BlightEcologyRules.Budget(),i*20L))added++;
                h.assertValueEqual(added,3,"Ecology mobs never fill shared three cap");h.assertFalse(BlightMobs.attempt(p,f.center,new BlightEcologyRules.Budget(),3000),"Fourth guard allowed");
                var mobs=f.level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(f.center).inflate(30),m->m.getPersistentData().getBoolean(BlightMobs.EXTRA));
                h.assertValueEqual(BlightData.load(f.data.save(new CompoundTag(),f.level.registryAccess()),f.level.registryAccess()).extraMobCount(),3,"Mob reservation lost on save");
                h.assertValueEqual(mobs.size(),3,"Missing live ecology mobs");var a=mobs.getFirst();var saved=new CompoundTag();a.save(saved);a.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);h.assertValueEqual(f.data.extraMobCount(),3,"Unload releases ecological global reservation");
                f.data.begin(f.level,f.center);h.assertValueEqual(f.data.extraMobCount(),0,"Purify leaks global cap");h.assertTrue(mobs.stream().allMatch(Entity::isRemoved),"Loaded ecology mob survives core destruction");
                var reloaded=EntityType.loadEntityRecursive(saved,f.level,e->e);h.assertFalse(f.level.addFreshEntity(reloaded),"Unloaded creature respawned after core destruction");
            }finally{spawning.set(oldSpawning,f.level.getServer());f.level.getServer().setDifficulty(old,true);BlightMobs.clear();}
        }});
        add(tests,"ground_and_double_flower_conversion_do_not_drop_items",h->{try(var f=new Site(h)){
            var floor=f.center.east(4).below();f.set(floor,Blocks.DIRT.defaultBlockState());f.set(floor.above(),Blocks.DANDELION.defaultBlockState());
            var box=new net.minecraft.world.phys.AABB(floor).inflate(5);int before=f.level.getEntitiesOfClass(ItemEntity.class,box).size();
            var b=new BlightEcologyRules.Budget();var batch=BlightConversion.ground(f.level,floor,b,1);
            h.assertTrue(BlightConversion.commit(f.level,f.data,f.center,floor,batch,false,b),"Flower ground transaction rejected");
            h.assertValueEqual(f.level.getEntitiesOfClass(ItemEntity.class,box).size(),before,"Ground update popped unsupported flower loot");
            f.set(floor.above(),Blocks.SUNFLOWER.defaultBlockState());f.set(floor.above(2),Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER));
            b=new BlightEcologyRules.Budget();batch=BlightConversion.plants(f.level,floor,f.data.ecology(f.center),b);
            h.assertTrue(BlightConversion.commit(f.level,f.data,f.center,floor,batch,true,b),"Double flower maintenance rejected");
            h.assertValueEqual(f.level.getEntitiesOfClass(ItemEntity.class,box).size(),before,"Double flower update popped loot outside transaction");
        }});
        add(tests,"legacy_and_regrown_plants_keep_first_undo_record",h->{try(var f=new Site(h)){
            var floor=f.center.east(2).below();var d=f.data;var j=d.ecology(f.center);
            for(var block:List.of(Blocks.SHORT_GRASS,Blocks.TALL_GRASS,Blocks.DANDELION,Blocks.SUNFLOWER,Blocks.PINK_PETALS))h.assertTrue(BlightConversion.flower(block.defaultBlockState()),"Plant excluded: "+block);
            f.set(floor.above(),Blocks.DANDELION.defaultBlockState());
            for(int t=0;t<80;t++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(f.level.isEmptyBlock(floor.above()),"Legacy plant never visited");int records=j.size();
            var first=j.changeAt(floor.above());h.assertValueEqual(first.before(),Blocks.DANDELION.defaultBlockState(),"Original plant lost");
            f.set(floor.above(),Blocks.TALL_GRASS.defaultBlockState());f.set(floor.above(2),Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER));
            for(int t=0;t<80;t++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(f.level.isEmptyBlock(floor.above())&&f.level.isEmptyBlock(floor.above(2)),"New double grass survived maintenance");
            h.assertValueEqual(j.size(),records+1,"Re-growth duplicated ledger records");h.assertValueEqual(j.changeAt(floor.above()).before(),first.before(),"First undo overwritten");
            f.set(floor.above(),Blocks.GOLD_BLOCK.defaultBlockState());
            for(int t=0;t<80;t++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(f.level.getBlockState(floor.above()).is(Blocks.GOLD_BLOCK),"Maintenance ate player block");
            f.set(floor.above(),Blocks.DANDELION.defaultBlockState());f.set(floor.east().above(),Blocks.GOLD_BLOCK.defaultBlockState());
            for(int t=0;t<80;t++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(j.protectedAt(floor),"Maintenance did not remember artificial protection");
            f.set(floor.east().above(),Blocks.AIR.defaultBlockState());for(int t=0;t<80;t++)BlightEcology.step(f.level.getServer(),false);
            h.assertTrue(f.level.getBlockState(floor.above()).is(Blocks.DANDELION),"Protection cache lost before its expiry");
        }});
        add(tests,"all_supported_animals_expose_at_200_not_199",h->{try(var f=new Site(h)){
            var animals=new ArrayList<Animal>();for(var type:List.of(EntityType.COW,EntityType.PIG,EntityType.SHEEP,EntityType.CHICKEN,EntityType.RABBIT,EntityType.HORSE,EntityType.DONKEY,EntityType.MULE))animals.add(f.animal(type));
            f.steps(199);for(var a:animals)h.assertFalse(CorruptedAnimals.infected(a),"Early infection "+a.getType());
            f.steps(1);h.assertValueEqual(animals.stream().filter(CorruptedAnimals::infected).count(),4L,"Infection change budget not four");
            f.steps(1);for(var a:animals)h.assertTrue(CorruptedAnimals.infected(a),"Supported species never infected "+a.getType());
            h.assertTrue(CorruptedAnimals.metrics().get("changes")<=4&&CorruptedAnimals.metrics().get("navigation")<=2,"Actor budget exceeded");
        }});
        add(tests,"sixteen_cap_unload_reservation_and_reload_restore",h->{try(var f=new Site(h)){
            var animals=new ArrayList<Animal>();for(int i=0;i<80;i++)animals.add(f.animal(EntityType.COW));f.steps(260);
            h.assertValueEqual(CorruptedAnimals.metrics().get("candidates"),64,"Global candidate cap not exercised");
            h.assertValueEqual(animals.stream().filter(CorruptedAnimals::infected).count(),16L,"Core cap not sixteen");
            var a=animals.getFirst();var saved=new CompoundTag();a.save(saved);a.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertValueEqual(f.data.animalCount(f.center),16,"Unload released reservation");
            var loaded=BlightData.load(f.data.save(new CompoundTag(),f.level.registryAccess()),f.level.registryAccess());h.assertValueEqual(loaded.animalCount(f.center),16,"Reservation not persistent");
            f.data.begin(f.level,f.center);for(int i=0;i<620;i++)f.data.advance(f.level);
            var restored=(Animal)EntityType.loadEntityRecursive(saved,f.level,e->e);f.animals.add(restored);f.level.addFreshEntity(restored);
            h.assertFalse(restored.getData(CorruptedAnimals.VIEW)||CorruptedAnimals.ownerActive(restored),"Reload briefly renders/attacks as corrupt after owner completion");f.steps(8);
            h.assertFalse(CorruptedAnimals.infected(restored),"Completed owner did not restore on entity load");
        }});
        add(tests,"horse_identity_saddle_donkey_inventory_and_age_preserved",h->{try(var f=new Site(h)){
            var a=(Donkey)f.animal(EntityType.DONKEY);a.setCustomName(Component.literal("Old friend"));a.setTamed(true);var owner=UUID.randomUUID();a.setOwnerUUID(owner);a.setAge(-4000);
            var n=new CompoundTag();a.save(n);n.putBoolean("ChestedHorse",true);a.load(n);a.equipSaddle(new ItemStack(Items.SADDLE),null);a.getSlot(500).set(new ItemStack(Items.DIAMOND,7));
            var holder=f.animal(EntityType.COW);a.setLeashedTo(holder,false);var id=a.getUUID();f.steps(205);
            h.assertTrue(CorruptedAnimals.infected(a),"Baby donkey not tinted");h.assertFalse(new CorruptAnimalGoal(a).eligible(Fixtures.player(h)),"Baby/leashed animal attacks");
            // Age/name edits made during infection must not be rolled back by restoration.
            a.setAge(-3900);a.setCustomName(Component.literal("Still my friend"));f.data.begin(f.level,f.center);
            for(int i=0;i<620;i++)f.data.advance(f.level);f.steps(2);
            h.assertFalse(CorruptedAnimals.infected(a),"Animal did not restore");h.assertValueEqual(a.getUUID(),id,"Entity replaced");h.assertValueEqual(a.getOwnerUUID(),owner,"Tame owner lost");
            h.assertTrue(a.isTamed()&&a.isSaddled()&&a.getLeashHolder()==holder,"Tame/saddle/leash lost");h.assertValueEqual(a.getAge(),-3900,"Age rolled back");
            h.assertValueEqual(a.getName().getString(),"Still my friend","Name rolled back");h.assertValueEqual(a.getSlot(500).get().getCount(),7,"Chest contents lost");
        }});
        add(tests,"loot_replaces_products_but_preserves_donkey_cargo",h->{try(var f=new Site(h)){
            var a=(Donkey)f.animal(EntityType.DONKEY);var n=new CompoundTag();a.save(n);n.putBoolean("ChestedHorse",true);a.load(n);
            a.equipSaddle(new ItemStack(Items.SADDLE),null);a.getSlot(500).set(new ItemStack(Items.DIAMOND,7));f.steps(205);
            h.assertTrue(CorruptedAnimals.infected(a),"Loot fixture not infected");a.hurt(a.damageSources().genericKill(),1000);
            var items=f.level.getEntitiesOfClass(ItemEntity.class,a.getBoundingBox().inflate(4));
            h.assertTrue(items.stream().anyMatch(i->i.getItem().is(OriItems.GLOOM_RESIDUE)),"Residue absent");h.assertFalse(items.stream().anyMatch(i->i.getItem().is(Items.LEATHER)),"Normal product still drops");
            h.assertTrue(items.stream().anyMatch(i->i.getItem().is(Items.SADDLE))&&items.stream().anyMatch(i->i.getItem().is(Items.DIAMOND)&&i.getItem().getCount()==7),"Cargo/equipment filtered with products");items.forEach(Entity::discard);
        }});
        add(tests,"player_footprint_healing_and_energy",h->{try(var f=new Site(h)){
            var p=Fixtures.player(h);p.setPos(Vec3.atBottomCenterOf(f.center.east(2)));p.setData(SpiritData.FORM,false);p.setHealth(10);p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(10);
            h.assertTrue(BlightExposure.affected(p),"Occupied column not detected");for(int i=0;i<100;i++)p.getFoodData().tick(p);Fixtures.near(h,p.getHealth(),10,"Human natural healing leaked");
            p.heal(2);Fixtures.near(h,p.getHealth(),12,"Active healing suppressed");p.setData(SpiritData.FORM,true);p.setData(SpiritData.ENERGY,20f);
            var s=p.getData(SpiritData.ENERGY_STATE);s.lastSpent=p.level().getGameTime()-1000;s.recoveryHurtAt=p.level().getGameTime()-1000;SpiritRecovery.tick(p);Fixtures.near(h,p.getData(SpiritData.ENERGY),20,"Passive energy leaked");
            p.setPos(p.position().add(0,20,0));h.assertFalse(BlightExposure.affected(p),"Infinite cylinder effect");
            p.setPos(Vec3.atBottomCenterOf(f.center.east(20)));h.assertFalse(BlightExposure.affected(p),"Envelope hole affected player");
            p.setPos(Vec3.atBottomCenterOf(f.center.east(2)));p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertFalse(BlightExposure.affected(p),"Creative drain");
        }});
        add(tests,"noai_invulnerable_exit_reset_and_orphan",h->{try(var f=new Site(h)){
            var a=f.animal(EntityType.COW);var b=f.animal(EntityType.PIG);a.setNoAi(true);b.setInvulnerable(true);f.steps(210);h.assertFalse(CorruptedAnimals.infected(a)||CorruptedAnimals.infected(b),"Excluded animal infected");
            a.setNoAi(false);f.steps(100);a.setPos(a.position().add(20,0,0));f.steps(1);h.assertValueEqual(a.getData(CorruptedAnimals.STATE).exposure(),0,"Exposure did not reset outside");
            CorruptedAnimals.state(a,new CorruptionState("minecraft:the_nether",BlockPos.ZERO.asLong(),200,true,0));f.steps(1);h.assertFalse(CorruptedAnimals.infected(a),"Orphan infection persists");
        }});
        add(tests,"dimension_transfer_keeps_original_owner_and_restores",h->{try(var f=new Site(h)){
            var a=f.animal(EntityType.COW);f.steps(205);var owner=a.getData(CorruptedAnimals.STATE);var saved=new CompoundTag();a.save(saved);a.remove(Entity.RemovalReason.CHANGED_DIMENSION);
            var nether=f.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);nether.getChunk(0,0);
            var moved=(Animal)EntityType.loadEntityRecursive(saved,nether,e->e);moved.setPos(8,200,8);f.animals.add(moved);nether.addFreshEntity(moved);
            h.assertValueEqual(moved.getData(CorruptedAnimals.STATE),owner,"Dimension changed owner or infection");h.assertTrue(CorruptedAnimals.ownerActive(moved),"Cross-dimension owner lookup fails");
            f.data.begin(f.level,f.center);for(int i=0;i<620;i++)f.data.advance(f.level);f.steps(2);h.assertFalse(CorruptedAnimals.infected(moved),"Cross-dimension recovery fails");
        }});
        tests.add(new TestFunction("r117_blight_attack","r117_blight_actors.real_ai_damage_cooldown_peaceful_and_core_stop","orimod_r117:room",140,0,true,h->{
            var f=new Site(h);var connection=Fixtures.connectedPlayer(h);var p=connection.player();var old=f.level.getDifficulty();
            var rule=f.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION);boolean oldNatural=rule.get();rule.set(false,f.level.getServer());
            f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);p.setPos(Vec3.atBottomCenterOf(f.center.east(2)));p.setHealth(20);p.setNoGravity(true);for(int i=0;i<61;i++)p.tick();p.invulnerableTime=0;
            var a=f.animal(EntityType.COW);a.setPos(p.position().add(0,0,1));f.data.claimAnimal(f.center,a.getUUID(),16);
            CorruptedAnimals.state(a,new CorruptionState(f.level.dimension().location().toString(),f.center.asLong(),200,true,2));
            Runnable close=()->{connection.close();f.close();rule.set(oldNatural,f.level.getServer());f.level.getServer().setDifficulty(old,true);};
            h.runAfterDelay(15,()->{try{Fixtures.near(h,p.getHealth(),18,"First actual melee hit must be 2 HP");}catch(Throwable t){close.run();throw t;}});
            h.runAfterDelay(34,()->{try{
                Fixtures.near(h,p.getHealth(),16,"Actual melee cooldown is not 20 ticks");f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
            }catch(Throwable t){close.run();throw t;}});
            h.runAfterDelay(65,()->{try{
                Fixtures.near(h,p.getHealth(),16,"Peaceful attack/healing leaked");f.level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);f.data.begin(f.level,f.center);
            }catch(Throwable t){close.run();throw t;}});
            h.runAfterDelay(95,()->{try{Fixtures.near(h,p.getHealth(),16,"Core destruction did not stop attacks immediately");h.succeed();}finally{close.run();}});
        }));
        tests.add(new TestFunction("r117_blight_drain","r117_blight_actors.exact_drain_preserves_spend_timers_and_active_healing","orimod_r117:room",60,0,true,h->{
            var f=new Site(h);var p=Fixtures.player(h);p.setPos(Vec3.atBottomCenterOf(f.center.east(2)));p.setData(SpiritData.FORM,true);p.setData(SpiritData.ENERGY,20f);
            var state=p.getData(SpiritData.ENERGY_STATE);state.lastSpent=-500;state.lastHintSpendAt=-600;
            h.runAfterDelay(20-f.level.getGameTime()%20,()->{try{
                BlightExposure.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));SpiritRecovery.tick(p);
                Fixtures.near(h,p.getData(SpiritData.ENERGY),19.5,"Real drain/passive interaction");h.assertValueEqual(state.lastSpent,-500L,"Drain is treated as spending");h.assertValueEqual(state.lastHintSpendAt,-600L,"Drain triggers low-energy hint");h.succeed();
            }finally{f.close();}});
        }));
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> body){tests.add(new TestFunction("r117_blight_actors","r117_blight_actors."+name,"orimod_r117:room",400,0,true,h->{body.accept(h);h.succeed();}));}
    private static final class Site implements AutoCloseable {
        final ServerLevel level;final BlockPos center;final BlightData data,previous;final Map<BlockPos,BlockState> before=new LinkedHashMap<>();final List<Animal> animals=new ArrayList<>();long clock;
        Site(GameTestHelper h){level=h.getLevel();center=h.absolutePos(new BlockPos(5,0,5)).atY(190);previous=BlightData.get(level);data=new BlightData();level.getDataStorage().set("orimod_blight",data);BlightEcology.clear();CorruptedAnimals.clear();clock=level.getServer().getTickCount();
            for(int x=(center.getX()-64)>>4;x<=(center.getX()+64)>>4;x++)for(int z=(center.getZ()-64)>>4;z<=(center.getZ()+64)>>4;z++)level.getChunk(x,z);
            var floors=new ArrayList<BlockPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var p=center.offset(x,-1,z);set(p,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());floors.add(p);}
            set(center,OriBlocks.GLOOM_CORE.get().defaultBlockState());data.register(center,floors.stream().mapToLong(BlockPos::asLong).toArray());data.initializeEcology(center,floors,true);BlightEcology.wake(level,center);
        }
        void set(BlockPos p,BlockState s){before.putIfAbsent(p,level.getBlockState(p));level.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
        Animal animal(EntityType<? extends Entity> type){var a=(Animal)type.create(level);a.setPos(Vec3.atBottomCenterOf(center.east(2)));animals.add(a);level.addFreshEntity(a);return a;}
        void steps(int n){for(int i=0;i<n;i++)CorruptedAnimals.step(level.getServer(),++clock);}
        public void close(){animals.forEach(Entity::discard);CorruptedAnimals.clear();before.forEach((p,s)->level.setBlock(p,s,Block.UPDATE_CLIENTS));level.getDataStorage().set("orimod_blight",previous);BlightEcology.clear();}
    }
}
