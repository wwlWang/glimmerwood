package cn.wwl.orimod.gametest;

import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_r117")
public final class ExplorationShardGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String mode : List.of("active", "human", "unequipped")) {
            add(tests, "magnet_" + mode, h -> {
                var p = player(h, Shard.MAGNET, mode);
                var item = item(p, 5.5); var outside = item(p, 6.1); var delayed = item(p, 3); delayed.setPickUpDelay(40);
                var reserved = item(p, 2); reserved.setTarget(UUID.randomUUID());
                var xp = new ExperienceOrb(h.getLevel(), p.getX() + 5.5, p.getY(), p.getZ(), 1); h.getLevel().addFreshEntity(xp);
                var entities = List.of(item, outside, delayed, reserved, xp);
                entities.forEach(e -> e.setDeltaMovement(Vec3.ZERO));
                try {
                    ExplorationShards.pull(p);
                    h.assertTrue((item.getDeltaMovement().x < 0) == mode.equals("active"), "Item eligibility");
                    h.assertTrue((xp.getDeltaMovement().x < 0) == mode.equals("active"), "XP eligibility");
                    for (var e : List.of(outside, delayed, reserved)) h.assertTrue(e.getDeltaMovement().equals(Vec3.ZERO), "Radius/pickup restriction ignored");
                } finally { entities.forEach(e -> e.discard()); }
            });
            add(tests, "breath_" + mode, h -> {
                var p = player(h, Shard.DEEP_BREATH, mode); int consumed = 0;
                for (int i = 0; i < 20; i++) { var event = new LivingBreatheEvent(p, false, 1, 0); NeoForge.EVENT_BUS.post(event); consumed += event.getConsumeAirAmount(); }
                h.assertValueEqual(consumed, mode.equals("active") ? 10 : 20, "Air loss");
            });
            add(tests, "ember_" + mode, h -> {
                var p = player(h, Shard.EMBER_SKIN, mode); p.setRemainingFireTicks(100);
                ExplorationShards.tick(new PlayerTickEvent.Post(p));
                h.assertValueEqual(p.getRemainingFireTicks(), mode.equals("active") ? 97 : 100, "Fire duration");
                var lava = new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().lava(), 4)); NeoForge.EVENT_BUS.post(lava);
                Fixtures.near(h, lava.getAmount(), mode.equals("active") ? 2 : 4, "Lava damage");
                var fall = new LivingIncomingDamageEvent(p, new DamageContainer(p.damageSources().fall(), 4)); NeoForge.EVENT_BUS.post(fall);
                Fixtures.near(h, fall.getAmount(), 4, "Non-lava damage changed");
            });
            add(tests, "forager_" + mode, h -> {
                var p = player(h, Shard.FORAGER, mode); p.getRandom().setSeed(117);
                int bonus = 0;
                var pos = h.absolutePos(new BlockPos(3, 2, 3));
                for (int i = 0; i < 120; i++) {
                    var drops = new ArrayList<ItemEntity>(); drops.add(new ItemEntity(h.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.WHEAT, 3)));
                    var event = new BlockDropsEvent(h.getLevel(), pos, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), null, drops, p, ItemStack.EMPTY);
                    NeoForge.EVENT_BUS.post(event);
                    for (int j = 1; j < drops.size(); j++) { h.assertValueEqual(drops.get(j).getItem().getCount(), 1, "Copied whole Fortune stack"); bonus++; }
                }
                h.assertTrue(mode.equals("active") ? bonus > 0 && bonus < 30 : bonus == 0, "Forager eligibility/chance: " + bonus);
            });
        }
        add(tests, "forager_excludes_immature_and_silk_leaf_blocks", h -> {
            var p = player(h, Shard.FORAGER, "active"); var pos = p.blockPosition();
            for (var state : List.of(Blocks.WHEAT.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState())) {
                for (int i = 0; i < 40; i++) {
                    var drops = new ArrayList<ItemEntity>(); drops.add(new ItemEntity(h.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(state.getBlock().asItem())));
                    NeoForge.EVENT_BUS.post(new BlockDropsEvent(h.getLevel(), pos, state, null, drops, p, ItemStack.EMPTY));
                    h.assertValueEqual(drops.size(), 1, "Ineligible harvest duplicated");
                }
            }
        });
        add(tests, "prospector_claims_cooldown_before_random_scan", h -> {
            var p = player(h, Shard.PROSPECTOR, "active"); var pos = p.blockPosition();
            NeoForge.EVENT_BUS.post(new BlockDropsEvent(h.getLevel(), pos, Blocks.STONE.defaultBlockState(), null, new ArrayList<>(), p, ItemStack.EMPTY));
            h.assertTrue(p.getData(SpiritData.ENERGY_STATE).prospectorAt == h.getLevel().getGameTime(), "No server cooldown");
            h.assertFalse(ExplorationShardRules.ready(h.getLevel().getGameTime() + 99, p.getData(SpiritData.ENERGY_STATE).prospectorAt), "Cooldown too short");
        });
        add(tests, "slot_request_is_atomic_and_rejects_locked_or_unowned", h -> {
            var p = player(h, Shard.MAGNET, "active"); p.setData(SpiritData.ABILITIES, 0);
            var before = p.getData(SpiritData.PROGRESS);
            SpiritProgression.request(p, "shard_slot", "3:magnet"); h.assertTrue(before.equals(p.getData(SpiritData.PROGRESS)), "Locked slot accepted");
            SpiritProgression.request(p, "shard_slot", "0:forager"); h.assertTrue(before.equals(p.getData(SpiritData.PROGRESS)), "Unowned shard accepted");
            p.setData(SpiritData.PROGRESS, before.withOwned(Shard.MAGNET.bit | Shard.DEEP_BREATH.bit));
            SpiritProgression.request(p, "shard_slot", "0:deep_breath");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).equippedShards(), Shard.DEEP_BREATH.bit, "Replacement was not atomic");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).ownedShards(), Shard.MAGNET.bit | Shard.DEEP_BREATH.bit, "Replacement lost ownership");
        });
        return tests;
    }
    private static ServerPlayer player(GameTestHelper h, Shard shard, String mode) {
        var p = Fixtures.player(h); p.setData(SpiritData.FORM, !mode.equals("human"));
        p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withOwned(shard.bit).withEquipped(mode.equals("unequipped") ? 0 : shard.bit));
        return p;
    }
    private static ItemEntity item(ServerPlayer p, double offset) {
        var item = new ItemEntity(p.level(), p.getX() + offset, p.getY(), p.getZ(), new ItemStack(Items.APPLE));
        item.setNoPickUpDelay(); p.level().addFreshEntity(item); return item;
    }
    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("r117_shards", "r117_shards." + name, "orimod_r117:room", 100, 0, true, h -> { run.accept(h); h.succeed(); }));
    }
}
