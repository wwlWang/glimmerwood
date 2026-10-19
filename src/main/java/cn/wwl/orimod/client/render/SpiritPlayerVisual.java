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
import com.wf.gemrender.gltf.GemRenderGltfModel;
import org.joml.Matrix4f;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the weighted mesh only while a player uses the spirit form. */
public final class SpiritPlayerVisual extends ComponentEntityVisual<Player> {
    /** Minimum block light on the spirit body (0-15). R92: full (15): Ori is a light source, white at any hour (R88 used 10, which read grey-pink at night). */
    public static final int SELF_LIGHT = 15;
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "models/entity/ori.glb");
    private static final Map<UUID, Body> BODIES = new ConcurrentHashMap<>();
    /** R92: when any spirit body last posed; while visuals are live, a spirit never falls back to the humanoid. */
    private static volatile long lastPosedNanos = Long.MIN_VALUE / 2;

    /**
     * R92: true while the spirit body is the intended visual but this frame has no pose yet (the Flywheel visual was just
     * recreated after a teleport or landing re-section). Drawing nothing for that frame beats flashing the vanilla humanoid.
     */
    public static boolean hideVanilla(Player player) {
        return SpiritAppearance.visible(player) && System.nanoTime() - lastPosedNanos < 2_000_000_000L;
    }
    private final VisualizationContext context;
    private final SpiritAnimation animation;
    private Body body;

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritClips::tick);
        SimpleEntityVisualizer.builder(EntityType.PLAYER).factory(SpiritPlayerVisual::new)
                .skipVanillaRender(player -> false).apply();
    }

    private SpiritPlayerVisual(VisualizationContext context, Player player, float partialTick) {
        super(context, player, partialTick);
        this.context = context;
        this.animation = new SpiritAnimation(player);
    }

    @Override public void beginFrame(Context frame) {
        var mc = Minecraft.getInstance();
        boolean visible = SpiritAppearance.visible(entity)
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
        result.put("leanPitch", b.animation.leanPitch()); result.put("leanRoll", b.animation.leanRoll()); result.put("squash", b.animation.squash());
        var gait = b.animation.gait(); result.put("gaitWalk", gait[0]); result.put("gaitRun", gait[1]); result.put("gaitSprint", gait[2]);
        result.put("baseClip", b.animation.baseClip()); result.put("overlayClip", b.animation.overlayClip());
        result.put("missingClips", b.animation.missingClips());
        result.put("secondary", b.animation.secondary());
        result.put("aimContactError", b.animation.aimContactError());
        result.put("sockets", b.socketsReady); result.put("shadow", false);
        result.put("layers", SpiritVanillaLayers.diagnostics());
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

    /** This frame's exact pose, shared by the body, equipment and outline. */
    public static Frame frame(Player player) {
        Body b = BODIES.get(player.getUUID());
        return b != null && b.socketsReady && SpiritAppearance.visible(player)
                ? new Frame(b.model(), b.pose()::boneMatrix, new Matrix4f(b.local), true) : null;
    }

    @FunctionalInterface public interface Bones { Matrix4f read(int slot, Matrix4f out); }
    public record Frame(GemRenderGltfModel model, Bones bones, Matrix4f transform, boolean world) {
        public Matrix4f boneMatrix(String name, Matrix4f out) { return boneMatrix(model.layout().nodeTable().slotOfName(name), out); }
        public Matrix4f boneMatrix(int slot, Matrix4f out) { return bones.read(slot, out); }
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
        private boolean socketsReady, animatedThisFrame, everAnimated;
        Body(VisualizationContext context, Player player, float partialTick, SpiritAnimation animation) {
            super(context, player, partialTick, GemRenderModels.handle(MODEL));
            this.context = context;
            this.animation = animation;
            BODIES.put(player.getUUID(), this);
        }

        @Override public void beginFrame(Context frame) {
            socketsReady = animatedThisFrame = false;
            super.beginFrame(frame);
            // R92: GemRender may skip re-blending on a frame (frame budget). The previous pose is still the one drawn,
            // so keep the sockets from it; otherwise frame() returns null and the vanilla humanoid flashes over Ori.
            if (animatedThisFrame) everAnimated = true;
            if ((animatedThisFrame || everAnimated) && pose() != null) {
                Vec3 at = entity.getPosition(frame.partialTick());
                modelTransform(local, frame.partialTick());
                world.translation((float)at.x, (float)at.y, (float)at.z).mul(local);
                // Source labels follow X signs; facing glTF +Z, -X is the right hand.
                pose().boneMatrix("grip_l", rightGrip).mulLocal(world);
                pose().boneMatrix("grip_r", leftGrip).mulLocal(world);
                pose().boneMatrix("sole_l", rightSole).mulLocal(world);
                pose().boneMatrix("sole_r", leftSole).mulLocal(world);
                position = at; poseNanos = System.nanoTime(); lastPosedNanos = poseNanos;
                socketsReady = true;
            }
        }

        private void modelTransform(Matrix4f target, float partial) {
            float yaw = animation.facing(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
            if (entity.isSleeping() && entity.getBedOrientation() != null) yaw = entity.getBedOrientation().toYRot();
            target.rotationY(-yaw * Mth.DEG_TO_RAD).scale(entity.getScale());
            // R74: lean into acceleration and turns about the hips, squash-stretch on jump/land (volume kept).
            float s = animation.squash(), w = 1 / (float) Math.sqrt(s);
            target.translate(0, .45f, 0).rotateX(animation.leanPitch()).rotateZ(animation.leanRoll()).translate(0, -.45f, 0).scale(w, s, w);
            if (entity.isAutoSpinAttack()) {
                // The swim pose is already horizontal along +Z. Roll about its torso,
                // at vanilla's 75 degrees per tick, including every fitted attachment.
                target.translate(0, .35f, 0).rotateX(-entity.getViewXRot(partial) * Mth.DEG_TO_RAD)
                        .rotateZ((entity.tickCount + partial) * -75f * Mth.DEG_TO_RAD).translate(0, -.35f, 0);
            } else if (entity.isVisuallySwimming() || entity.isFallFlying()) target.rotateX(-entity.getViewXRot(partial) * Mth.DEG_TO_RAD);
        }

        @Override protected void transform(Matrix4f target, float partial) {
            modelTransform(local, partial);
            target.translation(getVisualPosition(partial)).mul(local);
            // Keep the pose available to visible equipment and glowing outlines.
            if (entity.isInvisible()) target.scale(0);
        }

        /** R88: a spirit is faintly self-lit: never darker than {@link #SELF_LIGHT} block light, so it glows softly at night. */
        @Override protected int computePackedLight(float partialTick) {
            int packed = super.computePackedLight(partialTick);
            return net.minecraft.client.renderer.LightTexture.pack(Math.max(net.minecraft.client.renderer.LightTexture.block(packed), SELF_LIGHT),
                    net.minecraft.client.renderer.LightTexture.sky(packed));
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
