package cn.wwl.orimod.entity;

import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared, expiring recovery pickup. Server consumes one entity once; never enters an inventory. */
public final class RecoveryOrb extends Entity {
    private static final EntityDataAccessor<Boolean> LIFE = SynchedEntityData.defineId(RecoveryOrb.class, EntityDataSerializers.BOOLEAN);
    private int age;
    public RecoveryOrb(EntityType<? extends RecoveryOrb> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(LIFE, true); }
    public boolean life() { return entityData.get(LIFE); }
    public void life(boolean life) { entityData.set(LIFE, life); }
    public int age() { return age; }
    public boolean needed(ServerPlayer p) {
        return !isRemoved() && p.level() == level() && p.isAlive() && !p.isSpectator() && SpiritData.isSpirit(p)
                && SpiritData.isAwakened(p) && (life() ? p.getHealth() < p.getMaxHealth() : SpiritProgression.energy(p) < SpiritProgression.maxEnergy(p));
    }
    /** Used by touch and magnet completion; server-thread serialization prevents two players consuming a pickup. */
    public boolean collect(ServerPlayer p) {
        if (level().isClientSide || age < RecoveryTuning.ORB_PICKUP_DELAY || !needed(p)) return false;
        if (life()) {
            float before = p.getHealth(); p.heal(RecoveryTuning.ORB_LIFE);
            if (p.getHealth() <= before) return false; // Respect mods cancelling healing, without destroying the pickup.
        } else p.setData(SpiritData.ENERGY, Math.min(SpiritProgression.maxEnergy(p), SpiritProgression.energy(p) + RecoveryTuning.ORB_ENERGY));
        discard();
        level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, .25f, life() ? 1.3f : 1.6f);
        return true;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (++age >= RecoveryTuning.ORB_LIFETIME) { discard(); return; }
        var targets = level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(RecoveryTuning.ORB_MAGNET_RADIUS),
                p -> needed(p) && distanceToSqr(p) <= RecoveryTuning.ORB_MAGNET_RADIUS * RecoveryTuning.ORB_MAGNET_RADIUS);
        var target = age >= RecoveryTuning.ORB_PICKUP_DELAY ? targets.stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null) : null;
        if (target != null) {
            Vec3 toward = target.position().add(0, target.getBbHeight() * .45, 0).subtract(position());
            // A wall can block a pickup; pulling does not teleport it through blocks.
            if (toward.lengthSqr() <= RecoveryTuning.ORB_PICKUP_RADIUS * RecoveryTuning.ORB_PICKUP_RADIUS && target.hasLineOfSight(this) && collect(target)) return;
            setDeltaMovement(toward.normalize().scale(RecoveryTuning.ORB_SPEED));
        } else setDeltaMovement(getDeltaMovement().add(0, -.03, 0).scale(.96));
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(.6, 0, .6));
        hurtMarked = true;
    }
    @Override protected void readAdditionalSaveData(CompoundTag t) { life(t.getBoolean("life")); age = Math.clamp(t.getInt("age"), 0, RecoveryTuning.ORB_LIFETIME); }
    @Override protected void addAdditionalSaveData(CompoundTag t) { t.putBoolean("life", life()); t.putInt("age", age); }
}
