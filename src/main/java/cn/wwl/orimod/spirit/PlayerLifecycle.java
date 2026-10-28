package cn.wwl.orimod.spirit;

import net.minecraft.server.level.ServerPlayer;

/** Server-thread identity check for callbacks that can outlive a player incarnation. */
public final class PlayerLifecycle {
    private PlayerLifecycle() {}

    public static boolean current(ServerPlayer player) {
        var current = player.server.getPlayerList().getPlayer(player.getUUID());
        // placeNewPlayer teleports before registering the UUID; this is a live login callback.
        // Respawn's retired player is removed, even before the replacement enters the list.
        return !player.isRemoved() && (current == null || current == player);
    }
}
