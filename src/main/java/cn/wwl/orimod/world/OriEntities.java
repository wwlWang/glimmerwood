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
    public static final Supplier<EntityType<cn.wwl.orimod.entity.RecoveryOrb>> RECOVERY_ORB = TYPES.register("recovery_orb", () -> EntityType.Builder
            .<cn.wwl.orimod.entity.RecoveryOrb>of(cn.wwl.orimod.entity.RecoveryOrb::new, MobCategory.MISC).sized(.25f, .25f).clientTrackingRange(6).updateInterval(2).build("orimod:recovery_orb"));
    public static final Supplier<EntityType<PracticeTarget>> PRACTICE_TARGET = TYPES.register("practice_target", () -> EntityType.Builder
            .of(PracticeTarget::new, MobCategory.MISC).sized(.9f, 1.4f).clientTrackingRange(8).build("orimod:practice_target"));
    /** R39: thrown Light Burst orb. */
    public static final Supplier<EntityType<cn.wwl.orimod.combat.LightBurst>> LIGHT_BURST = TYPES.register("light_burst", () -> EntityType.Builder
            .<cn.wwl.orimod.combat.LightBurst>of(cn.wwl.orimod.combat.LightBurst::new, MobCategory.MISC).sized(.35f, .35f).clientTrackingRange(8).updateInterval(2).build("orimod:light_burst"));
    /** R60: thrown Spirit Spear. */
    public static final Supplier<EntityType<cn.wwl.orimod.combat.SpiritSpear>> SPIRIT_SPEAR = TYPES.register("spirit_spear", () -> EntityType.Builder
            .<cn.wwl.orimod.combat.SpiritSpear>of(cn.wwl.orimod.combat.SpiritSpear::new, MobCategory.MISC).sized(.3f, .3f).clientTrackingRange(8).updateInterval(1).build("orimod:spirit_spear"));
    public static void register(IEventBus bus) { TYPES.register(bus); bus.addListener(OriEntities::attributes); }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(PRACTICE_TARGET.get(), PracticeTarget.attributes().build()); }
    private OriEntities() {}
}
