package cn.wwl.orimod.gametest;

import cn.wwl.orimod.command.OriCommands;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.SpiritCells;
import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/** R83: creative tab, item names/models and the /orimod playtest commands. */
@GameTestHolder("orimod_gametest")
public final class ContentGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "creative_tab_lists_every_item", h -> {
            var tab = OriItems.TAB.get();
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
            var shown = tab.getDisplayItems().stream().map(s -> s.getItem()).toList();
            int ours = 0;
            for (var item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (!id.getNamespace().equals("orimod")) continue;
                ours++;
                h.assertTrue(shown.contains(item), "creative tab misses " + id);
            }
            h.assertTrue(ours >= 30, "only " + ours + " orimod items registered");
        });
        add(tests, "every_item_has_name_and_model", h -> {
            for (String lang : new String[] {"en_us", "zh_cn"}) {
                var json = read(h, "/assets/orimod/lang/" + lang + ".json");
                for (var item : BuiltInRegistries.ITEM) {
                    var id = BuiltInRegistries.ITEM.getKey(item);
                    if (!id.getNamespace().equals("orimod")) continue;
                    h.assertTrue(json.has(item.getDescriptionId()), lang + " lacks " + item.getDescriptionId());
                    h.assertTrue(java.nio.file.Files.exists(resource("/assets/orimod/models/item/" + id.getPath() + ".json")), "no item model for " + id);
                }
            }
        });
        add(tests, "commands_need_permission_and_apply", h -> {
            var dispatcher = new CommandDispatcher<CommandSourceStack>();
            OriCommands.register(dispatcher);
            var p = Fixtures.player(h);
            p.setData(SpiritData.AWAKENED, false);
            var player = p.createCommandSourceStack().withPermission(0);
            h.assertTrue(dispatcher.parse("orimod unlock all", player).getReader().canRead(), "level 0 must not see /orimod");
            var op = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            try {
                dispatcher.execute("orimod unlock all", op);
                h.assertTrue(SpiritData.isAwakened(p), "unlock all awakens");
                h.assertValueEqual(p.getData(SpiritData.ABILITIES), OriCommands.allAbilities(), "all abilities");
                dispatcher.execute("ori cells life 5", op);
                h.assertValueEqual(SpiritCells.count(p, SpiritCells.Kind.LIFE), 5, "life cells");
                h.assertTrue(p.getMaxHealth() >= 30, "life cells raise max health");
                dispatcher.execute("ori form on", op);
                h.assertTrue(SpiritData.isSpirit(p), "form on");
                dispatcher.execute("ori energy fill", op);
                h.assertTrue(p.getData(SpiritData.ENERGY) >= 10, "energy filled");
                dispatcher.execute("ori light 250", op);
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(), 250, "light granted");
                int before = p.getInventory().items.stream().mapToInt(s -> s.isEmpty() ? 0 : 1).sum();
                dispatcher.execute("ori giveall", op);
                h.assertTrue(p.getInventory().items.stream().mapToInt(s -> s.isEmpty() ? 0 : 1).sum() > before + 20, "giveall");
                dispatcher.execute("ori unlock glide", op);
                dispatcher.execute("ori reset", op);
                h.assertTrue(!SpiritData.isAwakened(p) && !SpiritData.isSpirit(p) && p.getData(SpiritData.ABILITIES) == 0, "reset");
                h.assertValueEqual(SpiritCells.count(p, SpiritCells.Kind.LIFE), 0, "reset cells");
                boolean rejected = false;
                try { dispatcher.execute("ori spawn dragon", op); } catch (Exception expected) { rejected = true; }
                h.assertTrue(rejected, "unknown mob rejected");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        });
                add(tests, "spirit_form_cancels_fall_damage", h -> {
            var p = Fixtures.player(h);
            cn.wwl.orimod.spirit.SpiritForm.toggle(p);
            h.assertTrue(SpiritData.isSpirit(p), "spirit");
            p.fallDistance = 20;
            var event = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p, 20, 1);
            cn.wwl.orimod.movement.AirMovement.fall(event);
            h.assertTrue(event.isCanceled(), "fall canceled");
            h.assertValueEqual(p.fallDistance, 0f, "fallDistance cleared");
            // Human form still takes fall damage (event not canceled by our handler when not spirit — leave to vanilla).
            p.setData(SpiritData.FORM, false); p.fallDistance = 20;
            var human = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(p, 20, 1);
            cn.wwl.orimod.movement.AirMovement.fall(human);
            h.assertTrue(!human.isCanceled(), "human falls");
        });

        return tests;
    }

    private static com.google.gson.JsonObject read(GameTestHelper h, String path) {
        var file = resource(path);
        h.assertTrue(java.nio.file.Files.exists(file), "missing " + path);
        try { return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject(); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static java.nio.file.Path resource(String path) {
        return net.neoforged.fml.ModList.get().getModFileById("orimod").getFile().findResource(path.substring(1).split("/"));
    }

    static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("content", "content." + name, "orimod_gametest:room", 30, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
