package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class BladeGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var type : new EntityType<?>[]{EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.COW,
                EntityType.PIG, EntityType.IRON_GOLEM, EntityType.ARMOR_STAND, EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN}) {
            add(tests, "protect_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, false, false, false));
        }
        for (var type : new EntityType<?>[]{EntityType.WOLF, EntityType.CAT}) {
            add(tests, "protect_tamed_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, true, false, false));
        }
        for (var type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER}) {
            add(tests, "hit_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, false, false, true));
        }
        for (var type : new EntityType<?>[]{EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN}) {
            add(tests, "hit_attacking_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, false, true, true));
        }
        return tests;
    }

    private static void checkHit(GameTestHelper helper, EntityType<?> type, boolean tamed, boolean attacking, boolean expectedHit) {
        var player = Fixtures.player(helper); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        var target = (LivingEntity)helper.spawn(type, new Vec3(5.5, 2, 7));
        if (target instanceof Mob mob) { mob.setNoAi(true); if (attacking) mob.setTarget(player); }
        if (tamed) ((TamableAnimal)target).tame(player);
        float before = target.getHealth();
        try {
            SpiritCombat.attack(player);
            var state = player.getData(SpiritData.COMBAT);
            helper.assertValueEqual(state.mode, "blade", "Blade fixture did not start");
            state.startedAt -= SpiritCombat.windup(1);
            SpiritCombat.tick(new PlayerTickEvent.Post(player));
            if (expectedHit) {
                helper.assertTrue(state.hitTargets.contains(target.getUUID()), "Hostile target not reached by swing");
                float afterHit = target.getHealth();
                helper.assertTrue(afterHit < before, "Hostile target took no actual damage");
                if (target.getArmorValue() == 0) Fixtures.near(helper, afterHit, before - 4, "Unarmored blade damage");
                helper.assertValueEqual(state.bladeHits, 1, "One successful blade hit");
                // Clear vanilla immunity so only our per-swing ledger prevents a duplicate hit.
                target.invulnerableTime = 0;
                SpiritCombat.tick(new PlayerTickEvent.Post(player));
                Fixtures.near(helper, target.getHealth(), afterHit, "Duplicate blade damage within swing");
            } else {
                helper.assertFalse(state.hitTargets.contains(target.getUUID()), "Protected entity entered the damage path");
                Fixtures.near(helper, target.getHealth(), before, "Protected entity health");
                helper.assertTrue(target.isAlive() && !target.isRemoved(), "Protected entity was destroyed");
            }
        } finally { SpiritCombat.cancel(player, "test_cleanup"); target.discard(); }
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("blade", "blade." + name, "orimod_gametest:room", 30, 0, true,
                helper -> { run.accept(helper); helper.succeed(); }));
    }
}
