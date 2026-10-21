package cn.wwl.orimod.fx;

/**
 * Spirit effect bursts sent from the server and spawned by each client under its own effect settings.
 * Wire ids are fixed; never reuse or renumber them.
 * {@code essential} particles are always spawned (even with advanced particles off or vanilla "minimal")
 * so the gameplay signal stays readable; the rest of {@code base} is decorative and scaled.
 */
public enum SpiritFxKind {
    BLADE_HIT(0, 10, 3, .12, .12, .12, .03),
    STOMP_IMPACT(1, 20, 4, 1.5, .06, 1.5, .02),
    ARROW_TRAIL(2, 1, 1, 0, 0, 0, 0),
    CHECKPOINT_REACHED(3, 12, 3, .4, .5, .4, .015),
    ROUTE_MARKER(4, 1, 1, .2, .1, .2, 0),
    DASH(5, 14, 5, .3, .3, .3, .02),
    DOUBLE_JUMP(6, 12, 4, .3, .05, .3, .01),
    WALL_JUMP(7, 10, 3, .1, .4, .1, .02),
    BASH(8, 18, 6, .3, .3, .3, .04),
    GRAPPLE_HOOK(9, 10, 3, .15, .15, .15, .02),
    FORM_SWITCH(10, 20, 6, .3, .8, .3, .02),
    /** Persistent: spawned every client tick while the server keeps the effect alive. */
    GLIDE(11, 3, 1, .25, .1, .25, 0, true),
    GRAPPLE_TETHER(12, 4, 2, 0, 0, 0, 0, true),
    /** Persistent (R32): ring around the player while Bash aims; the launch itself is {@link #BASH}. */
    BASH_AIM(13, 4, 2, 0, 0, 0, 0, true),
    /** One-shot (R37): companion Spirit Flame, a beam from the companion to the struck enemy. */
    SPIRIT_FLAME(14, 10, 4, 0, 0, 0, 0),
    /** One-shot (R63): Spirit Trials ghost of the best run, a pale column every other tick. */
    RACE_GHOST(15, 4, 2, .08, .05, .08, 0),
    /** R96: a cold white-blue flare when a spirit ability first ignites a lantern. */
    LANTERN_IGNITE(16, 12, 4, .1, .1, .1, .02),
    /** R98: sustained Flash light with sparse outward motes; lifecycle owns the beacon loop. */
    FLASH(17, 2, 1, .1, .1, .1, .01, true),
    /** R100: an upward root-heart column and motes gathering toward the blessed player. */
    TREE_BLESSING(18, 40, 10, .1, .1, .1, .01);

    private final int wireId, base, essential;
    private final double spreadX, spreadY, spreadZ, speed;
    private final boolean persistent;

    SpiritFxKind(int wireId, int base, int essential, double spreadX, double spreadY, double spreadZ, double speed) {
        this(wireId, base, essential, spreadX, spreadY, spreadZ, speed, false);
    }

    SpiritFxKind(int wireId, int base, int essential, double spreadX, double spreadY, double spreadZ, double speed, boolean persistent) {
        this.wireId = wireId; this.base = base; this.essential = essential;
        this.spreadX = spreadX; this.spreadY = spreadY; this.spreadZ = spreadZ; this.speed = speed; this.persistent = persistent;
    }

    /** Persistent effects follow their source entity between START and STOP; the rest are one-shot bursts. */
    public boolean persistent() { return persistent; }

    public int wireId() { return wireId; }
    public int base() { return base; }
    public int essential() { return essential; }
    public double spreadX() { return spreadX; }
    public double spreadY() { return spreadY; }
    public double spreadZ() { return spreadZ; }
    public double speed() { return speed; }

    /** @return the kind, or {@code null} for an id this build does not know. */
    public static SpiritFxKind byWireId(int id) {
        for (var kind : values()) if (kind.wireId == id) return kind;
        return null;
    }
}
