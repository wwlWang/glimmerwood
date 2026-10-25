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
    public static void beginHeld(LivingEntity entity, ItemStack item, ItemDisplayContext context, PoseStack pose, boolean left) {
        pose.pushPose();
        boolean measure = DIAGNOSTICS.enabled();
        prepared[left?1:0]=null;bladeMeasurements[left?1:0]=null;
        if (!pilot(item)) {
            var offset=SpiritHandSpace.offset(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),
                    new Vector3f(left?.10f:-.10f,.22f,-.20f));
            pose.last().pose().translateLocal(offset.x,offset.y,offset.z);return;
        }
        if(item.is(cn.wwl.orimod.item.OriItems.SPIRIT_BLADE)) {
            var offset=SpiritHandSpace.offset(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),new Vector3f(0,.055f,-.025f));
            pose.last().pose().translateLocal(offset.x,offset.y,offset.z);
        }
        var model=Minecraft.getInstance().getItemRenderer().getModel(item,entity.level(),entity,entity.getId()+context.ordinal());
        var itemPose=new PoseStack();model.applyTransform(context,itemPose,left);
        var point=SpiritFirstPersonGrip.contact(item).primary();

        itemPose.last().pose().transformPosition(point);
        var view=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        var input=new org.joml.Matrix4f(view).mul(pose.last().pose());
        if(item.getItem() instanceof net.minecraft.world.item.BlockItem) {
            var complete=new org.joml.Matrix4f(input).mul(itemPose.last().pose());
            point=SpiritFirstPersonGrip.blockSupport(complete,left);
            itemPose.last().pose().transformPosition(point);
        }
        var oldPoint=input.transformPosition(new Vector3f(point));
        org.joml.Matrix4f corrected;
        if (item.getItem() instanceof net.minecraft.world.item.BlockItem) {
            var rotation=SpiritArmRig.basis(new Vector3f(left?.32f:-.32f,.40f,-.86f).normalize());
            var frame=SpiritArmRig.surface(left,oldPoint,rotation);prepared[left?1:0]=frame;
            corrected=new org.joml.Matrix4f(view).invert().translate(new Vector3f(frame.palm()).sub(oldPoint)).mul(input);
            if (measure) gripErrors[left?1:0]=new org.joml.Matrix4f(view).mul(corrected).transformPosition(new Vector3f(point)).distance(frame.palm());
            bladeMeasurements[left?1:0]=null;
        } else {
            var fit=SpiritBladeFraming.fit(left,input,itemPose.last().pose(),measure);
            prepared[left?1:0]=fit.arm();
            corrected=new org.joml.Matrix4f(view).invert().mul(fit.item());
            if (measure) {
                gripErrors[left?1:0]=fit.gripError();
                float aspect=(float)Minecraft.getInstance().getWindow().getWidth()/Minecraft.getInstance().getWindow().getHeight();
                bladeMeasurements[left?1:0]=SpiritBladeFraming.project(new org.joml.Matrix4f(fit.item()).mul(itemPose.last().pose()),aspect);
            }
        }
        pose.last().pose().set(corrected);corrected.normal(pose.last().normal());

    }

    public static void emptyArm(RenderArmEvent event) {
        if (!SpiritData.isSpirit(event.getPlayer()) || event.getPlayer().isInvisible()) return;
        boolean left = event.getArm() == HumanoidArm.LEFT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        var requested=new Vector3f(left?-.485f:.485f,-.31f,-.65f);
        var frame=SpiritArmRig.surface(left,requested,SpiritArmRig.basis(new Vector3f(left?.65f:-.65f,.9f,-.2f).normalize()).rotateY((float)Math.toRadians(left?-25:25)),new Vector3f(left?.0357f:-.0357f,-.09f,.0108f));
        if (draw(left,false,pose,event.getMultiBufferSource(),event.getPackedLight(),frame)) event.setCanceled(true);
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
        // Only the legacy bow/hammer/spear/etc. path uses this stack scale. The new IK frame
        // ignores the stack and applies SpiritArmRig.PAW_SCALE (1.18) in SpiritForearm.vertex.
        if (prepared[left?1:0] == null) stack.scale(LEGACY_PAW_SCALE, LEGACY_PAW_SCALE, LEGACY_PAW_SCALE);
        draw(left, true, stack, buffers, light, prepared[left?1:0]);
        prepared[left?1:0]=null;
        stack.popPose();
    }

    private static final SpiritArmRig.Frame[] prepared=new SpiritArmRig.Frame[2];
    public static final SpiritHandDiagnostics DIAGNOSTICS = new SpiritHandDiagnostics();
    private static final java.util.List<java.util.Map<String,Object>> measurements=new java.util.ArrayList<>(java.util.List.of(java.util.Map.of(),java.util.Map.of()));
    private static final float[] gripErrors=new float[2];
    private static final SpiritBladeFraming.Projection[] bladeMeasurements=new SpiritBladeFraming.Projection[2];
    public static java.util.Map<String,Object> diagnostics() { return java.util.Map.of("right",measurements.get(0),"left",measurements.get(1)); }
    private static boolean pilot(ItemStack item) {
        return item.getItem() instanceof net.minecraft.world.item.BlockItem block && !(block.getBlock() instanceof cn.wwl.orimod.block.SanctuaryBlock)
                || item.is(cn.wwl.orimod.item.OriItems.SPIRIT_BLADE);
    }
    private static final float LEGACY_PAW_SCALE = 1.25f;

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

    private static boolean draw(boolean left, boolean grip, PoseStack pose, MultiBufferSource buffers, int light, SpiritArmRig.Frame frame) {
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
        boolean measure = frame != null && DIAGNOSTICS.enabled();
        var screenMin=measure?new Vector3f(Float.POSITIVE_INFINITY):null;
        var screenMax=measure?new Vector3f(Float.NEGATIVE_INFINITY):null;
        var measuredPoint=measure?new Vector3f():null;
        var screenPoint=measure?new Vector3f():null;
        int near=0,central=0,total=0;
        var view=measure?new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()):null;
        float aspect=measure?(float)Minecraft.getInstance().getWindow().getWidth()/Minecraft.getInstance().getWindow().getHeight():1;
        for (int pass = 0; pass < 2; pass++) for (var part : model.model().meshes()) {
            if (!(part.mesh() instanceof GltfMesh mesh)) continue;
            var vertices = buffers.getBuffer(pass == 0 ? SpiritHandRenderType.triangles(part.material().texture())
                    : SpiritHandRenderType.emission(part.material().texture()));
            var geometry = mesh.geometry();
            var forearm=frame==null?null:new SpiritForearm(frame,geometry);
            var legacy=frame==null&&grip?new SpiritLegacyForearm(pose.last(),geometry,left):null;
            var point = new Vector3f();
            var normal = new Vector3f();
            for (int triangle = 0; triangle < geometry.indexCount(); triangle += 3) {
                // Iris preserves smooth vertex normals for triangles. Its quad
                // path replaces them with a single face normal during world rendering.
                for (int corner = 0; corner < 3; corner++) {
                    int i = geometry.index(triangle + corner);
                    normal.set(geometry.normal(i,0), geometry.normal(i,1), geometry.normal(i,2));
                    if (forearm != null) forearm.vertex(geometry.position(i,0), geometry.position(i,1), geometry.position(i,2), point, normal);
                    else if(legacy!=null)legacy.vertex(geometry.position(i,0),geometry.position(i,1),geometry.position(i,2),point,normal);
                    else {
                        pose.last().pose().transformPosition(point.set(geometry.position(i,0), geometry.position(i,1), geometry.position(i,2)));
                        pose.last().normal().transform(normal).normalize();
                    }
                    if (pass == 0 && geometry.position(i,1) > -.1f) { min.min(point); max.max(point); }
                    if(pass==0&&measure) {
                        var v=view.transformPosition(measuredPoint.set(point));total++;
                        if(v.z>=-.05f)near++;
                        float sx=v.x/(-v.z*.7002075f*aspect),sy=v.y/(-v.z*.7002075f);
                        if(Math.abs(sx)<.07f&&Math.abs(sy)<.07f)central++;
                        screenPoint.set(sx,sy,v.z);
                        screenMin.min(screenPoint);screenMax.max(screenPoint);
                    }
                    vertices.addVertex(point.x, point.y, point.z)
                            .setColor(255, 255, 255, alpha)
                            .setUv(geometry.texCoord(i,0), geometry.texCoord(i,1) + (pass == 0 ? 0 : SpiritSelfLight.EMISSION_UV_OFFSET))
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(SpiritSelfLight.packed(light))
                            .setNormal(normal.x, normal.y, normal.z);
                }
            }
        }
        if(measure) {
            var values=new java.util.LinkedHashMap<String,Object>();
            values.put("elbowDegrees",frame.elbowDegrees());values.put("wristDegrees",frame.wristDegrees());values.put("twistDegrees",frame.twistDegrees());
            values.put("upperLength",frame.shoulder().distance(frame.elbow()));values.put("forearmLength",frame.elbow().distance(frame.wrist()));
            values.put("gripError",grip?gripErrors[left?1:0]:0f);values.put("nearPlaneVertices",near);values.put("crosshairVertices",central);values.put("vertices",total);
            values.put("screenMin",java.util.List.of(screenMin.x,screenMin.y,screenMin.z));values.put("screenMax",java.util.List.of(screenMax.x,screenMax.y,screenMax.z));
            values.put("palm",java.util.List.of(frame.palm().x,frame.palm().y,frame.palm().z));
            var blade=grip?bladeMeasurements[left?1:0]:null;
            if(blade!=null)values.put("blade",java.util.Map.of("area",blade.area(),"nearClearance",blade.nearClearance(),
                    "visible",blade.visible(),"angle",blade.angle(),"tipX",blade.tipX(),"tipY",blade.tipY()));
            measurements.set(left?1:0,java.util.Map.copyOf(values));
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
