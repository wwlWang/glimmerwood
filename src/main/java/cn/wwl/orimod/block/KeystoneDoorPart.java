package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;

/** Persisted ownership offsets from a placed door's bottom-centre anchor. */
public enum KeystoneDoorPart implements StringRepresentable {
    LEGACY(0, 0),
    BOTTOM_CENTER(0, 0), BOTTOM_LEFT(-1, 0), BOTTOM_RIGHT(1, 0),
    MIDDLE_LEFT(-1, 1), MIDDLE_CENTER(0, 1), MIDDLE_RIGHT(1, 1),
    TOP_LEFT(-1, 2), TOP_CENTER(0, 2), TOP_RIGHT(1, 2);

    public static final List<KeystoneDoorPart> PLACED = List.of(BOTTOM_CENTER, BOTTOM_LEFT, BOTTOM_RIGHT,
            MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT, TOP_LEFT, TOP_CENTER, TOP_RIGHT);
    private final int column, row;

    KeystoneDoorPart(int column, int row) { this.column = column; this.row = row; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public BlockPos position(BlockPos origin, Direction facing) { return origin.above(row).relative(facing.getClockWise(), column); }
    public BlockPos origin(BlockPos position, Direction facing) { return position.below(row).relative(facing.getClockWise(), -column); }
    public KeystoneDoorPart mirrored() {
        if (this == LEGACY) return LEGACY;
        return PLACED.stream().filter(part -> part.column == -column && part.row == row).findFirst().orElseThrow();
    }
}
