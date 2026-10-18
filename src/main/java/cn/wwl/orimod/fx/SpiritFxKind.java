package cn.wwl.orimod.fx;

/**
 * Spirit effect bursts sent from the server and spawned by each client under its own effect settings.
 * Wire ids are fixed; never reuse or renumber them.
 * {@code essential} particles are always spawned (even with advanced particles off or vanilla "minimal")
 * so the gameplay signal stays readable; the rest of {@code base} is decorative and scaled.
 */
public enum SpiritFxKind {
    BLADE_HIT(0, 4, 1, .12, .12, .12, .03),
    STOMP_IMPACT(1, 20, 4, 1.5, .06, 1.5, .02),
    ARROW_TRAIL(2, 1, 1, 0, 0, 0, 0),
    CHECKPOINT_REACHED(3, 12, 3, .4, .5, .4, .015),
    ROUTE_MARKER(4, 1, 1, .2, .1, .2, 0);

    private final int wireId, base, essential;
    private final double spreadX, spreadY, spreadZ, speed;

    SpiritFxKind(int wireId, int base, int essential, double spreadX, double spreadY, double spreadZ, double speed) {
        this.wireId = wireId; this.base = base; this.essential = essential;
        this.spreadX = spreadX; this.spreadY = spreadY; this.spreadZ = spreadZ; this.speed = speed;
    }

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
