package cn.wwl.orimod.block;

import cn.wwl.orimod.OriMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Short, non-renewable corruption flows. No bucket: purification remains the way to reclaim it. */
public final class OriFluids {
    public static final int SLOPE_DISTANCE = 2, LEVEL_DECREASE = 2, TICK_DELAY = 10;
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, OriMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, OriMod.MOD_ID);
    public static final DeferredHolder<FluidType, FluidType> GLOOM_TYPE = TYPES.register("gloom_water", () -> new FluidType(
            FluidType.Properties.create().descriptionId("block.orimod.gloom_water").canSwim(true).canDrown(true)
                    .supportsBoating(true).canExtinguish(true).canConvertToSource(false).lightLevel(4)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> GLOOM_WATER = FLUIDS.register("gloom_water", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_GLOOM_WATER = FLUIDS.register("flowing_gloom_water", () -> new BaseFlowingFluid.Flowing(properties()));

    /** NeoForge tracks custom FluidTypes separately: isInWater() only recognizes WATER_TYPE. */
    public static boolean inWater(net.minecraft.world.entity.Entity entity) {
        return entity.isInWaterOrBubble() || entity.isInFluidType(GLOOM_TYPE.get());
    }
    public static boolean underWater(net.minecraft.world.entity.Entity entity) {
        return entity.isUnderWater() || entity.isEyeInFluidType(GLOOM_TYPE.get());
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(GLOOM_TYPE, GLOOM_WATER, FLOWING_GLOOM_WATER).block(OriBlocks.GLOOM_WATER)
                .slopeFindDistance(SLOPE_DISTANCE).levelDecreasePerBlock(LEVEL_DECREASE).tickRate(TICK_DELAY).explosionResistance(100);
    }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); }
    private OriFluids() {}
}
