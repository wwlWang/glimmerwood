package cn.wwl.orimod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;

/**
 * R42 Gloom Spitter: an original corrupted-light creature (inspired by the projectile spitters of Ori, not a
 * copy). It keeps its distance and spits slow gloom orbs, which Ori can Bash back at it. It has no model
 * asset: the client draws it as a cluster of dark violet light around a pale core.
 */
public final class GloomSpitter extends Monster implements RangedAttackMob {
    public static final int ATTACK_INTERVAL = 50;
    /** R48: telegraph - the spitter swells and glows for this many ticks before the orb leaves. */
    public static final int CHARGE_TICKS = 14;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> CHARGE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(GloomSpitter.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private LivingEntity chargeTarget;
    /** R98: light interrupts telegraphing and resets the ranged goal's attack timer. */
    private int dazzled;

    public GloomSpitter(EntityType<? extends GloomSpitter> type, Level level) { super(type, level); xpReward = 5; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.MOVEMENT_SPEED, .22).add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        var rangedGoal = new RangedAttackGoal(this, 1, ATTACK_INTERVAL, 14) {
            @Override public boolean canUse() { return dazzled == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return dazzled == 0 && super.canContinueToUse(); }
            // Mob can tick running goals before its next selector cleanup pass.
            @Override public void tick() { if (dazzled == 0) super.tick(); }
        };
        goalSelector.addGoal(2, rangedGoal);
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(CHARGE, 0);
    }
    /** Ticks left before the charged orb leaves (0 when idle). */
    public int charge() { return entityData.get(CHARGE); }
    public boolean dazzled() { return dazzled > 0; }

    public void dazzle(int ticks) {
        if (ticks <= 0) return;
        dazzled = Math.max(dazzled, ticks);
        entityData.set(CHARGE, 0); chargeTarget = null;
        // Only GoalSelector may stop its WrappedGoal; calling the inner stop()
        // would clear its target while the wrapper still considers it running.
        getNavigation().stop();
    }

    /** Advance the telegraph; the orb leaves when it runs out. @return the shot, if one left this tick. */
    public GloomShot tickCharge() {
        if (dazzled > 0) return null;
        int left = charge();
        if (left <= 0) return null;
        entityData.set(CHARGE, left - 1);
        if (left > 1) return null;
        var target = chargeTarget; chargeTarget = null;
        return target != null && target.isAlive() && target.level() == level() ? spit(target) : null;
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (dazzled > 0) dazzled--;
            else tickCharge();
        }
        if (level().isClientSide) cn.wwl.orimod.client.fx.GloomLook.body(this);
    }

    @Override public void performRangedAttack(LivingEntity target, float power) {
        if (dazzled > 0 || charge() > 0) return;
        chargeTarget = target; entityData.set(CHARGE, CHARGE_TICKS);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1, .6f);
    }

    public GloomShot spit(LivingEntity target) {
        var shot = new GloomShot(OriMobs.GLOOM_SHOT.get(), level());
        shot.setOwner(this);
        shot.setPos(getX(), getEyeY() - .1, getZ());
        double dx = target.getX() - getX(), dy = target.getY(.5) - shot.getY(), dz = target.getZ() - getZ();
        shot.shoot(dx, dy, dz, (float) GloomShot.SPEED, 2);
        level().addFreshEntity(shot);
        playSound(SoundEvents.LLAMA_SPIT, .8f, .7f);
        return shot;
    }
}
