package cn.wwl.orimod.world;

import cn.wwl.orimod.world.SpiritTreeShape.Part;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible review artifact from the actual Java shape function, not a second implementation. */
class SpiritTreeShapeReportTest {
    @Test void reportFiveFixedPositionSeeds() throws Exception {
        var output = new StringBuilder("# R100 shape statistics\n\nCoordinates are feature bases; PRNG seed = origin.asLong() XOR 0x535049524954.\n"
                + "Y is relative to the base. Height counts occupied layers (top Y + 1). Trunk excludes roots/branches/heart; whole tree excludes AIR.\n"
                + "Projection collapses Z into XY: H heart > # wood > * leaves > | vine. Areas count voxels in each horizontal XZ layer.\n\n");
        for (var origin : List.of(new BlockPos(0, 70, 0), new BlockPos(3000, 120, 3000), new BlockPos(2986, 120, 3000),
                new BlockPos(-1015, 70, -115), new BlockPos(1200, 80, -300))) {
            var plan = SpiritTreeShape.plan(origin);
            var all = plan.blocks().entrySet().stream().filter(e -> e.getValue() != Part.AIR).toList();
            var leaves = all.stream().filter(e -> e.getValue() == Part.LEAVES).map(Map.Entry::getKey).toList();
            var xs = leaves.stream().mapToInt(BlockPos::getX).summaryStatistics();
            var zs = leaves.stream().mapToInt(BlockPos::getZ).summaryStatistics();
            var ys = leaves.stream().mapToInt(BlockPos::getY).summaryStatistics();
            int top = all.stream().mapToInt(e -> e.getKey().getY()).max().orElseThrow();
            long logs = all.stream().filter(e -> wood(e.getValue())).count();
            long vines = all.stream().filter(e -> e.getValue() == Part.VINE || e.getValue() == Part.VINE_TIP).count();
            output.append("## ").append(origin.toShortString()).append("; seed ").append(origin.asLong() ^ 0x535049524954L).append("\n\n");
            output.append(String.format(Locale.ROOT, "Height **%d**, trunk height %d; crowns %d, main branches %d. Crown X %d..%d (**%d**), Z %d..%d (**%d**); bottom/top Y **%d / %d**. Logs/leaves/vines **%d / %d / %d**.\n\n",
                    top + 1, plan.trunkHeight(), plan.crowns(), plan.branches(), xs.getMin(), xs.getMax(), xs.getMax() - xs.getMin() + 1,
                    zs.getMin(), zs.getMax(), zs.getMax() - zs.getMin() + 1, ys.getMin(), ys.getMax(), logs, leaves.size(), vines));
            var underside = SpiritTreeShapeMetrics.underside(plan);
            output.append(String.format(Locale.ROOT,
                    "Lowest-leaf Y per XZ column: **%d** columns, Y **%d..%d** (range **%d**); mode Y **%d**, **%d/%d (%.2f%%)**.\n\n",
                    underside.columns(), underside.minY(), underside.maxY(), underside.maxY() - underside.minY(),
                    underside.modeY(), underside.modeCount(), underside.columns(), underside.modeFraction() * 100));
            var rim = SpiritTreeShapeMetrics.underside(plan, 6);
            output.append(String.format(Locale.ROOT, "Outer rim (radius >= 6): Y %d..%d, mode %d/%d (%.2f%%).\n\n",
                    rim.minY(), rim.maxY(), rim.modeCount(), rim.columns(), rim.modeFraction() * 100));
            output.append("| Lowest leaf Y | XZ columns |\n|---:|---:|\n");
            underside.histogram().forEach((y, count) -> output.append("| ").append(y).append(" | ").append(count).append(" |\n"));
            output.append('\n');
            output.append("| Y | Trunk area | Whole-tree area |\n|---:|---:|---:|\n");
            for (int y = 0; y <= top; y++) {
                int layer = y;
                output.append("| ").append(y).append(" | ").append(plan.trunk().stream().filter(p -> p.getY() == layer).count())
                        .append(" | ").append(all.stream().filter(e -> e.getKey().getY() == layer).count()).append(" |\n");
            }
            output.append("\n```text\n");
            for (int y = top; y >= 0; y--) {
                output.append(String.format(Locale.ROOT, "%02d ", y));
                for (int x = -14; x <= 14; x++) {
                    int rank = 0;
                    for (int z = -14; z <= 14; z++) {
                        var part = plan.blocks().getOrDefault(new BlockPos(x, y, z), Part.AIR);
                        rank = Math.max(rank, part == Part.HEART ? 4 : wood(part) ? 3 : part == Part.LEAVES ? 2 : part == Part.VINE || part == Part.VINE_TIP ? 1 : 0);
                    }
                    output.append(" |*#H".charAt(rank));
                }
                output.append('\n');
            }
            output.append("   -14           0            14\n```\n\n");
            assertTrue(logs > 100 && vines > 0 && !leaves.isEmpty());
        }
        var path = Path.of(System.getProperty("orimod.projectDir", ".")).resolve("build/reports/spirit-tree-shapes.md");
        Files.createDirectories(path.getParent()); Files.writeString(path, output);
    }
    private static boolean wood(Part p) { return p == Part.LOG_X || p == Part.LOG_Y || p == Part.LOG_Z; }
}
