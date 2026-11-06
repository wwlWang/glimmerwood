package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * Survives logout independently of Soul Link; deliberately NOT copied on death.
 * Mid-escape progress resumes on rejoin (like {@link GloomEscapeProgress}); fail recovery still teleports to safe.
 */
public record GinsoEscapeRecovery(
        String dimension,
        BlockPos safe,
        boolean pending,
        long retryAt,
        BlockPos origin,
        long seed,
        int entrance,
        int approach,
        int shapeVersion,
        int courseVersion,
        int escapeVersion,
        int elapsed
) {
    public static final GinsoEscapeRecovery EMPTY = new GinsoEscapeRecovery("", BlockPos.ZERO, false, 0, BlockPos.ZERO, 0, 0, 0, 0, 0, 0, 0);
    public static final Codec<GinsoEscapeRecovery> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(GinsoEscapeRecovery::dimension),
            BlockPos.CODEC.fieldOf("safe").forGetter(GinsoEscapeRecovery::safe),
            Codec.BOOL.fieldOf("pending").forGetter(GinsoEscapeRecovery::pending),
            Codec.LONG.optionalFieldOf("retry_at", 0L).forGetter(GinsoEscapeRecovery::retryAt),
            BlockPos.CODEC.optionalFieldOf("origin", BlockPos.ZERO).forGetter(GinsoEscapeRecovery::origin),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(GinsoEscapeRecovery::seed),
            Codec.INT.optionalFieldOf("entrance", 0).forGetter(GinsoEscapeRecovery::entrance),
            Codec.INT.optionalFieldOf("approach", 0).forGetter(GinsoEscapeRecovery::approach),
            Codec.INT.optionalFieldOf("shape_version", 0).forGetter(GinsoEscapeRecovery::shapeVersion),
            Codec.INT.optionalFieldOf("course_version", 0).forGetter(GinsoEscapeRecovery::courseVersion),
            Codec.INT.optionalFieldOf("escape_version", 0).forGetter(GinsoEscapeRecovery::escapeVersion),
            Codec.INT.optionalFieldOf("elapsed", 0).forGetter(GinsoEscapeRecovery::elapsed)
    ).apply(i, GinsoEscapeRecovery::new));

    public GinsoEscapeRecovery {
        safe = safe.immutable();
        origin = origin.immutable();
        elapsed = Math.max(0, elapsed);
    }

    /** Fail path: teleport to the saved safe landing when the next tick/login can. */
    public static GinsoEscapeRecovery recover(String dimension, BlockPos safe, long retryAt) {
        return new GinsoEscapeRecovery(dimension, safe, true, retryAt, BlockPos.ZERO, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Cooldown without a pending teleport (success / dimension cancel / death). */
    public static GinsoEscapeRecovery cooldown(String dimension, BlockPos safe, long retryAt) {
        return new GinsoEscapeRecovery(dimension, safe, false, retryAt, BlockPos.ZERO, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Active escape snapshot that can rebuild the piece and continue the clock. */
    public static GinsoEscapeRecovery progress(GinsoPiece piece, int elapsed) {
        return new GinsoEscapeRecovery(
                "", piece.point(piece.escapeSafe()), false, 0,
                piece.origin(), piece.shapeSeed(), piece.entrance(), piece.approach(),
                piece.shapeVersion(), piece.courseVersion(), piece.escapeVersion(), elapsed);
    }

    public boolean resumable() {
        return !pending && escapeVersion > 0 && !origin.equals(BlockPos.ZERO) && retryAt == 0;
    }

    public GinsoPiece piece() {
        return new GinsoPiece(origin, seed, entrance, approach, shapeVersion, courseVersion, escapeVersion);
    }
}
