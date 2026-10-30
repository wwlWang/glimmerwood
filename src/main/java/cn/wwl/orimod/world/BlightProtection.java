package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Loaded-only runtime protection, independent of generation guards. DEFER never means permission to write. */
public final class BlightProtection {
    public enum Result { ALLOW, PROTECTED, DEFER }
    public enum Reason { NONE, ARTIFICIAL, STRUCTURE, HEART, SANCTUARY, GREAT_TREE, HEIGHT, WAIT }
    public record Check(Result result,Reason reason){}
    public static final TagKey<Block> NATURAL=tag("blight_natural"),ARTIFICIAL=tag("blight_artificial"),SOIL=tag("blight_convertible_soil");
    private static TagKey<Block> tag(String name){return TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("orimod",name));}
    public static boolean artificial(BlockState state){
        if(state.is(Blocks.BEE_NEST))return false; // Natural nests remain untouched; conversion still rejects every BE.
        return state.hasBlockEntity()||state.is(ARTIFICIAL)||state.hasProperty(LeavesBlock.PERSISTENT)&&state.getValue(LeavesBlock.PERSISTENT)||!state.is(NATURAL);
    }
    public static boolean soil(BlockState state){return !state.hasBlockEntity()&&state.is(SOIL)&&!artificial(state);}
    private static boolean heart(BlockState state){return state.is(OriBlocks.SPIRIT_TREE_HEART)||state.is(OriBlocks.GREAT_TREE_HEART);}
    private static final class HeartSection {
        final LevelChunkSection section;final List<BlockPos> positions=new ArrayList<>();int cursor;
        HeartSection(LevelChunkSection section){this.section=section;}
    }
    private static final Map<ServerLevel,LinkedHashMap<Long,HeartSection>> HEARTS=new IdentityHashMap<>();
    private record PieceQuery(StructureStart start,BlockPos at){PieceQuery{at=at.immutable();}}
    private static final class PieceScan {int cursor;Result result;}
    private static final Map<ServerLevel,LinkedHashMap<PieceQuery,PieceScan>> PIECES=new IdentityHashMap<>();
    public static void invalidate(ServerLevel level,BlockPos pos){var cache=HEARTS.get(level);if(cache!=null)cache.remove(SectionPos.asLong(pos));}
    public static void clear(ServerLevel level){HEARTS.remove(level);PIECES.remove(level);}
    public static void clear(){HEARTS.clear();PIECES.clear();}
    public static void unload(ServerLevel level,ChunkPos chunk){var cache=HEARTS.get(level);if(cache!=null)cache.keySet().removeIf(p->SectionPos.x(p)==chunk.x&&SectionPos.z(p)==chunk.z);
        var pieces=PIECES.get(level);if(pieces!=null)pieces.keySet().removeIf(q->q.start().getChunkPos().equals(chunk)||new ChunkPos(q.at()).equals(chunk));}
    public static Result check(ServerLevel level,BlockPos floor,BlightEcologyRules.Budget budget){
        return inspect(level,floor,null,budget).result;
    }
    public static Check inspect(ServerLevel level,BlockPos floor,BlockPos ownCore,BlightEcologyRules.Budget budget){
        if(level.isOutsideBuildHeight(floor))return new Check(Result.PROTECTED,Reason.HEIGHT);
        if(BlightData.get(level).ecologyProtects(floor))return new Check(Result.PROTECTED,Reason.SANCTUARY);
        if(GreatTreeSites.reserved(level.getChunkSource().randomState(),floor.getX(),floor.getZ(),0))return new Check(Result.PROTECTED,Reason.GREAT_TREE);
        var structures=structures(level,floor,budget);if(structures!=Result.ALLOW)return new Check(structures,structures==Result.DEFER?Reason.WAIT:Reason.STRUCTURE);
        var hearts=hearts(level,floor,budget);if(hearts!=Result.ALLOW)return new Check(hearts,hearts==Result.DEFER?Reason.WAIT:Reason.HEART);
        var artificial=artificialNearby(level,floor,ownCore,budget);
        return new Check(artificial,artificial==Result.ALLOW?Reason.NONE:artificial==Result.DEFER?Reason.WAIT:Reason.ARTIFICIAL);
    }
    /** An inconclusive scan does not leave a reusable partial answer. All suspected cells fit within 441 reads. */
    public static Result artificialNearby(ServerLevel level,BlockPos floor,BlightEcologyRules.Budget budget){
        return artificialNearby(level,floor,null,budget);
    }
    public static Result artificialNearby(ServerLevel level,BlockPos floor,BlockPos ownCore,BlightEcologyRules.Budget budget){
        int minX=floor.getX()-3,maxX=floor.getX()+3,minZ=floor.getZ()-3,maxZ=floor.getZ()+3;
        int minY=Math.max(level.getMinBuildHeight(),floor.getY()-2),maxY=Math.min(level.getMaxBuildHeight()-1,floor.getY()+6);
        record Slice(LevelChunkSection section,int minX,int maxX,int minY,int maxY,int minZ,int maxZ){}
        var slices=new ArrayList<Slice>();int readsNeeded=0;
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
            if(!budget.index())return Result.DEFER;
            var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null){budget.missingChunk();return Result.DEFER;}
            for(int sy=minY>>4;sy<=maxY>>4;sy++){
                if(!budget.index())return Result.DEFER;
                var section=chunk.getSection(chunk.getSectionIndex(sy<<4));
                if(!section.maybeHas(BlightProtection::artificial))continue;
                var slice=new Slice(section,Math.max(minX,cx<<4),Math.min(maxX,(cx<<4)+15),Math.max(minY,sy<<4),Math.min(maxY,(sy<<4)+15),Math.max(minZ,cz<<4),Math.min(maxZ,(cz<<4)+15));
                slices.add(slice);readsNeeded+=(slice.maxX-slice.minX+1)*(slice.maxY-slice.minY+1)*(slice.maxZ-slice.minZ+1);
            }
        }
        if(readsNeeded>budget.remainingReads())return Result.DEFER;
        for(var s:slices)for(int y=s.minY;y<=s.maxY;y++)for(int z=s.minZ;z<=s.maxZ;z++)for(int x=s.minX;x<=s.maxX;x++){
            if(!budget.read())return Result.DEFER;
            var state=s.section.getBlockState(x&15,y&15,z&15);
            if(ownCore!=null&&ownCore.getX()==x&&ownCore.getY()==y&&ownCore.getZ()==z&&state.is(OriBlocks.GLOOM_CORE))continue;
            if(artificial(state))return Result.PROTECTED;
        }
        return Result.ALLOW;
    }
    /** Only existing reference maps/starts; never StructureManager queries that may load a start chunk. */
    public static Result structures(ServerLevel level,BlockPos at,BlightEcologyRules.Budget budget){
        var seen=new HashSet<StructureStart>();
        for(int cx=(at.getX()-2)>>4;cx<=(at.getX()+2)>>4;cx++)for(int cz=(at.getZ()-2)>>4;cz<=(at.getZ()+2)>>4;cz++){
            if(!budget.index())return Result.DEFER;
            var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null){budget.missingChunk();return Result.DEFER;}
            for(var start:chunk.getAllStarts().values()){
                if(!budget.index())return Result.DEFER;
                if(seen.add(start)){var result=protects(level,start,at,budget);if(result!=Result.ALLOW)return result;}
            }
            for(var entry:chunk.getAllReferences().entrySet()){
                if(blight(entry.getKey()))continue;
                for(long packed:entry.getValue()){
                    if(!budget.index())return Result.DEFER;
                    var origin=level.getChunkSource().getChunkNow(ChunkPos.getX(packed),ChunkPos.getZ(packed));
                    if(origin==null){budget.missingChunk();return Result.DEFER;}
                    var start=origin.getStartForStructure(entry.getKey());if(start==null){budget.missingChunk();return Result.DEFER;}
                    if(seen.add(start)){var result=protects(level,start,at,budget);if(result!=Result.ALLOW)return result;}
                }
            }
        }
        return Result.ALLOW;
    }
    private static boolean blight(Structure structure){return structure instanceof FeatureSiteStructure site&&site.site==FeatureSiteStructure.Site.BLIGHTED_GROUNDS;}
    private static Result protects(ServerLevel level,StructureStart start,BlockPos at,BlightEcologyRules.Budget budget){
        if(!start.isValid()||blight(start.getStructure())||!protects(start.getBoundingBox(),at))return Result.ALLOW;
        // The start box includes terrain-adaptation padding and empty space between pieces.
        var cache=PIECES.computeIfAbsent(level,k->new LinkedHashMap<>(64,.75f,true));var query=new PieceQuery(start,at);
        var scan=cache.computeIfAbsent(query,k->new PieceScan());while(cache.size()>1024)cache.remove(cache.keySet().iterator().next());
        if(scan.result!=null)return scan.result;
        var pieces=start.getPieces();while(scan.cursor<pieces.size()){
            if(!budget.index())return Result.DEFER;
            if(protects(pieces.get(scan.cursor++).getBoundingBox(),at))return scan.result=Result.PROTECTED;
        }
        return scan.result=Result.ALLOW;
    }
    public static boolean protects(BoundingBox box,BlockPos at){
        return at.getX()>=box.minX()-2&&at.getX()<=box.maxX()+2&&at.getZ()>=box.minZ()-2&&at.getZ()<=box.maxZ()+2&&at.getY()>=box.minY()&&at.getY()<=box.maxY();
    }
    /** Rare heart-bearing palettes are indexed incrementally; ordinary sections cost no block reads.
     * The existing heart onPlace/onRemove invalidation also invalidates this cache. */
    public static Result hearts(ServerLevel level,BlockPos at,BlightEcologyRules.Budget budget){
        int r=BlightRules.SANCTUARY_RADIUS,minY=Math.max(level.getMinBuildHeight(),at.getY()-r),maxY=Math.min(level.getMaxBuildHeight()-1,at.getY()+r);
        var cache=HEARTS.computeIfAbsent(level,k->new LinkedHashMap<>(64,.75f,true));
        for(int cx=(at.getX()-r)>>4;cx<=(at.getX()+r)>>4;cx++)for(int cz=(at.getZ()-r)>>4;cz<=(at.getZ()+r)>>4;cz++){
            if(!touches(at,cx,at.getY()>>4,cz,r))continue;
            var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null){budget.missingChunk();return Result.DEFER;}
            for(int sy=minY>>4;sy<=maxY>>4;sy++){
                if(!touches(at,cx,sy,cz,r))continue;
                if(!budget.index())return Result.DEFER;
                var section=chunk.getSection(chunk.getSectionIndex(sy<<4));long key=SectionPos.asLong(cx,sy,cz);
                if(!section.maybeHas(BlightProtection::heart)){cache.remove(key);continue;}
                var entry=cache.get(key);
                if(entry==null||entry.section!=section){entry=new HeartSection(section);cache.put(key,entry);while(cache.size()>1024)cache.remove(cache.keySet().iterator().next());}
                for(var p:entry.positions){if(!budget.index())return Result.DEFER;if(p.distSqr(at)<=r*r)return Result.PROTECTED;}
                while(entry.cursor<4096){
                    if(!budget.read())return Result.DEFER;
                    int i=entry.cursor++,x=i&15,z=(i>>4)&15,y=i>>8;
                    if(heart(section.getBlockState(x,y,z))){var p=new BlockPos((cx<<4)+x,(sy<<4)+y,(cz<<4)+z);entry.positions.add(p);if(p.distSqr(at)<=r*r)return Result.PROTECTED;}
                }
            }
        }
        return Result.ALLOW;
    }
    private static boolean touches(BlockPos at,int cx,int cy,int cz,int radius){
        int dx=at.getX()-Math.clamp(at.getX(),cx<<4,(cx<<4)+15),dy=at.getY()-Math.clamp(at.getY(),cy<<4,(cy<<4)+15),dz=at.getZ()-Math.clamp(at.getZ(),cz<<4,(cz<<4)+15);
        return dx*dx+dy*dy+dz*dz<=radius*radius;
    }
    private BlightProtection(){}
}
