package cn.wwl.orimod.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Environmental health loss does not own the player's action; an external attack does. */
public final class DamageInterrupts {
    private DamageInterrupts() {}

    public static boolean action(Entity victim, DamageSource source, float damage) {
        if (!Float.isFinite(damage) || damage <= 0) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && attacker.getUUID().equals(victim.getUUID())) return false;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner == null || !owner.getUUID().equals(victim.getUUID());
        }
        return attacker instanceof LivingEntity;
    }
}
