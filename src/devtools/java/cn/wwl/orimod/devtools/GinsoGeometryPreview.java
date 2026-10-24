package cn.wwl.orimod.devtools;

import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Explicit development fixture repair in a copied save, never part of the production jar. */
final class GinsoGeometryPreview {
    static Map<String,Object> restorePristine(ServerLevel level,BlockPos origin){
        var chunk=level.getChunkSource().getChunkNow(origin.getX()>>4,origin.getZ()>>4);
        if(chunk==null)throw new IllegalStateException("Load the selected fixture first");
        GinsoPiece found=null;
        for(var start:chunk.getAllStarts().values())for(var piece:start.getPieces())
            if(piece instanceof GinsoPiece g&&g.origin().equals(origin))found=g;
        if(found==null||found.shapeVersion()!=3||found.courseVersion()!=2||found.escapeVersion()!=0)
            throw new IllegalStateException("Requires a copied Shape3/Course2/Escape0 fixture; refuses an active escape tree");
        int radius=GinsoShape.radius(3);
        for(int x=(origin.getX()-radius)>>4;x<=(origin.getX()+radius)>>4;x++)for(int z=(origin.getZ()-radius)>>4;z<=(origin.getZ()+radius)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalStateException("Entire upper tree must already be loaded");
        var box=new BoundingBox(origin.getX()-radius,origin.getY()+93,origin.getZ()-radius,origin.getX()+radius,origin.getY()+124,origin.getZ()+radius);
        int cleared=0;
        for(var pos:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()))
            if(!level.getBlockState(pos).isAir()){level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);cleared++;}
        found.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(0),box,new ChunkPos(origin),origin);
        return Map.of("shapeSeed",found.shapeSeed(),"shapeVersion",3,"courseVersion",2,"escapeVersion",0,"cleared",cleared,
                "description","Copied save; original 3/2 upper sculpture and course reconstructed from frozen generators, no escape overlay");
    }
    private GinsoGeometryPreview(){}
}
