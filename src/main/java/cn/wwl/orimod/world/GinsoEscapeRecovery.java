package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** Saved independently of Soul Link; deliberately NOT copied on death. No resumable timer. */
public record GinsoEscapeRecovery(String dimension, BlockPos safe, boolean pending, long retryAt) {
    public static final GinsoEscapeRecovery EMPTY=new GinsoEscapeRecovery("",BlockPos.ZERO,false,0);
    public static final Codec<GinsoEscapeRecovery> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("dimension").forGetter(GinsoEscapeRecovery::dimension),
            BlockPos.CODEC.fieldOf("safe").forGetter(GinsoEscapeRecovery::safe),
            Codec.BOOL.fieldOf("pending").forGetter(GinsoEscapeRecovery::pending),
            Codec.LONG.optionalFieldOf("retry_at",0L).forGetter(GinsoEscapeRecovery::retryAt)
    ).apply(i,GinsoEscapeRecovery::new));
    public GinsoEscapeRecovery { safe=safe.immutable(); }
}
