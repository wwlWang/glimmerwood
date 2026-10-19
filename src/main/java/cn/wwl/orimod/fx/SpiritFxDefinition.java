package cn.wwl.orimod.fx;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;

/**
 * FX02: runtime look of one spirit effect, exported from {@code art/source/effects/spirit/<kind>.json}
 * by {@code scripts/export-spirit-fx.py} into {@code assets/orimod/spirit_fx/<kind>.json}.
 * Particle counts stay in {@link FxBudget}; a definition splits that budget over its layers in order, so the
 * must-show (essential) particles always land in layer 1.
 *
 * <p>R32 layer shapes: {@code burst} (gaussian cloud), {@code ring} (circle in the horizontal plane, optional
 * outward speed), {@code arc} (sweep in front of the source's facing), {@code line} (trail segment behind the
 * source's motion, tapering), {@code column} (rising pillar). Spirit particles take colour, size and lifetime.
 */
public record SpiritFxDefinition(SpiritFxKind kind, List<Layer> layers) {
    public static final List<String> SHAPES = List.of("burst", "ring", "arc", "line", "column");

    public record Layer(String particle, double share, double spreadX, double spreadY, double spreadZ, double speed,
                        String shape, double radius, double length, double outward, int rgb, float size, int lifetime) {
        public static Layer simple(String particle, double share, double sx, double sy, double sz, double speed) {
            return new Layer(particle, share, sx, sy, sz, speed, "burst", 0, 0, 0, 0xC8F4FF, .25f, 16);
        }
    }

    /** Position of particle {@code index} of {@code total}: its layer, and its place {@code j} of {@code n} inside it. */
    public record Slot(Layer layer, int j, int n) {
        public double fraction() { return n <= 1 ? 0 : (double) j / (n - 1); }
    }

    public static String fileName(SpiritFxKind kind) { return kind.name().toLowerCase(Locale.ROOT); }

    /** Built-in look used when a resource pack removes or breaks a definition (the R18 table). */
    public static SpiritFxDefinition fallback(SpiritFxKind kind) {
        String particle = switch (kind) {
            case BLADE_HIT -> "minecraft:crit";
            case DASH, DOUBLE_JUMP, WALL_JUMP -> "minecraft:cloud";
            case BASH -> "minecraft:firework";
            case GRAPPLE_HOOK, GRAPPLE_TETHER -> "minecraft:electric_spark";
            case FORM_SWITCH -> "minecraft:glow";
            default -> "minecraft:end_rod";
        };
        return new SpiritFxDefinition(kind, List.of(Layer.simple(particle, 1, kind.spreadX(), kind.spreadY(), kind.spreadZ(), kind.speed())));
    }

    /** Strict parse; throws IllegalArgumentException naming the problem so a broken file falls back visibly in the log. */
    public static SpiritFxDefinition parse(SpiritFxKind kind, JsonObject json) {
        require(json.has("format") && json.get("format").getAsInt() == 1, "format must be 1");
        require(fileName(kind).equals(json.get("kind").getAsString()), "kind must be " + fileName(kind));
        var array = json.getAsJsonArray("layers");
        require(array != null && !array.isEmpty() && array.size() <= 4, "1-4 layers required");
        var layers = new java.util.ArrayList<Layer>();
        double total = 0;
        for (var element : array) {
            var o = element.getAsJsonObject();
            String particle = o.get("particle").getAsString();
            require(particle.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "bad particle id " + particle);
            double share = o.get("share").getAsDouble(), speed = o.get("speed").getAsDouble();
            var spread = o.getAsJsonArray("spread");
            require(spread.size() == 3, "spread needs 3 values");
            double sx = spread.get(0).getAsDouble(), sy = spread.get(1).getAsDouble(), sz = spread.get(2).getAsDouble();
            require(share > 0 && share <= 1 && speed >= 0 && speed <= 2 && sx >= 0 && sy >= 0 && sz >= 0 && sx <= 8 && sy <= 8 && sz <= 8,
                    "share (0,1], speed [0,2], spread [0,8]");
            String shape = o.has("shape") ? o.get("shape").getAsString() : "burst";
            require(SHAPES.contains(shape), "unknown shape " + shape);
            double radius = num(o, "radius", 0), length = num(o, "length", 0), outward = num(o, "outward", 0);
            require(radius >= 0 && radius <= 6 && length >= 0 && length <= 6 && Math.abs(outward) <= 2, "radius/length [0,6], outward [-2,2]");
            String color = o.has("color") ? o.get("color").getAsString() : "#c8f4ff";
            require(color.matches("#[0-9a-f]{6}"), "color must be #rrggbb lower case");
            float size = (float) num(o, "size", .25); int lifetime = (int) num(o, "lifetime", 16);
            require(size >= .02 && size <= 2 && lifetime >= 1 && lifetime <= 120, "size [0.02,2], lifetime [1,120]");
            total += share;
            layers.add(new Layer(particle, share, sx, sy, sz, speed, shape, radius, length, outward, Integer.parseInt(color.substring(1), 16), size, lifetime));
        }
        require(Math.abs(total - 1) < 1e-6, "layer shares must sum to 1");
        return new SpiritFxDefinition(kind, List.copyOf(layers));
    }

    /** Layers take consecutive blocks of the budget by share (rounded at cumulative boundaries), essential particles first. */
    public Slot slot(int index, int total) {
        double sum = 0; int start = 0;
        for (int l = 0; l < layers.size(); l++) {
            sum += layers.get(l).share();
            int end = l == layers.size() - 1 ? total : Math.max((int) Math.round(sum * total), l == 0 ? Math.min(1, total) : start);
            if (index < end || l == layers.size() - 1) return new Slot(layers.get(l), index - start, Math.max(1, end - start));
            start = end;
        }
        throw new IllegalStateException();
    }

    public Layer layerFor(int index, int total) { return slot(index, total).layer(); }

    private static double num(JsonObject o, String key, double fallback) { return o.has(key) ? o.get(key).getAsDouble() : fallback; }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
}
