package cn.wwl.orimod.gametest;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.gametest.framework.*;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod("orimod_gametest")
public final class OriGameTests {
    private static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems("orimod_gametest");
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.ElytraItem> OTHER_ELYTRA = ITEMS.register("other_elytra",
            () -> new net.minecraft.world.item.ElytraItem(new net.minecraft.world.item.Item.Properties().durability(432)));
    public OriGameTests(IEventBus bus) {
        ITEMS.register(bus);
        if (Boolean.getBoolean("orimod.worldgen.audit")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(WorldgenAudit::started);
            return;
        }
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            try {
                var path = Path.of(System.getProperty("orimod.gametest.report", "gametest.xml"));
                if (path.getParent() != null) Files.createDirectories(path.getParent());
                var xml = new JUnitLikeTestReporter(path.toFile());
                var log = new LogTestReporter();
                GlobalTestReporter.replaceWith(new TestReporter() {
                    public void onTestFailed(GameTestInfo test) { log.onTestFailed(test); xml.onTestFailed(test); }
                    public void onTestSuccess(GameTestInfo test) { log.onTestSuccess(test); xml.onTestSuccess(test); }
                    public void finish() { log.finish(); xml.finish(); }
                });
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot initialize GameTest XML report", exception);
            }
        }));
    }
}
