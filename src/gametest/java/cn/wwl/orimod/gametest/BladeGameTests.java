package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.combat.BladeArc;
import cn.wwl.orimod.combat.CombatTargets;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class BladeGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var type : new EntityType<?>[]{EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.COW,
                EntityType.PIG, EntityType.IRON_GOLEM, EntityType.ARMOR_STAND, EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN}) {
            add(tests, "protect_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, false, false, false));
            add(tests, "direct_matches_vanilla_" + EntityType.getKey(type).getPath(), h -> directMatchesVanilla(h, type));
        }
        for (var type : new EntityType<?>[]{EntityType.WOLF, EntityType.CAT}) {
            add(tests, "protect_tamed_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, true, false, false));
            add(tests, "protect_direct_tamed_" + EntityType.getKey(type).getPath(), h -> protectDirectPet(h, type));
        }
        for (var type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER}) {
            add(tests, "hit_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, false, false, true));
        }
        for (var type : new EntityType<?>[]{EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN}) {
            add(tests, "hit_attacking_" + EntityType.getKey(type).getPath(), h -> checkHit(h, type, false, true, true));
        }
        add(tests, "empty_crosshair_cow_in_fan_is_protected", h -> checkHit(h, EntityType.COW, false, false, false));
        for (boolean wall : new boolean[]{false, true})
            add(tests, wall ? "direct_pick_stops_at_wall" : "direct_pick_respects_vanilla_reach", h -> blockedPick(h, wall));
        add(tests, "protect_direct_teammate", BladeGameTests::protectDirectTeammate);
        for (boolean owned : new boolean[]{false, true})
            add(tests, "direct_practice_target_owned_" + owned, h -> practiceOwnership(h, owned));
        return tests;
    }

    private static void checkHit(GameTestHelper helper, EntityType<?> type, boolean tamed, boolean attacking, boolean expectedHit) {
        var player = Fixtures.player(helper); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        // Off the actual eye ray, but well inside the blade fan. Bystander protection is
        // independent of the K2 rule allowing deliberate vanilla-style direct attacks.
        var target = (LivingEntity)helper.spawn(type, new Vec3(7, 2, 7));
        if (target instanceof Mob mob) { mob.setNoAi(true); if (attacking) mob.setTarget(player); }
        if (tamed) ((TamableAnimal)target).tame(player);
        float before = target.getHealth();
        try {
            helper.assertTrue(CombatTargets.aimed(player, BladeArc.reach(1)) == null, "Bystander fixture must aim at empty space");
            var origin = player.position().add(0, player.getBbHeight() * .55, 0);
            helper.assertTrue(BladeArc.contains(origin, player.getYRot(), BladeArc.closest(origin, target.getBoundingBox()), 1), "Bystander is outside the fan");
            SpiritCombat.attack(player);
            var state = player.getData(SpiritData.COMBAT);
            helper.assertValueEqual(state.mode, "blade", "Blade fixture did not start");
            state.startedAt -= SpiritCombat.windup(1);
            SpiritCombat.tick(new PlayerTickEvent.Post(player));
            if (expectedHit) {
                helper.assertTrue(state.hitTargets.contains(target.getUUID()), "Hostile target not reached by swing");
                float afterHit = target.getHealth();
                helper.assertTrue(afterHit < before, "Hostile target took no actual damage");
                if (target.getArmorValue() == 0) Fixtures.near(helper, afterHit, before - 3, "Unarmored blade damage");
                helper.assertValueEqual(state.bladeHits, 1, "One successful blade hit");
                // Clear vanilla immunity so only our per-swing ledger prevents a duplicate hit.
                target.invulnerableTime = 0;
                SpiritCombat.tick(new PlayerTickEvent.Post(player));
                Fixtures.near(helper, target.getHealth(), afterHit, "Duplicate blade damage within swing");
            } else {
                helper.assertFalse(state.hitTargets.contains(target.getUUID()), "Protected entity entered the damage path");
                Fixtures.near(helper, target.getHealth(), before, "Protected entity health");
                helper.assertTrue(target.isAlive() && !target.isRemoved(), "Protected entity was destroyed");
            }
        } finally { SpiritCombat.cancel(player, "test_cleanup"); target.discard(); }
    }

    private static void directMatchesVanilla(GameTestHelper h, EntityType<?> type) {
        var player = Fixtures.player(h); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        var vanilla = Fixtures.player(h);
        vanilla.setPos(h.absoluteVec(new Vec3(2, 2, 5.5)));
        // Equal unenchanted damage, full attack charge, no critical/sweep: exercise the
        // actual Player.attack path as the reference, not a hand-written hurt result.
        vanilla.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3);
        vanilla.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
        vanilla.setOnGround(true);
        var target = (LivingEntity) h.spawn(type, new Vec3(5.5, 2, 7));
        var reference = (LivingEntity) h.spawn(type, new Vec3(2, 2, 7));
        for (var entity : new LivingEntity[]{target, reference}) {
            if (entity instanceof Mob mob) mob.setNoAi(true);
            h.getLevel().tickNonPassenger(entity); // Nonzero vanilla revenge timestamp.
            if (type == EntityType.ARMOR_STAND) entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        }
        try {
            aim(player, target.getBoundingBox().getCenter());
            h.assertTrue(CombatTargets.aimed(player, BladeArc.reach(1)) == target, "Direct fixture is not the actual server pick");
            vanilla.attack(reference);
            if (type == EntityType.ARMOR_STAND) SpiritCombat.attack(player); else strike(player);
            if (type != EntityType.ARMOR_STAND)
                h.assertTrue(player.getData(SpiritData.COMBAT).hitTargets.contains(target.getUUID()), "Direct target missed damage path");
            Fixtures.near(h, target.getHealth(), reference.getHealth(), "Native direct damage result");
            h.assertValueEqual(target.isRemoved(), reference.isRemoved(), "Native first-hit removal result");
            if (type == EntityType.ARMOR_STAND) {
                h.assertTrue(target.isAlive() && reference.isAlive(), "First survival hit should wobble the stand");
                vanilla.attack(reference);
                SpiritCombat.attack(player); // A second real input; no artificial combo reset.
                h.assertTrue(target.isRemoved() && reference.isRemoved(), "Second hit should break both stands");
                for (var item : new net.minecraft.world.item.Item[]{Items.ARMOR_STAND, Items.IRON_HELMET}) {
                    int actual = drops(h, target, item), expected = drops(h, reference, item);
                    h.assertValueEqual(expected, 1, "Native stand drop fixture");
                    h.assertValueEqual(actual, expected, "Blade must preserve native stand/equipment drops");
                }
            } else {
                h.assertTrue(target.getHealth() < target.getMaxHealth(), "Direct attack caused no damage");
                h.assertTrue(target.getLastHurtByMob() == player && reference.getLastHurtByMob() == vanilla, "Native player attribution lost");
                if (target instanceof NeutralMob && reference instanceof NeutralMob) {
                    retaliate(h, (PathfinderMob) target, player);
                    retaliate(h, (PathfinderMob) reference, vanilla);
                }
            }
        } finally {
            SpiritCombat.cancel(player, "test_cleanup");
            for (var entity : new LivingEntity[]{target, reference}) {
                h.getLevel().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(1)).forEach(ItemEntity::discard);
                entity.discard();
            }
        }
    }

    private static void retaliate(GameTestHelper h, PathfinderMob mob, ServerPlayer attacker) {
        var goal = new HurtByTargetGoal(mob);
        h.assertTrue(goal.canUse(), "Real player damage did not activate vanilla retaliation");
        goal.start();
        h.assertTrue(mob.getTarget() == attacker, "Vanilla retaliation selected the wrong attacker");
        var neutral = (NeutralMob) mob;
        neutral.updatePersistentAnger(h.getLevel(), true);
        h.assertTrue(neutral.isAngryAt(attacker), "Vanilla persistent anger did not follow player damage");
    }

    private static int drops(GameTestHelper h, LivingEntity entity, net.minecraft.world.item.Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(1), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void protectDirectPet(GameTestHelper h, EntityType<?> type) {
        var player = Fixtures.player(h); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        var pet = (TamableAnimal) h.spawn(type, new Vec3(5.5, 2, 7));
        pet.tame(player); pet.setNoAi(true);
        try {
            aim(player, pet.getBoundingBox().getCenter());
            h.assertTrue(CombatTargets.aimed(player, BladeArc.reach(1)) == pet, "Pet must be the actual direct pick");
            float before = pet.getHealth(); strike(player);
            h.assertFalse(player.getData(SpiritData.COMBAT).hitTargets.contains(pet.getUUID()), "Own pet entered direct damage path");
            Fixtures.near(h, pet.getHealth(), before, "Own directly aimed pet health");
        } finally { SpiritCombat.cancel(player, "test_cleanup"); pet.discard(); }
    }

    private static void blockedPick(GameTestHelper h, boolean wall) {
        var player = Fixtures.player(h); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        var cow = (LivingEntity) h.spawn(EntityType.COW, new Vec3(5.5, 2, wall ? 8 : 9.2));
        ((Mob) cow).setNoAi(true);
        var barrier = player.blockPosition().south();
        var old = h.getLevel().getBlockState(barrier);
        var oldAbove = h.getLevel().getBlockState(barrier.above());
        try {
            if (wall) {
                h.getLevel().setBlock(barrier, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                h.getLevel().setBlock(barrier.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            aim(player, cow.getBoundingBox().getCenter());
            var origin = player.position().add(0, player.getBbHeight() * .55, 0);
            h.assertTrue(BladeArc.contains(origin, player.getYRot(), BladeArc.closest(origin, cow.getBoundingBox()), 1), "Cow must remain in the fan");
            h.assertTrue(CombatTargets.aimed(player, 100) == null, "Ray bypassed native reach or wall");
            float before = cow.getHealth(); strike(player);
            h.assertFalse(player.getData(SpiritData.COMBAT).hitTargets.contains(cow.getUUID()), "Non-picked cow entered direct damage path");
            Fixtures.near(h, cow.getHealth(), before, "Cow behind wall/outside native reach");
        } finally {
            SpiritCombat.cancel(player, "test_cleanup"); cow.discard();
            if (wall) { h.getLevel().setBlock(barrier, old, 2); h.getLevel().setBlock(barrier.above(), oldAbove, 2); }
        }
    }

    private static void protectDirectTeammate(GameTestHelper h) {
        var player = Fixtures.player(h); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        var target = (LivingEntity) h.spawn(EntityType.ZOMBIE, new Vec3(5.5, 2, 7));
        ((Mob) target).setNoAi(true);
        var scoreboard = h.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("blade-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(target.getScoreboardName(), team);
        try {
            aim(player, target.getBoundingBox().getCenter());
            h.assertTrue(CombatTargets.aimed(player, 3.4) == target, "Teammate must be the actual direct pick");
            float before = target.getHealth(); strike(player);
            h.assertFalse(player.getData(SpiritData.COMBAT).hitTargets.contains(target.getUUID()), "Teammate entered damage path");
            Fixtures.near(h, target.getHealth(), before, "Direct teammate health");
        } finally { SpiritCombat.cancel(player, "test_cleanup"); target.discard(); scoreboard.removePlayerTeam(team); }
    }

    private static void practiceOwnership(GameTestHelper h, boolean owned) {
        var player = Fixtures.player(h); SpiritForm.toggle(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
        var target = cn.wwl.orimod.world.OriEntities.PRACTICE_TARGET.get().create(h.getLevel());
        target.practiceOwner(owned ? player.getUUID() : java.util.UUID.randomUUID());
        target.setPos(h.absoluteVec(new Vec3(5.5, 2, 7))); h.getLevel().addFreshEntity(target);
        try {
            aim(player, target.getBoundingBox().getCenter());
            h.assertTrue(CombatTargets.aimed(player, 3.4) == target, "Practice target must be the actual direct pick");
            float before = target.getHealth(); strike(player);
            h.assertValueEqual(player.getData(SpiritData.COMBAT).hitTargets.contains(target.getUUID()), owned, "Practice ownership eligibility");
            Fixtures.near(h, target.getHealth(), before - (owned ? 3 : 0), "Practice ownership damage");
        } finally { SpiritCombat.cancel(player, "test_cleanup"); target.discard(); }
    }

    private static void aim(ServerPlayer player, Vec3 target) {
        var d = target.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
    }

    private static void strike(ServerPlayer player) {
        SpiritCombat.attack(player);
        var state = player.getData(SpiritData.COMBAT);
        state.startedAt = player.level().getGameTime() - SpiritCombat.windup(1);
        SpiritCombat.tick(new PlayerTickEvent.Post(player));
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("blade", "blade." + name, "orimod_gametest:room", 30, 0, true,
                helper -> { run.accept(helper); helper.succeed(); }));
    }
}
