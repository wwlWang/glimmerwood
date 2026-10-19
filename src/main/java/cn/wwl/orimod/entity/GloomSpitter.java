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

    public GloomSpitter(EntityType<? extends GloomSpitter> type, Level level) { super(type, level); xpReward = 5; }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.MOVEMENT_SPEED, .22).add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1, ATTACK_INTERVAL, 14));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) cn.wwl.orimod.client.fx.GloomLook.body(this);
    }

    @Override public void performRangedAttack(LivingEntity target, float power) { spit(target); }

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
