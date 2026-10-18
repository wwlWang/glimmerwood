package cn.wwl.orimod.fx;

/** Lifecycle step carried by each spirit effect payload. Wire ids are fixed. */
public enum SpiritFxPhase {
    /** A one-shot burst at a position. */
    BURST(0),
    /** Start, or keep alive (heartbeat), a persistent effect on the source entity. */
    START(1),
    /** Stop one persistent effect on the source entity. */
    STOP(2),
    /** Stop every persistent effect on the source entity (form switch, death, dimension change, logout). */
    STOP_ALL(3);

    private final int wireId;
    SpiritFxPhase(int wireId) { this.wireId = wireId; }
    public int wireId() { return wireId; }
    public static SpiritFxPhase byWireId(int id) {
        for (var phase : values()) if (phase.wireId == id) return phase;
        return null;
    }
}
