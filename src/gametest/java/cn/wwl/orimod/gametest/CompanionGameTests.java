package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.command.OriCommands;
import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.CompanionHints;
import cn.wwl.orimod.progression.CompanionHints.Hint;
import cn.wwl.orimod.progression.SpiritProgress;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** R97: real server ticks, payload delivery, palette scans, saves and operator commands for Glimmer's guidance. */
@GameTestHolder("orimod_gametest")
public final class CompanionGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        timed(tests, "updraft_after_20_ticks_once_even_after_cooldown", h -> {
            var c = Fixtures.connectedPlayer(h);
            try {
                spirit(c.player(), Hint.UPDRAFT);
                c.player().setNoGravity(true);
                h.setBlock(new BlockPos(7, 2, 5), OriBlocks.UPDRAFT.get());
                FxGameTests.drain(c.channel());
                driveTicks(h, c.player(), 224);
                h.runAfterDelay(21, () -> {
                    try {
                        h.assertValueEqual(c.player().getData(SpiritData.PROGRESS).rewards().get("hint/updraft_locked"), 1, "Seen locked updraft");
                        var hints = hints(c);
                        h.assertValueEqual(hints.size(), 1, "First 20 ticks should send one hint");
                        h.assertValueEqual(hints.getFirst().id(), "updraft_locked", "Locked wording");
                        h.assertValueEqual(hints.getFirst().pos().orElseThrow(), h.absolutePos(new BlockPos(7, 2, 5)), "Guide destination");
                    } catch (RuntimeException | Error e) { c.close(); throw e; }
                });
                h.runAfterDelay(225, () -> {
                    try {
                        h.assertTrue(hints(c).isEmpty(), "Seen hint repeated after cooldown");
                        h.succeed();
                    } finally { c.close(); }
                });
            } catch (RuntimeException | Error e) { c.close(); throw e; }
        });
        for (var hint : List.of(Hint.UPDRAFT, Hint.GRAPPLE_FLOWER)) {
            timed(tests, hint.id + "_locked_then_unlocked_each_once", h -> {
                var c = Fixtures.connectedPlayer(h);
                try {
                    var p = c.player();
                    spirit(p, hint);
                    p.setNoGravity(true);
                    var pos = new BlockPos(7, 2, 5);
                    h.setBlock(pos, hint == Hint.UPDRAFT ? OriBlocks.UPDRAFT.get() : OriBlocks.GRAPPLE_FLOWER.get());
                    FxGameTests.drain(c.channel());
                    driveTicks(h, p, 454);
                    h.runAfterDelay(21, () -> {
                        try {
                            var sent = hints(c);
                            h.assertValueEqual(sent.size(), 1, "One locked hint");
                            h.assertValueEqual(sent.getFirst().id(), hint.id + "_locked", "Locked wording");
                            h.assertValueEqual(sent.getFirst().pos().orElseThrow(), h.absolutePos(pos), "Locked destination");
                            h.assertValueEqual(p.getData(SpiritData.PROGRESS).rewards().get("hint/" + hint.id + "_locked"), 1, "Locked record");
                            h.assertFalse(p.getData(SpiritData.PROGRESS).rewards().containsKey("hint/" + hint.id), "Locked hint consumed usage record");
                        } catch (RuntimeException | Error e) { c.close(); throw e; }
                    });
                    h.runAfterDelay(225, () -> {
                        try {
                            h.assertTrue(hints(c).isEmpty(), "Locked wording repeated after cooldown");
                            p.setData(SpiritData.ABILITIES, hint == Hint.UPDRAFT ? Ability.GLIDE.bit : Ability.GRAPPLE.bit);
                        } catch (RuntimeException | Error e) { c.close(); throw e; }
                    });
                    h.runAfterDelay(246, () -> {
                        try {
                            var sent = hints(c);
                            h.assertValueEqual(sent.size(), 1, "One usage hint after learning");
                            h.assertValueEqual(sent.getFirst().id(), hint.id, "Usage wording");
                            h.assertValueEqual(sent.getFirst().pos().orElseThrow(), h.absolutePos(pos), "Same destination after learning");
                            h.assertValueEqual(p.getData(SpiritData.PROGRESS).rewards().get("hint/" + hint.id), 1, "Usage record");
                            h.assertValueEqual(p.getData(SpiritData.PROGRESS).rewards().get("hint/" + hint.id + "_locked"), 1, "Locked record retained");
                        } catch (RuntimeException | Error e) { c.close(); throw e; }
                    });
                    h.runAfterDelay(455, () -> {
                        try {
                            h.assertTrue(hints(c).isEmpty(), "Usage wording repeated after cooldown");
                            h.succeed();
                        } finally { c.close(); }
                    });
                } catch (RuntimeException | Error e) { c.close(); throw e; }
            });
        }
        timed(tests, "human_receives_no_hint_on_server_ticks", h -> {
            var c = Fixtures.connectedPlayer(h);
            try {
                c.player().setNoGravity(true);
                h.setBlock(new BlockPos(7, 2, 5), OriBlocks.UPDRAFT.get());
                FxGameTests.drain(c.channel());
                driveTicks(h, c.player(), 20);
                h.runAfterDelay(21, () -> {
                    try {
                        h.assertTrue(CompanionHints.seen(c.player().getData(SpiritData.PROGRESS)).isEmpty(), "Human marked a hint");
                        h.assertTrue(hints(c).isEmpty(), "Human received a hint");
                        h.succeed();
                    } finally { c.close(); }
                });
            } catch (RuntimeException | Error e) { c.close(); throw e; }
        });
        record Spatial(Hint hint, Block block, String name) {}
        for (var entry : List.of(
                new Spatial(Hint.UPDRAFT, OriBlocks.UPDRAFT.get(), "updraft"),
                new Spatial(Hint.BASH_ANCHOR, OriBlocks.BASH_ANCHOR.get(), "lantern"),
                new Spatial(Hint.GRAPPLE_FLOWER, OriBlocks.GRAPPLE_FLOWER.get(), "flower"),
                new Spatial(Hint.SPIRIT_SAND, OriBlocks.SPIRIT_SAND.get(), "sand"),
                new Spatial(Hint.GLOOM_WATER, OriBlocks.GLOOM_WATER.get(), "gloom_water"),
                new Spatial(Hint.KEYSTONE_DOOR, OriBlocks.KEYSTONE_DOOR.get(), "door"),
                new Spatial(Hint.MAP_STONE, OriBlocks.MAP_STONE.get(), "map"),
                new Spatial(Hint.CELL_PEDESTAL, OriBlocks.LIFE_CELL_PEDESTAL.get(), "life_cell"),
                new Spatial(Hint.CELL_PEDESTAL, OriBlocks.ENERGY_CELL_PEDESTAL.get(), "energy_cell"),
                new Spatial(Hint.BOSS_ALTAR, OriBlocks.GLOOM_ALTAR.get(), "gloom_altar"),
                new Spatial(Hint.BOSS_ALTAR, OriBlocks.STORM_ALTAR.get(), "storm_altar"))) {
            add(tests, "scan_" + entry.name, h -> {
                var player = Fixtures.player(h);
                spirit(player, entry.hint);
                var pos = h.absolutePos(new BlockPos(7, 2, 5));
                h.getLevel().setBlock(pos, entry.block.defaultBlockState(), 3);
                var seen = CompanionHints.seen(player.getData(SpiritData.PROGRESS));
                var centre = Vec3.atCenterOf(pos);
                player.setPos(centre.add(0, entry.hint.radius, 0));
                h.assertValueEqual(CompanionHints.scan(player, seen).get(entry.hint), pos, "Exact radius must include block");
                player.setPos(centre.add(0, entry.hint.radius + .01, 0));
                h.assertFalse(CompanionHints.scan(player, seen).containsKey(entry.hint), "Block beyond hint radius included");
                player.setPos(centre.add(0, entry.hint.radius * .8, entry.hint.radius * .8));
                h.assertFalse(CompanionHints.scan(player, seen).containsKey(entry.hint), "Cube corner accepted outside sphere");
            });
        }
        add(tests, "scan_chooses_nearest_unseen_block", h -> {
            var p = Fixtures.player(h);
            spirit(p, Hint.UPDRAFT);
            h.setBlock(new BlockPos(3, 2, 5), OriBlocks.UPDRAFT.get());
            h.setBlock(new BlockPos(6, 2, 5), OriBlocks.UPDRAFT.get());
            var seen = CompanionHints.seen(p.getData(SpiritData.PROGRESS));
            h.assertValueEqual(CompanionHints.scan(p, seen).get(Hint.UPDRAFT), h.absolutePos(new BlockPos(6, 2, 5)), "Nearest block");
            var all = new java.util.HashSet<>(seen); all.add("updraft_locked");
            h.assertTrue(CompanionHints.scan(p, all).isEmpty(), "Seen palette candidate not skipped");
        });
        add(tests, "priority_cooldown_and_unlocked_wording", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player();
                spirit(p, Hint.UPDRAFT, Hint.GRAPPLE_FLOWER);
                p.setData(SpiritData.ABILITIES, Ability.GLIDE.bit | Ability.GRAPPLE.bit);
                h.setBlock(new BlockPos(7, 2, 5), OriBlocks.UPDRAFT.get());
                h.setBlock(new BlockPos(6, 2, 5), OriBlocks.GRAPPLE_FLOWER.get());
                FxGameTests.drain(c.channel());
                CompanionHints.check(p);
                h.assertValueEqual(hints(c).getFirst().id(), "updraft", "Priority and unlocked Glide wording");
                CompanionHints.check(p);
                h.assertTrue(hints(c).isEmpty(), "Second hint sent in same tick");
                p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt = p.level().getGameTime() - 199;
                CompanionHints.check(p);
                h.assertTrue(hints(c).isEmpty(), "Second hint sent before 200 ticks");
                p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt--;
                CompanionHints.check(p);
                h.assertValueEqual(hints(c).getFirst().id(), "grapple_flower", "Second hint at 200 ticks");
            }
        });
        add(tests, "low_energy_requires_an_actual_recent_spend", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); spirit(p, Hint.LOW_ENERGY);
                p.setData(SpiritData.ENERGY, 10f);
                FxGameTests.drain(c.channel());
                CompanionHints.check(p);
                h.assertTrue(hints(c).isEmpty(), "Fresh session treated as spent energy");
                h.assertTrue(SpiritProgression.spendEnergy(p, 1), "Spend failed");
                p.getData(SpiritData.ENERGY_STATE).lastHintSpendAt = p.level().getGameTime() - 201;
                CompanionHints.check(p);
                h.assertTrue(hints(c).isEmpty(), "Old spend triggered hint");
                SpiritProgression.spendEnergy(p, 1);
                CompanionHints.check(p);
                var sent = hints(c);
                h.assertValueEqual(sent.size(), 1, "Low energy payload count");
                h.assertValueEqual(sent.getFirst().id(), "low_energy", "Low energy id");
                h.assertTrue(sent.getFirst().pos().isEmpty(), "Personal hint has a block destination");
            }
        });
        add(tests, "shard_hint_only_when_owned_and_unequipped", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); spirit(p, Hint.SHARD_SLOT);
                FxGameTests.drain(c.channel());
                CompanionHints.check(p);
                h.assertTrue(hints(c).isEmpty(), "No owned shards triggers hint");
                var progress = p.getData(SpiritData.PROGRESS).withOwned(1).withEquipped(1);
                p.setData(SpiritData.PROGRESS, progress);
                CompanionHints.check(p);
                h.assertTrue(hints(c).isEmpty(), "Already equipped shard triggers hint");
                p.setData(SpiritData.PROGRESS, progress.withEquipped(0));
                CompanionHints.check(p);
                h.assertValueEqual(hints(c).getFirst().id(), "shard_slot", "Unequipped shard hint");
            }
        });
        add(tests, "old_save_without_hint_keys_loads_and_records_hint", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); SpiritForm.toggle(p);
                var old = SpiritProgress.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"light\":71,\"rewards\":{\"ruin/forest\":2}}")).getOrThrow();
                p.setData(SpiritData.PROGRESS, old);
                h.setBlock(new BlockPos(7, 2, 5), OriBlocks.UPDRAFT.get());
                CompanionHints.check(p);
                var updated = p.getData(SpiritData.PROGRESS);
                h.assertValueEqual(updated.light(), 71, "Old light lost");
                h.assertValueEqual(updated.rewards().get("ruin/forest"), 2, "Old reward lost");
                h.assertValueEqual(updated.rewards().get("hint/updraft_locked"), 1, "New locked hint not saved");
                var encoded = SpiritProgress.CODEC.encodeStart(JsonOps.INSTANCE, updated).getOrThrow();
                h.assertValueEqual(SpiritProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(), updated, "Hint save round trip");
            }
        });
        add(tests, "commands_list_read_only_reset_preserves_progress", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player();
                var saved = SpiritProgress.EMPTY.withLight(123).withOwned(3).withEquipped(1)
                        .withRewards(java.util.Map.of("hint/updraft", 1, "hint/updraft_locked", 1, "hint/future", 1, "ruin/forest", 2));
                p.setData(SpiritData.PROGRESS, saved);
                p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt = p.level().getGameTime();
                var dispatcher = new CommandDispatcher<CommandSourceStack>();
                OriCommands.register(dispatcher);
                try {
                    var source = p.createCommandSourceStack().withPermission(2);
                    h.assertValueEqual(dispatcher.execute("orimod hints list", source), 1, "List result");
                    h.assertValueEqual(p.getData(SpiritData.PROGRESS), saved, "List modified progress");
                    h.assertValueEqual(dispatcher.execute("orimod hints reset", source), 1, "Reset result");
                    h.assertValueEqual(p.getData(SpiritData.PROGRESS), saved.withRewards(java.util.Map.of("ruin/forest", 2)), "Reset erased unrelated progress");
                    h.assertTrue(CompanionHints.ready(p.level().getGameTime(), p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt), "Reset kept cooldown");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new RuntimeException(e); }
            }
        });
        return tests;
    }

    private static List<CompanionHintPayload> hints(Fixtures.ConnectedPlayer c) {
        return FxGameTests.drain(c.channel()).stream()
                .filter(p -> p instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CompanionHintPayload)
                .map(p -> (CompanionHintPayload) ((ClientboundCustomPayloadPacket) p).payload()).toList();
    }

    /** Embedded connections lack the network tick that normally calls doTick; run the real player/event path each tick. */
    private static void driveTicks(GameTestHelper h, ServerPlayer player, int count) {
        for (int tick = 1; tick <= count; tick++) h.runAfterDelay(tick, player::doTick);
    }

    /** Mark unrelated hints seen to keep adjacent parallel GameTest rooms from influencing each other's scans. */
    private static void spirit(ServerPlayer player, Hint... unseen) {
        SpiritForm.toggle(player);
        var omitted = Set.of(unseen);
        var rewards = new HashMap<String, Integer>();
        for (var hint : Hint.values()) if (!omitted.contains(hint)) {
            rewards.put("hint/" + hint.id, 1);
            rewards.put("hint/" + CompanionHints.wireId(hint, 0), 1);
        }
        player.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withRewards(rewards));
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        timed(tests, name, h -> { run.accept(h); h.succeed(); });
    }
    private static void timed(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("companion", "companion." + name, "orimod_gametest:room", 480, 0, true, run));
    }
}
