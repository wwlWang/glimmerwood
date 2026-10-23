package cn.wwl.orimod.client.render;

import cn.wwl.orimod.client.render.SpiritFirstPersonMotion.Pose;
import cn.wwl.orimod.combat.HammerSlam;
import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.item.SpiritHammerItem;
import cn.wwl.orimod.network.AnimationActionPayload;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * R114: Spirit form's first-person weapon motion, through NeoForge's {@code applyForgeHandTransform}. Returning true
 * replaces vanilla's equip, swing and use transforms for that hand; the paw then follows the item (SpiritHands).
 * Only the main hand of a Spirit-form player is taken over; human form and off-hand items keep vanilla motion.
 */
public final class SpiritFirstPerson {
    private SpiritFirstPerson() { }

    private static LocalPlayer owner;
    private static net.minecraft.world.item.Item lastItem;
    private static Pose shown = Pose.REST, bladeFrom = Pose.REST;
    private static int bladeAction = -1, bladeStage;
    private static long bladeStartedAt = Long.MIN_VALUE;
    private static boolean wasHammer, wasSlam, wasBow, releasedOnGround, landedOnGround;
    private static float hammerCharge, releasedCharge, hammerReleasedAt = -100, slamStartedAt = -100, slamEndedAt = -100,
            bowStartedAt, bowReleasedAt = -100;

    public static boolean apply(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                float equip, float swing) {
        // The renderer may still hold the previous stack instance while re-equipping: compare by item.
        if (!SpiritData.isSpirit(player) || arm != player.getMainArm() || !player.getMainHandItem().is(stack.getItem())) return false;
        if (owner != player) reset(player);
        // Edge flags belong to the item last drawn: switching away mid-draw must not replay a release on return.
        if (stack.getItem() != lastItem) { lastItem = stack.getItem(); wasHammer = wasSlam = wasBow = false; }
        float now = player.tickCount + partial;
        boolean left = arm == HumanoidArm.LEFT;
        int side = left ? -1 : 1;
        // Vanilla's private applyItemArmTransform: the resting arm position, lowered while re-equipping.
        if (stack.is(OriItems.SPIRIT_BOW)) {
            pose.translate(side * .56f, -.52f + equip * -.6f, -.72f);
            bowPose(pose, bow(player, stack, now), side, now);
            shown = Pose.REST;
            return true;
        }
        var target = stack.is(OriItems.SPIRIT_BLADE) ? blade(player, partial, swing)
                : stack.is(OriItems.SPIRIT_HAMMER) ? hammer(player, stack, now)
                : stack.is(OriItems.SPIRIT_SPEAR) ? spear(player, stack, now, partial) : null;
        if (target == null) return false;
        shown = target;
        var sided = target.side(left);
        pose.translate(side * .56f, -.52f + equip * -.6f, -.72f);
        pose.translate(sided.x(), sided.y(), sided.z());
        pose.mulPose(Axis.YP.rotationDegrees(sided.yaw()));
        pose.mulPose(Axis.ZP.rotationDegrees(sided.roll()));
        pose.mulPose(Axis.XP.rotationDegrees(sided.pitch()));
        return true;
    }

    private static void reset(LocalPlayer player) {
        owner = player; shown = bladeFrom = Pose.REST; bladeAction = -1; bladeStartedAt = Long.MIN_VALUE;
        wasHammer = wasSlam = wasBow = false; hammerReleasedAt = slamStartedAt = slamEndedAt = bowReleasedAt = -100;
    }

    private static boolean usingMain(LocalPlayer player, ItemStack stack) {
        return player.isUsingItem() && player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && player.getUseItem().is(stack.getItem());
    }

    private static Pose blade(LocalPlayer player, float partial, float swing) {
        var view = player.getData(SpiritData.COMBAT_VIEW);
        if (view.mode().equals("blade") && view.actionId() != bladeAction) {
            // Each combo stage winds up from wherever the previous one left the blade.
            bladeAction = view.actionId(); bladeStage = view.stage(); bladeStartedAt = view.startedAt(); bladeFrom = shown;
        }
        // Keep playing the stage to its end even after the combat view returns to idle.
        float elapsed = player.level().getGameTime() + partial - bladeStartedAt;
        if (bladeStartedAt != Long.MIN_VALUE && elapsed >= 0 && elapsed < SpiritCombat.duration(bladeStage))
            return stage(bladeStage, elapsed, bladeFrom);
        // A plain attack swing (no combo, e.g. not yet awakened) uses the first cut on vanilla's swing clock.
        return swing > 0 ? stage(1, swing * SpiritCombat.duration(1), Pose.REST) : Pose.REST;
    }

    private static Pose stage(int stage, float elapsed, Pose from) {
        int windup = SpiritCombat.windup(stage), hit = SpiritCombat.recoveryStart(stage) - windup;
        return SpiritFirstPersonMotion.blade(stage, elapsed, windup, hit, SpiritCombat.duration(stage) - windup - hit, from);
    }

    private static Pose hammer(LocalPlayer player, ItemStack stack, float now) {
        boolean using = usingMain(player, stack);
        if (using) hammerCharge = Math.min(1, player.getTicksUsingItem() / (float) SpiritHammerItem.WIND_UP_TICKS);
        if (wasHammer && !using) { hammerReleasedAt = now; releasedCharge = hammerCharge; releasedOnGround = player.onGround(); }
        wasHammer = using;
        boolean slam = HammerSlam.falling(player.getData(SpiritData.COMBAT_VIEW), player.level().getGameTime());
        if (slam && !wasSlam) slamStartedAt = now;
        if (!slam && wasSlam) { slamEndedAt = now; landedOnGround = player.onGround(); }
        wasSlam = slam;
        if (using) return SpiritFirstPersonMotion.hammerCharge(hammerCharge, now / 20);
        if (slam) return SpiritFirstPersonMotion.hammerPlunge(Math.max(now - slamStartedAt, now - hammerReleasedAt));
        float strike = SpiritFirstPersonMotion.HAMMER_STRIKE_TICKS + SpiritFirstPersonMotion.HAMMER_RECOVER_TICKS;
        if (now - slamEndedAt < strike)
            return landedOnGround ? SpiritFirstPersonMotion.hammerStrike(now - slamEndedAt, true)
                    : SpiritFirstPersonMotion.hammerLower(now - slamEndedAt, 1);
        float since = now - hammerReleasedAt;
        if (releasedCharge >= 1 && releasedOnGround && since < strike) return SpiritFirstPersonMotion.hammerStrike(since, false);
        // A charged release in the air waits briefly for the server's slam to begin before giving up.
        if (releasedCharge >= 1 && !releasedOnGround && since < 6) return SpiritFirstPersonMotion.hammerPlunge(since);
        if (since < 11) return SpiritFirstPersonMotion.hammerLower(Math.max(0, since - (releasedCharge >= 1 ? 6 : 0)), releasedCharge);
        return Pose.REST;
    }

    private static Pose spear(LocalPlayer player, ItemStack stack, float now, float partial) {
        var observed = SpiritAnimationEvents.of(player);
        float cooldown = player.getCooldowns().getCooldownPercent(stack.getItem(), partial);
        if (observed.action == AnimationActionPayload.Action.SPEAR_THROW) {
            float elapsed = now - observed.actionAt * 20;
            float settle = SpiritFirstPersonMotion.SPEAR_COIL_TICKS + SpiritFirstPersonMotion.SPEAR_THRUST_TICKS + 6;
            if (elapsed >= 0 && (elapsed < settle || cooldown > 0)) return SpiritFirstPersonMotion.spearThrow(elapsed, cooldown);
        }
        return SpiritFirstPersonMotion.spearCarry();
    }

    private static SpiritFirstPersonMotion.BowPose bow(LocalPlayer player, ItemStack stack, float now) {
        boolean using = usingMain(player, stack);
        if (using && !wasBow) bowStartedAt = now;
        if (!using && wasBow) bowReleasedAt = now;
        wasBow = using;
        if (using) return SpiritFirstPersonMotion.bowDraw(now - bowStartedAt);
        if (now - bowReleasedAt < SpiritFirstPersonMotion.BOW_RECOIL_TICKS) return SpiritFirstPersonMotion.bowRelease(now - bowReleasedAt);
        return new SpiritFirstPersonMotion.BowPose(0, 0, 0);
    }

    /** ItemInHandRenderer's BOW case, weighted by the raise; the z-stretch is left out so the paws keep their shape. */
    private static void bowPose(PoseStack pose, SpiritFirstPersonMotion.BowPose bow, int side, float now) {
        float w = bow.raise(), f = bow.pull();
        pose.translate(side * -.2785682f * w, .18344387f * w + .04f * bow.kick(), .15731531f * w - .16f * bow.kick());
        pose.mulPose(Axis.XP.rotationDegrees(-13.935f * w));
        pose.mulPose(Axis.YP.rotationDegrees(side * 35.3f * w));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -9.785f * w));
        if (f > .1f) pose.translate(0, (float) Math.sin((now - bowStartedAt - .1f) * 1.3f) * (f - .1f) * .004f, 0);
        pose.translate(0, 0, f * .04f);
        pose.mulPose(Axis.YN.rotationDegrees(side * 45 * w));
    }
}
