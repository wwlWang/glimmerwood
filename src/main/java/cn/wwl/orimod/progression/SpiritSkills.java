package cn.wwl.orimod.progression;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpiritSkills {
    private SpiritSkills() {}
    public static void tick(ServerPlayer p) {
        var old=p.getData(SpiritData.SKILL_BINDINGS);
        var current=old.migrate(SpiritData.isAwakened(p),p.getData(SpiritData.ABILITIES))
                .withoutUnlearned(SpiritData.isAwakened(p),p.getData(SpiritData.ABILITIES))
                .discover(SpiritData.isAwakened(p),p.getData(SpiritData.ABILITIES));
        if(!current.equals(old))p.setData(SpiritData.SKILL_BINDINGS,current);
        if(!old.initialized()&&current.initialized()&&!current.slots().contains("regenerate")
                &&SpiritProgression.reward(p,"hint/skill_wheel",0,0))
            p.sendSystemMessage(Component.translatable("message.orimod.skill_wheel",Component.keybind("key.orimod.panel")));
        for(int i=0;i<3;i++){
            var skill=current.skill(i);
            if(skill!=null&&!old.known().contains(skill.id())&&(old.initialized()||skill==WheelSkill.REGENERATE)){
                String key=switch(i){case 0->"key.orimod.light_burst";case 1->"key.orimod.charge_flame";default->"key.orimod.flash";};
                p.sendSystemMessage(Component.translatable("message.orimod.skill_bound",Component.translatable(skill.titleKey()),Component.keybind(key))
                        .append(" ").append(Component.translatable("message.orimod.skill_wheel",Component.keybind("key.orimod.panel"))));
            }
        }
        var ready=new java.util.ArrayList<Long>();
        for(int i=0;i<3;i++){var skill=current.skill(i);ready.add(skill==null?0:skill.readyAt(p));}
        if(!ready.equals(p.getData(SpiritData.SKILL_READY)))p.setData(SpiritData.SKILL_READY,java.util.List.copyOf(ready));
    }
    public static boolean bind(ServerPlayer p,int slot,WheelSkill skill) {
        if(!p.isAlive()||p.isSpectator()||!SpiritData.isAwakened(p)||Restoration.active(p))return false;
        tick(p);var old=p.getData(SpiritData.SKILL_BINDINGS);
        var next=old.bind(slot,skill,true,p.getData(SpiritData.ABILITIES));
        if(next.equals(old))return false;
        Regenerate.release(p);cn.wwl.orimod.combat.Flash.stop(p);
        p.getData(SpiritData.ENERGY_STATE).chargeFlameUntil=Long.MIN_VALUE;
        cn.wwl.orimod.network.AnimationActionPayload.spell(p,cn.wwl.orimod.network.AnimationActionPayload.Action.CAST_STOP);
        p.setData(SpiritData.SKILL_BINDINGS,next);tick(p);return true;
    }
}
