package cn.wwl.orimod.client.fx;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** A loop that follows its entity and ends itself as soon as the effect or the entity is gone. */
final class SpiritLoopSound extends AbstractTickableSoundInstance {
    private final Entity entity;
    private boolean ended;

    SpiritLoopSound(SoundEvent sound, Entity entity, float volume, float pitch) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.entity = entity; this.volume = volume; this.pitch = pitch;
        this.looping = true; this.delay = 0;
        this.x = entity.getX(); this.y = entity.getY(); this.z = entity.getZ();
    }

    void end() { ended = true; stop(); }
    boolean ended() { return ended || isStopped(); }

    @Override public void tick() {
        if (ended || entity.isRemoved() || !entity.isAlive()) { end(); return; }
        x = entity.getX(); y = entity.getY(); z = entity.getZ();
    }
}
