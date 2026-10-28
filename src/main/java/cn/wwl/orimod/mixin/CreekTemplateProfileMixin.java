package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.CreekPlacementTrace;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Disabled in normal games; no per-block probes or synchronous logs. */
@Mixin(StructureTemplate.class)
public abstract class CreekTemplateProfileMixin {
    @Inject(method="processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",at=@At("HEAD"))
    private static void orimod$processStart(CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> ci){CreekPlacementTrace.mark(0);}
    @Inject(method="processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",at=@At("RETURN"))
    private static void orimod$processEnd(CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> ci){CreekPlacementTrace.mark(1);}
    @Inject(method="updateShapeAtEdge(Lnet/minecraft/world/level/LevelAccessor;ILnet/minecraft/world/phys/shapes/DiscreteVoxelShape;III)V",at=@At("HEAD"))
    private static void orimod$edgeStart(CallbackInfo ci){CreekPlacementTrace.mark(2);}
    @Inject(method="updateShapeAtEdge(Lnet/minecraft/world/level/LevelAccessor;ILnet/minecraft/world/phys/shapes/DiscreteVoxelShape;III)V",at=@At("RETURN"))
    private static void orimod$edgeEnd(CallbackInfo ci){CreekPlacementTrace.mark(3);}
}
