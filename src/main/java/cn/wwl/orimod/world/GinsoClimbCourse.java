package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import java.util.HashMap;

/** Course 3 keeps the proven ability landings, with the escape start in the entry chamber. */
public final class GinsoClimbCourse {
    public static final BlockPos HEART = new BlockPos(0, 5, 0), SAFE = new BlockPos(0, 5, -2);
    private static final GinsoCourses.Layout INSTANCE = build();
    private GinsoClimbCourse() {}
    public static GinsoCourses.Layout layout() { return INSTANCE; }
    private static GinsoCourses.Layout build() {
        var old = TallGinsoInterior.layout();
        var blocks = new HashMap<>(old.blocks());
        // The top is now a transfer landing, not a second start shrine.
        blocks.put(new BlockPos(0, 93, 0), TallGinsoInterior.CUSHION);
        // No mid-chase door interaction at the wind-to-jump transfer.
        blocks.put(new BlockPos(1, 79, 0), GinsoShape.AIR);
        blocks.put(new BlockPos(1, 80, 0), GinsoShape.AIR);
        return new GinsoCourses.Layout(blocks, old.sections(), old.wells(), HEART, old.hops(), old.retreat());
    }
}
