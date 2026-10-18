package cn.wwl.orimod.gametest;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * R16 / WORLD02: ruins on adversarial terrain found by the natural site audit — carver holes under the
 * landing, water at the path edge, fluids inside the route — checked with the same inspector the audit uses.
 */
@GameTestHolder("orimod_gametest")
public final class RuinBoundaryGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var kind : RuinKind.values()) {
            add(tests, kind.id + "_flat_site_is_clean", h -> {
                var site = Site.build(h, kind);
                var report = site.inspect();
                h.assertTrue(report.ok(), kind.id + " flat site issues " + report.issues());
            });
            add(tests, kind.id + "_landing_over_carved_hole", h -> {
                var site = new Site(h, kind);
                site.ground();
                site.fill(-2, 2, -12, -9, site.doorY - 8, site.doorY - 1, Blocks.AIR.defaultBlockState());
                site.place();
                var report = site.inspect();
                h.assertTrue(report.ok(), kind.id + " carved landing issues " + report.issues());
                h.assertValueEqual(report.metrics().get("landingDrop"), 0, "landing step");
            });
        }
        add(tests, "water_at_path_edge_is_allowed", h -> {
            var site = new Site(h, RuinKind.FOREST);
            site.ground();
            site.fill(-2, 2, -12, -11, site.doorY - 2, site.doorY - 1, Blocks.WATER.defaultBlockState());
            site.place();
            var report = site.inspect();
            h.assertTrue(report.ok(), "water two blocks from the landing broke the site " + report.issues());
            int water = (Integer) report.metrics().get("waterWithin6");
            h.assertTrue(water >= 1 && water <= 3, "water edge not measured: " + water);
        });
        add(tests, "water_on_landing_is_reported", h -> {
            var site = Site.build(h, RuinKind.FOREST);
            site.fill(0, 0, -9, -9, site.doorY - 1, site.doorY - 1, Blocks.WATER.defaultBlockState());
            var report = site.inspect();
            h.assertTrue(report.issues().contains("landing:water"), "flooded landing not reported " + report.issues());
        });
        for (var fluid : new BlockState[]{Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
            String name = fluid.is(Blocks.LAVA) ? "lava" : "water";
            add(tests, "cave_" + name + "_on_checkpoint_blocks_route", h -> {
                var site = Site.build(h, RuinKind.CAVE);
                var checkpoint = site.placement.point(RuinKind.CAVE.layout().checkpoints().get(1).position());
                h.getLevel().setBlock(BlockPos.containing(checkpoint), fluid, 2);
                var report = site.inspect();
                h.assertTrue(report.issues().contains("checkpoint:1"), name + " checkpoint not reported " + report.issues());
                if (fluid.is(Blocks.LAVA)) h.assertTrue(report.issues().stream().anyMatch(i -> i.startsWith("lava:")), "lava not counted");
                try (var c = Fixtures.connectedPlayer(h)) {
                    var player = c.player();
                    SpiritForm.toggle(player); player.setData(SpiritData.ABILITIES, 63);
                    player.getInventory().add(new ItemStack(OriItems.SPIRIT_BLADE.get()));
                    var shrine = site.placement.block(RuinKind.CAVE.layout().shrine());
                    player.setPos(site.placement.point(RuinKind.CAVE.layout().safe()));
                    SpiritChallenge.request(player, shrine, true);
                    h.assertTrue(SpiritChallenge.session(player) == null, "route with " + name + " on a checkpoint started");
                    SpiritChallenge.finish(player, "exit", false);
                }
            });
        }
        return tests;
    }

    /** A ruin with its approach on hand-built terrain, rotation NONE so the approach stays in the test room. */
    private static final class Site {
        final GameTestHelper h; final RuinKind kind; final RuinPlacement placement, doorway; final int doorY;

        Site(GameTestHelper h, RuinKind kind) {
            this.h = h; this.kind = kind;
            placement = RuinPlacement.atMinimum(kind, h.absolutePos(new BlockPos(12, 30, 14)), Rotation.NONE);
            doorway = RuinApproachPiece.doorway(kind, placement);
            doorY = doorway.origin().getY();
        }

        static Site build(GameTestHelper h, RuinKind kind) { var s = new Site(h, kind); s.ground(); s.place(); return s; }

        /** Flat stone whose first air block is the door floor height, so every terrain sample equals doorY. */
        void ground() { fill(-2, 2, -13, -1, doorY - 8, doorY - 1, Blocks.STONE.defaultBlockState()); }

        void fill(int x0, int x1, int z0, int z1, int y0, int y1, BlockState state) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                var column = doorway.block(new BlockPos(x, 0, z));
                for (int y = y0; y <= y1; y++) h.getLevel().setBlock(column.atY(y), state, 2);
            }
        }

        void place() {
            var level = h.getLevel();
            var piece = new RuinPiece(level.getStructureManager(), kind, placement);
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(3),
                    piece.getBoundingBox(), new ChunkPos(placement.origin()), BlockPos.ZERO);
            int[] terrain = new int[RuinApproachPiece.WIDTH * RuinApproachPiece.DEPTH];
            java.util.Arrays.fill(terrain, doorY);
            var approach = new RuinApproachPiece(doorway, terrain);
            approach.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(3),
                    approach.getBoundingBox(), new ChunkPos(doorway.origin()), BlockPos.ZERO);
        }

        RuinSiteInspector.Report inspect() { return RuinSiteInspector.inspect(h.getLevel(), kind, placement); }
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("ruin_boundary", "ruin_boundary." + name, "orimod_gametest:ruin_room", 100, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
