package cn.wwl.orimod.gametest;

import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class RegenerateFxGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (boolean veto : new boolean[]{false,true}) tests.add(new TestFunction("regenerate_fx", "regenerate_fx.actual_heal_" + !veto,
                "orimod_gametest:room", 40, 0, true, h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); p.setData(SpiritData.FORM,true); p.setHealth(8); p.setData(SpiritData.ENERGY,50f);
                Consumer<LivingHealEvent> listener = event -> { if (veto && event.getEntity()==p) event.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(listener);
                try {
                    FxGameTests.drain(c.channel());
                    h.assertTrue(Regenerate.keepAlive(p),"Channel refused"); SpiritFxLifecycle.sync(p,p.level().getGameTime());
                    var start=FxGameTests.fx(FxGameTests.drain(c.channel()));
                    h.assertTrue(start.stream().anyMatch(v -> v.kind().name().equals("REGENERATE") && v.phase()==SpiritFxPhase.START),"No gathering FX at start");
                    var s=p.getData(SpiritData.ENERGY_STATE).regenerate;
                    s.began=p.level().getGameTime()-RecoveryTuning.CHANNEL_TICKS; Regenerate.tick(p); Regenerate.tick(p);
                    var healed=FxGameTests.fx(FxGameTests.drain(c.channel()));
                    h.assertValueEqual((int)healed.stream().filter(v -> v.kind().name().equals("REGENERATE_HEAL")).count(),veto?0:1,"Heal pulse not tied once to real healing");
                    Fixtures.near(h,p.getHealth(),veto?8:14,"Actual heal amount");
                    Regenerate.release(p); SpiritFxLifecycle.sync(p,p.level().getGameTime());
                    var end=FxGameTests.fx(FxGameTests.drain(c.channel()));
                    h.assertTrue(end.stream().anyMatch(v -> v.kind().name().equals("REGENERATE") && v.phase()==SpiritFxPhase.STOP),"No cancel/stop FX");
                    h.assertTrue(end.stream().noneMatch(v -> v.kind().name().equals("REGENERATE_HEAL")),"Cancel emitted heal pulse");
                } finally { NeoForge.EVENT_BUS.unregister(listener); Regenerate.release(p); SpiritFxLifecycle.stopAll(p,"test"); }
            }
            h.succeed();
        }));
        return tests;
    }
}
