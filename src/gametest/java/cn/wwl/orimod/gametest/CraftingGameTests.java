package cn.wwl.orimod.gametest;

import cn.wwl.orimod.item.OriItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_gametest")
public final class CraftingGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (boolean table : new boolean[]{false, true}) {
            String grid = table ? "table" : "inventory";
            for (var difficulty : Difficulty.values()) {
                add(tests, grid + "_" + difficulty.getKey(), (helper, player) -> {
                    helper.getLevel().getServer().setDifficulty(difficulty, true);
                    var menu = menu(helper, player, table); fill(menu);
                    boolean allowed = difficulty == Difficulty.PEACEFUL;
                    helper.assertTrue(menu.getSlot(0).hasItem() == allowed, "Crafting output does not respect difficulty");
                    menu.clicked(0, 0, ClickType.PICKUP, player);
                    if (allowed) {
                        helper.assertTrue(menu.getCarried().is(OriItems.SPIRIT_LIGHT), "Peaceful result is not Spirit Light");
                        helper.assertValueEqual(menu.getCarried().getCount(), 4, "Peaceful output count");
                        for (int slot = 1; slot <= 3; slot++) helper.assertFalse(menu.getSlot(slot).hasItem(), "Crafting did not consume ingredient");
                    } else {
                        helper.assertTrue(menu.getCarried().isEmpty(), "Normal difficulty took a result");
                        for (int slot = 1; slot <= 3; slot++) helper.assertValueEqual(menu.getSlot(slot).getItem().getCount(), 1, "Rejected craft consumed ingredient");
                    }
                });
            }
            for (var click : new ClickType[]{ClickType.PICKUP, ClickType.QUICK_MOVE}) {
                add(tests, grid + "_cached_output_" + click.name().toLowerCase(), (helper, player) -> {
                    var server = helper.getLevel().getServer(); server.setDifficulty(Difficulty.PEACEFUL, true);
                    var menu = menu(helper, player, table); fill(menu);
                    helper.assertTrue(menu.getSlot(0).getItem().is(OriItems.SPIRIT_LIGHT), "Peaceful fixture has no result");
                    server.setDifficulty(Difficulty.NORMAL, true);
                    menu.clicked(0, 0, click, player);
                    helper.assertTrue(menu.getCarried().isEmpty(), "Difficulty switch left a takeable cached result");
                    helper.assertValueEqual(player.getInventory().countItem(OriItems.SPIRIT_LIGHT.get()), 0, "Shift click took stale Spirit Light");
                    for (int slot = 1; slot <= 3; slot++) helper.assertValueEqual(menu.getSlot(slot).getItem().getCount(), 1, "Stale click consumed ingredient");
                });
            }
            add(tests, grid + "_extra_ingredient_rejected", (helper, player) -> {
                helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
                var menu = menu(helper, player, table); fill(menu);
                menu.getSlot(4).set(new ItemStack(Items.SUGAR));
                helper.assertFalse(menu.getSlot(0).hasItem(), "Extra ingredient accepted");
            });
            add(tests, grid + "_wrong_ingredient_rejected", (helper, player) -> {
                helper.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
                var menu = menu(helper, player, table); fill(menu);
                menu.getSlot(2).set(new ItemStack(Items.STICK));
                helper.assertFalse(menu.getSlot(0).hasItem(), "Wrong ingredient accepted");
            });
        }
        return tests;
    }

    private static AbstractContainerMenu menu(GameTestHelper helper, ServerPlayer player, boolean table) {
        if (!table) return player.inventoryMenu;
        var pos = new BlockPos(4, 2, 5); helper.setBlock(pos, Blocks.CRAFTING_TABLE);
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
        player.containerMenu = menu;
        return menu;
    }

    private static void fill(AbstractContainerMenu menu) {
        menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT));
        menu.getSlot(2).set(new ItemStack(Items.TORCH));
        menu.getSlot(3).set(new ItemStack(Items.SUGAR));
    }

    private static void add(Collection<TestFunction> tests, String name, BiConsumer<GameTestHelper, ServerPlayer> run) {
        tests.add(new TestFunction("crafting", "crafting." + name, "orimod_gametest:room", 30, 0, true, helper -> {
            var server = helper.getLevel().getServer();
            var originalDifficulty = helper.getLevel().getDifficulty();
            try (var fixture = Fixtures.connectedPlayer(helper)) { run.accept(helper, fixture.player()); helper.succeed(); }
            finally { server.setDifficulty(originalDifficulty, true); }
        }));
    }
}
