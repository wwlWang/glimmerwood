package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One overworld-owned choice, saved independently of structure chunks and player discovery. */
public final class GreatTreeSite extends SavedData {
    public record Site(BlockPos origin, BlockPos spawn, long shapeSeed, int shapeVersion) {
        public double spawnDistance() { return Math.hypot(origin.getX()-spawn.getX(),origin.getZ()-spawn.getZ()); }
        public boolean covers(int x,int z,int margin) {
            return Math.abs(x-origin.getX()) <= GreatTreeShape.radius(shapeVersion)+margin && Math.abs(z-origin.getZ()) <= GreatTreeShape.radius(shapeVersion)+margin;
        }
    }
    private Site site;
    private boolean begun;
    public Site site() { return site; }
    public boolean begun() { return begun; }
    public void choose(Site value) { site=value; begun=false; setDirty(); }
    public void begun(boolean value) { if(value && !begun){begun=true;setDirty();} }
    public static GreatTreeSite get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(GreatTreeSite::new,GreatTreeSite::load,null),"orimod_great_tree");
    }
    public static GreatTreeSite load(CompoundTag tag, HolderLookup.Provider provider) {
        var data=new GreatTreeSite();
        if(tag.contains("Origin")) data.site=new Site(BlockPos.of(tag.getLong("Origin")),BlockPos.of(tag.getLong("Spawn")),tag.getLong("ShapeSeed"),tag.getInt("ShapeVersion"));
        data.begun=tag.getBoolean("Begun");return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if(site!=null){tag.putLong("Origin",site.origin.asLong());tag.putLong("Spawn",site.spawn.asLong());tag.putLong("ShapeSeed",site.shapeSeed);tag.putInt("ShapeVersion",site.shapeVersion);}
        tag.putBoolean("Begun",begun);return tag;
    }
}
