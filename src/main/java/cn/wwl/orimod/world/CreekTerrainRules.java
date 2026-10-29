package cn.wwl.orimod.world;

import java.util.List;

/** Frozen creek configuration of the shared, registry-free terrain planner. */
final class CreekTerrainRules extends RuinTerrainRules {
    CreekTerrainRules(int sizeX,int sizeZ,List<Ground> ground,int entranceX,int halfWidth,int rearFromZ){
        super(sizeX,sizeZ,ground,entranceX,halfWidth,rearFromZ);
    }
}
