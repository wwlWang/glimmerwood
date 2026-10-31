package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Award at committed game events. Login backfills only milestones proven by existing save receipts. No polling. */
public final class StoryProgress {
    private StoryProgress() {}
    public enum Step {
        ROOT, SPIRIT, SKILL, EXPLORE, GINSO, GINSO_ESCAPE, BLIGHT, HEART_WISP, WING_WISP, EARTH_WISP, ALL_WISPS;
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static void award(ServerPlayer player, Step step) {
        if (player.isSpectator()) return;
        var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("orimod", "story/" + step.id()));
        if (advancement != null) player.getAdvancements().award(advancement, "reached");
    }
    public static void wisps(ServerPlayer player) {
        int mask = player.getData(SpiritData.PROGRESS).wisps();
        if ((mask & WillWisp.HEART.bit) != 0) award(player, Step.HEART_WISP);
        if ((mask & WillWisp.WING.bit) != 0) award(player, Step.WING_WISP);
        if ((mask & WillWisp.EARTH.bit) != 0) award(player, Step.EARTH_WISP);
        if (mask == 7) award(player, Step.ALL_WISPS);
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SpiritData.isAwakened(player)) return;
        award(player, Step.ROOT);
        if (SpiritData.isSpirit(player)) award(player, Step.SPIRIT);
        if (player.getData(SpiritData.ABILITIES) != 0) award(player, Step.SKILL);
        var rewards = player.getData(SpiritData.PROGRESS).rewards();
        if (rewards.entrySet().stream().anyMatch(e -> e.getValue() > 0 && (e.getKey().startsWith("discover/") || e.getKey().startsWith("tree/")))) award(player, Step.EXPLORE);
        if (rewards.entrySet().stream().anyMatch(e -> e.getValue() > 0 && e.getKey().startsWith("purify/"))) award(player, Step.BLIGHT);
        if (rewards.getOrDefault(cn.wwl.orimod.world.GinsoEscape.RECEIPT, 0) > 0) {
            award(player, Step.GINSO); award(player, Step.GINSO_ESCAPE);
        }
        wisps(player);
    }
}
