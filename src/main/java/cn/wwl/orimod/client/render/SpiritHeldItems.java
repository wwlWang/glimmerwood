package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla item renderers and display transforms, attached to this frame's skinned palms. */
public final class SpiritHeldItems {
    private SpiritHeldItems() { }

    public static java.util.Map<String, Object> diagnostics(Player player) {
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        var socket = new Matrix4f();
        if (player.getMainHandItem().isEmpty() || !SpiritPlayerVisual.socket(player, left, socket)) return java.util.Map.of();
        var mc = Minecraft.getInstance();
        var rotation = socket.getUnnormalizedRotation(new Quaternionf());
        var model = mc.getItemRenderer().getModel(player.getMainHandItem(), player.level(), player, player.getId());
        var display = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var poses = new PoseStack();
        poses.mulPose(rotation);
        poses.mulPose(Axis.XP.rotationDegrees(SpiritItemGrip.attachmentPitch(SpiritPlayerVisual.cradleWeight(player, left), SpiritPlayerVisual.lowered(player, left))));
        poses.mulPose(Axis.YP.rotationDegrees(180));
        model.applyTransform(display, poses, left);
        var arrow = poses.last().pose().transformDirection(new Vector3f(-1, 1, 0)).normalize();
        return java.util.Map.of("rotation", new float[]{rotation.x, rotation.y, rotation.z, rotation.w},
                "arrowDirection", new float[]{arrow.x, arrow.y, arrow.z});
    }

    static void render(Player player, SpiritPlayerVisual.Frame frame, PoseStack base,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        boolean mainLeft = player.getMainArm() == HumanoidArm.LEFT;
        draw(player, frame, base, buffers, light, player.getMainHandItem(), mainLeft);
        draw(player, frame, base, buffers, light, player.getOffhandItem(), !mainLeft);
    }

    private static void draw(Player player, SpiritPlayerVisual.Frame frame, PoseStack poses,
                             net.minecraft.client.renderer.MultiBufferSource buffers, int light, ItemStack item, boolean left) {
        if (item.isEmpty()) return;
        Matrix4f socket = frame.boneMatrix(left ? "grip_r" : "grip_l", new Matrix4f());
        poses.pushPose();
        poses.translate(socket.m30(), socket.m31(), socket.m32());
        poses.mulPose(socket.getUnnormalizedRotation(new Quaternionf()));
        float lowered = frame.world() ? SpiritPlayerVisual.lowered(player, left) : SpiritItemGrip.lowered(item) ? 1 : 0;
        boolean blocking = player.isUsingItem() && item.getItem() instanceof net.minecraft.world.item.ShieldItem
                && player.getUsedItemHand() == (left == (player.getMainArm() == HumanoidArm.LEFT)
                        ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
        renderAtPalm(player, item, left, poses, .72f,
                frame.world() ? SpiritPlayerVisual.cradleWeight(player, left) : lowered, lowered, buffers, light,
                blocking ? socket.getUnnormalizedRotation(new Quaternionf()) : null);
        poses.popPose();
    }

    static void renderAtPalm(Player player, ItemStack item, boolean left, PoseStack poses, float scale, float cradleWeight, float lowered,
                             net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        renderAtPalm(player, item, left, poses, scale, cradleWeight, lowered, buffers, light, null);
    }

    private static void renderAtPalm(Player player, ItemStack item, boolean left, PoseStack poses, float scale, float cradleWeight, float lowered,
                             net.minecraft.client.renderer.MultiBufferSource buffers, int light, Quaternionf shieldPalm) {
        if (item.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var model = itemTransform(player, item, left, poses, scale, cradleWeight, lowered, shieldPalm);
        var display = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        SpiritWeaponRenderer.withOwner(player, () -> mc.getItemRenderer().render(item, display, left, poses, buffers, light, OverlayTexture.NO_OVERLAY, model));
    }

    /** The same centered item transform used to draw the rod, evaluated at its tip. */
    public static Vec3 fishingLineOrigin(Player player, float partialTick) {
        var frame = SpiritPlayerVisual.frame(player);
        if (frame == null) return null;
        boolean main = player.getMainHandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST);
        var item = main ? player.getMainHandItem() : player.getOffhandItem();
        if (!item.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST)) return null;
        boolean left = (player.getMainArm() == HumanoidArm.LEFT) == main;
        var socket = frame.boneMatrix(left ? "grip_r" : "grip_l", new Matrix4f());
        var poses = new PoseStack();
        poses.mulPose(frame.transform());
        poses.translate(socket.m30(), socket.m31(), socket.m32());
        poses.mulPose(socket.getUnnormalizedRotation(new Quaternionf()));
        var model = itemTransform(player, item, left, poses, .72f, SpiritPlayerVisual.cradleWeight(player, left), SpiritPlayerVisual.lowered(player, left), null);
        model.applyTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poses, left);
        var tip = poses.last().pose().transformPosition(new Vector3f(.40625f, .40625f, 0));
        return player.getPosition(partialTick).add(tip.x, tip.y, tip.z);
    }

    private static net.minecraft.client.resources.model.BakedModel itemTransform(Player player, ItemStack item, boolean left, PoseStack poses, float scale, float cradleWeight, float lowered, Quaternionf shieldPalm) {
        var mc = Minecraft.getInstance();
        var display = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var model = mc.getItemRenderer().getModel(item, player.level(), player, player.getId());
        var itemPose = new PoseStack();
        model.applyTransform(display, itemPose, left);
        if (shieldPalm != null) poses.mulPose(SpiritItemGrip.shieldRotation(shieldPalm, itemPose.last().pose().getUnnormalizedRotation(new Quaternionf())));
        else {
            // Existing weapon / first-person attachment calibration stays in vanilla arm space.
            poses.mulPose(Axis.XP.rotationDegrees(SpiritItemGrip.attachmentPitch(cradleWeight, lowered)));
            poses.mulPose(Axis.YP.rotationDegrees(180));
        }
        poses.scale(scale, scale, scale);
        var contact = new Vector3f();
        SpiritItemGrip.local(item, contact, new Vector3f());
        itemPose.last().pose().transformPosition(contact);
        poses.translate(-contact.x, -contact.y, -contact.z);
        return model;
    }
}
