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
        String path = "/orimod/ruin_layout/" + id + ".json";
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
            var cells = new ArrayList<Cell>();
            if (root.has("cells")) for (var value : root.getAsJsonArray("cells")) {
                var cell = value.getAsJsonObject();
                cells.add(new Cell(BlockPos.containing(vector(cell.getAsJsonArray("position"))),
                        cn.wwl.orimod.progression.SpiritCells.Kind.valueOf(cell.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT))));
            }
            var updrafts = new ArrayList<BlockPos>();
            if (root.has("updrafts")) for (var value : root.getAsJsonArray("updrafts")) updrafts.add(BlockPos.containing(vector(value.getAsJsonArray())));
            BlockPos altar = root.has("boss_altar") ? BlockPos.containing(vector(root.getAsJsonArray("boss_altar"))) : null;
            var keystones = new ArrayList<BlockPos>();
            if (root.has("keystones")) for (var value : root.getAsJsonArray("keystones")) keystones.add(BlockPos.containing(vector(value.getAsJsonArray())));
            Vault vault = null;
            if (root.has("vault")) {
                var v = root.getAsJsonObject("vault"); var door = new ArrayList<BlockPos>();
                for (var value : v.getAsJsonArray("door")) door.add(BlockPos.containing(vector(value.getAsJsonArray())));
                vault = new Vault(BlockPos.containing(vector(v.getAsJsonArray("from"))), BlockPos.containing(vector(v.getAsJsonArray("to"))), List.copyOf(door));
            }
            BlockPos[] gloom = null;
            if (root.has("gloom_water")) { var gw = root.getAsJsonObject("gloom_water");
                gloom = new BlockPos[]{BlockPos.containing(vector(gw.getAsJsonArray("from"))), BlockPos.containing(vector(gw.getAsJsonArray("to")))}; }
            BlockPos mapStone = root.has("map_stone") ? BlockPos.containing(vector(root.getAsJsonArray("map_stone"))) : null;
            BlockPos storm = root.has("storm_altar") ? BlockPos.containing(vector(root.getAsJsonArray("storm_altar"))) : null;
            BlockPos[] sand = null;
            if (root.has("spirit_sand")) { var a = root.getAsJsonArray("spirit_sand"); sand = new BlockPos[]{BlockPos.containing(vector(a.get(0).getAsJsonArray())), BlockPos.containing(vector(a.get(1).getAsJsonArray()))}; }
            Secret secret = null;
            if (root.has("secret")) {
                var s = root.getAsJsonObject("secret");
                secret = new Secret(BlockPos.containing(vector(s.getAsJsonArray("entrance"))), net.minecraft.core.Direction.byName(s.get("facing").getAsString()), s.get("blocks").getAsString());
            }
            var bridges = new ArrayList<Bridge>();
            if (root.has("bridges")) for (var value : root.getAsJsonArray("bridges")) {
                var b = value.getAsJsonObject();
                bridges.add(new Bridge(BlockPos.containing(vector(b.getAsJsonArray("entry"))), BlockPos.containing(vector(b.getAsJsonArray("exit"))),
                        positions(b.getAsJsonArray("lanterns")), positions(b.getAsJsonArray("platforms")), positions(b.getAsJsonArray("escape")),
                        net.minecraft.core.Direction.byName(b.get("facing").getAsString())));
            }
            layout = new Layout(BlockPos.containing(vector(root.getAsJsonArray("size"))),
                    BlockPos.containing(vector(root.getAsJsonArray("shrine"))), vector(root.getAsJsonArray("safe")),
                    vector(root.getAsJsonArray("entry")), root.get("surface_y").getAsInt(),
                    List.copyOf(checks), List.copyOf(critical), List.copyOf(clearances), List.copyOf(cells), List.copyOf(updrafts), altar, List.copyOf(keystones), vault, gloom, mapStone, sand, storm, secret, List.copyOf(bridges), root.has("beetles")?positions(root.getAsJsonArray("beetles")):List.of());
            return layout;
        } catch (Exception error) { throw new IllegalStateException("Invalid ruin layout " + path, error); }
    }
    private static Vec3 vector(com.google.gson.JsonArray array) { return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()); }
    public record Checkpoint(Vec3 position, String action, int count) {}
    public record Anchor(BlockPos position, ResourceLocation block) {}
    public record Clearance(BlockPos from, BlockPos to) {}
    public record Layout(BlockPos size, BlockPos shrine, Vec3 safe, Vec3 entry, int surfaceY,
                         List<Checkpoint> checkpoints, List<Anchor> critical, List<Clearance> clearances, List<Cell> cells, List<BlockPos> updrafts, BlockPos bossAltar,
                         List<BlockPos> keystones, Vault vault, BlockPos[] gloomWater, BlockPos mapStone, BlockPos[] spiritSand, BlockPos stormAltar, Secret secret, List<Bridge> bridges, List<BlockPos> beetles) {}
    private static List<BlockPos> positions(com.google.gson.JsonArray array) {
        var out = new ArrayList<BlockPos>(); for (var p : array) out.add(BlockPos.containing(vector(p.getAsJsonArray()))); return List.copyOf(out);
    }
    public record Bridge(BlockPos entry, BlockPos exit, List<BlockPos> lanterns, List<BlockPos> platforms, List<BlockPos> escape, net.minecraft.core.Direction facing) {
        public Bridge {
            entry=entry.immutable();exit=exit.immutable();lanterns=lanterns.stream().map(BlockPos::immutable).toList();
            platforms=platforms.stream().map(BlockPos::immutable).toList();escape=escape.stream().map(BlockPos::immutable).toList();
            if(facing==null || platforms.isEmpty())throw new IllegalArgumentException("Invalid light bridge");
        }
        public net.minecraft.nbt.CompoundTag save() {
            var t=new net.minecraft.nbt.CompoundTag();t.putLong("Entry",entry.asLong());t.putLong("Exit",exit.asLong());t.putString("Facing",facing.getName());
            t.putLongArray("Lanterns",lanterns.stream().mapToLong(BlockPos::asLong).toArray());
            t.putLongArray("Platforms",platforms.stream().mapToLong(BlockPos::asLong).toArray());
            t.putLongArray("Escape",escape.stream().mapToLong(BlockPos::asLong).toArray());return t;
        }
        public static Bridge load(net.minecraft.nbt.CompoundTag t) {
            return new Bridge(BlockPos.of(t.getLong("Entry")),BlockPos.of(t.getLong("Exit")),
                    java.util.Arrays.stream(t.getLongArray("Lanterns")).mapToObj(BlockPos::of).toList(),
                    java.util.Arrays.stream(t.getLongArray("Platforms")).mapToObj(BlockPos::of).toList(),
                    java.util.Arrays.stream(t.getLongArray("Escape")).mapToObj(BlockPos::of).toList(),net.minecraft.core.Direction.byName(t.getString("Facing")));
        }
    }
    public record Secret(BlockPos entrance, net.minecraft.core.Direction facing, String blocks) {
        public Secret {
            if (entrance == null || facing == null || !(blocks.equals("wall") || blocks.equals("floor"))) throw new IllegalArgumentException("Invalid ruin secret");
            entrance = entrance.immutable();
        }
    }
    /** R57: a walled side vault (shell of from..to) sealed by a keystone door. */
    public record Vault(BlockPos from, BlockPos to, List<BlockPos> door) {}
    /** R44: a Life / Energy Cell pedestal spot (template-local, standing on solid ground). */
    public record Cell(BlockPos position, cn.wwl.orimod.progression.SpiritCells.Kind kind) {}
}
