package cn.wwl.orimod.world;

import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/** ForestTerrainVersion=1 only. Existing foreign water/lava and their container
 * faces survive every clearing path; authored pool cells are identified exactly. */
final class ForestFluidProtection {
    static final ForestFluidProtection FOREIGN_ONLY=new ForestFluidProtection(Map.of());
    private static final Direction[] FACES={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP};
    private final Map<BlockPos,Fluid> authored;
    private ForestFluidProtection(Map<BlockPos,Fluid> authored){this.authored=Map.copyOf(authored);}
    static ForestFluidProtection at(ForestTerrainContract contract,RuinPlacement placement){
        var cells=new HashMap<BlockPos,Fluid>();
        contract.fluids.forEach((p,f)->cells.put(placement.block(p),f));
        return new ForestFluidProtection(cells);
    }
    private boolean foreign(BlockGetter level,BlockPos at){
        var f=level.getFluidState(at);
        if(!f.is(FluidTags.WATER)&&!f.is(FluidTags.LAVA))return false;
        var own=authored.get(at);
        return own==null||!f.getType().isSame(own);
    }
    boolean allows(BlockGetter level,BlockPos at,BlockState next){
        var old=level.getBlockState(at);
        if(old==next)return true;
        // Do not pave/fill a natural lake, either source or flowing cells.
        if(foreign(level,at))return false;
        if(old.isAir()||next.getFluidState().isEmpty()&&next.isCollisionShapeFullBlock(level,at))return true;
        for(var d:FACES)if(foreign(level,at.relative(d)))return false;
        return true;
    }
    boolean preservesColumn(BlockGetter level,BlockPos top,int surface){
        // Same test before and after adjacent slices: the rejected cells and
        // foreign fluid are never written, so their classification cannot flip.
        int lo=Math.min(surface,top.getY()),hi=Math.max(surface,top.getY());
        for(int y=lo;y<=hi;y++){
            var at=top.atY(y);
            if(foreign(level,at)||y>top.getY()&&!allows(level,at,Blocks.AIR.defaultBlockState()))return true;
        }
        return false;
    }
}
