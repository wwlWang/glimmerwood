package cn.wwl.orimod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfMesh;
import cn.wwl.orimod.mixin.SpiritStuckLayerAccessor;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fits vanilla attachments to the same pose as the GemRender body, inside the entity pass. */
public final class SpiritVanillaLayers {
    private static final Map<String, Long> CALLS = new LinkedHashMap<>();
    private static Rig rig;
    private static final SpiritMeshSkinning SKIN = new SpiritMeshSkinning();

    private SpiritVanillaLayers() { }

    public static Map<String, Long> diagnostics() { return Map.copyOf(CALLS); }

    public static float shadowRadius(Player player) {
        return SpiritShaders.active() ? 0 : .30f * player.getScale();
    }

    public static PoseStack base(PoseStack initial, Player player, SpiritPlayerVisual.Frame frame) {
        PoseStack poses = new PoseStack();
        poses.mulPose(initial.last().pose());
        // PlayerRenderer offsets crouching by 1/8 block; the skinned crouch already contains its drop.
        if (frame.world() && player.isCrouching()) poses.translate(0, player.getScale() / 8f, 0);
        poses.mulPose(frame.transform());
        return poses;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(RenderLayer layer, PlayerModel model, Player player,
                              SpiritPlayerVisual.Frame frame, PoseStack initial, MultiBufferSource buffers, int light,
                              float swing, float amount, float partial, float age, float yaw, float pitch) {
        CALLS.merge(layer.getClass().getSimpleName(), 1L, Long::sum);
        if (layer instanceof ItemInHandLayer && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) return;
        if (layer instanceof ArrowLayer && player.getArrowCount() == 0 || layer instanceof BeeStingerLayer && player.getStingerCount() == 0) return;
        if (layer instanceof SpinAttackEffectLayer && !player.isAutoSpinAttack()) return;
        if (layer instanceof Deadmau5EarsLayer && !player.getName().getString().equals("deadmau5")) return;
        if (layer instanceof CapeLayer && (player.isInvisible() || !player.isModelPartShown(PlayerModelPart.CAPE))) return;
        if (layer instanceof ElytraLayer elytra && !elytra.shouldRender(player.getItemBySlot(EquipmentSlot.CHEST), player)) return;
        if (layer instanceof ParrotOnShoulderLayer && player.getShoulderEntityLeft().isEmpty() && player.getShoulderEntityRight().isEmpty()) return;
        if (layer instanceof HumanoidArmorLayer) {
            boolean armor = false;
            for (var stack : player.getArmorSlots()) armor |= stack.getItem() instanceof ArmorItem;
            if (!armor) return;
        }
        if (layer instanceof CustomHeadLayer) {
            var head = player.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty() || head.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.HEAD) return;
        }
        PoseStack poses = base(initial, player, frame);
        if (layer instanceof ItemInHandLayer) {
            SpiritHeldItems.render(player, frame, poses, buffers, light);
            return;
        }
        Rig current = rig(frame.model());
        if (layer instanceof ArrowLayer || layer instanceof BeeStingerLayer) {
            int count = layer instanceof ArrowLayer ? player.getArrowCount() : player.getStingerCount();
            if (count == 0) return;
            SKIN.prepare(frame);
            var meshes = frame.model().model().meshes().stream().map(m -> m.mesh())
                    .filter(GltfMesh.class::isInstance).map(GltfMesh.class::cast).toList();
            if (meshes.isEmpty()) return;
            var random = RandomSource.create(player.getId());
            Vector3f point = new Vector3f(), normal = new Vector3f();
            for (int i = 0; i < count; i++) {
                var geometry = meshes.get(random.nextInt(meshes.size())).geometry();
                SKIN.vertex(geometry, random.nextInt(geometry.vertexCount()), point, normal);
                poses.pushPose();
                poses.translate(point.x, point.y, point.z);
                ((SpiritStuckLayerAccessor)layer).orimod$renderStuck(poses, buffers, light, player,
                        -normal.x, -normal.y, -normal.z, partial);
                poses.popPose();
            }
        } else if (layer instanceof CapeLayer || layer instanceof ElytraLayer) {
            poses.mulPose(current.delta(frame, "spine").translate(0, .76f, -.065f)
                    .rotateX((float)Math.PI).scale(.60f));
            layer.render(poses, buffers, light, player, swing, amount, partial, age, yaw, pitch);
        } else if (layer instanceof ParrotOnShoulderLayer) {
            current.parrot(poses, buffers, light, frame, player.getShoulderEntityLeft(), true, swing, amount, yaw, pitch, player.tickCount);
            current.parrot(poses, buffers, light, frame, player.getShoulderEntityRight(), false, swing, amount, yaw, pitch, player.tickCount);
        } else {
            // Armor retains its material, trim, enchantment and slot rules. Head items and stuck
            // arrows use those same fitted parts. Restore every field before another player renders.
            ModelPart[] parts = {model.head, model.hat, model.body, model.rightArm, model.leftArm,
                    model.rightLeg, model.leftLeg, model.jacket, model.rightSleeve, model.leftSleeve,
                    model.rightPants, model.leftPants};
            PartState[] saved = new PartState[parts.length];
            for (int i = 0; i < parts.length; i++) saved[i] = new PartState(parts[i]);
            try {
                current.fit(frame, model);
                if (layer instanceof CustomHeadLayer) current.fitHeadItem(frame, model);
                if (!(layer instanceof HumanoidArmorLayer || layer instanceof CustomHeadLayer || layer instanceof StuckInBodyLayer)) {
                    poses.mulPose(current.delta(frame, "spine").translate(0, .9f, 0).rotateX((float)Math.PI).scale(.6f));
                }
                layer.render(poses, buffers, light, (LivingEntity)player, swing, amount, partial, age, yaw, pitch);
            } finally {
                for (PartState state : saved) state.restore();
            }
        }
    }

    private static Rig rig(GemRenderGltfModel model) {
        if (rig == null || rig.model != model) rig = new Rig(model);
        return rig;
    }

    private record PartState(ModelPart part, PartPose pose, float x, float y, float z) {
        PartState(ModelPart part) { this(part, part.storePose(), part.xScale, part.yScale, part.zScale); }
        void restore() { part.loadPose(pose); part.xScale = x; part.yScale = y; part.zScale = z; }
    }

    private static final class Rig {
        final GemRenderGltfModel model;
        final Matrix4f[] rest;
        final Map<String, Matrix4f> inverse = new LinkedHashMap<>();
        final Matrix4f head, body, rightArm, leftArm, rightLeg, leftLeg;
        final ParrotModel parrot = new ParrotModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PARROT));

        Rig(GemRenderGltfModel model) {
            this.model = model;
            rest = model.restPalette();
            head = anchor("head").translate(0, -.04f, 0).rotateX((float)Math.PI).scale(.54f, .46f, .50f);
            body = anchor("neck").rotateX((float)Math.PI).scale(.40f, .45f, .60f);
            rightArm = limb("upper_arm_l", "grip_l", .54f).translate(1 / 16f, 2 / 16f, 0);
            leftArm = limb("upper_arm_r", "grip_r", .54f).translate(-1 / 16f, 2 / 16f, 0);
            rightLeg = limb("thigh_l", "sole_l", .40f);
            leftLeg = limb("thigh_r", "sole_r", .40f);
        }

        Matrix4f delta(SpiritPlayerVisual.Frame frame, String name) {
            Matrix4f inv = inverse.computeIfAbsent(name, key -> new Matrix4f(rest[slot(key)]).invert());
            return frame.boneMatrix(name, new Matrix4f()).mul(inv);
        }

        void parrot(PoseStack poses, MultiBufferSource buffers, int light, SpiritPlayerVisual.Frame frame,
                    CompoundTag tag, boolean left, float swing, float amount, float yaw, float pitch, int tick) {
            if (EntityType.byString(tag.getString("id")).orElse(null) != EntityType.PARROT) return;
            poses.pushPose();
            poses.mulPose(delta(frame, "spine").translate(left ? .18f : -.18f, .65f, -.02f)
                    .rotateX((float)Math.PI).scale(.4f).translate(0, -1.5f, 0));
            var texture = ParrotRenderer.getVariantTexture(Parrot.Variant.byId(tag.getInt("Variant")));
            parrot.renderOnShoulder(poses, buffers.getBuffer(parrot.renderType(texture)), light,
                    OverlayTexture.NO_OVERLAY, swing, amount, yaw, pitch, tick);
            poses.popPose();
        }

        private int slot(String name) { return model.layout().nodeTable().slotOfName(name); }
        private Vector3f position(String name) { return rest[slot(name)].getTranslation(new Vector3f()); }
        private Matrix4f anchor(String name) { return new Matrix4f().translation(position(name)); }
        private Matrix4f limb(String from, String to, float width) {
            Vector3f origin = position(from), direction = position(to).sub(origin);
            float length = direction.length();
            return new Matrix4f().translation(origin)
                    .rotate(new Quaternionf().rotationTo(new Vector3f(0, -1, 0), direction.normalize()))
                    .rotateX((float)Math.PI).scale(width, length / .75f, width);
        }

        void fit(SpiritPlayerVisual.Frame frame, PlayerModel<?> model) {
            set(model.head, delta(frame, "head").mul(head)); model.hat.copyFrom(model.head);
            set(model.body, delta(frame, "spine").mul(body)); model.jacket.copyFrom(model.body);
            set(model.rightArm, delta(frame, "upper_arm_l").mul(rightArm)); model.rightSleeve.copyFrom(model.rightArm);
            set(model.leftArm, delta(frame, "upper_arm_r").mul(leftArm)); model.leftSleeve.copyFrom(model.leftArm);
            set(model.rightLeg, delta(frame, "thigh_l").mul(rightLeg)); model.rightPants.copyFrom(model.rightLeg);
            set(model.leftLeg, delta(frame, "thigh_r").mul(leftLeg)); model.leftPants.copyFrom(model.leftLeg);
        }

        void fitHeadItem(SpiritPlayerVisual.Frame frame, PlayerModel<?> model) {
            set(model.head, delta(frame, "head").mul(anchor("head").translate(0, -.04f, .025f)
                    .rotateX((float)Math.PI).scale(.58f, .46f, .64f)));
        }

        private static void set(ModelPart part, Matrix4f matrix) {
            Vector3f angles = matrix.getUnnormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());
            Vector3f scale = matrix.getScale(new Vector3f());
            part.setPos(matrix.m30() * 16, matrix.m31() * 16, matrix.m32() * 16);
            part.setRotation(angles.x, angles.y, angles.z);
            part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z;
        }
    }
}
