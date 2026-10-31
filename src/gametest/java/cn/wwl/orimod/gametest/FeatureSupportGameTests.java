package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.*;
import java.util.function.Consumer;

/** Accepted surface plans must survive later carvers. Fixtures keep the original shallow preflight strict. */
@GameTestHolder("orimod_p5_worldgen")
public final class FeatureSupportGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        int index = 0;
        for (var site : FeatureSiteStructure.Site.values()) for (String cavity : List.of("shallow_water", "deep_water", "dry_cave")) {
            var base = new BlockPos(12000 + index++ * 96, 100, 12000);
            add(tests, site.id() + "_" + cavity, h -> cavity(h, base, site, cavity));
        }
        add(tests, "seed116555_tree_carver_water", h -> recorded(h, FeatureSiteStructure.Site.SPIRIT_TREE,
                new BlockPos(-2332, 67, -132), new BlockPos(-2333, 62, -133), 39));
        add(tests, "seed116555_blight_noise_water", h -> recorded(h, FeatureSiteStructure.Site.BLIGHTED_GROUNDS,
                new BlockPos(-340, 67, 1348), new BlockPos(-347, 62, 1358), 29));
        add(tests, "surface_water_still_rejected", h -> {
            var base = new BlockPos(12800, 100, 12000);
            try (var fixture = new Fixture(h.getLevel(), base)) {
                fixture.set(base.below(), Blocks.WATER.defaultBlockState());
                for (var site : FeatureSiteStructure.Site.values())
                    h.assertFalse(Plan.check(site, h.getLevel(), base, 5).allowed(), "Surface lake accepted: " + site);
                h.succeed();
            }
        });
        return tests;
    }

    private static void cavity(GameTestHelper h, BlockPos base, FeatureSiteStructure.Site site, String cavity) {
        var fixture = new Fixture(h.getLevel(), base);
        try {
            var plan = Plan.check(site, h.getLevel(), base, 5);
            h.assertTrue(plan.allowed(), "Dry surface fixture rejected: " + plan.reason());
            var foot = site == FeatureSiteStructure.Site.SPIRIT_TREE ? plan.writes().entrySet().stream()
                    .filter(e -> e.getValue().is(OriBlocks.SPIRIT_LOG) && e.getKey().getY() == base.getY())
                    .map(e -> e.getKey().below()).min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow()
                    : plan.writes().entrySet().stream().filter(e -> e.getValue().is(OriBlocks.BLIGHTED_SOIL))
                    .map(e -> e.getKey().below()).min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
            int depth = cavity.equals("deep_water") ? 56 : 8;
            var fill = cavity.equals("dry_cave") ? Blocks.AIR : Blocks.WATER;
            for (int d = 0; d < depth; d++) fixture.set(foot.below(d), fill.defaultBlockState());
            // An adjacent source gets normal fluid ticks against the solid replacement column.
            if (fill == Blocks.WATER) for (int d = 1; d < depth; d++) fixture.set(foot.below(d).east(), fill.defaultBlockState());
            fixture.set(foot.below(depth), Blocks.STONE.defaultBlockState());
            h.assertValueEqual(plan.place(h.getLevel(), site), "OK", "Underground cavity rejected the accepted plan");
            int length = Math.min(depth, 48);
            verifyColumn(h, foot, length, site);
            h.assertTrue(h.getLevel().getBlockState(foot.below(length)).is(depth > length ? Blocks.WATER : Blocks.STONE),
                    "Root must end at its bed or exactly the 48-block limit");
            finishAfterFluidTicks(h, fixture, plan, () -> verifyColumn(h, foot, length, site));
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    private static void recorded(GameTestHelper h, FeatureSiteStructure.Site site, BlockPos origin, BlockPos water, int bedY) {
        var level = h.getLevel();
        var settings = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomes = MultiNoiseBiomeSource.createFromPreset(level.registryAccess().registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator = new NoiseBasedChunkGenerator(biomes, settings);
        var random = RandomState.create(settings.value(), level.registryAccess().lookupOrThrow(Registries.NOISE), 116555);
        var structure = (FeatureSiteStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath("orimod", site.id()));
        var context = new Structure.GenerationContext(level.registryAccess(), generator, biomes, random,
                level.getStructureManager(), 116555, new ChunkPos(origin), LevelHeightAccessor.create(-64, 384), structure.biomes()::contains);
        var accepted = ((Optional<?>) call(structure, "choose", new Class<?>[]{Structure.GenerationContext.class}, context))
                .orElseThrow(() -> new AssertionError("Recorded start no longer accepted"));
        var plan = new Plan(call(accepted, "plan", new Class<?>[0]));
        h.assertValueEqual(plan.base(), origin, "Recorded start moved");
        h.assertValueEqual(plan.submergedSupportStarts().contains(water), site == FeatureSiteStructure.Site.BLIGHTED_GROUNDS,
                "Noise-visible water must be recorded; the tree's carver-only water is absent from noise");
        var fixture = new Fixture(level, origin);
        try {
            var root = site == FeatureSiteStructure.Site.SPIRIT_TREE ? OriBlocks.SPIRIT_LOG.get() : OriBlocks.BLIGHTED_SOIL.get();
            var lowest = plan.writes().entrySet().stream().filter(e -> e.getKey().getX() == water.getX() && e.getKey().getZ() == water.getZ() && e.getValue().is(root))
                    .map(Map.Entry::getKey).min(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
            // Actual FULL-world columns captured from the failed density run, seed 116555:
            // tree: stone at 39, water 40..62, air 63..67; blight: stone 29, water 30..62, air 63, grass 64.
            // Only this recorded carver column is overlaid; neighbouring terrain is a controlled dry fixture.
            for (int y = bedY; y < lowest.getY(); y++) {
                var state = y == bedY ? Blocks.STONE : y <= water.getY() ? Blocks.WATER : Blocks.AIR;
                fixture.set(new BlockPos(water.getX(), y, water.getZ()), state.defaultBlockState());
            }
            h.assertValueEqual(plan.place(level, site), "OK", "Real failed origin still cannot place");
            var foot = lowest.below(); int length = foot.getY() - bedY;
            verifyColumn(h, foot, length, site);
            h.assertTrue(level.getBlockState(foot.below(length)).is(Blocks.STONE), "Recorded bed replaced");
            finishAfterFluidTicks(h, fixture, plan, () -> verifyColumn(h, foot, length, site));
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    private static void verifyColumn(GameTestHelper h, BlockPos foot, int length, FeatureSiteStructure.Site site) {
        var root = site == FeatureSiteStructure.Site.SPIRIT_TREE ? OriBlocks.SPIRIT_LOG.get() : Blocks.DIRT;
        for (int d = 0; d < length; d++) h.assertTrue(h.getLevel().getBlockState(foot.below(d)).is(root), "Disconnected support at " + foot.below(d));
        h.assertTrue(h.getLevel().getBlockState(foot.above()).isFaceSturdy(h.getLevel(), foot.above(), Direction.DOWN), "Support disconnected from surface");
    }
    private static void finishAfterFluidTicks(GameTestHelper h, Fixture fixture, Plan plan, Runnable check) {
        var level = h.getLevel();
        // Remove ecology bookkeeping so this worldgen test observes fluid updates, not subsequent growth.
        BlightData.get(level).removeCore(plan.base());
        for (var p : fixture.old.keySet()) if (level.getBlockState(p).is(Blocks.WATER)) level.scheduleTick(p, Fluids.WATER, 1);
        h.runAfterDelay(60, () -> {
            try {
                check.run();
                for (var p : BlockPos.betweenClosed(plan.base().offset(-14, 0, -14), plan.base().offset(14, 1, 14)))
                    h.assertFalse(level.getBlockState(p).is(Blocks.WATER), "Underground water leaked onto surface at " + p);
                h.succeed();
            } finally { fixture.close(); }
        });
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try { var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private record Plan(Object value) {
        static Plan check(FeatureSiteStructure.Site site, ServerLevel level, BlockPos base, long seed) {
            try {
                var terrain = Class.forName("cn.wwl.orimod.world.FeatureTerrain");
                var world = terrain.getDeclaredMethod("world", WorldGenLevel.class); world.setAccessible(true);
                var method = Class.forName("cn.wwl.orimod.world.FeatureSitePlan").getDeclaredMethod("check", FeatureSiteStructure.Site.class, terrain, BlockPos.class, long.class);
                method.setAccessible(true); return new Plan(method.invoke(null, site, world.invoke(null, level), base, seed));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        boolean allowed() { return (boolean) call(value, "allowed", new Class<?>[0]); }
        String reason() { return (String) call(value, "reason", new Class<?>[0]); }
        BlockPos base() { return (BlockPos) call(value, "base", new Class<?>[0]); }
        @SuppressWarnings("unchecked") Map<BlockPos, BlockState> writes() { return (Map<BlockPos, BlockState>) call(value, "writes", new Class<?>[0]); }
        @SuppressWarnings("unchecked") Set<BlockPos> submergedSupportStarts() { return (Set<BlockPos>) call(value, "submergedSupportStarts", new Class<?>[0]); }
        String place(ServerLevel level, FeatureSiteStructure.Site site) { return (String) call(value, "place", new Class<?>[]{WorldGenLevel.class, FeatureSiteStructure.Site.class}, level, site); }
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final Map<BlockPos, BlockState> old = new LinkedHashMap<>();
        final Set<ChunkPos> forced = new HashSet<>();
        Fixture(ServerLevel level, BlockPos base) {
            this.level = level;
            for (int x = (base.getX() - 16) >> 4; x <= (base.getX() + 16) >> 4; x++)
                for (int z = (base.getZ() - 16) >> 4; z <= (base.getZ() + 16) >> 4; z++) {
                    level.getChunk(x, z);
                    if (!level.getForcedChunks().contains(ChunkPos.asLong(x, z))) { level.setChunkForced(x, z, true); forced.add(new ChunkPos(x, z)); }
                }
            for (var p : BlockPos.betweenClosed(base.offset(-15, -60, -15), base.offset(15, 45, 15)))
                set(p, (p.getY() < base.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        void set(BlockPos pos, BlockState state) { old.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, state, 18); }
        public void close() { old.forEach((p, s) -> level.setBlock(p, s, 18)); forced.forEach(p -> level.setChunkForced(p.x, p.z, false)); }
    }
    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("p5_support", "p5_support." + name, "orimod_p5_worldgen:platform_room", 200, 0, true, run));
    }
}
