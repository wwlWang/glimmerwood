package cn.wwl.orimod.client;

import cn.wwl.orimod.OriMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = OriMod.MOD_ID, dist = Dist.CLIENT)
public final class OriModClient {
    public OriModClient(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        // Mods list -> Config: NeoForge's generated screen over orimod-client.toml; edits save to the same file.
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mod, parent) -> new net.neoforged.neoforge.client.gui.ConfigurationScreen(mod, parent,
                        (context, key, element) -> cn.wwl.orimod.config.OriClientConfig.HIDDEN_IN_MENU.contains(key) ? null : element));
        modEventBus.addListener(OriModClient::configLoaded);
        modEventBus.addListener(OriModClient::configReloaded);
        modEventBus.addListener(cn.wwl.orimod.client.fx.SpiritScreenFeedback::registerLayers);
        modEventBus.addListener(cn.wwl.orimod.client.fx.SpiritFxResources::register);
        modEventBus.addListener(cn.wwl.orimod.client.fx.SpiritParticle::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.SpiritScreenFeedback::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.SpiritScreenFeedback::camera);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.SpiritFxClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.LightOrbGlow::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.MotionFeel::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.MotionFeel::fov);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.SpiritFxClient::loggingOut);
        modEventBus.addListener(cn.wwl.orimod.client.render.SpiritWeaponRenderer::register);
        modEventBus.addListener(SpiritControls::registerKeys);
        modEventBus.addListener(SpiritTargetOverlay::register);
        modEventBus.addListener(SpiritCombatClient::register);
        modEventBus.addListener(RuinGuideOverlay::register);
        modEventBus.addListener(cn.wwl.orimod.client.render.PracticeTargetRenderer::register);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(cn.wwl.orimod.world.OriEntities.LIGHT_BURST.get(), net.minecraft.client.renderer.entity.NoopRenderer::new));
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLOOM_SPITTER.get(), GloomSpitterRenderer::new);
            event.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLOOM_HEART.get(), GloomHeartRenderer::new);
            event.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLOOM_HAWK.get(), GloomHawkRenderer::new);
            event.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLOOM_MATRIARCH.get(), GloomMatriarchRenderer::new);
            event.registerEntityRenderer(cn.wwl.orimod.world.OriEntities.SPIRIT_SPEAR.get(), cn.wwl.orimod.client.render.SpiritSpearRenderer::new);
            event.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLOOM_SHOT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritCombatClient::input);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritCombatClient::opening);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritCombatClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritControls::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritControls::opening);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritControls::movement);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritTargetOverlay::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritTargetOverlay::tickTarget);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritTargetOverlay::renderWorld);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.SpiritAura::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritAppearance::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritGlow::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritHands::emptyArm);
        modEventBus.addListener(this::onClientSetup);
    }

    private static void configLoaded(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == cn.wwl.orimod.config.OriClientConfig.SPEC) cn.wwl.orimod.config.OriClientConfig.upgrade(OriMod.LOGGER);
    }

    private static void configReloaded(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == cn.wwl.orimod.config.OriClientConfig.SPEC) cn.wwl.orimod.config.OriClientConfig.upgrade(OriMod.LOGGER);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(cn.wwl.orimod.client.render.SpiritPlayerVisual::register);
        event.enqueueWork(() -> {
            var bow = cn.wwl.orimod.item.OriItems.SPIRIT_BOW.get();
            net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack ? entity.getTicksUsingItem() / 20f : 0);
            net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        });
        OriMod.LOGGER.info("OriMod client setup ready");
    }
}
