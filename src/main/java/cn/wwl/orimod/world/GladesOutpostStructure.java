package cn.wwl.orimod.world;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import java.util.*;

public final class GladesOutpostStructure extends Structure {
    public static final MapCodec<GladesOutpostStructure> CODEC=simpleCodec(GladesOutpostStructure::new);
    public GladesOutpostStructure(StructureSettings settings){super(settings);}
    public static RuinPlacement placement(BlockPos minimum,Rotation r){return new RuinPlacement(minimum.offset(switch(r){case CLOCKWISE_90,CLOCKWISE_180->30;default->0;},0,switch(r){case COUNTERCLOCKWISE_90,CLOCKWISE_180->30;default->0;}),r);}
    public static final int MAX_RELIEF=10;
    public enum Rejection { OK, TERRAIN_RELIEF, NOT_DRY, ENTRY_APPROACH, BUILD_HEIGHT, BIOME }
    public record Site(Rejection reason, RuinPlacement placement, int[] approach) {}
    public static boolean terrainFits(int min,int max){return max-min<=MAX_RELIEF && RuinSiteRules.surfaceInRange(min) && RuinSiteRules.surfaceInRange(max);}
    public static int floorHeight(int min,int max){return Math.floorDiv(min+max,2);}
    /** Noise-only preflight. Terrace cuts/fills at most five sampled blocks; saved stairs adapt the entry. */
    public Site preflight(GenerationContext c){
        var at=placement(c.chunkPos().getWorldPosition(),Rotation.getRandom(c.random()));
        var heights=new HashMap<BlockPos,Integer>();
        java.util.function.ToIntFunction<BlockPos> sample=p->heights.computeIfAbsent(p,q->height(c,q));
        var entry=at.block(GladesOutpostPiece.ENTRY);int surface=sample.applyAsInt(entry),min=surface,max=surface;
        var finalAt=new RuinPlacement(at.origin().atY(surface-3),at.rotation());
        var biome=c.biomeSource().getNoiseBiome(entry.getX()>>2,surface>>2,entry.getZ()>>2,c.randomState().sampler());
        if(!c.validBiome().test(biome))return new Site(Rejection.BIOME,finalAt,new int[0]);
        for(int x=0;x<=30;x+=5)for(int z=0;z<=30;z+=5){
            int y=sample.applyAsInt(at.block(new BlockPos(x,0,z)));min=Math.min(min,y);max=Math.max(max,y);
        }
        if(!terrainFits(min,max))return new Site(Rejection.TERRAIN_RELIEF,finalAt,new int[0]);
        surface=floorHeight(min,max);finalAt=new RuinPlacement(at.origin().atY(surface-3),at.rotation());
        int base=surface-3;if(base<c.heightAccessor().getMinBuildHeight() || base+18>c.heightAccessor().getMaxBuildHeight())return new Site(Rejection.BUILD_HEIGHT,finalAt,new int[0]);
        var door=new RuinPlacement(finalAt.block(GladesOutpostPiece.ENTRY),at.rotation());
        int[] approach=new int[RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH];
        for(int x=-2;x<=2;x++)for(int z=-9;z<=-1;z++){
            var p=door.block(new BlockPos(x,0,z));int y=sample.applyAsInt(p);approach[(x+2)*9+z+9]=y;
            if(!RuinSiteRules.approachOk(y,surface))return new Site(Rejection.ENTRY_APPROACH,finalAt,approach);
        }
        // Check fluids after cheap biome/relief rejection. Never flatten rivers or ocean floors.
        for(var h:heights.entrySet())if(!dry(c,h.getKey(),h.getValue()))return new Site(Rejection.NOT_DRY,finalAt,approach);
        return new Site(Rejection.OK,finalAt,approach);
    }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext c){
        if(GreatTreeSites.reserved(c.randomState(),c.chunkPos().getMiddleBlockX(),c.chunkPos().getMiddleBlockZ(),48))return Optional.empty();
        if(GinsoStructure.reserved(c,48))return Optional.empty();
        var site=preflight(c);if(site.reason()!=Rejection.OK)return Optional.empty();
        var door=site.placement().block(GladesOutpostPiece.ENTRY);
        return Optional.of(new GenerationStub(door,b->{
            b.addPiece(new GladesOutpostPiece(c.structureTemplateManager(),site.placement()));
            b.addPiece(new RuinApproachPiece(new RuinPlacement(door,site.placement().rotation()),site.approach()));
        }));
    }
    private static int height(GenerationContext c,BlockPos p){return c.chunkGenerator().getBaseHeight(p.getX(),p.getZ(),Heightmap.Types.OCEAN_FLOOR_WG,c.heightAccessor(),c.randomState());}
    private static boolean dry(GenerationContext c,BlockPos p,int y){var column=c.chunkGenerator().getBaseColumn(p.getX(),p.getZ(),c.heightAccessor(),c.randomState());return !column.getBlock(y-1).isAir() && column.getBlock(y-1).getFluidState().isEmpty() && column.getBlock(y).getFluidState().isEmpty();}
    @Override public StructureType<?> type(){return OriWorldgen.OUTPOST.get();}
}
