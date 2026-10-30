package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.movement.FeelTuning;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class GinsoEscapeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){var tests=new ArrayList<TestFunction>();
        add(tests,"take_twice_keeps_one_clock_and_shared_crystal",(h,s)->{
            s.start();var run=GinsoEscape.session(s.p);s.tick(25);h.assertFalse(GinsoEscape.start(s.p,s.piece),"Repeated click restarted");h.assertTrue(GinsoEscape.session(s.p)==run&&run.elapsed==25,"Clock reset");h.assertTrue(h.getLevel().getBlockState(s.heart).is(OriBlocks.GINSO_WATER_HEART),"Personal pickup removed shared heart");
        });
        for(int turn=0;turn<4;turn++){int orientation=turn;add(tests,"success_rotated_"+turn,(h,s)->{
            s.piece=new GinsoPiece(s.piece.origin(),115117,orientation,0,3,2,1);s.ready();s.start();s.success();var p=s.p;
            h.assertValueEqual(GinsoEscape.session(p).phase,GinsoEscape.Phase.SUCCESS,"Success missing");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),100,"Light");h.assertValueEqual(SpiritCells.count(p,SpiritCells.Kind.ENERGY),1,"Energy cell");h.assertValueEqual(p.getData(SpiritData.SOUL_LINK),s.link,"Success rewrote Soul Link");
            s.tick(100);s.ready();s.start();s.success();h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),100,"Repeat reward");h.assertValueEqual(SpiritCells.count(p,SpiritCells.Kind.ENERGY),1,"Repeat cell");
        });}
        add(tests,"submerged_damage_nonlethal_reset_then_retry_success",(h,s)->{
            s.p.setHealth(5);s.start();s.tickUntilEnd();var run=GinsoEscape.session(s.p);
            h.assertValueEqual(run.outcome,GinsoEscape.Outcome.SUBMERGED,"Wrong fail reason");h.assertValueEqual(run.maximumSubmerged,60,"Wrong tolerance");h.assertValueEqual(s.p.getHealth(),4f,"Nonlethal lower bound");h.assertTrue(s.p.isAlive(),"Drowning killed participant");s.assertSafe();
            h.assertValueEqual(s.p.getData(SpiritData.SOUL_LINK),s.link,"Failure rewrote Soul Link");h.assertFalse(GinsoEscape.start(s.p,s.piece),"Retry ignored cooldown");s.tick(100);s.start();s.success();h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),100,"Retry could not win");
        });
        add(tests,"submerged_ordinary_hurt_respects_invulnerability",(h,s)->{s.start();s.p.getAbilities().invulnerable=true;s.tickUntilEnd();h.assertValueEqual(s.p.getHealth(),20f,"Invulnerability bypassed");h.assertValueEqual(GinsoEscape.session(s.p).maximumSubmerged,60,"Invulnerability bypassed failure");});
        for(String kind:List.of("door91","leafOutside","timeout","heartMissing"))add(tests,"fails_"+kind,(h,s)->{
            s.start();if(kind.equals("door91"))s.p.setPos(Vec3.atBottomCenterOf(s.piece.point(new BlockPos(-3,91,1))));
            else if(kind.equals("leafOutside"))s.p.setPos(Vec3.atBottomCenterOf(s.piece.point(new BlockPos(27,105,27))));
            else if(kind.equals("heartMissing"))s.write(s.heart,Blocks.AIR.defaultBlockState());
            else {var session=GinsoEscape.session(s.p);session.elapsed=60+session.plan.limitTicks()-1;}
            s.tick(1);h.assertFalse(GinsoEscape.active(s.p),"Session survived "+kind);s.assertSafe();h.assertValueEqual(s.p.getData(SpiritData.SOUL_LINK),s.link,"Failure changed link");
        });
        add(tests,"actual_death_does_not_teleport_or_copy_recovery",(h,s)->{
            s.start();var before=s.p.position();s.p.hurt(s.p.damageSources().genericKill(),Float.MAX_VALUE);
            h.assertTrue(s.p.isDeadOrDying(),"Normal death was blocked");h.assertFalse(s.p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending(),"Death left pending return");h.assertValueEqual(s.p.position(),before,"Death teleported");h.assertValueEqual(s.p.getData(SpiritData.SOUL_LINK),s.link,"Death changed link");
        });
        for(int jumps:List.of(2,3))for(int turn=0;turn<4;turn++){
            int orientation=turn;
            add(tests,"open_branch_"+(jumps==2?"double":"triple")+"_jump_"+turn,(h,s)->{
                s.piece=new GinsoPiece(s.piece.origin(),115117,orientation,0,3,2,1);s.ready();
                if(jumps==3)s.p.setData(SpiritData.PROGRESS,s.p.getData(SpiritData.PROGRESS)
                        .withOwned(Shard.TRIPLE_JUMP.bit).withEquipped(Shard.TRIPLE_JUMP.bit));
                s.start();s.tick(60);var foot=s.openBranch();var p=s.p;var movement=p.getData(SpiritData.MOVEMENT);
                p.setPos(foot);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);movement.groundLockUntil=0;
                movement.wallReadyAt=Long.MAX_VALUE;int sequence=1;ParityGameTests.jump(p,sequence++,true);
                double peak=0;boolean landed=false;
                for(int tick=0;tick<100;tick++){
                    if(p.getDeltaMovement().y<=.06&&movement.acceptedJumps<jumps){
                        ParityGameTests.jump(p,sequence++,false);ParityGameTests.jump(p,sequence++,true);
                    }
                    // Headless player has no LocalPlayer travel. Use the production jump handler,
                    // FeelTuning shaping and vanilla move/gravity/drag, with real block collision.
                    double vy=FeelTuning.vertical(p.getDeltaMovement().y,true,false);
                    p.move(MoverType.SELF,new Vec3(0,vy,0));peak=Math.max(peak,p.getY()-foot.y);
                    s.tick(1);
                    h.assertTrue(GinsoEscape.active(p)&&GinsoEscape.session(p).outcome==GinsoEscape.Outcome.NONE,
                            "Jump cancelled: jumps="+movement.acceptedJumps+" rise="+peak+" outcome="+GinsoEscape.session(p).outcome);
                    if(p.onGround()){landed=true;break;}
                    p.setDeltaMovement(0,(vy-FeelTuning.GRAVITY)*.98,0);
                }
                h.assertTrue(landed,"Jump never landed");h.assertValueEqual(movement.acceptedJumps,jumps,"Jump input rejected");
                h.assertTrue(peak>2.5,"Fixture did not cross the old route ceiling: "+peak);
                cn.wwl.orimod.OriMod.LOGGER.info("Ginso escape jump regression jumps={} turn={} peak={} health={}",jumps,orientation,peak,p.getHealth());
            });
        }
        add(tests,"dimension_change_stays_in_nether_with_cooldown",(h,s)->{
            s.start();var run=GinsoEscape.session(s.p);
            // Wet body but dry eyes: attach the real drag modifier before crossing dimensions.
            run.elapsed=60+(int)Math.ceil((94.25-run.plan.startWater())/GinsoEscapeRules.RISE_PER_SECOND*20);s.tick(1);
            var drag=net.minecraft.resources.ResourceLocation.parse("orimod:ginso_water_drag");
            h.assertTrue(s.p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(drag),"Wet fixture lacked drag");
            var nether=s.p.server.getLevel(Level.NETHER);h.assertTrue(nether!=null,"Nether missing");
            var at=new Vec3(0,100,0);s.p.changeDimension(new DimensionTransition(nether,at,Vec3.ZERO,0,0,DimensionTransition.DO_NOTHING));
            h.assertTrue(s.p.serverLevel()==nether,"Dimension fixture failed");s.tick(5);
            h.assertTrue(s.p.serverLevel()==nether&&s.p.position().distanceTo(at)<.01,"Dimension change pulled player back to tree");
            h.assertFalse(GinsoEscape.active(s.p),"Session survived dimension change");
            h.assertFalse(s.p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(drag),"Water drag survived dimension change");
            var r=s.p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);h.assertFalse(r.pending(),"Dimension left pending recovery");
            h.assertTrue(r.retryAt()>s.p.server.overworld().getGameTime(),"Dimension change lost retry cooldown");
            h.assertValueEqual(s.p.getData(SpiritData.SOUL_LINK),s.link,"Dimension change rewrote Soul Link");
            GinsoEscape.login(new PlayerEvent.PlayerLoggedInEvent(s.p));s.tick(1);
            h.assertTrue(s.p.serverLevel()==nether&&s.p.position().distanceTo(at)<.01,"Reconnect pulled player back after dimension cancel");
        });
        add(tests,"dimension_tick_fallback_does_not_return_to_tree",(h,s)->{
            s.start();var run=GinsoEscape.session(s.p);var nether=s.p.server.getLevel(Level.NETHER);var at=new Vec3(0,100,0);
            // Isolate the tick fallback from the normal dimension event; restore the entity's level in finally.
            s.p.setServerLevel(nether);s.p.setPos(at);
            try{s.tick(1);h.assertTrue(s.p.serverLevel()==nether&&s.p.position().equals(at),"Tick fallback pulled player back");
                h.assertValueEqual(run.outcome,GinsoEscape.Outcome.DIMENSION,"Wrong cancellation outcome");
                h.assertFalse(s.p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending(),"Tick fallback left recovery armed");
            }finally{s.p.setServerLevel(h.getLevel());}
        });
        add(tests,"exit_requires_landing_in_original_box",(h,s)->{
            s.start();s.tick(60);var e=s.piece.escape().exit();
            var foot=Vec3.atBottomCenterOf(s.piece.point(new BlockPos((int)Math.floor((e.x0()+e.x1())*.5),
                    (int)Math.floor(e.y0()),(int)Math.floor((e.z0()+e.z1())*.5)))).add(0,e.y0()%1,0);
            // Even a grounded flag on a higher branch must not enlarge the success volume.
            s.p.setPos(foot.add(0,4,0));s.p.setOnGround(true);s.tick(1);
            h.assertTrue(GinsoEscape.active(s.p),"Above exit cancelled or won");
            s.p.setPos(foot);s.p.setOnGround(false);s.tick(1);
            h.assertTrue(GinsoEscape.active(s.p),"Airborne exit won");
            s.p.setOnGround(true);s.tick(1);
            h.assertValueEqual(GinsoEscape.session(s.p).outcome,GinsoEscape.Outcome.SUCCESS,"Grounded exit failed");
        });
        add(tests,"fall_below_open_branch_still_returns_to_safe_point",(h,s)->{
            s.start();s.tick(60);var foot=s.openBranch();var at=GinsoEscape.session(s.p).local(foot);
            // Adjacent descending treads overlap here. Fall below the entire column, not merely
            // two blocks below a higher tread while still inside its lower neighbour's envelope.
            double bottom=s.piece.escape().route().stream().filter(b->at.x>=b.x0()&&at.x<b.x1()&&at.z>=b.z0()&&at.z<b.z1())
                    .mapToDouble(GinsoEscapeLayout.Box::y0).min().orElseThrow();
            s.p.setPos(foot.add(0,bottom-2-at.y,0));s.p.setOnGround(false);s.tick(1);
            h.assertValueEqual(GinsoEscape.session(s.p).outcome,GinsoEscape.Outcome.OUTSIDE,"Fell below route without failing");s.assertSafe();
        });
        for(String path:List.of("logout","shutdown"))add(tests,"recovery_"+path,(h,s)->{
            s.start();s.tick(40);var original=s.p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);
            var serialized=GinsoEscapeRecovery.CODEC.encodeStart(NbtOps.INSTANCE,original).getOrThrow();
            if(path.equals("shutdown"))GinsoEscape.stop(s.p.server);else GinsoEscape.disconnect(s.p);
            s.p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.CODEC.parse(NbtOps.INSTANCE,serialized).getOrThrow());s.p.setPos(s.p.position().add(20,-5,0));
            GinsoEscape.login(new PlayerEvent.PlayerLoggedInEvent(s.p));s.assertSafe();h.assertFalse(GinsoEscape.active(s.p),"Recovery resumed timer");h.assertFalse(s.p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending(),"Recovery not consumed");h.assertValueEqual(s.p.getData(SpiritData.SOUL_LINK),s.link,"Recovery overwrote link");
        });
        add(tests,"two_independent_clocks_bystander_untouched",(h,s)->{
            try(var other=Fixtures.connectedPlayer(h);var by=Fixtures.connectedPlayer(h)){
                var p2=other.player();p2.setData(SpiritData.FORM,true);p2.setPos(s.p.position());var spectator=by.player();spectator.setPos(s.p.position().add(0,-8,0));float hp=spectator.getHealth();
                s.start();s.tick(100);h.assertTrue(GinsoEscape.start(p2,s.piece),"Second participant rejected");h.assertValueEqual(GinsoEscape.session(p2).elapsed,0,"Shared timer");
                s.tickUntilEnd();h.assertTrue(GinsoEscape.active(p2),"One failure cancelled both");h.assertFalse(GinsoEscape.active(spectator),"Bystander enrolled");h.assertValueEqual(spectator.getHealth(),hp,"Bystander hurt");h.assertFalse(spectator.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending(),"Bystander recovery modified");GinsoEscape.disconnect(p2);
            }
        });
        for(var mode:List.of(GameType.CREATIVE,GameType.SPECTATOR))add(tests,"no_reward_"+mode.getName(),(h,s)->{s.p.setGameMode(mode);s.start();s.success();h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),0,"Debug reward");h.assertFalse(s.p.getData(SpiritData.PROGRESS).rewards().containsKey(GinsoEscape.RECEIPT),"Debug receipt");});
        for(boolean maxLife:List.of(false,true))add(tests,"reward_fallback_"+maxLife,(h,s)->{
            s.p.setData(SpiritData.PROGRESS,s.p.getData(SpiritData.PROGRESS).withRewards(Map.of("cell/energy",10,"cell/life",maxLife?10:0)));SpiritCells.apply(s.p);s.start();s.success();h.assertValueEqual(s.p.getData(SpiritData.PROGRESS).light(),maxLife?200:100,"Fallback light");h.assertValueEqual(SpiritCells.count(s.p,SpiritCells.Kind.LIFE),maxLife?10:1,"Fallback life");
        });
        add(tests,"virtual_water_never_changes_any_block_or_fluid",(h,s)->{
            var before=new HashMap<BlockPos,BlockState>();for(var p:BlockPos.betweenClosed(s.heart.offset(-6,-5,-6),s.heart.offset(6,15,6)))before.put(p.immutable(),h.getLevel().getBlockState(p));s.start();s.tickUntilEnd();s.tick(100);s.start();s.success();for(var e:before.entrySet())h.assertValueEqual(h.getLevel().getBlockState(e.getKey()),e.getValue(),"Flood modified block/fluid at "+e.getKey());
        });
        add(tests,"heart_only_written_for_new_escape_pieces",(h,s)->{
            var l=h.getLevel();for(int escape:new int[]{0,1}){
                var g=new GinsoPiece(s.piece.origin(),115117,0,0,3,2,escape);s.write(s.heart,Blocks.AIR.defaultBlockState());var c=l.getChunk(s.heart);Heightmap.primeHeightmaps(c,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                g.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(0),new BoundingBox(s.heart),new ChunkPos(s.heart),g.origin());
                h.assertTrue(l.getBlockState(s.heart).is(OriBlocks.GINSO_WATER_HEART)==(escape==1),"Wrong upgrade boundary");if(escape==1)h.assertTrue(l.getBlockEntity(s.heart) instanceof SanctuaryBlockEntity,"Heart missing BE");
            }
        });
        add(tests,"chunk_unload_marks_then_recovers_on_player_tick",(h,s)->{
            s.start();GinsoEscape.unload(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(h.getLevel().getChunk(s.heart)));
            h.assertTrue(GinsoEscape.active(s.p),"Unload callback performed teleport/reload");s.tick(1);
            h.assertValueEqual(GinsoEscape.session(s.p).outcome,GinsoEscape.Outcome.CHUNK,"Unload did not cancel");s.assertSafe();
        });
        for(int participants:List.of(1,8))add(tests,"hot_path_timing_"+participants,(h,s)->{
            var others=new ArrayList<Fixtures.ConnectedPlayer>();var players=new ArrayList<ServerPlayer>();players.add(s.p);
            try{
                for(int n=1;n<participants;n++){var c=Fixtures.connectedPlayer(h);others.add(c);var p=c.player();p.setData(SpiritData.FORM,true);p.setPos(s.p.position());players.add(p);}
                for(var p:players)h.assertTrue(GinsoEscape.start(p,s.piece),"Benchmark start");var times=new double[600];
                for(int tick=0;tick<1000;tick++){
                    // Stay ahead of the actual water with the accepted reference trajectory.
                    for(var p:players){var run=GinsoEscape.session(p);var points=run.layout.path();
                        int i=Math.min(points.size()-2,(int)((tick%600)/600.0*(points.size()-2)));var q=points.get(i);run.elapsed=60+(int)(GinsoEscapeRules.reference(q)*20);
                        var local=new BlockPos(q.x(),(int)Math.floor(q.feet()),q.z());var at=Vec3.atBottomCenterOf(s.piece.point(local)).add(0,q.feet()-Math.floor(q.feet()),0);p.setPos(at);p.setOnGround(false);
                        // Benchmark repeated live ticks, excluding input positioning and preparation.
                    }
                    try(var timing=GameTestDiskBackpressure.measured(h.getLevel(),"ginso-hot-tick")){
                        long start=System.nanoTime();for(var p:players)GinsoEscape.tick(p);double ms=(System.nanoTime()-start)/1e6;
                        if(tick>=400)times[tick-400]=ms;
                    }
                    for(var p:players)h.assertTrue(GinsoEscape.active(p),"Benchmark left route");
                }
                Arrays.sort(times);cn.wwl.orimod.OriMod.LOGGER.info("Ginso escape hot path participants={} samples=600 p50_ms={} p95_ms={} max_ms={}",participants,times[300],times[570],times[599]);
            }finally{for(var c:others){GinsoEscape.disconnect(c.player());c.player().setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);c.close();}}
        });
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Site> run){tests.add(new TestFunction("ginso_escape","ginso_escape."+name,"orimod_gametest:room",160,0,true,h->{try(var s=new Site(h)){run.accept(h,s);}h.succeed();}));}
    private static final class Site implements AutoCloseable {
        final GameTestHelper h;final Fixtures.ConnectedPlayer connected;final ServerPlayer p;GinsoPiece piece;final BlockPos heart;
        final Optional<SoulLink.Link> link;final Map<BlockPos,BlockState> before=new HashMap<>();final List<ChunkPos> pins=new ArrayList<>();
        Site(GameTestHelper h){this.h=h;connected=Fixtures.connectedPlayer(h);p=connected.player();heart=h.absolutePos(new BlockPos(4,3,4));piece=new GinsoPiece(heart.below(94),115117,0,0,3,2,1);
            try{for(int x=(heart.getX()-35)>>4;x<=(heart.getX()+35)>>4;x++)for(int z=(heart.getZ()-35)>>4;z<=(heart.getZ()+35)>>4;z++){if(h.getLevel().setChunkForced(x,z,true))pins.add(new ChunkPos(x,z));h.getLevel().getChunk(x,z);}
                p.setData(SpiritData.FORM,true);p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY);p.setHealth(20);p.getAbilities().invulnerable=false;
                link=Optional.of(new SoulLink.Link(h.getLevel().dimension(),h.absolutePos(new BlockPos(8,2,8))));p.setData(SpiritData.SOUL_LINK,link);
                // Match a real enclosing trunk; water must not exist in an open fixture/sky column.
                for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)if(Math.abs(x)==5||Math.abs(z)==5)
                    for(int y=90;y<=100;y++)write(piece.point(new BlockPos(x,y,z)),Blocks.OAK_LOG.defaultBlockState());
                write(heart,OriBlocks.GINSO_WATER_HEART.get().defaultBlockState());ready();
                // Expire vanilla login damage protection before testing ordinary hurt(), not a bypass source.
                for(int i=0;i<61;i++)p.tick();p.invulnerableTime=0;ready();
            }catch(Throwable e){close();throw e;}}
        void write(BlockPos at,BlockState state){before.putIfAbsent(at.immutable(),h.getLevel().getBlockState(at));h.getLevel().setBlock(at,state,2);}
        Vec3 openBranch(){
            var e=piece.escape();
            for(int i=e.path().size()-5;i>=e.branchIndex();i--){
                var q=e.path().get(i);if(e.exit().contains(q.x()+.5,q.feet(),q.z()+.5))continue;
                boolean open=true;
                for(int y=(int)Math.ceil(q.feet());y<=q.feet()+8;y++){
                    byte c=e.cell(q.x(),y,q.z());if(c==0)c=piece.course().cell(q.x(),y,q.z());
                    var world=GinsoInterior.rotate(new BlockPos(q.x(),y,q.z()),piece.entrance());
                    if(c==0)c=piece.sculpture().cell(world.getX(),y,world.getZ());
                    if(c!=0&&c!=GinsoShape.AIR&&c!=GinsoShape.VINE){open=false;break;}
                }
                if(!open)continue;
                var at=piece.point(new BlockPos(q.x(),(int)Math.floor(q.feet()),q.z()));
                var box=new BoundingBox(at.getX()-1,at.getY()-1,at.getZ()-1,at.getX()+1,at.getY()+9,at.getZ()+1);
                for(var p:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()))before.putIfAbsent(p.immutable(),h.getLevel().getBlockState(p));
                for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++){
                    var chunk=h.getLevel().getChunk(x,z);Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                    piece.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(0),box,new ChunkPos(x,z),piece.origin());
                }
                return Vec3.atBottomCenterOf(at).add(0,q.feet()%1,0);
            }
            throw new IllegalStateException("No open branch jump column");
        }
        void ready(){var safe=piece.point(GinsoEscapeLayout.SAFE);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){write(safe.offset(x,-1,z),Blocks.STONE.defaultBlockState());for(int y=0;y<3;y++)if(!safe.offset(x,y,z).equals(heart))write(safe.offset(x,y,z),Blocks.AIR.defaultBlockState());}p.setPos(Vec3.atBottomCenterOf(safe));p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);}
        void start(){h.assertTrue(GinsoEscape.start(p,piece),"Start refused");}
        void tick(int n){for(int i=0;i<n;i++){if(p.invulnerableTime>0)p.invulnerableTime--;GinsoEscape.tick(p);}}
        void tickUntilEnd(){for(int i=0;i<2400&&GinsoEscape.active(p);i++)tick(1);h.assertFalse(GinsoEscape.active(p),"No terminal state");}
        void success(){tick(60);var e=piece.escape().exit();var local=new BlockPos((int)Math.floor((e.x0()+e.x1())*.5),(int)e.y0(),(int)Math.floor((e.z0()+e.z1())*.5));p.setPos(Vec3.atBottomCenterOf(piece.point(local)));p.setOnGround(true);tick(1);}
        void assertSafe(){h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(piece.point(GinsoEscapeLayout.SAFE)))<.01,"Wrong return position: "+p.position());}
        @Override public void close(){GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);before.forEach((at,state)->h.getLevel().setBlock(at,state,2));for(var c:pins)h.getLevel().setChunkForced(c.x,c.z,false);connected.close();}
    }
}
