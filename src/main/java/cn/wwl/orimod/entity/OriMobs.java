package cn.wwl.orimod.entity;

import cn.wwl.orimod.OriMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** R42: original spirit enemies. Natural spawns come from data/orimod/neoforge/biome_modifier. */
public final class OriMobs {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, OriMod.MOD_ID);
    public static final Supplier<EntityType<GloomSpitter>> GLOOM_SPITTER = TYPES.register("gloom_spitter", () -> EntityType.Builder
            .of(GloomSpitter::new, MobCategory.MONSTER).sized(1f, 1.1f).clientTrackingRange(8).build("orimod:gloom_spitter"));
    public static final Supplier<EntityType<GloomShot>> GLOOM_SHOT = TYPES.register("gloom_shot", () -> EntityType.Builder
            .<GloomShot>of(GloomShot::new, MobCategory.MISC).sized(.3f, .3f).clientTrackingRange(8).updateInterval(2).build("orimod:gloom_shot"));

    /** R50: first original boss. */
    public static final Supplier<EntityType<GloomHeart>> GLOOM_HEART = TYPES.register("gloom_heart", () -> EntityType.Builder
            .of(GloomHeart::new, MobCategory.MONSTER).sized(2.4f, 2.4f).fireImmune().clientTrackingRange(10).build("orimod:gloom_heart"));

    /** R61: original aerial threat. */
    public static final Supplier<EntityType<GloomHawk>> GLOOM_HAWK = TYPES.register("gloom_hawk", () -> EntityType.Builder
            .of(GloomHawk::new, MobCategory.MONSTER).sized(1.2f, .6f).clientTrackingRange(10).build("orimod:gloom_hawk"));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> { event.put(GLOOM_SPITTER.get(), GloomSpitter.attributes().build()); event.put(GLOOM_HEART.get(), GloomHeart.attributes().build()); event.put(GLOOM_HAWK.get(), GloomHawk.attributes().build()); });
        bus.addListener((RegisterSpawnPlacementsEvent event) -> event.register(GLOOM_SPITTER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        bus.addListener((RegisterSpawnPlacementsEvent event) -> event.register(GLOOM_HAWK.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }
    private OriMobs() {}
}
