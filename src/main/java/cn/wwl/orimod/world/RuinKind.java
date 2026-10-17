package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Layouts are authored alongside the templates and packaged with the mod. */
public enum RuinKind {
    FOREST("forest_spring", 1), CREEK("creek_ruins", 6), CAVE("cave_altar", 24);

    public final String id;
    public final int abilities;
    private Layout layout;
    RuinKind(String id, int abilities) { this.id = id; this.abilities = abilities; }
    public ResourceLocation location() { return ResourceLocation.fromNamespaceAndPath("orimod", id); }
    public Component title() { return Component.translatable("ruin.orimod." + id); }
    public static RuinKind byId(String id) {
        for (var kind : values()) if (kind.id.equals(id)) return kind;
        throw new IllegalArgumentException("Unknown ruin " + id);
    }
    public synchronized Layout layout() {
        if (layout != null) return layout;
        String path = "/data/orimod/ruin_layout/" + id + ".json";
        try (var stream = RuinKind.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing layout " + path);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var checks = new ArrayList<Checkpoint>();
            for (var value : root.getAsJsonArray("checkpoints")) {
                var point = value.getAsJsonObject();
                checks.add(new Checkpoint(vector(point.getAsJsonArray("position")), point.get("action").getAsString(), point.get("count").getAsInt()));
            }
            var critical = new ArrayList<Anchor>();
            for (var value : root.getAsJsonArray("critical")) {
                var anchor = value.getAsJsonObject();
                critical.add(new Anchor(BlockPos.containing(vector(anchor.getAsJsonArray("position"))), ResourceLocation.parse(anchor.get("block").getAsString())));
            }
            var clearances = new ArrayList<Clearance>();
            for (var value : root.getAsJsonArray("clearances")) {
                var box = value.getAsJsonObject();
                clearances.add(new Clearance(BlockPos.containing(vector(box.getAsJsonArray("from"))), BlockPos.containing(vector(box.getAsJsonArray("to")))));
            }
            layout = new Layout(BlockPos.containing(vector(root.getAsJsonArray("size"))),
                    BlockPos.containing(vector(root.getAsJsonArray("shrine"))), vector(root.getAsJsonArray("safe")),
                    vector(root.getAsJsonArray("entry")), root.get("surface_y").getAsInt(),
                    List.copyOf(checks), List.copyOf(critical), List.copyOf(clearances));
            return layout;
        } catch (Exception error) { throw new IllegalStateException("Invalid ruin layout " + path, error); }
    }
    private static Vec3 vector(com.google.gson.JsonArray array) { return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()); }
    public record Checkpoint(Vec3 position, String action, int count) {}
    public record Anchor(BlockPos position, ResourceLocation block) {}
    public record Clearance(BlockPos from, BlockPos to) {}
    public record Layout(BlockPos size, BlockPos shrine, Vec3 safe, Vec3 entry, int surfaceY,
                         List<Checkpoint> checkpoints, List<Anchor> critical, List<Clearance> clearances) {}
}
