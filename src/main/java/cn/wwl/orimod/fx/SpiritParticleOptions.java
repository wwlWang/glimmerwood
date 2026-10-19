package cn.wwl.orimod.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** FX02 (R32): colour, size and lifetime of one spirit particle, set per definition layer. */
public record SpiritParticleOptions(ParticleType<SpiritParticleOptions> type, int rgb, float size, int lifetime) implements ParticleOptions {
    @Override public ParticleType<?> getType() { return type; }

    public float red() { return ((rgb >> 16) & 255) / 255f; }
    public float green() { return ((rgb >> 8) & 255) / 255f; }
    public float blue() { return (rgb & 255) / 255f; }

    public static MapCodec<SpiritParticleOptions> codec(ParticleType<SpiritParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("rgb").forGetter(SpiritParticleOptions::rgb),
                Codec.floatRange(.01f, 4f).fieldOf("size").forGetter(SpiritParticleOptions::size),
                Codec.intRange(1, 200).fieldOf("lifetime").forGetter(SpiritParticleOptions::lifetime)
        ).apply(i, (rgb, size, life) -> new SpiritParticleOptions(type, rgb, size, life)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, SpiritParticleOptions> streamCodec(ParticleType<SpiritParticleOptions> type) {
        return StreamCodec.composite(ByteBufCodecs.INT, SpiritParticleOptions::rgb, ByteBufCodecs.FLOAT, SpiritParticleOptions::size,
                ByteBufCodecs.VAR_INT, SpiritParticleOptions::lifetime, (rgb, size, life) -> new SpiritParticleOptions(type, rgb, size, life));
    }

    /** Particle type whose options carry colour/size/lifetime. */
    public static final class Type extends ParticleType<SpiritParticleOptions> {
        private final MapCodec<SpiritParticleOptions> codec = SpiritParticleOptions.codec(this);
        private final StreamCodec<RegistryFriendlyByteBuf, SpiritParticleOptions> stream = SpiritParticleOptions.streamCodec(this);
        public Type() { super(false); }
        @Override public MapCodec<SpiritParticleOptions> codec() { return codec; }
        @Override public StreamCodec<? super RegistryFriendlyByteBuf, SpiritParticleOptions> streamCodec() { return stream; }
    }
}
