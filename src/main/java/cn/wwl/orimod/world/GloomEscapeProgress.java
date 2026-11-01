package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

/** Only elapsed online time is saved; quitting never consumes the escape or its first-clear reward. */
public record GloomEscapeProgress(String dimension, Vec3 origin, int elapsed) {
    public static final GloomEscapeProgress EMPTY = new GloomEscapeProgress("", Vec3.ZERO, 0);
    public static final Codec<GloomEscapeProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(GloomEscapeProgress::dimension),
            Vec3.CODEC.optionalFieldOf("origin", Vec3.ZERO).forGetter(GloomEscapeProgress::origin),
            Codec.INT.optionalFieldOf("elapsed", 0).forGetter(GloomEscapeProgress::elapsed)
    ).apply(i, GloomEscapeProgress::new));
    public GloomEscapeProgress { elapsed = Math.clamp(elapsed, 0, EscapeSequence.LIMIT_TICKS); }
    public boolean pending() { return !dimension.isEmpty(); }
}
