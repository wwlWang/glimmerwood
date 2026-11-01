package cn.wwl.orimod.combat;

import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * R60 Spirit Spear (WotW): a fast, straight spear of light that pierces up to {@link #PIERCE} targets and
 * shatters on the first block. Thrown with the Spirit Spear item; costs energy.
 */
public final class SpiritSpear extends ThrowableProjectile {
    public static final float COST = 12, DAMAGE = 7, SPEED = 2.2f;
    public static final int PIERCE = 3, COOLDOWN_TICKS = 24, LIFETIME = 30;
    private final Set<Integer> hit = new HashSet<>();

    public SpiritSpear(EntityType<? extends SpiritSpear> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected double getDefaultGravity() { return 0; }

    public int hits() { return hit.size(); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) cn.wwl.orimod.client.fx.SpearTrail.tick(this);
        else if (tickCount > LIFETIME) discard();
    }

    /** Pierce: damage each valid target once; vanish after {@link #PIERCE} hits. @return whether it was damaged. */
    public boolean pierce(LivingEntity target) {
        if (isRemoved() || !(getOwner() instanceof ServerPlayer owner) || !CombatTargets.canHitDirect(owner, target) || !hit.add(target.getId())) return false;
        boolean hurt = target.hurt(damageSources().indirectMagic(this, owner), DAMAGE);
        if (hit.size() >= PIERCE) discard();
        return hurt;
    }

    @Override protected boolean canHitEntity(net.minecraft.world.entity.Entity entity) {
        return super.canHitEntity(entity) && !hit.contains(entity.getId())
                && (!(getOwner() instanceof Player owner) || !(entity instanceof LivingEntity living) || CombatTargets.canHitDirect(owner, living));
    }

    @Override protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide && result.getEntity() instanceof LivingEntity living) pierce(living);
    }

    @Override protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide) {
            cn.wwl.orimod.block.CrackedRuin.spear(this, result);
            if (getOwner() instanceof ServerPlayer p) cn.wwl.orimod.block.BlightInteractions.hit(p,result.getBlockPos(),cn.wwl.orimod.world.BlightRules.Source.SPEAR);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, .8f, 1.6f);
            discard();
        }
    }

    public static String refusal(boolean spirit, boolean unlocked, float energy, long now, long readyAt) {
        if (!spirit) return "not_spirit";
        if (!unlocked) return "locked";
        if (now < readyAt) return "cooldown";
        if (energy < COST) return "energy";
        return null;
    }

    public static SpiritSpear thrown(ServerPlayer player) {
        long now = cn.wwl.orimod.progression.SpiritCooldowns.now(player);
        var state = player.getData(SpiritData.COOLDOWNS);
        String refusal = refusal(SpiritData.isSpirit(player), Ability.SPEAR.unlocked(player), SpiritProgression.energyForUse(player), now, state.spearReadyAt);
        if (refusal != null) {
            if (!refusal.equals("cooldown")) player.displayClientMessage(Component.translatable("message.orimod.spear." + refusal), true);
            return null;
        }
        if (!SpiritProgression.spendEnergy(player, COST)) return null;
        state.spearReadyAt = now + COOLDOWN_TICKS;
        var spear = new SpiritSpear(cn.wwl.orimod.world.OriEntities.SPIRIT_SPEAR.get(), player.level());
        spear.setOwner(player);
        spear.setPos(player.getEyePosition().add(player.getLookAngle().scale(.6)).add(0, -.1, 0));
        spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, SPEED, 0);
        player.level().addFreshEntity(spear);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1, 1.3f);
        return spear;
    }
}
