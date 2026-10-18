package cn.wwl.orimod.client;

import cn.wwl.orimod.OriMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = OriMod.MOD_ID, dist = Dist.CLIENT)
public final class OriModClient {
    public OriModClient(IEventBus modEventBus) {
        modEventBus.addListener(SpiritControls::registerKeys);
        modEventBus.addListener(SpiritTargetOverlay::register);
        modEventBus.addListener(SpiritCombatClient::register);
        modEventBus.addListener(RuinGuideOverlay::register);
        modEventBus.addListener(cn.wwl.orimod.client.render.PracticeTargetRenderer::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritCombatClient::input);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritCombatClient::opening);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritCombatClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritControls::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritControls::movement);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritTargetOverlay::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritTargetOverlay::tickTarget);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SpiritTargetOverlay::renderWorld);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritAppearance::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.client.render.SpiritHands::emptyArm);
        modEventBus.addListener(this::onClientSetup);
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
