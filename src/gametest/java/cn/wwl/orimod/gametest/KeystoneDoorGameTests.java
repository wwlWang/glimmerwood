package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.KeystoneDoorBlock;
import cn.wwl.orimod.block.KeystoneDoorPart;
import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Item-level regressions: expectations describe the footprint independently of the door implementation. */
@GameTestHolder("orimod_gametest")
public final class KeystoneDoorGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var facing : Direction.Plane.HORIZONTAL) add(tests, "placement_nine_cells_" + facing.getName(), h -> {
            var origin = origin(h); prepare(h, origin, facing);
            var player = player(h, facing); var stack = doorStack(2);
            h.assertTrue(place(player, origin, stack).consumesAction(), "Door placement refused");
            h.assertValueEqual(stack.getCount(), 1, "Placement must consume exactly one door item");
            assertDoor(h, origin, facing);
            for (var cell : footprint(origin, facing)) {
                var state = h.getLevel().getBlockState(cell);
                h.assertTrue(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING), "Placed door has no facing");
                h.assertValueEqual(state.getValue(BlockStateProperties.HORIZONTAL_FACING), facing, "Door facing disagrees with player");
                h.assertTrue(state.getProperties().stream().anyMatch(p -> p.getName().equals("part")), "Placed door has no part identity");
                h.assertTrue(h.getLevel().getBlockEntity(cell) != null, "Door part lost its rendering block entity");
                h.assertValueEqual(KeystoneDoorBlock.door(h.getLevel(), cell).size(), 9, "A part cannot resolve its complete door");
            }
        });
        for (int index = 0; index < 9; index++) {
            if (index == 1) continue; // The vanilla placement context already validates the clicked anchor.
            final int blocked = index;
            add(tests, "preflight_obstruction_" + index, h -> {
                var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
                var cells = footprint(origin, facing); var obstacle = cells.get(blocked);
                // The anchor is replaceable; every other member must be checked before the first write.
                h.getLevel().setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
                var stack = doorStack(2);
                h.assertFalse(place(player(h, facing), origin, stack).consumesAction(), "Obstructed door reported successful placement");
                h.assertValueEqual(stack.getCount(), 2, "Failed placement consumed an item");
                for (var cell : cells) h.assertTrue(h.getLevel().getBlockState(cell).is(cell.equals(obstacle) ? Blocks.STONE : Blocks.AIR), "Failed placement left a partial door or overwrote the obstacle");
            });
        }
        add(tests, "preflight_entity_in_upper_side_cell", h -> {
            var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
            var occupied = origin.east().above(2);
            var cow = EntityType.COW.create(h.getLevel());
            cow.setPos(Vec3.atBottomCenterOf(occupied)); cow.setNoAi(true); h.getLevel().addFreshEntity(cow);
            try {
                var stack = doorStack(2);
                h.assertFalse(place(player(h, facing), origin, stack).consumesAction(), "Upper cell entity was ignored");
                h.assertValueEqual(stack.getCount(), 2, "Entity rejection consumed an item");
                for (var cell : footprint(origin, facing)) h.assertTrue(h.getLevel().isEmptyBlock(cell), "Entity rejection left a partial door");
            } finally { cow.discard(); }
        });
        for (int index = 0; index < 9; index++) {
            final int removed = index;
            add(tests, "removing_part_cleans_whole_door_" + index, h -> {
                var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
                h.assertTrue(place(player(h, facing), origin, doorStack(1)).consumesAction(), "Door placement refused");
                assertDoor(h, origin, facing);
                h.getLevel().destroyBlock(footprint(origin, facing).get(removed), true);
                for (var cell : footprint(origin, facing)) {
                    h.assertTrue(h.getLevel().isEmptyBlock(cell), "Removing a part left another part behind");
                    h.assertTrue(h.getLevel().getBlockEntity(cell) == null, "Removing a door left a block entity behind");
                }
                int drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(4)).stream()
                        .filter(e -> e.getItem().is(OriBlocks.KEYSTONE_DOOR.get().asItem())).mapToInt(e -> e.getItem().getCount()).sum();
                h.assertTrue(drops <= 1, "One door dropped multiple items");
            });
        }
        add(tests, "adjacent_placed_doors_keep_separate_keys_and_removal", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var origin = origin(h); var other = origin.east(3); var facing = Direction.NORTH;
                prepare(h, origin, facing); prepare(h, other, facing);
                var player = c.player(); player.setPos(h.absoluteVec(new Vec3(2, 3, 2))); player.setYRot(facing.toYRot());
                h.assertTrue(place(player, origin, doorStack(1)).consumesAction(), "First door refused");
                h.assertTrue(place(player, other, doorStack(1)).consumesAction(), "Adjacent door refused");
                assertDoor(h, origin, facing); assertDoor(h, other, facing);
                var keys = new ItemStack(OriItems.KEYSTONE.get(), 2);
                h.assertValueEqual(KeystoneDoorBlock.insert(player, origin.east().above(2), keys), 1, "First key refused at a non-anchor part");
                for (var cell : footprint(other, facing)) h.assertValueEqual(h.getLevel().getBlockState(cell).getValue(KeystoneDoorBlock.FILLED), 0, "Adjacent door received the first door's key");
                h.assertValueEqual(KeystoneDoorBlock.insert(player, origin.west().above(), keys), 0, "Second key refused at another part");
                for (var cell : footprint(origin, facing)) h.assertTrue(h.getLevel().isEmptyBlock(cell), "Opened door still blocks passage");
                assertDoor(h, other, facing);
            }
        });
        add(tests, "legacy_default_keeps_bfs_and_survival_permissions", h -> {
            var origin = origin(h); var level = h.getLevel();
            var cells = List.of(origin, origin.east(), origin.above(), origin.east().above());
            for (var cell : cells) level.setBlock(cell, OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(), 3);
            h.assertValueEqual(KeystoneDoorBlock.door(level, origin).size(), 4, "Default/template blocks lost legacy BFS grouping");
            h.assertValueEqual(level.getBlockState(origin).getDestroySpeed(level, origin), -1f, "Survival bypassed keystone requirement");
            level.destroyBlock(origin, true);
            for (var cell : cells.subList(1, cells.size())) h.assertTrue(level.getBlockState(cell).is(OriBlocks.KEYSTONE_DOOR), "Legacy single-cell removal behavior changed");
        });
        add(tests, "placed_door_does_not_join_touching_legacy_cells", h -> {
            var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
            var legacy = origin.east(2); h.getLevel().setBlock(legacy, OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(), 3);
            h.assertTrue(place(player(h, facing), origin, doorStack(1)).consumesAction(), "Door next to legacy cell refused");
            assertDoor(h, origin, facing);
            h.assertValueEqual(KeystoneDoorBlock.door(h.getLevel(), origin).size(), 9, "Placed door absorbed legacy cells");
            h.assertValueEqual(KeystoneDoorBlock.door(h.getLevel(), legacy).size(), 1, "Legacy BFS absorbed placed door");
            h.getLevel().destroyBlock(origin.above(), false);
            h.assertTrue(h.getLevel().getBlockState(legacy).is(OriBlocks.KEYSTONE_DOOR), "Placed removal erased legacy neighbor");
        });
        add(tests, "cancelled_multiplace_restores_cells_and_item", h -> {
            var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
            var player = player(h, facing); var stack = doorStack(2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            int[] snapshots = {0};
            Consumer<BlockEvent.EntityMultiPlaceEvent> cancel = event -> {
                if (event.getEntity() == player) { snapshots[0] = event.getReplacedBlockSnapshots().size(); event.setCanceled(true); }
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(origin), Direction.UP, origin.below(), false)));
                h.assertFalse(result.consumesAction(), "Cancelled placement still succeeded");
                h.assertValueEqual(snapshots[0], 9, "Placement protection did not receive all nine cells");
                h.assertValueEqual(stack.getCount(), 2, "Cancelled placement consumed a door item");
                for (var cell : footprint(origin, facing)) {
                    h.assertTrue(h.getLevel().isEmptyBlock(cell), "Cancelled placement left a partial door");
                    h.assertTrue(h.getLevel().getBlockEntity(cell) == null, "Cancelled placement retained a door block entity");
                }
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        });
        add(tests, "preflight_build_ceiling", h -> {
            var origin = origin(h).atY(h.getLevel().getMaxBuildHeight() - 2); var facing = Direction.NORTH;
            var support = origin.below(); var old = h.getLevel().getBlockState(support);
            h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            try {
                var stack = doorStack(2);
                h.assertFalse(place(player(h, facing), origin, stack).consumesAction(), "Door extended above the build ceiling");
                h.assertValueEqual(stack.getCount(), 2, "Build ceiling rejection consumed an item");
                for (var cell : footprint(origin, facing)) if (!h.getLevel().isOutsideBuildHeight(cell))
                    h.assertTrue(h.getLevel().isEmptyBlock(cell), "Build ceiling rejection left a partial door");
            } finally { h.getLevel().setBlock(support, old, 3); }
        });
        add(tests, "preflight_border_rejects_side_cells", h -> {
            var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
            var border = h.getLevel().getWorldBorder(); var old = border.createSettings();
            try {
                border.setCenter(origin.getX() + .5, origin.getZ() + .5); border.setSize(1);
                var stack = doorStack(2);
                h.assertFalse(place(player(h, facing), origin, stack).consumesAction(), "Door crossed world border");
                h.assertValueEqual(stack.getCount(), 2, "Border rejection consumed an item");
                for (var cell : footprint(origin, facing)) h.assertTrue(h.getLevel().isEmptyBlock(cell), "Border rejection left a partial door");
            } finally { border.applySettings(old); }
        });
        add(tests, "preflight_requires_player_edit_permission", h -> {
            var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
            var player = player(h, facing); player.getAbilities().mayBuild = false; var stack = doorStack(2);
            h.assertFalse(place(player, origin, stack).consumesAction(), "Door bypassed restricted player editing");
            h.assertValueEqual(stack.getCount(), 2, "Permission rejection consumed an item");
            for (var cell : footprint(origin, facing)) h.assertTrue(h.getLevel().isEmptyBlock(cell), "Permission rejection left a partial door");
        });
        add(tests, "item_components_cannot_forge_part_facing_or_key_progress", h -> {
            var origin = origin(h); var facing = Direction.EAST; prepare(h, origin, facing);
            var player = player(h, facing); var stack = doorStack(2);
            var properties = new BlockItemStateProperties(Map.of("part", "legacy", "facing", "north", "filled", "1"));
            stack.set(DataComponents.BLOCK_STATE, properties);
            h.assertTrue(place(player, origin, stack).consumesAction(), "Tagged door placement refused");
            h.assertValueEqual(stack.get(DataComponents.BLOCK_STATE), properties, "Placement changed the remaining item's components");
            assertDoor(h, origin, facing);
            for (var cell : footprint(origin, facing)) {
                var state = h.getLevel().getBlockState(cell);
                h.assertValueEqual(state.getValue(KeystoneDoorBlock.FACING), facing, "Item component forged facing");
                h.assertTrue(state.getValue(KeystoneDoorBlock.PART) != KeystoneDoorPart.LEGACY, "Item component forged a legacy part");
                h.assertValueEqual(state.getValue(KeystoneDoorBlock.FILLED), 0, "Item component supplied free key progress");
                h.assertValueEqual(state.getDestroyProgress(player, h.getLevel(), cell), 0f, "Placed door became survival-mineable");
                h.assertValueEqual(KeystoneDoorBlock.door(h.getLevel(), cell).size(), 9, "Tagged item broke ownership");
            }
        });
        add(tests, "old_palette_without_part_and_facing_decodes_as_legacy", h -> {
            var tag = new CompoundTag(); tag.putString("Name", "orimod:keystone_door");
            var properties = new CompoundTag(); properties.putString("filled", "1"); tag.put("Properties", properties);
            var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
            h.assertValueEqual(state.getValue(KeystoneDoorBlock.PART), KeystoneDoorPart.LEGACY, "Old saved palette changed placement mode");
            h.assertValueEqual(state.getValue(KeystoneDoorBlock.FILLED), 1, "Old saved key progress changed");
        });
        for (int index = 0; index < 9; index++) {
            final int part = index;
            for (boolean pull : List.of(false, true)) tests.add(new TestFunction("keystone_door",
                    "keystone_door." + (pull ? "sticky_pull" : "piston_push") + "_preserves_part_" + index,
                    "orimod_gametest:room", 80, 0, true, h -> piston(h, part, pull)));
            for (String command : List.of("setblock", "fill")) add(tests, "command_" + command + "_replaces_part_" + index, h -> {
                var origin = origin(h); var facing = Direction.NORTH; prepare(h, origin, facing);
                h.assertTrue(place(player(h, facing), origin, doorStack(1)).consumesAction(), "Door placement refused");
                assertDoor(h, origin, facing);
                var legacy = origin.east(2); var legacyState = OriBlocks.KEYSTONE_DOOR.get().defaultBlockState().setValue(KeystoneDoorBlock.FILLED, 1);
                h.getLevel().setBlock(legacy, legacyState, 3);
                var legacyEntity = h.getLevel().getBlockEntity(legacy);
                var target = footprint(origin, facing).get(part);
                String xyz = target.getX() + " " + target.getY() + " " + target.getZ();
                String input = command + " " + xyz + (command.equals("fill") ? " " + xyz : "") + " minecraft:gold_block replace";
                boolean[] succeeded = {false}; int[] result = {0};
                var source = h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput()
                        .withCallback((success, value) -> { succeeded[0] = success; result[0] = value; });
                h.getLevel().getServer().getCommands().performPrefixedCommand(source, input);
                h.assertTrue(succeeded[0], "Replacement command failed: " + input);
                h.assertValueEqual(result[0], 1, "Command did not replace exactly the targeted cell");
                for (var cell : footprint(origin, facing)) {
                    h.assertTrue(h.getLevel().getBlockState(cell).is(cell.equals(target) ? Blocks.GOLD_BLOCK : Blocks.AIR),
                            "Command lost its replacement or left an owned door part behind");
                    h.assertTrue(h.getLevel().getBlockEntity(cell) == null, "Command replacement retained a door block entity");
                }
                h.assertValueEqual(h.getLevel().getBlockState(legacy), legacyState, "Command cleanup changed a touching legacy cell");
                h.assertTrue(legacyEntity != null && h.getLevel().getBlockEntity(legacy) == legacyEntity, "Command cleanup replaced the legacy block entity");
                h.assertValueEqual(KeystoneDoorBlock.door(h.getLevel(), legacy).size(), 1, "Legacy neighbor gained an orphan member");
            });
        }
        return tests;
    }

    private static void piston(GameTestHelper h, int part, boolean pull) {
        var level = h.getLevel(); var origin = origin(h); var facing = Direction.NORTH;
        prepare(h, origin, facing);
        h.assertTrue(place(player(h, facing), origin, doorStack(1)).consumesAction(), "Door placement refused");
        assertDoor(h, origin, facing);
        var before = new LinkedHashMap<BlockPos, BlockState>();
        for (var cell : footprint(origin, facing)) {
            var state = level.getBlockState(cell).setValue(KeystoneDoorBlock.FILLED, 1);
            level.setBlock(cell, state, 3); before.put(cell, state);
        }
        var target = footprint(origin, facing).get(part); var direction = Direction.SOUTH;
        var pistonPos = target.north(pull ? 2 : 1); var power = pistonPos.north();
        var piston = pull ? Blocks.STICKY_PISTON : Blocks.PISTON;
        var pistonState = piston.defaultBlockState().setValue(PistonBaseBlock.FACING, direction).setValue(PistonBaseBlock.EXTENDED, pull);
        // Build an extended, powered sticky piston before removing its power, or an unpowered normal piston before powering it.
        // Known-shape writes avoid queuing a duplicate neighbor-triggered movement; the real block event below performs the attempt.
        level.setBlock(power, (pull ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState(), 18);
        level.setBlock(pistonPos, pistonState, 18);
        if (pull) level.setBlock(pistonPos.south(), Blocks.PISTON_HEAD.defaultBlockState()
                .setValue(PistonHeadBlock.FACING, direction).setValue(PistonHeadBlock.TYPE, PistonType.STICKY), 18);
        level.setBlock(power, (pull ? Blocks.AIR : Blocks.REDSTONE_BLOCK).defaultBlockState(), 18);
        var resolver = new PistonStructureResolver(level, pistonPos, direction, !pull);
        h.assertFalse(resolver.resolve(), "Piston resolver accepted a keystone door part");
        h.assertTrue(resolver.getToPush().isEmpty() && resolver.getToDestroy().isEmpty(), "Piston resolver scheduled door movement or destruction");
        int[] movementEvents = {0};
        Consumer<PistonEvent.Pre> observe = event -> {
            if (event.getLevel() == level && event.getPos().equals(pistonPos)
                    && event.getPistonMoveType() == (pull ? PistonEvent.PistonMoveType.RETRACT : PistonEvent.PistonMoveType.EXTEND)) movementEvents[0]++;
        };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            boolean moved = level.getBlockState(pistonPos).triggerEvent(level, pistonPos, pull ? 1 : 0, direction.get3DDataValue());
            h.assertValueEqual(movementEvents[0], 1, "Fixture never entered the real piston movement event");
            h.assertValueEqual(moved, pull, "Blocked push should fail; sticky piston should retract without pulling the door");
        } finally { NeoForge.EVENT_BUS.unregister(observe); }
        assertDoorSnapshot(h, before);
        h.runAfterDelay(5, () -> {
            assertDoorSnapshot(h, before);
            var after = level.getBlockState(pistonPos);
            h.assertTrue(after.is(piston) && !after.getValue(PistonBaseBlock.EXTENDED), "Piston did not settle in its unextended state");
            if (pull) h.assertTrue(level.isEmptyBlock(pistonPos.south()), "Sticky piston pulled a door part into the former head cell");
            h.succeed();
        });
    }

    private static void assertDoorSnapshot(GameTestHelper h, Map<BlockPos, BlockState> before) {
        before.forEach((cell, state) -> {
            h.assertValueEqual(h.getLevel().getBlockState(cell), state, "Piston changed a door part or its saved properties");
            h.assertTrue(h.getLevel().getBlockEntity(cell) != null, "Piston removed a door block entity");
            h.assertValueEqual(KeystoneDoorBlock.door(h.getLevel(), cell).size(), 9, "Piston broke door ownership");
        });
    }

    private static BlockPos origin(GameTestHelper h) { return h.absolutePos(new BlockPos(6, 3, 6)); }
    private static List<BlockPos> footprint(BlockPos origin, Direction facing) {
        var cells = new ArrayList<BlockPos>();
        for (int y = 0; y < 3; y++) for (int width = -1; width <= 1; width++)
            cells.add(origin.above(y).relative(facing.getClockWise(), width));
        return cells;
    }
    private static void prepare(GameTestHelper h, BlockPos origin, Direction facing) {
        for (var cell : footprint(origin, facing)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        for (int width = -1; width <= 1; width++) h.getLevel().setBlock(origin.below().relative(facing.getClockWise(), width), Blocks.STONE.defaultBlockState(), 3);
    }
    private static ServerPlayer player(GameTestHelper h, Direction facing) {
        var player = Fixtures.player(h); player.setPos(h.absoluteVec(new Vec3(2, 3, 2))); player.setYRot(facing.toYRot()); return player;
    }
    private static ItemStack doorStack(int count) { return new ItemStack(OriBlocks.KEYSTONE_DOOR.get().asItem(), count); }
    private static InteractionResult place(ServerPlayer player, BlockPos origin, ItemStack stack) {
        return ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atBottomCenterOf(origin), Direction.UP, origin.below(), false)));
    }
    private static void assertDoor(GameTestHelper h, BlockPos origin, Direction facing) {
        for (var cell : footprint(origin, facing)) h.assertTrue(h.getLevel().getBlockState(cell).is(OriBlocks.KEYSTONE_DOOR), "Door item did not place all nine cells: " + cell.subtract(origin));
    }
    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("keystone_door", "keystone_door." + name, "orimod_gametest:room", 80, 0, true, h -> { run.accept(h); h.succeed(); }));
    }
}
