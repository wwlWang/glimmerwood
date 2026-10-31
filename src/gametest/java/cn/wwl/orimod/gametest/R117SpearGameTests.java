package cn.wwl.orimod.gametest;

import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_r117")
public final class R117SpearGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (var hand : InteractionHand.values()) for (int held : new int[]{0, 3, 8})
            add(tests, "release_" + hand.name().toLowerCase(Locale.ROOT) + "_" + held, h -> {
                var p = player(h, hand); var stack = p.getItemInHand(hand);
                use(h, p, hand); untouched(h, p, 100);
                if (held > 4) h.runAfterDelay(4, () -> { tick(p); untouched(h, p, 100); });
                Runnable release = () -> {
                    p.releaseUsingItem();
                    if (held < 4) untouched(h, p, 100); else firedOnce(h, p);
                };
                if (held == 0) release.run(); else h.runAfterDelay(held, release);
                h.runAfterDelay(Math.max(4, held) + 1, () -> {
                    tick(p); firedOnce(h, p);
                    // A stale or repeated RELEASE packet cannot spend or spawn a second time.
                    OriItems.SPIRIT_SPEAR.get().releaseUsing(stack, p.level(), p, 0); tick(p);
                    firedOnce(h, p); clean(p); h.succeed();
                });
            });
        for (boolean pending : new boolean[]{false, true}) for (String reason : List.of("screen", "item", "slot"))
            add(tests, "cancel_" + reason + "_" + (pending ? "tap" : "held"), h -> {
                var p = player(h, InteractionHand.MAIN_HAND); var stack = p.getMainHandItem(); use(h, p, InteractionHand.MAIN_HAND);
                if (pending) p.releaseUsingItem();
                if (reason.equals("screen")) SpiritCombat.cancel(p, "input"); // Same handler as ScreenEvent's cancel payload.
                else if (reason.equals("item")) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
                else { p.getInventory().setItem(1, stack); p.getInventory().selected = 1; }
                tick(p);
                h.assertTrue(p.getData(SpiritData.COMBAT).spear == null, "Cancel retained pending spear");
                OriItems.SPIRIT_SPEAR.get().releaseUsing(stack, p.level(), p, 0);
                h.runAfterDelay(6, () -> { tick(p); untouched(h, p, 100); h.succeed(); });
            });
        for (String reason : List.of("energy", "item_cooldown", "server_cooldown", "form", "locked"))
            add(tests, "refuse_on_press_" + reason, h -> {
                var p = player(h, InteractionHand.MAIN_HAND);
                switch (reason) {
                    case "energy" -> p.setData(SpiritData.ENERGY, 11f);
                    case "item_cooldown" -> p.getCooldowns().addCooldown(OriItems.SPIRIT_SPEAR.get(), 24);
                    case "server_cooldown" -> p.getData(SpiritData.COOLDOWNS).spearReadyAt = p.level().getGameTime() + 24;
                    case "form" -> p.setData(SpiritData.FORM, false);
                    case "locked" -> p.setData(SpiritData.ABILITIES, 0);
                }
                var result = OriItems.SPIRIT_SPEAR.get().use(p.level(), p, InteractionHand.MAIN_HAND);
                h.assertValueEqual(result.getResult(), InteractionResult.FAIL, "Refused press entered use");
                h.assertFalse(p.isUsingItem(), "Refused press raised spear");
                h.assertTrue(p.getData(SpiritData.COMBAT).spear == null, "Refused press queued spear");
                untouched(h, p, reason.equals("energy") ? 11 : 100); h.succeed();
            });
        for (boolean pending : new boolean[]{false, true}) add(tests, "recheck_energy_" + (pending ? "tap" : "held"), h -> {
            var p = player(h, InteractionHand.MAIN_HAND); use(h, p, InteractionHand.MAIN_HAND);
            if (pending) p.releaseUsingItem();
            p.setData(SpiritData.ENERGY, 11f);
            h.runAfterDelay(6, () -> {
                if (!pending) p.releaseUsingItem(); tick(p); untouched(h, p, 11);
                h.assertTrue(p.getData(SpiritData.COMBAT).spear == null, "Failed throw retained charge"); h.succeed();
            });
        });
        return tests;
    }

    private static ServerPlayer player(GameTestHelper h, InteractionHand hand) {
        var p = Fixtures.player(h); p.setData(SpiritData.FORM, true); p.refreshDimensions();
        p.setData(SpiritData.ABILITIES, Ability.SPEAR.bit); p.setData(SpiritData.ENERGY, 100f);
        p.setItemInHand(hand, new ItemStack(OriItems.SPIRIT_SPEAR.get()));
        return p;
    }
    private static void use(GameTestHelper h, ServerPlayer p, InteractionHand hand) {
        h.assertValueEqual(OriItems.SPIRIT_SPEAR.get().use(p.level(), p, hand).getResult(), InteractionResult.CONSUME, "Valid press refused");
        h.assertTrue(p.isUsingItem(), "Valid press did not raise spear");
    }
    private static void tick(ServerPlayer p) { SpiritCombat.tick(new PlayerTickEvent.Post(p)); }
    private static List<SpiritSpear> spears(ServerPlayer p) {
        return p.level().getEntitiesOfClass(SpiritSpear.class, p.getBoundingBox().inflate(80), s -> s.getOwner() == p);
    }
    private static void untouched(GameTestHelper h, ServerPlayer p, float energy) {
        Fixtures.near(h, p.getData(SpiritData.ENERGY), energy, "Spent energy without a throw");
        h.assertTrue(spears(p).isEmpty(), "Threw before release/minimum or after cancel");
    }
    private static void firedOnce(GameTestHelper h, ServerPlayer p) {
        Fixtures.near(h, p.getData(SpiritData.ENERGY), 100 - SpiritSpear.COST, "Throw spending");
        h.assertValueEqual(spears(p).size(), 1, "Throw lost or duplicated");
        h.assertTrue(p.getCooldowns().isOnCooldown(OriItems.SPIRIT_SPEAR.get()), "Throw did not start cooldown");
    }
    private static void clean(ServerPlayer p) { spears(p).forEach(SpiritSpear::discard); }
    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("r117_spear", "r117_spear." + name, "orimod_r117:room", 100, 0, true, run));
    }
}
