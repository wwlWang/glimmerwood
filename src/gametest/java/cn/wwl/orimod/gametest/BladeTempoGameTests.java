package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class BladeTempoGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (boolean foreign : new boolean[]{false,true}) tests.add(new TestFunction("blade_tempo", "blade_tempo.seven_tick_foreign_"+foreign,
                "orimod_gametest:room",40,0,true,h -> {
            try (var c=Fixtures.connectedPlayer(h); var other=Fixtures.connectedPlayer(h)) {
                var p=c.player(); p.setData(SpiritData.FORM,true);
                p.setPos(h.absoluteVec(new Vec3(5.5,3,5.5))); p.setYRot(0); p.setOnGround(true);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_BLADE.get()));
                var target=EntityType.HUSK.create(h.getLevel()); target.setNoAi(true);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100);
                target.setPos(h.absoluteVec(new Vec3(5.5,3,7))); h.getLevel().addFreshEntity(target);
                try {
                    SpiritCombat.attack(p); var s=p.getData(SpiritData.COMBAT); int began=0;
                    var ticks=new ArrayList<Integer>(); var damage=new ArrayList<Float>();
                    // Exercise the actual combat/hurt functions while advancing their elapsed clock and vanilla
                    // hurt cooldown once per simulated tick. No entity ticks/AI/companion damage interleave.
                    for(int t=0;t<=24;t++) {
                        s.startedAt=p.level().getGameTime()-(t-began);
                        if(t==1||t==7)SpiritCombat.attack(p);
                        if(t==3) {
                            float before=target.getHealth();
                            target.hurt(other.player().damageSources().playerAttack(other.player()),3);
                            Fixtures.near(h,target.getHealth(),before,"Other player inherited our combo immunity bypass");
                        }
                        if(foreign && t==8) target.hurt(other.player().damageSources().playerAttack(other.player()),8);
                        float hp=target.getHealth(); int stage=s.stage;
                        SpiritCombat.tick(new PlayerTickEvent.Post(p));
                        if(target.getHealth()<hp){ticks.add(t);damage.add(hp-target.getHealth());}
                        if(s.stage!=stage)began=t;
                        target.invulnerableTime=Math.max(0,target.invulnerableTime-1);
                    }
                    h.assertValueEqual(ticks,foreign?List.of(2,16):List.of(2,9,16),"Hit timestamps/foreign immunity");
                    h.assertValueEqual(damage,foreign?List.of(3f,5f):List.of(3f,4f,5f),"Combo damage balance");
                    h.assertValueEqual(s.mode,"idle","Combo did not finish by 24 ticks");
                    System.out.println("BLADE_TEMPO foreign="+foreign+" hits="+ticks+" damage="+damage+" end=24");
                } finally { SpiritCombat.cancel(p,"test"); target.discard(); }
            }
            h.succeed();
        }));
        return tests;
    }
}
