package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.Flash;
import cn.wwl.orimod.config.OriServerConfig;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.network.GloomTidePayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

/** Real player, world, command, event and cache integration. Global settings/terrain are restored per case. */
@GameTestHolder("orimod_gametest")
public final class GloomTideGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests,"wave_uses_horizontal_ring_and_persistent_tags",(h,s)->{
            s.floor(); s.begin();
            int n=GloomTide.spawnWave(s.p,RandomSource.create(1),GloomTideDefense.QUERY);
            h.assertTrue(n>=1 && n<=4,"Wave composition " + n);
            h.assertValueEqual(s.mobs().size(),n,"Fixture must observe every mob in this wave");
            for(var mob:s.mobs()) {
                var d=mob.position().subtract(s.p.position());
                h.assertTrue(GloomTideRules.spawnDistance(d.x*d.x+d.z*d.z),"Outside horizontal ring");
                h.assertTrue(GloomTide.validGround(s.level,mob.blockPosition()),"Invalid ground");
                var copy=mob.getType().create(s.level); copy.load(mob.saveWithoutId(new CompoundTag()));
                h.assertTrue(GloomTide.tagged(copy),"Tag not persisted"); copy.discard();
            }
        });
        add(tests,"fixture_excludes_neighbour_wave_but_observes_own",(h,s)->{
            s.begin();var own=s.mob(s.p.position().add(3,0,0),true);
            try(var other=Fixtures.connectedPlayer(h)){
                var foreign=OriMobs.GLOOM_SPITTER.get().create(s.level);foreign.setPos(s.p.position().add(4,0,0));foreign.getPersistentData().putBoolean(GloomTide.TAG,true);foreign.setTarget(other.player());
                h.assertTrue(s.level.addFreshEntity(foreign),"Foreign fixture rejected");
                try{h.assertTrue(s.mobs().contains(own),"Own fixture missing");h.assertFalse(s.mobs().contains(foreign),"Neighbour counted as this wave");}
                finally{foreign.discard();}
            }
        });
        add(tests,"fixed_seed_mixed_waves_include_beetles_with_expected_frequency",(h,s)->{
            s.floor();s.begin();var random=RandomSource.create(105);int beetles=0,hawks=0,spitters=0;
            for(int wave=0;wave<200;wave++){
                int n=GloomTide.spawnWave(s.p,random,GloomTideDefense.QUERY);h.assertTrue(n>=1 && n<=4,"Wave size");
                var mobs=s.mobs();int b=0,ha=0,sp=0;
                for(var mob:mobs){if(mob instanceof GloomBeetle)b++;else if(mob instanceof GloomHawk)ha++;else if(mob instanceof GloomSpitter)sp++;else h.fail("Unexpected tide mob");mob.discard();}
                h.assertTrue(b<=1 && ha<=1 && sp>=1 && sp<=2,"Wave composition");beetles+=b;hawks+=ha;spitters+=sp;
            }
            h.assertTrue(beetles>=50 && beetles<=90,"Beetle frequency "+beetles);h.assertTrue(hawks>=40 && hawks<=80,"Hawk frequency "+hawks);h.assertTrue(spitters>=270 && spitters<=330,"Spitter frequency "+spitters);
            com.mojang.logging.LogUtils.getLogger().info("R105 fixed seed 105 / 200 waves: {} beetles, {} hawks, {} spitters",beetles,hawks,spitters);
        });
        add(tests,"restored_world_real_waves_only_spitters_at_half_cap",(h,s)->{
            var data=cn.wwl.orimod.world.RestorationData.get(s.p.server);boolean was=data.restored();
            try{data.complete();s.floor();s.begin();var random=RandomSource.create(108);
                for(int wave=0;wave<40;wave++){int n=GloomTide.spawnWave(s.p,random,GloomTideDefense.QUERY);h.assertTrue(n>=1 && n<=2,"Restored wave exceeded half cap");for(var mob:s.mobs()){h.assertTrue(mob instanceof GloomSpitter,"Restored tide spawned beetle/hawk");mob.discard();}}
            }finally{if(!was)data.reset();}
        });
        add(tests,"scheduler_enrolls_once_and_honors_wave_interval",(h,s)->{
            s.floor();s.p.setData(SpiritData.FORM,true);
            h.assertTrue(GloomTide.start(s.level)==null,"Start failed");
            var ledger=s.p.getData(SpiritData.ENERGY_STATE).tide;
            h.assertTrue(ledger.participating,"Eligible player not enrolled");
            h.assertTrue(!s.mobs().isEmpty(),"First scheduled wave missing");
            h.assertValueEqual(ledger.nextWaveAt,s.level.getGameTime()+200,"Next scheduled wave");
            s.mobs().forEach(Entity::discard);
            GloomTide.tickLevel(s.level);h.assertTrue(s.mobs().isEmpty(),"Wave repeated before interval");
            ledger.nextWaveAt=s.level.getGameTime();GloomTide.tickLevel(s.level);
            h.assertTrue(!s.mobs().isEmpty(),"Due wave not spawned");
        });
        add(tests,"protected_ground_attempts_are_bounded",(h,s)->{
            s.floor();s.begin();int[] calls={0};
            int spawned=GloomTide.spawnWave(s.p,RandomSource.create(8),(level,point)->{calls[0]++;return true;});
            h.assertValueEqual(spawned,0,"Protected query ignored");
            h.assertTrue(calls[0]>0 && calls[0]<=40,"Unbounded placement attempts: "+calls[0]);
        });
        add(tests,"nearby_cap_checked_after_every_spawn",(h,s)->{
            s.floor(); s.begin();
            h.assertValueEqual(GloomTide.nearby(s.p),0,"Cap arena must start without foreign tide mobs");
            for(int i=0;i<7;i++) s.mob(s.p.position().add(i,0,2),true);
            h.assertValueEqual(GloomTide.nearby(s.p),7,"Seven cap fixture mobs must be visible before spawning");
            int n=GloomTide.spawnWave(s.p,RandomSource.create(2),GloomTideDefense.QUERY);
            h.assertValueEqual(n,1,"Wave overran remaining slot");
            h.assertValueEqual(GloomTide.nearby(s.p),8,"Cap count");
            h.assertValueEqual(GloomTide.spawnWave(s.p,RandomSource.create(3),GloomTideDefense.QUERY),0,"Cap ignored");
        });
        add(tests,"multiplayer_wave_chunks_do_not_overlap",(h,s)->{
            s.floor(); s.begin(); OriServerConfig.MAX_NEARBY.set(32);
            try(var other=Fixtures.connectedPlayer(h)) {
                var p=other.player();s.owners.add(p.getUUID());p.setData(SpiritData.FORM,true);p.setPos(s.p.position());
                GloomTide.spawnWave(s.p,RandomSource.create(9),GloomTideDefense.QUERY);
                var first=Set.copyOf(s.mobs()); var chunks=new HashSet<ChunkPos>(); first.forEach(m->chunks.add(m.chunkPosition()));
                GloomTide.spawnWave(p,RandomSource.create(9),GloomTideDefense.QUERY);
                h.assertFalse(first.isEmpty(),"First wave missing");
                for(var mob:s.mobs()) if(!first.contains(mob)) h.assertFalse(chunks.contains(mob.chunkPosition()),"Two owners in one tick/chunk");
                h.assertValueEqual(GloomTide.nearby(s.p),GloomTide.nearby(p),"Shared mobs not counted for both players");
            }
        });
        add(tests,"lamps_exclude_spawn_points_over_many_waves",(h,s)->{
            s.floor(); s.begin(); OriServerConfig.MAX_NEARBY.set(64);
            var lamp=s.base.offset(21,0,0);s.set(lamp,OriBlocks.BASH_ANCHOR.get().defaultBlockState());
            int count=0;
            for(int i=0;i<24;i++) {
                count+=GloomTide.spawnWave(s.p,RandomSource.create(i),GloomTideDefense.QUERY);
                for(var mob:s.mobs()) {
                    h.assertTrue(lamp.distToCenterSqr(mob.position())>100,"Spawn inside lamp protection");mob.discard();
                }
            }
            h.assertTrue(count>0,"No unprotected control spawns");
        });
        add(tests,"outpost_well_shelters_twenty_four_waves",(h,s)->{
            s.floor();s.begin();int control=GloomTide.spawnWave(s.p,RandomSource.create(107),GloomTideDefense.QUERY);
            h.assertTrue(control>0,"Unprotected control never spawned");s.mobs().forEach(Entity::discard);
            s.set(s.base,OriBlocks.GLADES_WELL.get().defaultBlockState());
            for(int seed=0;seed<24;seed++)h.assertValueEqual(GloomTide.spawnWave(s.p,RandomSource.create(seed),GloomTideDefense.QUERY),0,"Outpost sanctuary leaked wave "+seed);
            h.assertTrue(GloomTideDefense.blocksProtect(s.level,Vec3.atCenterOf(s.base).add(32,0,0)),"Missing boundary shelter");
            h.assertFalse(GloomTideDefense.blocksProtect(s.level,Vec3.atCenterOf(s.base).add(32.01,0,0)),"Beyond boundary shelter");
        });
        add(tests,"tree_heart_shelters_entire_player_ring",(h,s)->{
            s.floor(); s.begin(); s.set(s.base,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState());
            h.assertValueEqual(GloomTide.spawnWave(s.p,RandomSource.create(1),GloomTideDefense.QUERY),0,"Tree sanctuary leaked");
        });
        add(tests,"purified_centre_shelters_twenty_four_waves_and_rejects_old_radius",(h,s)->{
            var previous=BlightData.get(s.level);var data=new BlightData();s.level.getDataStorage().set("orimod_blight",data);
            try {
                s.floor();s.begin();int oldSpawns=0,protectedSpawns=0;var centre=Vec3.atCenterOf(s.base);
                // Same real ground, player and fixed seeds: the former radius leaks through the outer ring.
                for(int seed=0;seed<24;seed++){
                    oldSpawns+=GloomTide.spawnWave(s.p,RandomSource.create(seed),(level,point)->point.distanceToSqr(centre)<=24*24);
                    s.mobs().forEach(Entity::discard);
                }
                h.assertTrue(oldSpawns>0,"Old-radius control never spawned; fixture would hide the regression");
                s.set(s.base.below(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState());
                data.register(s.base,new long[]{s.base.below().asLong()});h.assertTrue(data.begin(s.level,s.base),"Purification did not begin");
                for(int i=0;i<600;i++)data.advance(s.level);h.assertTrue(data.completed(s.base),"Purification did not finish");
                for(int seed=0;seed<24;seed++)protectedSpawns+=GloomTide.spawnWave(s.p,RandomSource.create(seed),GloomTideDefense.QUERY);
                h.assertValueEqual(protectedSpawns,0,"Purified centre leaked tide waves");h.assertTrue(s.mobs().isEmpty(),"Tide mobs spawned beside protected player");
                com.mojang.logging.LogUtils.getLogger().info("R106 sanctuary / 24 fixed-seed waves: old radius={}, purified radius={}",oldSpawns,protectedSpawns);
            } finally {s.level.getDataStorage().set("orimod_blight",previous);}
        });
        add(tests,"active_flash_skips_own_wave_and_protects_teammate",(h,s)->{
            s.floor();s.begin();s.p.setData(SpiritData.ABILITIES,Ability.FLASH.bit);
            h.assertTrue(Flash.keepAlive(s.p),"Flash refused");
            h.assertValueEqual(GloomTide.spawnWave(s.p,RandomSource.create(1),GloomTideDefense.QUERY),0,"Flash owner wave");
            h.assertTrue(GloomTideDefense.protects(s.level,s.p.position().add(32,0,0)),"32-block teammate protection missing");
            h.assertFalse(GloomTideDefense.protects(s.level,s.p.position().add(32.01,0,0)),"Flash beyond radius");
        });
        for(String type:List.of("lamp","heart","well")) add(tests,type+"_place_and_remove_invalidate_cached_absence_and_presence",(h,s)->{
            var at=Vec3.atCenterOf(s.base); h.assertFalse(GloomTideDefense.blocksProtect(s.level,at),"Fixture protected");
            s.set(s.base,(type.equals("lamp")?OriBlocks.BASH_ANCHOR:type.equals("heart")?OriBlocks.SPIRIT_TREE_HEART:OriBlocks.GLADES_WELL).get().defaultBlockState());
            h.assertTrue(GloomTideDefense.blocksProtect(s.level,at),"Placement waited for cache expiry");
            if(type.equals("lamp")) {
                SpiritLanternBlock.ignite(s.level,s.base,40);
                h.assertTrue(GloomTideDefense.blocksProtect(s.level,at),"Lit lamp lost protection");
            }
            s.set(s.base,Blocks.AIR.defaultBlockState());
            h.assertFalse(GloomTideDefense.blocksProtect(s.level,at),"Removal waited for cache expiry");
        });
        add(tests,"defense_spheres_have_exact_boundaries",(h,s)->{
            s.set(s.base,OriBlocks.BASH_ANCHOR.get().defaultBlockState());var at=Vec3.atCenterOf(s.base);
            h.assertTrue(GloomTideDefense.blocksProtect(s.level,at.add(10,0,0)),"Lamp inclusive edge");
            h.assertFalse(GloomTideDefense.blocksProtect(s.level,at.add(10.01,0,0)),"Lamp outside edge");
            s.set(s.base,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState());
            h.assertTrue(GloomTideDefense.blocksProtect(s.level,at.add(0,32,0)),"Tree inclusive vertical edge");
            h.assertFalse(GloomTideDefense.blocksProtect(s.level,at.add(0,32.01,0)),"Tree outside vertical edge");
        });
        add(tests,"defense_never_loads_missing_chunks",(h,s)->{
            var pos=new BlockPos(20_000_000,100,20_000_000);
            h.assertTrue(s.level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null,"Fixture chunk loaded");
            h.assertFalse(GloomTideDefense.blocksProtect(s.level,Vec3.atCenterOf(pos)),"Absent chunk protected");
            h.assertTrue(s.level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null,"Defense loaded chunk");
        });
        add(tests,"chunk_unload_drops_section_cache",(h,s)->{
            s.set(s.base,OriBlocks.BASH_ANCHOR.get().defaultBlockState());
            h.assertTrue(GloomTideDefense.blocksProtect(s.level,Vec3.atCenterOf(s.base)),"Lamp fixture");
            NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(s.level.getChunkAt(s.base)));
            s.set(s.base,Blocks.AIR.defaultBlockState());
            h.assertFalse(GloomTideDefense.blocksProtect(s.level,Vec3.atCenterOf(s.base)),"Stale unloaded lamp");
        });
        add(tests,"ground_rejects_tree_roof_water_and_low_ceiling",(h,s)->{
            for(var block:List.of(Blocks.STONE,OriBlocks.GREAT_SPIRIT_WOOD.get(),OriBlocks.GREAT_SPIRIT_ROOT.get(),OriBlocks.SPIRIT_LOG.get(),OriBlocks.SPIRIT_LEAVES.get(),Blocks.OAK_LEAVES,Blocks.WATER,Blocks.AIR)) {
                s.set(s.base.below(),block.defaultBlockState());
                h.assertValueEqual(GloomTide.validGround(s.level,s.base),block==Blocks.STONE,"Ground " + block);
            }
            s.set(s.base.below(),Blocks.STONE.defaultBlockState());s.set(s.base.above(),Blocks.STONE.defaultBlockState());
            h.assertFalse(GloomTide.validGround(s.level,s.base),"One-block headroom");s.set(s.base.above(),Blocks.AIR.defaultBlockState());
            s.set(s.base,Blocks.WATER.defaultBlockState());h.assertFalse(GloomTide.validGround(s.level,s.base),"Wet feet");
        });
        for(String disabled:List.of("peaceful","gamerule","config","human","unawakened","creative","spectator")) add(tests,"no_waves_"+disabled,(h,s)->{
            s.begin();
            switch(disabled) {
                case "peaceful"->s.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);
                case "gamerule"->s.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s.level.getServer());
                case "config"->OriServerConfig.ENABLED.set(false);
                case "human"->s.p.setData(SpiritData.FORM,false);
                case "unawakened"->s.p.setData(SpiritData.AWAKENED,false);
                case "creative"->s.p.setGameMode(GameType.CREATIVE);
                case "spectator"->s.p.setGameMode(GameType.SPECTATOR);
            }
            h.assertValueEqual(GloomTide.spawnWave(s.p,RandomSource.create(1),GloomTideDefense.QUERY),0,"Disabled condition " + disabled);
        });
        add(tests,"underground_and_active_boss_exclude_player",(h,s)->{
            var low=new BlockPos(s.base.getX(),s.level.getSeaLevel()-10,s.base.getZ());s.p.setPos(Vec3.atBottomCenterOf(low));
            s.set(low.above(2),Blocks.STONE.defaultBlockState());h.assertFalse(GloomTide.eligible(s.p),"Underground eligible");
            s.p.setPos(Vec3.atBottomCenterOf(s.base));
            var boss=OriMobs.GLOOM_HEART.get().create(s.level);boss.setPos(s.p.position().add(3,0,0));boss.setTarget(s.p);s.level.addFreshEntity(boss);
            try { h.assertFalse(GloomTide.eligible(s.p),"Boss fighter eligible"); } finally {boss.discard();}
        });
        add(tests,"commands_require_night_and_stop_suppresses_natural_restart",(h,s)->{
            s.level.setDayTime(6000);h.assertValueEqual(s.command("ori tide start"),0,"Day command succeeded");
            s.level.setDayTime(4*24000+18000);h.assertValueEqual(s.command("ori tide start"),1,"Night command failed");
            h.assertTrue(GloomTide.isActive(s.level),"Start inactive");h.assertValueEqual(s.command("ori tide status"),1,"Status failed");
            h.assertValueEqual(s.command("ori tide stop"),1,"Stop failed");GloomTide.tickLevel(s.level);
            h.assertFalse(GloomTide.isActive(s.level),"Natural moon restarted stopped night");
        });
        add(tests,"time_set_day_expires_force_and_cleans_only_tagged",(h,s)->{
            s.begin();var tide=s.mob(s.p.position().add(2,0,0),true);var ordinary=s.mob(s.p.position().add(3,0,0),false);
            s.level.setDayTime(1000);GloomTide.tickLevel(s.level);
            h.assertFalse(GloomTide.isActive(s.level),"Force survived day jump");h.assertTrue(tide.isRemoved(),"Loaded tide mob survived");
            h.assertFalse(ordinary.isRemoved(),"Ordinary mob removed");ordinary.discard();
            s.level.setDayTime(18000);GloomTide.tickLevel(s.level);h.assertFalse(GloomTide.isActive(s.level),"Force restarted next time night was entered");
        });
        add(tests,"zero_light_grant_has_no_state_or_packet_side_effects",(h,s)->{
            var progress=s.p.getData(SpiritData.PROGRESS);s.connection.channel().outboundMessages().clear();
            h.assertValueEqual(SpiritProgression.grantLight(s.p,0),0,"Zero grant result");
            h.assertTrue(s.p.getData(SpiritData.PROGRESS)==progress,"Zero grant rewrote progress");
            h.assertTrue(s.connection.channel().outboundMessages().isEmpty(),"Zero grant sent a packet or notification");
        });
        add(tests,"five_real_kills_settle_once_on_dawn",(h,s)->{
            s.begin(); s.kills(5);h.assertValueEqual(s.p.getData(SpiritData.ENERGY_STATE).tide.kills,5,"Death events not counted");
            s.dawn();h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),50,"Dawn award");
            GloomTide.tickLevel(s.level);h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),50,"Repeated dawn award");
        });
        add(tests,"same_day_rollback_cannot_award_twice",(h,s)->{
            s.begin();s.kills(5);s.dawn();s.level.setDayTime(18000);s.begin();s.kills(5);s.dawn();
            h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),50,"Same day paid twice");
        });
        add(tests,"earlier_day_rollback_is_a_new_night",(h,s)->{
            s.level.setDayTime(2*24000+18000);s.begin();s.kills(5);s.dawn();
            s.level.setDayTime(24000+18000);s.begin();
            h.assertValueEqual(s.p.getData(SpiritData.ENERGY_STATE).tide.kills,0,"Earlier day kept later kills");
            s.kills(5);s.dawn();h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),100,"Earlier night not awarded");
        });
        add(tests,"cancelled_death_and_ordinary_mobs_do_not_count",(h,s)->{
            s.begin();var mob=s.mob(s.p.position().add(2,0,0),true);
            var event=new LivingDeathEvent(mob,s.p.damageSources().playerAttack(s.p));event.setCanceled(true);GloomTide.death(event);
            h.assertValueEqual(s.p.getData(SpiritData.ENERGY_STATE).tide.kills,0,"Cancelled death counted");mob.discard();
            s.mob(s.p.position().add(2,0,0),false).hurt(s.p.damageSources().playerAttack(s.p),1000);
            h.assertValueEqual(s.p.getData(SpiritData.ENERGY_STATE).tide.kills,0,"Ordinary death counted");
        });
        add(tests,"real_tide_death_drops_extra_spirit_light",(h,s)->{
            s.begin();var mob=s.mob(s.p.position().add(3,0,0),true);int expected=SpiritProgression.lightFor(mob.getMaxHealth(),true);
            mob.hurt(s.p.damageSources().playerAttack(s.p),1000);
            int actual=s.level.getEntitiesOfClass(ItemEntity.class,s.p.getBoundingBox().inflate(8),e->e.getItem().is(OriItems.SPIRIT_LIGHT)).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertValueEqual(actual,expected,"Tide light drop multiplier");
        });
        add(tests,"saved_mob_join_outside_tide_is_discarded_without_loot",(h,s)->{
            s.begin();var mob=s.mob(s.p.position().add(2,0,0),true);var saved=mob.saveWithoutId(new CompoundTag());mob.discard();s.dawn();
            var loaded=OriMobs.GLOOM_SPITTER.get().create(s.level);loaded.load(saved);
            var event=NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(loaded,s.level,true));
            h.assertTrue(event.isCanceled() && loaded.isRemoved(),"Expired saved mob joined");
            h.assertTrue(s.level.getEntitiesOfClass(ItemEntity.class,s.p.getBoundingBox().inflate(8)).isEmpty(),"Discard generated loot");
        });
        add(tests,"saved_mob_join_during_tide_is_kept",(h,s)->{
            s.begin();var mob=s.mob(s.p.position().add(2,0,0),true);var saved=mob.saveWithoutId(new CompoundTag());mob.discard();
            var loaded=OriMobs.GLOOM_SPITTER.get().create(s.level);loaded.load(saved);
            var event=NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(loaded,s.level,true));
            h.assertFalse(event.isCanceled() || loaded.isRemoved(),"Current-night mob rejected");loaded.discard();
        });
        add(tests,"login_start_end_payloads_and_devtools_status",(h,s)->{
            s.connection.channel().outboundMessages().clear();s.begin();
            GloomTide.login(new PlayerEvent.PlayerLoggedInEvent(s.p));
            h.assertTrue(s.packets().stream().anyMatch(p->p.active() && p.intensity()==.35f),"Missing active/login payload");
            h.assertTrue(GloomTide.status(s.p).keySet().equals(Set.of("active","nearby","kills","nextWaveIn")),"Devtools schema");
            s.dawn();h.assertTrue(s.packets().stream().anyMatch(p->!p.active() && p.intensity()==0),"Missing end payload");
        });
        add(tests,"dimension_change_sends_inactive_and_disallows_nether",(h,s)->{
            s.begin();var nether=s.level.getServer().getLevel(Level.NETHER);
            s.p.teleportTo(nether,0,100,0,0,0);
            h.assertFalse(GloomTide.isActive(nether),"Nether tide");
            h.assertFalse(GloomTide.eligible(s.p),"Nether player eligible");
            h.assertTrue(GloomTide.start(nether)!=null,"Nether force accepted");
            h.assertTrue(s.packets().stream().anyMatch(p->!p.active()),"Dimension stop payload missing");
        });
        return tests;
    }
    private static void add(List<TestFunction> list,String name,BiConsumer<GameTestHelper,Site> run) {
        // These cases mutate the same tide/config/RestorationData and span 61x61 arenas.
        // A per-case batch prevents a waiting case from interleaving with another case's
        // scheduled waves. Every case needs loaded entity sections, not just the cap test:
        // otherwise mobs added to inaccessible sections escape both observation and cleanup.
        list.add(new TestFunction("gloom_tide_"+name,"gloom_tide."+name,"orimod_gametest:room",RING_READY_TICKS+20,0,true,h->{
            loadedRing(h,name, () -> {try(var site=new Site(h,arena(h,name))) {run.accept(h,site);} h.succeed();});
        }));
    }
    private static BlockPos arena(GameTestHelper h,String name) {
        // nearby() intentionally counts all players' tide mobs. Ownership filtering in mobs()
        // cannot isolate this cap test: delayed entity readiness lets neighbouring waves coexist.
        // Give it a separate physical arena, with the same private tickets as its spawn ring.
        return h.absolutePos(name.equals("nearby_cap_checked_after_every_spawn")
                ? new BlockPos(4096,0,4096) : BlockPos.ZERO);
    }
    private static final int RING_READY_TICKS=2000;
    private static final TicketType<ChunkPos> TIDE_TICKET=TicketType.create("orimod_gametest_tide",Comparator.comparingLong(ChunkPos::toLong));
    /** This 61x61 arena lies outside room's tickets. Block writes alone do not make entity sections ready.
     * Acquire only our own tickets, wait before touching global tide settings, and release on every exit. */
    private static void loadedRing(GameTestHelper h,String name,Runnable test) {
        var level=h.getLevel();var base=arena(h,name);var chunks=new ArrayList<ChunkPos>();
        var added=new ArrayList<ChunkPos>();
        var cleaned=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{if(cleaned.compareAndSet(false,true))added.forEach(c->level.getChunkSource().removeRegionTicket(TIDE_TICKET,c,2,c));};
        long began=System.nanoTime();int[] initiallyMissing={0,0};
        try {
            for(int x=(base.getX()-30)>>4;x<=(base.getX()+30)>>4;x++)
                for(int z=(base.getZ()-30)>>4;z<=(base.getZ()+30)>>4;z++) {
                    var c=new ChunkPos(x,z);chunks.add(c);
                    if(level.getChunkSource().getChunkNow(x,z)==null)initiallyMissing[0]++;
                    if(!level.areEntitiesLoaded(c.toLong()))initiallyMissing[1]++;
                    // A private ticket type cannot be removed by neighbouring GameTests releasing FORCED.
                    added.add(c);level.getChunkSource().addRegionTicket(TIDE_TICKET,c,2,c);
                    level.getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,true);
                }
            long started=level.getGameTime();
            double acquisitionMs=(System.nanoTime()-began)/1_000_000.;
            h.onEachTick(()->{
                if(cleaned.get())return;
                try {
                    long waited=level.getGameTime()-started;
                    long missingBlocks=chunks.stream().filter(c->level.getChunkSource().getChunkNow(c.x,c.z)==null).count();
                    long missingEntities=chunks.stream().filter(c->!level.areEntitiesLoaded(c.toLong())).count();
                    if(waited==80||waited==400)com.mojang.logging.LogUtils.getLogger().info(
                            "Tide fixture readiness at {}: blocks={}, entities={}, acquisition_ms={}, total_ms={}, pending={}",waited,missingBlocks,missingEntities,acquisitionMs,(System.nanoTime()-began)/1_000_000.,
                            chunks.stream().filter(c->!level.areEntitiesLoaded(c.toLong())).map(c->{var chunk=level.getChunkSource().getChunkNow(c.x,c.z);return c+":"+(chunk==null?"absent":chunk.getFullStatus().name());}).toList());
                    if(missingBlocks!=0 || missingEntities!=0) {
                        h.assertTrue(waited<RING_READY_TICKS,"Tide fixture ring did not load block/entity data: blocks="+missingBlocks+", entities="+missingEntities);
                        // GameTestServer.waitUntilNextTick() does not sleep. Hundreds of test ticks
                        // can elapse in under a second while the entity-region IO is still pending.
                        // Yield briefly only during fixture readiness; do not change game time,
                        // ticket levels, the wave assertions, or production scheduling.
                        java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
                        return;
                    }
                    com.mojang.logging.LogUtils.getLogger().info(
                            "Tide fixture {} ready: ticks={}, acquisition_ms={}, total_ms={}, initially_missing_blocks={}, initially_missing_entities={}",name,waited,acquisitionMs,(System.nanoTime()-began)/1_000_000.,initiallyMissing[0],initiallyMissing[1]);
                    try {test.run();} finally {cleanup.run();}
                }catch(Throwable error){cleanup.run();throw error;}
            });
        }catch(Throwable error){cleanup.run();throw error;}
    }
    private static final class Site implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final Fixtures.ConnectedPlayer connection;final ServerPlayer p;final BlockPos base;
        final long time;final Difficulty difficulty;final boolean spawning,loot,enabled;final int interval,max;
        final Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        final Set<UUID> owners=new HashSet<>(), fixtureMobs=new HashSet<>();
        Site(GameTestHelper h,BlockPos origin) {
            this.h=h;level=h.getLevel();time=level.getDayTime();difficulty=level.getDifficulty();
            spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);loot=level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
            enabled=OriServerConfig.ENABLED.get();interval=OriServerConfig.WAVE_INTERVAL.get();max=OriServerConfig.MAX_NEARBY.get();
            GloomTide.reset();level.setDayTime(18000);level.getServer().setDifficulty(Difficulty.NORMAL,true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true,level.getServer());
            OriServerConfig.ENABLED.set(true);OriServerConfig.MAX_NEARBY.set(8);OriServerConfig.WAVE_INTERVAL.set(200);
            connection=Fixtures.connectedPlayer(h);p=connection.player();owners.add(p.getUUID());
            base=new BlockPos(origin.getX(),180,origin.getZ());p.setPos(Vec3.atBottomCenterOf(base));
            p.setData(SpiritData.FORM,false);p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY);
            set(base,Blocks.AIR.defaultBlockState());set(base.above(),Blocks.AIR.defaultBlockState());
        }
        void set(BlockPos pos,BlockState state) {saved.putIfAbsent(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,state,18);}
        void floor() {
            for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++) {
                set(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());set(base.offset(x,0,z),Blocks.AIR.defaultBlockState());set(base.offset(x,1,z),Blocks.AIR.defaultBlockState());
            }
        }
        void begin() {
            p.setData(SpiritData.FORM,false);h.assertTrue(GloomTide.start(level)==null,"Tide refused");p.setData(SpiritData.FORM,true);
            p.getData(SpiritData.ENERGY_STATE).tide.begin(GloomTideRules.day(level.getDayTime()),level.getGameTime());
        }
        GloomSpitter mob(Vec3 at,boolean tagged) {
            var m=OriMobs.GLOOM_SPITTER.get().create(level);m.setPos(at);m.setNoAi(true);if(tagged)m.getPersistentData().putBoolean(GloomTide.TAG,true);
            h.assertTrue(level.addFreshEntity(m),"Fixture mob rejected");fixtureMobs.add(m.getUUID());return m;
        }
        // Neighbouring arenas overlap this box. Production waves set their owner as target;
        // manually spawned fixture mobs are tracked explicitly, including no-AI and reloaded copies.
        boolean owns(Mob mob) {return fixtureMobs.contains(mob.getUUID()) || mob.getTarget()!=null&&owners.contains(mob.getTarget().getUUID());}
        List<Mob> mobs() {return level.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(55),m->GloomTide.tagged(m)&&owns(m));}
        void kills(int n) {for(int i=0;i<n;i++)mob(Vec3.atBottomCenterOf(base).add(3,0,0),true).hurt(p.damageSources().playerAttack(p),1000);}
        void dawn() {level.setDayTime(GloomTideRules.day(level.getDayTime())*24000+23000);GloomTide.tickLevel(level);}
        int command(String cmd) {
            try {return level.getServer().getCommands().getDispatcher().execute(cmd,p.createCommandSourceStack().withPermission(4));}
            catch(Exception e) {throw new AssertionError(e);}
        }
        List<GloomTidePayload> packets() {return connection.channel().outboundMessages().stream().filter(packet->packet instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof GloomTidePayload)
                .map(packet->(GloomTidePayload)((ClientboundCustomPayloadPacket)packet).payload()).toList();}
        public void close() {
            Flash.stop(p);
            var box=new net.minecraft.world.phys.AABB(base).inflate(65);
            for(var entity:level.getEntities((Entity)null,box,e->e instanceof Mob mob&&owns(mob) || e instanceof ItemEntity))entity.discard();
            connection.close();GloomTide.reset();
            saved.forEach((pos,state)->level.setBlock(pos,state,18));
            level.setDayTime(time);level.getServer().setDifficulty(difficulty,true);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot,level.getServer());
            OriServerConfig.ENABLED.set(enabled);OriServerConfig.WAVE_INTERVAL.set(interval);OriServerConfig.MAX_NEARBY.set(max);
        }
    }
}
