package cn.wwl.orimod.item;

import cn.wwl.orimod.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import java.util.*;
import java.util.function.BiPredicate;

/** Whole footprint participates in NeoForge's normal placement event and single item consumption. */
public final class SanctuaryItem extends BlockItem {
    public SanctuaryItem(Block block,Properties p){super(block,p);}
    @Override public InteractionResult place(BlockPlaceContext context){
        var stack=context.getItemInHand();var properties=stack.remove(DataComponents.BLOCK_STATE);
        try{return super.place(context);}finally{if(properties!=null)stack.set(DataComponents.BLOCK_STATE,properties);}
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context){
        var state=getBlock().defaultBlockState().setValue(SanctuaryBlock.ASSEMBLED,true)
                .setValue(SanctuaryBlock.FACING,context.getHorizontalDirection().getOpposite());
        return canPlace(context,state)&&preflight(context,state)?state:null;
    }
    private static boolean preflight(BlockPlaceContext c,BlockState state){
        var level=c.getLevel();var player=c.getPlayer();var owner=SanctuaryAssembly.owner(c.getClickedPos(),state);
        var collision=player==null?CollisionContext.empty():CollisionContext.of(player);
        for(var cell:SanctuaryShape.cells(owner.kind(),owner.facing())){
            var p=owner.core().offset(cell.offset());
            if(level.isOutsideBuildHeight(p)||!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p))return false;
            if(player!=null&&(!level.mayInteract(player,p)||!player.mayUseItemAt(p,c.getClickedFace(),c.getItemInHand())))return false;
            if(!level.getBlockState(p).canBeReplaced(BlockPlaceContext.at(c,p,c.getClickedFace())))return false;
            var placed=SanctuaryAssembly.state(owner,cell.offset(),state);
            if(!placed.canSurvive(level,p)||!level.isUnobstructed(placed,p,collision))return false;
        }
        return true;
    }
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state){return placeParts(context,state,(p,s)->context.getLevel().setBlock(p,s,Block.UPDATE_ALL));}
    /** Writer injection tests partial-write and exception rollback in a real level. */
    static boolean placeParts(BlockPlaceContext context,BlockState state,BiPredicate<BlockPos,BlockState> writer){
        if(!preflight(context,state))return false;
        var level=context.getLevel();var owner=SanctuaryAssembly.owner(context.getClickedPos(),state);
        var cells=SanctuaryShape.cells(owner.kind(),owner.facing());var snapshots=new ArrayList<BlockSnapshot>(cells.size());
        for(var c:cells)snapshots.add(BlockSnapshot.create(level.dimension(),level,owner.core().offset(c.offset()),Block.UPDATE_ALL));
        int captured=level.capturedBlockSnapshots.size();boolean complete=false;
        try{
            for(int i=0;i<cells.size();i++){
                var old=snapshots.get(i);var next=SanctuaryAssembly.state(owner,cells.get(i).offset(),state);
                if(level.getBlockState(old.getPos())!=old.getState()||!writer.test(old.getPos(),next)||level.getBlockState(old.getPos())!=next)return false;
            }
            for(int i=0;i<cells.size();i++)if(level.getBlockState(snapshots.get(i).getPos())!=SanctuaryAssembly.state(owner,cells.get(i).offset(),state))return false;
            complete=true;return true;
        }finally{
            if(!complete){boolean capture=level.captureBlockSnapshots,restore=level.restoringBlockSnapshots;
                level.captureBlockSnapshots=false;level.restoringBlockSnapshots=true;
                try{for(var old:snapshots.reversed()){
                    if(level.getBlockState(old.getPos())!=old.getState()){
                        if(!old.restore(Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Sanctuary rollback failed at "+old.getPos());
                    }else old.restoreBlockEntity(level,old.getPos());
                }}finally{level.captureBlockSnapshots=capture;level.restoringBlockSnapshots=restore;level.capturedBlockSnapshots.subList(captured,level.capturedBlockSnapshots.size()).clear();}
            }
        }
    }
}
