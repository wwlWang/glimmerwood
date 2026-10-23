package cn.wwl.orimod.entity;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Shared by AI selection, retained targets, contact hits and area attacks. */
public final class GloomTargets {
    private GloomTargets() {}

    public static boolean attackable(LivingEntity target) {
        return target != null && target.isAlive() && target.isAttackable()
                && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
    }

    public static boolean canHit(Mob attacker, LivingEntity target) {
        return attackable(target) && attacker.canAttack(target) && !attacker.isAlliedTo(target);
    }
}
