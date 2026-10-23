package cn.wwl.orimod.gametest;
import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class MerchantGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var shard:Shard.values())add(tests,"buy_"+shard.id()+"_once",(h,s)->{
            s.p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withLight(500).withOwned(2047&~shard.bit));
            var quote=s.open(0);h.assertValueEqual(quote.offers().size(),1,"Only missing shard offered");var packet=s.packet(quote,shard.id());
            MerchantTrading.buy(s.p,packet);MerchantTrading.buy(s.p,packet);
            h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).ownedShards(),2047,"Shard not granted");s.balance(500-MerchantRules.price(shard.id()));
            var after=s.quote();h.assertFalse(after.offers().getFirst().available(),"Sold shard offered again");h.assertValueEqual(after.offers().getFirst().id(),shard.id(),"Sale rerolled stock");
        });
        add(tests,"insufficient_light_keeps_progress",(h,s)->{s.p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withLight(79));var q=s.open(0);MerchantTrading.buy(s.p,s.packet(q,q.offers().getFirst().id()));s.balance(79);h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).ownedShards(),0,"Unaffordable shard");h.assertValueEqual(s.quote().message(),"funds","Refusal");});
        for(String forgery:List.of("uuid","entity","token","day","stock"))add(tests,"reject_forged_"+forgery,(h,s)->{
            var q=s.open(0);String product=forgery.equals("stock")?"tree":q.offers().getFirst().id();
            MerchantTrading.buy(s.p,new MerchantBuyPayload(q.entityId()+(forgery.equals("entity")?1:0),forgery.equals("uuid")?UUID.randomUUID():q.merchant(),q.day()+(forgery.equals("day")?1:0),forgery.equals("token")?UUID.randomUUID():q.token(),product));
            s.balance(500);h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).ownedShards(),0,"Forged request granted shard");
        });
        add(tests,"distance_boundary_and_disconnect_close",(h,s)->{
            var q=s.open(0);s.p.setPos(s.m.position().add(6.01,0,0));MerchantTrading.buy(s.p,s.packet(q,q.offers().getFirst().id()));s.balance(500);h.assertFalse((boolean)MerchantTrading.debug(s.p).get("open"),"Out of range session survived");
            s.p.setPos(s.m.position().add(6,0,0));q=s.open(0);String id=q.offers().getFirst().id();MerchantTrading.buy(s.p,s.packet(q,id));s.balance(500-MerchantRules.price(id));
            MerchantTrading.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(s.p));h.assertFalse((boolean)MerchantTrading.debug(s.p).get("open"),"Logout session survived");
        });
        add(tests,"daily_snapshot_survives_reopen_and_nbt",(h,s)->{
            var q=s.open(0);var stock=q.offers().stream().map(MerchantOfferPayload.Offer::id).toList();String id=stock.getFirst();MerchantTrading.buy(s.p,s.packet(q,id));
            var saved=s.m.saveWithoutId(new CompoundTag());var reloaded=OriMobs.GLADES_MERCHANT.get().create(h.getLevel());reloaded.load(saved);
            h.assertValueEqual(reloaded.stock(s.p),stock,"Saved stock rerolled after sale");
            h.getLevel().setDayTime((q.day()+1)*24000);MerchantTrading.tick(new PlayerTickEvent.Post(s.p));
            h.assertFalse(s.quote().offers().stream().anyMatch(o->o.id().equals(id)),"Next day retained owned shard");h.assertValueEqual(s.quote().day(),q.day()+1,"Day did not refresh");
            int balance=s.p.getData(SpiritData.PROGRESS).light();MerchantTrading.buy(s.p,s.packet(q,stock.getLast()));s.balance(balance);reloaded.discard();
        });
        for(var kind:FeatureSites.Kind.values())add(tests,"guide_"+kind.name().toLowerCase()+"_skips_claimed_and_missing",(h,s)->{
            BlockPos first=s.p.blockPosition().east(10), second=first.east(3),missing=first.west(2);
            var block=kind==FeatureSites.Kind.TREE?OriBlocks.SPIRIT_TREE_HEART:OriBlocks.GLOOM_CORE;s.set(first,block.get().defaultBlockState());s.set(second,block.get().defaultBlockState());s.set(missing,Blocks.AIR.defaultBlockState());
            s.data.add(kind,missing);s.data.add(kind,first);s.data.add(kind,second);
            if(kind==FeatureSites.Kind.TREE)s.p.setData(SpiritData.PROGRESS,s.p.getData(SpiritData.PROGRESS).withRewards(Map.of(SpiritTreeHeartBlock.siteKey(h.getLevel(),first),1)));
            else {var tag=new CompoundTag();tag.putLongArray("completed",new long[]{first.asLong()});h.getLevel().getDataStorage().set("orimod_blight",BlightData.load(tag,h.getLevel().registryAccess()));}
            String id=kind==FeatureSites.Kind.TREE?"tree":"blight";var q=s.open(1);MerchantTrading.buy(s.p,s.packet(q,id));s.balance(500-MerchantRules.price(id));
            var guide=s.packets(RuinGuidePayload.class).getLast();h.assertValueEqual(guide.entry(),second,"Guide selected invalid/claimed target");h.assertValueEqual(guide.dimension(),h.getLevel().dimension().location().toString(),"Guide dimension");
        });
        add(tests,"no_feature_target_no_charge_and_clear_feedback",(h,s)->{var q=s.open(1);MerchantTrading.buy(s.p,s.packet(q,"tree"));s.balance(500);h.assertTrue(s.packets(RuinGuidePayload.class).isEmpty(),"Invented guide");h.assertValueEqual(s.quote().message(),"not_found","Missing not-found message");});
        add(tests,"unloaded_index_guides_without_loading",(h,s)->{
            var far=new BlockPos(12_000_000,80,12_000_000);s.p.setPos(Vec3.atBottomCenterOf(far));s.m.bind(1,far,far,0);var target=far.east(200);s.data.add(FeatureSites.Kind.TREE,target);
            var source=h.getLevel().getChunkSource();h.assertTrue(source.getChunkNow(target.getX()>>4,target.getZ()>>4)==null,"Fixture chunk loaded");int count=source.getLoadedChunksCount();
            var q=s.open(1);MerchantTrading.buy(s.p,s.packet(q,"tree"));s.balance(440);h.assertValueEqual(s.packets(RuinGuidePayload.class).getLast().entry(),target,"Unloaded index lost target");h.assertTrue(source.getChunkNow(target.getX()>>4,target.getZ()>>4)==null,"Index force-loaded target");h.assertValueEqual(source.getLoadedChunksCount(),count,"Index loaded chunks");
        });
        add(tests,"invalidated_loaded_target_pruned_and_range_boundary",(h,s)->{
            var origin=new Vec3(12_000_000.5,80,12_000_000.5);var edge=BlockPos.containing(origin).east(2048);var outside=edge.east();s.data.add(FeatureSites.Kind.TREE,outside);s.data.add(FeatureSites.Kind.TREE,edge);
            h.assertValueEqual(s.data.nearest(h.getLevel(),FeatureSites.Kind.TREE,origin,p->true).orElseThrow(),edge,"2048 boundary");h.assertTrue(s.data.nearest(h.getLevel(),FeatureSites.Kind.TREE,origin.add(-.01,0,0),p->true).isEmpty(),"Beyond 2048 accepted");
            var missing=s.p.blockPosition().east(1);s.set(missing,Blocks.AIR.defaultBlockState());s.data.add(FeatureSites.Kind.TREE,missing);h.assertTrue(s.data.nearest(s.p,FeatureSites.Kind.TREE).isEmpty(),"Destroyed tree found");h.assertValueEqual(s.data.count(FeatureSites.Kind.TREE),2,"Stale loaded entry retained");
        });
        add(tests,"merchant_invulnerable_unpushable_peaceful_persistent",(h,s)->{
            var original=s.m.position();h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL,true);s.m.hurt(s.p.damageSources().playerAttack(s.p),100);s.m.hurt(s.p.damageSources().genericKill(),100);s.m.push(5,3,5);s.m.knockback(10,1,1);s.m.tick();
            h.assertTrue(s.m.isAlive() && s.m.getHealth()==20,"Merchant damaged or removed in peaceful");h.assertValueEqual(s.m.position(),original,"Merchant pushed");h.assertFalse(s.m.isPushable() || s.m.canBeLeashed(),"Merchant can be pushed/leashed");h.assertFalse(s.p.startRiding(s.m),"Merchant ridden");h.assertFalse(s.m.startRiding(s.p,true),"Merchant became passenger");h.assertTrue(s.m.isPersistenceRequired(),"Merchant despawns");
            var saved=s.m.saveWithoutId(new CompoundTag());var loaded=OriMobs.GLADES_MERCHANT.get().create(h.getLevel());loaded.load(saved);h.assertValueEqual(loaded.well(),s.m.well(),"Well binding lost");h.assertTrue(loaded.isPersistenceRequired(),"Persistence lost");loaded.discard();
        });
        add(tests,"merchant_hint_once_and_line_waits_for_hint",(h,s)->{
            s.p.setData(SpiritData.FORM,true);var records=new HashMap<String,Integer>();for(var hint:CompanionHints.Hint.values())if(hint!=CompanionHints.Hint.MERCHANT)records.put("hint/"+CompanionHints.wireId(hint,0),1);s.p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(records));
            CompanionHints.check(s.p);h.assertTrue(s.p.getData(SpiritData.PROGRESS).rewards().containsKey("hint/merchant"),"Missing merchant hint");h.assertTrue(CompanionLines.offer(s.p,CompanionLines.Line.FIRST_OUTPOST),"Outpost line not queued");h.assertFalse(CompanionLines.flush(s.p),"Line interrupted hint");s.p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt-=200;
            h.assertTrue(CompanionLines.flush(s.p),"Outpost line not sent");h.assertTrue(s.p.getData(SpiritData.PROGRESS).rewards().containsKey("line/first_outpost"),"Line record missing");h.assertFalse(CompanionLines.offer(s.p,CompanionLines.Line.FIRST_OUTPOST),"Line repeated");
        });
        add(tests,"ruin_search_close_cancels_and_never_charges",(h,s)->{
            try(var placements=new SearchFixture(h)){
                var q=s.open(1);MerchantTrading.buy(s.p,s.packet(q,"ruin"));h.assertTrue((boolean)MerchantTrading.debug(s.p).get("busy"),"Ruin search not asynchronous");MerchantTrading.buy(s.p,s.packet(q,"ruin"));s.balance(500);MerchantTrading.buy(s.p,s.packet(q,"close"));h.assertFalse((boolean)RuinLocator.debug(s.p).get("active"),"Search survived closed trade");s.balance(500);
            }
        });
        for(String outcome:List.of("pay","moved","closed","spent","day"))tests.add(new TestFunction("merchant_search_"+outcome,"merchant.ruin_async_"+outcome,"orimod_gametest:platform_room",600,0,true,h->{
            var s=new Site(h);
            try {
                var fixture=new SearchFixture(h);s.searchCleanup=fixture::close;var level=h.getLevel();
                // The async success/cancellation cases must not start on the last tick of a game day.
                // The explicit "day" case below still advances the quoted day and verifies refusal.
                long beforeTime=level.getDayTime();level.setDayTime(Math.floorDiv(beforeTime,24000)*24000+6000);
                com.mojang.logging.LogUtils.getLogger().info("Merchant async {} fixture time {} -> {}",outcome,beforeTime,level.getDayTime());
                var potential=fixture.placement.getPotentialStructureChunk(level.getSeed(),s.p.chunkPosition().x,s.p.chunkPosition().z);
                s.pin(potential.getWorldPosition(),0);
                var chunk=level.getChunk(potential.x,potential.z);var starts=new HashMap<>(chunk.getAllStarts());
                s.searchCleanup=()->{try{chunk.setAllStarts(starts);}finally{fixture.close();}};
                var at=new RuinPlacement(potential.getWorldPosition().atY(180),net.minecraft.world.level.block.Rotation.NONE);
                var piece=new RuinPiece(level.getStructureManager(),RuinKind.FOREST,at);var entry=BlockPos.containing(at.point(RuinKind.FOREST.layout().entry()));
                var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(RuinKind.FOREST.location());
                chunk.setStartForStructure(structure,new net.minecraft.world.level.levelgen.structure.StructureStart(structure,potential,0,new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece))));
                // Moving an embedded player does not create player tickets. Keep both the structure start
                // and the merchant/7-block cancellation move loaded until the async callback finishes.
                s.pin(entry,8);
                s.p.setPos(Vec3.atBottomCenterOf(entry));s.p.setNoGravity(true);s.m.bind(1,entry,entry.east(),0);
                var q=s.open(1);MerchantTrading.buy(s.p,s.packet(q,"ruin"));boolean[] done={false};long[] finished={Long.MIN_VALUE};long began=level.getGameTime();
                Runnable cleanup=s::close;
                if(outcome.equals("moved"))s.p.setPos(s.m.position().add(7,0,0));
                if(outcome.equals("closed"))MerchantTrading.buy(s.p,s.packet(q,"close"));
                if(outcome.equals("spent"))s.p.setData(SpiritData.PROGRESS,s.p.getData(SpiritData.PROGRESS).withLight(0));
                if(outcome.equals("day"))level.setDayTime((q.day()+1)*24000);
                h.onEachTick(()->{if(done[0])return;try{
                    MerchantTrading.tick(new PlayerTickEvent.Post(s.p));RuinLocator.tick(new PlayerTickEvent.Post(s.p));
                    s.c.channel().runPendingTasks();
                    var guides=s.packets(RuinGuidePayload.class);boolean searching=(boolean)RuinLocator.debug(s.p).get("active");
                    if(!searching && finished[0]==Long.MIN_VALUE)finished[0]=level.getGameTime();
                    if(!searching && level.getGameTime()-finished[0]>=2){
                        if(outcome.equals("pay")){h.assertValueEqual(guides.size(),1,"Missing or duplicate async guide: offer="+s.quote()+" session="+MerchantTrading.debug(s.p)+" player="+s.p.position()+" merchant="+s.m.position()+" removed="+s.m.isRemoved()+" removalReason="+s.m.getRemovalReason()+" alive="+s.p.isAlive()+" day="+level.getDayTime());h.assertValueEqual(guides.getFirst().entry(),entry,"Guide target");s.balance(460);}
                        else {h.assertTrue(guides.isEmpty(),"Cancelled/stale async guide delivered");s.balance(outcome.equals("spent")?0:500);}
                        done[0]=true;cleanup.run();h.succeed();
                    }else h.assertTrue(level.getGameTime()-began<550,"Async search did not finish");
                }catch(Throwable error){done[0]=true;cleanup.run();throw error;}});
            }catch(Throwable error){s.close();throw error;}
        }));
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Site> run){tests.add(new TestFunction("merchant","merchant."+name,"orimod_gametest:platform_room",100,0,true,h->{try(var s=new Site(h)){run.accept(h,s);}h.succeed();}));}
    /** GameTest's flat generator has no ruin placements. Supply one normal random-spread placement only
     * in this test fixture, then exercise the real ticket/future/callback path and restore the generator map.
     * Reflection stays in the GameTest source set; production never accesses Minecraft internals. */
    private static final class SearchFixture implements AutoCloseable {
        final net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement placement=new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(16,6,net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,19721014);
        final Map<net.minecraft.world.level.levelgen.structure.Structure,List<net.minecraft.world.level.levelgen.structure.placement.StructurePlacement>> map;
        final net.minecraft.world.level.levelgen.structure.Structure structure;final List<net.minecraft.world.level.levelgen.structure.placement.StructurePlacement> old;
        @SuppressWarnings("unchecked") SearchFixture(GameTestHelper h){try{var generator=h.getLevel().getChunkSource().getGeneratorState();generator.ensureStructuresGenerated();var field=generator.getClass().getDeclaredField("placementsForStructure");field.setAccessible(true);map=(Map<net.minecraft.world.level.levelgen.structure.Structure,List<net.minecraft.world.level.levelgen.structure.placement.StructurePlacement>>)field.get(generator);structure=h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(RuinKind.FOREST.location());old=map.put(structure,List.of(placement));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
        public void close(){if(old==null)map.remove(structure);else map.put(structure,old);}
    }
    private static final class Site implements AutoCloseable {
        final GameTestHelper h;final Fixtures.ConnectedPlayer c;final ServerPlayer p;final GladesMerchant m;final long time;final Difficulty difficulty;final FeatureSites previous,data=new FeatureSites();final BlightData oldBlight;
        final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks=new HashMap<>();
        final List<net.minecraft.network.protocol.Packet<?>> recorded=new ArrayList<>();
        final Set<net.minecraft.world.level.ChunkPos> pinned=new HashSet<>();
        Runnable searchCleanup=()->{};boolean closed;
        Site(GameTestHelper h){this.h=h;time=h.getLevel().getDayTime();difficulty=h.getLevel().getDifficulty();previous=FeatureSites.get(h.getLevel());oldBlight=BlightData.get(h.getLevel());h.getLevel().getDataStorage().set("orimod_feature_sites",data);c=Fixtures.connectedPlayer(h);p=c.player();p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withLight(500));m=OriMobs.GLADES_MERCHANT.get().create(h.getLevel());m.bind(0,p.blockPosition(),p.blockPosition().east(2),0);h.getLevel().addFreshEntity(m);c.channel().outboundMessages().clear();}
        MerchantOfferPayload open(int job){m.bind(job,m.well(),m.blockPosition(),0);MerchantTrading.open(p,m);return quote();}
        MerchantOfferPayload quote(){return packets(MerchantOfferPayload.class).getLast();}
        MerchantBuyPayload packet(MerchantOfferPayload q,String id){return new MerchantBuyPayload(q.entityId(),q.merchant(),q.day(),q.token(),id);}
        <T> List<T> packets(Class<T> type){recorded.addAll(FxGameTests.drain(c.channel()));return recorded.stream().filter(o->o instanceof ClientboundCustomPayloadPacket packet && type.isInstance(packet.payload())).map(o->type.cast(((ClientboundCustomPayloadPacket)o).payload())).toList();}
        void balance(int amount){h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),amount,"Light balance");}
        void set(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){blocks.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlock(pos,state,18);}
        void pin(BlockPos center,int radius){
            var level=h.getLevel();
            for(int x=(center.getX()-radius)>>4;x<=(center.getX()+radius)>>4;x++)
                for(int z=(center.getZ()-radius)>>4;z<=(center.getZ()+radius)>>4;z++){
                    if(level.setChunkForced(x,z,true))pinned.add(new net.minecraft.world.level.ChunkPos(x,z));
                    level.getChunk(x,z);
                }
        }
        public void close(){
            if(closed)return;closed=true;
            try{
                MerchantTrading.close(p);RuinLocator.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));m.discard();
                blocks.forEach((pos,state)->h.getLevel().setBlock(pos,state,18));
                h.getLevel().getDataStorage().set("orimod_feature_sites",previous);h.getLevel().getDataStorage().set("orimod_blight",oldBlight);
                h.getLevel().setDayTime(time);h.getLevel().getServer().setDifficulty(difficulty,true);
            }finally{
                try{searchCleanup.run();}finally{
                    try{for(var chunk:pinned)h.getLevel().setChunkForced(chunk.x,chunk.z,false);}
                    finally{c.close();}
                }
            }
        }
    }
}
