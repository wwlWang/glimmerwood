package cn.wwl.orimod.fx;

import cn.wwl.orimod.OriMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** FX02: the mod's own particle types used by spirit effect definitions (sprites made by scripts/make-spirit-particles.py). */
public final class OriParticles {
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, OriMod.MOD_ID);
    /** Soft round light with a halo; drifts, gently pulses, fades. The default spirit glow. */
    public static final Supplier<SpiritParticleOptions.Type> SPIRIT_MOTE = TYPES.register("spirit_mote", SpiritParticleOptions.Type::new);
    /** Four-point glint for hits, hooks and launches; short and sharp. */
    public static final Supplier<SpiritParticleOptions.Type> SPIRIT_SPARK = TYPES.register("spirit_spark", SpiritParticleOptions.Type::new);
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private OriParticles() {}
}
