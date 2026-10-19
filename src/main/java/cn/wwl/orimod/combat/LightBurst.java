package cn.wwl.orimod.combat;

import cn.wwl.orimod.fx.SpiritFx;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * R39 Light Burst (WotW): a thrown orb of light. It is a Bash anchor for its own thrower while it flies;
 * Bashing it or hitting anything detonates it, as does its fuse. The blast hurts visible hostiles nearby and
 * never breaks blocks or hurts players.
 */
public final class LightBurst extends ThrowableProjectile {
    public static final float COST = 15, DAMAGE = 6;
    public static final double SPEED = 1.1, RADIUS = 3;
    public static final int FUSE_TICKS = 40, COOLDOWN_TICKS = 20;
    private boolean detonated;

    public LightBurst(EntityType<? extends LightBurst> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected double getDefaultGravity() { return .02; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) cn.wwl.orimod.client.fx.LightBurstTrail.tick(this);
        else if (tickCount >= FUSE_TICKS) detonate();
    }

    @Override protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) detonate();
    }

    /** Explode once: damage visible hostiles within {@link #RADIUS}, ring FX and sound, then vanish. */
    public int detonate() {
        if (detonated || !(level() instanceof ServerLevel level)) return 0;
        detonated = true;
        var owner = getOwner() instanceof ServerPlayer player ? player : null;
        int hit = 0;
        for (var target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS), e -> e instanceof Enemy && e.isAlive()
                && e.distanceToSqr(this) <= RADIUS * RADIUS && (owner == null || !owner.isAlliedTo(e)))) {
            var source = owner == null ? damageSources().magic() : damageSources().indirectMagic(this, owner);
            if (target.hurt(source, DAMAGE)) {
                hit++;
                Vec3 push = target.position().subtract(position()).normalize().scale(.5);
                target.push(push.x, .25, push.z);
            }
        }
        SpiritFx.emit(level, SpiritFxKind.STOMP_IMPACT, getX(), getY(), getZ(), null);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, .8f, 1.3f);
        discard();
        exploded++;
        return hit;
    }

    /** Diagnostics: bursts exploded since server start. */
    public static int exploded;

    /** Pure rule for throwing. */
    public static String refusal(boolean spirit, boolean unlocked, float energy, long now, long readyAt) {
        if (!spirit) return "not_spirit";
        if (!unlocked) return "locked";
        if (now < readyAt) return "cooldown";
        if (energy < COST) return "energy";
        return null;
    }

    public static LightBurst toss(ServerPlayer player) {
        long now = player.level().getGameTime();
        var state = player.getData(SpiritData.ENERGY_STATE);
        String refusal = refusal(SpiritData.isSpirit(player), Ability.LIGHT_BURST.unlocked(player), SpiritProgression.energy(player), now, state.burstReadyAt);
        if (refusal != null) {
            if (!refusal.equals("cooldown")) player.displayClientMessage(Component.translatable("message.orimod.light_burst." + refusal), true);
            return null;
        }
        if (!SpiritProgression.spendEnergy(player, COST)) return null;
        state.burstReadyAt = now + COOLDOWN_TICKS;
        var burst = new LightBurst(cn.wwl.orimod.world.OriEntities.LIGHT_BURST.get(), player.level());
        burst.setOwner(player);
        burst.setPos(player.getEyePosition().add(player.getLookAngle().scale(.5)));
        burst.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, (float) SPEED, 0);
        player.level().addFreshEntity(burst);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, .6f, 1.6f);
        return burst;
    }
}
