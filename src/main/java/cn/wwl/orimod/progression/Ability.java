package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public enum Ability {
    DASH(1, 20, null), BASH(2, 30, DASH), GLIDE(4, 30, BASH),
    GRAPPLE(8, 40, GLIDE), STOMP(16, 40, GRAPPLE), BOW(32, 30, DASH),
    /** R38: Launch (WotW) / Charge Jump (BF). */
    LAUNCH(64, 40, STOMP),
    /** R39: Light Burst (WotW). */
    LIGHT_BURST(128, 40, BASH),
    /** R52: Spirit Smash (WotW hammer); the Spirit Hammer only answers once this is learned. */
    SMASH(256, 40, BOW),
    /** R55: Climb (BF): push into a wall while sliding to climb it. */
    CLIMB(512, 30, DASH);

    public final int bit, cost;
    public final Ability prerequisite;
    Ability(int bit, int cost, Ability prerequisite) { this.bit = bit; this.cost = cost; this.prerequisite = prerequisite; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public Component title() { return Component.translatable("ability.orimod." + id()); }
    public Component description() { return Component.translatable("ability.orimod." + id() + ".description"); }
    public boolean unlocked(Player player) { return SpiritData.isAwakened(player) && (player.getData(SpiritData.ABILITIES) & bit) != 0; }
    public static Ability byId(String id) { for (var value : values()) if (value.id().equals(id)) return value; return null; }
}
