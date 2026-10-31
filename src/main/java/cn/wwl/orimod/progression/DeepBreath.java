package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One transient attribute modifier, reconstructed from current progress rather than saved or stacked. */
public final class DeepBreath {
    public static final ResourceLocation SWIM = ResourceLocation.fromNamespaceAndPath("orimod", "deep_breath_swim");
    public static final double BONUS = .25;
    private DeepBreath() {}
    public static boolean fasterSwimming(Player p) {
        return p.isAlive() && !p.isSpectator() && Shard.DEEP_BREATH.equipped(p) && Ability.WATER_DASH.unlocked(p);
    }
    public static void update(Player p) {
        var attribute = p.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (attribute == null) return;
        boolean active = fasterSwimming(p);
        if (active && !attribute.hasModifier(SWIM))
            attribute.addTransientModifier(new AttributeModifier(SWIM, BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!active) attribute.removeModifier(SWIM);
    }
    public static void clear(Player p) {
        var attribute = p.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (attribute != null) attribute.removeModifier(SWIM);
        p.getData(SpiritData.ENERGY_STATE).breath.consume(0, false);
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent e) { clear(e.getEntity()); update(e.getEntity()); }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { clear(e.getEntity()); }
    public static void clone(PlayerEvent.Clone e) { clear(e.getOriginal()); clear(e.getEntity()); }
    public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) clear(p); }
}
