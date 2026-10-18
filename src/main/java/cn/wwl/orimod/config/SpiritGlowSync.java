package cn.wwl.orimod.config;

/**
 * Decides how the bundled Spirit Glow shader pack follows the settings toggle without taking over a
 * shader pack the player chose in Iris.
 *
 * <ul>
 *   <li>First run: Spirit Glow is selected only when no shader pack is chosen in Iris. If another pack
 *       is chosen, the toggle is saved as off and that pack is left alone.</li>
 *   <li>Turning the toggle on remembers the pack in use; turning it off restores that pack.</li>
 *   <li>A change made in Iris (another pack, shaders off) is saved to the toggle, not reverted. The same
 *       rule as {@link SpiritCameraSync}: a settings change wins when both happen together.</li>
 * </ul>
 */
public final class SpiritGlowSync {
    /** File name of the bundled pack inside Iris's shaderpacks folder. */
    public static final String PACK = "Spirit Glow.zip";

    public enum Action { NONE, ENABLE, DISABLE, SAVE_ON, SAVE_OFF }

    /** Iris selection: the pack name ({@code null} or blank when none is chosen) and the shaders switch. */
    public record IrisState(String pack, boolean enabled) {
        public boolean ours() { return enabled && PACK.equals(pack); }
        public boolean otherPackChosen() { return pack != null && !pack.isBlank() && !PACK.equals(pack); }
    }

    /** What to put back when Spirit Glow is turned off. {@code pack == null} keeps the name and only turns shaders off. */
    public record Restore(String pack, boolean enabled) {}

    private SpiritGlowSync() {}

    /**
     * @param setupDone  whether the first-run choice was already made
     * @param configured toggle value in the config
     * @param synced     toggle value last applied or saved by this sync ({@code null} after start-up)
     * @param iris       current Iris selection
     */
    public static Action decide(boolean setupDone, boolean configured, Boolean synced, IrisState iris) {
        boolean live = iris.ours();
        if (!setupDone) {
            if (iris.otherPackChosen()) return configured ? Action.SAVE_OFF : Action.NONE;
            return configured && !live ? Action.ENABLE : Action.NONE;
        }
        if (synced != null && configured != synced) {
            if (live == configured) return Action.NONE;
            return configured ? Action.ENABLE : Action.DISABLE;
        }
        if (live == configured) return Action.NONE;
        return live ? Action.SAVE_ON : Action.SAVE_OFF;
    }

    /** Selection to remember before enabling, or {@code null} to keep what was remembered before. */
    public static IrisState remember(IrisState before) {
        return before.otherPackChosen() ? before : null;
    }

    public static Restore restore(String previousPack, boolean previousEnabled) {
        if (previousPack == null || previousPack.isBlank() || PACK.equals(previousPack)) return new Restore(null, false);
        return new Restore(previousPack, previousEnabled);
    }
}
