package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** Protect only the v2 course volume, using loaded structure references; never scan/load the world. */
public final class GinsoSanctuary {
    public static boolean protects(int courseVersion,BlockPos local){
        return courseVersion==2&&Math.abs(local.getX())<=6&&local.getZ()>=-10&&local.getZ()<=6&&local.getY()>=1&&local.getY()<=100;
    }
    public static boolean refuses(int version,BlockPos local,MobSpawnType reason,boolean hostile){
        return hostile&&(reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION)&&protects(version,local);
    }
    public static void spawn(MobSpawnEvent.PositionCheck event){
        if(event.getEntity().getType().getCategory()!=MobCategory.MONSTER||event.getSpawnType()!=MobSpawnType.NATURAL&&event.getSpawnType()!=MobSpawnType.CHUNK_GENERATION)return;
        var level=event.getLevel().getLevel();var pos=BlockPos.containing(event.getX(),event.getY(),event.getZ());
        var piece=GinsoCourse.at(level,pos);
        if(piece!=null&&protects(piece.courseVersion(),piece.local(pos)))event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
    private GinsoSanctuary(){}
}
