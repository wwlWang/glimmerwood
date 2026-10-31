package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One overworld-owned choice, saved independently of structure chunks and player discovery. */
public final class GreatTreeSite extends SavedData {
    public static final int SEARCH_VERSION=1;
    public record Site(BlockPos origin, BlockPos spawn, long shapeSeed, int shapeVersion) {
        public double spawnDistance() { return Math.hypot(origin.getX()-spawn.getX(),origin.getZ()-spawn.getZ()); }
        public boolean covers(int x,int z,int margin) {
            return Math.abs(x-origin.getX()) <= GreatTreeShape.radius(shapeVersion)+margin && Math.abs(z-origin.getZ()) <= GreatTreeShape.radius(shapeVersion)+margin;
        }
    }
    private Site site;
    private boolean begun;
    private int searchVersion, completedRange;
    private boolean noSite;
    public Site site() { return site; }
    public boolean begun() { return begun; }
    public void choose(Site value) { site=value; begun=false; noSite=false; setDirty(); }
    public boolean noSite() { return searchVersion==SEARCH_VERSION && noSite; }
    public boolean needsSearch() { return site==null && !begun && !noSite(); }
    public int completedRange() { return searchVersion==SEARCH_VERSION ? completedRange : 0; }
    public void searched(int range) {
        searchVersion=SEARCH_VERSION; completedRange=range; noSite=site==null && range>=2500; setDirty();
    }
    public void begun(boolean value) { if(value && !begun){begun=true;setDirty();} }
    public static GreatTreeSite get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(GreatTreeSite::new,GreatTreeSite::load,null),"orimod_great_tree");
    }
    public static GreatTreeSite load(CompoundTag tag, HolderLookup.Provider provider) {
        var data=new GreatTreeSite();
        if(tag.contains("Origin")) data.site=new Site(BlockPos.of(tag.getLong("Origin")),BlockPos.of(tag.getLong("Spawn")),tag.getLong("ShapeSeed"),tag.getInt("ShapeVersion"));
        data.begun=tag.getBoolean("Begun");
        data.searchVersion=tag.getInt("SearchVersion");data.completedRange=tag.getInt("CompletedRange");data.noSite=tag.getBoolean("NoSite");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if(site!=null){tag.putLong("Origin",site.origin.asLong());tag.putLong("Spawn",site.spawn.asLong());tag.putLong("ShapeSeed",site.shapeSeed);tag.putInt("ShapeVersion",site.shapeVersion);}
        tag.putBoolean("Begun",begun);tag.putInt("SearchVersion",searchVersion);tag.putInt("CompletedRange",completedRange);tag.putBoolean("NoSite",noSite);return tag;
    }
}
