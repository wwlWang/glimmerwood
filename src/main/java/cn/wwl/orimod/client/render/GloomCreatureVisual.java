package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.entity.*;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import static cn.wwl.orimod.client.render.GloomAnimationRules.*;

/** Shared skinned body; warning particles/ground markers remain owned by the existing entity FX. */
public final class GloomCreatureVisual<T extends LivingEntity> extends ComponentEntityVisual<T> {
    private static final Map<UUID, GloomCreatureVisual<?>> LIVE = new ConcurrentHashMap<>();
    private static final Map<GemRenderGltfModel, Boolean> VALIDATED = new WeakHashMap<>();
    private static volatile boolean legacy;
    private final VisualizationContext context;
    private final Kind kind;
    private final ResourceLocation asset;
    private Body body;
    private int generation = -1;
    private boolean failed;
    private volatile Map<String, Object> diagnostic = Map.of("active", false);

    public static void register() {
        register(OriMobs.GLOOM_SPITTER.get(), Kind.SPITTER, "gloom_spitter");
        register(OriMobs.GLOOM_BEETLE.get(), Kind.BEETLE, "gloom_beetle");
        register(OriMobs.GLOOM_HAWK.get(), Kind.HAWK, "gloom_hawk");
        register(OriMobs.GLOOM_HEART.get(), Kind.HEART, "gloom_heart");
        register(OriMobs.GLOOM_MATRIARCH.get(), Kind.MATRIARCH, "gloom_matriarch");
    }
    private static <T extends LivingEntity> void register(EntityType<T> type, Kind kind, String name) {
        SimpleEntityVisualizer.builder(type).factory((context, entity, partial) -> new GloomCreatureVisual<>(context, entity, partial, kind, name))
                // Keep vanilla's renderer for names, shadows and the per-body fallback decision.
                .skipVanillaRender(entity -> false).apply();
    }
    private GloomCreatureVisual(VisualizationContext context, T entity, float partial, Kind kind, String name) {
        super(context, entity, partial);
        this.context = context; this.kind = kind;
        asset = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "models/entity/" + name + ".glb");
        LIVE.put(entity.getUUID(), this);
    }

    public static boolean hideVanilla(LivingEntity entity) {
        var visual = LIVE.get(entity.getUUID());
        return visual != null && visual.entity == entity && !legacy && visual.generation == GemRenderModels.generation()
                && visual.body != null && visual.body.pose() != null && !visual.failed;
    }
    /** Devtools comparison only. A missing/invalid model takes the same fallback path automatically. */
    public static void legacy(boolean value) { legacy = value; }
    public static Map<String, Object> diagnostics(UUID id) {
        var visual = LIVE.get(id);
        return visual == null ? Map.of("active", false, "clip", "legacy", "time", 0) : visual.diagnostic;
    }

    @Override public void beginFrame(Context frame) {
        int nextGeneration = GemRenderModels.generation();
        if (generation != nextGeneration) { clearBody(); failed = false; generation = nextGeneration; }
        if (legacy || entity.isInvisible() || failed) { clearBody(); return; }
        try {
            var model = GemRenderModels.handle(asset).get();
            if (model == null || !valid(model, kind)) { clearBody(); return; }
            if (body == null) body = new Body(context, entity, frame.partialTick());
            body.beginFrame(frame);
            if (body.pose() != null) diagnostic = Map.of("active", true, "clip", body.selected.clip(), "time", body.sampleTime,
                    "speed", body.selected.speed(), "blend", body.weight, "bodyYaw", entity.yBodyRot,
                    "pitch", body.pitch, "timer", body.current.remaining(), "enrage", body.current.enrageRemaining());
        } catch (RuntimeException error) {
            failed = true; clearBody();
            OriMod.LOGGER.error("Creature visual {} unavailable; using legacy body until resource reload", asset, error);
        }
    }
    private static synchronized boolean valid(GemRenderGltfModel model, Kind kind) {
        return VALIDATED.computeIfAbsent(model, key -> {
            var missing = required(kind).stream().filter(name -> key.animation(name) == null
                    || !Float.isFinite(key.animation(name).duration()) || key.animation(name).duration() <= 0).toList();
            if (!missing.isEmpty()) OriMod.LOGGER.error("Creature {} missing clips {}; using legacy body", kind, missing);
            return missing.isEmpty();
        });
    }
    private void clearBody() {
        if (body != null) { body.delete(); body = null; }
        diagnostic = Map.of("active", false, "clip", "legacy", "time", 0);
    }
    @Override protected void _delete() { clearBody(); LIVE.remove(entity.getUUID(), this); super._delete(); }

    private State snapshot() {
        int action = 0, remaining = 0, phase = 1, enrage = 0;
        if (entity instanceof GloomSpitter e) remaining = e.charge();
        else if (entity instanceof GloomBeetle e) { action = e.state().ordinal(); remaining = e.remaining(); }
        else if (entity instanceof GloomHawk e) { action = e.state(); remaining = e.timer(); }
        else if (entity instanceof GloomHeart e) { action = e.attack(); remaining = e.timer(); phase = e.phase(); enrage = e.enrageRemaining(); }
        else if (entity instanceof GloomMatriarch e) { action = e.attack(); remaining = e.timer(); phase = e.phase(); enrage = e.enrageRemaining(); }
        return new State(kind, action, remaining, phase, enrage, entity.deathTime, entity.hurtTime, entity.getDeltaMovement().horizontalDistance());
    }

    private final class Body extends GemRenderEntityVisual<T> {
        private State current, previous;
        private Selection selected = new Selection("idle", Timing.LOOP, 0, 1);
        private String oldClip;
        private float lastTime = Float.NaN, changedAt, releasedAt = -100, hurtAt = -100, phase, sampleTime, oldTime, weight = 1, pitch;

        Body(VisualizationContext context, T entity, float partial) { super(context, entity, partial, GemRenderModels.handle(asset)); }

        @Override public void beginFrame(Context frame) {
            float now = (entity.tickCount + frame.partialTick()) / 20f;
            float dt = Float.isFinite(lastTime) ? Math.clamp(now - lastTime, 0, .25f) : 0;
            current = snapshot();
            if (released(previous, current)) releasedAt = now;
            if (current.hurtTicks() > 0 && (previous == null || current.hurtTicks() > previous.hurtTicks())) hurtAt = now;
            var next = select(current, frame.partialTick(), now - releasedAt, now - hurtAt);
            if (!next.clip().equals(selected.clip())) {
                oldClip = selected.clip(); oldTime = sampleTime; changedAt = now; phase = 0;
            }
            selected = next; phase += dt * next.speed();
            weight = oldClip == null ? 1 : Math.clamp((now - changedAt) / BLEND_SECONDS, 0, 1);
            if (weight == 1) oldClip = null;
            if (kind == Kind.HAWK || kind == Kind.MATRIARCH) {
                float target = flightPitch(current.speed(), entity.getDeltaMovement().y);
                pitch = Mth.lerp(1 - (float)Math.exp(-dt * 12), pitch, target);
            }
            previous = current; lastTime = now;
            super.beginFrame(frame);
        }

        @Override protected void transform(Matrix4f target, float partial) {
            target.translation(getVisualPosition(partial))
                    .rotateY(-Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD).scale(entity.getScale());
            if (entity instanceof GloomBeetle beetle) target.translate(0, .35f, 0).rotateZ(beetle.roll(partial) * Mth.DEG_TO_RAD).translate(0, -.35f, 0);
            if (kind == Kind.HAWK || kind == Kind.MATRIARCH) {
                float pivot = kind == Kind.HAWK ? .4f : .6f;
                target.translate(0, pivot, 0).rotateX(pitch).translate(0, -pivot, 0);
            }
        }
        @Override protected boolean blend(float partial, AnimationBlend blend) {
            var clip = model().animation(selected.clip());
            sampleTime = sample(selected, phase, clip.duration());
            if (oldClip != null) blend.override(model().animation(oldClip), oldTime, 1 - weight);
            blend.override(clip, sampleTime, weight);
            return true;
        }
        @Override protected void animate(float partial, GltfAnimation[] clips, float[] times) {
            clips[0] = model().animation("idle"); times[0] = clips[0].loop(entity.tickCount / 20f);
        }
        // GemRender PBR reads the emission band independently. Do not full-bright the non-emissive skin.
    }
}
