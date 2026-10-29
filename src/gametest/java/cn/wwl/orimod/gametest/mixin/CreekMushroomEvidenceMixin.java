package cn.wwl.orimod.gametest.mixin;

import cn.wwl.orimod.gametest.CreekGuardEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomBlock.class)
abstract class CreekMushroomEvidenceMixin {
    @Inject(method="canSurvive",at=@At("RETURN"))
    private void orimod$survival(BlockState state,LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> ci){
        if(state.is(Blocks.BROWN_MUSHROOM)&&level instanceof WorldGenLevel)CreekGuardEvidence.mushroom("mushroom_survival",level,pos,ci.getReturnValue());
    }
}
