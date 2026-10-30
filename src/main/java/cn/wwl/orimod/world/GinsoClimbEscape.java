package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.Ability;
import net.minecraft.core.BlockPos;
import java.util.*;
import static cn.wwl.orimod.world.GinsoEscapeLayout.*;

/** Escape 2: the whole ability ascent, followed by short grapple transfers onto the surviving crown fork. */
public final class GinsoClimbEscape {
    public static final int VERSION = 2;
    public static final List<Ability> REQUIRED = List.of(Ability.CLIMB, Ability.BASH, Ability.GRAPPLE, Ability.GLIDE);
    private record Key(long seed, int turn) {}
    public record Built(Layout layout, List<GinsoCourses.Hop> crownHops) {}
    private static final Map<Key, Built> CACHE = new LinkedHashMap<>();
    private GinsoClimbEscape() {}
    public static List<Ability> missing(int abilities) { return REQUIRED.stream().filter(a -> (abilities & a.bit) == 0).toList(); }
    public static synchronized Built forTree(long seed, int turn, GinsoShape shape) {
        var key = new Key(seed, Math.floorMod(turn, 4));
        var existing = CACHE.get(key); if (existing != null) return existing;
        // Use the frozen locator's proven native fork endpoint, without generating its long walking helix.
        var legacy = GinsoEscapeLayout.forTree(seed, turn, shape);
        var endpoint = legacy.path().getLast();
        var blocks = new HashMap<BlockPos, Byte>();
        var path = new ArrayList<Point>();
        var route = new ArrayList<Box>();
        var flood = new ArrayList<Box>();
        var crown = new ArrayList<GinsoCourses.Hop>();
        route.add(new Box(-4, 5, -4, 5, 98, 5));
        // Candidate water region inside the real course lining; solid cells are clipped by flood geometry.
        flood.add(new Box(-4, 1, -4, 5, 98, 5));
        append(path, GinsoClimbCourse.SAFE, 0);
        for (var hop : GinsoClimbCourse.layout().hops()) {
            append(path, hop.start(), path.size()); append(path, hop.goal(), path.size());
        }
        var start = new BlockPos(0, 94, 0);
        // Rise clear of the last jumping shaft before turning toward the native fork.
        var upper = new BlockPos(0, 98, 2);
        double distance = Math.sqrt(endpoint.x() * endpoint.x() + Math.pow(endpoint.z()-2,2) + Math.pow(endpoint.feet() - 98, 2));
        int count = Math.max(3, (int)Math.ceil(distance / 4));
        var stations = new ArrayList<BlockPos>(); stations.add(start); stations.add(upper);
        for (int i = 1; i <= count; i++) {
            double t = i / (double)count;
            stations.add(new BlockPos((int)Math.round(endpoint.x() * t), (int)Math.round(98 + (endpoint.feet() - 98) * t), (int)Math.round(2 + (endpoint.z()-2) * t)));
        }
        // A transverse rib holds the first crown landing from the lining, behind its grapple approach.
        for (int x=-5;x<=5;x++) blocks.put(new BlockPos(x,96,3),GinsoShape.WOOD);
        for (int i = 1; i < stations.size(); i++) {
            var a = stations.get(i - 1); var b = stations.get(i);
            // A continuous tapered root carries each landing; the transfers are short ability hops.
            int samples = (int)Math.ceil(Math.sqrt(a.distSqr(b)) * 3);
            for (int j = 0; j <= samples; j++) {
                double t = j / (double)samples;
                int x = (int)Math.round(a.getX() + (b.getX() - a.getX()) * t);
                int y = (int)Math.round(a.getY() + (b.getY() - a.getY()) * t);
                int z = (int)Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if(i>1)blocks.put(new BlockPos(x + dx, y - 3, z + dz), GinsoShape.WOOD);
                    for (int dy = 0; dy < 4; dy++) blocks.put(new BlockPos(x + dx, y + dy, z + dz), GinsoShape.AIR);
                }
                var box = new Box(x - 1, y - 1, z - 1, x + 2, y + 4, z + 2);
                route.add(box); flood.add(new Box(x - 1, y - 2, z - 1, x + 2, y + 3, z + 2));
            }
            // Keep the flower outside the arrival deck, like the interior lesson's open mouth.
            // A flower over a solid deck is hidden by its underside and stops the incoming grapple.
            int ax=a.getX()-b.getX(),az=a.getZ()-b.getZ();
            var anchor=b.offset(Math.abs(ax)>=Math.abs(az)?Integer.signum(ax)*2:0,0,
                    Math.abs(az)>Math.abs(ax)?Integer.signum(az)*2:0);
            crown.add(new GinsoCourses.Hop("crown", a.getY() - 1, b.getY() - 1, 0, a, b, anchor, GinsoInterior.FLOWER));
            append(path, b, path.size());
        }
        // Restore platform surfaces after shared clearance cuts. A flower sits at each arrival mouth.
        for (int i = 0; i < stations.size(); i++) {
            var p = stations.get(i);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                blocks.put(p.offset(dx, -1, dz), TallGinsoInterior.CUSHION);
                for (int dy = 0; dy < 3; dy++) blocks.put(p.offset(dx, dy, dz), GinsoShape.AIR);
            }
        }
        // A fork bending back over the centre can lay a later root across the first vertical
        // grapple shaft (rotation 1). Keep its one-block body/aim clearance after all supports.
        for(int y=start.getY();y<=upper.getY();y++)blocks.put(new BlockPos(0,y,0),GinsoShape.AIR);
        for(var hop:crown)blocks.put(hop.interaction(),GinsoInterior.FLOWER);
        // Never overwrite the incoming jump's shaft, half-step lip or overhead clearance below the top deck.
        blocks.keySet().removeIf(p -> p.getY()<94);
        var end = stations.getLast();
        var exit = new Box(end.getX() - 1, end.getY(), end.getZ() - 1, end.getX() + 2, end.getY() + 3, end.getZ() + 2);
        var layout = new Layout(blocks, path, route, flood, exit, shape.height, path.size() - stations.size(), path.size() - 1,
                seed, key.turn(), List.of(), new Audit(0, 0, 0, 0, 0));
        var result = new Built(layout, List.copyOf(crown));
        if (CACHE.size() >= 16) CACHE.remove(CACHE.keySet().iterator().next());
        CACHE.put(key, result); return result;
    }
    private static void append(List<Point> path, BlockPos p, int action) {
        double distance = 0;
        if (!path.isEmpty()) {
            var last = path.getLast();
            distance = last.distance() + Math.sqrt(Math.pow(p.getX() - last.x(), 2) + Math.pow(p.getY() - last.feet(), 2) + Math.pow(p.getZ() - last.z(), 2));
        }
        path.add(new Point(p.getX(), p.getY(), p.getZ(), distance, action));
    }
}
