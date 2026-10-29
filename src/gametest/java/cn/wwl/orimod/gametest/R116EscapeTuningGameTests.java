package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.world.EscapeSequence;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class R116EscapeTuningGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        add(tests,"recorded_escape05_route_survives_normal_cover",h->{
            var level=h.getLevel();var near=h.absolutePos(new BlockPos(32,0,32));var base=new BlockPos(near.getX(),70,near.getZ());
            var old=new HashMap<BlockPos,BlockState>();var pins=new ArrayList<ChunkPos>();
            var difficulty=level.getDifficulty();long time=level.getGameTime();
            try(var c=Fixtures.connectedPlayer(h)) {
                var p=c.player();
                try {
                    for(int x=base.getX()>>4;x<=(base.getX()+47)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+68)>>4;z++) {
                        if(level.setChunkForced(x,z,true))pins.add(new ChunkPos(x,z));level.getChunk(x,z);
                    }
                    // Fixed overhead cover prevents early victory through a template skylight.
                    for(int x=0;x<=46;x++)for(int z=0;z<=67;z++) {
                        var at=base.offset(x,34,z);old.put(at,level.getBlockState(at));level.setBlock(at,Blocks.STONE.defaultBlockState(),2);
                    }
                    var light=level.getChunkSource().getLightEngine();
                    var lighting=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
                    for(int x=base.getX()>>4;x<=(base.getX()+47)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+68)>>4;z++)
                        lighting.add(light.waitForPendingTasks(x,z));
                    light.tryScheduleUpdate();
                    var ready=java.util.concurrent.CompletableFuture.allOf(lighting.toArray(java.util.concurrent.CompletableFuture[]::new));
                    awaitLight(h,ready);
                    level.getServer().setDifficulty(Difficulty.NORMAL,true);p.setHealth(20);
                    for(int i=0;i<61;i++)p.tick(); // expire the real login invulnerability
                    p.setPos(Vec3.atBottomCenterOf(base).add(23,6,55));
                    h.assertFalse(level.canSeeSky(p.blockPosition().above()),"Cover fixture missing");
                    var origin=Vec3.atLowerCornerOf(base).add(23.5,8,60.5);EscapeSequence.start(p,origin);
                    var source=R116EscapeTuningGameTests.class.getResourceAsStream("/r116/escape-v1-route.csv");
                    h.assertTrue(source!=null,"Missing recorded route fixture");int lastTick=0;
                    try(var reader=new BufferedReader(new InputStreamReader(source,StandardCharsets.UTF_8))) {
                        for(String line:reader.lines().skip(1).toList()) {
                            var v=line.split(",");lastTick=Integer.parseInt(v[0]);
                            ((ServerLevelData)level.getLevelData()).setGameTime(time+lastTick);
                            p.setPos(base.getX()+Double.parseDouble(v[1]),base.getY()+Double.parseDouble(v[2]),base.getZ()+Double.parseDouble(v[3]));
                            p.tick();EscapeSequence.tick(new PlayerTickEvent.Post(p));
                            h.assertTrue(p.isAlive(),"Recorded escape05 route died at tick "+lastTick+", HP="+p.getHealth());
                            if(EscapeSequence.session(p)==null)break;
                        }
                    }
                    h.assertTrue(EscapeSequence.session(p)==null,"Recorded route never escaped");
                    h.assertTrue(p.getData(cn.wwl.orimod.spirit.SpiritData.PROGRESS).rewards().containsKey(EscapeSequence.CLEARED),"Session ended without victory");
                    cn.wwl.orimod.OriMod.LOGGER.info("R116_ESCAPE_REPLAY ticks={} health={} distance={}",lastTick,p.getHealth(),p.position().subtract(origin).horizontalDistance());
                } finally {EscapeSequence.stop(p);}
            } catch(IOException e) {throw new UncheckedIOException(e);}
            finally {
                ((ServerLevelData)level.getLevelData()).setGameTime(time);level.getServer().setDifficulty(difficulty,true);
                old.forEach((p,s)->level.setBlock(p,s,2));CreekFixtureChunks.release(level,pins);
            }
        });
        for(boolean creative:List.of(false,true))add(tests,creative?"pedestal_creative_removal":"pedestal_survival_fixed",h->{
            try(var c=Fixtures.connectedPlayer(h)) {
                var p=c.player();var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,2,5));
                level.setBlock(pos,OriBlocks.KEYSTONE_PEDESTAL.get().defaultBlockState(),3);
                p.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);p.setOnGround(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
                p.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.WEST,level.getMaxBuildHeight(),0);
                for(int i=0;i<600;i++)p.gameMode.tick();
                p.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,Direction.WEST,level.getMaxBuildHeight(),1);
                if(creative)h.assertTrue(level.isEmptyBlock(pos),"Creative cannot remove fixed pedestal");
                else {
                    h.assertTrue(level.getBlockState(pos).is(OriBlocks.KEYSTONE_PEDESTAL),"Survival mining removed the reforming pedestal");
                    h.assertTrue(level.getBlockState(pos).getDestroyProgress(p,level,pos)==0,"Fixed pedestal accumulates mining progress");
                    h.assertTrue(Block.getDrops(level.getBlockState(pos),level,pos,null,p,p.getMainHandItem()).isEmpty(),"Fixed pedestal has drops");
                }
            }
        });
        add(tests,"warning_packets_and_damage_cadence",h->{
            var level=h.getLevel();long time=level.getGameTime();var difficulty=level.getDifficulty();
            try(var c=Fixtures.connectedPlayer(h)) {
                var p=c.player();var at=p.blockPosition();level.setBlock(at.above(4),Blocks.STONE.defaultBlockState(),3);
                var light=level.getChunkSource().getLightEngine();var ready=light.waitForPendingTasks(at.getX()>>4,at.getZ()>>4);
                light.tryScheduleUpdate();awaitLight(h,ready);
                try {
                    level.getServer().setDifficulty(Difficulty.NORMAL,true);p.setHealth(20);
                    for(int i=0;i<61;i++)p.tick();FxGameTests.drain(c.channel());
                    EscapeSequence.start(p,p.position());
                    for(int t=0;t<=180;t++) {
                        ((ServerLevelData)level.getLevelData()).setGameTime(time+t);p.tick();EscapeSequence.tick(new PlayerTickEvent.Post(p));
                        h.assertTrue(EscapeSequence.session(p)!=null,"Covered stationary player escaped during warning");
                        if(t<160) {
                            Fixtures.near(h,p.getHealth(),20,"Warning dealt damage at tick "+t);
                            h.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)||p.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS),"Warning applied front debuffs");
                        } else Fixtures.near(h,p.getHealth(),18-2*((t-160)/10),"Front damage cadence at tick "+t);
                    }
                    var packets=FxGameTests.drain(c.channel());
                    var countdowns=packets.stream().filter(x->x instanceof net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket).map(x->((net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket)x).text().getContents())
                            .filter(x->x instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("subtitle.orimod.escape_countdown"))
                            .map(x->((net.minecraft.network.chat.contents.TranslatableContents)x).getArgs()[0]).toList();
                    h.assertValueEqual(countdowns,List.of(8,7,6,5,4,3,2,1),"Countdown missing, duplicated or out of order");
                    var chimes=packets.stream().filter(x->x instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound&&sound.getSound().value()==net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value())
                            .map(x->((net.minecraft.network.protocol.game.ClientboundSoundPacket)x).getPitch()).toList();
                    h.assertValueEqual(chimes.size(),8,"Expected one countdown sound each second");
                    for(int i=0;i<8;i++)Fixtures.near(h,chimes.get(i),i<5?.9:1.2+(i-5)*.2,"Final three warning pitches");
                } finally {EscapeSequence.stop(p);}
            } finally {((ServerLevelData)level.getLevelData()).setGameTime(time);level.getServer().setDifficulty(difficulty,true);}
        });
        return tests;
    }
    private static void awaitLight(GameTestHelper h,java.util.concurrent.CompletableFuture<?> ready) {
        try(var timing=GameTestDiskBackpressure.measured(h.getLevel(),"escape-light-deadline")){
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        // The sorter may enqueue its last <1000-task batch after the first flush.
        // Keep scheduling that tail while servicing the server mailbox; never wait forever.
        h.getLevel().getServer().managedBlock(()->{
            h.getLevel().getChunkSource().getLightEngine().tryScheduleUpdate();
            return ready.isDone()||System.nanoTime()>=deadline;
        });
        h.assertTrue(ready.isDone(),"Fixture light queue did not drain within 15 seconds");ready.join();
        }
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("r116_escape_tuning","r116_escape_tuning."+name,"orimod_gametest:room",600,0,true,h->{run.accept(h);h.succeed();}));
    }
}
