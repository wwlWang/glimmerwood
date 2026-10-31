package cn.wwl.orimod.devtools;

import cn.wwl.orimod.combat.CombatTargets;
import cn.wwl.orimod.combat.SpiritFlame;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.progression.SpiritCooldowns;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import java.util.*;

/** Read-only server diagnostics; visual absence alone does not establish a missed attack. */
final class FlameProbe {
    static Map<String,Object> inspect(ServerPlayer player) {
        var result=new LinkedHashMap<String,Object>();
        long now=SpiritCooldowns.now(player),readyAt=player.getData(SpiritData.COOLDOWNS).flameReadyAt;
        result.put("owner",player.getUUID().toString());result.put("gameTime",now);result.put("readyAt",readyAt);
        result.put("enabled",player.getData(SpiritData.COMPANION_FLAME));result.put("fired",SpiritFlame.fired);
        result.put("ready",SpiritFlame.ready(SpiritData.isSpirit(player)&&SpiritData.isAwakened(player),player.isAlive(),player.isSpectator(),now,readyAt));
        var selected=SpiritFlame.target(player);result.put("selected",selected==null?null:selected.getUUID().toString());
        result.put("mobs",player.level().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(16)).stream().map(mob->{
            var row=new LinkedHashMap<String,Object>();
            row.put("id",mob.getId());row.put("uuid",mob.getUUID().toString());row.put("type",mob.getType().toString());
            row.put("health",mob.getHealth());row.put("distance",player.distanceTo(mob));
            row.put("eligible",CombatTargets.canHit(player,mob));row.put("orphanEligible",CombatTargets.canHitWithoutOwner(mob));
            row.put("visible",SpiritTargets.visible(player,mob.getBoundingBox().getCenter()));row.put("invisible",mob.isInvisible());
            row.put("target",mob.getTarget()==null?null:mob.getTarget().getUUID().toString());
            row.put("lastHurtBy",mob.getLastHurtByMob()==null?null:mob.getLastHurtByMob().getUUID().toString());
            var brain=mob.getBrain();row.put("angryAt",brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)?brain.getMemory(MemoryModuleType.ANGRY_AT).orElseThrow().toString():null);
            return row;
        }).toList());
        return result;
    }
}
