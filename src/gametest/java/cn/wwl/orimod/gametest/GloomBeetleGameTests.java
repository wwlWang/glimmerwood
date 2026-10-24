package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.GloomTide;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;
import static cn.wwl.orimod.entity.BeetleRules.State.*;

/** Real damage, movement, goal ticks and registered player/network integration. */
@GameTestHolder("orimod_gametest")
public final class GloomBeetleGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(boolean front:List.of(true,false))add(tests,"blade_"+(front?"front":"back"),(h,s)->{
            s.p.setPos(s.b.position().add(0,0,-1.5));s.p.setYRot(0);s.p.setXRot(0);
            s.b.yBodyRot=front?180:0;s.b.setYHeadRot(front?0:180); // Head deliberately faces the opposite way.
            s.p.setItemInHand(InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(OriItems.SPIRIT_BLADE.get()));
            SpiritCombat.attack(s.p);var combat=s.p.getData(SpiritData.COMBAT);combat.startedAt-=SpiritCombat.windup(1);
            SpiritCombat.tick(new PlayerTickEvent.Post(s.p));
            h.assertTrue(combat.hitTargets.contains(s.b.getUUID()),"Blade did not reach beetle");
            Fixtures.near(h,s.b.getHealth(),18-(front?.6:3),"Body-relative blade armor");
        });
        for(String side:List.of("front","back","side"))add(tests,"projectile_incoming_"+side,(h,s)->{
            s.b.yBodyRot=0;s.p.setPos(s.b.position().add(0,0,4));
            var arrow=EntityType.ARROW.create(h.getLevel());arrow.setOwner(s.p);arrow.setPos(s.b.position().add(0,.3,1));
            arrow.setDeltaMovement(side.equals("front")?new Vec3(0,0,-1):side.equals("back")?new Vec3(0,0,1):new Vec3(-1,0,0));
            s.b.hurt(h.getLevel().damageSources().arrow(arrow,s.p),5);
            Fixtures.near(h,s.b.getHealth(),side.equals("front")?17:13,"Projectile velocity, not owner or projectile position");arrow.discard();
        });
        add(tests,"overhead_attack_bypasses_shell",(h,s)->{
            s.b.yBodyRot=0;s.p.setPos(s.b.position().add(0,3,.2));s.b.hurt(s.p.damageSources().playerAttack(s.p),4);
            Fixtures.near(h,s.b.getHealth(),14,"Overhead damage");
        });
        add(tests,"stomp_flip_recovery_and_immunity",(h,s)->{
            s.p.setPos(s.b.position().add(0,0,-2));stomp(s.p);h.assertValueEqual(s.b.state(),FLIPPED,"Stomp did not flip");
            Fixtures.near(h,s.b.getHealth(),6,"Stomp initiating hit receives exposed-belly multiplier");
            for(int i=0;i<59;i++)s.b.tick();h.assertValueEqual(s.b.state(),FLIPPED,"Flip ended early");
            s.b.tick();h.assertValueEqual(s.b.state(),IDLE,"Flip did not recover");h.assertValueEqual(s.b.immunity(),40,"Recovery guard");
            s.b.setHealth(18);s.b.invulnerableTime=0;s.p.setPos(s.b.position().add(0,0,-2));stomp(s.p);
            h.assertValueEqual(s.b.state(),IDLE,"Stomp bypassed guard");
            for(int i=0;i<40;i++)s.b.tick();s.b.setHealth(18);s.b.invulnerableTime=0;s.p.setPos(s.b.position().add(0,0,-2));stomp(s.p);
            h.assertValueEqual(s.b.state(),FLIPPED,"Guard never expired");
        });
        add(tests,"flipped_damage_double_and_repeat_does_not_extend",(h,s)->{
            s.b.flip();for(int i=0;i<10;i++)s.b.tick();h.assertFalse(s.b.flip(),"Flip refreshed");h.assertValueEqual(s.b.remaining(),50,"Flip time changed");
            s.b.yBodyRot=0;s.p.setPos(s.b.position().add(0,0,2));s.b.hurt(s.p.damageSources().playerAttack(s.p),4);
            Fixtures.near(h,s.b.getHealth(),10,"Exposed belly must bypass front armor");
        });
        for(boolean flipped:List.of(true,false))add(tests,"generic_kill_"+(flipped?"flipped":"front_armored")+"_finite_and_removed",(h,s)->{
            s.b.yBodyRot=0;s.p.setPos(s.b.position().add(0,0,2));
            if(flipped){s.b.flip();h.assertValueEqual(s.b.state(),FLIPPED,"Flip fixture");s.b.kill();}
            else{
                h.assertTrue(BeetleRules.front(s.b.yBodyRot,s.p.position().subtract(s.b.getBoundingBox().getCenter())),"Front fixture");
                // The same generic_kill type and amount as /kill, with an explicit
                // frontal source so the armour branch is also exercised.
                s.b.hurt(new net.minecraft.world.damagesource.DamageSource(s.b.damageSources().genericKill().typeHolder(),s.p),Float.MAX_VALUE);
            }
            h.assertTrue(Float.isFinite(s.b.getHealth()),"/kill produced non-finite health");
            h.assertTrue(s.b.isDeadOrDying() && !s.b.isAlive(),"/kill did not enter death immediately");
            for(int i=0;i<20 && !s.b.isRemoved();i++){s.b.tickCount++;s.b.tick();h.assertTrue(Float.isFinite(s.b.getHealth()),"Death tick corrupted health");}
            h.assertTrue(s.b.isRemoved(),"Killed beetle survived the 20-tick death lifecycle");
        });
        for(boolean release:List.of(true,false))add(tests,"bash_"+(release?"launch_flips_preserves_recoil":"cancel_does_not_flip"),(h,s)->{
            s.p.setPos(s.b.position().add(0,0,-3));var delta=s.b.getBoundingBox().getCenter().subtract(s.p.getEyePosition());
            float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
            SpiritMovement.accept(s.p,new MovementInputPayload(1,h.getLevel().getGameTime(),MovementInputPayload.ACTIVE|MovementInputPayload.BASH,0,0,0,pitch));
            h.assertTrue(s.p.getData(SpiritData.MOVEMENT).bash!=null,"Bash did not acquire beetle");
            if(release){SpiritMovement.accept(s.p,new MovementInputPayload(2,h.getLevel().getGameTime(),MovementInputPayload.ACTIVE,0,0,0,0));
                h.assertValueEqual(s.b.state(),FLIPPED,"Actual release did not flip");Fixtures.near(h,s.b.getDeltaMovement().z,-.8,"Recoil lost");
                var before=s.b.position();s.p.setPos(s.p.position().add(5,0,0));s.b.setNoAi(false);s.b.tick();h.assertTrue(s.b.getZ()<before.z-.1,"Flipped movement erased external recoil");
            }else{SpiritMovement.cancel(s.p,s.p.getData(SpiritData.MOVEMENT));h.assertValueEqual(s.b.state(),IDLE,"Canceled aim flipped");}
        });
        add(tests,"windup_immobile_and_direction_locked_when_target_sidesteps",(h,s)->{
            s.p.setPos(s.b.position().add(0,0,6));s.b.setNoAi(false);s.b.setTarget(s.p);s.b.tick();
            h.assertValueEqual(s.b.state(),WINDUP,"Real goal did not start windup");var origin=s.b.position();var heading=s.b.chargeDirection();
            s.p.setPos(s.p.position().add(5,0,0));
            for(int i=1;i<15;i++){s.b.tick();h.assertValueEqual(s.b.state(),WINDUP,"Windup ended before 15 ticks");Fixtures.near(h,s.b.position().distanceTo(origin),0,"Windup moved");}
            s.b.tick();h.assertValueEqual(s.b.state(),CHARGING,"Charge not started at tick 15");
            h.assertValueEqual(s.b.chargeDirection(),heading,"Target side-step retargeted charge");
            h.assertTrue(s.b.getZ()>origin.z+.5,"Charge did not move forward");Fixtures.near(h,s.b.getX(),origin.x,"Charge followed side-step");
        });
        add(tests,"charge_seven_blocks_then_sixty_tick_cooldown",(h,s)->{
            var before=s.b.position();s.start();s.until(IDLE,40);
            Fixtures.near(h,s.b.chargedDistance(),7,"Charge travel budget");Fixtures.near(h,s.b.getZ()-before.z,7,"Actual charge displacement");
            h.assertValueEqual(s.b.chargeCooldown(),60,"Cooldown at charge end");h.assertFalse(s.b.beginWindup(s.b.position().add(0,0,6)),"Cooldown bypass");
            s.b.setNoAi(true);for(int i=0;i<60;i++)s.b.tick();h.assertTrue(s.b.beginWindup(s.b.position().add(0,0,6)),"Cooldown never expired");
        });
        add(tests,"wall_collision_stuns_and_positive_hit_flips",(h,s)->{
            s.wall();s.start();s.until(STUNNED,40);h.assertValueEqual(s.b.remaining(),30,"Wall stun duration");
            s.b.setInvulnerable(true);h.assertFalse(s.b.hurt(s.p.damageSources().playerAttack(s.p),4),"Invulnerable hit accepted");h.assertValueEqual(s.b.state(),STUNNED,"Rejected damage flipped");
            s.b.setInvulnerable(false);s.b.hurt(s.p.damageSources().playerAttack(s.p),4);h.assertValueEqual(s.b.state(),FLIPPED,"Positive wall-stun hit did not flip");
        });
        add(tests,"wall_stun_expires_after_thirty_ticks",(h,s)->{
            s.wall();s.start();s.until(STUNNED,40);for(int i=0;i<29;i++)s.b.tick();h.assertValueEqual(s.b.state(),STUNNED,"Early wall recovery");
            s.b.tick();h.assertValueEqual(s.b.state(),IDLE,"Wall stun stuck");
        });
        for(String target:List.of("player","sheep","spitter","item"))add(tests,"charge_contact_"+target,(h,s)->{
            Entity victim=s.p;
            if(target.equals("player")){for(int i=0;i<61;i++)s.p.tick();s.p.invulnerableTime=0;s.p.setHealth(20);}
            if(target.equals("sheep"))victim=EntityType.SHEEP.create(h.getLevel());
            if(target.equals("spitter"))victim=OriMobs.GLOOM_SPITTER.get().create(h.getLevel());
            if(target.equals("item"))victim=new ItemEntity(h.getLevel(),0,0,0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
            victim.setPos(s.b.position().add(0,0,3));if(victim instanceof Mob m)m.setNoAi(true);
            if(victim!=s.p){h.getLevel().addFreshEntity(victim);s.extra.add(victim);}
            float health=victim instanceof LivingEntity living?living.getHealth():0;
            s.start();s.until(IDLE,40);h.assertTrue(s.b.chargedDistance()<3,"Charged through "+target);h.assertValueEqual(s.b.remaining(),0,"Entity collision self-stunned");
            if(victim instanceof LivingEntity living)Fixtures.near(h,living.getHealth(),health-(target.equals("player")?6:0),"Contact damage "+target);
            if(target.equals("player"))h.assertTrue(s.p.getDeltaMovement().z>0,"Player not knocked back");
        });
        add(tests,"water_cancels_charge_and_navigation_avoids_it",(h,s)->{
            h.assertTrue(s.b.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER)>0,"Water not avoided");
            s.start();h.getLevel().setBlock(s.b.blockPosition(),Blocks.WATER.defaultBlockState(),2);s.b.tick();s.b.tick();
            h.assertValueEqual(s.b.state(),IDLE,"Water charge remained active");h.assertFalse(s.b.beginWindup(s.b.position().add(0,0,6)),"Charge starts in water");
            s.b.setNoAi(false);var before=s.b.position();int exitTicks=0;while(s.b.isInWater() && exitTicks++<80)s.b.tick();
            h.assertTrue(s.b.position().distanceToSqr(before)>.1 && !s.b.isInWater(),"Beetle failed to walk out of shallow water: "+s.b.position().subtract(before)+" water="+s.b.isInWater()+" path="+s.b.getNavigation().getPath());for(int i=0;i<3;i++)s.b.tick();h.assertTrue(s.b.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER)>0,"Water avoidance not restored on shore");
        });
        add(tests,"saved_flip_and_immunity_do_not_reset",(h,s)->{
            s.b.flip();for(int i=0;i<17;i++)s.b.tick();var saved=s.b.saveWithoutId(new CompoundTag());
            var copy=OriMobs.GLOOM_BEETLE.get().create(h.getLevel());s.extra.add(copy);copy.load(saved);
            h.assertValueEqual(copy.state(),FLIPPED,"Saved flip state");h.assertValueEqual(copy.remaining(),43,"Saved flip time");
            for(int i=0;i<43;i++)copy.tick();copy.tick();saved=copy.saveWithoutId(new CompoundTag());
            s.b.load(saved);h.assertValueEqual(s.b.immunity(),39,"Reload refreshed immunity");h.assertFalse(s.b.flip(),"Reload bypasses immunity");
        });
        add(tests,"saved_windup_and_charge_keep_heading_time_and_travel_budget",(h,s)->{
            s.start();for(int i=0;i<5;i++)s.b.tick();var tag=s.b.saveWithoutId(new CompoundTag());s.b.tick();s.b.load(tag);
            h.assertValueEqual(s.b.state(),WINDUP,"Reload windup state");h.assertValueEqual(s.b.remaining(),10,"Reload windup duration");
            for(int i=0;i<13;i++)s.b.tick();h.assertValueEqual(s.b.state(),CHARGING,"Charge not resumed");double traveled=s.b.chargedDistance();var direction=s.b.chargeDirection();
            tag=s.b.saveWithoutId(new CompoundTag());s.b.tick();s.b.load(tag);
            Fixtures.near(h,s.b.chargedDistance(),traveled,"Reload refreshed charge distance");Fixtures.near(h,s.b.chargeDirection().distanceTo(direction),0,"Reload changed heading");
            s.until(IDLE,30);Fixtures.near(h,s.b.chargedDistance(),7,"Resumed charge exceeds total budget");
        });
        add(tests,"persistent_ruin_guard_still_obeys_peaceful_removal",(h,s)->{
            s.b.setPersistenceRequired();h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);s.b.checkDespawn();
            h.assertTrue(s.b.isRemoved(),"Persistent beetle survived peaceful difficulty");
        });
        add(tests,"flash_has_no_special_beetle_effect",(h,s)->{
            s.p.setPos(s.b.position().add(0,0,-2));h.assertTrue(Flash.keepAlive(s.p),"Flash refused");
            for(int i=0;i<20;i++)Flash.tick(new PlayerTickEvent.Post(s.p));
            Fixtures.near(h,s.b.getHealth(),18,"Flash hurt beetle");h.assertValueEqual(s.b.state(),IDLE,"Flash stunned beetle");
        });
        add(tests,"hint_visible_twelve_blocks_once_and_human_excluded",(h,s)->{
            s.p.setPos(s.b.position().add(0,0,-12.01));s.onlyBeetleUnseen();CompanionHints.check(s.p);h.assertTrue(s.hints().isEmpty(),"Hint beyond 12");
            s.p.setPos(s.b.position().add(0,0,-12));s.p.setData(SpiritData.FORM,false);CompanionHints.check(s.p);h.assertTrue(s.hints().isEmpty(),"Human hint");
            s.p.setData(SpiritData.FORM,true);CompanionHints.check(s.p);var sent=s.hints();h.assertValueEqual(sent.size(),1,"Visible exact-radius hint");h.assertValueEqual(sent.getFirst().id(),"gloom_beetle","Hint id");
            s.p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt=Long.MIN_VALUE;CompanionHints.check(s.p);h.assertTrue(s.hints().isEmpty(),"Seen beetle hint repeated");
        });
        add(tests,"hint_cannot_see_through_wall",(h,s)->{
            s.p.setPos(s.b.position().add(0,0,-4));s.onlyBeetleUnseen();
            for(int x=-2;x<=2;x++)for(int y=0;y<=3;y++)h.getLevel().setBlock(s.b.blockPosition().offset(x,y,-2),Blocks.STONE.defaultBlockState(),2);
            CompanionHints.check(s.p);h.assertTrue(s.hints().isEmpty(),"Hidden beetle hint");h.assertFalse(s.p.getData(SpiritData.PROGRESS).rewards().containsKey("hint/gloom_beetle"),"Hidden hint recorded");
        });
        for(boolean tide:List.of(false,true))add(tests,"light_drop_"+(tide?"tide":"ordinary"),(h,s)->{
            boolean loot=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
            try{h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(true,h.getLevel().getServer());if(tide)s.b.getPersistentData().putBoolean(GloomTide.TAG,true);
                s.b.hurt(s.p.damageSources().playerAttack(s.p),1000);
                int light=h.getLevel().getEntitiesOfClass(ItemEntity.class,s.b.getBoundingBox().inflate(2),e->e.getItem().is(OriItems.SPIRIT_LIGHT)).stream().mapToInt(e->e.getItem().getCount()).sum();
                h.assertValueEqual(light,tide?3:2,"R35 / tide light drop");
            }finally{h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(loot,h.getLevel().getServer());}
        });
        add(tests,"spawn_command_registered",(h,s)->{
            try{int result=h.getLevel().getServer().getCommands().getDispatcher().execute("ori spawn beetle",s.p.createCommandSourceStack().withPermission(4));h.assertTrue(result>0,"Spawn command refused");
                h.assertTrue(h.getLevel().getEntitiesOfClass(GloomBeetle.class,s.p.getBoundingBox().inflate(8)).stream().anyMatch(b->b!=s.b),"Command created no beetle");
            }catch(Exception e){throw new AssertionError(e);}
        });
        return tests;
    }
    private static void stomp(ServerPlayer p){var movement=p.getData(SpiritData.MOVEMENT);movement.mode=MoveMode.STOMP;AirMovement.tickOwned(p,movement,p.level().getGameTime(),true);}
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,Site> run){tests.add(new TestFunction("gloom_beetle","gloom_beetle."+name,"orimod_gametest:platform_room",100,0,true,h->{try(var s=new Site(h)){run.accept(h,s);}h.succeed();}));}
    private static final class Site implements AutoCloseable{
        final GameTestHelper h;final Fixtures.ConnectedPlayer c;final ServerPlayer p;final GloomBeetle b;final List<Entity> extra=new ArrayList<>();final BlockPos base;final net.minecraft.world.Difficulty difficulty;
        Site(GameTestHelper h){
            this.h=h;difficulty=h.getLevel().getDifficulty();h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);c=Fixtures.connectedPlayer(h);p=c.player();CrackedRuinGameTests.prepare(c);p.getAbilities().invulnerable=false;base=h.absolutePos(new BlockPos(24,3,24));
            for(var pos:BlockPos.betweenClosed(base.offset(-6,-1,-14),base.offset(8,3,15)))h.getLevel().setBlock(pos,pos.getY()==base.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            p.setPos(Vec3.atBottomCenterOf(base).add(5,0,-3));p.setNoGravity(true);
            b=OriMobs.GLOOM_BEETLE.get().create(h.getLevel());b.setPos(Vec3.atBottomCenterOf(base));b.setNoAi(true);h.getLevel().addFreshEntity(b);
        }
        void start(){b.setNoAi(false);h.assertTrue(b.beginWindup(b.position().add(0,0,6)),"Windup refused");}
        void until(BeetleRules.State state,int limit){for(int i=0;i<limit && b.state()!=state;i++)b.tick();h.assertValueEqual(b.state(),state,"Never reached "+state+" at "+b.position()+" travel="+b.chargedDistance());}
        void wall(){for(int x=-2;x<=2;x++)for(int y=0;y<=2;y++)h.getLevel().setBlock(base.offset(x,y,3),Blocks.STONE.defaultBlockState(),2);}
        void onlyBeetleUnseen(){var rewards=new HashMap<String,Integer>();for(var hint:CompanionHints.Hint.values())if(hint!=CompanionHints.Hint.GLOOM_BEETLE)rewards.put("hint/"+hint.id,1);p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withRewards(rewards));p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt=Long.MIN_VALUE;c.channel().outboundMessages().clear();}
        List<CompanionHintPayload> hints(){var result=new ArrayList<CompanionHintPayload>();Object packet;while((packet=c.channel().readOutbound())!=null)if(packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof CompanionHintPayload hint)result.add(hint);return result;}
        public void close(){Flash.stop(p);SpiritCombat.cancel(p,"test_cleanup");SpiritMovement.cancel(p,p.getData(SpiritData.MOVEMENT));b.discard();extra.forEach(Entity::discard);h.getLevel().getEntities((Entity)null,new AABB(base).inflate(20),e->e instanceof GloomBeetle || e instanceof ItemEntity).forEach(Entity::discard);c.close();h.getLevel().getServer().setDifficulty(difficulty,true);}
    }
}
