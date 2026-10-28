package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.*;

/** Audit ONLY. Does not reject production sites, build pieces or sample heights.
 * Pair rates are conservative potential overlaps, not observed placed structures. */
final class CreekOverlapAudit {
    private final ServerLevel level;
    private final RandomSpreadStructurePlacement ruins,outposts;
    private final Map<String,Boolean> cheap=new HashMap<>();
    private final Map<String,BoundingBox> boxes=new HashMap<>();
    private final Map<String,Structure> structures=new HashMap<>();
    CreekOverlapAudit(ServerLevel level){
        this.level=level;var sets=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        ruins=(RandomSpreadStructurePlacement)sets.get(ResourceLocation.fromNamespaceAndPath("orimod","spirit_ruins")).placement();
        outposts=(RandomSpreadStructurePlacement)sets.get(ResourceLocation.fromNamespaceAndPath("orimod","glades_outpost")).placement();
        for(String kind:List.of("creek_ruins","forest_spring","cave_altar","glades_outpost"))structures.put(kind,level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod",kind)));
    }
    Map<String,Object> sample(ChunkPos cp){
        return sample(cp,envelope("creek_ruins",cp));
    }
    Map<String,Object> sample(ChunkPos cp,BoundingBox own){
        long started=System.nanoTime();var result=new LinkedHashMap<String,Object>();
        boolean any=false;
        for(String kind:List.of("forest_spring","cave_altar","creek_ruins")){
            int geometrical=0,viable=0;var hits=new ArrayList<List<Integer>>();
            int gx=Math.floorDiv(cp.x,ruins.spacing()),gz=Math.floorDiv(cp.z,ruins.spacing());
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;
                var neighbour=ruins.getPotentialStructureChunk(level.getSeed(),(gx+dx)*ruins.spacing(),(gz+dz)*ruins.spacing());
                if(!own.intersects(envelope(kind,neighbour)))continue;
                geometrical++;
                if(cheap(kind,neighbour)){viable++;hits.add(List.of(neighbour.x,neighbour.z));}
            }
            result.put(kind,Map.of("geometry",geometrical,"cheapPassed",viable,"hits",hits));any|=viable>0;
        }
        int geom=0,passed=0;var hits=new ArrayList<List<Integer>>();
        int gx=Math.floorDiv(cp.x,outposts.spacing()),gz=Math.floorDiv(cp.z,outposts.spacing());
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var neighbour=outposts.getPotentialStructureChunk(level.getSeed(),(gx+dx)*outposts.spacing(),(gz+dz)*outposts.spacing());
            if(!own.intersects(envelope("glades_outpost",neighbour)))continue;
            geom++;if(cheap("glades_outpost",neighbour)){passed++;hits.add(List.of(neighbour.x,neighbour.z));}
        }
        result.put("glades_outpost",Map.of("geometry",geom,"cheapPassed",passed,"hits",hits));
        result.put("wouldYieldToNeighbourRuin",any);result.put("wouldYieldIncludingExactOutpost",any||passed>0);
        result.put("auditMs",(System.nanoTime()-started)/1e6);return result;
    }
    private Structure.GenerationContext context(String kind,ChunkPos cp){
        var cs=level.getChunkSource();var generator=cs.getGenerator();var structure=structures.get(kind);
        return new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),cs.randomState(),level.getStructureManager(),level.getSeed(),cp,level,structure.biomes()::contains);
    }
    private boolean cheap(String kind,ChunkPos cp){return cheap.computeIfAbsent(kind+":"+cp.toLong(),key->{
        var c=context(kind,cp);boolean outpost=kind.equals("glades_outpost");
        var k=outpost?null:RuinKind.byId(kind);var layout=outpost?null:k.layout(k.currentVersion());
        int margin=outpost?48:(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8))+(k==RuinKind.CAVE?0:12);
        if(GinsoStructure.reserved(c,margin))return false;
        for(var rotation:Rotation.values()){
            var placement=outpost?GladesOutpostStructure.placement(cp.getWorldPosition(),rotation):RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);
            var entry=outpost?placement.block(GladesOutpostPiece.ENTRY):BlockPos.containing(placement.point(layout.entry()));
            int delta=outpost?0:(int)Math.floor(layout.entry().y)-layout.surfaceY();
            for(int y=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE+delta);y<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE+delta);y++)
                if(c.validBiome().test(c.biomeSource().getNoiseBiome(QuartPos.fromBlock(entry.getX()),y,QuartPos.fromBlock(entry.getZ()),c.randomState().sampler())))return true;
        }return false;
    });}
    private BoundingBox envelope(String kind,ChunkPos cp){return boxes.computeIfAbsent(kind+":"+cp.toLong(),key->{
        BoundingBox result=null;boolean outpost=kind.equals("glades_outpost");
        var k=outpost?null:RuinKind.byId(kind);var layout=outpost?null:k.layout(k.currentVersion());
        for(var rotation:Rotation.values()){
            var p=outpost?GladesOutpostStructure.placement(cp.getWorldPosition(),rotation):RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);
            var size=outpost?GladesOutpostPiece.SIZE:layout.size();var b=p.blocks(BlockPos.ZERO,size.offset(-1,-1,-1));
            int skirt=!outpost&&k!=RuinKind.CAVE?8:0;
            b=new BoundingBox(b.minX()-skirt,0,b.minZ()-skirt,b.maxX()+skirt,0,b.maxZ()+skirt);
            var entry=outpost?new RuinPlacement(p.block(GladesOutpostPiece.ENTRY),rotation):RuinApproachPiece.doorway(k,layout,p);
            var a=entry.blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));
            b.encapsulate(new BoundingBox(a.minX(),0,a.minZ(),a.maxX(),0,a.maxZ()));
            if(result==null)result=b;else result.encapsulate(b);
        }return result;
    });}
}
