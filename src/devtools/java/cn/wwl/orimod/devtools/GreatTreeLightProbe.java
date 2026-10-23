package cn.wwl.orimod.devtools;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import java.util.*;

/** Read-only, loaded chunks only. Block light is separate from sky light and emissive model brightness. */
final class GreatTreeLightProbe {
    static Map<String,Object> scan(ServerLevel level,GreatTreeSite.Site site){
        var points=new ArrayList<Map<String,Object>>();var histogram=new TreeMap<Integer,Integer>();
        var base=site.origin();var at=new BlockPos.MutableBlockPos();int r=GreatTreeShape.radius(site.shapeVersion())-8,missing=0,covered=0;
        for(int dx=-r;dx<=r;dx+=8)for(int dz=-r;dz<=r;dz+=8){
            int distance=dx*dx+dz*dz;if(distance<20*20||distance>r*r)continue;
            int x=base.getX()+dx,z=base.getZ()+dz;
            if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null){missing++;continue;}
            for(int y=base.getY()+36;y>=base.getY()-16;y--){
                at.set(x,y,z);if(!level.getBlockState(at).isFaceSturdy(level,at,Direction.UP)||!level.getBlockState(at.above()).isAir())continue;
                var feet=at.above();int light=level.getBrightness(LightLayer.BLOCK,feet);histogram.merge(light,1,Integer::sum);
                boolean safe=GreatTreeSanctuary.protects(site,x+.5,y+1,z+.5);if(safe)covered++;
                points.add(Map.of("pos",List.of(x,y+1,z),"block",light,"sky",level.getBrightness(LightLayer.SKY,feet),"naturalHostileSuppressed",safe));break;
            }
        }
        return Map.of("samples",points.size(),"missingChunks",missing,"minimumBlockLight",histogram.isEmpty()?-1:histogram.firstKey(),
                "atLeast8",histogram.entrySet().stream().filter(e->e.getKey()>=8).mapToInt(Map.Entry::getValue).sum(),"protected",covered,"histogram",histogram,"points",points);
    }
    private GreatTreeLightProbe(){}
}
