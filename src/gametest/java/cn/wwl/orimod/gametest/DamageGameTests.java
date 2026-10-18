package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.combat.DamageInterrupts;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.movement.AirMovement;
import cn.wwl.orimod.movement.BashSession;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;

/** Actual survival-player hurt calls, including the post-damage movement tick. */
@GameTestHolder("orimod_gametest")
public final class DamageGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String action : new String[]{"blade", "bow", "dash", "bash_aim", "grapple", "stomp", "glide"}) {
            add(tests, action, "fire", false);
            add(tests, action, "melee", true);
        }
        for (String source : new String[]{"poison", "cactus", "starve", "drown", "fall", "generic", "own_arrow"})
            add(tests, "bow", source, false);
        for (String source : new String[]{"enemy_arrow", "dispenser_arrow"}) add(tests, "bash_aim", source, true);
        tests.add(new TestFunction("damage", "damage.bash_manual_cancel_after_fire_restores_momentum", "orimod_gametest:room", 40, 0, true,
                h -> { check(h, "bash_aim", "fire_manual", false); h.succeed(); }));
        return tests;
    }

    private static void add(Collection<TestFunction> tests, String action, String source, boolean interrupt) {
        tests.add(new TestFunction("damage", "damage." + action + "_" + source, "orimod_gametest:room", 40, 0, true,
                h -> { check(h, action, source, interrupt); h.succeed(); }));
    }

    private static void check(GameTestHelper h, String action, String source, boolean interrupt) {
        var server = h.getLevel().getServer(); var difficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.NORMAL, true);
        try (var connection = Fixtures.connectedPlayer(h)) {
            var p = connection.player();
            // ServerPlayer has a separate sixty-tick login immunity. Exercise its ordinary tick path.
            for (int i = 0; i < 61; i++) p.tick();
            p.invulnerableTime = 0; p.setHealth(p.getMaxHealth());
            SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, 63);
            p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false);
            p.setYRot(0); p.setXRot(0); p.setDeltaMovement(.23, -.1, .11);
            var s = p.getData(SpiritData.MOVEMENT);
            s.dimension = p.level().dimension().location().toString(); s.previousPosition = p.position().add(0, .1, 0);
            s.lastInput = p.level().getGameTime(); s.active = true; s.airDashSpent = false;
            var enemy = (Zombie)h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 5, 8)); enemy.setNoAi(true);
            var arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setPos(enemy.position());
            BlockPos flower = h.absolutePos(new BlockPos(5, 5, 9));
            Vec3 saved = p.getDeltaMovement(), targetMotion = new Vec3(.1, -.2, .3);
            enemy.setDeltaMovement(targetMotion);
            try {
                switch (action) {
                    case "blade" -> {
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
                        SpiritCombat.attack(p);
                    }
                    case "bow" -> {
                        var bow = new ItemStack(OriItems.SPIRIT_BOW.get()); p.setItemInHand(InteractionHand.MAIN_HAND, bow);
                        h.assertTrue(SpiritCombat.beginBow(p, bow), "Bow fixture failed"); p.startUsingItem(InteractionHand.MAIN_HAND);
                    }
                    case "dash", "bash_aim" -> SpiritMovement.accept(p, new MovementInputPayload(1, s.lastInput,
                            MovementInputPayload.ACTIVE | (action.equals("dash") ? MovementInputPayload.DASH : MovementInputPayload.BASH), 0, 0, 0, 0));
                    case "grapple" -> {
                        h.getLevel().setBlockAndUpdate(flower, OriBlocks.GRAPPLE_FLOWER.get().defaultBlockState());
                        AirMovement.beginGrapple(p, s, new SpiritTargets.Target(null, flower, Vec3.atCenterOf(flower), true), s.lastInput);
                    }
                    case "stomp" -> AirMovement.beginStomp(p, s, s.lastInput);
                    case "glide" -> { s.jumpHeld = true; s.observedDy = -.1; AirMovement.glide(p, s, s.lastInput, false, false); }
                }
                boolean combat = action.equals("blade") || action.equals("bow");
                h.assertValueEqual(combat ? p.getData(SpiritData.COMBAT).mode : s.mode.id(), action, "Action fixture failed");
                float health = p.getHealth();
                var damageSource = source(p, enemy, arrow, source);
                if (source.equals("own_arrow")) {
                    // GameTest servers disable PvP, so a self-owned arrow is rejected by vanilla first.
                    h.assertFalse(DamageInterrupts.action(p, damageSource, 2), "Self-owned projectile interrupts");
                    h.assertFalse(p.hurt(damageSource, 2), "Fixture unexpectedly allowed self-owned player damage");
                    Fixtures.near(h, p.getHealth(), health, "Vanilla-rejected damage changed health");
                } else {
                    h.assertTrue(p.hurt(damageSource, 2), "Actual damage rejected by fixture");
                    h.assertTrue(p.getHealth() < health, "Damage did not reduce health");
                }
                SpiritMovement.tick(new PlayerTickEvent.Post(p)); SpiritCombat.tick(new PlayerTickEvent.Post(p));
                h.assertValueEqual(combat ? p.getData(SpiritData.COMBAT).mode : s.mode.id(), interrupt ? "idle" : action, "Damage interruption rule");
                if (action.equals("bow")) h.assertTrue(p.isUsingItem() != interrupt, "Bow item use did not follow interruption");
                if (action.equals("bash_aim") && interrupt) {
                    h.assertTrue(s.bash == null, "Interrupted Bash retained session");
                    Fixtures.near(h, enemy.getDeltaMovement().distanceTo(targetMotion), 0, "Target motion was not restored");
                    enemy.setDeltaMovement(.6, .1, .2); BashSession.tickTarget(new EntityTickEvent.Post(enemy));
                    Fixtures.near(h, enemy.getDeltaMovement().x, .6, "Target remained frozen");
                    if (source.equals("melee")) h.assertTrue(p.getDeltaMovement().horizontalDistanceSqr() > .01, "Bash cancellation swallowed vanilla knockback");
                }
                if (source.equals("fire_manual")) {
                    h.assertTrue(p.hurtTime > 0, "Fixture must retain recent environmental hurt");
                    SpiritMovement.cancel(p, s);
                    Fixtures.near(h, p.getDeltaMovement().distanceTo(saved), 0, "Manual Bash cancellation lost saved momentum");
                }
            } finally { SpiritCombat.cancel(p, "test_cleanup"); SpiritMovement.cancel(p, s); enemy.discard(); arrow.discard(); h.getLevel().setBlockAndUpdate(flower, Blocks.AIR.defaultBlockState()); }
        } finally { server.setDifficulty(difficulty, true); }
    }

    private static DamageSource source(ServerPlayer p, Zombie enemy, Arrow arrow, String source) {
        var d = p.damageSources();
        return switch (source) {
            case "fire", "fire_manual" -> d.onFire();
            case "poison" -> d.magic();
            case "cactus" -> d.cactus();
            case "starve" -> d.starve();
            case "drown" -> d.drown();
            case "fall" -> d.fall();
            case "generic" -> d.generic();
            case "melee" -> d.mobAttack(enemy);
            case "own_arrow" -> { arrow.setOwner(p); yield d.arrow(arrow, p); }
            case "enemy_arrow" -> { arrow.setOwner(enemy); yield d.arrow(arrow, enemy); }
            case "dispenser_arrow" -> d.arrow(arrow, null);
            default -> throw new IllegalArgumentException(source);
        };
    }
}
