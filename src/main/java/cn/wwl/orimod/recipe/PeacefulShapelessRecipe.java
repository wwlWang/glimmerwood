package cn.wwl.orimod.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A normal data-driven shapeless recipe whose result is available only on Peaceful. */
public record PeacefulShapelessRecipe(ShapelessRecipe delegate) implements CraftingRecipe {
    @Override public boolean matches(CraftingInput input, Level level) {
        return level.getDifficulty() == Difficulty.PEACEFUL && delegate.matches(input, level);
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return delegate.assemble(input, registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return delegate.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return delegate.getResultItem(registries); }
    @Override public NonNullList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public RecipeSerializer<?> getSerializer() { return OriRecipes.PEACEFUL_SHAPELESS.get(); }

    public static final class Serializer implements RecipeSerializer<PeacefulShapelessRecipe> {
        private static final MapCodec<PeacefulShapelessRecipe> CODEC = new ShapelessRecipe.Serializer().codec()
                .xmap(PeacefulShapelessRecipe::new, PeacefulShapelessRecipe::delegate);
        private static final StreamCodec<RegistryFriendlyByteBuf, PeacefulShapelessRecipe> STREAM_CODEC = ShapelessRecipe.Serializer.STREAM_CODEC
                .map(PeacefulShapelessRecipe::new, PeacefulShapelessRecipe::delegate);
        @Override public MapCodec<PeacefulShapelessRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, PeacefulShapelessRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
