package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Map;

/** Immutable siting output. No template loading, pieces, ring sampling or chunk requests. */
public record RuinDecision(RuinKind kind, int version, RuinPlacement placement,
                           List<Integer> approachHeights, Map<BlockPos,Integer> surfaceHeights) {
    public RuinDecision {
        approachHeights=List.copyOf(approachHeights);
        surfaceHeights=Map.copyOf(surfaceHeights);
        if(approachHeights.size()!=RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH)
            throw new IllegalArgumentException("Approach requires 45 heights");
    }
    /** BoundingBox is mutable; cache primitive records instead, returning fresh boxes on demand. */
    public record Box(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        public static Box of(BoundingBox b){return new Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
        public BoundingBox mutable(){return new BoundingBox(minX,minY,minZ,maxX,maxY,maxZ);}
        public boolean intersects(Box b,boolean horizontal){
            return maxX>=b.minX&&minX<=b.maxX&&maxZ>=b.minZ&&minZ<=b.maxZ
                    &&(horizontal||maxY>=b.minY&&minY<=b.maxY);
        }
    }
    public static List<Box> boxes(RuinKind kind,int version,RuinPlacement placement){
        var layout=kind.layout(version);
        var main=placement.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));
        if(version>0&&kind==RuinKind.FOREST)main=ForestRuinTerrain.bounds(main);
        if(version>0&&kind==RuinKind.CREEK)main=CreekRuinTerrain.bounds(main);
        var approach=RuinApproachPiece.bounds(RuinApproachPiece.doorway(kind,layout,placement),kind==RuinKind.CAVE&&version>0);
        return List.of(Box.of(main),Box.of(approach));
    }
    public List<Box> boxes(){return boxes(kind,version,placement);}
    public BlockPos entry(){return BlockPos.containing(placement.point(kind.layout(version).entry()));}
    public int[] approach(){return approachHeights.stream().mapToInt(Integer::intValue).toArray();}
    public Structure.GenerationStub stub(Structure.GenerationContext context){
        return new Structure.GenerationStub(entry(),builder->{
            // Only the start actually being built pays for its terrain snapshot.
            long start=Boolean.getBoolean("orimod.profileRuins")?System.nanoTime():0;
            int[] natural=kind==RuinKind.CREEK&&version>0?CreekRuinTerrain.sample(context,placement,surfaceHeights):
                    kind==RuinKind.FOREST&&version>0?ForestGradedTerrain.sample(context,placement,surfaceHeights):new int[0];
            long sampled=start==0?0:System.nanoTime();
            int[] terrain=approach();
            var main=new RuinPiece(context.structureTemplateManager(),kind,placement,version,
                    (kind==RuinKind.FOREST||kind==RuinKind.CREEK)&&version>0?terrain:new int[0],natural,kind==RuinKind.FOREST&&version>0?ForestGradedTerrain.VERSION:0);
            builder.addPiece(main);
            builder.addPiece(new RuinApproachPiece(RuinApproachPiece.doorway(kind,kind.layout(version),placement),terrain,kind==RuinKind.CAVE&&version>0,kind==RuinKind.FOREST&&version>0,main.forestApproachSideProfile()));
            long prepared=start==0?0:System.nanoTime();
            if(start!=0&&natural.length!=0){
                if(kind==RuinKind.FOREST)cn.wwl.orimod.OriMod.LOGGER.info("FOREST_NOISE_SNAPSHOT phase=STRUCTURE_STARTS api=WORLD_SURFACE_WG grid=8 snapshotEntries={} ms={}",natural.length,(sampled-start)/1e6);
                else cn.wwl.orimod.OriMod.LOGGER.info("CREEK_NOISE_SNAPSHOT phase=STRUCTURE_STARTS api=WORLD_SURFACE_WG grid=8 snapshotEntries={} ms={}",natural.length,(sampled-start)/1e6);
            }
            if(start!=0)cn.wwl.orimod.OriMod.LOGGER.info("RUIN_TEMPLATE kind={} version={} ms={}",kind.id,version,(prepared-sampled)/1e6);
        });
    }
}
