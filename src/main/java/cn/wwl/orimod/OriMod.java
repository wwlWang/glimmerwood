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

    public OriMod(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, cn.wwl.orimod.config.OriClientConfig.SPEC);
        cn.wwl.orimod.block.OriBlocks.register(modEventBus);
        cn.wwl.orimod.world.OriWorldgen.register(modEventBus);
        cn.wwl.orimod.world.OriEntities.register(modEventBus);
        cn.wwl.orimod.fx.OriParticles.register(modEventBus);
        cn.wwl.orimod.item.OriItems.ITEMS.register(modEventBus);
        cn.wwl.orimod.recipe.OriRecipes.register(modEventBus);
        cn.wwl.orimod.spirit.SpiritData.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(OriServerLifecycle::stopping);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(OriServerLifecycle::stopped);
        modEventBus.addListener(cn.wwl.orimod.network.ToggleSpiritPayload::register);
        modEventBus.addListener(cn.wwl.orimod.movement.SpiritMovement::register);
        modEventBus.addListener(cn.wwl.orimod.network.ProgressActionPayload::register);
        modEventBus.addListener(cn.wwl.orimod.network.CombatInputPayload::register);
        modEventBus.addListener(cn.wwl.orimod.network.ChallengeActionPayload::register);
        modEventBus.addListener(cn.wwl.orimod.network.SpiritFxPayload::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.recipe.OriRecipes::difficultyChanged);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.world.SpiritChallenge::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.world.SpiritChallenge::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.world.SpiritChallenge::dimension);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.world.SpiritChallenge::death);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.world.RuinLocator::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.world.RuinLocator::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.combat.SpiritCombat::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.combat.SpiritCombat::attackEntity);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.combat.SpiritCombat::damaged);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.combat.SpiritCombat::changedDimension);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.combat.SpiritCombat::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.combat.SpiritCombat::tickArrow);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.progression.SpiritProgression::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.progression.SpiritProgression::drops);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.progression.SpiritProgression::pickup);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.progression.SpiritProgression::damaged);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.progression.SpiritProgression::changedDimension);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.spirit.SpiritForm::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.movement.SpiritMovement::tick);
        // Registered after movement so persistent effects follow this tick's authoritative state.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.fx.SpiritFxLifecycle::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.fx.SpiritFxLifecycle::death);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.fx.SpiritFxLifecycle::dimension);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.fx.SpiritFxLifecycle::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.fx.SpiritFxLifecycle::respawn);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.movement.AirMovement::fall);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.movement.BashSession::tickTarget);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.movement.BashSession::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cn.wwl.orimod.movement.BashSession::changedDimension);
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("OriMod common setup ready");
    }
}
