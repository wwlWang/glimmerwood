package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.block.SpiritLanternBlock;
import cn.wwl.orimod.combat.ChargeFlame;
import cn.wwl.orimod.combat.LightBurst;
import cn.wwl.orimod.fx.SpiritFxKind;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/** R96: real scheduled expiry, ability hits, legacy ruin palette, and survival crafting for Ori lanterns. */
@GameTestHolder("orimod_gametest")
public final class LanternGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "legacy_palette_shape_and_light", h -> {
            var tag = new CompoundTag();
            tag.putString("Name", "orimod:bash_anchor"); // Original ruin NBT has no Properties.
            var state = NbtUtils.readBlockState(h.getLevel().holderLookup(Registries.BLOCK), tag);
            h.assertTrue(state.is(OriBlocks.BASH_ANCHOR.get()), "Legacy registry id changed");
            h.assertFalse(state.getValue(SpiritLanternBlock.LIT), "Legacy lamp defaults to lit");
            var pos = lantern(h, 5, 3, 5);
            h.assertValueEqual(state.getLightEmission(h.getLevel(), pos), 7, "Resting block light");
            h.assertValueEqual(state.setValue(SpiritLanternBlock.LIT, true).getLightEmission(h.getLevel(), pos), 15, "Lit block light");
            h.assertTrue(state.getCollisionShape(h.getLevel(), pos).isEmpty(), "Lantern blocks Bash movement");
            var bounds = state.getShape(h.getLevel(), pos).bounds();
            Fixtures.near(h, bounds.getXsize(), 6 / 16.0, "Outline width");
            Fixtures.near(h, bounds.getYsize(), 10 / 16.0, "Outline height");
            Fixtures.near(h, bounds.getZsize(), 6 / 16.0, "Outline depth");
        });
        add(tests, "rejects_other_blocks_and_nonpositive_duration", h -> {
            var pos = lantern(h, 5, 3, 5);
            h.assertFalse(SpiritLanternBlock.ignite(h.getLevel(), pos, 0), "Zero duration accepted");
            h.assertFalse(SpiritLanternBlock.ignite(h.getLevel(), pos, -1), "Negative duration accepted");
            h.assertFalse(lit(h, pos), "Invalid duration lights lantern");
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            h.assertFalse(SpiritLanternBlock.ignite(h.getLevel(), pos, 100), "Stone accepted");
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE), "Non-lantern changed");
        });
        timed(tests, "expires_after_100_ticks", h -> {
            var pos = lantern(h, 5, 3, 5);
            h.assertTrue(SpiritLanternBlock.ignite(h.getLevel(), pos, SpiritLanternBlock.LIT_TICKS), "Ignition refused");
            h.assertTrue(lit(h, pos), "Ignition did not set LIT");
            h.runAfterDelay(99, () -> h.assertTrue(lit(h, pos), "Extinguished before 100 ticks"));
            h.runAfterDelay(100, () -> { h.assertFalse(lit(h, pos), "Still lit at 100 ticks"); h.succeed(); });
        });
        timed(tests, "renewal_extends_preserves_neighbor_and_serializes", h -> {
            var pos = lantern(h, 5, 3, 5);
            var neighbor = lantern(h, 6, 3, 5);
            SpiritLanternBlock.ignite(h.getLevel(), pos, 100);
            SpiritLanternBlock.ignite(h.getLevel(), neighbor, 100);
            h.runAfterDelay(50, () -> {
                SpiritLanternBlock.ignite(h.getLevel(), pos, 100);
                long now = h.getLevel().getGameTime();
                var saved = h.getLevel().getChunkAt(pos).getTicksForSerialization().blocks().save(now, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
                var restored = LevelChunkTicks.load((net.minecraft.nbt.ListTag) saved,
                        id -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)), new ChunkPos(pos));
                restored.unpack(now);
                long expiry = restored.getAll().filter(t -> t.pos().equals(pos)).mapToLong(net.minecraft.world.ticks.ScheduledTick::triggerTick).max().orElse(-1);
                h.assertValueEqual(expiry, now + 100, "Renewal deadline lost when serializing chunk ticks");
            });
            h.runAfterDelay(101, () -> {
                h.assertTrue(lit(h, pos), "Original tick extinguished the renewed lamp");
                h.assertFalse(lit(h, neighbor), "Renewal changed another position's tick");
                SpiritLanternBlock.ignite(h.getLevel(), pos, 10);
            });
            h.runAfterDelay(149, () -> h.assertTrue(lit(h, pos), "Shorter refresh reduced remaining duration"));
            h.runAfterDelay(150, () -> { h.assertFalse(lit(h, pos), "Renewed lamp did not expire"); h.succeed(); });
        });
        timed(tests, "replacement_does_not_inherit_old_expiry", h -> {
            var pos = lantern(h, 5, 3, 5);
            SpiritLanternBlock.ignite(h.getLevel(), pos, 100);
            h.runAfterDelay(10, () -> {
                h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                h.getLevel().setBlock(pos, OriBlocks.BASH_ANCHOR.get().defaultBlockState(), 3);
                SpiritLanternBlock.ignite(h.getLevel(), pos, 20);
            });
            h.runAfterDelay(29, () -> h.assertTrue(lit(h, pos), "Replacement expired early"));
            h.runAfterDelay(30, () -> { h.assertFalse(lit(h, pos), "Replacement inherited old expiry"); h.succeed(); });
        });
        add(tests, "sphere_counts_boundary_and_refreshes", h -> {
            var centre = h.absoluteVec(new Vec3(5.5, 3.5, 5.5));
            var near = lantern(h, 7, 3, 5);
            var edge = lantern(h, 9, 3, 5);
            var diagonal = lantern(h, 9, 3, 9);
            h.assertValueEqual(SpiritLanternBlock.igniteAround(h.getLevel(), centre, 4, 100), 2, "Sphere count");
            h.assertTrue(lit(h, near) && lit(h, edge), "Sphere excludes near or boundary lamp");
            h.assertFalse(lit(h, diagonal), "Bounding-box corner lit outside sphere");
            h.assertValueEqual(SpiritLanternBlock.igniteAround(h.getLevel(), centre, 4, 40), 2, "Refresh count");
        });
        add(tests, "light_burst_lights_near_but_not_far", h -> {
            var near = lantern(h, 7, 3, 5);
            var far = lantern(h, 11, 3, 5);
            var burst = new LightBurst(cn.wwl.orimod.world.OriEntities.LIGHT_BURST.get(), h.getLevel());
            burst.setPos(h.absoluteVec(new Vec3(5.5, 3.5, 5.5)));
            h.getLevel().addFreshEntity(burst);
            try {
                burst.detonate();
                h.assertTrue(lit(h, near), "Blast did not light lamp two blocks away");
                h.assertFalse(lit(h, far), "Blast lit lamp more than five blocks away");
            } finally { burst.discard(); }
        });
        add(tests, "charge_flame_lights_only_after_accepted_release", h -> {
            var player = spirit(h);
            var near = lantern(h, 7, 2, 5);
            var far = lantern(h, 11, 2, 5);
            h.assertValueEqual(ChargeFlame.release(player), -1, "Locked charge flame accepted");
            h.assertFalse(lit(h, near), "Rejected charge flame lit lantern");
            player.setData(SpiritData.ABILITIES, Ability.CHARGE_FLAME.bit);
            player.setData(SpiritData.ENERGY, 100f);
            h.assertTrue(ChargeFlame.release(player) >= 0, "Unlocked charge flame refused");
            h.assertTrue(lit(h, near), "Charge flame did not light nearby lamp");
            h.assertFalse(lit(h, far), "Charge flame lit distant lamp");
        });
        for (boolean landing : new boolean[]{false, true}) timed(tests,
                landing ? "lit_target_reopens_on_early_landing" : "bash_release_ignites_and_lit_target_remains_valid", h -> {
            var player = spirit(h);
            var pos = lantern(h, 5, 3, 7);
            var state = player.getData(SpiritData.MOVEMENT);
            try {
                press(player, pos, 1, true);
                h.assertTrue(state.bash != null && pos.equals(state.bash.target.block()), "Lantern not selected");
                h.assertFalse(lit(h, pos), "Aiming lights lantern before launch");
                var target = state.bash.target;
                press(player, pos, 2, false);
                h.assertValueEqual(state.acceptedBashes, 1, "Bash launch count");
                h.assertTrue(lit(h, pos), "Bash release did not light lantern");
                h.assertTrue(target.valid(player), "LIT invalidated existing target");
                h.assertTrue(SpiritTargets.find(player, 0, pitch(player, pos)) == null, "Recently bashed lamp was not excluded");
                h.runAfterDelay(landing ? 5 : cn.wwl.orimod.movement.BashSession.TARGET_COOLDOWN_TICKS, () -> {
                    try {
                        if (landing) {
                            player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
                            h.assertTrue(SpiritMovement.grounded(player), "Landing fixture lacks real support");
                            h.assertTrue(h.getLevel().getGameTime() < state.recentBashUntil, "Landing test waited for timeout");
                        }
                        h.assertTrue(lit(h, pos), "Lamp went out during Bash exclusion");
                        h.assertTrue(SpiritTargets.find(player, 0, pitch(player, pos)) != null, "Lit lamp cannot be selected after exclusion");
                        h.succeed();
                    } finally { SpiritMovement.cancel(player, state); }
                });
            } catch (RuntimeException | Error e) { SpiritMovement.cancel(player, state); throw e; }
        });
        add(tests, "cancelled_bash_does_not_ignite", h -> {
            var player = spirit(h);
            var pos = lantern(h, 5, 3, 7);
            var state = player.getData(SpiritData.MOVEMENT);
            press(player, pos, 1, true);
            h.assertTrue(state.bash != null, "Bash fixture did not aim");
            SpiritMovement.cancel(player, state);
            h.assertFalse(lit(h, pos), "Cancelled Bash lights lantern");
        });
        add(tests, "crafting_table_makes_two_and_consumes_ingredients", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var player = c.player();
                var table = new BlockPos(2, 2, 2);
                h.setBlock(table, Blocks.CRAFTING_TABLE);
                var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(table)));
                player.containerMenu = menu;
                try {
                    menu.getSlot(1).set(new ItemStack(Items.AMETHYST_SHARD));
                    menu.getSlot(2).set(new ItemStack(Items.LANTERN));
                    menu.getSlot(3).set(new ItemStack(Items.AMETHYST_SHARD));
                    menu.getSlot(5).set(new ItemStack(Items.GLOWSTONE_DUST));
                    menu.clicked(0, 0, ClickType.PICKUP, player);
                    h.assertTrue(menu.getCarried().is(OriBlocks.BASH_ANCHOR.get().asItem()), "Crafting result is not lantern");
                    h.assertValueEqual(menu.getCarried().getCount(), 2, "Lantern output count");
                    for (int i = 1; i <= 9; i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "Ingredient left in slot " + i);
                    menu.setCarried(ItemStack.EMPTY);
                } finally { player.containerMenu = player.inventoryMenu; }
            }
        });
        add(tests, "amethyst_unlocks_recipe", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var player = c.player();
                var id = ResourceLocation.parse("orimod:bash_anchor");
                h.assertFalse(player.getRecipeBook().contains(id), "New player already knows recipe");
                player.getInventory().add(new ItemStack(Items.GLOWSTONE_DUST));
                player.inventoryMenu.broadcastChanges();
                h.assertFalse(player.getRecipeBook().contains(id), "Glowstone unlocked recipe early");
                player.getInventory().add(new ItemStack(Items.AMETHYST_SHARD));
                player.inventoryMenu.broadcastChanges();
                h.assertTrue(player.getRecipeBook().contains(id), "Amethyst did not unlock lantern recipe");
            }
        });
        add(tests, "refresh_does_not_repeat_ignite_effect_or_sound", h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var pos = lantern(h, 5, 3, 5);
                FxGameTests.drain(c.channel());
                SpiritLanternBlock.ignite(h.getLevel(), pos, 100);
                var packets = FxGameTests.drain(c.channel());
                h.assertValueEqual(FxGameTests.fx(packets).stream().filter(p -> p.kind() == SpiritFxKind.LANTERN_IGNITE).count(), 1L, "Ignition burst count");
                h.assertValueEqual(packets.stream().filter(p -> p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket).count(), 1L, "Ignition sound count");
                SpiritLanternBlock.ignite(h.getLevel(), pos, 200);
                var refresh = FxGameTests.drain(c.channel());
                h.assertTrue(FxGameTests.fx(refresh).isEmpty(), "Refresh replayed ignition burst");
                h.assertFalse(refresh.stream().anyMatch(p -> p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket), "Refresh replayed chime");
            }
        });
        return tests;
    }

    private static BlockPos lantern(GameTestHelper h, int x, int y, int z) {
        var pos = h.absolutePos(new BlockPos(x, y, z));
        h.getLevel().setBlock(pos, OriBlocks.BASH_ANCHOR.get().defaultBlockState(), 3);
        return pos;
    }

    private static boolean lit(GameTestHelper h, BlockPos pos) { return h.getLevel().getBlockState(pos).getValue(SpiritLanternBlock.LIT); }

    private static ServerPlayer spirit(GameTestHelper h) {
        var player = Fixtures.player(h);
        SpiritForm.toggle(player);
        player.setData(SpiritData.ABILITIES, Ability.BASH.bit);
        return player;
    }

    private static float pitch(ServerPlayer player, BlockPos pos) {
        var offset = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
        return (float) -Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));
    }

    private static void press(ServerPlayer player, BlockPos target, int sequence, boolean bash) {
        SpiritMovement.accept(player, new MovementInputPayload(sequence, player.level().getGameTime(),
                MovementInputPayload.ACTIVE | (bash ? MovementInputPayload.BASH : 0), 0, 0, 0, pitch(player, target)));
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        timed(tests, name, h -> { run.accept(h); h.succeed(); });
    }

    private static void timed(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("lantern", "lantern." + name, "orimod_gametest:room", 180, 0, true, run));
    }
}
