package cn.wwl.orimod.gametest;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * R17: a fresh survival player reaches every ruin without commands or debug grants.
 * Everything goes through the same server entry points that player input reaches
 * (crafting menu clicks, item use, panel requests, shrine use, kills and pickups).
 * The documented boundary: route traversal increments the accepted movement counters,
 * as in {@link RuinRotationGameTests}; real movement is covered by the client acceptance runs.
 */
@GameTestHolder("orimod_gametest")
public final class SurvivalFlowGameTests {
    /** Kills needed on Easy/Normal/Hard with the R17 light economy; guards against an accidental grind. */
    static final int MAX_KILLS = 40;
    /** Peaceful replaces kills with the Spirit Light recipe (4 per craft). */
    static final int MAX_LIGHT_CRAFTS = 20;

    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "recipes_unlock_from_vanilla_materials", SurvivalFlowGameTests::recipes);
        add(tests, "fresh_easy_world_to_all_ruins", h -> flow(h, Difficulty.EASY));
        add(tests, "fresh_peaceful_world_to_all_ruins", h -> flow(h, Difficulty.PEACEFUL));
        add(tests, "ruins_hold_claimable_cell_pedestals", SurvivalFlowGameTests::cellPedestals);
        add(tests, "glide_ruins_lift_over_their_gaps", SurvivalFlowGameTests::updrafts);
        add(tests, "cave_ruin_keystone_vault_opens_with_two_keystones", h -> {
            var placement = place(h, RuinKind.CAVE, Rotation.CLOCKWISE_90);
            try {
                var layout = RuinKind.CAVE.layout();
                var player = cn.wwl.orimod.gametest.Fixtures.player(h);
                for (var local : layout.keystones()) {
                    var pos = placement.block(local);
                    h.assertTrue(h.getLevel().getBlockState(pos.below()).isFaceSturdy(h.getLevel(), pos.below(), net.minecraft.core.Direction.UP), "Keystone pedestal floats at " + pos);
                    h.assertTrue(cn.wwl.orimod.block.KeystonePedestalBlock.take(player, pos), "Keystone not taken at " + pos);
                    h.assertTrue(!cn.wwl.orimod.block.KeystonePedestalBlock.take(player, pos), "Keystone taken twice at " + pos);
                }
                var keystone = cn.wwl.orimod.item.OriItems.KEYSTONE.get();
                h.assertValueEqual(player.getInventory().countItem(keystone), 2, "Keystones collected");
                var door = placement.block(layout.vault().door().getFirst());
                h.assertValueEqual(cn.wwl.orimod.block.KeystoneDoorBlock.door(h.getLevel(), door).size(), 4, "Door size");
                var cell = placement.block(layout.cells().getLast().position());
                h.assertTrue(h.getLevel().getBlockState(cell).getBlock() instanceof cn.wwl.orimod.block.CellPedestalBlock, "Vault cell missing");
                int slot = player.getInventory().findSlotMatchingItem(new ItemStack(keystone));
                var stack = player.getInventory().getItem(slot);
                h.assertValueEqual(cn.wwl.orimod.block.KeystoneDoorBlock.insert(player, door, stack), 1, "First keystone");
                for (var local : layout.vault().door())
                    h.assertValueEqual(h.getLevel().getBlockState(placement.block(local)).getValue(cn.wwl.orimod.block.KeystoneDoorBlock.FILLED), 1, "Door progress not shared");
                h.assertValueEqual(cn.wwl.orimod.block.KeystoneDoorBlock.insert(player, placement.block(layout.vault().door().getLast()), stack), 0, "Second keystone did not open");
                for (var local : layout.vault().door()) h.assertTrue(h.getLevel().getBlockState(placement.block(local)).isAir(), "Door block left after opening");
                h.assertValueEqual(player.getInventory().countItem(keystone), 0, "Keystones not consumed");
            } finally { clear(h, placement, RuinKind.CAVE); }
            h.succeed();
        });
        add(tests, "map_stone_surveys_the_cave_ruin", h -> {
            var placement = place(h, RuinKind.CAVE, Rotation.NONE);
            try {
                var stone = placement.block(RuinKind.CAVE.layout().mapStone());
                h.assertTrue(h.getLevel().getBlockState(stone).is(cn.wwl.orimod.block.OriBlocks.MAP_STONE.get()), "Map stone missing");
                var player = cn.wwl.orimod.gametest.Fixtures.player(h);
                var survey = cn.wwl.orimod.block.MapStoneBlock.survey(player, stone);
                h.assertValueEqual(survey.cells(), 2, "Cells surveyed");
                h.assertValueEqual(survey.keystones(), 2, "Keystones surveyed");
                h.assertValueEqual(survey.altars(), 1, "Altar surveyed");
                cn.wwl.orimod.block.KeystonePedestalBlock.take(player, placement.block(RuinKind.CAVE.layout().keystones().getFirst()));
                h.assertValueEqual(cn.wwl.orimod.block.MapStoneBlock.survey(player, stone).keystones(), 1, "Survey after taking a keystone");
            } finally { clear(h, placement, RuinKind.CAVE); }
            h.succeed();
        });
        add(tests, "cave_ruin_holds_the_gloom_altar", h -> {
            var placement = place(h, RuinKind.CAVE, Rotation.COUNTERCLOCKWISE_90);
            try {
                var pos = placement.block(RuinKind.CAVE.layout().bossAltar());
                h.assertTrue(h.getLevel().getBlockState(pos).is(cn.wwl.orimod.block.OriBlocks.GLOOM_ALTAR.get()), "Gloom altar missing at " + pos);
                for (int dy = 1; dy <= 7; dy++) h.assertTrue(h.getLevel().getBlockState(pos.above(dy)).isAir(), "Boss space blocked at +" + dy);
            } finally { clear(h, placement, RuinKind.CAVE); }
            h.succeed();
        });
        return tests;
    }

    private static void recipes(GameTestHelper h) {
        try (var c = Fixtures.connectedPlayer(h)) {
            var player = fresh(c.player());
            for (var id : new String[]{"spirit_core", "ruin_guide", "spirit_light", "spirit_blade", "spirit_bow"})
                h.assertFalse(knows(player, id), "Recipe known before any material: " + id);
            give(player, Items.COPPER_INGOT, 1);
            for (var id : new String[]{"spirit_core", "ruin_guide", "spirit_light"})
                h.assertTrue(knows(player, id), "Copper did not unlock recipe " + id);
            give(player, Items.AMETHYST_SHARD, 1);
            for (var id : new String[]{"spirit_blade", "spirit_bow"})
                h.assertTrue(knows(player, id), "Amethyst did not unlock recipe " + id);
        }
        h.succeed();
    }

    private static void flow(GameTestHelper h, Difficulty difficulty) {
        var server = h.getLevel().getServer(); var original = h.getLevel().getDifficulty();
        server.setDifficulty(difficulty, true);
        var c = Fixtures.connectedPlayer(h);
        var player = fresh(c.player());
        try {
            // 1. Craft the Spirit Core at a crafting table from vanilla materials.
            var core = craft(h, player, new Item[]{Items.COPPER_INGOT, Items.GLASS, Items.COPPER_INGOT, Items.GLASS, Items.IRON_INGOT,
                    Items.GLASS, Items.COPPER_INGOT, Items.GLASS, Items.COPPER_INGOT});
            h.assertTrue(core.is(OriItems.SPIRIT_CORE), "Crafting table did not make a Spirit Core");
            // 2. Use it: awaken, receive the guide, enter spirit form.
            player.setItemInHand(InteractionHand.MAIN_HAND, core);
            player.gameMode.useItem(player, player.serverLevel(), core, InteractionHand.MAIN_HAND);
            h.assertTrue(SpiritData.isAwakened(player), "Spirit Core did not awaken");
            h.assertTrue(SpiritData.isSpirit(player), "Spirit Core did not switch form");
            h.assertTrue(player.getInventory().countItem(OriItems.RUIN_GUIDE.get()) == 1, "Awakening did not give the Ruin Guide");
            h.assertValueEqual(player.getData(SpiritData.PROGRESS).light(), 0, "Fresh player started with light");
            SpiritProgression.request(player, "claim", "blade");
            h.assertTrue(player.getInventory().countItem(OriItems.SPIRIT_BLADE.get()) == 1, "Panel did not hand out the free blade");

            int[] earned = new int[2]; // kills or light crafts, total light from them
            var placements = new RuinPlacement[3];
            var order = new RuinKind[]{RuinKind.FOREST, RuinKind.CREEK, RuinKind.CAVE};
            var rotations = new Rotation[]{Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180};
            for (int i = 0; i < order.length; i++) {
                var kind = order[i];
                var placement = placements[i] = place(h, kind, rotations[i]);
                var shrine = placement.block(kind.layout().shrine());
                player.teleportTo(placement.point(kind.layout().safe()).x, placement.point(kind.layout().safe()).y, placement.point(kind.layout().safe()).z);
                int before = light(player);
                useShrine(h, player, shrine);
                h.assertValueEqual(light(player) - before, 15, kind.id + " discovery reward");
                SpiritChallenge.request(player, shrine, true);
                h.assertTrue(SpiritChallenge.session(player) == null, kind.id + " started without its abilities");
                for (var ability : Ability.values())
                    if ((kind.abilities & ability.bit) != 0 && !ability.unlocked(player)) buy(h, player, ability, difficulty, earned);
                SpiritChallenge.request(player, shrine, true);
                h.assertTrue(SpiritChallenge.session(player) != null, kind.id + " did not start with prerequisites");
                before = light(player);
                complete(h, player, kind, placement);
                h.assertValueEqual(light(player) - before, 30, kind.id + " first clear reward");
                var ghost = SpiritChallenge.ghost(player, cn.wwl.orimod.world.ChallengeSession.key(h.getLevel().dimension(), shrine));
                h.assertTrue(ghost != null && ghost.size() >= 2, kind.id + " best run left no ghost");
                clear(h, placement, kind);
            }
            buy(h, player, Ability.BOW, difficulty, earned);
            h.assertValueEqual(player.getData(SpiritData.ABILITIES), 63, "Not every ability was bought");
            var rewards = player.getData(SpiritData.PROGRESS).rewards();
            h.assertValueEqual((int) rewards.keySet().stream().filter(k -> k.startsWith("clear/")).count(), 3, "Cleared ruin records");
            int limit = difficulty == Difficulty.PEACEFUL ? MAX_LIGHT_CRAFTS : MAX_KILLS;
            h.assertTrue(earned[0] <= limit, "Light grind too long: " + earned[0]);
            cn.wwl.orimod.OriMod.LOGGER.info("Survival flow {}: {} {} for {} light, leftover {}", difficulty.getKey(), earned[0],
                    difficulty == Difficulty.PEACEFUL ? "light crafts" : "kills", earned[1], light(player));
            // 3. Save and load the player file; progress must survive the restart path.
            var tag = player.saveWithoutId(new CompoundTag());
            var reloaded = new ServerPlayer(server, h.getLevel(), player.getGameProfile(), ClientInformation.createDefault());
            reloaded.load(tag);
            h.assertTrue(SpiritData.isAwakened(reloaded), "Awakening lost on save");
            h.assertValueEqual(reloaded.getData(SpiritData.ABILITIES), 63, "Abilities lost on save");
            h.assertValueEqual(reloaded.getData(SpiritData.PROGRESS), player.getData(SpiritData.PROGRESS), "Progress changed on save");
        } finally {
            SpiritChallenge.finish(player, "exit", false); c.close();
            server.setDifficulty(original, true);
        }
        h.succeed();
    }

    private static ServerPlayer fresh(ServerPlayer player) {
        player.setData(SpiritData.AWAKENED, false);
        player.getInventory().clearContent();
        return player;
    }

    private static boolean knows(ServerPlayer player, String id) {
        return player.getRecipeBook().contains(ResourceLocation.fromNamespaceAndPath("orimod", id));
    }

    private static void give(ServerPlayer player, Item item, int count) {
        player.getInventory().add(new ItemStack(item, count));
        player.inventoryMenu.broadcastChanges();
    }

    private static int light(ServerPlayer player) { return player.getData(SpiritData.PROGRESS).light(); }

    private static ItemStack craft(GameTestHelper h, ServerPlayer player, Item[] grid) {
        var pos = new BlockPos(2, 2, 2); h.setBlock(pos, Blocks.CRAFTING_TABLE);
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(pos)));
        player.containerMenu = menu;
        try {
            for (int i = 0; i < grid.length; i++) if (grid[i] != null) menu.getSlot(i + 1).set(new ItemStack(grid[i]));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            var result = menu.getCarried().copy();
            menu.setCarried(ItemStack.EMPTY);
            for (int i = 1; i <= 9; i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "Crafting left an ingredient in slot " + i);
            return result;
        } finally { player.containerMenu = player.inventoryMenu; h.setBlock(pos, Blocks.AIR); }
    }

    private static void buy(GameTestHelper h, ServerPlayer player, Ability ability, Difficulty difficulty, int[] earned) {
        if (ability.prerequisite != null && !ability.prerequisite.unlocked(player)) buy(h, player, ability.prerequisite, difficulty, earned);
        while (light(player) < ability.cost) {
            int before = light(player);
            if (difficulty == Difficulty.PEACEFUL) {
                var stack = craft(h, player, new Item[]{Items.COPPER_INGOT, Items.TORCH, Items.SUGAR});
                h.assertTrue(stack.is(OriItems.SPIRIT_LIGHT) && stack.getCount() == 4, "Peaceful light recipe failed");
                pickUp(h, player, stack);
            } else kill(h, player);
            h.assertTrue(light(player) > before, "Light source gave nothing");
            earned[0]++; earned[1] += light(player) - before;
            h.assertTrue(earned[0] <= 200, "Light loop did not converge");
        }
        int before = light(player);
        SpiritProgression.request(player, "ability", ability.name().toLowerCase());
        h.assertTrue(ability.unlocked(player), "Panel purchase failed: " + ability);
        h.assertValueEqual(before - light(player), ability.cost, ability + " cost");
    }

    private static void kill(GameTestHelper h, ServerPlayer player) {
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(zombie != null, "Zombie fixture");
        zombie.setBaby(false); zombie.setPos(player.position().add(1, 0, 0));
        h.getLevel().addFreshEntity(zombie);
        zombie.hurt(player.damageSources().playerAttack(player), 1000);
        h.assertTrue(zombie.isDeadOrDying(), "Zombie survived");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(zombie.blockPosition()).inflate(3));
        for (var drop : drops) {
            if (drop.getItem().is(OriItems.SPIRIT_LIGHT)) { drop.setNoPickUpDelay(); drop.playerTouch(player); }
            if (!drop.isRemoved()) drop.discard();
        }
        zombie.discard();
    }

    private static void pickUp(GameTestHelper h, ServerPlayer player, ItemStack stack) {
        var entity = new ItemEntity(h.getLevel(), player.getX(), player.getY(), player.getZ(), stack);
        entity.setNoPickUpDelay(); h.getLevel().addFreshEntity(entity);
        entity.playerTouch(player);
        h.assertTrue(entity.isRemoved(), "Spirit Light item was not absorbed");
    }

    /** R44: every ruin carries a cell pedestal on solid ground; each player may take its cell once. */
    private static void cellPedestals(GameTestHelper h) {
        var rotations = Rotation.values();
        for (int i = 0; i < RuinKind.values().length; i++) {
            var kind = RuinKind.values()[i];
            var placement = place(h, kind, rotations[(i + 1) % rotations.length]);
            try {
                h.assertTrue(!kind.layout().cells().isEmpty(), kind.id + " has no cell spot");
                for (var cell : kind.layout().cells()) {
                    var pos = placement.block(cell.position());
                    var state = h.getLevel().getBlockState(pos);
                    h.assertTrue(state.getBlock() instanceof cn.wwl.orimod.block.CellPedestalBlock pedestal && pedestal.kind == cell.kind(), kind.id + " pedestal missing at " + pos);
                    h.assertTrue(h.getLevel().getBlockState(pos.below()).isFaceSturdy(h.getLevel(), pos.below(), net.minecraft.core.Direction.UP), kind.id + " pedestal floats");
                    h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), kind.id + " pedestal is buried");
                    var player = cn.wwl.orimod.gametest.Fixtures.player(h);
                    var pedestal = (cn.wwl.orimod.block.CellPedestalBlock) state.getBlock();
                    var item = cell.kind() == cn.wwl.orimod.progression.SpiritCells.Kind.LIFE ? cn.wwl.orimod.item.OriItems.LIFE_CELL.get() : cn.wwl.orimod.item.OriItems.ENERGY_CELL.get();
                    h.assertTrue(pedestal.claim(player, pos), kind.id + " cell not given");
                    h.assertTrue(player.getInventory().countItem(item) == 1, kind.id + " cell item missing");
                    h.assertTrue(!pedestal.claim(player, pos) && player.getInventory().countItem(item) == 1, kind.id + " cell given twice");
                    h.assertTrue(pedestal.claim(cn.wwl.orimod.gametest.Fixtures.player(h), pos), kind.id + " second player got nothing");
                }
            } finally { clear(h, placement, kind); }
        }
        h.succeed();
    }

    /** R46: the glide ruins carry updraft vents with open sky, and a glider above them is lifted. */
    private static void updrafts(GameTestHelper h) {
        for (var kind : new RuinKind[]{RuinKind.FOREST, RuinKind.CREEK}) {
            var placement = place(h, kind, Rotation.CLOCKWISE_180);
            try {
                h.assertTrue(kind.layout().updrafts().size() >= 4, kind.id + " has too few updrafts");
                if (kind.layout().gloomWater() != null) {
                    int gloom = 0;
                    for (var local : BlockPos.betweenClosed(kind.layout().gloomWater()[0], kind.layout().gloomWater()[1]))
                        if (h.getLevel().getBlockState(placement.block(local)).is(cn.wwl.orimod.block.OriBlocks.GLOOM_WATER.get())) gloom++;
                    h.assertTrue(gloom >= 20, kind.id + " gloom water under the glide route: " + gloom);
                }
                for (var local : kind.layout().updrafts()) {
                    var pos = placement.block(local);
                    h.assertTrue(h.getLevel().getBlockState(pos).is(cn.wwl.orimod.block.OriBlocks.UPDRAFT.get()), kind.id + " updraft missing at " + pos);
                    var probe = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + 6, pos.getZ() + .5, ItemStack.EMPTY);
                    h.assertTrue(cn.wwl.orimod.block.UpdraftBlock.lifting(h.getLevel(), probe), kind.id + " updraft blocked above " + pos);
                }
            } finally { clear(h, placement, kind); }
        }
        h.succeed();
    }

    private static RuinPlacement place(GameTestHelper h, RuinKind kind, Rotation rotation) {
        var level = h.getLevel();
        var placement = RuinPlacement.atMinimum(kind, h.absolutePos(new BlockPos(12, 2, 12)), rotation);
        var piece = new RuinPiece(level.getStructureManager(), kind, placement);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(17),
                piece.getBoundingBox(), new ChunkPos(placement.origin()), BlockPos.ZERO);
        return placement;
    }

    private static void clear(GameTestHelper h, RuinPlacement placement, RuinKind kind) {
        var level = h.getLevel();
        var box = new RuinPiece(level.getStructureManager(), kind, placement).getBoundingBox();
        for (var target : level.getEntitiesOfClass(PracticeTarget.class, AABB.of(box).inflate(2))) target.discard();
        BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16));
    }

    private static void useShrine(GameTestHelper h, ServerPlayer player, BlockPos shrine) {
        var state = h.getLevel().getBlockState(shrine);
        h.assertTrue(state.getBlock() instanceof RuinShrineBlock, "Shrine missing");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var hit = new net.minecraft.world.phys.BlockHitResult(shrine.getCenter(), net.minecraft.core.Direction.UP, shrine, false);
        player.gameMode.useItemOn(player, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    }

    private static void complete(GameTestHelper h, ServerPlayer player, RuinKind kind, RuinPlacement placement) {
        var session = SpiritChallenge.session(player);
        var movement = player.getData(SpiritData.MOVEMENT);
        int blade = player.getInventory().findSlotMatchingItem(new ItemStack(OriItems.SPIRIT_BLADE.get()));
        if (blade >= 0) player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(blade).copy());
        for (var checkpoint : kind.layout().checkpoints()) {
            player.setPos(placement.point(checkpoint.position())); player.setOnGround(true);
            switch (checkpoint.action()) {
                case "jump" -> movement.acceptedJumps += checkpoint.count();
                case "wall_jump" -> movement.acceptedWallJumps += checkpoint.count();
                case "dash" -> movement.acceptedDashes += checkpoint.count();
                case "bash" -> movement.acceptedBashes += checkpoint.count();
                case "glide" -> movement.startedGlides += checkpoint.count();
                case "grapple" -> movement.completedGrapples += checkpoint.count();
                case "stomp" -> movement.stompImpacts += checkpoint.count();
                case "blade" -> {
                    var target = h.getLevel().getEntity(session.target);
                    h.assertTrue(target instanceof PracticeTarget, kind.id + " blade target missing");
                    player.attack(target);
                    if (target.isAlive()) target.hurt(player.damageSources().playerAttack(player), 30);
                }
            }
            SpiritChallenge.tick(new PlayerTickEvent.Post(player));
        }
        h.assertTrue(SpiritChallenge.session(player) == null, kind.id + " route did not finish");
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("flow", "flow." + name, "orimod_gametest:ruin_room", 200, 0, true, run));
    }
}
