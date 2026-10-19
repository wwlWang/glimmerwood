package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementRules;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/** R38+: original-game parity mechanics (Launch, ...). */
@GameTestHolder("orimod_gametest")
public final class ParityGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "launch_needs_ability_and_charge", h -> {
            var p = spirit(h, 63); p.setOnGround(true);
            var s = p.getData(SpiritData.MOVEMENT); s.launchCharge = MovementRules.LAUNCH_CHARGE_TICKS;
            try {
                crouchJump(p, 1, true); crouchJump(p, 2, false);
                h.assertValueEqual(s.acceptedLaunches, 0, "Launch without the ability");
                // R88: without a ready Launch, a sneaking press is an ordinary jump (was swallowed).
                h.assertValueEqual(s.acceptedJumps, 1, "Sneak jump without Launch");
                regrounded(p, s);
                p.setData(SpiritData.ABILITIES, 127); s.launchCharge = 3;
                crouchJump(p, 3, true); crouchJump(p, 4, false);
                h.assertValueEqual(s.acceptedLaunches, 0, "Uncharged ground Launch");
                h.assertValueEqual(s.acceptedJumps, 2, "Sneak jump before Launch is charged");
                regrounded(p, s);
                s.launchCharge = MovementRules.LAUNCH_CHARGE_TICKS;
                crouchJump(p, 5, true);
                h.assertValueEqual(s.acceptedLaunches, 1, "Charged ground Launch");
                h.assertTrue(s.mode == MoveMode.LAUNCH, "Mode after Launch: " + s.mode);
                h.assertTrue(p.getDeltaMovement().y >= MovementRules.LAUNCH_MIN_UP * MovementRules.LAUNCH_GROUND_SPEED - 1e-6, "Launch did not rise");
                Fixtures.near(h, p.getDeltaMovement().length(), MovementRules.LAUNCH_GROUND_SPEED, "Ground Launch speed");
            } finally { SpiritMovement.cancel(p, s); }
        });
        add(tests, "air_launch_once_and_refreshes_air_moves", h -> {
            var p = spirit(h, 127); p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false);
            var s = p.getData(SpiritData.MOVEMENT); s.airLaunchSpent = false; s.airJumpSpent = s.airDashSpent = true;
            try {
                crouchJump(p, 1, true);
                h.assertValueEqual(s.acceptedLaunches, 1, "Air Launch");
                h.assertFalse(s.airJumpSpent || s.airDashSpent, "Launch did not refresh air moves");
                Fixtures.near(h, p.getDeltaMovement().length(), MovementRules.LAUNCH_AIR_SPEED, "Air Launch speed");
                crouchJump(p, 2, false); crouchJump(p, 3, true);
                h.assertValueEqual(s.acceptedLaunches, 1, "Second air Launch in one airtime");
            } finally { SpiritMovement.cancel(p, s); }
        });
        add(tests, "light_burst_throw_rules_and_blast", h -> {
            var p = spirit(h, 63); p.setData(SpiritData.ENERGY, 100f);
            h.assertTrue(cn.wwl.orimod.combat.LightBurst.toss(p) == null, "Light Burst thrown while locked");
            p.setData(SpiritData.ABILITIES, 255);
            var burst = cn.wwl.orimod.combat.LightBurst.toss(p);
            h.assertTrue(burst != null, "Unlocked Light Burst refused");
            Fixtures.near(h, p.getData(SpiritData.ENERGY), 100 - cn.wwl.orimod.combat.LightBurst.COST, "Energy after Light Burst");
            h.assertTrue(cn.wwl.orimod.combat.LightBurst.toss(p) == null, "Light Burst ignored the cooldown");
            h.assertTrue(cn.wwl.orimod.movement.SpiritTargets.eligible(p, burst), "Own Light Burst is not a Bash anchor");
            var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(5.5, 2, 7.5)); zombie.setNoAi(true);
            var pig = h.spawn(net.minecraft.world.entity.EntityType.PIG, new Vec3(6.5, 2, 7.5)); pig.setNoAi(true);
            try {
                burst.setPos(h.absoluteVec(new Vec3(5.5, 2.5, 7)));
                h.assertValueEqual(burst.detonate(), 1, "Blast should hit exactly the zombie");
                Fixtures.near(h, zombie.getMaxHealth() - zombie.getHealth(), cn.wwl.orimod.combat.LightBurst.DAMAGE, "Blast damage");
                Fixtures.near(h, pig.getMaxHealth() - pig.getHealth(), 0, "Blast hurt a passive mob");
                h.assertTrue(burst.isRemoved(), "Burst survived its blast");
                h.assertValueEqual(burst.detonate(), 0, "Burst exploded twice");
            } finally { zombie.discard(); pig.discard(); if (!burst.isRemoved()) burst.discard(); }
        });
        add(tests, "updraft_lifts_only_with_open_column", h -> {
            var p = spirit(h, 127);
            h.setBlock(new net.minecraft.core.BlockPos(5, 1, 5), cn.wwl.orimod.block.OriBlocks.UPDRAFT.get());
            p.setPos(h.absoluteVec(new Vec3(5.5, 6, 5.5)));
            h.assertTrue(cn.wwl.orimod.block.UpdraftBlock.lifting(h.getLevel(), p), "Open column above a vent does not lift");
            h.setBlock(new net.minecraft.core.BlockPos(5, 3, 5), net.minecraft.world.level.block.Blocks.STONE);
            h.assertFalse(cn.wwl.orimod.block.UpdraftBlock.lifting(h.getLevel(), p), "Lift passes through a solid block");
            h.setBlock(new net.minecraft.core.BlockPos(5, 3, 5), net.minecraft.world.level.block.Blocks.AIR);
            p.setPos(h.absoluteVec(new Vec3(7.5, 6, 5.5)));
            h.assertFalse(cn.wwl.orimod.block.UpdraftBlock.lifting(h.getLevel(), p), "Lift beside the vent");
        });
        add(tests, "triple_jump_shard_adds_one_air_jump", h -> {
            var p = spirit(h, 127); p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false);
            var s = p.getData(SpiritData.MOVEMENT); s.airJumpSpent = false; s.groundJumpAvailable = false;
            try {
                jump(p, 1, true); jump(p, 2, false);
                jump(p, 3, true); jump(p, 4, false);
                h.assertValueEqual(s.acceptedJumps, 1, "Without the shard only one air jump");
                var progress = p.getData(SpiritData.PROGRESS);
                p.setData(SpiritData.PROGRESS, progress.withOwned(progress.ownedShards() | cn.wwl.orimod.progression.Shard.TRIPLE_JUMP.bit)
                        .withEquipped(cn.wwl.orimod.progression.Shard.TRIPLE_JUMP.bit));
                s.airJumpSpent = false; s.wallReadyAt = Long.MAX_VALUE;
                for (int i = 0; i < 3; i++) { jump(p, 10 + 2 * i, true); jump(p, 11 + 2 * i, false); }
                h.assertValueEqual(s.acceptedJumps, 3, "With the shard two more air jumps (double + triple)");
            } finally { SpiritMovement.cancel(p, s); }
        });
        add(tests, "spirit_well_refills_and_travels", h -> {
            var p = spirit(h, 127);
            var a = new net.minecraft.core.BlockPos(5, 2, 3); var b = new net.minecraft.core.BlockPos(5, 2, 8);
            h.setBlock(a, cn.wwl.orimod.block.OriBlocks.FOREST_SHRINE.get()); h.setBlock(b, cn.wwl.orimod.block.OriBlocks.FOREST_SHRINE.get());
            var absA = h.absolutePos(a); var absB = h.absolutePos(b);
            p.setPos(h.absoluteVec(new Vec3(5.5, 2, 5.5)));
            p.hurt(p.damageSources().generic(), 6); p.setData(SpiritData.ENERGY, 10f);
            h.assertTrue(cn.wwl.orimod.world.SpiritWell.refill(p), "Well did not refill");
            Fixtures.near(h, p.getHealth(), p.getMaxHealth(), "Health after well");
            h.assertFalse(cn.wwl.orimod.world.SpiritWell.refill(p), "Well refilled again during cooldown");
            var progress = p.getData(SpiritData.PROGRESS); var records = new java.util.HashMap<>(progress.rewards());
            String dim = h.getLevel().dimension().location().toString();
            records.put("discover/" + dim + "/" + absA.getX() + "," + absA.getY() + "," + absA.getZ(), 1);
            h.assertFalse(cn.wwl.orimod.world.SpiritWell.travel(p, absA), "Travel with a single discovered well");
            records.put("discover/" + dim + "/" + absB.getX() + "," + absB.getY() + "," + absB.getZ(), 1);
            p.setData(SpiritData.PROGRESS, progress.withRewards(records));
            var stand = cn.wwl.orimod.world.SpiritWell.destination(h.getLevel(), absA, records.keySet());
            h.assertTrue(stand != null && stand.closerThan(absB, 1.5), "Destination is not the second well: " + stand);
            h.assertTrue(cn.wwl.orimod.world.SpiritWell.travel(p, absA), "Travel to the second well failed");
            h.setBlock(b, net.minecraft.world.level.block.Blocks.AIR);
            h.assertTrue(cn.wwl.orimod.world.SpiritWell.destination(h.getLevel(), absA, records.keySet()) == null, "Travel to a destroyed well");
        });
        add(tests, "gloom_spitter_spits_bashable_orbs", h -> {
            var p = spirit(h, 127);
            var spitter = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_SPITTER.get(), new Vec3(5.5, 2, 9.5)); spitter.setNoAi(true);
            try {
                h.assertTrue(spitter instanceof net.minecraft.world.entity.monster.Enemy, "Spitter must be an enemy (light drops, flame, Bash)");
                var shot = spitter.spit(p);
                h.assertTrue(shot.getOwner() == spitter, "Shot owner");
                h.assertTrue(shot.getDeltaMovement().z < 0, "Shot not aimed at the player");
                h.assertTrue(cn.wwl.orimod.movement.SpiritTargets.eligible(p, shot), "Gloom orb is not Bashable");
                // FakePlayer ignores damage; a villager stands in for the victim of an un-redirected orb.
                var victim = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new Vec3(3.5, 2, 5.5)); victim.setNoAi(true);
                float before = victim.getHealth();
                h.assertTrue(shot.strike(victim), "Gloom orb did not hurt its target");
                h.assertTrue(victim.getHealth() < before && victim.getLastHurtByMob() == spitter, "Orb hit not credited to the spitter");
                victim.discard();
                shot.setOwner(p);   // as after a Bash redirect
                float spitterBefore = spitter.getHealth();
                h.assertTrue(shot.strike(spitter), "Redirected orb did not hurt the spitter");
                Fixtures.near(h, spitterBefore - spitter.getHealth(), cn.wwl.orimod.entity.GloomShot.DAMAGE, "Redirected orb damage");
                h.assertTrue(spitter.getLastHurtByMob() == p, "Redirected hit not credited to the player");
                shot.discard();
            } finally { spitter.discard(); }
        });
        add(tests, "gloom_spitter_telegraphs_before_spitting", h -> {
            var p = spirit(h, 127);
            var spitter = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_SPITTER.get(), new Vec3(5.5, 2, 9.5)); spitter.setNoAi(true);
            try {
                spitter.performRangedAttack(p, 1);
                h.assertValueEqual(spitter.charge(), cn.wwl.orimod.entity.GloomSpitter.CHARGE_TICKS, "Charge after attack");
                spitter.performRangedAttack(p, 1);
                h.assertValueEqual(spitter.charge(), cn.wwl.orimod.entity.GloomSpitter.CHARGE_TICKS, "A second attack restarted the charge");
                cn.wwl.orimod.entity.GloomShot shot = null;
                for (int i = 1; i < cn.wwl.orimod.entity.GloomSpitter.CHARGE_TICKS; i++) h.assertTrue(spitter.tickCharge() == null, "Orb left early at tick " + i);
                shot = spitter.tickCharge();
                h.assertTrue(shot != null && shot.getOwner() == spitter, "Orb did not leave after the telegraph");
                h.assertValueEqual(spitter.charge(), 0, "Charge after spit");
                shot.discard();
            } finally { spitter.discard(); }
        });
        add(tests, "spirit_smash_hits_ahead_and_downs_flyers", h -> {
            var p = spirit(h, 127); p.setYRot(0);
            var ground = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(5.5, 2, 7.7)); ground.setNoAi(true); ground.setOnGround(true);
            var flyer = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(6.3, 3.5, 7.7)); flyer.setNoAi(true); flyer.setNoGravity(true); flyer.setOnGround(false);
            var behind = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(5.5, 2, 2.5)); behind.setNoAi(true);
            try {
                float g = ground.getHealth(), f = flyer.getHealth(), b = behind.getHealth();
                int hits = cn.wwl.orimod.item.SpiritHammerItem.smash(p);
                h.assertValueEqual(hits, 2, "Smash hits");
                h.assertTrue(g - ground.getHealth() > 8, "Ground target damage " + (g - ground.getHealth()));
                h.assertTrue(f - flyer.getHealth() > 8 && flyer.getDeltaMovement().y < -1, "Flyer not slammed down: " + flyer.getDeltaMovement());
                h.assertTrue(ground.getDeltaMovement().y > 0, "Ground target not bounced");
                h.assertTrue(behind.getHealth() == b, "Smash hit behind the player");
            } finally { ground.discard(); flyer.discard(); behind.discard(); }
        });
        add(tests, "gloom_heart_altar_summons_one_boss", h -> {
            var p = spirit(h, 127);
            var altar = h.absolutePos(new net.minecraft.core.BlockPos(5, 1, 9));
            h.getLevel().setBlock(altar, cn.wwl.orimod.block.OriBlocks.GLOOM_ALTAR.get().defaultBlockState(), 3);
            var heart = cn.wwl.orimod.block.GloomAltarBlock.summon(p, altar);
            try {
                h.assertTrue(heart != null && heart.home().equals(altar), "Altar did not wake the Heart");
                h.assertTrue(cn.wwl.orimod.block.GloomAltarBlock.summon(p, altar) == null, "Altar woke a second Heart");
            } finally { if (heart != null) heart.discard(); }
        });
        add(tests, "gloom_heart_telegraphs_volleys_and_rings", h -> {
            var p = spirit(h, 127);
            var heart = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_HEART.get(), new Vec3(5.5, 5, 10.5)); heart.setNoAi(true);
            try {
                heart.startAttack(cn.wwl.orimod.entity.GloomHeart.VOLLEY, p);
                for (int i = 1; i < cn.wwl.orimod.entity.GloomHeart.VOLLEY_TELEGRAPH; i++) h.assertTrue(heart.tickAttack().isEmpty(), "Volley left early");
                var volley = heart.tickAttack();
                h.assertValueEqual(volley.size(), 3, "Phase 1 volley");
                for (var s : volley) { h.assertTrue(s.getOwner() == heart && cn.wwl.orimod.movement.SpiritTargets.eligible(p, s), "Volley orb not Bashable"); s.discard(); }
                heart.startAttack(cn.wwl.orimod.entity.GloomHeart.RING, p);
                java.util.List<cn.wwl.orimod.entity.GloomShot> ring = java.util.List.of();
                for (int i = 0; i < cn.wwl.orimod.entity.GloomHeart.RING_TELEGRAPH; i++) ring = heart.tickAttack();
                h.assertValueEqual(ring.size(), 8, "Phase 1 ring");
                ring.forEach(net.minecraft.world.entity.Entity::discard);
                // Its own orbs never hurt it; a Bashed orb hurts it hard.
                var own = new cn.wwl.orimod.entity.GloomShot(cn.wwl.orimod.entity.OriMobs.GLOOM_SHOT.get(), h.getLevel()); own.setOwner(heart);
                h.assertTrue(!own.strike(heart), "Heart hurt by its own orb");
                own.setOwner(p); float hp = heart.getHealth();
                h.assertTrue(own.strike(heart), "Bashed orb did not hurt the Heart");
                h.assertTrue(hp - heart.getHealth() >= cn.wwl.orimod.entity.GloomHeart.REFLECT_DAMAGE - 2, "Reflected damage " + (hp - heart.getHealth()));
                own.discard();
            } finally { heart.discard(); }
        });
        add(tests, "gloom_heart_enrages_at_half_health", h -> {
            var p = spirit(h, 127);
            var heart = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_HEART.get(), new Vec3(5.5, 5, 10.5)); heart.setNoAi(true);
            heart.setHome(h.absolutePos(new net.minecraft.core.BlockPos(5, 1, 10)));
            try {
                heart.setHealth(cn.wwl.orimod.entity.GloomHeart.MAX_HEALTH / 2 + 2);
                heart.hurt(h.getLevel().damageSources().playerAttack(p), 6);
                h.assertValueEqual(heart.phase(), 2, "Phase after half health");
                h.assertTrue(heart.enraging(), "No invulnerable window");
                float hp = heart.getHealth(); heart.invulnerableTime = 0;
                h.assertTrue(!heart.hurt(h.getLevel().damageSources().playerAttack(p), 6) && heart.getHealth() == hp, "Hurt while enraging");
                var spitters = h.getLevel().getEntitiesOfClass(cn.wwl.orimod.entity.GloomSpitter.class, heart.getBoundingBox().inflate(12));
                h.assertValueEqual(spitters.size(), 2, "Spitters called");
                spitters.forEach(net.minecraft.world.entity.Entity::discard);
                h.assertValueEqual(cn.wwl.orimod.entity.GloomHeart.volleyCount(heart.phase()), 5, "Phase 2 volley");
                // Phase 2 slam: marked on the ground, hits only near the mark.
                heart.startAttack(cn.wwl.orimod.entity.GloomHeart.SLAM, p);
                h.assertTrue(heart.slamTarget() != null && heart.slamTarget().distanceTo(p.position()) < .01, "Slam not marked at the player");
            } finally { heart.discard(); }
        });
        add(tests, "gloom_heart_escape_then_victory_reward", h -> {
            var p = spirit(h, 127);
            var heart = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_HEART.get(), new Vec3(5.5, 5, 10.5)); heart.setNoAi(true);
            heart.setHome(h.absolutePos(new net.minecraft.core.BlockPos(5, 1, 10)));
            try {
                // FakePlayers are not in the level's player list, so start the escape the way die() does for real players.
                cn.wwl.orimod.world.EscapeSequence.start(p, Vec3.atBottomCenterOf(heart.home()).add(0, 1, 0));
                h.assertTrue(cn.wwl.orimod.world.EscapeSequence.session(p) != null, "Escape did not start");
                int life = cn.wwl.orimod.item.OriItems.LIFE_CELL.get() == null ? 0 : p.getInventory().countItem(cn.wwl.orimod.item.OriItems.LIFE_CELL.get());
                h.assertTrue(cn.wwl.orimod.world.EscapeSequence.victory(p), "First victory not marked first");
                h.assertTrue(cn.wwl.orimod.world.EscapeSequence.session(p) == null, "Escape still running after victory");
                h.assertValueEqual(p.getInventory().countItem(cn.wwl.orimod.item.OriItems.LIFE_CELL.get()), life + 1, "First-clear Life Cell");
                h.assertTrue(!cn.wwl.orimod.world.EscapeSequence.victory(p), "Second victory marked first");
                h.assertValueEqual(p.getInventory().countItem(cn.wwl.orimod.item.OriItems.LIFE_CELL.get()), life + 1, "Cells granted twice");
            } finally { heart.discard(); cn.wwl.orimod.world.EscapeSequence.stop(p); }
        });
        add(tests, "water_dash_breath_and_cooldown", h -> {
            var p = spirit(h, 127 | cn.wwl.orimod.progression.Ability.WATER_DASH.bit);
            var pos = h.absolutePos(new net.minecraft.core.BlockPos(5, 2, 5));
            h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            h.getLevel().setBlock(pos.above(), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            try {
                p.setPos(Vec3.atBottomCenterOf(pos)); p.baseTick();   // refreshes the in-water state
                h.assertTrue(cn.wwl.orimod.movement.WaterMovement.active(p), "Water movement not active in water");
                p.setAirSupply(10);
                cn.wwl.orimod.movement.WaterMovement.breathe(p);
                h.assertValueEqual(p.getAirSupply(), p.getMaxAirSupply(), "Air not refilled");
                h.assertTrue(cn.wwl.orimod.movement.WaterMovement.dash(p), "Water dash refused");
                h.assertTrue(!cn.wwl.orimod.movement.WaterMovement.dash(p), "Water dash ignored its cooldown");
                p.setData(SpiritData.ABILITIES, 127);
                h.assertTrue(!cn.wwl.orimod.movement.WaterMovement.active(p), "Water movement without the ability");
            } finally {
                h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                h.getLevel().setBlock(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        });
        add(tests, "charge_flame_bursts_and_cleanses_gloom_water", h -> {
            var p = spirit(h, 127 | cn.wwl.orimod.progression.Ability.CHARGE_FLAME.bit | cn.wwl.orimod.progression.Ability.LIGHT_BURST.bit);
            p.setData(SpiritData.ENERGY, 100f);
            var near = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(7.5, 2, 5.5)); near.setNoAi(true);
            var far = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(5.5, 2, 11.5)); far.setNoAi(true);
            var pool = h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 5));
            h.getLevel().setBlock(pool, cn.wwl.orimod.block.OriBlocks.GLOOM_WATER.get().defaultBlockState(), 3);
            try {
                float n = near.getHealth(), f = far.getHealth();
                h.assertValueEqual(cn.wwl.orimod.combat.ChargeFlame.release(p), 1, "Charge Flame hits");
                h.assertTrue(n - near.getHealth() > 6 && far.getHealth() == f, "Charge Flame damage near/far");
                h.assertTrue(h.getLevel().getBlockState(pool).is(net.minecraft.world.level.block.Blocks.WATER), "Gloom water not cleansed");
                h.assertValueEqual(cn.wwl.orimod.combat.ChargeFlame.release(p), -1, "Charge Flame ignored its cooldown");
                h.assertTrue(cn.wwl.orimod.combat.ChargeFlame.refusal(true, false, 100, 0, 0).equals("locked"), "Locked refusal");
            } finally { near.discard(); far.discard(); h.getLevel().setBlock(pool, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }
        });
        add(tests, "gloom_water_hurts_and_slows", h -> {
            var pool = h.absolutePos(new net.minecraft.core.BlockPos(5, 2, 5));
            h.getLevel().setBlock(pool, cn.wwl.orimod.block.OriBlocks.GLOOM_WATER.get().defaultBlockState(), 3);
            var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new Vec3(5.5, 2, 5.5)); villager.setNoAi(true);
            try {
                float hp = villager.getHealth();
                for (int i = 0; i < cn.wwl.orimod.block.GloomWaterBlock.HURT_INTERVAL; i++)
                    h.getLevel().getBlockState(pool).entityInside(h.getLevel(), pool, villager);
                h.assertTrue(villager.getHealth() < hp, "Gloom water did not hurt");
                h.assertTrue(villager.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "Gloom water did not slow");
            } finally { villager.discard(); h.getLevel().setBlock(pool, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); }
        });
        add(tests, "spirit_spear_pierces_three", h -> {
            var p = spirit(h, 127 | cn.wwl.orimod.progression.Ability.SPEAR.bit);
            p.setData(SpiritData.ENERGY, 100f);
            var zombies = new java.util.ArrayList<net.minecraft.world.entity.monster.Zombie>();
            for (int i = 0; i < 4; i++) { var z = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(5.5, 2, 7.5 + i)); z.setNoAi(true); zombies.add(z); }
            try {
                var spear = cn.wwl.orimod.combat.SpiritSpear.thrown(p);
                h.assertTrue(spear != null, "Spear not thrown");
                h.assertTrue(cn.wwl.orimod.combat.SpiritSpear.thrown(p) == null, "Spear ignored its cooldown");
                for (var z : zombies) spear.pierce(z);
                h.assertTrue(!spear.pierce(zombies.getFirst()), "Spear hit the same target twice");
                int hurt = 0; for (var z : zombies) if (z.getHealth() < z.getMaxHealth()) hurt++;
                h.assertValueEqual(hurt, cn.wwl.orimod.combat.SpiritSpear.PIERCE, "Spear pierce count");
                h.assertTrue(spear.isRemoved(), "Spear survived its last pierce");
                h.assertTrue("locked".equals(cn.wwl.orimod.combat.SpiritSpear.refusal(true, false, 100, 0, 0)), "Locked refusal");
            } finally { zombies.forEach(net.minecraft.world.entity.Entity::discard); }
        });
        add(tests, "gloom_hawk_telegraphs_then_dives", h -> {
            var victim = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new Vec3(5.5, 2, 5.5)); victim.setNoAi(true);
            var hawk = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_HAWK.get(), new Vec3(5.5, 8, 9.5)); hawk.setNoAi(true);
            try {
                h.assertTrue(hawk instanceof net.minecraft.world.entity.monster.Enemy, "Hawk is not an enemy");
                hawk.telegraph(victim);
                h.assertValueEqual(hawk.state(), cn.wwl.orimod.entity.GloomHawk.TELEGRAPH, "State after telegraph");
                for (int i = 1; i < cn.wwl.orimod.entity.GloomHawk.TELEGRAPH_TICKS; i++) hawk.step();
                h.assertValueEqual(hawk.state(), cn.wwl.orimod.entity.GloomHawk.TELEGRAPH, "Dived before the telegraph ended");
                hawk.step();
                h.assertValueEqual(hawk.state(), cn.wwl.orimod.entity.GloomHawk.DIVE, "No dive after the telegraph");
                hawk.step();
                var v = hawk.getDeltaMovement();
                h.assertTrue(v.y < 0 && v.z < 0, "Dive not aimed at the mark: " + v);
                float hp = victim.getHealth();
                h.assertTrue(hawk.strike(victim) && victim.getHealth() < hp, "Dive contact did not hurt");
            } finally { hawk.discard(); victim.discard(); }
        });
        add(tests, "gloom_matriarch_dives_stuns_feathers_and_enrages", h -> {
            var M = cn.wwl.orimod.entity.GloomMatriarch.class;
            var victim = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new Vec3(5.5, 2, 3.5)); victim.setNoAi(true);
            var boss = h.spawn(cn.wwl.orimod.entity.OriMobs.GLOOM_MATRIARCH.get(), new Vec3(5.5, 9, 9.5)); boss.setNoAi(true);
            boss.setHome(h.absolutePos(new net.minecraft.core.BlockPos(5, 2, 9)));
            try {
                // Dive: telegraph first, then a plunge towards the marked point.
                boss.startAttack(cn.wwl.orimod.entity.GloomMatriarch.DIVE_TELEGRAPH, victim);
                for (int i = 1; i < cn.wwl.orimod.entity.GloomMatriarch.DIVE_TELEGRAPH_TICKS; i++) boss.step();
                h.assertValueEqual(boss.attack(), cn.wwl.orimod.entity.GloomMatriarch.DIVE_TELEGRAPH, "Dived before the telegraph ended");
                boss.step();
                h.assertValueEqual(boss.attack(), cn.wwl.orimod.entity.GloomMatriarch.DIVE, "No dive after the telegraph");
                var v = boss.diveVelocity();
                h.assertTrue(v.y < 0 && v.z < 0 && Math.abs(v.length() - cn.wwl.orimod.entity.GloomMatriarch.DIVE_SPEED) < 1e-6, "Dive not aimed at the mark: " + v);
                // A dodged dive leaves her stunned, taking double damage.
                boss.stun();
                float hp = boss.getHealth();
                boss.hurt(h.getLevel().damageSources().generic(), 4);
                h.assertTrue(Math.abs(hp - boss.getHealth() - 4 * cn.wwl.orimod.entity.GloomMatriarch.STUN_MULTIPLIER) < .01f, "Stun damage " + (hp - boss.getHealth()));
                for (int i = 0; i < cn.wwl.orimod.entity.GloomMatriarch.STUN_TICKS; i++) boss.step();
                h.assertValueEqual(boss.attack(), cn.wwl.orimod.entity.GloomMatriarch.IDLE, "Stun did not end");
                // Feathers: a Bashable fan after the telegraph.
                boss.startAttack(cn.wwl.orimod.entity.GloomMatriarch.FEATHERS, victim);
                java.util.List<cn.wwl.orimod.entity.GloomShot> shots = java.util.List.of();
                for (int i = 0; i < cn.wwl.orimod.entity.GloomMatriarch.FEATHER_TELEGRAPH && shots.isEmpty(); i++) shots = boss.step();
                h.assertValueEqual(shots.size(), cn.wwl.orimod.entity.GloomMatriarch.featherCount(1), "Feather fan");
                shots.forEach(net.minecraft.world.entity.Entity::discard);
                // Phase two at half health: two Gloom Hawks join.
                boss.invulnerableTime = 0;
                boss.hurt(h.getLevel().damageSources().generic(), boss.getHealth() - cn.wwl.orimod.entity.GloomMatriarch.MAX_HEALTH / 2 + 1);
                h.assertValueEqual(boss.phase(), 2, "No phase two");
                var hawks = h.getLevel().getEntitiesOfClass(cn.wwl.orimod.entity.GloomHawk.class, boss.getBoundingBox().inflate(16));
                h.assertValueEqual(hawks.size(), 2, "Phase two hawks");
                hawks.forEach(net.minecraft.world.entity.Entity::discard);
            } finally { boss.discard(); victim.discard(); }
        });
        add(tests, "launch_contact_hits_enemies_once", h -> {
            var p = spirit(h, 127);
            var s = p.getData(SpiritData.MOVEMENT);
            var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new Vec3(5.5, 2, 6.2)); zombie.setNoAi(true);
            var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new Vec3(6.2, 2, 5.5)); villager.setNoAi(true);
            try {
                p.setDeltaMovement(0, 1, .8); s.launchHits.clear();
                float hp = zombie.getHealth();
                h.assertValueEqual(cn.wwl.orimod.movement.SpiritMovement.launchContact(p, s), 1, "Launch contact hits");
                h.assertTrue(hp - zombie.getHealth() > MovementRules.LAUNCH_DAMAGE - .5f, "Launch contact damage " + (hp - zombie.getHealth()));   // zombie armour 2
                h.assertTrue(villager.getHealth() == villager.getMaxHealth(), "Launch hurt a villager");
                zombie.invulnerableTime = 0;
                h.assertValueEqual(cn.wwl.orimod.movement.SpiritMovement.launchContact(p, s), 0, "Same Launch hit twice");
            } finally { zombie.discard(); villager.discard(); }
        });
        return tests;
    }

    static void jump(ServerPlayer p, int sequence, boolean jump) {
        SpiritMovement.accept(p, new MovementInputPayload(sequence, p.level().getGameTime(),
                MovementInputPayload.ACTIVE | (jump ? MovementInputPayload.JUMP : 0), 0, 0, 0, 0));
    }

    static ServerPlayer spirit(GameTestHelper h, int abilities) {
        var p = Fixtures.player(h); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, abilities);
        return p;
    }

    /** Back on the ground after a test jump, without waiting out the post-jump ground lock. */
    static void regrounded(ServerPlayer p, cn.wwl.orimod.movement.MovementState s) {
        SpiritMovement.cancel(p, s); s.groundLockUntil = 0; p.setDeltaMovement(Vec3.ZERO); p.setOnGround(true);
    }

    static void crouchJump(ServerPlayer p, int sequence, boolean jump) {
        SpiritMovement.accept(p, new MovementInputPayload(sequence, p.level().getGameTime(),
                MovementInputPayload.ACTIVE | MovementInputPayload.CROUCH | (jump ? MovementInputPayload.JUMP : 0), 0, 0, 0, -30));
    }

    static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("parity", "parity." + name, "orimod_gametest:room", 30, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
