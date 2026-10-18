package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla item renderers and display transforms, attached to this frame's skinned palms. */
public final class SpiritHeldItems {
    private static final Matrix4f SOCKET = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector3f POSITION = new Vector3f();
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
        poses.mulPose(Axis.XP.rotationDegrees(-90)); poses.mulPose(Axis.YP.rotationDegrees(180));
        model.applyTransform(display, poses, left);
        var arrow = poses.last().pose().transformDirection(new Vector3f(-1, 1, 0)).normalize();
        return java.util.Map.of("rotation", new float[]{rotation.x, rotation.y, rotation.z, rotation.w},
                "arrowDirection", new float[]{arrow.x, arrow.y, arrow.z});
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Player player : mc.level.players()) {
            if (!SpiritAppearance.visible(player) || player.isInvisible()
                    || player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            boolean mainLeft = player.getMainArm() == HumanoidArm.LEFT;
            draw(event, player, player.getMainHandItem(), mainLeft);
            draw(event, player, player.getOffhandItem(), !mainLeft);
        }
    }

    private static void draw(RenderLevelStageEvent event, Player player, ItemStack item, boolean left) {
        if (item.isEmpty() || !SpiritPlayerVisual.socket(player, left, SOCKET)) return;
        var mc = Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        SOCKET.getTranslation(POSITION);
        SOCKET.getUnnormalizedRotation(ROTATION);
        var poses = new PoseStack();
        poses.translate(POSITION.x - camera.x, POSITION.y - camera.y, POSITION.z - camera.z);
        poses.mulPose(ROTATION);
        renderAtPalm(player, item, left, poses, .72f * player.getScale(),
                mc.renderBuffers().bufferSource(), LevelRenderer.getLightColor(player.level(), player.blockPosition().above()));
        mc.renderBuffers().bufferSource().endBatch();
    }

    static void renderAtPalm(Player player, ItemStack item, boolean left, PoseStack poses, float scale,
                             net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (item.isEmpty()) return;
        var mc = Minecraft.getInstance();
        // Attachment axes match vanilla arm space; the origin is already at the palm.
        poses.mulPose(Axis.XP.rotationDegrees(-90));
        poses.mulPose(Axis.YP.rotationDegrees(180));
        poses.scale(scale, scale, scale);
        var display = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var model = mc.getItemRenderer().getModel(item, player.level(), player, player.getId());
        var itemPose = new PoseStack();
        model.applyTransform(display, itemPose, left);
        var contact = new Vector3f();
        SpiritItemGrip.local(item, contact, new Vector3f());
        itemPose.last().pose().transformPosition(contact);
        poses.translate(-contact.x, -contact.y, -contact.z);
        mc.getItemRenderer().render(item, display, left, poses, buffers, light, OverlayTexture.NO_OVERLAY, model);
    }
}
