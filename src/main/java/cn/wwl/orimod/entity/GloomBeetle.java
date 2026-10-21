package cn.wwl.orimod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import java.util.*;
import static cn.wwl.orimod.entity.BeetleRules.*;

/** Ground ambusher: aim once, telegraph, then commit. Physics/knockback remain active while flipped. */
public final class GloomBeetle extends Monster {
    private static final EntityDataAccessor<Integer> STATE=SynchedEntityData.defineId(GloomBeetle.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEFT=SynchedEntityData.defineId(GloomBeetle.class,EntityDataSerializers.INT);
    private Clock clock=Clock.idle();
    private Vec3 chargeDirection=Vec3.ZERO;
    private float chargeYaw;
    private double charged;
    private float roll,previousRoll;
    public GloomBeetle(EntityType<? extends GloomBeetle> type,Level level){
        super(type,level);xpReward=5;setPathfindingMalus(PathType.WATER,32);
        if(getNavigation() instanceof GroundPathNavigation ground)ground.setCanFloat(false);
    }
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,18).add(Attributes.MOVEMENT_SPEED,.24).add(Attributes.FOLLOW_RANGE,16).add(Attributes.ATTACK_DAMAGE,6);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(STATE,0);builder.define(LEFT,0);}
    public State state(){return State.values()[entityData.get(STATE)];}
    public String stateName(){return state().name().toLowerCase(Locale.ROOT);}
    public int remaining(){return entityData.get(LEFT);}
    public int immunity(){return clock.immunity();}
    public int chargeCooldown(){return clock.cooldown();}
    public Vec3 chargeDirection(){return chargeDirection;}
    public double chargedDistance(){return charged;}
    public float roll(float partial){return Mth.lerp(partial,previousRoll,roll);}
    private void clock(Clock value){clock=value;entityData.set(STATE,value.state().ordinal());entityData.set(LEFT,value.left());}
    @Override protected void registerGoals(){
        goalSelector.addGoal(1,new LeaveWaterGoal());
        goalSelector.addGoal(2,new ChargeGoal());
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.8){@Override public boolean canUse(){return state()==State.IDLE && super.canUse();}@Override public boolean canContinueToUse(){return state()==State.IDLE && super.canContinueToUse();}});
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8){@Override public boolean canUse(){return state()==State.IDLE && super.canUse();}});
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    public boolean beginWindup(Vec3 target){
        if(level().isClientSide || state()!=State.IDLE || clock.cooldown()>0 || isInWater())return false;
        var delta=target.subtract(position()).multiply(1,0,1);
        if(!chargeRange(delta.lengthSqr()))return false;
        chargeDirection=delta.normalize();chargeYaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));charged=0;
        getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);clock(clock.with(State.WINDUP,WINDUP));faceCharge();
        playSound(SoundEvents.RAVAGER_STEP,.8f,.6f);return true;
    }
    public static void flip(Entity target){if(target instanceof GloomBeetle beetle)beetle.flip();}
    public boolean flip(){
        if(level().isClientSide || !isAlive() || !clock.canFlip())return false;
        getNavigation().stop();clock(clock.flipped());return true;
    }
    /** Runs only after positive applied damage: an invulnerable/rejected hit cannot flip a stunned beetle. */
    public static void damaged(LivingDamageEvent.Post event){
        if(event.getNewDamage()>0 && event.getEntity() instanceof GloomBeetle beetle && beetle.state()==State.STUNNED)beetle.flip();
    }
    private void stopCharge(boolean wall){getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);clock(clock.stopped(wall));}
    private void faceCharge(){setYRot(chargeYaw);yBodyRot=chargeYaw;setYHeadRot(chargeYaw);}
    @Override public void tick(){
        if(!level().isClientSide){
            clock(clock.step());
            if(isInWater() && (state()==State.WINDUP || state()==State.CHARGING))stopCharge(false);
            if(state()!=State.IDLE)getNavigation().stop();
        }
        var before=position();var box=getBoundingBox();boolean charging=state()==State.CHARGING;
        super.tick();
        if(level().isClientSide){previousRoll=roll;roll=Mth.approach(roll,state()==State.FLIPPED?180:0,30);return;}
        if(state()==State.WINDUP || state()==State.CHARGING)faceCharge();
        if(state()==State.WINDUP && remaining()%3==0 && level() instanceof ServerLevel server){
            var rear=position().subtract(chargeDirection.scale(.4));server.sendParticles(ParticleTypes.SMOKE,rear.x,rear.y+.08,rear.z,3,.25,.05,.25,.015);
        }
        if(charging && state()==State.CHARGING){
            var movement=position().subtract(before);charged+=movement.horizontalDistance();
            var center=box.getCenter();var end=center.add(movement);
            var hit=level().getEntities(this,box.minmax(getBoundingBox()).inflate(.025),e->e.isAlive() && !e.isSpectator() && !e.noPhysics && !isPassengerOfSameVehicle(e)).stream()
                    .filter(e->{var bounds=e.getBoundingBox().inflate(getBbWidth()/2.+.025,getBbHeight()/2.+.025,getBbWidth()/2.+.025);return bounds.contains(center) || bounds.clip(center,end).isPresent();})
                    .min(Comparator.comparingDouble(e->e.distanceToSqr(before))).orElse(null);
            if(hit!=null){
                if(hit instanceof Player player && player.hurt(damageSources().mobAttack(this),6)){
                    player.push(chargeDirection.x*.7,.25,chargeDirection.z*.7);player.hurtMarked=true;
                }
                stopCharge(false); // Any entity stops the charge, including harmless bystanders; no self-stun.
            }else if(horizontalCollision)stopCharge(true);
            else if(charged>=DISTANCE-1e-5)stopCharge(false);
        }
    }
    @Override public void travel(Vec3 input){
        if(!level().isClientSide && state()!=State.IDLE){
            setSpeed(0);setZza(0);setXxa(0);
            if(state()==State.CHARGING)setDeltaMovement(chargeDirection.scale(Math.min(SPEED,Math.max(0,DISTANCE-charged))).add(0,getDeltaMovement().y,0));
            else if(state()==State.WINDUP)setDeltaMovement(0,getDeltaMovement().y,0);
            super.travel(Vec3.ZERO);
        }else super.travel(input);
    }
    private Vec3 incoming(DamageSource source){
        var direct=source.getDirectEntity();
        if(direct instanceof Projectile && direct.getDeltaMovement().lengthSqr()>1e-7)return direct.getDeltaMovement().scale(-1);
        var pos=direct!=null?direct.position():source.getSourcePosition();
        return pos==null?null:pos.subtract(getBoundingBox().getCenter());
    }
    @Override public boolean hurt(DamageSource source,float amount){
        boolean armored=state()!=State.FLIPPED && front(yBodyRot,incoming(source));
        boolean applied=super.hurt(source,amount*damageScale(state(),armored));
        if(applied && armored && level() instanceof ServerLevel server){
            playSound(SoundEvents.ANVIL_HIT,.5f,1.7f);server.sendParticles(ParticleTypes.ELECTRIC_SPARK,getX(),getY()+.5,getZ(),5,.45,.15,.45,.02);
        }
        return applied;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putString("BeetleState",state().name());tag.putInt("BeetleLeft",remaining());tag.putInt("BeetleImmunity",clock.immunity());tag.putInt("BeetleCooldown",clock.cooldown());
        tag.putDouble("BeetleTravel",charged);tag.putFloat("BeetleYaw",chargeYaw);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);State state;try{state=State.valueOf(tag.getString("BeetleState"));}catch(IllegalArgumentException e){state=State.IDLE;}
        int left=Math.clamp(tag.getInt("BeetleLeft"),0,FLIP);if(left==0 && state!=State.CHARGING)state=State.IDLE;
        clock(new Clock(state,left,Math.clamp(tag.getInt("BeetleImmunity"),0,IMMUNITY),Math.clamp(tag.getInt("BeetleCooldown"),0,COOLDOWN)));
        double savedTravel=tag.getDouble("BeetleTravel");charged=Double.isFinite(savedTravel)?Mth.clamp(savedTravel,0,DISTANCE):0;
        chargeYaw=tag.getFloat("BeetleYaw");if(!Float.isFinite(chargeYaw))chargeYaw=0;
        chargeDirection=Vec3.directionFromRotation(0,chargeYaw);
    }
    private final class ChargeGoal extends Goal {
        ChargeGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return !isInWater() && (state()!=State.IDLE || getTarget()!=null && getTarget().isAlive());}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            if(state()!=State.IDLE)return;
            var target=getTarget();if(target==null)return;
            getLookControl().setLookAt(target,30,30);
            if(clock.cooldown()==0 && hasLineOfSight(target) && beginWindup(target.position()))return;
            getNavigation().moveTo(target,1);
        }
        @Override public void stop(){getNavigation().stop();}
    }
    private final class LeaveWaterGoal extends Goal {
        private Vec3 shore;
        LeaveWaterGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return isInWater() && state()!=State.FLIPPED && state()!=State.STUNNED;}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void start(){
            // A high water malus prevents even the first escape step of this 1.1-wide mob.
            // Allow walking OUT while this goal owns movement, then restore avoidance on dry land.
            setPathfindingMalus(PathType.WATER,0);
            shore=null;double best=Double.MAX_VALUE;
            for(var p:BlockPos.betweenClosed(blockPosition().offset(-6,-2,-6),blockPosition().offset(6,2,6))){
                if(!level().hasChunkAt(p) || !level().getFluidState(p).isEmpty() || !level().getBlockState(p.below()).isFaceSturdy(level(),p.below(),net.minecraft.core.Direction.UP))continue;
                var at=Vec3.atBottomCenterOf(p);double distance=distanceToSqr(at);
                var box=getBoundingBox().move(at.subtract(position()));
                if(distance<best && !level().containsAnyLiquid(box) && level().noCollision(box)){best=distance;shore=at;}
            }
        }
        @Override public void tick(){if(shore!=null){
            getLookControl().setLookAt(shore.x,shore.y+.5,shore.z);
            // moveTo(x,y,z) accepts a path one block short. That can finish while a wide beetle
            // is still immersed, so require the actual dry destination instead of its neighbor.
            getNavigation().moveTo(getNavigation().createPath(BlockPos.containing(shore),0),1.2);
            if(horizontalCollision)getJumpControl().jump();
        }}
        @Override public void stop(){getNavigation().stop();setPathfindingMalus(PathType.WATER,32);}
    }
}
