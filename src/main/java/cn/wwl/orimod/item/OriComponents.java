package cn.wwl.orimod.item;

import cn.wwl.orimod.OriMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class OriComponents {
    private static final DeferredRegister<DataComponentType<?>> TYPES=DeferredRegister.create(Registries.DATA_COMPONENT_TYPE,OriMod.MOD_ID);
    public static final Supplier<DataComponentType<String>> KEYSTONE_RUIN=TYPES.register("keystone_ruin",()->DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    public static void register(IEventBus bus){TYPES.register(bus);}
    private OriComponents(){}
}
