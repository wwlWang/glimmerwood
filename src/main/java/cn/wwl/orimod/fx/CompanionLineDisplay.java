package cn.wwl.orimod.fx;

/** Vanilla actionbar lifetime: 60 ticks total, with its last 20 ticks fading out. */
public final class CompanionLineDisplay {
    public static final int DURATION = 60, FADE_TICKS = 20, BOTTOM_OFFSET = 84, ROW_SPACING = 10;
    private String id = "";
    private int remaining;
    public void show(String lineId) { id = lineId; remaining = DURATION; }
    public void tick() { if (remaining > 0 && --remaining == 0) id = ""; }
    public void clear() { id = ""; remaining = 0; }
    public String id() { return id; }
    public int remaining() { return remaining; }
    public int alpha(float partial) { return (int) Math.clamp((remaining - partial) * 255 / FADE_TICKS, 0, 255); }
}
