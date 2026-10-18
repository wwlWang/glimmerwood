package cn.wwl.orimod.client.render;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short visual release; the authoritative form and collision change immediately. */
public final class SpiritAppearance {
    private static final Map<UUID, Observation> FORMS = new HashMap<>();
    private static Object level;
    private SpiritAppearance() { }
    public static void tick(ClientTickEvent.Post event) {
        var current = Minecraft.getInstance().level;
        if (level != current) { FORMS.clear(); level = current; }
        if (current == null) return;
        FORMS.keySet().removeIf(id -> current.getPlayerByUUID(id) == null);
        for (Player player : current.players()) {
            boolean spirit = SpiritData.isSpirit(player);
            var old = FORMS.get(player.getUUID());
            int entered = spirit && (old == null || !old.spirit) ? player.tickCount : old == null ? -1000 : old.entered;
            int lastSpirit = spirit ? player.tickCount : old == null ? -1000 : old.lastSpirit;
            FORMS.put(player.getUUID(), new Observation(spirit, entered, lastSpirit));
        }
    }
    public static boolean visible(Player player) {
        return !player.isSpectator() && (SpiritData.isSpirit(player)
                || player.tickCount - FORMS.getOrDefault(player.getUUID(), Observation.NONE).lastSpirit < 10);
    }

    static float enteredAt(Player player) { return FORMS.getOrDefault(player.getUUID(), Observation.NONE).entered / 20f; }

    private record Observation(boolean spirit, int entered, int lastSpirit) {
        private static final Observation NONE = new Observation(false, -1000, -1000);
    }
}
