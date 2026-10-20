package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.block.SpiritLanternBlock;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.*;
import java.util.function.BiConsumer;

/** R98: authoritative Flash leases, real entities and spawn events; original Charge Flame cases remain untouched. */
@GameTestHolder("orimod_gametest")
public final class FlashGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "purchase_requires_charge_flame_and_costs_40", (h, c) -> {
            var p = c.player();
            p.setData(SpiritData.ABILITIES, 0);
            p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY.withLight(40));
            SpiritProgression.request(p, "ability", "flash");
            h.assertFalse(Ability.FLASH.unlocked(p), "Bought without prerequisite");
            p.setData(SpiritData.ABILITIES, Ability.CHARGE_FLAME.bit);
            SpiritProgression.request(p, "ability", "flash");
            h.assertTrue(Ability.FLASH.unlocked(p), "Purchase failed");
            h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 0, "Purchase price");
        });
        add(tests, "human_locked_busy_and_low_energy_refused", (h, c) -> {
            var p = c.player();
            p.setData(SpiritData.FORM, false);
            h.assertFalse(Flash.keepAlive(p), "Human accepted");
            p.setData(SpiritData.FORM, true); p.setData(SpiritData.ABILITIES, 0);
            h.assertFalse(Flash.keepAlive(p), "Locked accepted");
            p.setData(SpiritData.ABILITIES, Ability.FLASH.bit); p.setData(SpiritData.ENERGY, 4.99f);
            h.assertFalse(Flash.keepAlive(p), "Started below five");
            p.setData(SpiritData.ENERGY, 100f);
            for (var mode : List.of(MoveMode.DASH, MoveMode.BASH_AIM, MoveMode.GRAPPLE, MoveMode.STOMP)) {
                p.getData(SpiritData.MOVEMENT).mode = mode;
                h.assertFalse(Flash.keepAlive(p), "Exclusive movement " + mode);
            }
        });
        add(tests, "spitter_dazzle_cancels_charge_and_damage_is_paced", (h, c) -> {
            var p = c.player();
            var mob = h.spawn(OriMobs.GLOOM_SPITTER.get(), new Vec3(7.5, 2, 5.5)); mob.setNoAi(true);
            var far = h.spawn(OriMobs.GLOOM_SPITTER.get(), new Vec3(13.5, 2, 5.5)); far.setNoAi(true);
            try {
                mob.performRangedAttack(p, 1);
                h.assertTrue(mob.charge() > 0, "Spitter never started charge");
                Flash.keepAlive(p); age(p, 9);
                h.assertTrue(mob.dazzled(), "Spitter not dazzled");
                h.assertValueEqual(mob.charge(), 0, "Telegraph survived");
                mob.performRangedAttack(p, 1);
                h.assertValueEqual(mob.charge(), 0, "New charge while dazzled");
                h.assertTrue(mob.tickCharge() == null, "Shot during dazzle");
                Fixtures.near(h, mob.getHealth(), mob.getMaxHealth(), "Damage before tenth tick");
                age(p, 10);
                Fixtures.near(h, mob.getHealth(), mob.getMaxHealth() - 1, "Tenth tick damage");
                h.assertFalse(far.dazzled(), "Dazzled outside sphere");
                Fixtures.near(h, far.getHealth(), far.getMaxHealth(), "Damaged outside sphere");
                Flash.stop(p);
                for (int i = 0; i < Flash.DAZZLE_TICKS; i++) mob.tick();
                h.assertFalse(mob.dazzled(), "Spitter dazzle did not expire");
                mob.performRangedAttack(p, 1);
                h.assertTrue(mob.charge() > 0, "Spitter cannot resume charging");
            } finally { mob.discard(); far.discard(); }
        });
        add(tests, "hawk_dive_stops_and_retreats_from_light", (h, c) -> {
            var p = c.player();
            var hawk = h.spawn(OriMobs.GLOOM_HAWK.get(), new Vec3(7.5, 3, 5.5)); hawk.setNoAi(true);
            try {
                hawk.telegraph(p);
                for (int i = 0; i < GloomHawk.TELEGRAPH_TICKS; i++) hawk.step();
                h.assertValueEqual(hawk.state(), GloomHawk.DIVE, "Dive fixture");
                Flash.keepAlive(p); age(p, 10);
                h.assertTrue(hawk.dazzled(), "Hawk not dazzled");
                h.assertValueEqual(hawk.state(), GloomHawk.CIRCLE, "Dive survived dazzle");
                h.assertTrue(hawk.mark() == null, "Dive mark survived dazzle");
                h.assertTrue(hawk.getDeltaMovement().dot(hawk.position().subtract(p.position())) > 0, "Retreat points toward player");
                h.assertFalse(hawk.strike(p), "Dazzled hawk can strike");
                Fixtures.near(h, hawk.getHealth(), hawk.getMaxHealth() - 1, "Hawk damage");
                for (int i = 0; i < Flash.DAZZLE_TICKS; i++) hawk.step();
                hawk.telegraph(p);
                h.assertValueEqual(hawk.state(), GloomHawk.TELEGRAPH, "Hawk cannot resume after dazzle");
            } finally { hawk.discard(); }
        });
        add(tests, "bosses_take_damage_without_losing_attack", (h, c) -> {
            var p = c.player();
            var heart = h.spawn(OriMobs.GLOOM_HEART.get(), new Vec3(7.5, 2, 5.5)); heart.setNoAi(true);
            var matriarch = h.spawn(OriMobs.GLOOM_MATRIARCH.get(), new Vec3(5.5, 3, 7.5)); matriarch.setNoAi(true);
            try {
                heart.startAttack(GloomHeart.RING, p); matriarch.startAttack(GloomMatriarch.FEATHERS, p);
                Flash.keepAlive(p); age(p, 10);
                Fixtures.near(h, heart.getHealth(), heart.getMaxHealth(), "Boss damaged early");
                age(p, 20);
                Fixtures.near(h, heart.getHealth(), heart.getMaxHealth() - 1, "Heart damage");
                Fixtures.near(h, matriarch.getHealth(), matriarch.getMaxHealth() - 1, "Matriarch damage");
                h.assertValueEqual(heart.attack(), GloomHeart.RING, "Heart attack interrupted");
                h.assertValueEqual(matriarch.attack(), GloomMatriarch.FEATHERS, "Matriarch attack interrupted");
            } finally { heart.discard(); matriarch.discard(); }
        });
        add(tests, "undead_weakened_passive_and_other_monsters_untouched", (h, c) -> {
            var zombie = h.spawn(EntityType.ZOMBIE, new Vec3(7.5, 2, 5.5)); zombie.setNoAi(true);
            var cow = h.spawn(EntityType.COW, new Vec3(5.5, 2, 7.5)); cow.setNoAi(true);
            var creeper = h.spawn(EntityType.CREEPER, new Vec3(3.5, 2, 5.5)); creeper.setNoAi(true);
            try {
                Flash.keepAlive(c.player()); age(c.player(), 20);
                h.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS), "Undead not weakened");
                h.assertValueEqual(zombie.getEffect(MobEffects.WEAKNESS).getDuration(), 40, "Weakness duration");
                h.assertValueEqual(zombie.getEffect(MobEffects.WEAKNESS).getAmplifier(), 0, "Weakness strength");
                Fixtures.near(h, zombie.getHealth(), zombie.getMaxHealth() - .5, "Undead damage");
                Fixtures.near(h, cow.getHealth(), cow.getMaxHealth(), "Passive damaged");
                Fixtures.near(h, creeper.getHealth(), creeper.getMaxHealth(), "Other monster damaged");
            } finally { zombie.discard(); cow.discard(); creeper.discard(); }
        });
        add(tests, "lantern_scan_every_ten_ticks_and_only_in_sphere", (h, c) -> {
            var p = c.player();
            var near = new BlockPos(7, 2, 5); var far = new BlockPos(13, 2, 5); var later = new BlockPos(5, 2, 7);
            h.setBlock(near, OriBlocks.BASH_ANCHOR.get()); h.setBlock(far, OriBlocks.BASH_ANCHOR.get());
            Flash.keepAlive(p); age(p, 1);
            h.assertTrue(h.getBlockState(near).getValue(SpiritLanternBlock.LIT), "Near lamp dark");
            h.assertFalse(h.getBlockState(far).getValue(SpiritLanternBlock.LIT), "Far lamp lit");
            h.setBlock(later, OriBlocks.BASH_ANCHOR.get());
            age(p, 2); age(p, 10);
            h.assertFalse(h.getBlockState(later).getValue(SpiritLanternBlock.LIT), "Scanned lamps before tenth interval");
            age(p, 11);
            h.assertTrue(h.getBlockState(later).getValue(SpiritLanternBlock.LIT), "Missed next lamp scan");
        });
        add(tests, "water_first_at_40_then_every_20_with_radius_three", (h, c) -> {
            var p = c.player(); var near = new BlockPos(7, 2, 5); var far = new BlockPos(9, 2, 5);
            h.setBlock(near, OriBlocks.GLOOM_WATER.get()); h.setBlock(far, OriBlocks.GLOOM_WATER.get());
            Flash.keepAlive(p); age(p, 39);
            h.assertTrue(h.getBlockState(near).is(OriBlocks.GLOOM_WATER), "Cleanse before tick 40");
            age(p, 40);
            h.assertTrue(h.getBlockState(near).is(Blocks.WATER), "First cleanse missing");
            h.assertTrue(h.getBlockState(far).is(OriBlocks.GLOOM_WATER), "Cleanse outside radius");
            h.setBlock(near, OriBlocks.GLOOM_WATER.get()); age(p, 41); age(p, 59);
            h.assertTrue(h.getBlockState(near).is(OriBlocks.GLOOM_WATER), "Repeated cleanse too soon");
            age(p, 60);
            h.assertTrue(h.getBlockState(near).is(Blocks.WATER), "Second cleanse missing");
        });
        add(tests, "natural_spawn_event_filters_reason_distance_and_dimension", (h, c) -> {
            var p = c.player(); Flash.keepAlive(p);
            var zombie = EntityType.ZOMBIE.create(h.getLevel());
            try {
                zombie.setPos(p.position().add(16, 0, 0));
                for (var reason : MobSpawnType.values()) {
                    var event = new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), reason, null);
                    NeoForge.EVENT_BUS.post(event);
                    h.assertValueEqual(event.getResult(), reason == MobSpawnType.NATURAL ? MobSpawnEvent.PositionCheck.Result.FAIL : MobSpawnEvent.PositionCheck.Result.DEFAULT, "Spawn reason " + reason);
                }
                zombie.setPos(p.position().add(16.01, 0, 0));
                var far = new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), MobSpawnType.NATURAL, null);
                Flash.spawn(far);
                h.assertValueEqual(far.getResult(), MobSpawnEvent.PositionCheck.Result.DEFAULT, "Outside sphere blocked");
                zombie.setPos(p.position());
                var nether = h.getLevel().getServer().getLevel(Level.NETHER);
                var other = new MobSpawnEvent.PositionCheck(zombie, nether, MobSpawnType.NATURAL, null);
                Flash.spawn(other);
                h.assertValueEqual(other.getResult(), MobSpawnEvent.PositionCheck.Result.DEFAULT, "Other dimension blocked");
                p.getData(SpiritData.ENERGY_STATE).flashUntil = p.level().getGameTime();
                var expired = new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), MobSpawnType.NATURAL, null);
                Flash.spawn(expired);
                h.assertValueEqual(expired.getResult(), MobSpawnEvent.PositionCheck.Result.DEFAULT, "Expired Flash blocked spawn");
            } finally { zombie.discard(); }
        });
        add(tests, "fx_start_heartbeat_stop_and_no_duplicate_drain", (h, c) -> {
            var p = c.player(); FxGameTests.drain(c.channel());
            SpiritProgression.request(p, "flash", "");
            var start = FxGameTests.fx(FxGameTests.drain(c.channel()));
            h.assertTrue(start.stream().anyMatch(v -> v.kind() == SpiritFxKind.FLASH && v.phase() == SpiritFxPhase.START), "Missing Flash START");
            SpiritFxLifecycle.sync(p, SpiritFxTracker.HEARTBEAT_TICKS);
            h.assertTrue(FxGameTests.fx(FxGameTests.drain(c.channel())).stream().anyMatch(v -> v.kind() == SpiritFxKind.FLASH && v.phase() == SpiritFxPhase.START), "Missing heartbeat");
            tick(p); tick(p);
            Fixtures.near(h, SpiritProgression.energy(p), 99.7, "Duplicate event consumed twice");
            Flash.stop(p);
            h.assertTrue(FxGameTests.fx(FxGameTests.drain(c.channel())).stream().anyMatch(v -> v.kind() == SpiritFxKind.FLASH && v.phase() == SpiritFxPhase.STOP), "Missing STOP");
        });
        for (String action : List.of("form", "logout", "death", "dimension", "inactive_input", "movement", "menu")) {
            add(tests, "interrupt_" + action, (h, c) -> {
                var p = c.player(); h.assertTrue(Flash.keepAlive(p), "Flash did not start");
                switch (action) {
                    case "form" -> SpiritForm.toggle(p);
                    case "logout" -> SpiritFxLifecycle.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
                    case "death" -> SpiritFxLifecycle.death(new LivingDeathEvent(p, p.damageSources().generic()));
                    case "dimension" -> SpiritFxLifecycle.dimension(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
                    case "inactive_input" -> SpiritMovement.accept(p, new MovementInputPayload(1, p.level().getGameTime(), 0, 0, 0, 0, 0));
                    case "movement" -> { p.getData(SpiritData.MOVEMENT).mode = MoveMode.DASH; tick(p); }
                    case "menu" -> NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(p, p.inventoryMenu));
                }
                h.assertFalse(Flash.active(p), "Flash survived " + action);
                h.assertFalse(SpiritFxLifecycle.active(p).contains(SpiritFxKind.FLASH), "FX survived " + action);
            });
        }
        for (boolean external : List.of(true, false)) {
            add(tests, external ? "interrupt_damage" : "environmental_damage_keeps_flash", (h, c) -> {
                var p = c.player(); var server = h.getLevel().getServer();
                var difficulty = server.getWorldData().getDifficulty();
                var zombie = EntityType.ZOMBIE.create(h.getLevel());
                try {
                    server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                    // Same fixture as DamageGameTests: let the separate sixty-tick login immunity expire.
                    for (int i = 0; i < 61; i++) p.tick();
                    p.invulnerableTime = 0; p.setHealth(p.getMaxHealth());
                    h.assertTrue(Flash.keepAlive(p), "Flash did not start");
                    float health = p.getHealth();
                    h.assertTrue(p.hurt(external ? p.damageSources().mobAttack(zombie) : p.damageSources().cactus(), 1), "Damage rejected by fixture");
                    h.assertTrue(p.getHealth() < health, "Damage did not reduce health");
                    h.assertTrue(Flash.active(p) != external, "Flash damage interruption rule");
                    h.assertTrue(SpiritFxLifecycle.active(p).contains(SpiritFxKind.FLASH) != external, "FX damage interruption rule");
                } finally { zombie.discard(); server.setDifficulty(difficulty, true); }
            });
        }
        for (String weapon : List.of("blade", "bow", "hammer", "spear")) {
            add(tests, "weapon_" + weapon + "_ends_flash", (h, c) -> {
                var p = c.player(); p.setData(SpiritData.ABILITIES, Arrays.stream(Ability.values()).mapToInt(a -> a.bit).reduce(0, (a, b) -> a | b));
                var item = switch (weapon) { case "blade" -> OriItems.SPIRIT_BLADE.get(); case "bow" -> OriItems.SPIRIT_BOW.get(); case "hammer" -> OriItems.SPIRIT_HAMMER.get(); default -> OriItems.SPIRIT_SPEAR.get(); };
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                Flash.keepAlive(p);
                if (weapon.equals("blade")) SpiritCombat.attack(p); else item.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
                h.assertFalse(Flash.active(p), "Weapon did not stop Flash");
                p.stopUsingItem(); SpiritCombat.cancel(p, "test");
            });
        }
        add(tests, "charge_lease_excludes_flash_but_expiry_never_refuses_release", (h, c) -> {
            var p = c.player(); p.setData(SpiritData.ABILITIES, Ability.FLASH.bit | Ability.CHARGE_FLAME.bit);
            Flash.keepAlive(p);
            SpiritProgression.request(p, "charge_flame_hold", "");
            h.assertFalse(Flash.active(p), "Charging did not stop Flash");
            h.assertFalse(Flash.keepAlive(p), "Flash during charge");
            p.getData(SpiritData.ENERGY_STATE).chargeFlameUntil = p.level().getGameTime() - 1;
            h.assertTrue(ChargeFlame.release(p) >= 0, "Expired keepalive rejected valid Charge Flame release");
            Fixtures.near(h, SpiritProgression.energy(p), 90, "Charge Flame cost changed");
            p.getData(SpiritData.ENERGY_STATE).chargeFlameReadyAt = 0;
            p.getData(SpiritData.ENERGY_STATE).chargeFlameUntil = Long.MIN_VALUE;
            h.assertTrue(ChargeFlame.release(p) >= 0, "Absent keepalive rejected valid Charge Flame release");
            Fixtures.near(h, SpiritProgression.energy(p), 80, "Legacy release changed");
        });
        add(tests, "world_unload_releases_only_its_active_players", (h, c) -> {
            var nether = h.getLevel().getServer().getLevel(Level.NETHER);
            var other = new net.neoforged.neoforge.common.util.FakePlayer(nether, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "flash-test"));
            other.setData(SpiritData.AWAKENED, true); other.setData(SpiritData.FORM, true);
            other.setData(SpiritData.ABILITIES, Ability.FLASH.bit); other.setData(SpiritData.ENERGY, 100f);
            try {
                Flash.keepAlive(c.player()); h.assertTrue(Flash.keepAlive(other), "Other world fixture");
                Flash.unload(new LevelEvent.Unload(nether));
                h.assertFalse(Flash.active(other), "Unloaded world retained Flash");
                h.assertTrue(Flash.active(c.player()), "Unloading another world stopped this Flash");
            } finally { Flash.stop(other); }
        });
        for (boolean renew : List.of(true, false)) {
            add(tests, "other_energy_spend_exhausts_on_" + (renew ? "renew" : "tick"), (h, c) -> {
                var p = c.player(); h.assertTrue(Flash.keepAlive(p), "Flash did not start");
                SpiritProgression.spendEnergy(p, SpiritProgression.energy(p));
                if (renew) Flash.keepAlive(p); else tick(p);
                h.assertFalse(Flash.active(p), "Zero-energy Flash survived");
                h.assertValueEqual(p.getData(SpiritData.ENERGY_STATE).flashReadyAt, p.level().getGameTime() + 20, "Missing exhaustion cooldown");
            });
        }
        timed(tests, "lease_expires_at_eight_without_release_packet", (h, c) -> {
            var p = c.player(); Flash.keepAlive(p);
            h.runAfterDelay(7, safe(c, () -> h.assertTrue(Flash.active(p), "Lease expired before eight")));
            h.runAfterDelay(8, safe(c, () -> {
                tick(p); h.assertFalse(Flash.active(p), "Lease survived eight");
                h.assertFalse(SpiritFxLifecycle.active(p).contains(SpiritFxKind.FLASH), "Expired loop still active");
                finish(h, c);
            }));
        });
        timed(tests, "fifty_energy_drains_without_regen_then_twenty_tick_cooldown", (h, c) -> {
            var p = c.player(); p.setData(SpiritData.ENERGY, 50f); Flash.keepAlive(p);
            for (int i = 1; i <= 167; i++) {
                final int tick = i;
                h.runAfterDelay(i, safe(c, () -> {
                    if (tick % Flash.KEEPALIVE_TICKS == 0) Flash.keepAlive(p);
                    SpiritProgression.tick(new PlayerTickEvent.Post(p));
                    tick(p);
                    if (tick == 166) h.assertTrue(Flash.active(p) && SpiritProgression.energy(p) < .21f, "Stopped before last energy");
                }));
            }
            h.runAfterDelay(168, safe(c, () -> {
                Fixtures.near(h, SpiritProgression.energy(p), 0, "Energy did not reach zero");
                h.assertFalse(Flash.active(p), "Exhausted Flash still active");
                h.assertFalse(Flash.keepAlive(p), "Exhausted Flash renewed");
                p.setData(SpiritData.ENERGY, 100f);
            }));
            h.runAfterDelay(186, safe(c, () -> h.assertFalse(Flash.keepAlive(p), "Cooldown shorter than twenty ticks")));
            h.runAfterDelay(187, safe(c, () -> { h.assertTrue(Flash.keepAlive(p), "Cooldown did not end"); finish(h, c); }));
        });
        return tests;
    }

    private static void tick(ServerPlayer player) { Flash.tick(new PlayerTickEvent.Post(player)); }
    /** Exercise cadence without changing the shared world's clock or waiting between independent condition checks. */
    private static void age(ServerPlayer player, long age) {
        var s = player.getData(SpiritData.ENERGY_STATE);
        s.flashActiveSince = player.level().getGameTime() - age + 1;
        s.flashLastTick = Long.MIN_VALUE;
        tick(player);
    }
    private static Runnable safe(Fixtures.ConnectedPlayer c, Runnable run) {
        return () -> { try { run.run(); } catch (RuntimeException | Error e) { Flash.stop(c.player()); c.close(); throw e; } };
    }
    private static void finish(GameTestHelper h, Fixtures.ConnectedPlayer c) {
        Flash.stop(c.player()); c.close(); h.succeed();
    }
    private static void add(Collection<TestFunction> tests, String name, BiConsumer<GameTestHelper, Fixtures.ConnectedPlayer> run) {
        timed(tests, name, (h, c) -> { try { run.accept(h, c); h.succeed(); } finally { Flash.stop(c.player()); c.close(); } });
    }
    private static void timed(Collection<TestFunction> tests, String name, BiConsumer<GameTestHelper, Fixtures.ConnectedPlayer> run) {
        tests.add(new TestFunction("flash", "flash." + name, "orimod_gametest:room", 220, 0, true, h -> {
            var c = Fixtures.connectedPlayer(h);
            try {
                var p = c.player(); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, Ability.FLASH.bit);
                p.setData(SpiritData.ENERGY, 100f); p.setNoGravity(true);
                p.getData(SpiritData.ENERGY_STATE).flameReadyAt = Long.MAX_VALUE;
                run.accept(h, c);
            } catch (RuntimeException | Error e) { Flash.stop(c.player()); c.close(); throw e; }
        }));
    }
}
