package cn.wwl.orimod.block;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Fixed-offset ownership; never joins adjacent buildings or legacy blocks. */
public final class SanctuaryAssembly {
    private SanctuaryAssembly(){}
    public record Owner(BlockPos core,SanctuaryKind kind,Direction facing){}
    private record Removal(Level level,Owner owner){}
    private static final ThreadLocal<Set<Removal>> REMOVING=ThreadLocal.withInitial(HashSet::new);
    public static boolean assembled(BlockState s){return s.hasProperty(SanctuaryBlock.ASSEMBLED)&&s.getValue(SanctuaryBlock.ASSEMBLED);}
    public static boolean legacy(BlockState s){return !(s.getBlock() instanceof SanctuaryPartBlock)&&!assembled(s);}
    public static Owner owner(BlockPos pos,BlockState s){
        if(s.getBlock() instanceof SanctuaryPartBlock){
            var offset=SanctuaryPartBlock.offset(s);
            return new Owner(pos.subtract(SanctuaryShape.rotate(offset,s.getValue(SanctuaryBlock.FACING))),s.getValue(SanctuaryPartBlock.KIND),s.getValue(SanctuaryBlock.FACING));
        }
        return assembled(s)?new Owner(pos.immutable(),SanctuaryKind.of(s.getBlock()),s.getValue(SanctuaryBlock.FACING)):null;
    }
    public static BlockState coreState(Owner owner){return owner.kind.block().defaultBlockState().setValue(SanctuaryBlock.ASSEMBLED,true).setValue(SanctuaryBlock.FACING,owner.facing);}
    public static BlockState state(Owner owner,BlockPos offset,BlockState core){
        if(offset.equals(BlockPos.ZERO))return core;
        var p=SanctuaryShape.unrotate(offset,owner.facing);
        return OriBlocks.SANCTUARY_PART.get().defaultBlockState().setValue(SanctuaryPartBlock.KIND,owner.kind).setValue(SanctuaryBlock.FACING,owner.facing)
                .setValue(SanctuaryPartBlock.DX,p.getX()+1).setValue(SanctuaryPartBlock.DY,p.getY()).setValue(SanctuaryPartBlock.DZ,p.getZ()+1);
    }
    public static boolean matches(Owner owner,BlockPos pos,BlockState state){return owner.equals(owner(pos,state));}
    public static BlockState resolve(BlockGetter level,Owner owner){
        if(owner==null || level instanceof LevelReader reader&&!reader.hasChunkAt(owner.core))return null;
        var state=level.getBlockState(owner.core);return matches(owner,owner.core,state)?state:null;
    }
    public static void removed(BlockState old,Level level,BlockPos pos,BlockState next){
        if(level.isClientSide||level.restoringBlockSnapshots)return;
        var owner=owner(pos,old);if(owner==null||matches(owner,pos,next))return;
        var removal=new Removal(level,owner);var removing=REMOVING.get();if(!removing.add(removal))return;
        try{
            // Like the door, server removal may load the bounded 3x3 footprint at a chunk boundary.
            // Never infer missing ownership from an unloaded neighboring chunk.
            for(var cell:SanctuaryShape.cells(owner.kind,owner.facing)){
                var p=owner.core.offset(cell.offset());var s=level.getBlockState(p);
                if(matches(owner,p,s))level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            }
        }finally{removing.remove(removal);if(removing.isEmpty())REMOVING.remove();}
    }
    public static SanctuaryShape.Cell shape(BlockState state){
        var owner=owner(BlockPos.ZERO,state);var offset=BlockPos.ZERO.subtract(owner.core);
        return SanctuaryShape.cell(owner.kind,owner.facing,offset);
    }
}
