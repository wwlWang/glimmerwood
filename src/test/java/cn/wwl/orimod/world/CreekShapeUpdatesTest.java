package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreekShapeUpdatesTest {
    private static class Inherits extends Block { Inherits(){super(Properties.of());} }
    private static class Overrides extends Inherits {
        @Override protected BlockState updateShape(BlockState state,Direction d,BlockState other,LevelAccessor level,BlockPos p,BlockPos n){return state;}
    }
    private static class InheritsOverride extends Overrides {}
    @Test void onlyUntouchedBaseImplementationIsSkipped(){
        assertTrue(CreekShapeUpdates.unchanged(Block.class));assertTrue(CreekShapeUpdates.unchanged(Inherits.class));
        assertFalse(CreekShapeUpdates.unchanged(Overrides.class));assertFalse(CreekShapeUpdates.unchanged(InheritsOverride.class));
        for(var type:java.util.List.of(FenceBlock.class,WallBlock.class,LiquidBlock.class,VineBlock.class))assertFalse(CreekShapeUpdates.unchanged(type),type.getName());
    }
}
