package cn.wwl.orimod.item;

import cn.wwl.orimod.combat.CombatTargets;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * R49 Spirit Smash (after WotW's hammer): hold use to wind up a heavy blow, release to smash the ground ahead.
 * Slow but strong; enemies caught in the air are slammed down, and a smash from the air drives Ori down with it.
 */
public final class SpiritHammerItem extends Item {
    public static final int WIND_UP_TICKS = 12, COOLDOWN_TICKS = 30;
    public static final float DAMAGE = 10, COST = 8;
    public static final double REACH = 2.2, RADIUS = 2.2;

    public SpiritHammerItem(Properties properties) { super(properties); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.SPEAR; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        String refusal = refusal(SpiritData.isSpirit(player), cn.wwl.orimod.progression.Ability.SMASH.unlocked(player));
        if (refusal != null) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(refusal), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player)) return;
        int held = getUseDuration(stack, entity) - timeLeft;
        if (held < WIND_UP_TICKS || refusal(SpiritData.isSpirit(player), cn.wwl.orimod.progression.Ability.SMASH.unlocked(player)) != null) return;
        if (!SpiritProgression.spendEnergy(player, COST)) {
            player.displayClientMessage(Component.translatable("message.orimod.hammer_energy"), true); return;
        }
        smash(player);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
    }

    /** Pure rule: why the hammer will not wind up, or null. */
    public static String refusal(boolean spirit, boolean unlocked) {
        return !spirit ? "message.orimod.hammer_spirit" : !unlocked ? "message.orimod.hammer_locked" : null;
    }

    /** Where the blow lands: {@link #REACH} blocks ahead along the horizontal look direction, at the feet. */
    public static Vec3 impactPoint(Vec3 feet, float yaw) {
        double r = Math.toRadians(yaw);
        return feet.add(-Math.sin(r) * REACH, 0, Math.cos(r) * REACH);
    }

    /** Resolve one smash. @return the number of enemies hit. */
    public static int smash(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        Vec3 at = impactPoint(player.position(), player.getYRot());
        boolean airborne = !player.onGround();
        int hits = 0;
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS, 1.5, RADIUS).expandTowards(0, 1, 0),
                e -> e != player && CombatTargets.canHit(player, e))) {
            boolean wasAirborne = !target.onGround();
            if (!target.hurt(player.damageSources().playerAttack(player), DAMAGE)) continue;
            hits++;
            Vec3 push = target.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(.9);
            if (wasAirborne) target.setDeltaMovement(push.x * .3, -1.4, push.z * .3);   // knock flyers out of the air
            else target.setDeltaMovement(push.x, .45, push.z);
            target.hurtMarked = true;
        }
        if (airborne) { player.setDeltaMovement(player.getDeltaMovement().multiply(.3, 0, .3).add(0, -1.2, 0)); player.hurtMarked = true; }
        cn.wwl.orimod.fx.SpiritFx.emit(level, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, at.x, at.y, at.z, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1, .9f);
        return hits;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.orimod.hammer").withStyle(net.minecraft.ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.orimod.spirit_required").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
