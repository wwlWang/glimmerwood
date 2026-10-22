package cn.wwl.orimod.client;

/** Monotonic milliseconds, independent of FPS and server ticks. One press has exactly one outcome. */
public final class PanelGesture {
    public static final long HOLD_MILLIS = 250;
    private long began;
    private boolean pressed, wheel;
    public void press(long now) { if (!pressed) { pressed=true; wheel=false; began=now; } }
    public boolean poll(long now) { if (pressed&&!wheel&&now-began>=HOLD_MILLIS) { wheel=true; return true; } return false; }
    public boolean release(long now) { boolean tap=pressed&&!wheel&&now-began<HOLD_MILLIS; cancel(); return tap; }
    public boolean pressed() { return pressed; }
    public void cancel() { pressed=wheel=false; }
}
