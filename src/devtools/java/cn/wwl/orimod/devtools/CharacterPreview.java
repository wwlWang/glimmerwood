package cn.wwl.orimod.devtools;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.blend.Crossfade;
import com.wf.gemrender.gltf.blend.FadeCurve;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Disposable compatibility scene; never registered by the production mod. */
final class CharacterPreview {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "orimod_devtools");
    private static final Supplier<EntityType<ArmorStand>> PREVIEW = ENTITIES.register("ori_preview", () ->
            EntityType.Builder.<ArmorStand>of(ArmorStand::new, MobCategory.MISC)
                    .sized(0.65f, 1.3f).clientTrackingRange(12).updateInterval(1)
                    .build("orimod_devtools:ori_preview"));
    private static final AtomicInteger LIVE = new AtomicInteger();
    private static volatile String lastClip = "unloaded";
    private static volatile int modelGenerations;

    static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(PREVIEW.get(), LivingEntity.createLivingAttributes().build()));
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(PREVIEW.get(), NoopRenderer::new));
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                SimpleEntityVisualizer.builder(PREVIEW.get()).factory(PreviewVisual::new)
                        .skipVanillaRender(entity -> true).apply()));
    }

    static Map<String, Object> state() {
        return Map.of("liveVisuals", LIVE.get(), "clip", lastClip, "modelGenerations", modelGenerations);
    }

    private static final class PreviewVisual extends GemRenderEntityVisual<ArmorStand> {
        private static final String[] CLIPS = {"idle", "run", "crouch", "bend", "tail_sway"};
        private GemRenderGltfModel loaded;
        private Crossfade crossfade;
        private String selected;

        PreviewVisual(VisualizationContext context, ArmorStand entity, float partialTick) {
            super(context, entity, partialTick, GemRenderModels.handle(
                    ResourceLocation.fromNamespaceAndPath("orimod_devtools", "models/ori_deformation_sample.glb")));
            // Shader packs cast geometry shadows; the vanilla blob becomes an opaque
            // disc on the Iris/Colorwheel alpha-test path.
            if (!net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()) {
                addComponent(new ShadowComponent(context, entity).radius(0.35f));
            }
            LIVE.incrementAndGet();
        }

        @Override
        protected boolean blend(float partialTick, AnimationBlend blend) {
            GemRenderGltfModel current = model();
            if (current == null) return false;
            if (current != loaded) {
                loaded = current;
                crossfade = new Crossfade(current.layout().nodeTable());
                selected = null;
                modelGenerations++;
            }
            float now = (entity.tickCount + partialTick) / 20.0f;
            String next = entity.hasCustomName() ? entity.getCustomName().getString() : CLIPS[(entity.tickCount / 80) % CLIPS.length];
            if (current.animation(next) == null) next = "idle";
            if (!next.equals(selected)) {
                crossfade.play(current.animation(next), now, 0.18f, FadeCurve.SMOOTH);
                selected = next;
                lastClip = next;
            }
            crossfade.write(now, blend);
            return true;
        }

        @Override
        protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
            clips[0] = model() == null ? null : model().animation("idle");
            times[0] = (entity.tickCount + partialTick) / 20.0f;
        }

        @Override
        protected void _delete() {
            super._delete();
            LIVE.decrementAndGet();
        }
    }
}
