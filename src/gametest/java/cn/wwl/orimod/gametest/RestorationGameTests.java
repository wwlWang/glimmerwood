package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class RestorationGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){var tests=new ArrayList<TestFunction>();
        for(var kind:List.of(WillWisp.HEART,WillWisp.WING))add(tests,"boss_"+kind.id()+"_creates_personal_persistent_pickups",(h,p)->{
            var level=h.getLevel();var source=h.absolutePos(new BlockPos(8,2,8));p.setPos(Vec3.atCenterOf(source));
            try(var other=Fixtures.connectedPlayer(h)){
                other.player().setPos(p.position().add(2,0,0));
                if(kind==WillWisp.HEART){var boss=OriMobs.GLOOM_HEART.get().create(level);boss.setHome(source);boss.setPos(p.position().add(0,4,0));level.addFreshEntity(boss);boss.die(p.damageSources().playerAttack(p));boss.discard();EscapeSequence.stopAll();}
                else{var boss=OriMobs.GLOOM_MATRIARCH.get().create(level);boss.setHome(source);boss.setPos(p.position().add(0,4,0));level.addFreshEntity(boss);boss.die(p.damageSources().playerAttack(p));boss.discard();}
                var data=RestorationData.get(p.server);var claim=data.claim(p.getUUID(),kind);var second=data.claim(other.player().getUUID(),kind);
                h.assertTrue(claim!=null && second!=null,"Boss did not create one claim per participant");h.assertFalse(claim.entity().equals(second.entity()),"Participants share one entity");
                h.assertValueEqual(claim.source(),source,"Pending position is not the altar");
                var e=(WillWispEntity)level.getEntity(claim.entity());h.assertTrue(e!=null,"No altar pickup entity");
                h.assertFalse(e.hurt(p.damageSources().fellOutOfWorld(),100),"Wisp takes damage");
                h.assertFalse(WillWisps.pickup(other.player(),e),"Another player stole the wisp");
                p.setPos(e.position());h.assertTrue(WillWisps.pickup(p,e),"Owner could not pick up wisp");h.assertValueEqual(p.getData(SpiritData.PROGRESS).wisps(),kind.bit,"Wrong collection bit");
                WillWisps.offer(p,kind,level,source.above(4));h.assertTrue(data.claim(p.getUUID(),kind)==null,"Repeated boss kill recreated collected wisp");
                WillWisps.clear(other.player());
            }
        });
        add(tests,"pending_entity_nbt_and_ledger_reload_keep_identity",(h,p)->{
            var source=h.absolutePos(new BlockPos(10,3,10));WillWisps.offer(p,WillWisp.WING,h.getLevel(),source);
            var data=RestorationData.get(p.server);var claim=data.claim(p.getUUID(),WillWisp.WING);var e=(WillWispEntity)h.getLevel().getEntity(claim.entity());
            var tag=new CompoundTag();e.save(tag);e.discard();var loaded=EntityType.loadEntityRecursive(tag,h.getLevel(),v->v);h.assertTrue(loaded instanceof WillWispEntity,"Entity did not deserialize");
            h.assertTrue(h.getLevel().addFreshEntity(loaded),"Reload rejected");h.assertTrue(WillWisps.valid((WillWispEntity)loaded),"Saved claim/entity no longer match");
            WillWisps.offer(p,WillWisp.WING,h.getLevel(),source.offset(20,0,0));h.assertValueEqual(data.claim(p.getUUID(),WillWisp.WING),claim,"Repeat kill moved or duplicated pending altar");
        });
        add(tests,"unloaded_pending_altar_does_not_load_chunk",(h,p)->{
            var at=new BlockPos(2_500_000,80,2_500_000);var chunks=h.getLevel().getChunkSource();int before=chunks.getLoadedChunksCount();
            WillWisps.offer(p,WillWisp.HEART,h.getLevel(),at);h.assertTrue(chunks.getChunkNow(at.getX()>>4,at.getZ()>>4)==null,"Offer loaded distant altar");h.assertValueEqual(chunks.getLoadedChunksCount(),before,"Offer added a chunk ticket");h.assertTrue(RestorationData.get(p.server).claim(p.getUUID(),WillWisp.HEART)!=null,"Unloaded altar lost claim");
        });
        add(tests,"third_real_purification_awards_each_snapshot_player_independently",(h,p)->{
            try(var other=Fixtures.connectedPlayer(h)){
                var second=other.player();var data=new BlightData();
                for(int i=0;i<3;i++){
                    var source=h.absolutePos(new BlockPos(10+i,3,10));p.setPos(Vec3.atCenterOf(source));second.setPos(p.position().add(1,0,0));
                    h.getLevel().setBlock(source.below(),OriBlocks.BLIGHTED_SOIL.get().defaultBlockState(),2);data.register(source,new long[]{source.below().asLong()});h.assertTrue(data.begin(h.getLevel(),source),"Purification refused");for(int tick=0;tick<600;tick++)data.advance(h.getLevel());
                    for(var player:List.of(p,second)){var claim=RestorationData.get(p.server).claim(player.getUUID(),WillWisp.EARTH);h.assertTrue((claim!=null)==(i==2),"Earth Wisp appeared at wrong individual purification count");if(claim!=null)h.assertValueEqual(claim.source(),source,"Earth Wisp not at third core");}
                }
                h.assertValueEqual(data.pendingCount(),0,"Delivered purification participants retained");WillWisps.clear(second);
            }
        });
        add(tests,"offline_third_purification_login_repairs_pending_earth_claim_once",(h,p)->{
            p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(Map.of("purify/one",1,"purify/two",1)));
            var level=h.getLevel();var source=h.absolutePos(new BlockPos(12,3,12));var old=BlightData.get(level);var tag=old.save(new CompoundTag(),level.registryAccess());
            var entry=new CompoundTag();entry.putLong("center",source.asLong());var ids=new ListTag();ids.add(NbtUtils.createUUID(p.getUUID()));entry.put("players",ids);tag.getList("pending",Tag.TAG_COMPOUND).add(entry);
            try{var data=BlightData.load(tag,level.registryAccess());level.getDataStorage().set("orimod_blight",data);BlightData.login(new PlayerEvent.PlayerLoggedInEvent(p));
                var claim=RestorationData.get(p.server).claim(p.getUUID(),WillWisp.EARTH);h.assertTrue(claim!=null,"Login lost the third purification");h.assertValueEqual(claim.source(),source,"Login spawned earth at player rather than core");BlightData.login(new PlayerEvent.PlayerLoggedInEvent(p));h.assertValueEqual(RestorationData.get(p.server).claim(p.getUUID(),WillWisp.EARTH),claim,"Login duplicated pending wisp");
            }finally{level.getDataStorage().set("orimod_blight",old);}
        });
        for(int bits=0;bits<7;bits++){int mask=bits;add(tests,"missing_wisps_"+bits+"_cannot_start",(h,p)->{var tree=tree(h,p);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withWisps(mask));h.assertFalse(Restoration.start(p,tree),"Incomplete collection started ritual");});}
        add(tests,"great_heart_needs_no_ancestral_blessing_but_human_cannot_start",(h,p)->{
            var tree=tree(h,p);WillWisps.give(p,7);var progress=p.getData(SpiritData.PROGRESS);p.setData(SpiritData.PROGRESS,progress.withRewards(Map.of()));h.assertTrue(Restoration.start(p,tree),"Three wisps should suffice at the great heart");Restoration.cancel(p);p.setData(SpiritData.PROGRESS,progress);p.setData(SpiritData.FORM,false);h.assertFalse(Restoration.start(p,tree),"Human started ritual");
        });
        add(tests,"skip_requires_matching_session_and_settles_once",(h,p)->{
            var tree=tree(h,p);WillWisps.give(p,7);int light=p.getData(SpiritData.PROGRESS).light();h.assertTrue(Restoration.start(p,tree),"Start refused");h.assertFalse(Restoration.skip(p,"forged"),"Forged skip accepted");
            String id=Restoration.session(p).id().toString().replace("-","");h.assertTrue(Restoration.skip(p,id),"Valid skip refused");h.assertFalse(Restoration.active(p),"Skip left movement locked");h.assertTrue(RestorationData.get(p.server).restored(),"Skip omitted world effect");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),light+500,"Wrong reward");h.assertFalse(Restoration.skip(p,id),"Repeated skip settled again");h.assertFalse(Restoration.start(p,tree),"Completed player restarted");
        });
        add(tests,"later_player_receives_personal_reward_in_restored_world",(h,p)->{var tree=tree(h,p);RestorationData.get(p.server).complete();WillWisps.give(p,7);h.assertTrue(Restoration.start(p,tree),"Already-restored world rejected new player");h.assertTrue(Restoration.finish(p),"Later player's completion failed");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),500,"Later player lost personal reward");});
        add(tests,"ritual_blocks_damage_and_position_but_keeps_rotation",(h,p)->{
            var tree=tree(h,p);WillWisps.give(p,7);h.assertTrue(Restoration.start(p,tree),"Start refused");float hp=p.getHealth();p.hurt(p.damageSources().fellOutOfWorld(),10);h.assertValueEqual(p.getHealth(),hp,"Ritual player took damage");
            var origin=Restoration.session(p).origin();p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(origin.x+8,origin.y,origin.z,42,17,true));h.assertTrue(p.position().distanceToSqr(origin)<1e-6,"Movement packet bypassed freeze");h.assertValueEqual(p.getYRot(),42f,"Movement packet lost camera rotation");p.setPos(origin.add(3,0,0));p.setYRot(87);p.setXRot(34);p.setDeltaMovement(1,2,3);Restoration.tick(new PlayerTickEvent.Post(p));h.assertTrue(p.position().distanceToSqr(origin)<1e-6,"Position not held");h.assertValueEqual(p.getYRot(),87f,"Camera yaw was locked");h.assertValueEqual(p.getXRot(),34f,"Camera pitch was locked");h.assertValueEqual(p.getDeltaMovement(),Vec3.ZERO,"Velocity not cleared");
            Restoration.cancel(p);p.invulnerableTime=0;h.assertTrue(p.hurt(p.damageSources().fellOutOfWorld(),2),"Cancellation left invulnerability");
        });
        for(String path:List.of("logout","dimension","death","tree_removed","clear"))add(tests,"cancel_"+path+"_retains_collection_without_reward",(h,p)->{
            var tree=tree(h,p);WillWisps.give(p,7);h.assertTrue(Restoration.start(p,tree),"Start refused");
            switch(path){case "logout"->Restoration.logout(new PlayerEvent.PlayerLoggedOutEvent(p));case "dimension"->Restoration.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));case "death"->Restoration.death(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p,p.damageSources().generic()));case "tree_removed"->{h.getLevel().setBlock(tree,Blocks.AIR.defaultBlockState(),2);Restoration.tick(new PlayerTickEvent.Post(p));}case "clear"->WillWisps.clear(p);}
            h.assertFalse(Restoration.active(p),"Cancellation left session");h.assertFalse(Restoration.completed(p),"Cancellation awarded completion");h.assertValueEqual(p.getData(SpiritData.PROGRESS).wisps(),path.equals("clear")?0:7,"Cancellation lost collection");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),0,"Cancellation paid reward");
        });
        add(tests,"reset_invalidates_offline_receipt_and_preserves_wisps",(h,p)->{
            var tree=tree(h,p);WillWisps.give(p,7);Restoration.start(p,tree);Restoration.finish(p);var saved=p.getData(SpiritData.PROGRESS);Restoration.reset(p.server);h.assertFalse(Restoration.completed(p),"Reset retained online receipt");
            p.setData(SpiritData.PROGRESS,saved);WillWisps.login(new PlayerEvent.PlayerLoggedInEvent(p));h.assertFalse(p.getData(SpiritData.PROGRESS).rewards().containsKey(Restoration.RECEIPT),"Offline receipt not retired on login");h.assertValueEqual(p.getData(SpiritData.PROGRESS).wisps(),7,"Reset erased wisps");
        });
        add(tests,"debug_commands_reject_non_op_and_give_clear_all",(h,p)->{var d=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();cn.wwl.orimod.command.OriCommands.register(d);
            try{var source=p.createCommandSourceStack().withPermission(2);h.assertValueEqual(d.execute("ori wisp give all",source),1,"Give failed");h.assertValueEqual(p.getData(SpiritData.PROGRESS).wisps(),7,"Give did not set bits");d.execute("ori wisp clear",source);h.assertValueEqual(p.getData(SpiritData.PROGRESS).wisps(),0,"Clear failed");d.execute("ori restoration status",source);d.execute("ori restoration reset",source);boolean denied=false;try{d.execute("ori wisp give heart",source.withPermission(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}h.assertTrue(denied,"Non-OP debug access");}catch(Exception e){throw new RuntimeException(e);}
        });
        add(tests,"restored_new_and_reloaded_leaves_are_initialized_without_loading_neighbours",(h,p)->{
            var level=h.getLevel();var leaf=h.absolutePos(new BlockPos(13,5,13));RestorationData.get(p.server).complete();level.setBlock(leaf,OriBlocks.SPIRIT_LEAVES.get().defaultBlockState(),2);
            for(int i=0;i<50;i++)RestorationBloom.step(p.server);h.assertTrue(level.getBlockState(leaf).getValue(SpiritLeavesBlock.BLOOMED),"New leaf ignored restored world");
            var chunk=level.getChunkAt(leaf);RestorationBloom.unload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));RestorationBloom.step(p.server);
            level.setBlock(leaf,level.getBlockState(leaf).setValue(SpiritLeavesBlock.BLOOMED,false),2);
            int before=level.getChunkSource().getLoadedChunksCount();RestorationBloom.load(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk,false));for(int i=0;i<50;i++)RestorationBloom.step(p.server);
            h.assertTrue(level.getBlockState(leaf).getValue(SpiritLeavesBlock.BLOOMED),"Reload failed to reapply world effect");h.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),before,"Bloom loaded neighbouring chunks");
        });
        for(String scenario:List.of("clear","wall","void","magma","water","occupied"))
            add(tests,"viewpoint_"+scenario,(h,p)->viewpoint(h,p,scenario));
        add(tests,"viewpoint_never_loads_missing_chunks",(h,p)->{
            var tree=new BlockPos(24000000,100,24000000);p.setPos(Vec3.atBottomCenterOf(tree.north(2)));
            int chunks=h.getLevel().getChunkSource().getLoadedChunksCount();var before=p.position();
            h.assertValueEqual(RestorationViewpoint.choose(p,tree),before,"Unloaded viewpoint selected");
            h.assertValueEqual(h.getLevel().getChunkSource().getLoadedChunksCount(),chunks,"Viewpoint loaded chunks");
        });
        natural(tests);bloom(tests);return tests;
    }
    private static void viewpoint(GameTestHelper h,ServerPlayer p,String scenario){
        var level=h.getLevel();var heart=h.absolutePos(new BlockPos(30,30,30));
        var old=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        net.minecraft.world.entity.Entity occupant=null;
        try{
            for(var mutable:BlockPos.betweenClosed(heart.offset(-4,-2,-6),heart.offset(4,3,1))){
                var pos=mutable.immutable();old.put(pos,level.getBlockState(pos));level.setBlock(pos,pos.getY()<heart.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            level.setBlock(heart,OriBlocks.GREAT_TREE_HEART.get().defaultBlockState(),2);
            p.setPos(Vec3.atBottomCenterOf(heart.north(1).below()));p.setYRot(17);p.setXRot(23);var before=p.position();
            p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withWisps(7).withRewards(Map.of(SpiritTreeHeartBlock.siteKey(level,heart),1)));
            if(scenario.equals("wall"))for(int x=-4;x<=4;x++)for(int y=0;y<3;y++)level.setBlock(heart.offset(x,y-1,-2),Blocks.STONE.defaultBlockState(),2);
            if(List.of("void","magma","water").contains(scenario))for(int x=-2;x<=2;x++)for(int z=-5;z<=-2;z++){
                level.setBlock(heart.offset(x,-2,z),switch(scenario){case "magma"->Blocks.MAGMA_BLOCK.defaultBlockState();case "water"->Blocks.WATER.defaultBlockState();default->Blocks.AIR.defaultBlockState();},2);
            }
            if(scenario.equals("occupied")){
                var mob=net.minecraft.world.entity.EntityType.COW.create(level);mob.setNoAi(true);mob.moveTo(Vec3.atBottomCenterOf(heart.north(3).below()));level.addFreshEntity(mob);occupant=mob;
            }
            h.assertTrue(Restoration.start(p,heart),"Start refused");var origin=Restoration.session(p).origin();
            if(scenario.equals("clear"))h.assertValueEqual(origin,Vec3.atBottomCenterOf(heart.north(3).below()),"Did not back away from entrance");
            else if(scenario.equals("occupied")){h.assertFalse(origin.equals(Vec3.atBottomCenterOf(heart.north(3).below())),"Teleported into another entity");h.assertTrue(level.noCollision(p,p.getBoundingBox()),"Viewpoint collides");h.assertFalse(p.getBoundingBox().intersects(occupant.getBoundingBox()),"Living entity overlap remains");}
            else h.assertValueEqual(origin,before,"Unsafe corridor/ground moved player");
            h.assertValueEqual(p.position(),origin,"Server did not move to authoritative origin");h.assertValueEqual(p.getYRot(),17f,"Forced yaw");h.assertValueEqual(p.getXRot(),23f,"Forced pitch");
        }finally{Restoration.cancel(p);if(occupant!=null)occupant.discard();old.forEach((pos,state)->level.setBlock(pos,state,2));}
    }
    private static BlockPos tree(GameTestHelper h,ServerPlayer p){var at=h.absolutePos(new BlockPos(7,2,7));h.getLevel().setBlock(at,OriBlocks.GREAT_TREE_HEART.get().defaultBlockState(),2);p.setPos(Vec3.atBottomCenterOf(at).add(0,0,-2));var r=new HashMap<>(p.getData(SpiritData.PROGRESS).rewards());r.put(SpiritTreeHeartBlock.siteKey(p.level(),at),1);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withRewards(r));return at;}
    private static void prepare(ServerPlayer p){p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY);}
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,ServerPlayer> run){tests.add(new TestFunction("restoration_sync","restoration."+name,"orimod_gametest:room",100,0,true,h->{try(var c=Fixtures.connectedPlayer(h)){var p=c.player();Restoration.reset(p.server);prepare(p);try{run.accept(h,p);h.succeed();}finally{WillWisps.clear(p);Restoration.reset(p.server);}}}));}
    private static void natural(List<TestFunction> tests){tests.add(new TestFunction("restoration_natural","restoration.natural_300_ticks_finishes_same_as_skip","orimod_gametest:room",380,0,true,h->{var c=Fixtures.connectedPlayer(h);var p=c.player();Restoration.reset(p.server);prepare(p);var tree=tree(h,p);WillWisps.give(p,7);h.assertTrue(Restoration.start(p,tree),"Start failed");var low=tree.above(4);var high=tree.above(20);h.getLevel().setBlock(low,OriBlocks.SPIRIT_LEAVES.get().defaultBlockState(),2);h.getLevel().setBlock(high,OriBlocks.SPIRIT_LEAVES.get().defaultBlockState(),2);
        h.runAfterDelay(152,()->{h.assertTrue(h.getLevel().getBlockState(low).getValue(SpiritLeavesBlock.BLOOMED),"Lower crown did not light during ceremony");h.assertFalse(h.getLevel().getBlockState(high).getValue(SpiritLeavesBlock.BLOOMED),"Upper crown lit before its cue");});
        h.runAfterDelay(232,()->h.assertTrue(h.getLevel().getBlockState(high).getValue(SpiritLeavesBlock.BLOOMED),"Upper crown cue missing"));
        for(int i=1;i<=301;i++)h.runAfterDelay(i,()->Restoration.tick(new PlayerTickEvent.Post(p)));
        h.runAfterDelay(299,()->h.assertTrue(Restoration.active(p),"Ceremony ended early"));h.runAfterDelay(302,()->{try{h.assertFalse(Restoration.active(p),"Ceremony did not end");h.assertTrue(Restoration.completed(p),"No receipt");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),500,"No personal reward");h.assertTrue(RestorationData.get(p.server).restored(),"No world effect");h.succeed();}finally{WillWisps.clear(p);Restoration.reset(p.server);c.close();}});
    }));}
    public static final class BloomTickMeter {
        boolean measuring;long start,max;
        final net.minecraft.server.level.ServerLevel level;
        GameTestDiskBackpressure.Scope timing;
        BloomTickMeter(net.minecraft.server.level.ServerLevel level){this.level=level;}
        @net.neoforged.bus.api.SubscribeEvent public void pre(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e){
            timing=GameTestDiskBackpressure.measured(level,"restoration-server-tick");start=System.nanoTime();
        }
        @net.neoforged.bus.api.SubscribeEvent public void post(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
            try{if(measuring && RestorationBloom.lastWrites()>0)max=Math.max(max,System.nanoTime()-start);}
            finally{finish();}
        }
        void finish(){if(timing!=null){timing.close();timing=null;}}
    }
    private static void bloom(List<TestFunction> tests){tests.add(new TestFunction("restoration_bloom","restoration.full_tree_bloom_budget_timing_and_reversible_reset","orimod_gametest:room",400,0,true,h->{
        var level=h.getLevel();var server=level.getServer();Restoration.reset(server);var base=h.absolutePos(new BlockPos(12,60,12));var shape=SpiritTreeShape.plan(base);var old=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();var leaves=new ArrayList<BlockPos>();
        shape.blocks().forEach((relative,part)->{var pos=base.offset(relative);old.put(pos,level.getBlockState(pos));var block=switch(part){case LEAVES->OriBlocks.SPIRIT_LEAVES.get().defaultBlockState();case HEART->OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState();case AIR->Blocks.AIR.defaultBlockState();case VINE,VINE_TIP->OriBlocks.SPIRIT_VINE.get().defaultBlockState();default->OriBlocks.SPIRIT_LOG.get().defaultBlockState();};level.setBlock(pos,block,2);if(part==SpiritTreeShape.Part.LEAVES)leaves.add(pos);});
        int[] finishedTick={-1};var meter=new BloomTickMeter(level);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(meter);
        h.runAfterDelay(30,()->{meter.measuring=true;RestorationData.get(server).complete();RestorationBloom.resetMetrics();RestorationBloom.refresh(server);});
        for(int i=31;i<180;i++){int elapsed=i-30;h.runAfterDelay(i,()->{h.assertTrue(RestorationBloom.lastWrites()<=256,"Exceeded shared write budget");if(finishedTick[0]<0 && leaves.stream().allMatch(pos->level.getBlockState(pos).getValue(SpiritLeavesBlock.BLOOMED)))finishedTick[0]=elapsed;});}
        h.runAfterDelay(180,()->{
            h.assertTrue(leaves.size()>1000,"Benchmark is not an entire tree");for(var pos:leaves)h.assertTrue(level.getBlockState(pos).getValue(SpiritLeavesBlock.BLOOMED),"Leaf did not bloom");
            cn.wwl.orimod.OriMod.LOGGER.info("R108_BLOOM_PERF leaves={} ticks={} budget={} maxBatchMs={} maxServerTickMs={} light={}",leaves.size(),finishedTick[0],RestorationBloom.WRITE_BUDGET,RestorationBloom.maxMillis(),meter.max/1e6,level.getBlockState(leaves.getFirst()).getLightEmission());
            meter.measuring=false;meter.finish();net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(meter);
            h.assertTrue(level.getBlockState(base.offset(SpiritTreeShape.HEART)).getValue(SpiritTreeHeartBlock.BLOOMED),"Heart not bloomed");Restoration.reset(server);
        });
        h.runAfterDelay(330,()->{try{for(var pos:leaves){h.assertFalse(level.getBlockState(pos).getValue(SpiritLeavesBlock.BLOOMED),"Reset did not revert leaf");h.assertValueEqual(level.getBlockState(pos).getLightEmission(),9,"Reset left increased light");}h.succeed();}finally{old.forEach((p,s)->level.setBlock(p,s,2));Restoration.reset(server);}});
    }));}
}
