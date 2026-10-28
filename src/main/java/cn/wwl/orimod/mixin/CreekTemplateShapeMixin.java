package cn.wwl.orimod.mixin;

import cn.wwl.orimod.world.CreekShapeUpdates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StructureTemplate.class)
public abstract class CreekTemplateShapeMixin {
    @WrapOperation(method="placeInWorld",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState orimod$ownShape(BlockState state,LevelAccessor level,BlockPos pos,Operation<BlockState> original,
            @Local(argsOnly=true) StructurePlaceSettings settings){
        return CreekShapeUpdates.maySkip(state,settings)?state:original.call(state,level,pos);
    }
}
