package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class SkillTargetGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (int bits : new int[]{0, 2, 8, 10}) {
            for (boolean nearGrapple : new boolean[]{false, true}) {
                String name = "skill_targets.bits_" + bits + "_near_grapple_" + nearGrapple;
                tests.add(new TestFunction("skill_targets", name, "orimod_gametest:room", 40, 0, true, h -> {
                    try (var c = Fixtures.connectedPlayer(h)) {
                        var p = c.player(); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, bits);
                        p.setPos(h.absoluteVec(new Vec3(5.5, 3, 5.5))); p.setOnGround(false);
                        BlockPos near = h.absolutePos(new BlockPos(6, 4, 7)), far = h.absolutePos(new BlockPos(4, 4, 9));
                        h.getLevel().setBlock(near, (nearGrapple ? OriBlocks.GRAPPLE_FLOWER : OriBlocks.BASH_ANCHOR).get().defaultBlockState(), 2);
                        h.getLevel().setBlock(far, (nearGrapple ? OriBlocks.BASH_ANCHOR : OriBlocks.GRAPPLE_FLOWER).get().defaultBlockState(), 2);
                        var target = SpiritTargets.find(p, 0, 0);
                        if (bits == 0) h.assertTrue(target == null, "Unlearned targets selected");
                        else {
                            boolean expectedGrapple = bits == 8 || bits == 10 && nearGrapple;
                            h.assertTrue(target != null && target.grapple() == expectedGrapple, "Locked target stole the learned target");
                            h.assertValueEqual(target.block(), expectedGrapple == nearGrapple ? near : far, "Both-learned priority changed");
                            p.setData(SpiritData.ABILITIES, 0);
                            h.assertFalse(target.valid(p), "Revoked skill kept a valid target");
                        }
                    }
                    h.succeed();
                }));
            }
        }
        tests.add(new TestFunction("skill_targets", "skill_targets.grapple_only_ignores_enemy", "orimod_gametest:room", 40, 0, true, h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, 8);
                p.setPos(h.absoluteVec(new Vec3(5.5, 3, 5.5))); p.setOnGround(false);
                var enemy = EntityType.HUSK.create(h.getLevel());
                enemy.setPos(h.absoluteVec(new Vec3(6.5, 3, 7.5))); enemy.setNoAi(true); h.getLevel().addFreshEntity(enemy);
                var flower = h.absolutePos(new BlockPos(4, 4, 9));
                h.getLevel().setBlock(flower, OriBlocks.GRAPPLE_FLOWER.get().defaultBlockState(), 2);
                try {
                    var target = SpiritTargets.find(p, 0, 0);
                    h.assertTrue(target != null && target.grapple() && flower.equals(target.block()), "Locked enemy stole grapple");
                } finally { enemy.discard(); }
            }
            h.succeed();
        }));
        return tests;
    }
}
