package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

/** R100: real feature placement, atomic preflight, root-heart use and per-player exploration rewards. */
@GameTestHolder("orimod_gametest")
public final class SpiritTreeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "flat_feature_has_accessible_heart_and_bounded_crowns", h -> site(h, 0, base -> {
            var untouched = snapshot(h, base);
            var check = SpiritTreeFeature.preflightLoaded(h.getLevel(), base).orElseThrow();
            h.assertValueEqual(check.reason(), SpiritTreeFeature.Reason.OK, "Open terrain preflight failed");
            h.assertFalse(check.writes().isEmpty(), "Successful preflight omitted its plan");
            h.assertValueEqual(snapshot(h, base), untouched, "Successful observation wrote a tree");
            h.assertTrue(place(h, base), "Flat placement failed");
            var level = h.getLevel();
            var heart=base.offset(SpiritTreeShape.HEART);
            h.assertTrue(level.getChunk(heart).getData(FeatureSites.TREES).contains(heart.asLong()), "Generated heart absent from durable journal");
            h.assertValueEqual(FeatureSites.get(level).nearest(level,FeatureSites.Kind.TREE,Vec3.atCenterOf(heart),p->true).orElseThrow(),heart,"Index recorded base instead of heart");
            h.assertTrue(level.getBlockState(base.offset(SpiritTreeShape.HEART)).is(OriBlocks.SPIRIT_TREE_HEART), "Missing root heart");
            int leaves = 0, logs = 0, vines = 0;
            for (var pos : BlockPos.betweenClosed(base.offset(-15, -4, -15), base.offset(15, 30, 15))) {
                var state = level.getBlockState(pos);
                boolean tree = state.is(OriBlocks.SPIRIT_LOG) || state.is(OriBlocks.SPIRIT_LEAVES) || state.is(OriBlocks.SPIRIT_TREE_HEART)
                        || state.is(OriBlocks.SPIRIT_VINE);
                if (tree) h.assertTrue(Math.abs(pos.getX() - base.getX()) <= 14 && Math.abs(pos.getZ() - base.getZ()) <= 14, "Outside ±14");
                if (state.is(OriBlocks.SPIRIT_LOG)) logs++;
                if (state.is(OriBlocks.SPIRIT_LEAVES)) leaves++;
                if (state.is(OriBlocks.SPIRIT_VINE)) {
                    vines++;
                    h.assertTrue(state.canSurvive(level, pos), "Unsupported spirit vine");
                    h.assertFalse(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES), "Vine has berries");
                }
                h.assertFalse(state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT), "Warm vanilla vines remain");
            }
            h.assertTrue(logs > 100 && leaves > 200 && vines > 3, "Tree missing wood, crowns or dangling vines");
            for (int x = -1; x <= 1; x++) for (int z = -5; z <= -3; z++) {
                h.assertTrue(level.getBlockState(base.offset(x, -1, z)).isFaceSturdy(level, base.offset(x, -1, z), Direction.UP), "Entrance has no floor");
                for (int y = 0; y < 3; y++) h.assertTrue(level.isEmptyBlock(base.offset(x, y, z)), "Entrance blocked");
            }
        }));
        add(tests, "vanilla_tree_heightmap_and_survival_filter_reject_spirit_canopy", h -> site(h, 31, base -> {
            var level = h.getLevel();
            h.assertTrue(place(h, base), "Spirit canopy fixture failed");
            var topLeaf = SpiritTreeShape.plan(base).blocks().entrySet().stream()
                    .filter(e -> e.getValue() == SpiritTreeShape.Part.LEAVES)
                    .max(Comparator.comparingInt(e -> e.getKey().getY())).orElseThrow().getKey();
            var top = base.offset(topLeaf);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, top.getX(), top.getZ());
            h.assertValueEqual(y, top.getY() + 1, "Vanilla heightmap ignored luminous leaves");
            var saplingPos = new BlockPos(top.getX(), y, top.getZ());
            for (var sapling : List.of(Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.CHERRY_SAPLING))
                h.assertFalse(sapling.defaultBlockState().canSurvive(level, saplingPos), "Sapling survives on spirit leaves");
            var before = snapshot(h, base);
            var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            for (String id : List.of("oak_checked", "birch_checked", "spruce_checked", "pine_checked", "fancy_oak_checked", "cherry_checked"))
                h.assertFalse(registry.get(ResourceLocation.withDefaultNamespace(id)).place(level,
                        level.getChunkSource().getGenerator(), RandomSource.create(1), saplingPos), "Vanilla tree grew on canopy: " + id);
            h.assertValueEqual(snapshot(h, base), before, "Rejected vanilla tree modified canopy");
        }));
        add(tests, "three_block_slope_connects_roots_and_entrance_to_ground", h -> site(h, 30, base -> {
            var level = h.getLevel();
            for (int x = -15; x < 0; x++) for (int z = -15; z <= 15; z++) {
                level.setBlock(base.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 18);
                level.setBlock(base.offset(x, -4, z), Blocks.STONE.defaultBlockState(), 18);
            }
            h.assertTrue(place(h, base), "Three-block slope was rejected");
            for (int y = -3; y <= -1; y++) h.assertTrue(level.getBlockState(base.offset(-1, y, -4)).is(OriBlocks.SPIRIT_LOG), "Floating root entrance");
            for (int y = 0; y < 3; y++) h.assertTrue(level.isEmptyBlock(base.offset(-1, y, -4)), "Slope blocked entrance");
        }));
        for (String obstruction : List.of("crown_stone", "crown_cavity_stone", "wet_floor", "steep_floor", "entrance_chest",
                "crown_log", "foot_water", "foot_ungrounded")) {
            int siteIndex = tests.size();
            add(tests, obstruction + "_rejects_without_any_world_write", h -> site(h, siteIndex, base -> {
                var level = h.getLevel();
                switch (obstruction) {
                    case "crown_stone", "crown_log" -> {
                        var leaf = SpiritTreeShape.plan(base).blocks().entrySet().stream()
                                .filter(e -> e.getValue() == SpiritTreeShape.Part.LEAVES && Math.abs(e.getKey().getX()) > 3).findFirst().orElseThrow().getKey();
                        level.setBlock(base.offset(leaf), (obstruction.equals("crown_log") ? Blocks.OAK_LOG : Blocks.STONE).defaultBlockState(), 2);
                    }
                    case "crown_cavity_stone" -> {
                        var plan = SpiritTreeShape.plan(base);
                        var gap = plan.crownSpace().stream().filter(p -> !plan.blocks().containsKey(p) && Math.abs(p.getX()) > 3).findFirst().orElseThrow();
                        level.setBlock(base.offset(gap), Blocks.STONE.defaultBlockState(), 2);
                    }
                    case "foot_water" -> level.setBlock(base.offset(0, -1, -4), Blocks.WATER.defaultBlockState(), 2);
                    case "foot_ungrounded" -> level.setBlock(base.offset(0, -1, -4), Blocks.AIR.defaultBlockState(), 2);
                    case "wet_floor" -> level.setBlock(base.below(), Blocks.WATER.defaultBlockState(), 2);
                    case "steep_floor" -> {
                        for (int y = 0; y < 4; y++) level.setBlock(base.offset(1, y, 1), Blocks.STONE.defaultBlockState(), 2);
                    }
                    case "entrance_chest" -> level.setBlock(base.offset(0, 0, -4), Blocks.CHEST.defaultBlockState(), 2);
                }
                var before = snapshot(h, base);
                var reason = switch (obstruction) {
                    case "wet_floor" -> SpiritTreeFeature.Reason.NOT_DRY;
                    case "steep_floor" -> SpiritTreeFeature.Reason.RELIEF;
                    case "crown_log" -> SpiritTreeFeature.Reason.CLEARANCE_LOG;
                    case "foot_water" -> SpiritTreeFeature.Reason.FOOT_BLOCKED;
                    case "foot_ungrounded" -> SpiritTreeFeature.Reason.FOOT_UNGROUNDED;
                    default -> SpiritTreeFeature.Reason.CLEARANCE_OTHER;
                };
                var check = SpiritTreeFeature.preflightLoaded(level, base).orElseThrow();
                h.assertValueEqual(check.reason(), reason, "Wrong rejection reason: " + obstruction);
                h.assertTrue(check.writes().isEmpty(), "Rejected preflight exposed a partial plan");
                h.assertValueEqual(snapshot(h, base), before, "Rejected observation wrote blocks");
                h.assertFalse(place(h, base), "Invalid tree placed: " + obstruction);
                h.assertValueEqual(snapshot(h, base), before, "Failed tree left partial blocks");
            }));
        }
        add(tests, "preflight_does_not_load_missing_footprint_chunks", h -> {
            var level = h.getLevel(); var source = level.getChunkSource();
            var origin = new BlockPos(2_000_000, 100, 2_000_000);
            h.assertTrue(source.getChunkNow(origin.getX() >> 4, origin.getZ() >> 4) == null, "Fixture already loaded");
            int before = source.getLoadedChunksCount();
            h.assertTrue(SpiritTreeFeature.preflightLoaded(level, origin).isEmpty(), "Unloaded footprint was inspected");
            h.assertTrue(source.getChunkNow(origin.getX() >> 4, origin.getZ() >> 4) == null, "Preflight loaded origin");
            h.assertValueEqual(source.getLoadedChunksCount(), before, "Preflight loaded chunks");
        });
        add(tests, "preflight_rejects_crown_above_build_height_without_writes", h -> {
            var base = new BlockPos(8192, h.getLevel().getMaxBuildHeight() - 10, 8192);
            site(h, base, p -> {
                var before = snapshot(h, p);
                var check = SpiritTreeFeature.preflightLoaded(h.getLevel(), p).orElseThrow();
                h.assertValueEqual(check.reason(), SpiritTreeFeature.Reason.BUILD_HEIGHT, "Ceiling not diagnosed");
                h.assertFalse(place(h, p), "Tree crossed build ceiling");
                h.assertValueEqual(snapshot(h, p), before, "Ceiling rejection wrote blocks");
            });
        });
        add(tests, "spirit_claims_one_free_skill_and_blooms_only_once", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); SpiritForm.toggle(p);
                p.setData(SpiritData.ABILITIES, 0);
                p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(23));
                var heart = heart(h, 7);
                FxGameTests.drain(c.channel());
                use(p, heart);
                h.assertValueEqual(p.getData(SpiritData.ABILITIES), Ability.DASH.bit, "Wrong free skill");
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 23, "Gift charged currency");
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).rewards().get(SpiritTreeHeartBlock.siteKey(h.getLevel(), heart)), 1, "No site record");
                h.assertTrue(h.getLevel().getBlockState(heart).getValue(SpiritTreeHeartBlock.BLOOMED), "Tree did not bloom");
                var fx = FxGameTests.fx(FxGameTests.drain(c.channel())).stream().filter(v -> v.kind() == SpiritFxKind.TREE_BLESSING).toList();
                h.assertValueEqual(fx.size(), 1, "Blessing payload count");
                h.assertValueEqual(fx.getFirst().sourceEntity(), p.getId(), "Blessing recipient");
                Fixtures.near(h, fx.getFirst().x(), heart.getX() + .5, "Blessing starts at heart");
                use(p, heart);
                h.assertValueEqual(p.getData(SpiritData.ABILITIES), Ability.DASH.bit, "Same tree gave twice");
                h.assertTrue(FxGameTests.fx(FxGameTests.drain(c.channel())).stream().noneMatch(v -> v.kind() == SpiritFxKind.TREE_BLESSING), "Repeat blessing FX");
            }
        });
        for (boolean awake : List.of(false, true)) add(tests, awake ? "human_cannot_claim" : "unawakened_cannot_claim", h -> {
            var p = Fixtures.player(h);
            p.setData(SpiritData.AWAKENED, awake); p.setData(SpiritData.FORM, !awake);
            p.setData(SpiritData.ABILITIES, 0); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY);
            var heart = heart(h, 7); use(p, heart);
            h.assertValueEqual(p.getData(SpiritData.ABILITIES), 0, "Ineligible gift");
            h.assertTrue(p.getData(SpiritData.PROGRESS).rewards().isEmpty(), "Ineligible claim consumed tree");
            h.assertFalse(h.getLevel().getBlockState(heart).getValue(SpiritTreeHeartBlock.BLOOMED), "Ineligible tree bloomed");
        });
        add(tests, "all_skills_grants_one_hundred_light_once", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p);
            p.setData(SpiritData.ABILITIES, Arrays.stream(Ability.values()).mapToInt(a -> a.bit).reduce(0, (a, b) -> a | b));
            p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(31));
            var heart = heart(h, 7); use(p, heart); use(p, heart);
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 131, "Full-skills light gift");
        });
        add(tests, "different_hearts_each_grant_and_bloom_is_not_a_global_lock", h -> {
            try (var a = Fixtures.connectedPlayer(h); var b = Fixtures.connectedPlayer(h)) {
                for (var p : List.of(a.player(), b.player())) {
                    SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, 0); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY);
                }
                var first = heart(h, 7); var second = heart(h, 9);
                use(a.player(), first); use(a.player(), second);
                h.assertValueEqual(a.player().getData(SpiritData.ABILITIES), Ability.DASH.bit | Ability.BASH.bit, "Two-tree progression");
                use(b.player(), first);
                h.assertValueEqual(b.player().getData(SpiritData.ABILITIES), Ability.DASH.bit, "Bloomed tree denied another player");
                var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                h.assertFalse(SpiritTreeHeartBlock.siteKey(h.getLevel(), first).equals(SpiritTreeHeartBlock.siteKey(nether, first)), "Dimensions share a claim");
            }
        });
        add(tests, "leaves_require_shears_logs_drop_and_heart_has_no_loot", h -> {
            var p = Fixtures.player(h); var pos = h.absolutePos(new BlockPos(7, 2, 5));
            var leaves = OriBlocks.SPIRIT_LEAVES.get().defaultBlockState();
            h.assertTrue(Block.getDrops(leaves, h.getLevel(), pos, null, p, ItemStack.EMPTY).isEmpty(), "Leaves dropped without shears");
            var drops = Block.getDrops(leaves, h.getLevel(), pos, null, p, new ItemStack(Items.SHEARS));
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(OriBlocks.SPIRIT_LEAVES.asItem()), "Shears missing leaves");
            h.assertTrue(Block.getDrops(OriBlocks.SPIRIT_LOG.get().defaultBlockState(), h.getLevel(), pos, null, p, ItemStack.EMPTY)
                    .stream().anyMatch(s -> s.is(OriBlocks.SPIRIT_LOG.asItem())), "Log does not drop itself");
            h.assertTrue(Block.getDrops(OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), h.getLevel(), pos, null, p, ItemStack.EMPTY).isEmpty(), "Heart has loot");
            for (var block : List.of(OriBlocks.SPIRIT_LOG.get(), OriBlocks.SPIRIT_LEAVES.get(), OriBlocks.SPIRIT_VINE.get(), OriBlocks.SPIRIT_TREE_HEART.get()))
                h.assertFalse(block.asItem() == Items.AIR, "Missing creative block item");
        });
        add(tests, "tree_hint_at_twenty_three_blocks_once_not_at_twenty_five", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); SpiritForm.toggle(p);
                var rewards = new HashMap<String, Integer>();
                for (var hint : CompanionHints.Hint.values()) if (hint != CompanionHints.Hint.SPIRIT_TREE) {
                    rewards.put("hint/" + hint.id, 1); rewards.put("hint/" + CompanionHints.wireId(hint, 0), 1);
                }
                p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withRewards(rewards));
                var far = p.blockPosition().east(25); var near = p.blockPosition().east(23);
                var level = h.getLevel(); var oldNear = level.getBlockState(near); var oldFar = level.getBlockState(far);
                try {
                    level.setBlock(far, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 2);
                    h.assertFalse(CompanionHints.scan(p, CompanionHints.seen(p.getData(SpiritData.PROGRESS))).containsKey(CompanionHints.Hint.SPIRIT_TREE), "Hint outside radius");
                    level.setBlock(near, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 2);
                    FxGameTests.drain(c.channel());
                    CompanionHints.check(p);
                    var hints = FxGameTests.drain(c.channel()).stream().filter(packet ->
                            packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CompanionHintPayload hint
                                    && hint.id().equals("spirit_tree")).toList();
                    h.assertValueEqual(hints.size(), 1, "Missing 24-block tree hint");
                    h.assertValueEqual(p.getData(SpiritData.PROGRESS).rewards().get("hint/spirit_tree"), 1, "Tree hint not recorded");
                    p.getData(SpiritData.ENERGY_STATE).lastCompanionHintAt = Long.MIN_VALUE;
                    CompanionHints.check(p);
                    h.assertTrue(FxGameTests.drain(c.channel()).stream().noneMatch(packet ->
                            packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CompanionHintPayload), "Tree hint repeated");
                } finally { level.setBlock(near, oldNear, 2); level.setBlock(far, oldFar, 2); }
            }
        });
        add(tests, "vine_hangs_from_tagged_leaves_updates_tip_and_falls_without_support", h -> {
            var level = h.getLevel(); var top = h.absolutePos(new BlockPos(6, 5, 6));
            var leaves = OriBlocks.SPIRIT_LEAVES.get().defaultBlockState();
            var vine = OriBlocks.SPIRIT_VINE.get().defaultBlockState();
            h.assertTrue(leaves.is(net.minecraft.tags.BlockTags.LEAVES), "Spirit leaves missing leaves tag");
            level.setBlock(top, leaves, 3);
            level.setBlock(top.below(), vine, 3);
            h.assertTrue(vine.canSurvive(level, top.below()), "Vine cannot hang from spirit leaves");
            h.assertTrue(level.getBlockState(top.below()).getValue(SpiritVineBlock.TIP), "Single segment is not a tip");
            level.setBlock(top.below(2), vine, 3);
            h.assertTrue(vine.canSurvive(level, top.below(2)), "Vine cannot hang from vine");
            h.assertFalse(level.getBlockState(top.below()).getValue(SpiritVineBlock.TIP), "Upper segment kept tip");
            h.assertTrue(level.getBlockState(top.below(2)).getValue(SpiritVineBlock.TIP), "Bottom segment lost tip");
            level.setBlock(top.below(2), Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(level.getBlockState(top.below()).getValue(SpiritVineBlock.TIP), "Cut vine did not regain tip");
            level.setBlock(top.below(2), vine, 3);
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(level.isEmptyBlock(top.below()) && level.isEmptyBlock(top.below(2)), "Unsupported chain did not detach");
            h.assertFalse(vine.canSurvive(level, top.below()), "Floating vine survives");
            h.assertFalse(vine.isRandomlyTicking(), "Decorative vine grows randomly");
        });
        add(tests, "vine_drops_only_for_shears_or_silk_touch", h -> {
            var p = Fixtures.player(h); var pos = h.absolutePos(new BlockPos(7, 3, 5));
            var vine = OriBlocks.SPIRIT_VINE.get().defaultBlockState();
            var silk = new ItemStack(Items.DIAMOND_PICKAXE);
            silk.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
            for (var tool : List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_PICKAXE)))
                h.assertTrue(Block.getDrops(vine, h.getLevel(), pos, null, p, tool).isEmpty(), "Vine dropped without shears/silk");
            for (var tool : List.of(new ItemStack(Items.SHEARS), silk)) {
                var drops = Block.getDrops(vine, h.getLevel(), pos, null, p, tool);
                h.assertTrue(drops.size() == 1 && drops.getFirst().is(OriBlocks.SPIRIT_VINE.asItem()), "Missing vine drop for shears/silk");
            }
        });
        add(tests, "leaves_vines_and_both_hearts_are_emissive_but_bark_is_not", h -> {
            var pos = h.absolutePos(new BlockPos(7, 3, 5)); var level = h.getLevel();
            for (var state : List.of(OriBlocks.SPIRIT_LEAVES.get().defaultBlockState(),
                    OriBlocks.SPIRIT_VINE.get().defaultBlockState(), OriBlocks.SPIRIT_VINE.get().defaultBlockState().setValue(SpiritVineBlock.TIP, false),
                    OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState().setValue(SpiritTreeHeartBlock.BLOOMED, true)))
                h.assertTrue(state.emissiveRendering(level, pos), "Missing fullbright state: " + state);
            h.assertFalse(OriBlocks.SPIRIT_LOG.get().defaultBlockState().emissiveRendering(level, pos), "Bark must retain ambient lighting");
        });
        add(tests, "search_chooses_nearest_in_three_dimensions_across_negative_chunk_border", h -> {
            var level = h.getLevel(); var base = new BlockPos(-4097, level.getMaxBuildHeight() - 20, -4097);
            var near = base.east(5); var far = base.east(3).below(8);
            var oldNear = level.getBlockState(near); var oldFar = level.getBlockState(far);
            try {
                level.setBlock(near, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 2);
                level.setBlock(far, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 2);
                h.assertValueEqual(SpiritTreeSearch.nearest(level, Vec3.atCenterOf(base), 10).orElseThrow(), near, "Used horizontal rather than 3D distance");
                level.setBlock(near, oldNear, 2);
                h.assertValueEqual(SpiritTreeSearch.nearest(level, Vec3.atCenterOf(base), 10).orElseThrow(), far, "Missed next section/chunk");
            } finally { level.setBlock(near, oldNear, 2); level.setBlock(far, oldFar, 2); }
        });
        for (int radius : List.of(1, 1024)) add(tests, "search_radius_" + radius + "_includes_boundary_not_outside", h -> {
            var level = h.getLevel(); var base = new BlockPos(-10000, level.getMaxBuildHeight() - 20, -10000);
            var target = base.east(radius); var old = level.getBlockState(target);
            try {
                level.setBlock(target, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 2);
                h.assertValueEqual(SpiritTreeSearch.nearest(level, Vec3.atCenterOf(base), radius).orElseThrow(), target, "Exact boundary excluded");
                h.assertTrue(SpiritTreeSearch.nearest(level, Vec3.atCenterOf(base).add(-.01, 0, 0), radius).isEmpty(), "Outside sphere included");
            } finally { level.setBlock(target, old, 2); }
        });
        add(tests, "search_never_loads_chunks_and_rejects_invalid_radius", h -> {
            var level = h.getLevel(); var source = level.getChunkSource(); var origin = new Vec3(1000000, 100, 1000000);
            int cx = 1000000 >> 4, cz = 1000000 >> 4;
            h.assertTrue(source.getChunkNow(cx, cz) == null, "Fixture chunk already loaded");
            int before = source.getLoadedChunksCount();
            h.assertTrue(SpiritTreeSearch.nearest(level, origin, 1024).isEmpty(), "Unexpected remote heart");
            h.assertTrue(source.getChunkNow(cx, cz) == null, "Search loaded origin chunk");
            h.assertValueEqual(source.getLoadedChunksCount(), before, "Search generated or loaded chunks");
            for (int radius : List.of(-1, 0, 1025, Integer.MAX_VALUE)) {
                boolean rejected = false;
                try { SpiritTreeSearch.nearest(level, origin, radius); } catch (IllegalArgumentException expected) { rejected = true; }
                h.assertTrue(rejected, "Invalid radius accepted: " + radius);
            }
        });
        add(tests, "nearest_command_reports_found_and_missing_and_bounds_arguments", h -> {
            var level = h.getLevel(); var target = new BlockPos(-12000, level.getMaxBuildHeight() - 20, -12000);
            var old = level.getBlockState(target);
            var messages = new ArrayList<net.minecraft.network.chat.Component>();
            var capture = new net.minecraft.commands.CommandSource() {
                public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            var source = level.getServer().createCommandSourceStack().withSource(capture).withLevel(level)
                    .withPosition(Vec3.atCenterOf(target).add(0, 0, -1)).withPermission(2);
            var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            cn.wwl.orimod.command.OriCommands.register(dispatcher);
            try {
                level.setBlock(target, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 2);
                h.assertValueEqual(dispatcher.execute("ori tree nearest", source), 1, "Default nearest failed");
                h.assertValueEqual(dispatcher.execute("ori tree nearest 1", source), 1, "Minimum radius failed");
                h.assertValueEqual(dispatcher.execute("orimod tree nearest 1024", source), 1, "Maximum radius failed");
                var found = (net.minecraft.network.chat.contents.TranslatableContents) messages.getFirst().getContents();
                h.assertValueEqual(found.getKey(), "commands.orimod.tree.nearest", "Missing found message");
                h.assertValueEqual(found.getArgs()[1], "1.0", "Distance was not reported");
                var coordinates = (net.minecraft.network.chat.Component) found.getArgs()[0];
                h.assertValueEqual(coordinates.getStyle().getClickEvent().getValue(), "/tp @s -12000 " + target.getY() + " -12003", "Teleport is not at entrance floor");
                level.setBlock(target, old, 2);
                h.assertValueEqual(dispatcher.execute("ori tree nearest 1", source), 0, "Empty search succeeded");
                // CommandSourceStack.sendFailure wraps the translated message in a red empty component.
                h.assertValueEqual(((net.minecraft.network.chat.contents.TranslatableContents) messages.getLast().getSiblings().getFirst().getContents()).getKey(), "commands.orimod.tree.none", "Missing empty message");
                for (String radius : List.of("0", "1025")) {
                    boolean rejected = false;
                    try { dispatcher.execute("ori tree nearest " + radius, source); } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
                    h.assertTrue(rejected, "Command accepted radius " + radius);
                }
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new RuntimeException(e); }
            finally { level.setBlock(target, old, 2); }
        });
        return tests;
    }

    private static BlockPos heart(GameTestHelper h, int x) {
        var pos = h.absolutePos(new BlockPos(x, 2, 5));
        h.getLevel().setBlock(pos, OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(), 3);
        return pos;
    }
    private static void use(ServerPlayer player, BlockPos pos) {
        player.level().getBlockState(pos).useWithoutItem(player.level(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }
    private static boolean place(GameTestHelper h, BlockPos base) {
        var feature = h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.parse("orimod:spirit_tree"));
        h.assertTrue(feature != null, "Missing configured feature");
        return feature.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), RandomSource.create(1), base);
    }
    private static Map<BlockPos, BlockState> snapshot(GameTestHelper h, BlockPos base) {
        var result = new HashMap<BlockPos, BlockState>();
        for (var p : BlockPos.betweenClosed(base.offset(-15, -4, -15), new BlockPos(base.getX() + 15, Math.min(base.getY() + 30, h.getLevel().getMaxBuildHeight() - 1), base.getZ() + 15)))
            result.put(p.immutable(), h.getLevel().getBlockState(p));
        return result;
    }
    private static void site(GameTestHelper h, int index, Consumer<BlockPos> test) {
        // Elevated isolated sites, separated beyond the 29-block crown diameter; restore everything afterwards.
        var base = h.absolutePos(new BlockPos(128 + index * 64, 100, 0));
        site(h, base, test);
    }
    private static void site(GameTestHelper h, BlockPos base, Consumer<BlockPos> test) {
        var original = snapshot(h, base);
        try {
            for (var p : original.keySet()) h.getLevel().setBlock(p, p.getY() == base.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            test.accept(base);
        } finally { original.forEach((p, state) -> h.getLevel().setBlock(p, state, 18)); }
    }
    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> test) {
        tests.add(new TestFunction("spirit_tree", "spirit_tree." + name, "orimod_gametest:room", 100, 0, true,
                h -> { test.accept(h); h.succeed(); }));
    }
}
