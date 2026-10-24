package cn.wwl.orimod.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** A follow-up may cross only this combo's immediately preceding hurt window. */
final class BladeDamage {
    private BladeDamage() {}
    static boolean hurt(ServerPlayer player, LivingEntity target, CombatState state, float amount) {
        var prior = state.comboHits.get(target.getUUID());
        var ordinary = player.damageSources().playerAttack(player);
        var source = new DamageSource(ordinary.typeHolder(), player) {
            @Override public boolean is(TagKey<DamageType> tag) {
                // Scoped to this hurt invocation: no global tag, invulnerableTime reset, or change to
                // creative/general invulnerability. A successful intervening hit invalidates the receipt.
                return tag.equals(DamageTypeTags.BYPASSES_COOLDOWN) && prior != null
                        && prior.stage() == state.stage - 1 && target.getLastDamageSource() == prior.source()
                        || super.is(tag);
            }
        };
        boolean hit = target.hurt(source, amount);
        if (hit) state.comboHits.put(target.getUUID(),new CombatState.ComboHit(state.stage,source));
        return hit;
    }
}
