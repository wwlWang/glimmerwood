package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.spirit.SpiritData;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.blend.Crossfade;
import com.wf.gemrender.gltf.blend.FadeCurve;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/** Owns the weighted mesh only while a player uses the spirit form. */
public final class SpiritPlayerVisual extends ComponentEntityVisual<Player> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(OriMod.MOD_ID, "models/entity/ori.glb");
    private final VisualizationContext context;
    private Body body;

    public static void register() {
        SimpleEntityVisualizer.builder(EntityType.PLAYER).factory(SpiritPlayerVisual::new)
                .skipVanillaRender(player -> SpiritData.isSpirit(player) && GemRenderModels.handle(MODEL).get() != null).apply();
    }

    private SpiritPlayerVisual(VisualizationContext context, Player player, float partialTick) {
        super(context, player, partialTick);
        this.context = context;
    }

    @Override public void beginFrame(Context frame) {
        var mc = Minecraft.getInstance();
        boolean visible = SpiritData.isSpirit(entity) && !entity.isInvisible()
                && (entity != mc.player || !mc.options.getCameraType().isFirstPerson());
        if (!visible) {
            if (body != null) { body.delete(); body = null; }
            return;
        }
        if (body == null) body = new Body(context, entity, frame.partialTick());
        body.beginFrame(frame);
    }

    @Override protected void _delete() {
        if (body != null) { body.delete(); body = null; }
        super._delete();
    }

    private static final class Body extends GemRenderEntityVisual<Player> {
        private GemRenderGltfModel loaded;
        private Crossfade crossfade;
        private String selected;
        Body(VisualizationContext context, Player player, float partialTick) {
            super(context, player, partialTick, GemRenderModels.handle(MODEL));
        }

        @Override protected boolean blend(float partialTick, AnimationBlend blend) {
            var current = model();
            if (current == null) return false;
            if (current != loaded) {
                loaded = current;
                crossfade = new Crossfade(current.layout().nodeTable());
                selected = null;
            }
            String next = entity.isCrouching() ? "crouch" : entity.getDeltaMovement().horizontalDistanceSqr() > 0.002 ? "run" : "idle";
            float now = (entity.tickCount + partialTick) / 20.0f;
            if (!next.equals(selected)) {
                crossfade.play(current.animation(next), now, 0.12f, FadeCurve.SMOOTH);
                selected = next;
            }
            crossfade.write(now, blend);
            return true;
        }

        @Override protected void animate(float partialTick, GltfAnimation[] clips, float[] times) {
            clips[0] = model() == null ? null : model().animation("idle");
            times[0] = (entity.tickCount + partialTick) / 20.0f;
        }
    }
}
