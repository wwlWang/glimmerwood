package cn.wwl.orimod.progression;

import cn.wwl.orimod.entity.RecoveryOrb;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.OriEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class RecoveryOrbs {
    private RecoveryOrbs() {}
    public static void drops(LivingDropsEvent e) {
        var victim = e.getEntity();
        if (!(victim instanceof Enemy) || !(e.getSource().getEntity() instanceof ServerPlayer p) || !SpiritData.isAwakened(p)
                || !victim.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;
        var random = victim.getRandom();
        for (int i = 0; i < RecoveryRules.orbGroups(victim.getMaxHealth()); i++) for (boolean life : new boolean[]{true, false}) {
            if (random.nextFloat() >= RecoveryTuning.ORB_CHANCE) continue;
            var orb = new RecoveryOrb(OriEntities.RECOVERY_ORB.get(), victim.level()); orb.life(life);
            orb.setPos(victim.position().add(0, .4, 0));
            orb.setDeltaMovement((random.nextDouble() - .5) * .15, .16, (random.nextDouble() - .5) * .15);
            victim.level().addFreshEntity(orb);
        }
    }
}
