package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public enum Shard {
    CAPACITY(1, Ability.DASH), RECOVERY(2, Ability.DASH), GATHERING(4, null),
    RESILIENCE(8, Ability.BASH), AIR_CONTROL(16, Ability.GLIDE),
    /** R40: a third air jump (Ori triple jump). */
    TRIPLE_JUMP(32, Ability.BASH),
    /** R99: WotW-inspired pickup sustain, Bash upgrade, retaliation and wall attachment. */
    LIFE_HARVEST(64, null), ENERGY_HARVEST(128, null), ULTRA_BASH(256, Ability.BASH),
    THORN(512, null), STICKY(1024, Ability.CLIMB),
    MAGNET(2048, null), DEEP_BREATH(4096, null), LUMEN_SIGHT(8192, null),
    PROSPECTOR(16384, null), EMBER_SKIN(32768, null), FORAGER(65536, null), SPLINTER(131072, Ability.BOW);

    public static final int MASK = java.util.Arrays.stream(values()).mapToInt(s -> s.bit).reduce(0, (a, b) -> a | b);

    public final int bit;
    public final Ability prerequisite;
    Shard(int bit, Ability prerequisite) { this.bit = bit; this.prerequisite = prerequisite; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public Component title() { return Component.translatable("shard.orimod." + id()); }
    public Component description() { return Component.translatable("shard.orimod." + id() + ".description"); }
    public Component description(Player player) {
        return this == DEEP_BREATH && player != null && Ability.WATER_DASH.unlocked(player)
                ? Component.translatable("shard.orimod.deep_breath.swim_description") : description();
    }
    public boolean equipped(Player player) { return SpiritData.isSpirit(player) && (player.getData(SpiritData.PROGRESS).equippedShards() & bit) != 0; }
    public static Shard byId(String id) { for (var value : values()) if (value.id().equals(id)) return value; return null; }
}
