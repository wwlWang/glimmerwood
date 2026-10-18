package cn.wwl.orimod.movement;

import io.netty.handler.codec.DecoderException;

/** Explicit wire IDs are append-only; enum declaration order is not a protocol. */
public enum MoveMode {
    IDLE(0, "idle"), JUMP(1, "jump"), DOUBLE_JUMP(2, "double_jump"), WALL_JUMP(3, "wall_jump"),
    DASH(4, "dash"), WALL_SLIDE(5, "wall_slide"), BASH_AIM(6, "bash_aim"), BASH_LAUNCH(7, "bash_launch"),
    GRAPPLE(8, "grapple"), GRAPPLE_END(9, "grapple_end"), GLIDE(10, "glide"), STOMP(11, "stomp"),
    STOMP_LAND(12, "stomp_land"), RESUME(13, "resume"), JUMP_REJECT(14, "jump_reject"), RELOCATE(15, "relocate");

    private final int wireId;
    private final String id;

    MoveMode(int wireId, String id) { this.wireId = wireId; this.id = id; }
    public int wireId() { return wireId; }
    public String id() { return id; }

    public static MoveMode fromWireId(int id) {
        for (MoveMode mode : values()) if (mode.wireId == id) return mode;
        throw new DecoderException("Unknown Ori movement mode: " + id);
    }

    public boolean blocksJump() {
        return this == DASH || this == BASH_AIM || this == GRAPPLE || this == STOMP;
    }
}
