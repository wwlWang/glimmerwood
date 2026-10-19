package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.UUID;

public final class ChallengeSession {
    public final UUID id = UUID.randomUUID();
    public final RuinKind kind;
    public final BlockPos shrine, origin;
    public final RuinPlacement placement;
    public final ResourceKey<Level> dimension;
    public final long started;
    public int checkpoint, baseline;
    /** R88: pads cleared without the suggested action (diagnostics only; the landing still counts). */
    public int improvised;
    public UUID target;
    /** R63: this run's ruin-local trace, and the best run's trace replayed as a ghost. */
    public final java.util.ArrayList<net.minecraft.world.phys.Vec3> trace = new java.util.ArrayList<>();
    public java.util.List<net.minecraft.world.phys.Vec3> ghost;
    public ChallengeSession(RuinKind kind, BlockPos shrine, RuinPlacement placement, ResourceKey<Level> dimension, long started) {
        this.kind = kind; this.shrine = shrine.immutable(); this.placement = placement; this.origin = placement.origin();
        this.dimension = dimension; this.started = started;
    }
    public String key() { return key(dimension, shrine); }
    public static String key(ResourceKey<Level> dimension, BlockPos shrine) {
        return dimension.location() + "/" + shrine.getX() + "," + shrine.getY() + "," + shrine.getZ();
    }
}
