package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.gltf.GltfMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.neoforged.neoforge.client.event.RenderArmEvent;

/** Keeps vanilla equip, attack, eating, bow and map transforms around the spirit paws. */
public final class SpiritHands {
    private SpiritHands() { }

    public static boolean held(LivingEntity entity, ItemStack item, ItemDisplayContext context) {
        return !item.isEmpty() && entity instanceof Player player && SpiritData.isSpirit(player) && !player.isInvisible()
                && (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
    }

    /** Move item and palm together, in view space, preserving the handle contact under equip/use rotations. */
    public static void beginHeld(PoseStack pose, boolean left) {
        pose.pushPose();
        var offset = SpiritHandSpace.offset(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),
                new Vector3f(left ? .10f : -.10f, .22f, -.20f));
        pose.last().pose().translateLocal(offset.x, offset.y, offset.z);
    }

    public static void emptyArm(RenderArmEvent event) {
        if (!SpiritData.isSpirit(event.getPlayer()) || event.getPlayer().isInvisible()) return;
        boolean left = event.getArm() == HumanoidArm.LEFT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(left ? .3125f : -.3125f, .75f, 0); pose.scale(2,2,2);
        if (draw(left, false, pose, event.getMultiBufferSource(), event.getPackedLight())) event.setCanceled(true);
        pose.popPose();
    }

    public static void heldItem(LivingEntity entity, ItemStack item, ItemDisplayContext context, PoseStack stack, MultiBufferSource buffers, int light) {
        if (!(entity instanceof Player player) || !SpiritData.isSpirit(player) || player.isInvisible()) return;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        // Locate the handle after the item's own display transform. This keeps
        // resource-pack translations and vanilla use/equip motion in agreement.
        var model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, player.getId() + context.ordinal());
        var itemPose = new PoseStack();
        model.applyTransform(context, itemPose, left);
        var point = new Vector3f();
        SpiritItemGrip.local(item, point, new Vector3f());
        itemPose.last().pose().transformPosition(point);
        var palm = palm(model, itemPose, left, SpiritItemGrip.lowered(item));
        stack.pushPose();
        stack.translate(point.x, point.y, point.z);
        stack.mulPose(palm);
        stack.scale(PAW_SCALE, PAW_SCALE, PAW_SCALE);
        draw(left, true, stack, buffers, light);
        stack.popPose();
    }

    static final float PAW_SCALE = 1.25f;

    /**
     * The paw's orientation for an item drawn with {@code itemPose}. The paw meshes are baked in the grip bone's frame,
     * and third person attaches items to that bone as pitch * Y180 * display. Inverting that relation gives one fixed
     * hand-to-item grip in every view: the handle runs through the fist and the fingers wrap the same side. The old
     * single-axis alignment left the roll free (fingers outward) and flipped near the antipode during eating motion.
     */
    static Quaternionf palm(net.minecraft.client.resources.model.BakedModel model, PoseStack itemPose, boolean left, boolean lowered) {
        var held = new PoseStack();
        held.mulPose(com.mojang.math.Axis.XP.rotationDegrees(SpiritItemGrip.attachmentPitch(1, lowered ? 1 : 0)));
        held.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        model.applyTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, held, left);
        return SpiritItemGrip.palmFromItem(itemPose.last().pose().getNormalizedRotation(new Quaternionf()),
                held.last().pose().getNormalizedRotation(new Quaternionf()));
    }

    private static boolean draw(boolean left, boolean grip, PoseStack pose, MultiBufferSource buffers, int light) {
        var id = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID,
                "models/entity/paw_" + (left ? "left" : "right") + (grip ? "_grip.glb" : "_open.glb"));
        var model = GemRenderModels.handle(id).get();
        if (model == null) return false;
        var textures = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        for (var part : model.model().meshes()) {
            // Atlas publication is queued on the render thread after async loading.
            if (textures.getTexture(part.material().texture(), null) == null) return false;
        }
        // These small meshes are already posed. Feed their triangles through the
        // same entity buffers as vanilla hands so Iris owns the hand pass too.
        // GemRender's standalone direct shader is not part of Iris's hand pass.
        var min = new Vector3f(Float.POSITIVE_INFINITY);
        var max = new Vector3f(Float.NEGATIVE_INFINITY);
        int alpha = SpiritSelfLight.handAlpha(SpiritGlow.active());
        for (int pass = 0; pass < 2; pass++) for (var part : model.model().meshes()) {
            if (!(part.mesh() instanceof GltfMesh mesh)) continue;
            var vertices = buffers.getBuffer(pass == 0 ? SpiritHandRenderType.triangles(part.material().texture())
                    : SpiritHandRenderType.emission(part.material().texture()));
            var geometry = mesh.geometry();
            var forearm = grip ? new SpiritForearm(pose.last(), geometry, left) : null;
            var point = new Vector3f();
            var normal = new Vector3f();
            for (int triangle = 0; triangle < geometry.indexCount(); triangle += 3) {
                // Iris preserves smooth vertex normals for triangles. Its quad
                // path replaces them with a single face normal during world rendering.
                for (int corner = 0; corner < 3; corner++) {
                    int i = geometry.index(triangle + corner);
                    normal.set(geometry.normal(i,0), geometry.normal(i,1), geometry.normal(i,2));
                    if (forearm != null) forearm.vertex(geometry.position(i,0), geometry.position(i,1), geometry.position(i,2), point, normal);
                    else {
                        pose.last().pose().transformPosition(point.set(geometry.position(i,0), geometry.position(i,1), geometry.position(i,2)));
                        pose.last().normal().transform(normal).normalize();
                    }
                    if (pass == 0 && geometry.position(i,1) > -.1f) { min.min(point); max.max(point); }
                    vertices.addVertex(point.x, point.y, point.z)
                            .setColor(255, 255, 255, alpha)
                            .setUv(geometry.texCoord(i,0), geometry.texCoord(i,1) + (pass == 0 ? 0 : SpiritSelfLight.EMISSION_UV_OFFSET))
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(SpiritSelfLight.packed(light))
                            .setNormal(normal.x, normal.y, normal.z);
                }
            }
        }
        // The first-person pass has no world SpiritAura. A small view-space halo follows the posed palm.
        if (Float.isFinite(min.x)) {
            var centre = new Vector3f(min).add(max).mul(.5f);
            float radius = Math.max(.08f, Math.max(max.x - min.x, max.y - min.y) * .8f);
            SpiritEnergy.quad(buffers.getBuffer(SpiritEnergy.glow()), new PoseStack().last(), centre,
                    new Vector3f(radius,0,0), new Vector3f(0,radius,0), 0xBFE4FF, .35f);
        }
        return true;
    }
}
