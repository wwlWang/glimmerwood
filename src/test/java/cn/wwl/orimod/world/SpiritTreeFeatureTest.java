package cn.wwl.orimod.world;

import cn.wwl.orimod.world.SpiritTreeShape.Part;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritTreeFeatureTest {
    @Test void naturalSpiritTreesReserveTheirWholeCrownOutsideRuins() {
        var bounds = new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 60, 0, 10, 80, 10);
        assertTrue(RuinVegetationGuard.protects(bounds, new BlockPos(24, 90, 5), SpiritTreeShape.RADIUS));
        assertFalse(RuinVegetationGuard.protects(bounds, new BlockPos(25, 90, 5), SpiritTreeShape.RADIUS));
        assertFalse(RuinVegetationGuard.protects(bounds, new BlockPos(24, 90, 5)), "Vanilla margin must stay unchanged");
    }
    @Test void terrainAcceptsThreeBlocksOfReliefButNotFourOrWetGround() {
        int[] heights = new int[25]; Arrays.fill(heights, 64);
        assertTrue(SpiritTreeFeature.terrainAllowed(heights, true));
        heights[0] = 67; assertTrue(SpiritTreeFeature.terrainAllowed(heights, true));
        heights[0] = 68; assertFalse(SpiritTreeFeature.terrainAllowed(heights, true));
        heights[0] = 64; assertFalse(SpiritTreeFeature.terrainAllowed(heights, false));
        assertFalse(SpiritTreeFeature.terrainAllowed(new int[0], true));
    }
    @Test void replacementRequiresNaturalSoftBlocksAndRejectsFluidsOrBlockEntities() {
        for (int flags = 0; flags < 64; flags++) {
            boolean expected = (flags & 15) != 0 && (flags & 48) == 0;
            assertEquals(expected, SpiritTreeFeature.replaceable((flags & 1) != 0, (flags & 2) != 0,
                    (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0));
        }
    }
    @Test void shapeIsRepeatableVariedAndBoundedWithAnAccessibleHeart() {
        var shapes = new HashSet<Integer>();
        for (int seed = 0; seed < 64; seed++) {
            var origin = new BlockPos(seed * 37 - 1200, 70, seed * -23);
            var plan = SpiritTreeShape.plan(origin);
            assertEquals(plan, SpiritTreeShape.plan(origin));
            shapes.add(plan.blocks().hashCode());
            assertTrue(plan.trunkHeight() >= 16 && plan.trunkHeight() <= 18);
            assertTrue(plan.roots() >= 4 && plan.roots() <= 6);
            assertTrue(plan.branches() >= 3 && plan.branches() <= 4);
            assertTrue(plan.crowns() >= 6 && plan.crowns() <= 10);
            assertEquals(Part.HEART, plan.blocks().get(SpiritTreeShape.HEART));
            assertTrue(plan.blocks().values().stream().filter(v -> v == Part.LEAVES).count() > 200);
            assertTrue(plan.blocks().containsValue(Part.VINE_TIP));
            for (var p : plan.blocks().keySet()) {
                assertTrue(Math.abs(p.getX()) <= 14 && Math.abs(p.getZ()) <= 14, p.toString());
                assertTrue(p.getY() >= 0 && p.getY() < 30);
            }
            for (int x = -1; x <= 1; x++) for (int z = -5; z <= -3; z++) for (int y = 0; y < 3; y++)
                assertEquals(Part.AIR, plan.blocks().get(new BlockPos(x, y, z)));
        }
        assertEquals(64, shapes.size());
    }
    @Test void woodAndVinesStayConnectedInsteadOfFloatingDiagonalSegments() {
        var plan = SpiritTreeShape.plan(new BlockPos(1200, 80, -300));
        var wood = new HashSet<BlockPos>();
        plan.blocks().forEach((p, part) -> { if (part == Part.LOG_X || part == Part.LOG_Y || part == Part.LOG_Z) wood.add(p); });
        var queue = new ArrayDeque<BlockPos>();
        queue.add(BlockPos.ZERO); wood.remove(BlockPos.ZERO);
        while (!queue.isEmpty()) {
            var p = queue.remove();
            for (var dir : net.minecraft.core.Direction.values()) if (wood.remove(p.relative(dir))) queue.add(p.relative(dir));
        }
        assertTrue(wood.isEmpty(), "Disconnected wood: " + wood);
        plan.blocks().forEach((p, part) -> {
            if (part == Part.VINE || part == Part.VINE_TIP)
                assertTrue(Set.of(Part.LEAVES, Part.VINE).contains(plan.blocks().get(p.above())));
        });
    }
    @Test void domeHasDeepCrownsBroadShouldersAndATaperedTrunk() {
        for (int seed = 0; seed < 64; seed++) {
            var plan = SpiritTreeShape.plan(new BlockPos(seed * 37 - 1200, 70, seed * -23));
            var leaves = plan.blocks().entrySet().stream().filter(e -> e.getValue() == Part.LEAVES).map(Map.Entry::getKey).toList();
            var ys = leaves.stream().mapToInt(BlockPos::getY).summaryStatistics();
            assertTrue(ys.getMax() - ys.getMin() + 1 >= 6, "Shallow canopy: " + seed);
            assertTrue(leaves.stream().mapToDouble(p -> Math.hypot(p.getX(), p.getZ())).max().orElseThrow() >= 8, "Narrow canopy: " + seed);
            assertTrue(leaves.stream().allMatch(p -> Math.hypot(p.getX(), p.getZ()) <= 11), "Canopy exceeds 11-block radius");
            long base = plan.trunk().stream().filter(p -> p.getY() == 0).count();
            long middle = plan.trunk().stream().filter(p -> p.getY() == plan.trunkHeight() / 2).count();
            assertTrue(base >= middle * 2, "Untapered trunk: " + seed);
            int widthX = leaves.stream().mapToInt(BlockPos::getX).max().orElseThrow() - leaves.stream().mapToInt(BlockPos::getX).min().orElseThrow() + 1;
            int widthZ = leaves.stream().mapToInt(BlockPos::getZ).max().orElseThrow() - leaves.stream().mapToInt(BlockPos::getZ).min().orElseThrow() + 1;
            assertTrue(Math.max(widthX, widthZ) >= .9 * (ys.getMax() + 1), "Tree is too tall for its crown: " + seed + " width " + widthX + "/" + widthZ + " height " + (ys.getMax() + 1));
            double centreTop = leaves.stream().filter(p -> Math.hypot(p.getX(), p.getZ()) <= 4).mapToInt(BlockPos::getY).max().orElseThrow();
            double edgeTop = leaves.stream().filter(p -> Math.hypot(p.getX(), p.getZ()) >= 9).mapToInt(BlockPos::getY).max().orElseThrow();
            assertTrue(centreTop >= edgeTop + 1, "Dome centre does not rise above rim: " + seed);
        }
    }
    @Test void entireTreeIsFaceConnectedAndVinesStayOutsideEveryCrownEnvelope() {
        for (int seed = 0; seed < 64; seed++) {
            var plan = SpiritTreeShape.plan(new BlockPos(seed * 37 - 1200, 70, seed * -23));
            var remaining = new HashSet<BlockPos>();
            plan.blocks().forEach((p, part) -> { if (part != Part.AIR) remaining.add(p); });
            var queue = new ArrayDeque<BlockPos>(); queue.add(BlockPos.ZERO); remaining.remove(BlockPos.ZERO);
            while (!queue.isEmpty()) {
                var p = queue.remove();
                for (var dir : net.minecraft.core.Direction.values()) if (remaining.remove(p.relative(dir))) queue.add(p.relative(dir));
            }
            assertTrue(remaining.isEmpty(), "Disconnected shape at seed " + seed + ": " + remaining);
            plan.blocks().forEach((p, part) -> {
                if (part != Part.VINE && part != Part.VINE_TIP) return;
                assertFalse(plan.crownSpace().contains(p), "Vine inside crown: " + p);
                assertTrue(Set.of(Part.LEAVES, Part.VINE).contains(plan.blocks().get(p.above())), "Unsupported vine: " + p);
                assertEquals(part == Part.VINE, Set.of(Part.VINE, Part.VINE_TIP).contains(plan.blocks().getOrDefault(p.below(), Part.AIR)), "Wrong vine tip");
            });
        }
    }
    @Test void outerCrownCentresDifferEvenAcrossTheClosingPair() {
        for (int branches : List.of(3, 4)) for (int i = 0; i < branches; i++) {
            int offset = SpiritTreeShape.outerCrownOffset(branches, i);
            int next = SpiritTreeShape.outerCrownOffset(branches, (i + 1) % branches);
            assertTrue(Math.abs(offset) <= 3);
            assertTrue(Math.abs(offset - next) >= 2, "Adjacent crown centres share a layer");
        }
    }
    @Test void canopyUndersideIsUnevenAcrossItsHorizontalFootprint() throws Exception {
        var measurements = new ArrayList<String>();
        measurements.add("seed,x,y,z,minY,maxY,columns,modeY,modeCount,modePercent,rimMin,rimMax,rimCount,rimModeCount,rimModePercent");
        var assertions = new ArrayList<org.junit.jupiter.api.function.Executable>();
        for (int seed = 0; seed < 64; seed++) {
            var origin = new BlockPos(seed * 37 - 1200, 70, seed * -23);
            var plan = SpiritTreeShape.plan(origin);
            var underside = SpiritTreeShapeMetrics.underside(plan);
            var rim = SpiritTreeShapeMetrics.underside(plan, 6);
            measurements.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%d,%d,%d,%d,%.4f", seed,
                    origin.getX(), origin.getY(), origin.getZ(), underside.minY(), underside.maxY(),
                    underside.columns(), underside.modeY(), underside.modeCount(), underside.modeFraction() * 100,
                    rim.minY(), rim.maxY(), rim.columns(), rim.modeCount(), rim.modeFraction() * 100));
            int sample = seed;
            assertions.add(() -> {
                assertTrue(underside.maxY() - underside.minY() >= 4,
                        "Flat underside at seed " + sample + ": " + underside.histogram());
                assertTrue(underside.modeCount() * 100 <= underside.columns() * 35,
                        "More than 35% of leaf columns share one bottom at seed " + sample + ": " + underside.histogram());
            });
        }
        var report = Path.of(System.getProperty("orimod.projectDir", ".")).resolve("build/reports/spirit-tree-undersides.csv");
        Files.createDirectories(report.getParent()); Files.write(report, measurements);
        assertAll(assertions);
    }
    @Test void outerRimDoesNotHideAFlatShelfBehindIrregularInnerColumns() {
        var origins = new ArrayList<BlockPos>();
        // The actual rejected client screenshot: the all-column 35% rule missed this tree.
        origins.add(new BlockPos(3000, 120, 3000));
        for (int seed = 0; seed < 64; seed++) origins.add(new BlockPos(seed * 37 - 1200, 70, seed * -23));
        for (var origin : origins) {
            var plan = SpiritTreeShape.plan(origin);
            var all = SpiritTreeShapeMetrics.underside(plan);
            var rim = SpiritTreeShapeMetrics.underside(plan, 6);
            assertTrue(all.maxY() - all.minY() >= 4 && all.modeCount() * 100 <= all.columns() * 35,
                    "Whole canopy underside regressed: " + origin);
            assertTrue(rim.maxY() - rim.minY() >= 4, "Outer canopy is shallow: " + origin);
            // Exclude the trunk/inner dome, whose cavities had diluted the visually flat outer shelf.
            // Old screenshot: 50/157 = 31.85%; staggered lobes: 38/158 = 24.05%.
            assertTrue(rim.modeCount() * 100 <= rim.columns() * 30,
                    "More than 30% of outer columns share one bottom at " + origin + ": " + rim.histogram());
        }
    }
    @Test void luminousFoliageKeepsItsTransparentRenderLayersAndLeafTag() throws Exception {
        var root = Path.of(System.getProperty("orimod.projectDir", ".")).resolve("src/main/resources");
        for (String model : List.of("spirit_leaves", "spirit_vine", "spirit_vine_tip")) {
            var json = JsonParser.parseString(Files.readString(root.resolve("assets/orimod/models/block/" + model + ".json"))).getAsJsonObject();
            assertEquals(model.equals("spirit_leaves") ? "minecraft:cutout_mipped" : "minecraft:cutout", json.get("render_type").getAsString());
            var image = javax.imageio.ImageIO.read(root.resolve("assets/orimod/textures/block/" + model + ".png").toFile());
            int transparent = 0, visible = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 0) transparent++;
                else {
                    visible++;
                    assertTrue((pixel & 255) >= ((pixel >> 16) & 255), "Warm-coloured foliage");
                }
            }
            assertTrue(transparent > 30 && visible > 20, "Foliage needs both silhouettes and transparent gaps");
        }
        assertTrue(Files.readString(root.resolve("data/minecraft/tags/block/leaves.json")).contains("orimod:spirit_leaves"));
    }
    @Test void worldgenDataKeepsRarityAndBiomeScopeExplicit() throws Exception {
        var root = Path.of(System.getProperty("orimod.projectDir", ".")).resolve("src/main/resources/data/orimod");
        var placed = JsonParser.parseString(Files.readString(root.resolve("worldgen/placed_feature/spirit_tree.json"))).getAsJsonObject();
        assertEquals(220, placed.getAsJsonArray("placement").get(0).getAsJsonObject().get("chance").getAsInt());
        var biomes = Files.readString(root.resolve("tags/worldgen/biome/has_spirit_tree.json"));
        assertFalse(biomes.contains("lush_caves"));
        assertTrue(biomes.contains("minecraft:cherry_grove"));
        var modifier = JsonParser.parseString(Files.readString(root.resolve("neoforge/biome_modifier/spirit_tree.json"))).getAsJsonObject();
        assertEquals("local_modifications", modifier.get("step").getAsString(), "Spirit trees must precede vanilla vegetation");
        assertEquals("#orimod:has_spirit_tree", modifier.get("biomes").getAsString());
    }
}
