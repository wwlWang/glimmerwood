package cn.wwl.orimod.config;

/**
 * Keeps the remembered spirit camera and the live camera in step while in spirit form.
 * A change made in the settings screen is applied to the live camera; a change made with F5 is saved.
 * The settings change wins when both happen in the same tick, so a menu choice is never overwritten.
 */
public final class SpiritCameraSync {
    public enum Action { NONE, APPLY_SETTING, SAVE_CAMERA }
    private SpiritCameraSync() {}

    /**
     * @param configured value currently in the config
     * @param synced     value last applied or saved by this sync ({@code null} right after entering spirit form)
     * @param live       camera currently in use
     */
    public static Action decide(SpiritCameraView configured, SpiritCameraView synced, SpiritCameraView live) {
        if (synced != null && configured != synced) return live == configured ? Action.NONE : Action.APPLY_SETTING;
        return live == configured ? Action.NONE : Action.SAVE_CAMERA;
    }
}
