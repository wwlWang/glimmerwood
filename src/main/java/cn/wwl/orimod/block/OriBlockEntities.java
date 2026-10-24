package cn.wwl.orimod.block;
import cn.wwl.orimod.OriMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import java.util.function.Supplier;
public final class OriBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,OriMod.MOD_ID);
    public static final Supplier<BlockEntityType<GloomCoreBlockEntity>> GLOOM_CORE=TYPES.register("gloom_core",()->BlockEntityType.Builder.of(GloomCoreBlockEntity::new,OriBlocks.GLOOM_CORE.get()).build(null));
    public static final Supplier<BlockEntityType<SanctuaryBlockEntity>> SANCTUARY=TYPES.register("sanctuary",()->BlockEntityType.Builder.of(SanctuaryBlockEntity::new,
            OriBlocks.GLADES_WELL.get(), OriBlocks.FOREST_SHRINE.get(), OriBlocks.CREEK_SHRINE.get(), OriBlocks.CAVE_SHRINE.get(),
            OriBlocks.GLOOM_ALTAR.get(), OriBlocks.STORM_ALTAR.get(), OriBlocks.MAP_STONE.get(), OriBlocks.KEYSTONE_DOOR.get(), OriBlocks.GREAT_TREE_HEART.get(), OriBlocks.GINSO_WATER_HEART.get()).build(null));
    public static void register(IEventBus bus){TYPES.register(bus);}
    private OriBlockEntities(){}
}
