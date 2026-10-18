package cn.wwl.orimod.client.render;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.asset.AnimationContract;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One validation per loaded model, including each resource reload. Values never retain the model. */
final class SpiritClips {
    private static final GltfAnimation REST = GltfAnimation.procedural("ori_missing_rest", 1f);
    private static final Map<GemRenderGltfModel, Resolved> CACHE = new WeakHashMap<>();
    private SpiritClips() {}

    static GltfAnimation get(GemRenderGltfModel model, String name) {
        var resolved = resolved(model);
        return resolved.clips.getOrDefault(name, resolved.fallback);
    }
    static List<String> missing(GemRenderGltfModel model) { return resolved(model).missing; }

    static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().getOverlay() != null) return;
        var model = com.wf.gemrender.asset.GemRenderModels.handle(net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("orimod", "models/entity/ori.glb")).get();
        if (model != null) resolved(model);
    }

    private static synchronized Resolved resolved(GemRenderGltfModel model) {
        return CACHE.computeIfAbsent(model, SpiritClips::validate);
    }

    private static Resolved validate(GemRenderGltfModel model) {
        java.util.function.Predicate<String> available = name -> {
            var clip = model.animation(name);
            return clip != null && Float.isFinite(clip.duration()) && clip.duration() > 0;
        };
        var missing = AnimationContract.missing(available);
        var fallback = available.test("idle") ? model.animation("idle") : REST;
        var clips = new HashMap<String, GltfAnimation>();
        for (String name : AnimationContract.REQUIRED) {
            String resolved = AnimationContract.resolveName(name, available);
            clips.put(name, resolved.isEmpty() ? REST : model.animation(resolved));
        }
        if (!missing.isEmpty()) OriMod.LOGGER.warn("Ori animation contract: missing or invalid clips {}; fallback={}", missing, fallback.name());
        return new Resolved(Map.copyOf(clips), missing, fallback);
    }

    private record Resolved(Map<String, GltfAnimation> clips, List<String> missing, GltfAnimation fallback) {}
}
