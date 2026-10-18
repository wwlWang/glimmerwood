package cn.wwl.orimod.combat;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class SpiritCombat {
    public static final int BOW_MIN_CHARGE = 5, BOW_FULL_CHARGE = 20;
    public static final float BOW_COST = 12;
    private static final int[] WINDUP = {0, 2, 3, 4}, RECOVERY = {0, 8, 8, 12};
    private static final float[] DAMAGE = {0, 4, 5, 7};
    private SpiritCombat() {}

    public static int windup(int stage) { return WINDUP[Math.clamp(stage, 1, 3)]; }
    public static int recoveryStart(int stage) { return windup(stage) + 2; }
    public static int duration(int stage) { return recoveryStart(stage) + RECOVERY[Math.clamp(stage, 1, 3)]; }
    public static String phase(int stage, long elapsed) {
        return elapsed < windup(stage) ? "windup" : elapsed < recoveryStart(stage) ? "hit" : "recovery";
    }

    public static boolean available(Player player) {
        return SpiritData.isAwakened(player) && SpiritData.isSpirit(player) && player.isAlive()
                && !player.isSpectator() && !player.isSleeping() && !player.isPassenger() && !player.hasContainerOpen();
    }

    private static boolean movementOwnsAction(Player player) {
        return switch (player.getData(SpiritData.MOVEMENT).mode) {
            case DASH, BASH_AIM, GRAPPLE, STOMP -> true;
            default -> false;
        };
    }

    public static void attack(ServerPlayer player) {
        if (!available(player) || !player.getMainHandItem().is(OriItems.SPIRIT_BLADE) || movementOwnsAction(player)) return;
        var s = player.getData(SpiritData.COMBAT);
        long now = player.level().getGameTime();
        if (s.mode.equals("blade")) {
            long elapsed = now - s.startedAt;
            if (s.stage < 3 && elapsed >= windup(s.stage) && elapsed < duration(s.stage)) s.buffered = true;
        } else if (s.mode.equals("idle")) startBlade(player, 1);
    }

    private static void startBlade(ServerPlayer player, int stage) {
        var s = player.getData(SpiritData.COMBAT);
        s.mode = "blade"; s.stage = stage; s.startedAt = player.level().getGameTime();
        s.weapon = player.getMainHandItem(); s.buffered = false; s.hitTargets.clear(); s.bladeStarts++;
        publish(player);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void attackEntity(AttackEntityEvent event) {
        if (!event.getEntity().getMainHandItem().is(OriItems.SPIRIT_BLADE)) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) attack(player);
    }

    public static boolean canUseBow(Player player) {
        return available(player) && Ability.BOW.unlocked(player) && SpiritProgression.energy(player) >= BOW_COST
                && !movementOwnsAction(player) && !player.getCooldowns().isOnCooldown(OriItems.SPIRIT_BOW.get());
    }

    public static boolean beginBow(ServerPlayer player, ItemStack stack) {
        var s = player.getData(SpiritData.COMBAT);
        if (!canUseBow(player) || player.level().getGameTime() < s.nextBowAt || !s.mode.equals("idle")) return false;
        s.mode = "bow"; s.stage = 0; s.startedAt = player.level().getGameTime(); s.weapon = stack;
        publish(player);
        return true;
    }

    public static void releaseBow(ServerPlayer player, ItemStack stack) {
        var s = player.getData(SpiritData.COMBAT);
        if (!s.mode.equals("bow")) return;
        int charge = (int)Math.min(BOW_FULL_CHARGE, player.level().getGameTime() - s.startedAt);
        boolean valid = s.weapon == stack && canUseBow(player) && charge >= BOW_MIN_CHARGE;
        s.mode = "idle"; s.weapon = ItemStack.EMPTY;
        publish(player);
        if (!valid) return;
        float power = (float)charge / BOW_FULL_CHARGE;
        var arrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), stack.copy());
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(2.5);
        arrow.setCritArrow(false); // Keep damage deterministic: no random full-charge critical bonus.
        // Exclude shooter momentum so the reticle and the shot have the same direction during movement.
        Vec3 direction = player.getLookAngle();
        arrow.shoot(direction.x, direction.y, direction.z, 1.2f + 1.8f * power, 0);
        arrow.addTag("orimod_spirit_arrow");
        if (!player.serverLevel().addFreshEntity(arrow)) return;
        SpiritProgression.spendEnergy(player, BOW_COST);
        s.bowShots++; s.nextBowAt = player.level().getGameTime() + 6;
        player.getCooldowns().addCooldown(OriItems.SPIRIT_BOW.get(), 6);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(OriItems.SPIRIT_BOW.get()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, .65f, 1 + power * .3f);
    }

    /** Ability starts may cancel a bow or blade recovery; windup and hit windows remain committed. */
    public static boolean allowMovement(ServerPlayer player) {
        var s = player.getData(SpiritData.COMBAT);
        if (s.mode.equals("blade") && player.level().getGameTime() - s.startedAt < recoveryStart(s.stage)) return false;
        if (!s.mode.equals("idle")) cancel(player, "movement");
        return true;
    }

    public static void cancel(ServerPlayer player, String reason) {
        var s = player.getData(SpiritData.COMBAT);
        if (s.mode.equals("idle")) return;
        s.mode = "idle"; s.stage = 0; s.buffered = false; s.weapon = ItemStack.EMPTY; s.hitTargets.clear();
        s.lastCancel = reason; s.cancellations++;
        if (player.isUsingItem() && player.getUseItem().is(OriItems.SPIRIT_BOW)) player.stopUsingItem();
        publish(player);
    }

    private static void publish(ServerPlayer player) {
        var s = player.getData(SpiritData.COMBAT);
        player.setData(SpiritData.COMBAT_VIEW, new CombatView(++s.actionId, s.mode, s.stage, s.startedAt));
    }

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var s = player.getData(SpiritData.COMBAT);
        if (s.mode.equals("idle")) return;
        if (!available(player) || movementOwnsAction(player)) { cancel(player, "unavailable"); return; }
        if (s.mode.equals("bow")) {
            if (!player.isUsingItem() || player.getUseItem() != s.weapon) cancel(player, "item");
            return;
        }
        if (player.getMainHandItem() != s.weapon || !s.weapon.is(OriItems.SPIRIT_BLADE)) { cancel(player, "item"); return; }
        long elapsed = player.level().getGameTime() - s.startedAt;
        if (elapsed >= duration(s.stage)) {
            if (s.buffered && s.stage < 3) startBlade(player, s.stage + 1);
            else { s.mode = "idle"; s.stage = 0; s.weapon = ItemStack.EMPTY; publish(player); }
            return;
        }
        if (elapsed >= windup(s.stage) && elapsed < recoveryStart(s.stage)) hitBlade(player, s);
    }

    private static void hitBlade(ServerPlayer player, CombatState s) {
        Vec3 origin = player.getEyePosition(), direction = player.getLookAngle();
        double reach = s.stage == 3 ? 3.2 : 2.8;
        for (var target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                entity -> CombatTargets.canHit(player, entity))) {
            var box = target.getBoundingBox();
            Vec3 closest = new Vec3(Math.clamp(origin.x, box.minX, box.maxX), Math.clamp(origin.y, box.minY, box.maxY), Math.clamp(origin.z, box.minZ, box.maxZ));
            Vec3 offset = closest.subtract(origin);
            if (offset.lengthSqr() > reach * reach || offset.lengthSqr() > .01 && direction.dot(offset.normalize()) < .5
                    || !SpiritTargets.visible(player, closest) || s.hitTargets.contains(target.getUUID())) continue;
            s.hitTargets.add(target.getUUID()); // A failed/invulnerable hit cannot retry within this swing.
            if (target.hurt(player.damageSources().playerAttack(player), DAMAGE[s.stage])) {
                s.bladeHits++;
                target.knockback(s.stage == 3 ? .45 : .15, -direction.x, -direction.z);
                cn.wwl.orimod.fx.SpiritFx.emit(player.serverLevel(), cn.wwl.orimod.fx.SpiritFxKind.BLADE_HIT, closest, player);
                player.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, .5f, 1 + s.stage * .1f);
                s.weapon.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
        }
    }

    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && DamageInterrupts.action(player, event.getSource(), event.getNewDamage())) {
            cancel(player, "damage");
            cn.wwl.orimod.movement.SpiritMovement.interrupt(player);
        }
        if (event.getSource().getDirectEntity() instanceof Arrow arrow && arrow.getTags().contains("orimod_spirit_arrow")
                && event.getSource().getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0) {
            player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, .35f, 1.25f);
        }
    }
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player, "dimension");
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player, "logout");
    }
    public static void tickArrow(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Arrow arrow && !arrow.level().isClientSide && arrow.getTags().contains("orimod_spirit_arrow")) {
            if (arrow.tickCount >= 100) arrow.discard();
            else if (arrow.tickCount % 2 == 0 && arrow.getDeltaMovement().lengthSqr() > .01) {
                cn.wwl.orimod.fx.SpiritFx.emit((net.minecraft.server.level.ServerLevel)arrow.level(), cn.wwl.orimod.fx.SpiritFxKind.ARROW_TRAIL,
                        arrow.getX(), arrow.getY(), arrow.getZ(), arrow.getOwner());
            }
        }
    }
}
