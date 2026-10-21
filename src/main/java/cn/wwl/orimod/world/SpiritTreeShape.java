package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import java.util.*;

/** R100: Ori's spreading spirit trees, adapted as a deterministic voxel plan before any world writes. */
public final class SpiritTreeShape {
    public static final int RADIUS = 14, CROWN_RADIUS = 11, MIN_HEIGHT = 16, MAX_HEIGHT = 18, MAX_ROOT_DROP = 3;
    public static final BlockPos HEART = new BlockPos(0, 0, -2);
    public enum Part { LOG_X, LOG_Y, LOG_Z, LEAVES, VINE, VINE_TIP, HEART, AIR }
    public record Plan(Map<BlockPos, Part> blocks, Set<BlockPos> crownSpace, Set<BlockPos> trunk,
                       int trunkHeight, int roots, int branches, int crowns) {}
    private record Crown(BlockPos centre, int rx, int ryUp, int ryDown, int rz) {}
    private SpiritTreeShape() {}

    public static Plan plan(BlockPos origin) {
        var random = new Random(origin.asLong() ^ 0x535049524954L);
        var blocks = new LinkedHashMap<BlockPos, Part>();
        var crownSpace = new HashSet<BlockPos>();
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        double bend = random.nextDouble() * Math.PI * 2;
        for (int y = 0; y < height; y++) {
            var centre = trunk(y, height, bend);
            // Broad root flare, rounded shoulders, then 3x3 and finally 2x2; not a long straight pillar.
            int min = y < 5 ? -2 : y < 10 ? -1 : 0, max = y < 5 ? 2 : 1;
            for (int x = min; x <= max; x++) for (int z = min; z <= max; z++) {
                if (y >= 3 && Math.abs(x) == 2 && Math.abs(z) == 2) continue;
                blocks.put(centre.offset(x, 0, z), Part.LOG_Y);
            }
        }
        var trunk = new HashSet<>(blocks.keySet());
        trunk.remove(HEART); // The root heart replaces one trunk voxel, so it is not part of the wood area.
        int roots = 5 + random.nextInt(2);
        double phase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < roots; i++) {
            double angle = phase + i * Math.PI * 2 / roots;
            int length = 4 + random.nextInt(3);
            var previous = BlockPos.ZERO;
            // Tall fins near the trunk taper into wide, low buttresses.
            for (int r = 1; r <= length; r++) {
                int x = (int) Math.round(Math.cos(angle) * r), z = (int) Math.round(Math.sin(angle) * r);
                int fin = Math.max(0, 5 - r);
                for (int y = 0; y <= fin; y++) {
                    blocks.put(new BlockPos(x, y, previous.getZ()), Part.LOG_Y);
                    blocks.put(new BlockPos(x, y, z), Math.abs(x) > Math.abs(z) ? Part.LOG_X : Part.LOG_Z);
                    if (r <= 3) blocks.put(new BlockPos(x + (Math.abs(z) > Math.abs(x) ? 1 : 0), y,
                            z + (Math.abs(x) >= Math.abs(z) ? 1 : 0)), Part.LOG_Y);
                }
                previous = new BlockPos(x, 0, z);
            }
        }
        int branches = 3 + random.nextInt(2);
        var crowns = new ArrayList<Crown>();
        for (int i = 0; i < branches; i++) {
            double angle = phase + i * Math.PI * 2 / branches + random.nextDouble() * .3;
            int startY = 7 + i % 3, reach = 7;
            // Alternate low/high shoulders around the tree, including the last-to-first pair. A height cap
            // previously flattened almost every branch-end crown onto the same level.
            int crownY = height - 4 + outerCrownOffset(branches, i);
            var tip = new BlockPos((int) Math.round(Math.cos(angle) * reach), crownY - 1,
                    (int) Math.round(Math.sin(angle) * reach));
            branch(blocks, trunk(startY, height, bend), tip);
            crowns.add(new Crown(tip.above(), 5, 3, 4, 5));
        }
        // Three inner shoulders and a high cap lift the centre into a dome. Each lobe has its own underside,
        // cavity and notch; outer lobes sit lower, leaving the thick ascending limbs readable from below.
        for (int i = 0; i < 3; i++) {
            double angle = phase + .65 + i * Math.PI * 2 / 3;
            var centre = new BlockPos((int) Math.round(Math.cos(angle) * 3.5), height - 2 + i % 2,
                    (int) Math.round(Math.sin(angle) * 3.5));
            branch(blocks, trunk(height - 5, height, bend), centre);
            int ryUp = 3 + random.nextInt(2);
            crowns.add(new Crown(centre, 4, ryUp, ryUp + 1, 4));
        }
        crowns.add(new Crown(trunk(height - 1, height, bend).above(), 4, 3, 4, 4));
        for (var c : crowns) crown(blocks, crownSpace, c, random);
        // Carve before connectivity cleanup: the entrance can sever the outward tip of a north-facing root.
        for (int x = -1; x <= 1; x++) for (int z = -5; z <= -3; z++) for (int y = 0; y < 3; y++)
            blocks.put(new BlockPos(x, y, z), Part.AIR);
        blocks.put(HEART, Part.HEART);
        pruneDetached(blocks);
        // Select exposed undersides, so vines attach to actual leaves and don't run through another crown.
        for (var c : crowns) {
            var centre = c.centre;
            int placed = 0;
            for (int attempt = 0; attempt < 24 && placed < 3; attempt++) {
                int x = centre.getX() + random.nextInt(7) - 3, z = centre.getZ() + random.nextInt(7) - 3;
                for (int y = centre.getY() - c.ryDown; y <= centre.getY(); y++) {
                    var leaf = new BlockPos(x, y, z);
                    if (blocks.get(leaf) != Part.LEAVES || blocks.containsKey(leaf.below())) continue;
                    int length = 2 + random.nextInt(4);
                    boolean clear = true;
                    for (int d = 1; d <= length; d++)
                        if (leaf.getY() - d < 0 || blocks.containsKey(leaf.below(d)) || crownSpace.contains(leaf.below(d))) clear = false;
                    if (!clear) break;
                    for (int d = 1; d <= length; d++) blocks.put(leaf.below(d), d == length ? Part.VINE_TIP : Part.VINE);
                    placed++;
                    break;
                }
            }
        }
        return new Plan(Collections.unmodifiableMap(blocks), Set.copyOf(crownSpace), Set.copyOf(trunk), height, roots, branches, crowns.size());
    }

    /** Three limbs: -2/0/+2; four: -3/+1/-1/+3. Every angular neighbour differs by at least two. */
    static int outerCrownOffset(int branches, int index) {
        int rank = branches == 4 ? index / 2 + (index % 2) * 2 : index;
        return 2 * rank - (branches - 1);
    }

    private static void pruneDetached(Map<BlockPos, Part> blocks) {
        var connected = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        pending.add(BlockPos.ZERO); connected.add(BlockPos.ZERO);
        while (!pending.isEmpty()) {
            var p = pending.remove();
            for (var direction : net.minecraft.core.Direction.values()) {
                var next = p.relative(direction);
                if (blocks.getOrDefault(next, Part.AIR) != Part.AIR && connected.add(next)) pending.add(next);
            }
        }
        // Keep the AIR entries: those positions still require preflight and clearing during feature placement.
        blocks.entrySet().removeIf(e -> e.getValue() != Part.AIR && !connected.contains(e.getKey()));
    }

    private static BlockPos trunk(int y, int height, double bend) {
        double sway = Math.pow((double) y / height, 2) * 2;
        return new BlockPos((int) Math.round(Math.cos(bend) * sway), y, (int) Math.round(Math.sin(bend) * sway));
    }

    private static void branch(Map<BlockPos, Part> blocks, BlockPos from, BlockPos to) {
        int steps = 4 * Math.max(Math.abs(to.getX() - from.getX()), Math.max(Math.abs(to.getY() - from.getY()), Math.abs(to.getZ() - from.getZ())));
        var axis = Math.abs(to.getX() - from.getX()) > Math.abs(to.getZ() - from.getZ()) ? Part.LOG_X : Part.LOG_Z;
        var previous = from;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var next = new BlockPos((int) Math.round(from.getX() + (to.getX() - from.getX()) * t),
                    (int) Math.round(from.getY() + (to.getY() - from.getY()) * t),
                    (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t));
            // Bridge each axis separately; diagonal branches remain face-connected.
            blocks.put(new BlockPos(next.getX(), previous.getY(), previous.getZ()), axis);
            blocks.put(new BlockPos(next.getX(), next.getY(), previous.getZ()), axis);
            blocks.put(next, axis);
            if (t < .75) {
                blocks.put(next.below(), axis);
                var side = Math.abs(to.getX() - from.getX()) > Math.abs(to.getZ() - from.getZ()) ? next.south() : next.east();
                blocks.put(side, axis);
                blocks.put(side.below(), axis);
            }
            previous = next;
        }
    }

    private static void crown(Map<BlockPos, Part> blocks, Set<BlockPos> space, Crown crown, Random random) {
        var centre = crown.centre;
        int rx = crown.rx, rz = crown.rz;
        double notch = random.nextDouble() * Math.PI * 2;
        for (int x = -rx; x <= rx; x++) for (int z = -rz; z <= rz; z++) for (int y = -crown.ryDown; y <= crown.ryUp; y++) {
            // A deeper lower half rounds the hanging lobes instead of joining shallow, coplanar undersides.
            int ry = y < 0 ? crown.ryDown : crown.ryUp;
            double d = (double) x * x / (rx * rx) + (double) y * y / (ry * ry) + (double) z * z / (rz * rz);
            if (d > 1 || Math.hypot(centre.getX() + x, centre.getZ() + z) > CROWN_RADIUS) continue;
            space.add(centre.offset(x, y, z)); // holes still belong to the clearance envelope
            if (d < .32) continue; // porous, hollow crown; the branch reaches the interior
            double a = Math.atan2(z, x);
            // Underside notches reveal the limbs without cutting the broad shoulders out of the silhouette.
            if (y < -1 && Math.cos(a - notch) > .9 && d > .48
                    || Math.abs(y) >= 2 && d > .72 && random.nextDouble() < .23) continue;
            blocks.putIfAbsent(centre.offset(x, y, z), Part.LEAVES);
        }
    }
}
