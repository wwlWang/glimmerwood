package cn.wwl.orimod.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** R42: slow gloom orb spat by a {@link GloomSpitter}; straight flight, Bashable, fades after a few seconds. */
public final class GloomShot extends ThrowableProjectile {
    public static final double SPEED = .55;
    public static final float DAMAGE = 3;
    public static final int LIFETIME = 100;

    public GloomShot(EntityType<? extends GloomShot> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected double getDefaultGravity() { return 0; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) cn.wwl.orimod.client.fx.GloomLook.shot(this);
        else if (tickCount > LIFETIME) discard();
    }

    @Override protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide) strike(result.getEntity());
    }

    @Override protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) discard();
    }

    /** Damage the struck entity on behalf of the current owner (the player after a Bash redirect). */
    public boolean strike(Entity target) {
        var owner = getOwner() instanceof LivingEntity living ? living : null;
        // R50: Bashed orbs are the main way to hurt the Gloom Heart.
        float damage = target instanceof GloomHeart && owner instanceof net.minecraft.world.entity.player.Player ? GloomHeart.REFLECT_DAMAGE : DAMAGE;
        if (target instanceof GloomHeart && owner instanceof GloomHeart) return false;
        return target.hurt(damageSources().mobProjectile(this, owner), damage);
    }
}
