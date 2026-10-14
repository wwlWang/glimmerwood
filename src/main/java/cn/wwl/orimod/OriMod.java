package cn.wwl.orimod;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(OriMod.MOD_ID)
public final class OriMod {
    public static final String MOD_ID = "orimod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OriMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("OriMod common setup ready");
    }
}
