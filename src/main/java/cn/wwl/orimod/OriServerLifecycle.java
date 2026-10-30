package cn.wwl.orimod;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.movement.BashSession;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.RuinLocator;
import cn.wwl.orimod.world.SpiritChallenge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Release world-owned resources before player/world saving; also drop references after a failed stop. */
public final class OriServerLifecycle {
    private OriServerLifecycle() {}

    public static void stopping(ServerStoppingEvent event) {
        try {
            RuinLocator.stopAll();
            cn.wwl.orimod.progression.MerchantTrading.clear();
            cn.wwl.orimod.world.EscapeSequence.stopAll();
            cn.wwl.orimod.world.GinsoEscape.stop(event.getServer());
            SpiritChallenge.stopAll(event.getServer());
            for (var player : event.getServer().getPlayerList().getPlayers()) {
                cn.wwl.orimod.progression.Restoration.cancel(player);
                SpiritCombat.cancel(player, "shutdown");
                var movement = player.getData(SpiritData.MOVEMENT);
                movement.active = false;
                SpiritMovement.cancel(player, movement);
                movement.targetCooldowns.clear();
            }
        } finally { BashSession.releaseAll(); }
    }

    public static void stopped(ServerStoppedEvent event) {
        // This event also runs after an exception bypassed ServerStoppingEvent. Closed worlds
        // must not be mutated; saved challenge recovery remains available on the next login.
        cn.wwl.orimod.progression.Restoration.clear();
        cn.wwl.orimod.world.GinsoEscape.clear();
        cn.wwl.orimod.world.RestorationBloom.clear();
        cn.wwl.orimod.world.BlightEcology.clear();
        cn.wwl.orimod.world.BlightAtmosphereSync.clear();
        cn.wwl.orimod.world.BlightMobs.clear();
        cn.wwl.orimod.entity.CorruptedAnimals.clear();
        cn.wwl.orimod.world.GloomTide.reset();
        cn.wwl.orimod.world.FeatureSites.clear();
        RuinLocator.discardAll();
        cn.wwl.orimod.progression.MerchantTrading.clear();
        SpiritChallenge.discardAll();
        BashSession.discardAll();
        cn.wwl.orimod.combat.BashedImpact.reset();
        cn.wwl.orimod.fx.SpiritFxLifecycle.reset();
    }
}
