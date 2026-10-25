package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.KeystoneDoorBlock;
import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.item.KeystoneDoorItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.util.function.BiPredicate;

/** Exercise the real level/snapshot transaction, including a writer that mutates and then reports failure. */
@GameTestHolder("orimod_gametest")
public final class KeystoneDoorPlacementGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (boolean capture : List.of(false, true)) for (String fault : List.of("before_sixth", "after_sixth", "exception_after_sixth", "invalidates_first"))
            tests.add(new TestFunction("keystone_door", "keystone_door.rollback_" + fault + "_capture_" + capture,
                    "orimod_gametest:room", 80, 0, true, h -> { rollback(h, capture, fault); h.succeed(); }));
        return tests;
    }

    private static void rollback(GameTestHelper h, boolean capture, String fault) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(6, 3, 6));
        var old = new LinkedHashMap<BlockPos, BlockState>();
        for (int y = 0; y < 3; y++) for (int x = -1; x <= 1; x++) {
            var pos = origin.offset(x, y, 0); var state = (x == -1 ? Blocks.WATER : Blocks.AIR).defaultBlockState();
            level.setBlock(pos, state, 18); old.put(pos, state);
        }
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "door-rollback"));
        player.setPos(h.absoluteVec(new Vec3(2, 3, 2))); player.setYRot(Direction.NORTH.toYRot());
        var stack = new ItemStack(OriBlocks.KEYSTONE_DOOR.get().asItem(), 2);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atBottomCenterOf(origin), Direction.UP, origin.below(), false));
        var state = OriBlocks.KEYSTONE_DOOR.get().getStateForPlacement(context);
        boolean previousCapture = level.captureBlockSnapshots;
        int snapshotsBefore = level.capturedBlockSnapshots.size(); int[] writes = {0}; boolean[] threw = {false};
        var failure = new IllegalStateException("injected partial placement failure");
        level.captureBlockSnapshots = capture;
        try {
            boolean result = false;
            try {
                result = placeParts(context, state, (pos, placed) -> {
                    int attempt = ++writes[0];
                    if (attempt == 6 && fault.equals("before_sixth")) return false;
                    boolean changed = level.setBlock(pos, placed, 3);
                    if (attempt == 6 && fault.equals("after_sixth")) return false;
                    if (attempt == 6 && fault.equals("exception_after_sixth")) throw failure;
                    if (attempt == 9 && fault.equals("invalidates_first")) level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
                    return changed;
                });
            } catch (IllegalStateException exception) {
                if (exception != failure) throw exception;
                threw[0] = true;
            }
            h.assertFalse(result, "Partial placement was accepted");
            h.assertValueEqual(writes[0], fault.equals("invalidates_first") ? 9 : 6, "Fixture did not reach the injected write failure");
            h.assertValueEqual(threw[0], fault.equals("exception_after_sixth"), "Write exception was swallowed or invented");
            old.forEach((pos, previous) -> {
                h.assertValueEqual(level.getBlockState(pos), previous, "Rollback lost a replaced water/air cell");
                h.assertTrue(level.getBlockEntity(pos) == null, "Rollback retained a door block entity");
            });
            h.assertValueEqual(stack.getCount(), 2, "Failed transaction consumed an item");
            h.assertValueEqual(level.capturedBlockSnapshots.size(), snapshotsBefore, "Failed transaction leaked snapshots into its caller");
            h.assertValueEqual(level.captureBlockSnapshots, capture, "Rollback changed caller's capture state");
            h.assertFalse(level.restoringBlockSnapshots, "Rollback left the level in restoration mode");
        } finally { level.captureBlockSnapshots = previousCapture; }
    }

    private static boolean placeParts(BlockPlaceContext context, BlockState state, BiPredicate<BlockPos, BlockState> writer) {
        try {
            var method = KeystoneDoorItem.class.getDeclaredMethod("placeParts", BlockPlaceContext.class, BlockState.class, BiPredicate.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, context, state, writer);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            if (exception.getCause() instanceof Error cause) throw cause;
            throw new AssertionError(exception.getCause());
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
