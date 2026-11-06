package cn.wwl.orimod.progression;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

/** Persistent, once-per-player gifts. Bits 1/2 retain the legacy blade/bow receipts. */
public enum WeaponGift {
    BLADE(1, "blade", null, OriItems.SPIRIT_BLADE),
    BOW(2, "bow", Ability.BOW, OriItems.SPIRIT_BOW),
    HAMMER(4, "smash", Ability.SMASH, OriItems.SPIRIT_HAMMER),
    SPEAR(8, "spear", Ability.SPEAR, OriItems.SPIRIT_SPEAR);

    public static final int MASK = 15;
    public final int bit;
    public final String id;
    public final Ability ability;
    private final Supplier<? extends Item> item;
    WeaponGift(int bit, String id, Ability ability, Supplier<? extends Item> item) {
        this.bit = bit; this.id = id; this.ability = ability; this.item = item;
    }
    public static WeaponGift forAbility(Ability ability) {
        for (var gift : values()) if (gift.ability == ability) return gift;
        return null;
    }
    public static WeaponGift byId(String id) {
        for (var gift : values()) if (gift.id.equals(id)) return gift;
        return null;
    }
    public boolean eligible(ServerPlayer player) { return SpiritData.isAwakened(player) && (ability == null || ability.unlocked(player)); }
    public boolean claimed(int claims) { return (claims & bit) != 0; }
    public boolean holding(net.minecraft.world.entity.player.Player player) {
        var it = item.get();
        return player.getInventory().hasAnyMatching(s -> s.is(it)) || player.getOffhandItem().is(it);
    }

    public boolean grant(ServerPlayer player) {
        var progress = player.getData(SpiritData.PROGRESS);
        if (!eligible(player) || claimed(progress.claimedWeapons())) return false;
        var stack = new ItemStack(item.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.setData(SpiritData.PROGRESS, progress.withClaims(progress.claimedWeapons() | bit));
        player.containerMenu.broadcastChanges();
        return true;
    }
}
