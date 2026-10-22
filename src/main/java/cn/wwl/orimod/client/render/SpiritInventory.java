package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.platform.Lighting;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.gltf.*;
import com.wf.gemrender.gltf.blend.*;
import com.wf.gemrender.texture.Paint;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import cn.wwl.orimod.mixin.SpiritRenderLayersAccessor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The inventory uses a separate GUI draw; Flywheel's world visual does not run here. */
public final class SpiritInventory {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "models/entity/ori.glb");
    private static final GltfPose.Scratch SCRATCH = new GltfPose.Scratch();
    private static final AnimationBlend BLEND = new AnimationBlend();
    private static GemRenderGltfModel loaded;
    private static BlendMask leftArm, rightArm, bothArms, head;
    private static AdditiveReference rest;
    private static GltfAnimation lookYaw, lookPitch;
    private static final SpiritPoseLayer CARRY_RIGHT = new SpiritPoseLayer("ori_inventory_carry_right"),
            CARRY_LEFT = new SpiritPoseLayer("ori_inventory_carry_left");
    private static BlendMask carryRightMask, carryLeftMask;
    private SpiritInventory() { }

    public static boolean render(GuiGraphics graphics, float x, float y, float scale, Vector3f translate,
                                 Quaternionf rotation, LivingEntity entity) {
        if (!(entity instanceof Player player) || !SpiritData.isSpirit(player)) return false;
        var model = GemRenderModels.handle(MODEL).get();
        if (model == null) return false;
        var table = model.layout().nodeTable();
        Matrix4f[] palette = SCRATCH.palette(model.jointCount());
        if (model != loaded) {
            loaded = model;
            leftArm = BlendMask.subtree(table, "upper_arm_r"); rightArm = BlendMask.subtree(table, "upper_arm_l");
            bothArms = BlendMask.subtree(table, "upper_arm_l", "upper_arm_r"); head = BlendMask.nodes(table, "head");
            rest = AdditiveReference.rest(table);
            CARRY_RIGHT.bind(table); CARRY_LEFT.bind(table);
            SpiritPoses.lowered(CARRY_RIGHT, 0, 0, 1); CARRY_RIGHT.publish();
            SpiritPoses.lowered(CARRY_LEFT, 1, 0, 1); CARRY_LEFT.publish();
            carryRightMask = CARRY_RIGHT.mask(SpiritPoseLayer.UPPER_ARM_L, SpiritPoseLayer.FOREARM_L, SpiritPoseLayer.HAND_L);
            carryLeftMask = CARRY_LEFT.mask(SpiritPoseLayer.UPPER_ARM_R, SpiritPoseLayer.FOREARM_R, SpiritPoseLayer.HAND_R);
            GltfPose.evaluate(model.layout(), (GltfAnimation)null, 0, palette);
            lookYaw = GltfAnimation.procedural("ori_inventory_yaw", SpiritAnimation.angle(table, palette, "head", new Vector3f(0,1,0), 1));
            lookPitch = GltfAnimation.procedural("ori_inventory_pitch", SpiritAnimation.angle(table, palette, "head", new Vector3f(1,0,0), 1));
        }
        boolean mainLeft = player.getMainArm() == HumanoidArm.LEFT;
        ItemStack leftItem = mainLeft ? player.getMainHandItem() : player.getOffhandItem();
        ItemStack rightItem = mainLeft ? player.getOffhandItem() : player.getMainHandItem();
        BlendMask held = !leftItem.isEmpty() && !rightItem.isEmpty() ? bothArms : !leftItem.isEmpty() ? leftArm : !rightItem.isEmpty() ? rightArm : null;
        float now = (player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) / 20f;
        var idle = SpiritClips.get(model, "idle");
        var holdLeft = SpiritClips.get(model, "hold_left");
        var holdRight = SpiritClips.get(model, "hold");
        BLEND.clear().override(idle, idle.loop(now), 1,
                held == null ? BlendMask.ALL : held.complement(table));
        if (!leftItem.isEmpty()) BLEND.override(holdLeft, holdLeft.loop(now), 1, leftArm);
        if (!rightItem.isEmpty()) BLEND.override(holdRight, holdRight.loop(now), 1, rightArm);
        if (SpiritItemGrip.lowered(leftItem)) BLEND.override(CARRY_LEFT.clip(), now, 1, carryLeftMask);
        if (SpiritItemGrip.lowered(rightItem)) BLEND.override(CARRY_RIGHT.clip(), now, 1, carryRightMask);
        BLEND.additive(lookYaw, -Mth.wrapDegrees(player.yHeadRot - player.yBodyRot) * Mth.DEG_TO_RAD, 1, head, rest);
        BLEND.additive(lookPitch, player.getXRot() * Mth.DEG_TO_RAD, 1, head, rest);
        GltfPose.evaluate(model.layout(), BLEND, palette, model.morphs(), null, SCRATCH);
        graphics.flush();
        graphics.pose().pushPose();
        try {
            float fit = 1.45f;
            graphics.pose().translate(x, y, 50);
            graphics.pose().scale(scale, scale, -scale);
            graphics.pose().translate(translate.x, translate.y + (fit - 1) * player.getBbHeight() / 2, translate.z);
            graphics.pose().mulPose(rotation);
            var pose = new Matrix4f(graphics.pose().last().pose())
                    .rotateY(-player.yBodyRot * Mth.DEG_TO_RAD).scale(fit * player.getScale());
            Lighting.setupForEntityInInventory();
            DirectRenderer.submit(model, palette, null, pose, 15728880, OverlayTexture.NO_OVERLAY, -1,
                    DirectPass.GUI, VariantUv.NONE, Paint.NONE, 0, palette);
            DirectRenderer.flush(DirectPass.GUI);
            var frame = new SpiritPlayerVisual.Frame(model, (slot, out) -> out.set(palette[slot]), new Matrix4f(), false);
            SpiritInventoryGlow.render(frame, pose, graphics.bufferSource());
            if (player instanceof AbstractClientPlayer clientPlayer
                    && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(clientPlayer) instanceof PlayerRenderer renderer) {
                var poses = new com.mojang.blaze3d.vertex.PoseStack(); poses.mulPose(pose);
                var parent = renderer.getModel();
                parent.setAllVisible(true); parent.young = false; parent.riding = false;
                parent.prepareMobModel(clientPlayer, 0, 0, 0);
                parent.setupAnim(clientPlayer, 0, 0, now * 20, 0, 0);
                for (var layer : ((SpiritRenderLayersAccessor)renderer).orimod$layers()) {
                    SpiritVanillaLayers.render(layer, parent, player, frame, poses, graphics.bufferSource(), 15728880,
                            0, 0, 0, now * 20, 0, 0);
                }
            }
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            Lighting.setupFor3DItems();
        }
        return true;
    }

}
