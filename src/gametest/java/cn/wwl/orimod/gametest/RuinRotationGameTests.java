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
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class RuinRotationGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var rotation : Rotation.values()) {
            for (var kind : RuinKind.values()) add(tests, kind.id + "_" + rotation.name(), h -> route(h, kind, rotation));
            add(tests, "approach_chunk_order_" + rotation.name(), h -> approach(h, rotation));
        }
        add(tests, "legacy_piece_nbt", RuinRotationGameTests::legacy);
        add(tests, "player_tree_growth_is_unchanged", h -> {
            h.assertFalse(RuinVegetationGuard.blocksTree(h.getLevel(), h.absolutePos(new BlockPos(12, 2, 12))), "Normal world tree growth was blocked");
            h.succeed();
        });
        return tests;
    }

    private static void route(GameTestHelper h, RuinKind kind, Rotation rotation) {
        var level = h.getLevel(); var context = StructurePieceSerializationContext.fromLevel(level);
        var placement = RuinPlacement.atMinimum(kind, h.absolutePos(new BlockPos(12, 2, 12)), rotation);
        var original = new RuinPiece(level.getStructureManager(), kind, placement);
        var piece = new RuinPiece(level.getStructureManager(), original.createTag(context));
        h.assertValueEqual(piece.placement(), placement, "Saved placement changed");
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(6),
                piece.getBoundingBox(), new ChunkPos(placement.origin()), BlockPos.ZERO);
        var shrine = placement.block(kind.layout().shrine());
        h.assertValueEqual(RuinPlacement.atShrine(kind, shrine, level.getBlockState(shrine)), placement, "Shrine did not preserve template rotation");
        var connection = Fixtures.connectedPlayer(h);
        var player = connection.player();
        SpiritForm.toggle(player); player.setData(SpiritData.ABILITIES, 63);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        Vec3 safe = placement.point(kind.layout().safe()); player.setPos(safe);
        try {
            SpiritChallenge.request(player, shrine, true);
            h.assertTrue(SpiritChallenge.session(player) != null, "Rotated route was rejected");
            var recovery = player.getData(SpiritData.CHALLENGE_RECOVERY);
            h.assertValueEqual(recovery.safe(), BlockPos.containing(safe), "Recovery marker points to unrotated safe point");
            player.setPos(safe.add(2, 1, 2));
            SpiritChallenge.finish(player, "exit", true);
            Fixtures.near(h, player.position().distanceTo(safe), 0, "Cancel safe return");
            player.setData(SpiritData.CHALLENGE_RECOVERY, recovery); player.setPos(safe.add(2, 1, 2));
            SpiritChallenge.tick(new PlayerTickEvent.Post(player));
            Fixtures.near(h, player.position().distanceTo(safe), 0, "Reload safe return");
            SpiritChallenge.request(player, shrine, true);
            var session = SpiritChallenge.session(player);
            h.assertTrue(session != null, "Second route did not start");
            var movement = player.getData(SpiritData.MOVEMENT);
            for (var checkpoint : kind.layout().checkpoints()) {
                player.setPos(placement.point(checkpoint.position())); player.setOnGround(true);
                switch (checkpoint.action()) {
                    case "jump" -> movement.acceptedJumps += checkpoint.count();
                    case "wall_jump" -> movement.acceptedWallJumps += checkpoint.count();
                    case "dash" -> movement.acceptedDashes += checkpoint.count();
                    case "bash" -> movement.acceptedBashes += checkpoint.count();
                    case "glide" -> movement.startedGlides += checkpoint.count();
                    case "grapple" -> movement.completedGrapples += checkpoint.count();
                    case "stomp" -> movement.stompImpacts += checkpoint.count();
                    case "blade" -> {
                        var target = level.getEntity(session.target);
                        h.assertTrue(target instanceof PracticeTarget, "Rotated blade target missing");
                        Fixtures.near(h, target.position().distanceTo(placement.point(checkpoint.position())), 0, "Target position");
                        h.assertTrue(target.hurt(player.damageSources().playerAttack(player), 30), "Owned target damage rejected");
                    }
                }
                SpiritChallenge.tick(new PlayerTickEvent.Post(player));
            }
            h.assertTrue(SpiritChallenge.session(player) == null, "Rotated route did not finish");
            h.assertValueEqual(player.getData(SpiritData.PROGRESS).light(), 30, "Completion reward");
            SpiritChallenge.finish(player, "exit", true);
            h.assertValueEqual(player.getData(SpiritData.PROGRESS).light(), 30, "Cleanup duplicated reward");
            level.setBlock(placement.block(kind.layout().critical().getFirst().position()), Blocks.AIR.defaultBlockState(), 2);
            player.setPos(safe); SpiritChallenge.request(player, shrine, true);
            h.assertTrue(SpiritChallenge.session(player) == null, "Broken rotated route started");
        } finally { SpiritChallenge.finish(player, "exit", false); connection.close(); }
    }

    private static void approach(GameTestHelper h, Rotation rotation) {
        var level = h.getLevel();
        var door = new RuinPlacement(h.absolutePos(new BlockPos(32, 10, 32)), rotation);
        int[] terrain = new int[45];
        for (int x = -2; x <= 2; x++) for (int z = -9; z <= -1; z++) {
            int height = door.origin().getY() - 3 + Math.floorMod(x + z, 3);
            terrain[(x + 2) * 9 + z + 9] = height;
            var column = door.block(new BlockPos(x, 0, z));
            for (int y = door.origin().getY() - 6; y < height; y++) level.setBlock(column.atY(y), Blocks.STONE.defaultBlockState(), 2);
            for (int y = height; y < height + 7; y++) level.setBlock(column.atY(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        var piece = new RuinApproachPiece(door, terrain);
        var box = piece.getBoundingBox();
        var initial = snapshot(h, box);
        var chunks = new ArrayList<ChunkPos>();
        for (int x = Math.floorDiv(box.minX(), 16); x <= Math.floorDiv(box.maxX(), 16); x++)
            for (int z = Math.floorDiv(box.minZ(), 16); z <= Math.floorDiv(box.maxZ(), 16); z++) chunks.add(new ChunkPos(x, z));
        placeChunks(h, piece, chunks);
        var forward = snapshot(h, box);
        h.assertFalse(forward.equals(initial), "Approach fixture did not place blocks");
        initial.forEach((pos, state) -> level.setBlock(pos, state, 2));
        Collections.reverse(chunks);
        var restored = new RuinApproachPiece(piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
        placeChunks(h, restored, chunks);
        h.assertValueEqual(snapshot(h, box), forward, "Chunk order changed approach blocks");
        for (int x = -2; x <= 2; x++) for (int z = -8; z <= -1; z++) {
            int ground = terrain[(x + 2) * 9];
            int floor = ground - 1 + Math.round((door.origin().getY() - ground) * (z + 8) / 7f);
            var column = door.block(new BlockPos(x, 0, z));
            h.assertFalse(level.getBlockState(column.atY(floor)).isAir(), "Missing path floor");
            for (int y = floor + 1; y <= floor + 3; y++) h.assertTrue(level.getBlockState(column.atY(y)).isAir(), "Blocked path clearance");
        }
    }

    private static void placeChunks(GameTestHelper h, RuinApproachPiece piece, List<ChunkPos> chunks) {
        var level = h.getLevel();
        for (var chunk : chunks) {
            var bounds = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
            var guarded = (WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> {
                if (args != null && args.length > 0 && args[0] instanceof BlockPos pos
                        && Set.of("getBlockState", "getFluidState", "setBlock").contains(method.getName()))
                    h.assertTrue(bounds.isInside(pos), "Approach accessed a neighboring chunk: " + method.getName() + " " + pos);
                try { return method.invoke(level, args); }
                catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
            });
            piece.postProcess(guarded, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(6), bounds, chunk, BlockPos.ZERO);
        }
    }

    private static Map<BlockPos, BlockState> snapshot(GameTestHelper h, BoundingBox box) {
        var result = new HashMap<BlockPos, BlockState>();
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            if (pos.getY() >= h.getLevel().getMinBuildHeight() && pos.getY() < h.getLevel().getMaxBuildHeight()) result.put(pos.immutable(), h.getLevel().getBlockState(pos));
        return result;
    }

    private static void legacy(GameTestHelper h) {
        var context = StructurePieceSerializationContext.fromLevel(h.getLevel());
        var origin = h.absolutePos(new BlockPos(12, 2, 12));
        var piece = new RuinPiece(h.getLevel().getStructureManager(), RuinKind.CAVE, new RuinPlacement(origin, Rotation.NONE));
        var tag = piece.createTag(context); tag.remove("Rot");
        var old = new RuinPiece(h.getLevel().getStructureManager(), tag);
        h.assertValueEqual(old.placement(), piece.placement(), "Old template start moved");
        var door = RuinApproachPiece.doorway(RuinKind.CAVE, old.placement());
        int[] heights = new int[45]; Arrays.fill(heights, door.origin().getY());
        var path = new RuinApproachPiece(door, heights); var oldTag = path.createTag(context);
        for (String key : new String[]{"DoorX", "DoorY", "DoorZ", "Terrain", "Rot"}) oldTag.remove(key);
        oldTag.putInt("CenterX", door.origin().getX()); oldTag.putInt("FrontZ", door.origin().getZ() - 8); oldTag.putInt("DoorwayGround", door.origin().getY());
        var saved = new RuinApproachPiece(oldTag).createTag(context);
        h.assertValueEqual(saved.getInt("DoorX"), door.origin().getX(), "Old approach X");
        h.assertValueEqual(saved.getInt("DoorY"), door.origin().getY(), "Old approach Y");
        h.assertValueEqual(saved.getInt("DoorZ"), door.origin().getZ(), "Old approach Z");
        h.assertValueEqual(saved.getString("Rot"), "NONE", "Old approach rotated");
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("ruins", "ruins." + name.toLowerCase(Locale.ROOT), "orimod_gametest:ruin_room", 60, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
