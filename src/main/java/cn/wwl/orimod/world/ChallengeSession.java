package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.UUID;

public final class ChallengeSession {
    public final UUID id = UUID.randomUUID();
    public final RuinKind kind;
    public final BlockPos shrine, origin;
    public final ResourceKey<Level> dimension;
    public final long started;
    public int checkpoint, baseline;
    public UUID target;
    public ChallengeSession(RuinKind kind, BlockPos shrine, ResourceKey<Level> dimension, long started) {
        this.kind = kind; this.shrine = shrine.immutable(); this.origin = shrine.subtract(kind.layout().shrine());
        this.dimension = dimension; this.started = started;
    }
    public String key() { return key(dimension, shrine); }
    public static String key(ResourceKey<Level> dimension, BlockPos shrine) {
        return dimension.location() + "/" + shrine.getX() + "," + shrine.getY() + "," + shrine.getZ();
    }
}
