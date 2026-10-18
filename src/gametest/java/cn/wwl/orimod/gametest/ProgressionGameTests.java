package cn.wwl.orimod.gametest;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.Shard;
import cn.wwl.orimod.progression.SpiritProgress;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class ProgressionGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "form_requires_core", h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.AWAKENED, false);
            h.assertFalse(SpiritForm.toggle(p), "Unawakened player must remain human");
            h.assertFalse(SpiritData.isSpirit(p), "Rejected toggle changed form");
        });
        add(tests, "standing_form_dimensions", h -> {
            var p = Fixtures.player(h);
            h.assertTrue(SpiritForm.toggle(p), "Open space should allow spirit form");
            Fixtures.near(h, p.getBbHeight(), 1.2, "Spirit collision height");
            Fixtures.near(h, p.getEyeHeight(), 1.04, "Spirit eye height");
        });
        add(tests, "crouching_form_dimensions", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p); p.setPose(Pose.CROUCHING); p.refreshDimensions();
            Fixtures.near(h, p.getBbHeight(), 1, "Crouched spirit collision height");
            Fixtures.near(h, p.getEyeHeight(), .85, "Crouched spirit eye height");
        });
        add(tests, "low_ceiling_refuses_human", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p);
            // Top slab leaves 1.5 blocks of headroom above the floor.
            h.setBlock(new BlockPos(5, 3, 5), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            var position = p.position();
            h.assertFalse(SpiritForm.toggle(p), "Cannot restore 1.8-block human under 1.5-block ceiling");
            h.assertTrue(SpiritData.isSpirit(p), "Rejected toggle lost spirit form");
            h.assertValueEqual(p.position(), position, "Rejected toggle must not push player");
        });
        add(tests, "open_ceiling_restores_human", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p);
            var humanBox = net.minecraft.world.entity.EntityType.PLAYER.getDimensions().makeBoundingBox(p.position()).deflate(1.0E-5);
            String diagnostic = "pos=" + p.position() + " box=" + humanBox + " blocks="
                    + java.util.stream.StreamSupport.stream(h.getLevel().getBlockCollisions(p, humanBox).spliterator(), false)
                        .map(shape -> shape.bounds().toString()).toList();
            h.assertTrue(SpiritForm.toggle(p), "Open ceiling should permit human form; " + diagnostic);
            Fixtures.near(h, p.getBbHeight(), 1.8, "Human collision height");
        });
        for (var ability : Ability.values()) {
            add(tests, "buy_" + ability.id() + "_once", h -> {
                var p = Fixtures.player(h);
                p.setData(SpiritData.ABILITIES, 63 & ~ability.bit);
                p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(500));
                SpiritProgression.request(p, "ability", ability.id());
                SpiritProgression.request(p, "ability", ability.id());
                h.assertTrue(ability.unlocked(p), "Ability not unlocked");
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 500 - ability.cost, "Duplicate ability charge");
            });
        }
        for (var shard : Shard.values()) {
            add(tests, "buy_shard_" + shard.id() + "_once", h -> {
                var p = Fixtures.player(h); p.setData(SpiritData.ABILITIES, 63);
                p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(100));
                SpiritProgression.request(p, "shard", shard.id()); SpiritProgression.request(p, "shard", shard.id());
                h.assertTrue((p.getData(SpiritData.PROGRESS).ownedShards() & shard.bit) != 0, "Shard not owned");
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 100 - Shard.COST, "Duplicate shard charge");
            });
        }
        add(tests, "insufficient_light_rejects_purchase", h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(Ability.DASH.cost - 1));
            var before = p.getData(SpiritData.PROGRESS);
            SpiritProgression.request(p, "ability", Ability.DASH.id());
            h.assertFalse(Ability.DASH.unlocked(p), "Unaffordable ability granted");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS), before, "Rejected purchase changed progress");
        });
        add(tests, "missing_prerequisite_rejects_purchase", h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(500));
            var before = p.getData(SpiritData.PROGRESS);
            SpiritProgression.request(p, "ability", Ability.BASH.id());
            h.assertFalse(Ability.BASH.unlocked(p), "Ability bypassed prerequisite");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS), before, "Prerequisite rejection charged light");
        });
        add(tests, "capacity_max_rejects_further_charge", h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(500));
            for (int i = 0; i < 4; i++) SpiritProgression.request(p, "capacity", "");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).capacityLevel(), 3, "Capacity cap");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 365, "Capacity total charge");
        });
        add(tests, "claim_blade_once", h -> {
            var p = Fixtures.player(h);
            SpiritProgression.request(p, "claim", "blade"); SpiritProgression.request(p, "claim", "blade");
            h.assertValueEqual(p.getInventory().countItem(OriItems.SPIRIT_BLADE.get()), 1, "Duplicate starter blade");
        });
        add(tests, "locked_bow_cannot_be_claimed", h -> {
            var p = Fixtures.player(h); SpiritProgression.request(p, "claim", "bow");
            h.assertValueEqual(p.getInventory().countItem(OriItems.SPIRIT_BOW.get()), 0, "Locked bow granted");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).claimedWeapons(), 0, "Rejected claim consumed reward");
        });
        add(tests, "discovery_reward_is_idempotent", h -> {
            var p = Fixtures.player(h);
            h.assertTrue(SpiritProgression.reward(p, "overworld/0/64/0", 15, 0), "First discovery missing");
            h.assertFalse(SpiritProgression.reward(p, "overworld/0/64/0", 15, 0), "Duplicate discovery granted");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 15, "Discovery light total");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).rewards().get("overworld/0/64/0"), 1, "Discovery count");
        });
        add(tests, "challenge_repeat_uses_repeat_reward", h -> {
            var p = Fixtures.player(h);
            SpiritProgression.reward(p, "challenge/0/64/0", 30, 5);
            SpiritProgression.reward(p, "challenge/0/64/0", 30, 5);
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 35, "Challenge repeat reward");
        });
        add(tests, "light_grant_caps_without_overflow", h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(SpiritProgress.LIGHT_LIMIT - 2));
            h.assertValueEqual(SpiritProgression.grantLight(p, Integer.MAX_VALUE), 2, "Bounded grant");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), SpiritProgress.LIGHT_LIMIT, "Light cap");
        });
        for (float amount : new float[]{-1, 0, Float.NaN, Float.POSITIVE_INFINITY, 101}) {
            add(tests, "reject_energy_" + Float.toString(amount).replace('.', '_'), h -> {
                var p = Fixtures.player(h);
                h.assertFalse(SpiritProgression.spendEnergy(p, amount), "Invalid/unaffordable energy spend accepted");
                Fixtures.near(h, SpiritProgression.energy(p), 100, "Rejected spend changed energy");
            });
        }
        add(tests, "exact_energy_spend", h -> {
            var p = Fixtures.player(h);
            h.assertTrue(SpiritProgression.spendEnergy(p, 100), "Exact energy spend rejected");
            Fixtures.near(h, SpiritProgression.energy(p), 0, "Exact spend balance");
            h.assertFalse(SpiritProgression.spendEnergy(p, 1), "Empty energy overdraft");
        });
        return tests;
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("progression", "progression." + name.toLowerCase(), "orimod_gametest:room", 30, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
