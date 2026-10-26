package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a generated ruin in the actual world: entry, approach, landing, safe point and every
 * checkpoint must be standable and dry, anchors present, route volumes clear, and no lava inside.
 * Used by the worldgen site audit and boundary GameTests; it never edits terrain.
 */
public final class RuinSiteInspector {
    private RuinSiteInspector() {}

    public record Report(List<String> issues, Map<String, Object> metrics) {
        public boolean ok() { return issues.isEmpty(); }
    }

    /** Player-sized standing check without an entity: dry feet/head, no hazards, collision only below. */
    public static boolean standable(Level level, Vec3 point) {
        var feet = BlockPos.containing(point);
        if (!level.isLoaded(feet) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        for (var pos : new BlockPos[]{feet.below(), feet, feet.above()}) {
            var block = level.getBlockState(pos);
            if (block.is(BlockTags.FIRE) || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS) || block.is(Blocks.WITHER_ROSE)
                    || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.LAVA)) return false;
        }
        var bounds = new AABB(point.x - .3, point.y + .001, point.z - .3, point.x + .3, point.y + 1.8, point.z + .3);
        return level.noCollision(null, bounds) && !level.noCollision(null, bounds.move(0, -.1, 0));
    }

    public static Report inspect(Level level, RuinKind kind, RuinPlacement placement) {
        return inspect(level,kind,kind.layout(),placement);
    }
    public static Report inspect(Level level, RuinKind kind, RuinKind.Layout layout, RuinPlacement placement) {
        var issues = new ArrayList<String>();
        var metrics = new LinkedHashMap<String, Object>();
        for (var anchor : layout.critical())
            if (!level.getBlockState(placement.block(anchor.position())).is(BuiltInRegistries.BLOCK.get(anchor.block())))
                issues.add("critical:" + anchor.block());
        for (int i = 0; i < layout.clearances().size(); i++) {
            var box = placement.clearance(layout.clearances().get(i)).deflate(.001);
            if (!level.noCollision(null, box)) issues.add("clearance:" + i + ":blocked");
            if (level.containsAnyLiquid(box)) issues.add("clearance:" + i + ":fluid");
        }
        if (!standable(level, placement.point(layout.entry()))) issues.add("entry");
        if (!standable(level, placement.point(layout.safe()))) issues.add("safe");
        for (int i = 0; i < layout.checkpoints().size(); i++)
            if (!standable(level, placement.point(layout.checkpoints().get(i).position()))) issues.add("checkpoint:" + i);

        // Approach: the three middle columns are the walkway; the outer two are trim.
        var doorway = RuinApproachPiece.doorway(kind, layout, placement);
        int doorY = doorway.origin().getY();
        Integer firstStep = null;
        for (int dx = -1; dx <= 1; dx++) for (int i = 0; i < 8; i++) {
            var column = doorway.block(new BlockPos(dx, 0, -8 + i));
            Integer step = null;
            for (int y = doorY + 9; y >= doorY - 24; y--) {
                var state = level.getBlockState(column.atY(y));
                if (state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICK_STAIRS)) { step = y; break; }
            }
            if (step == null) { issues.add("approach:missing:" + dx + ":" + i); continue; }
            if (dx == 0 && i == 0) firstStep = step;
            if (!standable(level, Vec3.atBottomCenterOf(column.atY(step + 1)))) issues.add("approach:blocked:" + dx + ":" + i);
        }
        // Landing: natural ground just outside the first step must be reachable within one block.
        if (firstStep != null) {
            var landing = doorway.block(new BlockPos(0, 0, -9));
            Integer ground = null;
            for (int y = firstStep + 3; y >= firstStep - 6; y--) {
                var pos = landing.atY(y);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) { ground = y; break; }
            }
            if (ground == null) issues.add("landing:void");
            else {
                metrics.put("landingDrop", firstStep - ground);
                if (!level.getFluidState(landing.atY(ground)).isEmpty() || !level.getFluidState(landing.atY(ground + 1)).isEmpty()) issues.add("landing:water");
                else if (Math.abs(firstStep - ground) > 1) issues.add("landing:step:" + (firstStep - ground));
                else if (!standable(level, Vec3.atBottomCenterOf(landing.atY(ground + 1)))) issues.add("landing:blocked");
            }
            metrics.put("firstStepY", firstStep);
        }
        // Water within 6 blocks of the landing, at path height: the "water edge" boundary category.
        var landing = doorway.block(new BlockPos(0, 0, -9));
        int nearestWater = -1;
        int base = firstStep == null ? doorY : firstStep;
        search:
        for (int r = 0; r <= 6; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            for (int dy = -2; dy <= 2; dy++)
                if (level.getFluidState(landing.offset(dx, base + dy - landing.getY(), dz)).is(FluidTags.WATER)) { nearestWater = r; break search; }
        }
        metrics.put("waterWithin6", nearestWater);
        // Lava anywhere in the template volume would flood or burn the route.
        var box = placement.blocks(BlockPos.ZERO, layout.size().offset(-1, -1, -1));
        int lava = 0;
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            if (level.getFluidState(pos).is(FluidTags.LAVA)) lava++;
        if (lava > 0) issues.add("lava:" + lava);
        metrics.put("entryY", BlockPos.containing(placement.point(layout.entry())).getY());
        metrics.put("originY", placement.origin().getY());
        return new Report(List.copyOf(issues), metrics);
    }
}
