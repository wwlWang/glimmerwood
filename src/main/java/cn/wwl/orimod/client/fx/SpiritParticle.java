package cn.wwl.orimod.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** FX02: full-bright spirit light that drifts, slows, shrinks and fades; sprite frames follow its age. */
public final class SpiritParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    SpiritParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean spark) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.xd = vx; this.yd = vy; this.zd = vz;      // keep the definition's speed instead of vanilla's random kick
        this.friction = spark ? .80f : .90f;
        this.gravity = spark ? 0 : -.004f;             // motes rise very slightly
        this.lifetime = (spark ? 6 : 14) + random.nextInt(spark ? 4 : 10);
        this.startSize = (spark ? .09f : .12f) * (.8f + random.nextFloat() * .4f);
        this.quadSize = startSize;
        if (spark) setColor(.92f, .98f, 1f); else setColor(.70f, .93f, 1f);
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            quadSize = startSize * (1 - .6f * t);
            alpha = t < .7f ? 1 : 1 - (t - .7f) / .3f;
        }
    }

    @Override public int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public record Provider(SpriteSet sprites, boolean spark) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SpiritParticle(level, x, y, z, vx, vy, vz, sprites, spark);
        }
    }

    public static void register(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), sprites -> new Provider(sprites, false));
        event.registerSpriteSet(cn.wwl.orimod.fx.OriParticles.SPIRIT_SPARK.get(), sprites -> new Provider(sprites, true));
    }
}
