package cn.wwl.orimod.gametest;

import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.*;
import java.util.function.Consumer;

/** R99: real item consumption, damage events, Bash release and shared wall physics for the five new shards. */
@GameTestHolder("orimod_gametest")
public final class ShardGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "pickup_heals_and_restores_energy_once", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit);
            pickup(p, 1);
            Fixtures.near(h, p.getHealth(), 10.5, "Single orb healing");
            Fixtures.near(h, SpiritProgression.energy(p), 12, "Single orb energy");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 1, "Currency");
        });
        add(tests, "magnet_reaches_pickup_without_double_harvest", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit | Shard.GATHERING.bit);
            var item = orb(p, 1); item.setPos(p.position().add(2, .5, 0));
            try {
                h.assertTrue(SpiritProgression.pullLight(p) >= 1, "Magnet did not pull");
                h.assertTrue(item.getDeltaMovement().x < 0, "Orb moved away");
                Fixtures.near(h, p.getHealth(), 10, "Harvest before consumption");
                item.setPos(p.position()); item.playerTouch(p);
                SpiritProgression.pickup(new ItemEntityPickupEvent.Pre(p, item));
                h.assertTrue(item.isRemoved(), "Picked orb remains");
                Fixtures.near(h, p.getHealth(), 10.5, "Magnet/pickup double counted healing");
                Fixtures.near(h, SpiritProgression.energy(p), 12, "Magnet/pickup double counted energy");
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 1, "Magnet/pickup double counted light");
            } finally { item.discard(); }
        });
        timed(tests, "inventory_auto_deposit_harvests_once", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit);
            p.getInventory().add(new ItemStack(OriItems.SPIRIT_LIGHT.get(), 3));
            int delay = (int) (20 - h.getLevel().getGameTime() % 20);
            h.runAfterDelay(delay, () -> {
                p.getData(SpiritData.ENERGY_STATE).lastSpent = h.getLevel().getGameTime();
                SpiritProgression.tick(new PlayerTickEvent.Post(p));
                SpiritProgression.tick(new PlayerTickEvent.Post(p));
                Fixtures.near(h, p.getHealth(), 11, "Stack exceeds healing window");
                Fixtures.near(h, SpiritProgression.energy(p), 16, "Inventory counted twice");
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 3, "Inventory currency");
                h.assertValueEqual(p.getInventory().countItem(OriItems.SPIRIT_LIGHT.get()), 0, "Inventory not consumed");
                h.succeed();
            });
        });
        add(tests, "currency_capacity_only_harvests_consumed_items", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit);
            p.setData(SpiritData.PROGRESS, p.getData(SpiritData.PROGRESS).withLight(SpiritProgress.LIGHT_LIMIT - 1));
            var stack = new ItemStack(OriItems.SPIRIT_LIGHT.get(), 5);
            h.assertValueEqual(SpiritProgression.collectLight(p, stack), 1, "Accepted count at currency cap");
            h.assertValueEqual(SpiritProgression.collectLight(p, stack), 0, "Full wallet consumed more");
            h.assertValueEqual(stack.getCount(), 4, "Unaccepted items lost");
            Fixtures.near(h, p.getHealth(), 10.5, "Unaccepted items healed");
            Fixtures.near(h, SpiritProgression.energy(p), 12, "Unaccepted items restored energy");
        });
        for (boolean human : List.of(false, true)) add(tests, human ? "human_does_not_harvest" : "unequipped_does_not_harvest", h -> {
            var p = player(h, human ? Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit : 0);
            if (human) SpiritForm.toggle(p);
            pickup(p, 4);
            Fixtures.near(h, p.getHealth(), 10, "Inactive shard healed");
            Fixtures.near(h, SpiritProgression.energy(p), 10, "Inactive shard restored energy");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 4, "Ordinary pickup lost currency");
        });
        add(tests, "reward_currency_is_not_a_pickup", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit);
            SpiritProgression.grantLight(p, 10); SpiritProgression.reward(p, "test/reward", 10, 0);
            Fixtures.near(h, p.getHealth(), 10, "Quest/command money healed");
            Fixtures.near(h, SpiritProgression.energy(p), 10, "Quest/command money restored energy");
        });
        add(tests, "harvest_respects_health_and_energy_capacity", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit | Shard.ENERGY_HARVEST.bit);
            p.setHealth(p.getMaxHealth() - .25f); p.setData(SpiritData.ENERGY, SpiritProgression.maxEnergy(p) - 1);
            pickup(p, 64);
            Fixtures.near(h, p.getHealth(), p.getMaxHealth(), "Overhealed");
            Fixtures.near(h, SpiritProgression.energy(p), SpiritProgression.maxEnergy(p), "Energy overflow");
        });
        timed(tests, "life_harvest_rolling_window_19_20_boundary", h -> {
            var p = player(h, Shard.LIFE_HARVEST.bit);
            pickup(p, 1);
            h.runAfterDelay(19, () -> { pickup(p, 8); Fixtures.near(h, p.getHealth(), 11, "19 tick limit"); });
            h.runAfterDelay(20, () -> { pickup(p, 8); Fixtures.near(h, p.getHealth(), 11.5, "20 tick rolling refill"); });
            h.runAfterDelay(21, () -> { pickup(p, 8); Fixtures.near(h, p.getHealth(), 11.5, "Burst crossed second boundary"); });
            h.runAfterDelay(39, () -> { pickup(p, 8); Fixtures.near(h, p.getHealth(), 12, "Second half of window did not expire"); h.succeed(); });
        });
        add(tests, "fourth_slot_requires_ten_skills_and_reduction_keeps_equipment", h -> {
            var p = player(h, 7); p.setData(SpiritData.ABILITIES, (1 << 9) - 1);
            SpiritProgression.request(p, "equip", "life_harvest");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).equippedShards(), 7, "Fourth equipped at nine skills");
            p.setData(SpiritData.ABILITIES, (1 << 10) - 1);
            SpiritProgression.request(p, "equip", "life_harvest");
            h.assertValueEqual(Integer.bitCount(p.getData(SpiritData.PROGRESS).equippedShards()), 4, "Fourth slot did not open");
            p.setData(SpiritData.ABILITIES, Ability.BASH.bit);
            int equipped = p.getData(SpiritData.PROGRESS).equippedShards();
            SpiritProgression.request(p, "equip", "thorn");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).equippedShards(), equipped, "Slot reduction removed equipment or accepted another shard");
            SpiritProgression.request(p, "unequip", "life_harvest");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).equippedShards(), 7, "Overfull equipment cannot be removed");
        });
        for (var shard : List.of(Shard.ULTRA_BASH, Shard.STICKY)) add(tests, shard.id() + "_purchase_checks_prerequisite", h -> {
            var p = player(h, 0); p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(20));
            p.setData(SpiritData.ABILITIES, 0); SpiritProgression.request(p, "shard", shard.id());
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).ownedShards(), 0, "Prerequisite ignored");
            p.setData(SpiritData.ABILITIES, shard.prerequisite.bit); SpiritProgression.request(p, "shard", shard.id());
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).ownedShards(), shard.bit, "Valid purchase refused");
        });

        add(tests, "thorn_real_melee_and_per_attacker_cooldown", h -> damagePlayer(h, p -> {
            var first = enemy(h, p.position().add(2, 0, 0)); var second = enemy(h, p.position().add(0, 0, 2));
            try {
                hit(h, p, first); Fixtures.near(h, first.getHealth(), 18, "First reflection");
                first.invulnerableTime = 0; hit(h, p, first);
                Fixtures.near(h, first.getHealth(), 18, "Cooldown reflected twice");
                hit(h, p, second); Fixtures.near(h, second.getHealth(), 18, "Different attacker shared cooldown");
                p.getData(SpiritData.ENERGY_STATE).thornHits.put(first.getUUID(), p.level().getGameTime() - 9);
                first.invulnerableTime = 0; hit(h, p, first);
                Fixtures.near(h, first.getHealth(), 18, "Cooldown ended at nine");
                p.getData(SpiritData.ENERGY_STATE).thornHits.put(first.getUUID(), p.level().getGameTime() - 10);
                first.invulnerableTime = 0; hit(h, p, first);
                Fixtures.near(h, first.getHealth(), 16, "Cooldown not ready at ten");
            } finally { first.discard(); second.discard(); }
        }));
        for (String reason : List.of("projectile", "indirect", "far", "reflected", "zero", "human", "unequipped")) {
            add(tests, "thorn_rejects_" + reason, h -> damagePlayer(h, p -> {
                var enemy = enemy(h, p.position().add(reason.equals("far") ? 3.01 : 2, 0, 0));
                var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel()); arrow.setOwner(enemy);
                try {
                    if (reason.equals("human")) SpiritForm.toggle(p);
                    if (reason.equals("unequipped")) p.setData(SpiritData.PROGRESS, p.getData(SpiritData.PROGRESS).withEquipped(0));
                    var source = switch (reason) {
                        case "projectile" -> p.damageSources().arrow(arrow, enemy);
                        case "indirect" -> p.damageSources().indirectMagic(p, enemy);
                        case "reflected" -> p.damageSources().thorns(enemy);
                        default -> p.damageSources().mobAttack(enemy);
                    };
                    p.invulnerableTime = 0;
                    float before = p.getHealth();
                    p.hurt(source, reason.equals("zero") ? 0 : 1);
                    if (!reason.equals("zero")) h.assertTrue(p.getHealth() < before, "Damage rejected by fixture");
                    Fixtures.near(h, enemy.getHealth(), 20, "Reflected forbidden source " + reason);
                    h.assertTrue(p.getData(SpiritData.ENERGY_STATE).thornHits.isEmpty(), "Forbidden source consumed cooldown");
                } finally { enemy.discard(); arrow.discard(); }
            }));
        }
        add(tests, "two_players_with_thorn_do_not_recurse", h -> damagePlayer(h, victim -> {
            var server = h.getLevel().getServer(); boolean pvp = server.isPvpAllowed();
            try (var c = Fixtures.connectedPlayer(h)) {
                var attacker = c.player();
                for (int i = 0; i < 61; i++) attacker.tick();
                prepare(attacker, Shard.THORN.bit); attacker.setHealth(attacker.getMaxHealth());
                attacker.setPos(victim.position().add(1, 0, 0));
                victim.setHealth(victim.getMaxHealth()); server.setPvpAllowed(true);
                h.assertTrue(victim.hurt(attacker.damageSources().playerAttack(attacker), 1), "PvP fixture rejected damage");
                Fixtures.near(h, victim.getHealth(), victim.getMaxHealth() - 1, "Reflection bounced back");
                Fixtures.near(h, attacker.getHealth(), attacker.getMaxHealth() - 2, "Reflection amount");
                h.assertTrue(attacker.getData(SpiritData.ENERGY_STATE).thornHits.isEmpty(), "Reflected hit re-entered Thorn");
            } finally { server.setPvpAllowed(pvp); }
        }));
        add(tests, "thorn_includes_exact_three_block_boundary", h -> damagePlayer(h, p -> {
            var mob = enemy(h, p.position().add(3, 0, 0));
            try { hit(h, p, mob); Fixtures.near(h, mob.getHealth(), 18, "Exact radius excluded"); }
            finally { mob.discard(); }
        }));

        add(tests, "ultra_bash_enemy_damage_and_player_launch", h -> {
            var p = player(h, Shard.ULTRA_BASH.bit);
            var mob = enemy(h, p.position().add(0, 0, 2));
            try {
                bash(p, true, 1); h.assertTrue(p.getData(SpiritData.MOVEMENT).bash != null, "No Bash aim");
                bash(p, false, 2);
                Fixtures.near(h, mob.getHealth(), 16, "Ultra Bash damage");
                Fixtures.near(h, p.getDeltaMovement().length(), 1.5, "Ultra launch speed");
                Fixtures.near(h, mob.getDeltaMovement().z, -.8, "Enemy recoil changed");
            } finally { SpiritMovement.cancel(p, p.getData(SpiritData.MOVEMENT)); mob.discard(); }
        });
        for (boolean flying : List.of(true, false)) add(tests, flying ? "ultra_bash_flying_projectile_speed" : "ultra_bash_lodged_arrow_stays_anchor", h -> {
            var p = player(h, Shard.ULTRA_BASH.bit);
            var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, h.getLevel());
            var owner = EntityType.SKELETON.create(h.getLevel());
            arrow.setOwner(owner); arrow.setPos(p.position().add(0, 1, 2));
            arrow.setDeltaMovement(0, 0, flying ? -.5 : 0); h.getLevel().addFreshEntity(arrow);
            try {
                bash(p, true, 1); h.assertTrue(p.getData(SpiritData.MOVEMENT).bash != null, "No arrow aim");
                bash(p, false, 2);
                Fixtures.near(h, arrow.getDeltaMovement().length(), flying ? 1.6 * 1.3 : .8, "Projectile speed");
                h.assertTrue(arrow.getOwner() == (flying ? p : owner), "Wrong projectile owner");
                Fixtures.near(h, p.getDeltaMovement().length(), 1.5, "Projectile anchor did not boost player");
            } finally { SpiritMovement.cancel(p, p.getData(SpiritData.MOVEMENT)); arrow.discard(); owner.discard(); }
        });
        add(tests, "ultra_bash_cancel_does_not_hurt", h -> {
            var p = player(h, Shard.ULTRA_BASH.bit); var mob = enemy(h, p.position().add(0, 0, 2));
            try { bash(p, true, 1); SpiritMovement.cancel(p, p.getData(SpiritData.MOVEMENT)); Fixtures.near(h, mob.getHealth(), 20, "Cancel dealt damage"); }
            finally { mob.discard(); }
        });
        add(tests, "ultra_bash_protects_players_allies_and_practice_targets", h -> {
            var p = player(h, Shard.ULTRA_BASH.bit); var other = Fixtures.player(h);
            var cow = EntityType.COW.create(h.getLevel());
            var practice = cn.wwl.orimod.world.OriEntities.PRACTICE_TARGET.get().create(h.getLevel());
            var ally = enemy(h, p.position().add(0, 0, 2));
            var scoreboard = h.getLevel().getScoreboard(); var team = scoreboard.addPlayerTeam("shard-" + UUID.randomUUID().toString().substring(0, 8));
            try {
                scoreboard.addPlayerToTeam(p.getScoreboardName(), team); scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
                practice.owner = p.getUUID();
                for (var target : List.of(other, cow, practice, ally)) {
                    h.assertFalse(ShardEffects.ultraBashTarget(p, target), "Protected target accepted");
                    float health = target.getHealth(); ShardEffects.bashHit(p, target);
                    Fixtures.near(h, target.getHealth(), health, "Protected target damaged");
                }
            } finally { scoreboard.removePlayerTeam(team); cow.discard(); practice.discard(); ally.discard(); }
        });
        for (String action : List.of("passive", "hold", "climb", "unequipped", "crouch", "jump", "away", "away_diagonal")) {
            add(tests, "sticky_wall_" + action, h -> {
                var p = player(h, action.equals("unequipped") ? 0 : Shard.STICKY.bit);
                for (int y = 2; y <= 7; y++) h.setBlock(new BlockPos(7, y, 5), Blocks.STONE);
                p.setPos(h.absoluteVec(new Vec3(7 - p.getBbWidth() / 2 - .02, 4, 5.5)));
                p.setOnGround(false); p.setDeltaMovement(.01, -.5, .02);
                var s = p.getData(SpiritData.MOVEMENT);
                s.dimension = p.level().dimension().location().toString(); s.previousPosition = p.position(); s.groundJumpAvailable = false;
                float forward = action.equals("climb") ? 1 : 0, left = action.equals("hold") || action.equals("unequipped") ? 1 : 0;
                float yaw = action.equals("climb") ? -90 : 0;
                int flags = MovementInputPayload.ACTIVE | (action.equals("crouch") ? MovementInputPayload.CROUCH : 0);
                SpiritMovement.accept(p, new MovementInputPayload(1, p.level().getGameTime(), flags, forward, left, yaw, 0));
                SpiritMovement.tick(new PlayerTickEvent.Post(p));
                if (action.equals("crouch")) {
                    h.assertTrue(s.mode != MoveMode.WALL_SLIDE, "Crouch retained wall");
                    Fixtures.near(h, p.getDeltaMovement().y, -.5, "Crouch clamped falling");
                } else {
                    h.assertValueEqual(s.mode, MoveMode.WALL_SLIDE, "No wall state");
                    double expected = action.equals("hold") ? 0 : action.equals("climb") ? .14 : action.equals("unequipped") ? -.12 : -.042;
                    Fixtures.near(h, p.getDeltaMovement().y, expected, "Shared wall speed");
                    if (action.equals("hold") || action.equals("climb")) {
                        SpiritMovement.tick(new PlayerTickEvent.Post(p));
                        h.assertValueEqual(s.mode, MoveMode.WALL_SLIDE, "Zero/positive velocity lost wall lease");
                        Fixtures.near(h, p.getDeltaMovement().y, expected, "Renewed wall speed");
                    }
                    if (action.equals("jump")) {
                        SpiritMovement.accept(p, new MovementInputPayload(2, p.level().getGameTime(), MovementInputPayload.ACTIVE | MovementInputPayload.JUMP, 0, 0, 0, 0));
                        h.assertValueEqual(s.mode, MoveMode.WALL_JUMP, "Passive Sticky cannot wall-jump");
                        h.assertTrue(p.getDeltaMovement().x < 0 && p.getDeltaMovement().y > 0, "Wall jump did not leave wall");
                        var jump = p.getDeltaMovement();
                        h.assertValueEqual(SpiritMovement.wallVelocity(p, new Vec3(1, 0, 0), 1, net.minecraft.core.Direction.EAST),
                                jump, "Stale client wall-slide lease replaced jump with Climb/Sticky");
                    }
                    if (action.startsWith("away")) {
                        // Keep the previous wall and its 0.15 preference: even a diagonal release must beat it.
                        p.setDeltaMovement(.01, -.5, .02);
                        boolean diagonal = action.equals("away_diagonal");
                        SpiritMovement.accept(p, new MovementInputPayload(2, p.level().getGameTime(), MovementInputPayload.ACTIVE,
                                diagonal ? 1 : 0, diagonal ? -.25f : -1, 0, 0));
                        SpiritMovement.tick(new PlayerTickEvent.Post(p));
                        h.assertValueEqual(s.mode, MoveMode.IDLE, "Input away from wall retained Sticky");
                        Fixtures.near(h, p.getDeltaMovement().y, -.5, "Released Sticky still slowed the fall");
                    }
                }
                Fixtures.near(h, SpiritProgression.energy(p), 10, "Sticky spent energy");
            });
        }
        return tests;
    }

    private static ServerPlayer player(GameTestHelper h, int shards) { var p = Fixtures.player(h); prepare(p, shards); return p; }
    private static void prepare(ServerPlayer p, int shards) {
        SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, (1 << 14) - 1);
        p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withOwned(2047).withEquipped(shards));
        p.setData(SpiritData.ENERGY, 10f); p.setHealth(10); p.setNoGravity(true);
        p.getData(SpiritData.ENERGY_STATE).flameReadyAt = Long.MAX_VALUE;
    }
    private static ItemEntity orb(ServerPlayer p, int count) {
        var item = new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), new ItemStack(OriItems.SPIRIT_LIGHT.get(), count));
        item.setNoPickUpDelay(); p.level().addFreshEntity(item); return item;
    }
    private static void pickup(ServerPlayer p, int count) {
        var item = orb(p, count);
        try { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(p, item)); }
        finally { item.discard(); }
    }
    private static net.minecraft.world.entity.monster.Zombie enemy(GameTestHelper h, Vec3 position) {
        var mob = EntityType.ZOMBIE.create(h.getLevel()); mob.setNoAi(true); mob.setPos(position);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(0); h.getLevel().addFreshEntity(mob); return mob;
    }
    private static void damagePlayer(GameTestHelper h, Consumer<ServerPlayer> run) {
        var server = h.getLevel().getServer(); var difficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.NORMAL, true);
        try (var c = Fixtures.connectedPlayer(h)) {
            var p = c.player(); for (int i = 0; i < 61; i++) p.tick();
            prepare(p, Shard.THORN.bit); p.invulnerableTime = 0; p.setHealth(p.getMaxHealth());
            run.accept(p);
        } finally { server.setDifficulty(difficulty, true); }
    }
    private static void hit(GameTestHelper h, ServerPlayer p, LivingEntity enemy) {
        p.invulnerableTime = 0;
        h.assertTrue(p.hurt(p.damageSources().mobAttack(enemy), 1), "Melee damage rejected by fixture");
    }
    private static void bash(ServerPlayer p, boolean down, int sequence) {
        SpiritMovement.accept(p, new MovementInputPayload(sequence, p.level().getGameTime(), MovementInputPayload.ACTIVE | (down ? MovementInputPayload.BASH : 0), 0, 0, 0, 0));
    }
    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        timed(tests, name, h -> { run.accept(h); h.succeed(); });
    }
    private static void timed(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("shards", "shards." + name, "orimod_gametest:room", 100, 0, true, run));
    }
}
