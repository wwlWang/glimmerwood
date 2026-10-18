package cn.wwl.orimod.recipe;

import cn.wwl.orimod.OriMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.DifficultyChangeEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.function.Supplier;

public final class OriRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> TYPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, OriMod.MOD_ID);
    public static final Supplier<RecipeSerializer<PeacefulShapelessRecipe>> PEACEFUL_SHAPELESS = TYPES.register("peaceful_shapeless", PeacefulShapelessRecipe.Serializer::new);
    private OriRecipes() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static void difficultyChanged(DifficultyChangeEvent event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || event.getDifficulty() == event.getOldDifficulty()) return;
        // This event precedes the difficulty assignment. Invalidate cached results before another click can take them.
        if (event.getDifficulty() != Difficulty.PEACEFUL) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                invalidate(player.inventoryMenu);
                if (player.containerMenu != player.inventoryMenu) invalidate(player.containerMenu);
            }
        }
        // Recompute after the new difficulty is installed, including when a filled grid becomes craftable.
        server.tell(new TickTask(server.getTickCount(), () -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.inventoryMenu.slotsChanged(player.getInventory());
                if (player.containerMenu instanceof CraftingMenu menu) menu.slotsChanged(player.getInventory());
            }
        }));
    }

    private static void invalidate(AbstractContainerMenu menu) {
        if (!(menu instanceof InventoryMenu || menu instanceof CraftingMenu)) return;
        var slot = menu.getSlot(0);
        if (slot.hasItem() && slot.container instanceof ResultContainer result && result.getRecipeUsed() != null
                && result.getRecipeUsed().value() instanceof PeacefulShapelessRecipe) {
            slot.set(ItemStack.EMPTY);
            menu.broadcastChanges();
        }
    }
}
