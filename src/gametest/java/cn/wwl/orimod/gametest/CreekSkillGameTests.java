package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.FeelTuning;
import cn.wwl.orimod.movement.MovementState;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.SpiritChallenge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/** Server-accepted Bash followed by real collisions and the ordinary client feel curve. */
@GameTestHolder("orimod_creek_rebuild")
public final class CreekSkillGameTests {
    private static final float RELEASE_PITCH=-70;
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var r:Rotation.values()){
            tests.add(new TestFunction("creek_skill","creek_skill.bash_c1_"+r.name().toLowerCase(),
                    "orimod_creek_rebuild:room",Rotation.NONE,500,0,true,h->bash(h,r)));
            tests.add(new TestFunction("creek_skill","creek_skill.bash_direct_window_"+r.name().toLowerCase(),
                    "orimod_creek_rebuild:room",Rotation.NONE,500,0,true,h->directSweep(h,r)));
        }
        return tests;
    }
    /** No jump key, air jump, reposition, or aim correction between release and first landing. */
    private static void directSweep(GameTestHelper h,Rotation rotation){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,rotation,28700+rotation.ordinal()*200,5000);var c=Fixtures.connectedPlayer(h)){
            s.place();s.publish();var p=c.player();var layout=s.piece.layout();
            p.setGameMode(GameType.SURVIVAL);p.setData(SpiritData.FORM,true);p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.ABILITIES,6);
            p.refreshDimensions();p.getData(SpiritData.COOLDOWNS).flameReadyAt=Long.MAX_VALUE;
            var target=s.p.block(new BlockPos(12,13,29));var goal=layout.checkpoints().getFirst().position();
            int run=0,best=0,bestLow=0,bestHigh=0,successes=0;var passingPitches=new ArrayList<Integer>();
            System.out.println("CREEK_BASH_SWEEP_CSV rotation,pitch,yaw_offset,jump_held,accepted_bashes,accepted_jumps,ticks,apex,x,y,z,on_ground,on_c1,edge_margin,goal_error,first_collision_tick,first_collision_x,first_collision_y,first_collision_z");
            try{
                for(int pitch=-85;pitch<=-35;pitch+=5){
                    boolean allYaw=true;
                    for(int offset=-10;offset<=10;offset+=5){
                        // Each trial is an independent input sequence from the exact safe A
                        // point. Resetting here cannot supply any movement during the flight.
                        SpiritMovement.cancel(p,p.getData(SpiritData.MOVEMENT));
                        var movement=new MovementState();p.setData(SpiritData.MOVEMENT,movement);
                        p.setPos(s.p.point(layout.safe()));p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
                        p.fallDistance=0;p.setHealth(p.getMaxHealth());p.horizontalCollision=false;p.verticalCollision=false;
                        Vec3 aim=Vec3.atCenterOf(target).subtract(p.getEyePosition());
                        float targetYaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
                        float targetPitch=(float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance()));
                        long now=h.getLevel().getGameTime();int flags=MovementInputPayload.ACTIVE;
                        SpiritMovement.accept(p,new MovementInputPayload(0,now,flags|MovementInputPayload.BASH,1,0,targetYaw,targetPitch));
                        h.assertTrue(movement.bash!=null&&target.equals(movement.bash.target.block()),"Direct Bash sweep fixture cannot select C1 anchor at "+rotation+"/"+pitch+"/"+offset);
                        float releaseYaw=rotation.rotate(Direction.SOUTH).toYRot()+offset;
                        SpiritMovement.accept(p,new MovementInputPayload(1,now,flags,1,0,releaseYaw,pitch));
                        h.assertValueEqual(movement.acceptedBashes,1,"Direct sweep did not launch");
                        h.assertValueEqual(movement.acceptedJumps,0,"Direct sweep accidentally supplied a jump");
                        h.assertTrue(movement.jumps.isEmpty()&&!movement.jumpHeld,"Direct sweep left a jump queued or held");
                        // The owner applies this flag for BASH_LAUNCH, independently of
                        // whether the player ever pressed jump before the Bash input.
                        p.setOnGround(false);Vec3 wish=Vec3.directionFromRotation(0,releaseYaw);
                        double apex=layout.safe().y;int ticks=0,firstCollision=-1;Vec3 collision=Vec3.ZERO;
                        for(;ticks<80&&!p.onGround();ticks++){
                            var observed=p.getDeltaMovement();
                            var horizontal=new Vec3(observed.x,0,observed.z).scale(ticks==0?1:1/.91);
                            horizontal=FeelTuning.horizontal(horizontal,wish,false,1,1);
                            p.setDeltaMovement(horizontal.x,FeelTuning.vertical(observed.y,false,false),horizontal.z);
                            p.move(MoverType.SELF,p.getDeltaMovement());
                            var local=s.p.local(p.position());apex=Math.max(apex,local.y);
                            if(p.horizontalCollision&&firstCollision<0){firstCollision=ticks+1;collision=local;}
                            if(p.onGround()){ticks++;break;}
                            var after=p.getDeltaMovement();p.setDeltaMovement(after.x*.91,(after.y-FeelTuning.GRAVITY)*.98,after.z*.91);
                        }
                        var landed=s.p.local(p.position());
                        // C1 is the authored six-by-eight landing, x10..15/z31..38,
                        // at feet y15. Require the whole 0.6-wide body to fit on it;
                        // do not substitute the exact-angle test's one-metre centre goal.
                        double margin=Math.min(Math.min(landed.x-10,16-landed.x),Math.min(landed.z-31,39-landed.z))-.3;
                        boolean landedC1=p.onGround()&&Math.abs(landed.y-goal.y)<.05&&margin>=-1e-6;
                        double error=new Vec3(landed.x-goal.x,0,landed.z-goal.z).length();
                        allYaw&=landedC1;if(landedC1)successes++;
                        System.out.printf(Locale.ROOT,"CREEK_BASH_SWEEP_CSV %s,%d,%d,false,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%s,%s,%.6f,%.6f,%d,%.6f,%.6f,%.6f%n",
                                rotation,pitch,offset,movement.acceptedBashes,movement.acceptedJumps,ticks,apex,landed.x,landed.y,landed.z,p.onGround(),landedC1,margin,error,firstCollision,collision.x,collision.y,collision.z);
                    }
                    if(allYaw){passingPitches.add(pitch);run++;if(run>best){best=run;bestLow=pitch-(run-1)*5;bestHigh=pitch;}}
                    else run=0;
                }
                int window=Math.max(0,(best-1)*5);
                System.out.println("CREEK_BASH_SWEEP rotation="+rotation+" trials=55 jumpHeld=false acceptedJumps=0 successes="+successes+" allYawPitches="+passingPitches+" bestLow="+bestLow+" bestHigh="+bestHigh+" contiguousDegrees="+window+" requiredDegrees=15 maxTicks=80");
                h.assertTrue(window>=15,"Direct safe-A Bash has no >=15 degree pitch window for every yaw -10..10: rotation="+rotation+", pitches="+passingPitches+", best="+window+" degrees, successful trials="+successes+"/55");
                h.succeed();
            }finally{SpiritMovement.cancel(p,p.getData(SpiritData.MOVEMENT));}
        }
    }
    private static void bash(GameTestHelper h,Rotation rotation){
        try(var s=new CreekRuinsLayoutGameTests.Site(h,rotation,27500+rotation.ordinal()*200,5000);var c=Fixtures.connectedPlayer(h)){
            s.place();s.publish();var p=c.player();var layout=s.piece.layout();
            p.setGameMode(GameType.SURVIVAL);p.setData(SpiritData.FORM,true);p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.ABILITIES,6);
            p.refreshDimensions();p.setPos(s.p.point(layout.safe()));p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
            p.getData(SpiritData.COOLDOWNS).flameReadyAt=Long.MAX_VALUE;
            SpiritChallenge.request(p,s.p.block(layout.shrine()),true);
            try{
                var challenge=SpiritChallenge.session(p);h.assertTrue(challenge!=null,"Bash fixture cannot start saved creek challenge");
                var movement=p.getData(SpiritData.MOVEMENT);var target=s.p.block(new BlockPos(12,13,29));
                Vec3 aim=Vec3.atCenterOf(target).subtract(p.getEyePosition());
                float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance()));
                long now=h.getLevel().getGameTime();
                // A legal ground jump held through Bash provides the same held-
                // jump rise shaping as real input. No air-jump is injected later.
                int flags=MovementInputPayload.ACTIVE|MovementInputPayload.JUMP;
                SpiritMovement.accept(p,new MovementInputPayload(movement.sequence+1,now,flags|MovementInputPayload.BASH,1,0,yaw,pitch));
                h.assertTrue(movement.bash!=null&&target.equals(movement.bash.target.block()),"C1 anchor not selected");
                float forwardYaw=rotation.rotate(Direction.SOUTH).toYRot();
                SpiritMovement.accept(p,new MovementInputPayload(movement.sequence+1,now,flags,1,0,forwardYaw,RELEASE_PITCH));
                h.assertValueEqual(movement.acceptedBashes,1,"No accepted real Bash");h.assertTrue(movement.jumps.isEmpty(),"Launch retains an unconsumed jump edge");
                // MovementResultPayload applies this exact airborne handoff to
                // BASH_LAUNCH on the owner; positions below are changed only by move().
                p.setOnGround(false);
                var wish=Vec3.atLowerCornerOf(rotation.rotate(Direction.SOUTH).getNormal());
                int ticks=0;double apex=s.p.local(p.position()).y;var trace=new ArrayList<String>();
                for(;ticks<80&&!p.onGround();ticks++){
                    var observed=p.getDeltaMovement();
                    // SpiritControls.feel removes vanilla horizontal friction
                    // from the previous tick, except for the fresh raw impulse.
                    var horizontal=new Vec3(observed.x,0,observed.z).scale(ticks==0?1:1/.91);
                    horizontal=FeelTuning.horizontal(horizontal,wish,false,1,1);
                    double vertical=FeelTuning.vertical(observed.y,true,false);
                    p.setDeltaMovement(horizontal.x,vertical,horizontal.z);
                    p.move(MoverType.SELF,p.getDeltaMovement());
                    var local=s.p.local(p.position());apex=Math.max(apex,local.y);
                    trace.add(ticks+":"+String.format(java.util.Locale.ROOT,"%.3f,%.3f,%.3f",local.x,local.y,local.z));
                    if(p.onGround())break;
                    var after=p.getDeltaMovement();p.setDeltaMovement(after.x*.91,(after.y-FeelTuning.GRAVITY)*.98,after.z*.91);
                }
                var landed=s.p.local(p.position());var goal=layout.checkpoints().getFirst().position();
                double error=new Vec3(landed.x-goal.x,0,landed.z-goal.z).length();
                System.out.println("CREEK_BASH rotation="+rotation+" pitch="+RELEASE_PITCH+" ticks="+(ticks+1)+" launch=1.25 heldGroundJump=true acceptedBashes="+movement.acceptedBashes+" acceptedJumps="+movement.acceptedJumps+" apex="+apex+" landed="+landed+" error="+error+" trace="+trace);
                h.assertTrue(p.onGround()&&Math.abs(landed.y-goal.y)<.05,"Bash did not land on C1: "+landed);
                h.assertTrue(error<=1,"Bash C1 landing farther than one block: "+error);
                h.assertValueEqual(movement.acceptedJumps,1,"Flight gained an extra jump");
                SpiritChallenge.tick(new PlayerTickEvent.Post(p));
                h.assertValueEqual(challenge.checkpoint,1,"Real Bash arrival did not advance the inherited challenge");
                h.assertValueEqual(challenge.improvised,0,"Actual Bash arrival was counted as improvised");h.succeed();
            }finally{SpiritChallenge.finish(p,"exit",false);SpiritMovement.cancel(p,p.getData(SpiritData.MOVEMENT));}
        }
    }
}
