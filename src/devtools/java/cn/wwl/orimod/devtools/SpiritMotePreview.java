package cn.wwl.orimod.devtools;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class SpiritMotePreview {
    private static BlockEffectExecutor active;
    private static BlockState anchorState;
    private static int restarts;

    static Map<String, Object> start(Path directory, BlockPos position) throws IOException {
        var mc = Minecraft.getInstance();
        if (mc.level == null) throw new IllegalStateException("World required");
        var provider = mc.level.registryAccess();
        Path source = directory.resolve("spirit-motes-preview.fxproj");
        Path export = directory.resolve("spirit-motes-preview.fx");
        if (!Files.exists(source)) {
            var project = new FXProject();
            var emitter = new ParticleEmitter();
            emitter.setName("Spirit mote preview");
            var config = emitter.config;
            config.setDuration(100);
            config.setLooping(true);
            config.setMaxParticles(96);
            config.setStartLifetime(NumberFunction.constant(60));
            config.setStartSpeed(NumberFunction.constant(0.4f));
            config.setStartSize(new NumberFunction3(0.10, 0.10, 0.10));
            config.setStartColor(NumberFunction.color(0xB070EDFF));
            config.emission.setEmissionRate(NumberFunction.constant(0.5f));
            var cone = new Cone();
            cone.setRadius(0.55f);
            cone.setAngle(12);
            config.shape.setShape(cone);
            project.getFx().getFxData().objects().add(emitter);
            NbtIo.writeCompressed(project.serializeNBT(provider), source);
        }
        // Export from the reopened editor project, then play the reopened export.
        var reopened = new FXProject();
        reopened.deserializeNBT(provider, NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap()));
        var data = reopened.getFx().serializeNBT(provider);
        data.putInt("version", FXProject.VERSION);
        NbtIo.writeCompressed(data, export);
        var fx = new FX();
        fx.deserializeNBT(provider, NbtIo.readCompressed(export, NbtAccounter.unlimitedHeap()));
        stop();
        active = new BlockEffectExecutor(fx, mc.level, position);
        active.start();
        anchorState = mc.level.getBlockState(position);
        restarts = 0;
        return Map.of("source", source.toString(), "export", export.toString(),
                "objects", fx.getFxData().objects().size(), "anchor", position.toShortString());
    }

    static void tick() {
        if (active == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level != active.level || !mc.level.isLoaded(active.pos)
                || !mc.level.getBlockState(active.pos).equals(anchorState)) {
            stop();
        } else if (mc.getOverlay() == null && (active.getRuntime() == null || !active.getRuntime().isValid())) {
            // Persistent previews own their lifecycle. Photon clears particles on a
            // resource reload; start prunes the discarded executor before replacing it.
            active.start();
            restarts++;
        }
    }

    static void stop() {
        if (active == null) return;
        if (active.getRuntime() != null) active.getRuntime().destroy(true);
        var bucket = BlockEffectExecutor.CACHE.get(active.pos);
        if (bucket != null) {
            bucket.remove(active);
            if (bucket.isEmpty()) BlockEffectExecutor.CACHE.remove(active.pos);
        }
        active = null;
    }

    static Map<String, Object> state() {
        return Map.of("active", active != null && active.getRuntime() != null && active.getRuntime().isValid(),
                "restarts", restarts, "anchor", active == null ? "none" : active.pos.toShortString());
    }
}
