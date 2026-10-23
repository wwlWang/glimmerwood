package cn.wwl.orimod.world;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** Long leaf-to-ground distances exceed vanilla block-light range. No scan is needed for this backup. */
public final class GreatTreeSanctuary {
    public static boolean protects(GreatTreeSite.Site site,double x,double y,double z){
        return site!=null&&site.shapeVersion()>=2&&site.covers((int)Math.floor(x),(int)Math.floor(z),0)
                &&y>=site.origin().getY()-16&&y<=site.origin().getY()+GreatTreeShape.maxY(site.shapeVersion())+2;
    }
    public static boolean refuses(GreatTreeSite.Site site,MobSpawnType reason,boolean hostile,double x,double y,double z){
        return hostile&&(reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION)&&protects(site,x,y,z);
    }
    public static void spawn(MobSpawnEvent.PositionCheck event){
        var level=event.getLevel().getLevel();
        check(event,GreatTreeSites.site(level.getChunkSource().randomState()));
    }
    public static void check(MobSpawnEvent.PositionCheck event,GreatTreeSite.Site site){
        if(refuses(site,event.getSpawnType(),
                event.getEntity().getType().getCategory()==MobCategory.MONSTER,event.getX(),event.getY(),event.getZ()))
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
    private GreatTreeSanctuary(){}
}
