package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_r117")
public final class R117GameplayGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var ability : List.of(Ability.BOW, Ability.SMASH, Ability.SPEAR)) {
            for (String path : List.of("panel", "command", "legacy", "full")) add(tests, "gift_" + ability.id() + "_" + path, h -> {
                var p = Fixtures.player(h);
                var item = ability == Ability.BOW ? OriItems.SPIRIT_BOW.get() : ability == Ability.SMASH ? OriItems.SPIRIT_HAMMER.get() : OriItems.SPIRIT_SPEAR.get();
                p.setData(SpiritData.ABILITIES, cn.wwl.orimod.command.OriCommands.allAbilities() & ~ability.bit);
                p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(500));
                if (path.equals("full")) for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
                if (path.equals("command")) for (int i = 0; i < 2; i++) p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), "ori unlock " + ability.id());
                else if (path.equals("legacy")) p.setData(SpiritData.ABILITIES, p.getData(SpiritData.ABILITIES) | ability.bit);
                else SpiritProgression.request(p, "ability", ability.id());
                if (!path.equals("legacy")) h.assertTrue(WeaponGift.forAbility(ability).claimed(p.getData(SpiritData.PROGRESS).claimedWeapons()), "Unlock did not save receipt");
                SpiritProgression.request(p, "claim", ability.id());
                SpiritProgression.request(p, "claim", ability.id());
                SpiritProgression.unlock(p, ability);
                var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(2), e -> e.getItem().is(item));
                try {
                    h.assertValueEqual(p.getInventory().countItem(item) + drops.stream().mapToInt(e -> e.getItem().getCount()).sum(), 1, "Gift lost or duplicated");
                    h.assertValueEqual(drops.size(), path.equals("full") ? 1 : 0, "Full inventory did not drop gift at feet");
                    var saved = p.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                    var copy = Fixtures.player(h); copy.load(saved);
                    h.assertFalse(WeaponGift.forAbility(ability).grant(copy), "Reload repeated gift");
                } finally { drops.forEach(net.minecraft.world.entity.Entity::discard); }
            });
        }
        add(tests, "unlock_all_once", h -> {
            var p = Fixtures.player(h);
            for (int i = 0; i < 2; i++) cn.wwl.orimod.command.OriCommands.unlockAll(p);
            for (var item : List.of(OriItems.SPIRIT_BOW.get(), OriItems.SPIRIT_HAMMER.get(), OriItems.SPIRIT_SPEAR.get()))
                h.assertValueEqual(p.getInventory().countItem(item), 1, "Unlock all duplicated/missed weapon");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).claimedWeapons(), 14, "Unlock all altered blade receipt");
        });
        for (boolean storm : new boolean[]{false, true}) add(tests, "altar_" + (storm ? "storm" : "gloom") + "_cooldown_reload", h -> {
            var p = Fixtures.player(h); var pos = h.absolutePos(new BlockPos(4, 2, 4));
            var state = (storm ? OriBlocks.STORM_ALTAR : OriBlocks.GLOOM_ALTAR).get().defaultBlockState();
            h.getLevel().setBlock(pos, state, 3);
            h.assertFalse(h.getLevel().getBlockEntity(pos).saveWithoutMetadata(h.getLevel().registryAccess()).contains("bossReadyAt"), "Unused altar changed legacy structure NBT");
            var boss = storm ? StormAltarBlock.summon(p, pos) : GloomAltarBlock.summon(p, pos);
            h.assertTrue(boss != null, "First summon failed");
            try {
                h.assertTrue((storm ? StormAltarBlock.summon(p, pos) : GloomAltarBlock.summon(p, pos)) == null, "Living boss duplicated");
                boss.kill();
                var be = (SanctuaryBlockEntity)h.getLevel().getBlockEntity(pos);
                h.assertValueEqual(be.cooldownRemaining(), AltarRules.COOLDOWN_TICKS, "Victory did not start a day cooldown");
                var tag = be.saveWithFullMetadata(h.getLevel().registryAccess());
                var loaded = (SanctuaryBlockEntity)BlockEntity.loadStatic(pos, state, tag, h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(loaded);
                h.assertValueEqual(loaded.cooldownRemaining(), AltarRules.COOLDOWN_TICKS, "Reload lost cooldown");
                h.assertTrue((storm ? StormAltarBlock.summon(p, pos) : GloomAltarBlock.summon(p, pos)) == null, "Cooldown allowed summon");
                var other = pos.offset(2, 0, 0); h.getLevel().setBlock(other, state, 3);
                h.assertFalse(SanctuaryBlockEntity.coolingDown(h.getLevel(), other), "Cooldown leaked to another altar");
                var independent = storm ? StormAltarBlock.summon(p, other) : GloomAltarBlock.summon(p, other);
                h.assertTrue(independent != null, "Another altar shares cooldown"); independent.discard();
                tag.putLong("bossReadyAt", h.getLevel().getGameTime()); loaded.loadWithComponents(tag, h.getLevel().registryAccess());
                var next = storm ? StormAltarBlock.summon(p, pos) : GloomAltarBlock.summon(p, pos);
                h.assertTrue(next != null, "Expired cooldown blocked summon"); next.discard();
                tag.remove("bossReadyAt"); loaded.loadWithComponents(tag, h.getLevel().registryAccess());
                h.assertValueEqual(loaded.cooldownRemaining(), 0L, "Legacy altar gained a cooldown");
            } finally { boss.discard(); cn.wwl.orimod.world.EscapeSequence.stopAll(); }
        });
        for (boolean wood : new boolean[]{false, true}) for (boolean sticky : new boolean[]{false, true}) add(tests, "wall_" + (wood ? "wood" : "stone") + "_sticky_" + sticky, h -> {
            var p = wallPlayer(h, wood);
            if (sticky) p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withOwned(Shard.STICKY.bit).withEquipped(Shard.STICKY.bit));
            h.assertValueEqual(SpiritMovement.climbableWall(p, Direction.EAST), wood, "Contact whitelist");
            SpiritMovement.accept(p, new MovementInputPayload(1, p.level().getGameTime(), MovementInputPayload.ACTIVE, 1, 0, -90, 0));
            SpiritMovement.tick(new PlayerTickEvent.Post(p));
            h.assertValueEqual(p.getData(SpiritData.MOVEMENT).mode, MoveMode.WALL_SLIDE, "Wall slide lost");
            Fixtures.near(h, p.getDeltaMovement().y, wood ? MovementRules.CLIMB_SPEED : MovementRules.WALL_SLIDE_SPEED, "Climb restriction");
            h.assertValueEqual(SpiritMovement.slidingWall(p, Vec3.ZERO, Direction.EAST.ordinal()) != null, wood && sticky, "Passive Sticky whitelist");
            p.setOnGround(false); p.setDeltaMovement(0, -.5, 0);
            SpiritMovement.accept(p, new MovementInputPayload(2, p.level().getGameTime(), MovementInputPayload.ACTIVE | MovementInputPayload.JUMP, 1, 0, -90, 0));
            h.assertValueEqual(p.getData(SpiritData.MOVEMENT).mode, MoveMode.WALL_JUMP, "Stone/wood wall jump blocked");
            h.assertTrue(p.getDeltaMovement().x < 0 && p.getDeltaMovement().y > 0, "Wall jump did not push away");
        });
        add(tests, "wall_head_contact_and_unrelated_log", h -> {
            var p = wallPlayer(h, false);
            h.setBlock(new BlockPos(8, 4, 5), Blocks.OAK_LOG);
            h.assertFalse(SpiritMovement.climbableWall(p, Direction.EAST), "Log behind stone authorizes climb");
            h.setBlock(new BlockPos(7, 5, 5), Blocks.OAK_LOG);
            h.assertTrue(SpiritMovement.climbableWall(p, Direction.EAST), "Head-only contact not sampled");
        });
        for (int height : new int[]{2, 24}) add(tests, "hammer_height_boost_" + height, h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.FORM, true); p.refreshDimensions();
            var landing = p.position(); p.setPos(landing.add(0, height, 0)); p.setOnGround(false);
            HammerSlam.release(p);
            p.setPos(landing); p.setOnGround(true);
            var near = h.spawn(net.minecraft.world.entity.EntityType.HUSK, new Vec3(5.5, 2, 7.7));
            var edge = h.spawn(net.minecraft.world.entity.EntityType.HUSK, new Vec3(8.4, 2, 7.7));
            try {
                for (var target : List.of(near, edge)) { target.setNoAi(true); target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0); }
                HammerSlam.tick(p);
                Fixtures.near(h, near.getMaxHealth() - near.getHealth(), cn.wwl.orimod.item.SpiritHammerItem.DAMAGE * SlamRules.strength(height), "Height damage");
                h.assertValueEqual(edge.getHealth() < edge.getMaxHealth(), height == 24, "Height radius did not grow with impact");
            } finally { near.discard(); edge.discard(); }
        });
        for (var floor : List.of(Blocks.STONE.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState())) add(tests, "hammer_fall_collision_" + (floor.is(Blocks.STONE) ? "block" : "slab"), h -> {
            var p = Fixtures.player(h); p.setData(SpiritData.FORM, true); p.refreshDimensions();
            // The reusable room has a roof. Open only the fall shaft so this checks the selected floor.
            for (int y = 2; y <= 63; y++) h.setBlock(new BlockPos(5, y, 5), Blocks.AIR);
            h.setBlock(new BlockPos(5, 1, 5), floor);
            p.setPos(h.absoluteVec(new Vec3(5.5, 62, 5.5))); p.setOnGround(false);
            HammerSlam.release(p); Fixtures.near(h, p.getDeltaMovement().y, -1.2, "Slam onset changed");
            double fastest = 0; float health = p.getHealth();
            for (int tick = 0; tick < 100 && !p.onGround(); tick++) {
                HammerSlam.tick(p); fastest = Math.min(fastest, p.getDeltaMovement().y);
                p.move(MoverType.SELF, p.getDeltaMovement());
            }
            h.assertTrue(p.onGround(), "Slam tunneled through floor");
            Fixtures.near(h, p.getY(), h.absolutePos(new BlockPos(5, 1, 5)).getY() + (floor.is(Blocks.STONE) ? 1 : .5), "Wrong landing height");
            Fixtures.near(h, fastest, SlamRules.TERMINAL_SPEED, "Slam did not reach terminal speed");
            HammerSlam.tick(p);
            h.assertValueEqual(p.getData(SpiritData.COMBAT).mode, "idle", "Landing retained slam");
            h.assertValueEqual(p.getHealth(), health, "Slam caused fall damage");
        });
        return tests;
    }

    private static ServerPlayer wallPlayer(GameTestHelper h, boolean wood) {
        var p = Fixtures.player(h); p.setData(SpiritData.FORM, true); p.refreshDimensions();
        p.setData(SpiritData.ABILITIES, cn.wwl.orimod.command.OriCommands.allAbilities());
        for (int y = 2; y <= 7; y++) h.setBlock(new BlockPos(7, y, 5), wood ? Blocks.OAK_LOG : Blocks.STONE);
        p.setPos(h.absoluteVec(new Vec3(7 - p.getBbWidth() / 2 - .02, 4, 5.5))); p.setOnGround(false); p.setDeltaMovement(0, -.5, 0);
        var s = p.getData(SpiritData.MOVEMENT); s.dimension = p.level().dimension().location().toString(); s.previousPosition = p.position(); s.groundJumpAvailable = false;
        return p;
    }
    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("r117_gameplay", "r117_gameplay." + name, "orimod_r117:room", 100, 0, true, h -> { run.accept(h); h.succeed(); }));
    }
}
