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
                p.setData(SpiritData.ABILITIES, 127); s.launchCharge = 3;
                crouchJump(p, 3, true); crouchJump(p, 4, false);
                h.assertValueEqual(s.acceptedLaunches, 0, "Uncharged ground Launch");
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

    static void crouchJump(ServerPlayer p, int sequence, boolean jump) {
        SpiritMovement.accept(p, new MovementInputPayload(sequence, p.level().getGameTime(),
                MovementInputPayload.ACTIVE | MovementInputPayload.CROUCH | (jump ? MovementInputPayload.JUMP : 0), 0, 0, 0, -30));
    }

    static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("parity", "parity." + name, "orimod_gametest:room", 30, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
