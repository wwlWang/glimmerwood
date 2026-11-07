package cn.wwl.orimod.client.render;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import cn.wwl.orimod.client.render.SpiritFirstPersonMotion.Pose;

/**
 * NeoForge's {@code IClientItemExtensions#applyForgeHandTransform} for the spirit weapons in first person. It replaces
 * only the per-item part of {@code ItemInHandRenderer#renderArmWithItem}: the native arm placement, equip drop and the
 * outer view bob are unchanged; the swing / draw / charge choreography comes from {@link SpiritFirstPersonMotion}.
 * Returns false (vanilla path) for anything that is not a spirit weapon held by Ori.
 */
public final class SpiritFirstPersonHand {
    private SpiritFirstPersonHand() { }

    /** Per-arm memory so every action starts from the pose actually on screen. */
    static final class Channel {
        ItemStack item = ItemStack.EMPTY;
        final SpiritFirstPersonMotion.Memory pose = new SpiritFirstPersonMotion.Memory();
        boolean using;
        float lastUsed, lastPull, lastRaise, releasedAt = -100;
        final SpiritAttackPose.Timeline timeline = new SpiritAttackPose.Timeline();
        void reset(ItemStack stack) {
            item = stack; pose.reset(); using = false; releasedAt = -100; lastRaise = 0; lastUsed = 0; lastPull = 0;
            timeline.end();
        }
    }
    private static final Channel RIGHT = new Channel(), LEFT = new Channel();
    private static LocalPlayer owner;

    static boolean handles(LocalPlayer player, ItemStack stack) {
        return player != null && SpiritData.isSpirit(player) && !player.isInvisible()
                && (stack.is(OriItems.SPIRIT_BLADE) || stack.is(OriItems.SPIRIT_HAMMER)
                || stack.is(OriItems.SPIRIT_SPEAR) || stack.is(OriItems.SPIRIT_BOW));
    }

    public static boolean apply(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                float equip, float swing) {
        if (!handles(player, stack)) return false;
        if (owner != player) { owner = player; RIGHT.reset(ItemStack.EMPTY); LEFT.reset(ItemStack.EMPTY); }
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Channel ch = arm == HumanoidArm.RIGHT ? RIGHT : LEFT;
        if (!ItemStack.isSameItem(ch.item, stack)) ch.reset(stack);
        ch.item = stack;
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        float now = player.tickCount + partial;
        boolean using = player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand;
        float used = using ? Math.max(0, stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partial + 1)) : 0;
        if (ch.using && !using) ch.releasedAt = now;
        ch.using = using;
        if (using) ch.lastUsed = used;

        var combat = player.getData(SpiritData.COMBAT_VIEW);
        boolean bladeCombo = hand == InteractionHand.MAIN_HAND && stack.is(OriItems.SPIRIT_BLADE) && combat.mode().equals("blade");
        // Sprint bob sways the arm sideways; a screen-space tuck (pure translation, before any arm rotation) keeps tips in.
        var tuck = SpiritFirstPersonMotion.sprintTuck(net.minecraft.util.Mth.lerp(partial, player.oBob, player.bob));
        pose.translate(side * tuck.dx(), tuck.dy(), tuck.dz());
        int pendingSince = !bladeCombo && hand == InteractionHand.MAIN_HAND && stack.is(OriItems.SPIRIT_BLADE)
                ? cn.wwl.orimod.client.SpiritCombatClient.comboPendingSince() : -1;
        // Mining / plain swings with the blade or bow keep vanilla's full swing; only the eased use is ours there.
        if (!bladeCombo && pendingSince < 0 && !using && swing > 0 && (stack.is(OriItems.SPIRIT_BLADE) || stack.is(OriItems.SPIRIT_BOW))) {
            ch.timeline.end();
            pose.mulPose(SpiritFirstPersonMotion.vanillaSwing(side, equip, swing));
            ch.pose.reset();
            return true;
        }

        pose.mulPose(SpiritFirstPersonMotion.arm(side, equip));
        var memory = ch.pose;
        Pose local;
        if (bladeCombo) {
            long key = combat.startedAt() * 4 + combat.stage();
            float elapsed = ch.timeline.elapsed(key, now, player.level().getGameTime() - combat.startedAt() + partial);
            local = SpiritFirstPersonMotion.blade(combat.stage(), elapsed, memory.begin("blade", key, now));
        } else if (pendingSince >= 0) {
            ch.timeline.end();
            local = SpiritFirstPersonMotion.bladePending(Math.max(0, now - pendingSince), memory.begin("pending", pendingSince, now));
        } else {
            ch.timeline.end();
            if (stack.is(OriItems.SPIRIT_HAMMER) && using) {
                local = SpiritFirstPersonMotion.hammerWindUp(used, memory.begin("wind", 0, now));
            } else if (stack.is(OriItems.SPIRIT_HAMMER) && swing > 0) {
                local = SpiritFirstPersonMotion.hammerStrike(swing, memory.begin("strike", 0, now));
            } else if (stack.is(OriItems.SPIRIT_HAMMER) && memory.action.equals("wind")
                    && ch.lastUsed >= cn.wwl.orimod.item.SpiritHammerItem.WIND_UP_TICKS && now - ch.releasedAt < SpiritFirstPersonMotion.STRIKE_WAIT_TICKS) {
                // The release swing arrives from the server a few ticks after the button is let go: hold the cocked pose.
                local = memory.shown;
            } else if (stack.is(OriItems.SPIRIT_SPEAR) && swing > 0) {
                local = SpiritFirstPersonMotion.spearThrust(swing, memory.begin("thrust", 0, now));
            } else if (stack.is(OriItems.SPIRIT_SPEAR) && !using && ch.lastUsed >= SpiritFirstPersonMotion.SPEAR_MIN_RAISE
                    && now - ch.releasedAt < SpiritFirstPersonMotion.LOWER_TICKS) {
                // Throw follow-through: the raised arm lowers while the spear hand drives forward.
                local = SpiritFirstPersonMotion.spearThrust((now - ch.releasedAt) / SpiritFirstPersonMotion.LOWER_TICKS,
                        memory.begin("throw", 0, now));
            } else {
                local = memory.settle(now);
            }
        }
        memory.show(local);

        // Bow / spear use: vanilla's use transform, eased in on raise and out on release.
        boolean bow = stack.is(OriItems.SPIRIT_BOW), spear = stack.is(OriItems.SPIRIT_SPEAR);
        if ((bow || spear) && (using || now - ch.releasedAt < SpiritFirstPersonMotion.LOWER_TICKS)) {
            float weight, pull;
            if (using) {
                weight = SpiritFirstPersonMotion.raiseWeight(used);
                pull = bow ? SpiritFirstPersonMotion.bowPull(used) : SpiritFirstPersonMotion.spearPull(used);
                ch.lastUsed = used; ch.lastPull = pull; ch.lastRaise = weight;
            } else {
                weight = ch.lastRaise * SpiritFirstPersonMotion.lowerWeight(now - ch.releasedAt);
                pull = ch.lastPull * weight;
            }
            pose.mulPose(bow ? SpiritFirstPersonMotion.bowUse(side, ch.lastUsed, pull, weight)
                    : SpiritFirstPersonMotion.spearUse(side, ch.lastUsed, pull, weight));
        }
        pose.mulPose(local.matrix(side));
        return true;
    }

    public static java.util.Map<String, Object> diagnostics() {
        return java.util.Map.of("right", RIGHT.pose.action, "left", LEFT.pose.action);
    }
}
