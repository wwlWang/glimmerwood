package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.fx.SpiritParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;

/** FX02: full-bright spirit light. Motes swell in, glow, then shrink and fade; sparks flash and die fast. */
public final class SpiritParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;
    private final boolean spark;

    SpiritParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, SpiritParticleOptions o, boolean spark) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites; this.spark = spark;
        this.xd = vx; this.yd = vy; this.zd = vz;      // keep the definition's velocity instead of vanilla's random kick
        this.friction = spark ? .78f : .88f;
        this.gravity = spark ? 0 : -.003f;             // motes rise very slightly
        this.lifetime = Math.max(2, Math.round(o.lifetime() * (.8f + random.nextFloat() * .4f)));
        this.startSize = o.size() * (.85f + random.nextFloat() * .3f);
        this.quadSize = startSize;
        setColor(o.red(), o.green(), o.blue());
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float t = (float) age / lifetime;
        if (spark) { quadSize = startSize * (1.2f - t); alpha = 1 - t * t; }
        else {
            float swell = Math.min(1, t * 6);                      // pop in over the first frames
            quadSize = startSize * (.55f + .45f * swell) * (1 - .45f * t);
            alpha = t < .6f ? 1 : 1 - (t - .6f) / .4f;
        }
    }

    @Override public int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites, boolean spark) implements ParticleProvider<SpiritParticleOptions> {
        @Override public Particle createParticle(SpiritParticleOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SpiritParticle(level, x, y, z, vx, vy, vz, sprites, o, spark);
        }
    }

    public static void register(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), sprites -> new Provider(sprites, false));
        event.registerSpriteSet(cn.wwl.orimod.fx.OriParticles.SPIRIT_SPARK.get(), sprites -> new Provider(sprites, true));
    }
}
