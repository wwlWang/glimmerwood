package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.progression.CompanionLines.Line;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

/** R102: actual hooks and payloads, with unsaved queues and loaded structure references. */
@GameTestHolder("orimod_gametest")
public final class CompanionLinesGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(boolean hawk:List.of(false,true)) add(tests,(hawk?"matriarch":"heart")+"_opening_once_per_encounter_and_saved_relock",(h,s)->{
            Mob boss=hawk?OriMobs.GLOOM_MATRIARCH.get().create(h.getLevel()):OriMobs.GLOOM_HEART.get().create(h.getLevel());
            boss.setPos(s.p.position().add(2,0,0));
            try {
                boss.setTarget(s.p);s.sent(Line.BOSS_START);
                s.state().lines.spoken.put(Line.BOSS_START,s.now()-6000);s.ready();
                boss.setTarget(null);boss.setTarget(s.p);
                h.assertTrue(s.state().lines.pending==null,"Same encounter reopened after cooldown");
                Mob restored=hawk?OriMobs.GLOOM_MATRIARCH.get().create(h.getLevel()):OriMobs.GLOOM_HEART.get().create(h.getLevel());
                restored.load(boss.saveWithoutId(new CompoundTag()));restored.setTarget(s.p);
                h.assertTrue(s.state().lines.pending==null,"Saved encounter reopened");restored.discard();
            } finally {boss.discard();}
        });
        add(tests,"new_boss_encounter_respects_6000_tick_player_cooldown",(h,s)->{
            for(int i=0;i<3;i++) {
                var boss=OriMobs.GLOOM_HEART.get().create(h.getLevel());boss.setPos(s.p.position().add(2,0,0));
                try {
                    if(i==2)s.state().lines.spoken.put(Line.BOSS_START,s.now()-6000);
                    boss.setTarget(s.p);
                    if(i==1)h.assertTrue(s.state().lines.pending==null,"New boss bypassed player cooldown");
                    else{s.ready();s.sent(Line.BOSS_START);}
                } finally {boss.discard();}
            }
        });
        add(tests,"matriarch_victory_hook_sends_defeated_line",(h,s)->{GloomMatriarch.victory(s.p);s.sent(Line.BOSS_DEFEATED);});
        add(tests,"heart_defeat_escape_replaces_victory_line",(h,s)->{
            var boss=OriMobs.GLOOM_HEART.get().create(h.getLevel());boss.setPos(s.p.position().add(2,0,0));
            try {boss.die(s.p.damageSources().playerAttack(s.p));s.sent(Line.ESCAPE);}
            finally {boss.discard();EscapeSequence.stop(s.p);}
        });
        add(tests,"tree_claim_records_line_only_when_sent_and_only_once",(h,s)->{
            var pos=h.absolutePos(new BlockPos(6,2,5));var old=h.getLevel().getBlockState(pos);
            try {
                h.getLevel().setBlock(pos,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),18);
                h.assertTrue(SpiritTreeHeartBlock.claim(s.p,pos),"Tree claim failed");
                h.assertFalse(s.seen(Line.TREE_FOUND),"Recorded before speaking");s.sent(Line.TREE_FOUND);
                h.assertTrue(s.seen(Line.TREE_FOUND),"Once record missing");
                s.ready();h.assertFalse(CompanionLines.offer(s.p,Line.TREE_FOUND),"Tree line repeated");
            } finally {h.getLevel().setBlock(pos,old,18);}
        });
        add(tests,"tide_transition_hooks_preserve_functional_actionbar_packets",(h,s)->{
            var level=h.getLevel();var diff=level.getDifficulty();boolean spawn=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            try {
                level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
                level.setDayTime(18000);s.p.setPos(s.p.getX(),180,s.p.getZ());
                h.assertTrue(GloomTide.start(level)==null,"Tide start failed");s.sent(Line.TIDE_BEGIN);
                h.assertTrue(s.connection.channel().outboundMessages().stream().anyMatch(packet->packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket message && message.overlay()),"Functional tide message missing");
                s.ready();GloomTide.stop(level);s.sent(Line.TIDE_END);
            } finally {
                GloomTide.reset();level.getServer().setDifficulty(diff,true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawn,level.getServer());
            }
        });
        add(tests,"pending_latest_wins_and_unsent_once_record_is_not_consumed",(h,s)->{
            CompanionLines.offer(s.p,Line.TREE_FOUND);CompanionLines.offer(s.p,Line.ESCAPE);s.sent(Line.ESCAPE);
            h.assertFalse(s.seen(Line.TREE_FOUND),"Overwritten once line consumed");
        });
        add(tests,"shared_hint_gap_expires_pending_line_without_record",(h,s)->{
            s.state().lastCompanionHintAt=s.now();CompanionLines.offer(s.p,Line.TREE_FOUND);
            h.assertFalse(CompanionLines.flush(s.p),"Shared 200 tick gap ignored");
            s.state().lines.pending=new CompanionLines.Pending(Line.TREE_FOUND,s.now()-241);s.ready();
            h.assertFalse(CompanionLines.flush(s.p),"Expired line sent");h.assertFalse(s.seen(Line.TREE_FOUND),"Expired once record consumed");
        });
        add(tests,"low_health_waits_at_most_sixty_ticks",(h,s)->{
            s.state().lastCompanionHintAt=s.now();CompanionLines.offer(s.p,Line.LOW_HEALTH);
            s.state().lines.pending=new CompanionLines.Pending(Line.LOW_HEALTH,s.now()-60);
            CompanionLines.observe(s.p);
            h.assertTrue(s.state().lines.pending!=null,"Low-health line expired at the inclusive boundary");
            h.assertFalse(CompanionLines.flush(s.p),"Low-health line bypassed shared gap");
            s.state().lines.pending=new CompanionLines.Pending(Line.LOW_HEALTH,s.now()-61);s.ready();
            h.assertFalse(CompanionLines.flush(s.p),"Stale low-health line sent");
            h.assertTrue(s.state().lines.pending==null,"Stale low-health line retained");
            h.assertFalse(s.state().lines.spoken.containsKey(Line.LOW_HEALTH),"Unsent line consumed cooldown");
        });
        for(boolean tree:List.of(true,false)) addHintWait(tests,tree);
        add(tests,"hint_beats_line_in_real_tick_event_order",(h,s)->{
            var pos=new BlockPos(7,2,5);h.setBlock(pos,OriBlocks.BASH_ANCHOR.get());
            var rewards=new HashMap<>(s.p.getData(SpiritData.PROGRESS).rewards());rewards.remove("hint/bash_anchor");s.p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(rewards));
            CompanionLines.offer(s.p,Line.TREE_FOUND);
            // Both production listeners see the same eligible scan tick; player events precede the level flush.
            s.alignScanTick();net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(s.p));
            CompanionLines.flushLevel(new LevelTickEvent.Post(()->true,h.getLevel()));
            h.assertTrue(s.packets().stream().anyMatch(p->p.kind()==CompanionHintPayload.Kind.HINT && p.id().equals("bash_anchor")),"Hint not delivered");
            h.assertTrue(s.packets().stream().noneMatch(p->p.kind()==CompanionHintPayload.Kind.LINE),"Line displaced hint");
        });
        for(String change:List.of("human","unawakened","dead","spectator")) add(tests,"gates_"+change+"_before_queue_and_before_send",(h,s)->{
            CompanionLines.offer(s.p,Line.TREE_FOUND);
            switch(change) {
                case "human"->s.p.setData(SpiritData.FORM,false);
                case "unawakened"->s.p.setData(SpiritData.AWAKENED,false);
                case "dead"->s.p.setHealth(0);
                case "spectator"->s.p.setGameMode(GameType.SPECTATOR);
            }
            h.assertFalse(CompanionLines.flush(s.p),"Invalid queued speaker");h.assertFalse(CompanionLines.offer(s.p,Line.ESCAPE),"Invalid new speaker");
            h.assertFalse(s.seen(Line.TREE_FOUND),"Unsent line recorded");
        });
        add(tests,"logout_drops_pending_without_saving_a_once_record",(h,s)->{
            CompanionLines.offer(s.p,Line.TREE_FOUND);CompanionLines.logout(new PlayerEvent.PlayerLoggedOutEvent(s.p));
            h.assertTrue(s.state().lines.pending==null,"Logout retained queue");h.assertFalse(s.seen(Line.TREE_FOUND),"Logout marked seen");
            h.assertFalse(CompanionLines.flush(s.p),"Login replayed queue");
            h.assertTrue(CompanionLines.offer(s.p,Line.TREE_FOUND),"Later real event cannot retry");
        });
        add(tests,"dimension_change_clears_old_context_but_keeps_cooldown",(h,s)->{
            CompanionLines.offer(s.p,Line.BOSS_START);s.sent(Line.BOSS_START);s.ready();CompanionLines.offer(s.p,Line.TREE_FOUND);
            CompanionLines.dimension(new PlayerEvent.PlayerChangedDimensionEvent(s.p,Level.OVERWORLD,Level.NETHER));
            h.assertTrue(s.state().lines.pending==null,"Old dimension queue retained");
            h.assertFalse(CompanionLines.offer(s.p,Line.BOSS_START),"Dimension bypassed cooldown");
        });
        add(tests,"low_health_crossing_only_then_recovery_and_cooldown",(h,s)->{
            s.p.setHealth(s.p.getMaxHealth()*.25f);CompanionLines.observe(s.p);
            s.p.setHealth(s.p.getMaxHealth()*.24f);CompanionLines.observe(s.p);s.sent(Line.LOW_HEALTH);
            s.ready();s.state().lines.spoken.put(Line.LOW_HEALTH,s.now()-1200);CompanionLines.observe(s.p);
            h.assertTrue(s.state().lines.pending==null,"Still low retriggered on cooldown expiry");
            s.p.setHealth(s.p.getMaxHealth());CompanionLines.observe(s.p);
            s.state().lines.spoken.put(Line.LOW_HEALTH,s.now()-1199);s.p.setHealth(s.p.getMaxHealth()*.1f);CompanionLines.observe(s.p);
            h.assertTrue(s.state().lines.pending==null,"Crossing ignored cooldown");
            s.p.setHealth(s.p.getMaxHealth());CompanionLines.observe(s.p);
            s.state().lines.spoken.put(Line.LOW_HEALTH,s.now()-1200);s.p.setHealth(s.p.getMaxHealth()*.1f);CompanionLines.observe(s.p);s.sent(Line.LOW_HEALTH);
        });
        add(tests,"night_one_surface_threshold_and_once_record",(h,s)->{
            var level=h.getLevel();s.p.setPos(s.p.getX(),180,s.p.getZ());level.setDayTime(12999);CompanionLines.observe(s.p);
            h.assertTrue(s.state().lines.pending==null,"Before night");level.setDayTime(13000);CompanionLines.observe(s.p);s.sent(Line.NIGHT_ONE);
            h.assertTrue(s.seen(Line.NIGHT_ONE),"Night record missing");s.ready();
            level.setDayTime(6000);CompanionLines.observe(s.p);level.setDayTime(13000);CompanionLines.observe(s.p);
            h.assertTrue(s.state().lines.pending==null,"Second night repeated");
        });
        add(tests,"night_one_does_not_trigger_underground",(h,s)->{
            var level=h.getLevel();var roof=s.p.blockPosition().above(2);var old=level.getBlockState(roof);
            try {level.setBlock(roof,Blocks.STONE.defaultBlockState(),18);level.setDayTime(13000);
                h.assertTrue(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,roof.getX(),roof.getZ())>s.p.getY(),"Roof fixture absent");
                CompanionLines.observe(s.p);
                h.assertTrue(s.state().lines.pending==null,"Underground night speech");}
            finally {level.setBlock(roof,old,18);}
        });
        for(var kind:RuinKind.values())add(tests,"first_ruin_"+kind.id+"_from_loaded_reference",(h,s)->{
            s.alignScanTick();var level=h.getLevel();var origin=h.absolutePos(new BlockPos(64,120,64));
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
            var startChunk=level.getChunkAt(origin);
            var piece=new RuinPiece(level.getStructureManager(),kind,new RuinPlacement(origin,Rotation.NONE));
            var start=new StructureStart(structure,startChunk.getPos(),0,new PiecesContainer(List.of(piece)));
            var bounds=piece.getBoundingBox();var point=new BlockPos(bounds.maxX()-1,bounds.minY()+2,bounds.maxZ()-1);
            var current=level.getChunkAt(point);var starts=new HashMap<>(startChunk.getAllStarts());var references=copyRefs(current);
            try {
                startChunk.setStartForStructure(structure,start);current.addReferenceForStructure(structure,startChunk.getPos().toLong());
                s.p.setPos(Vec3.atBottomCenterOf(point));CompanionLines.observe(s.p);s.sent(Line.ruin(kind));
                s.ready();CompanionLines.observe(s.p);h.assertTrue(s.state().lines.pending==null,"Remaining in ruin repeated line");
            } finally {startChunk.setAllStarts(starts);current.setAllReferences(references);}
        });
        add(tests,"first_outpost_enters_loaded_piece_once",(h,s)->{
            s.alignScanTick();var level=h.getLevel();var at=GladesOutpostStructure.placement(h.absolutePos(new BlockPos(64,120,64)),Rotation.NONE);
            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(net.minecraft.resources.ResourceLocation.parse("orimod:glades_outpost"));
            var origin=level.getChunkAt(at.origin());var piece=new GladesOutpostPiece(level.getStructureManager(),at);var point=at.block(GladesOutpostPiece.ENTRY);
            var current=level.getChunkAt(point);var starts=new HashMap<>(origin.getAllStarts());var refs=copyRefs(current);
            try{origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(piece))));current.addReferenceForStructure(structure,origin.getPos().toLong());
                s.p.setPos(Vec3.atBottomCenterOf(point));CompanionLines.observe(s.p);s.sent(Line.FIRST_OUTPOST);h.assertTrue(s.seen(Line.FIRST_OUTPOST),"Outpost once record missing");s.ready();CompanionLines.observe(s.p);h.assertTrue(s.state().lines.pending==null,"Outpost entry repeated");
            }finally{origin.setAllStarts(starts);current.setAllReferences(refs);}
        });
        add(tests,"ruin_reference_does_not_load_missing_start_chunk",(h,s)->{
            var level=h.getLevel();var current=level.getChunkAt(s.p.blockPosition());var refs=copyRefs(current);
            var far=new ChunkPos(1_000_000,1_000_000);var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.FOREST.location());
            h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null,"Fixture already loaded");
            try {
                current.addReferenceForStructure(structure,far.toLong());
                h.assertTrue(CompanionLines.ruinAt(level,s.p.blockPosition())==null,"Missing start appeared");
                h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null,"Reference lookup loaded chunk");
            } finally {current.setAllReferences(refs);}
        });
        add(tests,"all_ruin_records_skip_entry_detection",(h,s)->{
            s.alignScanTick();var records=new HashMap<>(s.p.getData(SpiritData.PROGRESS).rewards());
            for(var kind:RuinKind.values())records.put("line/"+Line.ruin(kind).id,1);
            s.p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(records));
            s.state().lines.insideRuin=RuinKind.CAVE;CompanionLines.observe(s.p);
            h.assertValueEqual(s.state().lines.insideRuin,RuinKind.CAVE,"Entry lookup still ran after all three records");
        });
        return tests;
    }
    /** Real hint, trigger and server time: neither the pending timestamp nor the shared gap is backdated. */
    private static void addHintWait(List<TestFunction> tests,boolean tree) {
        var line=tree?Line.TREE_FOUND:Line.FIRST_RUIN_FOREST_SPRING;
        // Separate batches keep the long-lived fixtures' day-time save/restore from overlapping.
        tests.add(new TestFunction("companion_lines_wait_"+line.id, "companion_lines."+line.id+"_survives_hint_gap",
                "orimod_gametest:room",280,0,true,h->{
            Site[] site={null};Runnable[] restore={()->{}};long[] began={0};boolean[] done={false};
            h.onEachTick(()->{
                if(done[0])return;
                try {
                    if(site[0]==null) {
                        if(h.getLevel().getGameTime()%CompanionHints.SCAN_TICKS!=0)return;
                        var s=site[0]=new Site(h);var level=h.getLevel();began[0]=s.now();
                        var hint=tree?CompanionHints.Hint.SPIRIT_TREE:CompanionHints.Hint.BASH_ANCHOR;
                        var records=new HashMap<>(s.p.getData(SpiritData.PROGRESS).rewards());
                        records.remove("hint/"+hint.id);s.p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(records));
                        if(!tree) {
                            var kind=RuinKind.FOREST;var origin=h.absolutePos(new BlockPos(64,120,64));
                            var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
                            var startChunk=level.getChunkAt(origin);
                            var piece=new RuinPiece(level.getStructureManager(),kind,new RuinPlacement(origin,Rotation.NONE));
                            var start=new StructureStart(structure,startChunk.getPos(),0,new PiecesContainer(List.of(piece)));
                            var bounds=piece.getBoundingBox();var point=new BlockPos(bounds.maxX()-1,bounds.minY()+2,bounds.maxZ()-1);
                            var current=level.getChunkAt(point);var starts=new HashMap<>(startChunk.getAllStarts());var refs=copyRefs(current);
                            restore[0]=()->{startChunk.setAllStarts(starts);current.setAllReferences(refs);};
                            startChunk.setStartForStructure(structure,start);current.addReferenceForStructure(structure,startChunk.getPos().toLong());
                            s.p.setPos(Vec3.atBottomCenterOf(point));
                        }
                        var pos=s.p.blockPosition().east(2);var old=level.getBlockState(pos);var restoreStructure=restore[0];
                        restore[0]=()->{level.setBlock(pos,old,18);restoreStructure.run();};
                        level.setBlock(pos,(tree?OriBlocks.SPIRIT_TREE_HEART:OriBlocks.BASH_ANCHOR).get().defaultBlockState(),18);
                        // PlayerTick listeners send the hint first and detect the ruin entry in the same scan.
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(s.p));
                        h.assertTrue(s.packets().stream().anyMatch(p->p.kind()==CompanionHintPayload.Kind.HINT && p.id().equals(hint.id)),"Initial hint not sent");
                        if(tree)h.assertTrue(SpiritTreeHeartBlock.claim(s.p,pos),"First tree claim failed");
                        h.assertTrue(s.state().lines.pending!=null && s.state().lines.pending.line()==line,"Trigger did not queue "+line);
                        CompanionLines.flushLevel(new LevelTickEvent.Post(()->true,level));
                    }
                    var s=site[0];long elapsed=s.now()-began[0];
                    // Embedded connections lack the network tick which normally drives player observation.
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(s.p));
                    if(elapsed<CompanionHints.GAP_TICKS) {
                        h.assertFalse(s.seen(line),"Once record written before shared gap ended");
                        h.assertTrue(s.packets().stream().noneMatch(p->p.kind()==CompanionHintPayload.Kind.LINE),"Line displaced hint");
                        h.assertTrue(s.state().lines.pending!=null && s.state().lines.pending.line()==line,"Line expired before shared gap ended");
                    }
                    // The actual level listener flushes every 20 ticks; inspect after it has had a scan to send.
                    if(elapsed>=CompanionHints.GAP_TICKS+CompanionHints.SCAN_TICKS+1) {
                        h.assertTrue(s.seen(line),"Delayed line missing once-only record");
                        h.assertTrue(s.packets().stream().filter(p->p.kind()==CompanionHintPayload.Kind.LINE && p.id().equals(line.id)).count()==1,"Expected exactly one delayed line");
                        long delay=s.state().lines.spoken.get(line)-began[0];
                        h.assertTrue(delay>=200 && delay<=220,"Line sent outside first available scan: "+delay);
                        h.assertFalse(CompanionLines.offer(s.p,line),"Once-only line queued again");
                        done[0]=true;try{restore[0].run();}finally{s.close();}h.succeed();
                    }
                } catch(RuntimeException | Error error) {
                    if(!done[0]){done[0]=true;try{restore[0].run();}finally{if(site[0]!=null)site[0].close();}}
                    throw error;
                }
            });
        }));
    }
    private static Map<Structure,it.unimi.dsi.fastutil.longs.LongSet> copyRefs(net.minecraft.world.level.chunk.LevelChunk chunk) {
        var refs=new HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();
        chunk.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));return refs;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Site> run) {
        tests.add(new TestFunction("companion_lines","companion_lines."+name,"orimod_gametest:room",100,0,true,h->{
            boolean[] ran={false};
            h.onEachTick(()->{
                if(ran[0] || h.getLevel().getGameTime()%20!=0)return;
                ran[0]=true;try(var s=new Site(h)){run.accept(h,s);}h.succeed();
            });
        }));
    }
    private static final class Site implements AutoCloseable {
        final GameTestHelper h;final Fixtures.ConnectedPlayer connection;final ServerPlayer p;final long time;
        Site(GameTestHelper h){
            this.h=h;time=h.getLevel().getDayTime();h.getLevel().setDayTime(6000);
            connection=Fixtures.connectedPlayer(h);p=connection.player();p.setData(SpiritData.FORM,true);p.setNoGravity(true);
            var records=new HashMap<String,Integer>();for(var hint:CompanionHints.Hint.values()){
                records.put("hint/"+hint.id,1);records.put("hint/"+CompanionHints.wireId(hint,0),1);
            }
            p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(records));connection.channel().outboundMessages().clear();
        }
        EnergyState state(){return p.getData(SpiritData.ENERGY_STATE);}long now(){return h.getLevel().getGameTime();}
        boolean seen(Line line){return p.getData(SpiritData.PROGRESS).rewards().containsKey("line/"+line.id);}
        void ready(){state().lastCompanionHintAt=Long.MIN_VALUE;}
        void alignScanTick(){h.assertTrue(now()%20==0,"Fixture must run on an actual scan tick");}
        List<CompanionHintPayload> packets(){return connection.channel().outboundMessages().stream()
                .filter(packet->packet instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof CompanionHintPayload)
                .map(packet->(CompanionHintPayload)((ClientboundCustomPayloadPacket)packet).payload()).toList();}
        void sent(Line line){
            h.assertTrue(state().lines.pending!=null && state().lines.pending.line()==line,"Wrong queued line, expected "+line+" got "+state().lines.pending);
            h.assertTrue(CompanionLines.flush(p),"Line not sent: "+line);
            h.assertTrue(packets().stream().anyMatch(packet->packet.kind()==CompanionHintPayload.Kind.LINE && packet.id().equals(line.id) && packet.pos().isEmpty()),"Missing line payload "+line);
        }
        public void close(){EscapeSequence.stop(p);connection.close();h.getLevel().setDayTime(time);}
    }
}
