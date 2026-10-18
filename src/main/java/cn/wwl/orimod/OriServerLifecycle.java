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
            SpiritChallenge.stopAll(event.getServer());
            for (var player : event.getServer().getPlayerList().getPlayers()) {
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
        RuinLocator.discardAll();
        SpiritChallenge.discardAll();
        BashSession.discardAll();
    }
}
