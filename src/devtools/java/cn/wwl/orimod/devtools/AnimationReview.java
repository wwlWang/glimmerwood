package cn.wwl.orimod.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Session-only silhouette review; no settings or gameplay changes, never included in the release jar. */
public final class AnimationReview {
    private static java.lang.ref.WeakReference<ClientLevel> level = new java.lang.ref.WeakReference<>(null);
    private static long suppressedParticles;
    private AnimationReview() {}
    public static void configure(boolean enabled) { level = new java.lang.ref.WeakReference<>(enabled ? Minecraft.getInstance().level : null); suppressedParticles = 0; }
    public static boolean muted() { var current = level.get(); return current != null && current == Minecraft.getInstance().level; }
    public static void particleSuppressed() { suppressedParticles++; }
    public static long suppressedParticles() { return suppressedParticles; }
}
