package cn.wwl.orimod.fx;

import cn.wwl.orimod.OriMod;
import java.util.function.Supplier;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** FX02: the mod's own particle types used by spirit effect definitions (sprites made by scripts/make-spirit-particles.py). */
public final class OriParticles {
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, OriMod.MOD_ID);
    /** Soft round light, fades and shrinks; the default spirit glow. */
    public static final Supplier<SimpleParticleType> SPIRIT_MOTE = TYPES.register("spirit_mote", () -> new SimpleParticleType(false));
    /** Short four-point glint for hits, hooks and launches. */
    public static final Supplier<SimpleParticleType> SPIRIT_SPARK = TYPES.register("spirit_spark", () -> new SimpleParticleType(false));
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private OriParticles() {}
}
