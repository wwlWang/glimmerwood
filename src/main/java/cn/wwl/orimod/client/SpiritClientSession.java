package cn.wwl.orimod.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One reset boundary for movement prediction and combat feedback, including direct player/world replacement. */
public final class SpiritClientSession {
    private static final ClientSessionClock CLOCK = new ClientSessionClock();
    private SpiritClientSession() {}
    public static long now() { return CLOCK.now(); }
    public static void observe() {
        var mc = Minecraft.getInstance();
        if (CLOCK.observe(mc.player, mc.level)) resetState();
    }
    public static void tick(ClientTickEvent.Pre event) { CLOCK.tick(); observe(); }
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) { reset(); }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    private static void reset() { CLOCK.clear(); resetState(); }
    private static void resetState() {
        SpiritCombatClient.resetSession();
        SpiritControls.resetSession();
        SpiritMantle.resetSession();
        SpiritActionPrediction.resetSession();
    }
}
