package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public enum Shard {
    CAPACITY(1, Ability.DASH), RECOVERY(2, Ability.DASH), GATHERING(4, null),
    RESILIENCE(8, Ability.BASH), AIR_CONTROL(16, Ability.GLIDE);

    public static final int COST = 20;
    public final int bit;
    public final Ability prerequisite;
    Shard(int bit, Ability prerequisite) { this.bit = bit; this.prerequisite = prerequisite; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public Component title() { return Component.translatable("shard.orimod." + id()); }
    public Component description() { return Component.translatable("shard.orimod." + id() + ".description"); }
    public boolean equipped(Player player) { return SpiritData.isSpirit(player) && (player.getData(SpiritData.PROGRESS).equippedShards() & bit) != 0; }
    public static Shard byId(String id) { for (var value : values()) if (value.id().equals(id)) return value; return null; }
}
