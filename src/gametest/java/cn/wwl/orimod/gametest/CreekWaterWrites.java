package cn.wwl.orimod.gametest;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Test jar only, separate from acceptance timing. No work in normal gameplay. */
public final class CreekWaterWrites {
    private static final boolean ENABLED="creek_water_baseline".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private static final ConcurrentLinkedQueue<Change> CHANGES=new ConcurrentLinkedQueue<>();
    public record Change(List<Integer> origin,List<Integer> position,String before,String after) {}
    public static boolean write(WorldGenLevel level,BlockPos origin,BlockPos pos,BlockState state,int flags,Operation<Boolean> original){
        if(!ENABLED)return original.call(level,pos,state,flags);
        var before=level.getBlockState(pos);boolean changed=original.call(level,pos,state,flags);
        if(changed&&before.getFluidState().is(FluidTags.WATER)!=state.getFluidState().is(FluidTags.WATER))
            CHANGES.add(new Change(coords(origin),coords(pos),before.toString(),state.toString()));
        return changed;
    }
    private static List<Integer> coords(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    public static List<Change> snapshot(){return List.copyOf(CHANGES);}
    private CreekWaterWrites(){}
}
