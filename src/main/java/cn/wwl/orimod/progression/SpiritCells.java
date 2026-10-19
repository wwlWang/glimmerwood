package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * R36 Life and Energy Cells (Ori): permanent pickups found in ruins. Each Life Cell adds one heart, each
 * Energy Cell 10 spirit energy; counts live in {@link SpiritProgress#rewards()} (kept through death) and are
 * capped. The health bonus is a transient modifier re-applied on login, respawn and use, so it never piles up.
 */
public final class SpiritCells {
    public enum Kind { LIFE, ENERGY;
        public String key() { return "cell/" + name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final int MAX = 10;
    public static final double HEALTH_PER_CELL = 2;
    public static final float ENERGY_PER_CELL = 10;
    public static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath("orimod", "life_cells");

    public static int count(Player player, Kind kind) {
        return Math.clamp(player.getData(SpiritData.PROGRESS).rewards().getOrDefault(kind.key(), 0), 0, MAX);
    }

    /** @return whether the cell was absorbed (awakened and below the cap). */
    public static boolean absorb(ServerPlayer player, Kind kind) {
        if (!SpiritData.isAwakened(player)) return false;
        var p = player.getData(SpiritData.PROGRESS);
        int now = count(player, kind);
        if (now >= MAX) return false;
        var records = new java.util.HashMap<>(p.rewards());
        records.put(kind.key(), now + 1);
        player.setData(SpiritData.PROGRESS, p.withRewards(records));
        apply(player);
        if (kind == Kind.LIFE) player.heal((float) HEALTH_PER_CELL);
        else player.setData(SpiritData.ENERGY, SpiritProgression.maxEnergy(player));
        return true;
    }

    public static void apply(Player player) {
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        int cells = SpiritData.isAwakened(player) ? count(player, Kind.LIFE) : 0;
        if (cells == 0) health.removeModifier(MODIFIER);
        else health.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, cells * HEALTH_PER_CELL, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) { apply(event.getEntity()); }
    public static void respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        apply(event.getEntity());
        event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }

    /** Reward for the first clear of a ruin trial: one cell of each kind. */
    public static void grantFirstClear(ServerPlayer player) {
        for (var item : new net.minecraft.world.item.ItemStack[]{
                new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.LIFE_CELL.get()),
                new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.ENERGY_CELL.get())})
            if (!player.getInventory().add(item)) player.drop(item, false);
    }

    private SpiritCells() {}
}
