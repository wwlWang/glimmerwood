package cn.wwl.orimod.world;

import cn.wwl.orimod.OriMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class OriEntities {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, OriMod.MOD_ID);
    public static final Supplier<EntityType<PracticeTarget>> PRACTICE_TARGET = TYPES.register("practice_target", () -> EntityType.Builder
            .of(PracticeTarget::new, MobCategory.MISC).sized(.9f, 1.4f).clientTrackingRange(8).build("orimod:practice_target"));
    public static void register(IEventBus bus) { TYPES.register(bus); bus.addListener(OriEntities::attributes); }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(PRACTICE_TARGET.get(), PracticeTarget.attributes().build()); }
    private OriEntities() {}
}
