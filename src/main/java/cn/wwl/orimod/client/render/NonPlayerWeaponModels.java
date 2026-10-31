package cn.wwl.orimod.client.render;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import java.util.Set;

/** Ordinary held-item sprites for display stands and mobs, without player weapon energy effects. */
public final class NonPlayerWeaponModels {
    private static final Set<String> WEAPONS = Set.of("spirit_blade", "spirit_bow", "spirit_hammer", "spirit_spear");
    private NonPlayerWeaponModels() {}
    public static ModelResourceLocation model(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("orimod") && WEAPONS.contains(id.getPath()) ? model(id.getPath()) : null;
    }
    private static ModelResourceLocation model(String id) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("orimod", "item/" + id + "_display"));
    }
    public static void register(ModelEvent.RegisterAdditional event) { WEAPONS.forEach(id -> event.register(model(id))); }
}
