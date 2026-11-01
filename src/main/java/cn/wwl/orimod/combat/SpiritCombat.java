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
    private static final int[] WINDUP = {0, 2, 3, 4}, RECOVERY = {0, 8, 8, 6};
    private static final float[] DAMAGE = {0, 3, 4, 5};
    private SpiritCombat() {}

    /** Stages start six ticks apart; their windups increase by one, so hit cadence is seven ticks. */
    public static final int CHAIN_TICKS = 6, BUFFER_FROM = 1;
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
        // Armor stands use a native two-click window of five ticks. The seven-tick
        // combo cadence cannot break one; preserve the actual Player.attack interaction.
        if (CombatTargets.aimed(player, player.entityInteractionRange()) instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
            cancel(player, "native_armor_stand");
            player.attack(stand); // AttackEntityEvent below still enforces allies/ownership.
            return;
        }
        var s = player.getData(SpiritData.COMBAT);
        long now = player.level().getGameTime();
        if (s.mode.equals("blade")) {
            long elapsed = now - s.startedAt;
            if (s.stage < 3 && elapsed >= BUFFER_FROM && elapsed < duration(s.stage)) s.buffered = true;
        } else if (s.mode.equals("idle")) startBlade(player, 1);
    }

    private static void startBlade(ServerPlayer player, int stage) {
        Flash.stop(player);
        var s = player.getData(SpiritData.COMBAT);
        if (stage == 1) s.comboHits.clear();
        s.mode = "blade"; s.stage = stage; s.startedAt = player.level().getGameTime();
        s.weapon = player.getMainHandItem(); s.buffered = false; s.hitTargets.clear(); s.hitBlocks.clear(); s.bladeStarts++;
        publish(player);
        player.swing(InteractionHand.MAIN_HAND, true);
        step(player);
    }

    /** Ground displacement belongs to the local movement curve; the server only grants bounded air holds. */
    private static void step(ServerPlayer player) {
        var s = player.getData(SpiritData.COMBAT);
        if (player.onGround() || s.airHolds >= BladeArc.AIR_HOLDS) return;
        s.airHolds++;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.max(motion.y, BladeArc.AIR_HOLD_FALL), motion.z);
        player.hurtMarked = true;
    }

    public static void attackEntity(AttackEntityEvent event) {
        var stack = event.getEntity().getMainHandItem();
        if ((stack.is(OriItems.SPIRIT_BLADE) || stack.is(OriItems.SPIRIT_SPEAR) || stack.is(OriItems.SPIRIT_HAMMER)
                || stack.is(OriItems.SPIRIT_BOW)) && event.getTarget() instanceof LivingEntity target
                && !CombatTargets.canHitDirect(event.getEntity(), target)) { event.setCanceled(true); return; }
        if (!event.getEntity().getMainHandItem().is(OriItems.SPIRIT_BLADE)) return;
        if (event.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) attack(player);
    }

    public static boolean canUseBow(Player player) {
        return available(player) && Ability.BOW.unlocked(player) && SpiritProgression.energyForUse(player) >= BOW_COST
                && !movementOwnsAction(player) && !player.getCooldowns().isOnCooldown(OriItems.SPIRIT_BOW.get());
    }

    public static boolean beginBow(ServerPlayer player, ItemStack stack) {
        var s = player.getData(SpiritData.COMBAT);
        if (!canUseBow(player) || player.level().getGameTime() < s.nextBowAt || !s.mode.equals("idle")) return false;
        Flash.stop(player);
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
        // Splinter replaces the hidden sneaking trigger; partial/low-energy shots retain the single arrow.
        boolean volley = ArcVolley.applies(charge, cn.wwl.orimod.progression.Shard.SPLINTER.equipped(player), SpiritProgression.energyForUse(player));
        var directions = volley ? ArcVolley.directions(player.getLookAngle()) : java.util.List.of(player.getLookAngle());
        int fired = 0;
        for (Vec3 direction : directions) {
            var arrow = new Arrow(player.level(), player, new ItemStack(Items.ARROW), stack.copy());
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setBaseDamage(2.5);
            arrow.setCritArrow(false); // Keep damage deterministic: no random full-charge critical bonus.
            // Exclude shooter momentum so the reticle and the shot have the same direction during movement.
            arrow.shoot(direction.x, direction.y, direction.z, 1.2f + 1.8f * power, 0);
            arrow.addTag("orimod_spirit_arrow");
            if (volley) arrow.addTag("orimod_arc_volley");
            if (player.serverLevel().addFreshEntity(arrow)) fired++;
        }
        if (fired == 0) return;
        SpiritProgression.spendEnergy(player, volley ? ArcVolley.COST : BOW_COST);
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
        SpearCharge.cancel(player);
        var s = player.getData(SpiritData.COMBAT);
        if (s.mode.equals("idle")) return;
        s.mode = "idle"; s.stage = 0; s.buffered = false; s.weapon = ItemStack.EMPTY; s.hitTargets.clear(); s.hitBlocks.clear(); s.comboHits.clear();
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
        SpearCharge.tick(player);
        var s = player.getData(SpiritData.COMBAT);
        if (s.mode.equals("idle")) { if (player.onGround()) s.airHolds = 0; return; }
        if (player.onGround()) s.airHolds = 0;
        if (s.mode.equals(HammerSlam.MODE)) { HammerSlam.tick(player); return; }
        if (!available(player) || movementOwnsAction(player)) { cancel(player, "unavailable"); return; }
        if (s.mode.equals("bow")) {
            if (!player.isUsingItem() || player.getUseItem() != s.weapon) cancel(player, "item");
            return;
        }
        if (player.getMainHandItem() != s.weapon || !s.weapon.is(OriItems.SPIRIT_BLADE)) { cancel(player, "item"); return; }
        long elapsed = player.level().getGameTime() - s.startedAt;
        if (s.buffered && s.stage < 3 && elapsed >= CHAIN_TICKS) { startBlade(player, s.stage + 1); return; }
        if (elapsed >= duration(s.stage)) {
            if (s.buffered && s.stage < 3) startBlade(player, s.stage + 1);
            else { s.mode = "idle"; s.stage = 0; s.weapon = ItemStack.EMPTY; s.comboHits.clear(); publish(player); }
            return;
        }
        if (elapsed >= windup(s.stage) && elapsed < recoveryStart(s.stage)) hitBlade(player, s);
    }

    private static void hitBlade(ServerPlayer player, CombatState s) {
        // The fan is centred on the chest and follows the body's facing, not the camera pitch.
        Vec3 origin = player.position().add(0, player.getBbHeight() * .55, 0);
        float yaw = player.getYRot();
        double reach = BladeArc.reach(s.stage);
        cn.wwl.orimod.block.BlightInteractions.melee(player,reach,s.hitBlocks);
        var primary = CombatTargets.aimed(player, reach);
        for (var target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach, BladeArc.VERTICAL, reach),
                entity -> entity == primary ? CombatTargets.canHitDirect(player, entity) : CombatTargets.canHit(player, entity))) {
            Vec3 closest = BladeArc.closest(origin, target.getBoundingBox());
            if (!BladeArc.contains(origin, yaw, closest, s.stage)
                    || !SpiritTargets.visible(player, closest) || s.hitTargets.contains(target.getUUID())) continue;
            s.hitTargets.add(target.getUUID()); // A failed/invulnerable hit cannot retry within this swing.
            if (BladeDamage.hurt(player, target, s, DAMAGE[s.stage])) {
                s.bladeHits++;
                Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1e-4) away = BladeArc.facing(yaw);
                away = away.normalize();
                target.knockback(BladeArc.knockback(s.stage), -away.x, -away.z);
                if (BladeArc.overhead(s.stage) && target.onGround())
                    target.setDeltaMovement(target.getDeltaMovement().add(0, BladeArc.FINISHER_LIFT, 0));
                cn.wwl.orimod.fx.SpiritFx.emit(player.serverLevel(), cn.wwl.orimod.fx.SpiritFxKind.BLADE_HIT, closest.x, closest.y, closest.z, player, 1 + s.stage * .1f);
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

    public static void projectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getProjectile().getTags().contains("orimod_spirit_arrow")
                && event.getProjectile().getOwner() instanceof Player owner
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target && !CombatTargets.canHitDirect(owner, target))
            event.setCanceled(true);
    }
}
