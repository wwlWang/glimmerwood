package cn.wwl.orimod.world;

import cn.wwl.orimod.OriMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class OriWorldgen {
    private static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, OriMod.MOD_ID);
    public static final Supplier<SpiritTreeFeature> SPIRIT_TREE = FEATURES.register("spirit_tree", SpiritTreeFeature::new);
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, OriMod.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, OriMod.MOD_ID);
    public static final Supplier<StructureType<RuinStructure>> RUIN = TYPES.register("ruin", () -> () -> RuinStructure.CODEC);
    public static final Supplier<StructurePieceType> PIECE = PIECES.register("ruin", () -> (StructurePieceType.StructureTemplateType) RuinPiece::new);
    public static final Supplier<StructurePieceType> APPROACH = PIECES.register("ruin_approach", () -> (StructurePieceType.ContextlessType) RuinApproachPiece::new);
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); FEATURES.register(bus); }
    private OriWorldgen() {}
}
