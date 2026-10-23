package cn.wwl.orimod.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * R61 Gloom Hawk: an original aerial threat (in the spirit of Kuro, not a copy). It circles high above its
 * prey, shrieks and flares red for {@link #TELEGRAPH_TICKS}, then dives at the marked spot; touching it mid-dive
 * hurts. Its dive can be dodged with a dash, and it can be Bashed like any enemy.
 */
public final class GloomHawk extends Monster {
    public static final int CIRCLE = 0, TELEGRAPH = 1, DIVE = 2, CLIMB = 3;
    public static final int TELEGRAPH_TICKS = 20, DIVE_TICKS = 24, CIRCLE_TICKS = 70;
    public static final double HEIGHT = 7, ORBIT = 6, DIVE_SPEED = 1.1, CLIMB_SPEED = .35;
    public static final float DIVE_DAMAGE = 5;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(GloomHawk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIMER = SynchedEntityData.defineId(GloomHawk.class, EntityDataSerializers.INT);
    private Vec3 mark;
    private boolean struck;
    /** R98: retreat from the light, cancelling any telegraph or dive. */
    private int dazzled;
    private Vec3 lightOrigin;
    public static final double DAZZLE_RETREAT_SPEED = .35, DAZZLE_LIFT = .15;

    public GloomHawk(EntityType<? extends GloomHawk> type, Level level) { super(type, level); setNoGravity(true); xpReward = 8; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.FLYING_SPEED, .6);
    }

    @Override public boolean canAttack(LivingEntity target) {
        return GloomTargets.attackable(target) && super.canAttack(target);
    }

    @Override public void setTarget(@javax.annotation.Nullable LivingEntity target) {
        super.setTarget(GloomTargets.attackable(target) ? target : null);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STATE, CIRCLE); builder.define(TIMER, CIRCLE_TICKS); }
    public int state() { return entityData.get(STATE); }
    public int timer() { return entityData.get(TIMER); }
    private void timer(int ticks) { entityData.set(TIMER, ticks); }
    private int advanceTimer() { int left = timer() - 1; timer(left); return left; }
    public Vec3 mark() { return mark; }
    public boolean dazzled() { return dazzled > 0; }
    public void dazzle(int ticks) { dazzle(ticks, getTarget() == null ? position().subtract(1, 0, 0) : getTarget().position()); }
    public void dazzle(int ticks, Vec3 light) {
        if (ticks <= 0) return;
        dazzled = Math.max(dazzled, ticks); lightOrigin = light;
        mark = null; struck = false; timer(CIRCLE_TICKS); entityData.set(STATE, CIRCLE);
        retreat();
    }
    private void retreat() {
        var away = position().subtract(lightOrigin).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < 1e-6) away = new Vec3(1, 0, 0);
        setDeltaMovement(away.scale(DAZZLE_RETREAT_SPEED).add(0, DAZZLE_LIFT, 0));
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {}

    /** Velocity toward the mark at dive speed. */
    public static Vec3 diveVelocity(Vec3 from, Vec3 to) {
        var d = to.subtract(from);
        return d.lengthSqr() < 1e-6 ? Vec3.ZERO : d.normalize().scale(DIVE_SPEED);
    }

    public void telegraph(LivingEntity target) {
        if (dazzled > 0 || !GloomTargets.attackable(target)) return;
        setTarget(target);
        mark = target.position().add(0, .5, 0);
        entityData.set(STATE, TELEGRAPH); timer(TELEGRAPH_TICKS); struck = false;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.6f, .7f);
    }

    /** One step of the attack cycle (driven by tick, callable from tests). */
    public void step() {
        if (dazzled > 0) { dazzled--; retreat(); return; }
        if (getTarget()!=null && !GloomTargets.attackable(getTarget())) {
            setTarget(null); mark=null; timer(CIRCLE_TICKS); entityData.set(STATE,CIRCLE); setDeltaMovement(Vec3.ZERO); return;
        }
        int s = state();
        if (s == TELEGRAPH) {
            setDeltaMovement(getDeltaMovement().scale(.5));
            if (advanceTimer() <= 0) { entityData.set(STATE, DIVE); timer(DIVE_TICKS); level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.4f, .6f); }
        } else if (s == DIVE) {
            if (mark != null) setDeltaMovement(diveVelocity(position(), mark));
            if (!level().isClientSide && !struck)
                for (var e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(.4), e -> e == getTarget()))
                    if (strike(e)) { struck = true; break; }
            if (advanceTimer() <= 0 || mark != null && position().distanceTo(mark) < .8 || horizontalCollision || verticalCollision) { entityData.set(STATE, CLIMB); timer(30); }
        } else if (s == CLIMB) {
            setDeltaMovement(getDeltaMovement().multiply(.8, 0, .8).add(0, CLIMB_SPEED, 0));
            if (advanceTimer() <= 0) { entityData.set(STATE, CIRCLE); timer(CIRCLE_TICKS); }
        }
    }

    /** Dive contact. @return whether the target was hurt. */
    public boolean strike(LivingEntity target) {
        if (dazzled > 0 || !GloomTargets.canHit(this, target)) return false;
        boolean hurt = target.hurt(damageSources().mobAttack(this), DIVE_DAMAGE);
        if (hurt) { var push = getDeltaMovement().multiply(1, 0, 1).normalize().scale(.6); target.push(push.x, .3, push.z); target.hurtMarked = true; }
        return hurt;
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { cn.wwl.orimod.client.fx.GloomLook.hawk(this); return; }
        if (dazzled > 0) { step(); return; }
        if (isNoAi()) return;
        var target = level().getNearestPlayer(getX(), getY(), getZ(), 32, p -> p instanceof Player pl && GloomTargets.canHit(this, pl));
        if (state() == CIRCLE) {
            if (target == null) { setDeltaMovement(getDeltaMovement().scale(.9)); return; }
            double a = tickCount * .05;
            var goal = target.position().add(Math.cos(a) * ORBIT, HEIGHT, Math.sin(a) * ORBIT);
            setDeltaMovement(goal.subtract(position()).scale(.06));
            if (advanceTimer() <= 0) telegraph(target);
        } else step();
        if (getDeltaMovement().horizontalDistanceSqr() > 1e-4) setYRot((float) Math.toDegrees(Math.atan2(-getDeltaMovement().x, getDeltaMovement().z)));
    }

    /** R68: every hawk carries a map fragment. */
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.MAP_FRAGMENT.get()));
    }
}
