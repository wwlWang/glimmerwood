package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import dev.engine_room.flywheel.lib.visual.component.FireComponent;
import org.joml.Matrix4f;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the weighted mesh only while a player uses the spirit form. */
public final class SpiritPlayerVisual extends ComponentEntityVisual<Player> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "models/entity/ori.glb");
    private static final Map<UUID, Body> BODIES = new ConcurrentHashMap<>();
    private final VisualizationContext context;
    private final SpiritAnimation animation;
    private Body body;

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritClips::tick);
        SimpleEntityVisualizer.builder(EntityType.PLAYER).factory(SpiritPlayerVisual::new)
                .skipVanillaRender(player -> SpiritAppearance.visible(player) && GemRenderModels.handle(MODEL).get() != null).apply();
    }

    private SpiritPlayerVisual(VisualizationContext context, Player player, float partialTick) {
        super(context, player, partialTick);
        this.context = context;
        this.animation = new SpiritAnimation(player);
    }

    @Override public void beginFrame(Context frame) {
        var mc = Minecraft.getInstance();
        boolean visible = SpiritAppearance.visible(entity) && !entity.isInvisible()
                && (entity != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson());
        if (!visible) {
            if (body != null) { body.delete(); body = null; }
            return;
        }
        if (body == null) body = new Body(context, entity, frame.partialTick(), animation);
        body.beginFrame(frame);
    }

    public static Map<String, Object> diagnostics(Player player) {
        Body b = BODIES.get(player.getUUID());
        if (b == null) return Map.of("visible", false);
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("visible", true); result.put("base", b.animation.selected()); result.put("overlay", b.animation.overlay());
        result.put("clipTime", b.animation.clipTime()); result.put("speed", b.animation.speed());
        result.put("baseClip", b.animation.baseClip()); result.put("overlayClip", b.animation.overlayClip());
        result.put("missingClips", b.animation.missingClips());
        result.put("aimContactError", b.animation.aimContactError());
        result.put("sockets", b.socketsReady); result.put("shadow", b.shadow != null);
        if (b.socketsReady) {
            result.put("poseNanos", b.poseNanos);
            result.put("position", new double[]{b.position.x, b.position.y, b.position.z});
            result.put("soleLeft", new float[]{b.leftSole.m30(), b.leftSole.m31(), b.leftSole.m32()});
            result.put("soleRight", new float[]{b.rightSole.m30(), b.rightSole.m31(), b.rightSole.m32()});
            result.put("gripLeft", b.leftGrip.get(new float[16])); result.put("gripRight", b.rightGrip.get(new float[16]));
            result.put("pitch", player.getXRot()); result.put("yaw", player.getYRot());
            result.put("bodyYaw", player.yBodyRot);
        }
        return result;
    }

    static boolean socket(Player player, boolean left, Matrix4f target) {
        Body b = BODIES.get(player.getUUID());
        if (b == null || !b.socketsReady || !SpiritAppearance.visible(player)) return false;
        target.set(left ? b.leftGrip : b.rightGrip);
        return true;
    }

    @Override protected void _delete() {
        if (body != null) { body.delete(); body = null; }
        super._delete();
    }

    private static final class Body extends GemRenderEntityVisual<Player> {
        private final VisualizationContext context;
        private final SpiritAnimation animation;
        private final Matrix4f local = new Matrix4f(), world = new Matrix4f(), leftGrip = new Matrix4f(), rightGrip = new Matrix4f();
        private final Matrix4f leftSole = new Matrix4f(), rightSole = new Matrix4f();
        private Vec3 position = Vec3.ZERO;
        private long poseNanos;
        private ShadowComponent shadow;
        private boolean socketsReady, animatedThisFrame;
        Body(VisualizationContext context, Player player, float partialTick, SpiritAnimation animation) {
            super(context, player, partialTick, GemRenderModels.handle(MODEL));
            this.context = context;
            this.animation = animation;
            addComponent(new FireComponent(context, player));
            BODIES.put(player.getUUID(), this);
        }

        @Override public void beginFrame(Context frame) {
            boolean wantsShadow = !SpiritShaders.active();
            if (wantsShadow && shadow == null) {
                shadow = new ShadowComponent(context, entity).radius(.30f).strength(.65f);
                addComponent(shadow);
            } else if (!wantsShadow && shadow != null) {
                components.remove(shadow); shadow.delete(); shadow = null;
            }
            socketsReady = animatedThisFrame = false;
            super.beginFrame(frame);
            if (animatedThisFrame && pose() != null) {
                Vec3 at = entity.getPosition(frame.partialTick());
                modelTransform(local, frame.partialTick());
                world.translation((float)at.x, (float)at.y, (float)at.z).mul(local);
                // Source labels follow X signs; facing glTF +Z, -X is the right hand.
                pose().boneMatrix("grip_l", rightGrip).mulLocal(world);
                pose().boneMatrix("grip_r", leftGrip).mulLocal(world);
                pose().boneMatrix("sole_l", rightSole).mulLocal(world);
                pose().boneMatrix("sole_r", leftSole).mulLocal(world);
                position = at; poseNanos = System.nanoTime();
                socketsReady = true;
            }
        }

        private void modelTransform(Matrix4f target, float partial) {
            float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
            if (entity.isSleeping() && entity.getBedOrientation() != null) yaw = entity.getBedOrientation().toYRot();
            target.rotationY(-yaw * Mth.DEG_TO_RAD).scale(entity.getScale());
            if (entity.isVisuallySwimming() || entity.isFallFlying()) target.rotateX(-entity.getViewXRot(partial) * Mth.DEG_TO_RAD);
        }

        @Override protected void transform(Matrix4f target, float partial) {
            modelTransform(local, partial);
            target.translation(getVisualPosition(partial)).mul(local);
        }

        @Override protected boolean blend(float partialTick, AnimationBlend blend) {
            if (model() == null) return false;
            animatedThisFrame = true;
            animation.write(model(), partialTick, blend);
            return true;
        }

        @Override protected void _delete() {
            BODIES.remove(entity.getUUID(), this);
            socketsReady = false;
            super._delete();
        }

        @Override protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
            clips[0] = model() == null ? null : SpiritClips.get(model(), "idle");
            times[0] = (entity.tickCount + partialTick) / 20.0f;
        }
    }
}
