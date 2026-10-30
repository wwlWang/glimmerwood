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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritHud::hideVanilla);
        modEventBus.addListener(SpiritHud::register);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(cn.wwl.orimod.block.OriBlockEntities.SANCTUARY.get(), cn.wwl.orimod.client.render.SanctuaryRenderer::new));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.DoorOpening::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.DoorOpening::logout);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(cn.wwl.orimod.world.OriEntities.RECOVERY_ORB.get(), RecoveryOrbRenderer::new));
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
        modEventBus.addListener(cn.wwl.orimod.client.render.SanctuaryItemRenderer::register);
        modEventBus.addListener(GloomFluidVisual::register);
        modEventBus.addListener(SpiritControls::registerKeys);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkillControls::input);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkillControls::frame);
        modEventBus.addListener(SpiritTargetOverlay::register);
        modEventBus.addListener(SpiritCombatClient::register);
        modEventBus.addListener(RuinGuideOverlay::register);
        modEventBus.addListener(RestorationClient::register);
        modEventBus.addListener(GinsoEscapeClient::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GinsoEscapeClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LumenSight::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GinsoEscapeClient::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GinsoEscapeClient::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RestorationClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RestorationClient::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RestorationClient::render);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.WILL_WISP.get(),WillWispRenderer::new));
        modEventBus.addListener(CompanionLineOverlay::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CompanionLineOverlay::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CompanionLineOverlay::logout);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLADES_MERCHANT.get(),GladesMerchantRenderer::new));
        modEventBus.addListener(cn.wwl.orimod.client.render.PracticeTargetRenderer::register);
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(cn.wwl.orimod.world.OriEntities.LIGHT_BURST.get(), net.minecraft.client.renderer.entity.NoopRenderer::new));
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(cn.wwl.orimod.entity.OriMobs.GLOOM_BEETLE.get(), GloomBeetleRenderer::new);
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.BladeSlash::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.SpiritAura::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritAppearance::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritGlow::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritHands::emptyArm);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.GloomTideClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.GloomTideClient::fog);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.GloomTideClient::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.BloomSparkClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.fx.BloomSparkClient::logout);
        modEventBus.addListener(this::onClientSetup);
    }

    private static void configLoaded(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == cn.wwl.orimod.config.OriClientConfig.SPEC) cn.wwl.orimod.config.OriClientConfig.upgrade(OriMod.LOGGER);
    }

    private static void configReloaded(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == cn.wwl.orimod.config.OriClientConfig.SPEC) cn.wwl.orimod.config.OriClientConfig.upgrade(OriMod.LOGGER);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(GloomFluidVisual::setup);
        event.enqueueWork(FeatherItemAppearance::register);
        event.enqueueWork(cn.wwl.orimod.client.render.SpiritPlayerVisual::register);
        event.enqueueWork(() -> { cn.wwl.orimod.client.render.GloomCreatureVisual.register(); });
        event.enqueueWork(() -> {
            var bow = cn.wwl.orimod.item.OriItems.SPIRIT_BOW.get();
            net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack ? entity.getTicksUsingItem() / 20f : 0);
            net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
            net.minecraft.client.renderer.item.ItemProperties.register(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR.get(),
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("throwing"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        });
        OriMod.LOGGER.info("OriMod client setup ready");
    }
}
