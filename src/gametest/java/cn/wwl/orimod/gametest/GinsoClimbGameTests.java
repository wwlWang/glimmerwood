package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_r117")
public final class GinsoClimbGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String mode : List.of("ready", "human", "climb", "bash", "grapple", "glide"))
            tests.add(new TestFunction("r117_ginso", "r117_ginso.start_" + mode, "orimod_r117:room", 100, 0, true, h -> {
                try (var connected = Fixtures.connectedPlayer(h)) {
                    var p = connected.player(); var heart = h.absolutePos(new BlockPos(4, 3, 4));
                    var piece = new GinsoPiece(heart.below(5), 115117, 0, 0);
                    var original = h.getLevel().getBlockState(heart);
                    try {
                        h.assertValueEqual(piece.courseVersion(), 3, "New tree course");
                        h.assertValueEqual(piece.escapeVersion(), 2, "New tree escape");
                        h.getLevel().setBlock(heart, OriBlocks.GINSO_WATER_HEART.get().defaultBlockState(), 2);
                        p.setPos(Vec3.atBottomCenterOf(piece.point(piece.escapeSafe())));
                        p.setData(SpiritData.AWAKENED, true); p.setData(SpiritData.FORM, !mode.equals("human"));
                        int abilities = GinsoClimbEscape.REQUIRED.stream().mapToInt(a -> a.bit).reduce(0, (a,b) -> a|b);
                        var absent = Ability.byId(mode); if (absent != null) abilities &= ~absent.bit;
                        p.setData(SpiritData.ABILITIES, abilities);
                        boolean accepted = GinsoEscape.start(p, piece);
                        h.assertTrue(accepted == mode.equals("ready"), "Wrong start eligibility");
                        h.assertTrue((GinsoEscape.session(p) != null) == accepted, "Refusal allocated a session");
                        var recovery = p.getData(SpiritData.GINSO_ESCAPE_RECOVERY);
                        h.assertTrue(recovery.resumable() == accepted, "Refusal armed resume snapshot");
                        h.assertFalse(recovery.pending(), "Start path armed fail recovery");
                        if (accepted) h.assertValueEqual(recovery.safe(), piece.point(GinsoClimbCourse.SAFE), "Recovery is not beside bottom heart");
                        h.succeed();
                    } finally {
                        GinsoEscape.disconnect(p); p.setData(SpiritData.GINSO_ESCAPE_RECOVERY, GinsoEscapeRecovery.EMPTY);
                        h.getLevel().setBlock(heart, original, 2);
                    }
                }
            }));
        for(String ending:List.of("submerged_retry","success","logout","dimension"))
            tests.add(new TestFunction("r117_ginso", "r117_ginso.lifecycle_"+ending,"orimod_r117:room",160,0,true,h->{
                try(var connected=Fixtures.connectedPlayer(h)){
                    var p=connected.player();var level=h.getLevel();var heart=h.absolutePos(new BlockPos(4,3,4));
                    var piece=new GinsoPiece(heart.below(5),115117,0,0);var safe=piece.point(piece.escapeSafe());
                    var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
                    try{
                        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++){
                            var at=safe.offset(x,y,z);before.put(at,level.getBlockState(at));
                            level.setBlock(at,(y==-1?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),2);
                        }
                        // A real enclosure is required now; an unbounded empty test room must not flood.
                        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)if(Math.abs(x)==5||Math.abs(z)==5)for(int y=0;y<4;y++){
                            var at=heart.offset(x,y,z);before.putIfAbsent(at,level.getBlockState(at));
                            level.setBlock(at,Blocks.OAK_LOG.defaultBlockState(),2);
                        }
                        before.putIfAbsent(heart,level.getBlockState(heart));level.setBlock(heart,OriBlocks.GINSO_WATER_HEART.get().defaultBlockState(),2);
                        p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.refreshDimensions();
                        p.setData(SpiritData.ABILITIES,GinsoClimbEscape.REQUIRED.stream().mapToInt(a->a.bit).reduce(0,(a,b)->a|b));
                        p.setPos(Vec3.atBottomCenterOf(safe));p.setOnGround(true);
                        h.assertTrue(GinsoEscape.start(p,piece),"New escape refused");var run=GinsoEscape.session(p);
                        h.assertTrue(run.plan.startWater()==-4,"Provisional water remained");
                        h.assertTrue(run.flood.contains(.5,5.25,-1.5),"Enclosed bottom chamber missing");
                        h.assertFalse(run.flood.contains(.5,9.25,-1.5),"Water floated above the wall rim");
                        run.elapsed=(int)Math.ceil(GinsoEscapeRules.reach(run.plan,5.25)*20);GinsoEscape.tick(p);
                        var drag=net.minecraft.resources.ResourceLocation.parse("orimod:ginso_water_drag");
                        h.assertTrue(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).hasModifier(drag),"Actual bottom water failed to apply drag");
                        switch(ending){
                            case "submerged_retry"->{
                                run.elapsed=(int)Math.ceil(GinsoEscapeRules.reach(run.plan,5+p.getEyeHeight()+.1)*20);
                                for(int t=0;t<60;t++)GinsoEscape.tick(p);
                                h.assertValueEqual(run.outcome,GinsoEscape.Outcome.SUBMERGED,"New water failed to drown");
                                h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(safe))<.01,"Wrong bottom recovery");
                                h.assertFalse(GinsoEscape.start(p,piece),"Retry cooldown ignored");
                                for(int t=0;t<100;t++)GinsoEscape.tick(p);
                                h.assertTrue(GinsoEscape.start(p,piece),"Retry unavailable after cooldown");
                                h.assertTrue(GinsoEscape.session(p).elapsed==0&&GinsoEscape.session(p).water()==piece.origin().getY()-4,"Retry reused old water/clock");
                                GinsoEscape.disconnect(p);
                            }
                            case "success"->{
                                var exit=piece.escape().exit();var at=piece.point(new BlockPos((int)((exit.x0()+exit.x1())*.5),(int)exit.y0(),(int)((exit.z0()+exit.z1())*.5)));
                                p.setPos(Vec3.atBottomCenterOf(at));p.setOnGround(true);GinsoEscape.tick(p);
                                h.assertValueEqual(run.outcome,GinsoEscape.Outcome.SUCCESS,"Crown landing did not finish");
                            }
                            case "logout"->{
                                var mid=p.position();
                                GinsoEscape.disconnect(p);h.assertTrue(GinsoEscape.session(p)==null,"Logout kept session");
                                h.assertTrue(p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).resumable(),"Logout lost resume snapshot");
                                GinsoEscape.login(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
                                h.assertTrue(GinsoEscape.active(p),"Login did not resume escape");
                                h.assertTrue(p.position().distanceTo(mid)<.01,"Resume teleported away from mid-route");
                                // Clear so shared terminal assertions still see a finished escape.
                                GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);
                            }
                            case "dimension"->{
                                var nether=p.server.getLevel(net.minecraft.world.level.Level.NETHER);var at=new Vec3(0,100,0);
                                p.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(nether,at,Vec3.ZERO,0,0,net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
                                h.assertValueEqual(run.outcome,GinsoEscape.Outcome.DIMENSION,"Dimension did not cancel");
                                h.assertTrue(p.serverLevel()==nether&&p.position().distanceTo(at)<.01,"Dimension returned player to tree");
                            }
                        }
                        h.assertFalse(GinsoEscape.active(p),"Terminal path retained active water");
                        h.assertFalse(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).hasModifier(drag),"Terminal path retained drag");
                        if(!ending.equals("submerged_retry"))h.assertFalse(p.getData(SpiritData.GINSO_ESCAPE_RECOVERY).pending(),"Terminal path retained recovery");
                        h.succeed();
                    }finally{GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);before.forEach((at,state)->level.setBlock(at,state,2));}
                }
            }));
        return tests;
    }
}
