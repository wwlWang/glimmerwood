package cn.wwl.orimod.client;

import cn.wwl.orimod.OriMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = OriMod.MOD_ID, dist = Dist.CLIENT)
public final class OriModClient {
    public OriModClient(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        OriMod.LOGGER.info("OriMod client setup ready");
    }
}
