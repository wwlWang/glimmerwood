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

    /** Deliberately aimed attacks follow vanilla damage/anger, while retaining friendly protection. */
    public static boolean canHitDirect(Player player, LivingEntity target) {
        if (target == player || !target.isAlive() || target.isSpectator() || player.isAlliedTo(target)) return false;
        if (target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID())) return false;
        if (target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse
                && player.getUUID().equals(horse.getOwnerUUID())) return false;
        return !(target instanceof PracticeTarget practice) || player.getUUID().equals(practice.owner);
    }

    /** Pick the first entity, including protected friends, so a friend never makes the ray hit through it. */
    public static LivingEntity aimed(Player player, double reach) {
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(Math.min(reach, player.entityInteractionRange())));
        var wall = player.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) to = wall.getLocation();
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, from, to,
                player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1),
                e -> !e.isSpectator() && e.isPickable() && !e.isPassengerOfSameVehicle(player), from.distanceToSqr(to));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

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
