package cn.wwl.orimod.combat;

import cn.wwl.orimod.world.PracticeTarget;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/** Area swings protect bystanders; neutral mobs become targets only when attacking this player. */
public final class CombatTargets {
    private CombatTargets() {}

    /** An orphaned area effect cannot infer which player a neutral mob is angry at. */
    public static boolean canHitWithoutOwner(LivingEntity target) {
        return target.isAlive() && !target.isSpectator() && target instanceof Enemy && !(target instanceof NeutralMob);
    }

    public static boolean canHit(Player player, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target instanceof Player || player.isAlliedTo(target)
                || target instanceof AbstractVillager || target instanceof TamableAnimal pet && pet.isTame()) return false;
        if (target instanceof PracticeTarget practice) return player.getUUID().equals(practice.owner);
        boolean attackingPlayer = target instanceof Mob mob && mob.getTarget() == player;
        if (target instanceof NeutralMob neutral) return attackingPlayer || neutral.isAngryAt(player);
        return target instanceof Enemy || attackingPlayer;
    }
}
