package cn.wwl.orimod.combat;

import cn.wwl.orimod.world.PracticeTarget;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** Area swings protect bystanders; neutral mobs become targets only when attacking this player. */
public final class CombatTargets {
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> CONDITIONALLY_NEUTRAL =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("orimod", "conditionally_neutral"));
    private CombatTargets() {}

    /** An orphaned area effect cannot infer which player a neutral mob is angry at. */
    public static boolean canHitWithoutOwner(LivingEntity target) {
        return target.isAlive() && !target.isSpectator() && target instanceof Enemy && !(target instanceof NeutralMob)
                && !target.getType().is(CONDITIONALLY_NEUTRAL);
    }

    public static boolean canHit(Player player, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target instanceof Player || player.isAlliedTo(target)
                || target instanceof AbstractVillager || target instanceof TamableAnimal pet && pet.isTame()) return false;
        if (target instanceof PracticeTarget practice) return player.getUUID().equals(practice.owner);
        boolean attackingPlayer = target instanceof Mob mob && mob.getTarget() == player;
        if (target.getType().is(CONDITIONALLY_NEUTRAL)) {
            if (attackingPlayer || target.getLastHurtByMob() == player) return true;
            if (target instanceof Mob mob) {
                var brain = mob.getBrain();
                return brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                        && brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(player::equals).isPresent()
                        || brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
                        && brain.getMemory(MemoryModuleType.ANGRY_AT).filter(player.getUUID()::equals).isPresent();
            }
            return false;
        }
        if (target instanceof NeutralMob neutral) return attackingPlayer || neutral.isAngryAt(player);
        return target instanceof Enemy || attackingPlayer;
    }
}
