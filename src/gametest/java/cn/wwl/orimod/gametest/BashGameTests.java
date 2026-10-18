package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class BashGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var type : new EntityType<?>[]{EntityType.SNOWBALL, EntityType.EGG, EntityType.ENDER_PEARL, EntityType.ARROW}) {
            add(tests, "own_" + EntityType.getKey(type).getPath(), h -> {
                var player = player(h); var target = spawn(h, type);
                ((Projectile) target).setOwner(player);
                try { h.assertTrue(SpiritTargets.find(player, 0, 0) == null, "Own projectile selected for Bash"); }
                finally { target.discard(); }
            });
        }
        for (boolean own : new boolean[]{false, true}) {
            add(tests, (own ? "own" : "other") + "_fishing_hook", h -> {
                var player = player(h);
                var hook = new FishingHook(own ? player : Fixtures.player(h), h.getLevel(), 0, 0);
                hook.setPos(h.absoluteVec(new Vec3(5.5, 3, 7.5))); h.getLevel().addFreshEntity(hook);
                try { h.assertTrue(SpiritTargets.find(player, 0, 0) == null, "Fishing hook became a reusable anchor"); }
                finally { hook.discard(); }
            });
        }
        for (var type : new EntityType<?>[]{EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN,
                EntityType.ELDER_GUARDIAN, EntityType.RAVAGER, EntityType.GHAST, EntityType.CREEPER}) {
            add(tests, "immune_" + EntityType.getKey(type).getPath(), h -> {
                var player = player(h); var target = spawn(h, type);
                // Aim at its centre so a rejected large mob cannot pass due to reticle alignment.
                Vec3 offset = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
                float pitch = (float) -Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));
                try { h.assertTrue(SpiritTargets.find(player, 0, pitch) == null, "Boss, oversize or tagged entity selected"); }
                finally { target.discard(); }
            });
        }
        for (var type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.HUSK, EntityType.ENDERMAN}) {
            add(tests, "ordinary_" + EntityType.getKey(type).getPath(), h -> {
                var player = player(h); var target = spawn(h, type);
                try {
                    var selected = SpiritTargets.find(player, 0, 0);
                    h.assertTrue(selected != null && selected.entity() == target, "Ordinary enemy no longer selectable");
                } finally { target.discard(); }
            });
        }
        add(tests, "other_player_projectile_remains_eligible", h -> {
            var player = player(h); var target = (Projectile)spawn(h, EntityType.SNOWBALL);
            target.setOwner(Fixtures.player(h));
            try {
                var selected = SpiritTargets.find(player, 0, 0);
                h.assertTrue(selected != null && selected.entity() == target, "Other player's projectile rejected");
            } finally { target.discard(); }
        });
        add(tests, "stationary_foreign_arrow_is_deliberate_anchor", h -> {
            var player = player(h); var target = (Projectile)spawn(h, EntityType.ARROW);
            target.setOwner(EntityType.SKELETON.create(h.getLevel())); target.setDeltaMovement(Vec3.ZERO);
            try {
                var selected = SpiritTargets.find(player, 0, 0);
                h.assertTrue(selected != null && selected.entity() == target, "Foreign stationary arrow lost anchor behavior");
            } finally { target.discard(); }
        });
        add(tests, "owner_change_invalidates_selection", h -> {
            var player = player(h); var target = (Projectile)spawn(h, EntityType.ARROW);
            target.setOwner(EntityType.SKELETON.create(h.getLevel()));
            try {
                var selected = SpiritTargets.find(player, 0, 0);
                h.assertTrue(selected != null, "Fixture projectile not selected");
                target.setOwner(player);
                h.assertFalse(selected.valid(player), "Session did not recheck projectile ownership");
            } finally { target.discard(); }
        });
        add(tests, "growth_invalidates_selection", h -> {
            var player = player(h); var target = (Slime)spawn(h, EntityType.SLIME); target.setSize(2, true);
            try {
                var selected = SpiritTargets.find(player, 0, 0);
                h.assertTrue(selected != null, "Small slime fixture not selected");
                target.setSize(6, true);
                h.assertFalse(selected.valid(player), "Session did not recheck changed size");
            } finally { target.discard(); }
        });
        add(tests, "own_projectile_cannot_start_or_refill", h -> {
            var player = player(h); var target = (Projectile)spawn(h, EntityType.SNOWBALL); target.setOwner(player);
            var state = player.getData(SpiritData.MOVEMENT);
            try {
                press(player, 1, true); press(player, 2, false);
                h.assertTrue(state.bash == null, "Own projectile started a Bash session");
                h.assertValueEqual(state.acceptedBashes, 0, "Own projectile launched player");
                h.assertTrue(state.airJumpSpent && state.airDashSpent, "Rejected Bash refilled air charges");
            } finally { SpiritMovement.cancel(player, state); target.discard(); }
        });
        add(tests, "enemy_bash_launches_once_and_cools_down", h -> {
            var player = player(h); var target = spawn(h, EntityType.ZOMBIE);
            var state = player.getData(SpiritData.MOVEMENT);
            try {
                press(player, 1, true);
                h.assertTrue(state.bash != null, "Enemy Bash failed to enter aim");
                press(player, 2, false);
                h.assertValueEqual(state.acceptedBashes, 1, "Bash launch count");
                h.assertFalse(state.airJumpSpent || state.airDashSpent, "Valid Bash did not refill air charges");
                Fixtures.near(h, target.getDeltaMovement().z, -.8, "Target recoil");
                press(player, 3, true);
                h.assertTrue(state.bash == null, "Same target bypassed cooldown");
            } finally { SpiritMovement.cancel(player, state); target.discard(); }
        });
        return tests;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = Fixtures.player(helper); SpiritForm.toggle(player); player.setData(SpiritData.ABILITIES, 63);
        return player;
    }

    private static Entity spawn(GameTestHelper helper, EntityType<?> type) {
        var target = helper.spawn(type, new Vec3(5.5, 2, 7.5));
        if (target instanceof Mob mob) mob.setNoAi(true);
        if (target instanceof Projectile) target.setPos(target.position().add(0, 1, 0));
        return target;
    }

    private static void press(ServerPlayer player, int sequence, boolean bash) {
        SpiritMovement.accept(player, new MovementInputPayload(sequence, player.level().getGameTime(),
                MovementInputPayload.ACTIVE | (bash ? MovementInputPayload.BASH : 0), 0, 0, 0, 0));
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("bash", "bash." + name, "orimod_gametest:room", 30, 0, true,
                helper -> { run.accept(helper); helper.succeed(); }));
    }
}
