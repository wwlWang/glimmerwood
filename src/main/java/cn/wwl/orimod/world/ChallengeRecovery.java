package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** Written before a challenge starts; a reload returns a living player without resuming a stale timer. */
public record ChallengeRecovery(String dimension, BlockPos safe) {
    public static final ChallengeRecovery EMPTY = new ChallengeRecovery("", BlockPos.ZERO);
    public static final Codec<ChallengeRecovery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(ChallengeRecovery::dimension),
            BlockPos.CODEC.fieldOf("safe").forGetter(ChallengeRecovery::safe)
    ).apply(instance, ChallengeRecovery::new));
}
