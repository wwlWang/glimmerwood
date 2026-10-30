package cn.wwl.orimod.entity;

import cn.wwl.orimod.config.OriServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import java.util.EnumSet;

/** Never clears vanilla goals/attributes or breaks a leash. Navigation decisions are globally budgeted. */
public final class CorruptAnimalGoal extends Goal {
    private final Animal animal;
    private ServerPlayer target;
    private long nextAttack,nextDecision;
    private boolean navigating;
    public CorruptAnimalGoal(Animal animal){this.animal=animal;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public boolean eligible(ServerPlayer p){
        return p!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&animal.level()==p.level()
                &&CorruptionRules.attack(animal.level().getDifficulty()==Difficulty.PEACEFUL,animal.isBaby(),animal.isLeashed(),animal.isVehicle(),true,animal.distanceToSqr(p));
    }
    public boolean active(){return OriServerConfig.blightEnabled()&&CorruptedAnimals.ownerActive(animal)&&!animal.isNoAi()&&!animal.isInvulnerable();}
    /** One selected player search plus one navigation request, at most once per ten ticks per animal. */
    public boolean decide(long tick){
        if(tick<nextDecision)return false;nextDecision=tick+10;
        if(!active()){stop();return false;}
        target=null;double best=CorruptionRules.RANGE*CorruptionRules.RANGE;
        for(var p:((net.minecraft.server.level.ServerLevel)animal.level()).players())if(eligible(p)){
            double d=animal.distanceToSqr(p);if(d<best&&animal.getSensing().hasLineOfSight(p)){best=d;target=p;}
        }
        if(target!=null){animal.getNavigation().moveTo(target,1.15);navigating=true;}else stop();
        return true;
    }
    @Override public boolean canUse(){return active()&&eligible(target);}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick(){
        if(!canUse()){stop();return;}
        animal.getLookControl().setLookAt(target,30,30);
        long now=animal.level().getGameTime();double reach=animal.getBbWidth()+target.getBbWidth()+.3;
        if(now>=nextAttack&&animal.distanceToSqr(target)<=reach*reach&&animal.getSensing().hasLineOfSight(target)){
            nextAttack=now+CorruptionRules.ATTACK_INTERVAL;target.hurt(animal.damageSources().mobAttack(animal),2);
        }
    }
    @Override public void stop(){if(navigating){animal.getNavigation().stop();navigating=false;}if(target!=null&&animal.getTarget()==target)animal.setTarget(null);target=null;}
}
