package cn.wwl.orimod.gametest;

import cn.wwl.orimod.command.OriCommands;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.SpiritCells;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class R116CoreGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (int occupied : new int[]{0, 18}) add(tests, "giveall_slots_" + occupied, h -> {
            var p = Fixtures.player(h);
            for (int i = 0; i < occupied; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            var before = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12)).size();
            OriCommands.giveAll(p);
            h.assertValueEqual(h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12)).size(), before, "Full inventory spilled giveall items");
            h.assertValueEqual((int)OriItems.ITEMS.getEntries().stream().filter(e -> p.getInventory().countItem(e.get()) > 0).count(), 36 - occupied, "Giveall did not stop at capacity");
            for (int i = 0; i < occupied; i++) h.assertValueEqual(p.getInventory().getItem(i).getCount(), 64, "Existing stack overwritten");
        });
        add(tests, "giveall_twice_equipment_and_resume", h -> {
            var p = Fixtures.player(h);
            p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(OriItems.SPIRIT_BLADE.get()));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(OriItems.SPIRIT_BOW.get()));
            OriCommands.giveAll(p);
            var first = p.getInventory().save(new net.minecraft.nbt.ListTag());
            OriCommands.giveAll(p);
            h.assertTrue(p.getInventory().save(new net.minecraft.nbt.ListTag()).equals(first), "Second giveall changed inventory");
            h.assertValueEqual(p.getInventory().countItem(OriItems.SPIRIT_BLADE.get()), 1, "Offhand item duplicated");
            h.assertValueEqual(p.getInventory().countItem(OriItems.SPIRIT_BOW.get()), 1, "Equipped item duplicated");
            var removed = p.getInventory().removeItemNoUpdate(0);
            OriCommands.giveAll(p);
            h.assertTrue(p.getInventory().contains(removed.getItem().getDefaultInstance()), "Missing item not replenished after freeing slot");
        });
        for (String command : List.of("ori cells life 10", "ori form on", "orimod energy fill")) add(tests, "feedback_" + command.replace(' ', '_'), h -> {
            var p = Fixtures.player(h); var feedback = new ArrayList<Component>();
            CommandSource sink = new CommandSource() {
                public void sendSystemMessage(Component message) { feedback.add(message); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            try {
                h.getLevel().getServer().getCommands().getDispatcher().execute(command, p.createCommandSourceStack().withPermission(2).withSource(sink));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
            h.assertTrue(feedback.stream().anyMatch(c -> c.getString().contains("/" + command)), "Feedback omitted real command: " + feedback);
        });
        for (float hp : new float[]{40, 27}) add(tests, "health_save_load_" + (int)hp, h -> {
            var p = Fixtures.player(h); OriCommands.cells(p, SpiritCells.Kind.LIFE, 10); p.setHealth(hp);
            var tag = p.saveWithoutId(new CompoundTag());
            var copy = Fixtures.player(h); copy.load(tag);
            // No logout hook, no repair before vanilla Health deserialization: covers autosave/restart.
            Fixtures.near(h, copy.getMaxHealth(), 40, "Saved max-health modifier missing before login");
            Fixtures.near(h, copy.getHealth(), hp, "NBT health clamped before login");
            SpiritCells.login(new PlayerEvent.PlayerLoggedInEvent(copy)); SpiritCells.login(new PlayerEvent.PlayerLoggedInEvent(copy));
            Fixtures.near(h, copy.getHealth(), hp, "Login healed or clamped saved health");
            h.assertValueEqual(copy.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream().filter(m -> m.id().equals(SpiritCells.MODIFIER)).count(), 1L, "Duplicate cell modifier");
        });
        add(tests, "health_legacy_clone_and_clamp", h -> {
            var p = Fixtures.player(h); OriCommands.cells(p, SpiritCells.Kind.LIFE, 10);
            p.getAttribute(Attributes.MAX_HEALTH).removeModifier(SpiritCells.MODIFIER); p.setHealth(20);
            var legacy = Fixtures.player(h); legacy.load(p.saveWithoutId(new CompoundTag()));
            SpiritCells.login(new PlayerEvent.PlayerLoggedInEvent(legacy));
            Fixtures.near(h, legacy.getMaxHealth(), 40, "Legacy save not upgraded"); Fixtures.near(h, legacy.getHealth(), 20, "Legacy upgrade invented lost health");
            var clone = Fixtures.player(h); clone.restoreFrom(legacy, false); SpiritCells.respawn(new PlayerEvent.PlayerRespawnEvent(clone, false));
            Fixtures.near(h, clone.getMaxHealth(), 40, "Clone lost cells"); Fixtures.near(h, clone.getHealth(), 40, "Respawn did not heal to cell cap");
            OriCommands.cells(clone, SpiritCells.Kind.LIFE, 3);
            Fixtures.near(h, clone.getHealth(), 26, "Reduced cap did not clamp");
            var copy = Fixtures.player(h); copy.load(clone.saveWithoutId(new CompoundTag()));
            Fixtures.near(h, copy.getMaxHealth(), 26, "Reduced modifier not saved"); Fixtures.near(h, copy.getHealth(), 26, "Reduced saved health lost");
        });
        add(tests, "energy_save_load", h -> {
            var p = Fixtures.player(h); OriCommands.cells(p, SpiritCells.Kind.ENERGY, 10);
            for (float energy : new float[]{200, 137}) {
                p.setData(SpiritData.ENERGY, energy); var copy = Fixtures.player(h); copy.load(p.saveWithoutId(new CompoundTag()));
                Fixtures.near(h, SpiritProgression.maxEnergy(copy), 200, "Energy cap lost");
                Fixtures.near(h, SpiritProgression.energy(copy), energy, "Energy clamped on load");
            }
        });
        return tests;
    }
    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("r116_core", "r116_core." + name, "orimod_gametest:room", 100, 0, true, h -> { run.accept(h); h.succeed(); }));
    }
}
