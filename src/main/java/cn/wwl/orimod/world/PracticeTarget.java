package cn.wwl.orimod.world;

import cn.wwl.orimod.item.OriItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** A non-hostile, session-owned crystal. It cannot produce loot, XP, or saved stray entities. */
public final class PracticeTarget extends PathfinderMob {
    public UUID owner, session;
    private boolean freePractice;
    public void practiceOwner(UUID player){owner=player;session=null;freePractice=true;}
    private boolean ownedBy(ServerPlayer player){return player.getUUID().equals(owner)&&(freePractice||SpiritChallenge.ownsTarget(player,this));}
    public PracticeTarget(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true); setNoGravity(true); setPersistenceRequired();
        setCustomName(Component.translatable("entity.orimod.practice_target")); setCustomNameVisible(true);
    }
    public static AttributeSupplier.Builder attributes() { return createMobAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.KNOCKBACK_RESISTANCE, 1); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) { discard(); return true; }
        if (!(source.getDirectEntity() instanceof ServerPlayer player) || !ownedBy(player)
                || !player.getMainHandItem().is(OriItems.SPIRIT_BLADE)) return false;
        boolean hit = super.hurt(source, amount);
        if (hit && !isAlive() && !freePractice) SpiritChallenge.targetDefeated(player, this);
        return hit;
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && (owner == null || !(level().getPlayerByUUID(owner) instanceof ServerPlayer player) || !ownedBy(player))) discard();
    }
}
