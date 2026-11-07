package cn.wwl.orimod.client.render;

import cn.wwl.orimod.combat.HammerSlam;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Vanilla owns equip and view bob; {@link SpiritFirstPersonHand} owns the weapon swing / draw / charge choreography.
 * This adds the spirit hammer's airborne plunge on top, at renderItem's head. */
public final class SpiritFirstPerson {
    private SpiritFirstPerson() { }
    private static LivingEntity owner;
    private static boolean falling;
    private static float startedAt, endedAt = -100;

    static void slam(LivingEntity entity, ItemStack item, ItemDisplayContext context, PoseStack pose) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)
                || !SpiritFirstPersonGrip.mainHandView(context, player.getMainArm())) return;
        if (owner != player) { owner = player; falling = false; endedAt = -100; }
        if (!item.is(OriItems.SPIRIT_HAMMER)) { falling = false; endedAt = -100; return; }
        float now = player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        boolean active = HammerSlam.falling(player.getData(SpiritData.COMBAT_VIEW), player.level().getGameTime());
        if (active && !falling) startedAt = now;
        if (active) endedAt = now;
        falling = active;
        float weight = SpiritFirstPersonMotion.plungeWeight(active, now-startedAt, now-endedAt);
        // Local to vanilla's held arm: no lateral translation or camera-space framing.
        pose.mulPose(SpiritFirstPersonMotion.plunge(weight));
    }

    public static java.util.Map<String,Object> diagnostics() {
        return java.util.Map.of("source", "vanilla", "hammerPlunge", falling);
    }
}
