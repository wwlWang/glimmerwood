package cn.wwl.orimod.devtools;

import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.*;

/** Development-only noise survey, no chunk loads and no writes. Uses the real structure placement and preflight. */
final class GinsoProbe {
    static Map<String,Object> state(net.minecraft.server.level.ServerPlayer p){
        var m=new LinkedHashMap<String,Object>(nearby(p.serverLevel(),p.blockPosition()));
        m.put("escape",GinsoEscape.diagnostics(p));return m;
    }
    static Map<String,Object> scan(ServerLevel level,BlockPos center,int radius,int spacing,int separation){
        if(radius<1||radius>6)throw new IllegalArgumentException("Region radius must be 1..6");
        if(spacing!=0&&(spacing<32||spacing>80||separation<8||separation>=spacing))throw new IllegalArgumentException("Invalid diagnostic spacing");
        var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
        var actual=(RandomSpreadStructurePlacement)set.placement();
        var placement=spacing==0?actual:new RandomSpreadStructurePlacement(spacing,separation,net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,GinsoSiteRules.SALT);
        var legacy=new RandomSpreadStructurePlacement(64,24,net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,GinsoSiteRules.SALT);
        // Fixed physical window, even when comparing different placement grids: never change the denominator.
        int sx=Math.floorDiv(center.getX(),1024),sz=Math.floorDiv(center.getZ(),1024);
        int minX=(sx-radius)*1024,minZ=(sz-radius)*1024,maxX=(sx+radius+1)*1024,maxZ=(sz+radius+1)*1024;
        long started=System.nanoTime();int before=level.getChunkSource().getLoadedChunksCount();
        var result=survey(level,placement,minX,minZ,maxX,maxZ,3);
        if(spacing==0&&radius<=4){
            var adapted=actual.spacing()==64&&actual.separation()==24?new LinkedHashMap<>(result):survey(level,legacy,minX,minZ,maxX,maxZ,3);
            result.put("legacy",survey(level,legacy,minX,minZ,maxX,maxZ,2));result.put("adaptedAtOriginalSpacing",adapted);
        }
        result.put("window",Map.of("minXZ",List.of(minX,minZ),"maxXZExclusive",List.of(maxX,maxZ),"area",(long)(maxX-minX)*(maxZ-minZ)));
        result.put("seed",level.getSeed());result.put("hypothetical",spacing!=0);
        result.put("elapsedMs",(System.nanoTime()-started)/1e6);result.put("chunksBefore",before);result.put("chunksAfter",level.getChunkSource().getLoadedChunksCount());return result;
    }
    private static LinkedHashMap<String,Object> survey(ServerLevel level,RandomSpreadStructurePlacement placement,int minX,int minZ,int maxX,int maxZ,int version){
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
        var generator=level.getChunkSource().getGenerator();var randomState=level.getChunkSource().randomState();
        var histogram=new TreeMap<String,Integer>();var sites=new ArrayList<Map<String,Object>>();var candidates=new ArrayList<Map<String,Object>>();
        var byBiome=new TreeMap<String,Map<String,Integer>>();int span=placement.spacing()*16;
        for(int gx=Math.floorDiv(minX,span);gx<=Math.floorDiv(maxX-1,span);gx++)for(int gz=Math.floorDiv(minZ,span);gz<=Math.floorDiv(maxZ-1,span);gz++){
            var chunk=placement.getPotentialStructureChunk(level.getSeed(),gx*placement.spacing(),gz*placement.spacing());
            int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ();if(x<minX||x>=maxX||z<minZ||z>=maxZ)continue;
            var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),randomState,level.getStructureManager(),level.getSeed(),chunk,level,structure.biomes()::contains);
            var check=GinsoStructure.preflight(context,version);var at=check.origin();String biome=generator.getBiomeSource().getNoiseBiome(at.getX()>>2,at.getY()>>2,at.getZ()>>2,randomState.sampler()).unwrapKey().orElseThrow().location().toString();
            histogram.merge(check.reason().name(),1,Integer::sum);byBiome.computeIfAbsent(biome,k->new TreeMap<>()).merge(check.reason().name(),1,Integer::sum);
            candidates.add(Map.of("chunk",List.of(chunk.x,chunk.z),"origin",xyz(at),"biome",biome,"reason",check.reason().name(),"detail",check.detail()));
            if(check.reason()==GinsoSiteRules.Reason.OK)sites.add(Map.of("origin",xyz(at),"biome",biome));
        }
        var result=new LinkedHashMap<String,Object>();result.put("histogram",histogram);result.put("sites",sites);result.put("byBiome",byBiome);result.put("candidates",candidates);
        result.put("spacing",placement.spacing());result.put("separation",placement.separation());result.put("treesPerMillionBlocks",sites.size()*1e6/((long)(maxX-minX)*(maxZ-minZ)));return result;
    }
    static Map<String,Object> nearby(ServerLevel level,BlockPos at){
        var found=new LinkedHashMap<BlockPos,GinsoPiece>();
        for(int x=(at.getX()>>4)-5;x<=(at.getX()>>4)+5;x++)for(int z=(at.getZ()>>4)-5;z<=(at.getZ()>>4)+5;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var start:chunk.getAllStarts().values())if(start.isValid()&&start.getStructure() instanceof GinsoStructure)
                for(var piece:start.getPieces())if(piece instanceof GinsoPiece g)found.put(g.origin(),g);
        }
        return Map.of("sites",found.values().stream().map(g->{
            var p=g.origin();var row=new LinkedHashMap<String,Object>();
            row.put("origin",xyz(p));row.put("height",g.sculpture().height);row.put("shapeSeed",g.shapeSeed());row.put("shapeVersion",g.shapeVersion());row.put("courseVersion",g.courseVersion());
            row.put("escapeVersion",g.escapeVersion());
            if(g.escapeVersion()!=0){
                var escape=g.escape();var plan=GinsoEscapeRules.plan(escape,g.escapeVersion());
                var route=escape.path().stream().map(q->{
                    var foot=g.point(new BlockPos(q.x(),(int)Math.floor(q.feet()),q.z()));
                    return Map.of("position",List.of(foot.getX()+.5,foot.getY()+q.feet()%1,foot.getZ()+.5),"distance",q.distance(),"corners",q.corners());
                }).toList();
                row.put("escapeGeometry",Map.of("route",route,"cells",escape.blocks().size(),"floodBoxes",escape.flood().size(),
                        "cleftIndex",escape.cleftIndex(),"branchIndex",escape.branchIndex(),"initialWater",p.getY()+plan.startWater(),"referenceSeconds",plan.referenceSeconds(),"limitTicks",plan.limitTicks(),"interactive",true,"nativeWood",Map.of("clearance",escape.audit().clearanceRemoved(),"trimmed",escape.audit().trimmed(),"maximumTrim",escape.audit().maximumTrim(),"braces",escape.audit().bracedBlocks(),"maximumSevered",escape.audit().maximumSevered())));
            }
            row.put("rootSupports",rootSupports(level,g));
            row.put("rootArches",rootArches(level,g));
            row.put("biome",level.getBiome(p).unwrapKey().orElseThrow().location().toString());row.put("baseBlock",level.getBlockState(p).toString());
            row.put("entranceTurns",g.entrance());row.put("approachOffset",g.approach());row.put("course",g.hasCourse());
            row.put("entry",xyz(g.point(new BlockPos(0,g.approachFloor(-21)+1,-21))));
            var course=g.course();
            row.put("heart",xyz(g.point(course.heart())));row.put("checkpoints",course.wells().stream().map(g::point).map(GinsoProbe::xyz).toList());
            if(g.courseVersion()==1){row.put("lantern",xyz(g.point(GinsoInterior.LANTERN)));row.put("flower",xyz(g.point(GinsoInterior.GRAPPLE)));row.put("vent",xyz(g.point(GinsoInterior.VENT)));}
            row.put("sections",course.sections().stream().map(s->Map.of("id",s.id(),"start",xyz(g.point(s.start())),"goal",xyz(g.point(s.goal())),"floor",p.getY()+s.floor(),"top",p.getY()+s.top(),"required",s.required()==null?"basic":s.required().id())).toList());
            row.put("hops",g.ascentHops().stream().map(h->{var hop=new LinkedHashMap<String,Object>();
                hop.put("section",h.section());hop.put("start",xyz(g.point(h.start())));hop.put("goal",xyz(g.point(h.goal())));
                hop.put("floor",p.getY()+h.floor());hop.put("top",p.getY()+h.top());hop.put("side",h.side());
                if(h.interaction()!=null)hop.put("interaction",xyz(g.point(h.interaction())));return hop;}).toList());
            row.put("retreat",course.retreat().stream().map(g::point).map(GinsoProbe::xyz).toList());
            return row;
        }).toList());
    }
    private static List<Map<String,Object>> rootSupports(ServerLevel level,GinsoPiece piece){
        if(piece.shapeVersion()<3)return List.of();var out=new ArrayList<Map<String,Object>>();var shape=piece.sculpture();var base=piece.origin();
        for(var foot:GinsoSiteRules.rootFeet(piece.shapeSeed())){
            BlockPos toe=null;
            for(int x=foot[0]-1;x<=foot[0]+1&&toe==null;x++)for(int z=foot[1]-1;z<=foot[1]+1&&toe==null;z++)
                for(int y=-5;y<=0;y++)if(GinsoShape.timber(shape.cell(x,y,z))){toe=base.offset(x,y,z);break;}
            var row=new LinkedHashMap<String,Object>();row.put("localFoot",List.of(foot[0],foot[1]));
            if(toe==null){row.put("error","no terminal voxel");out.add(row);continue;}
            row.put("toe",xyz(toe));boolean loaded=level.getChunkSource().getChunkNow(toe.getX()>>4,toe.getZ()>>4)!=null;row.put("loaded",loaded);
            if(loaded){
                var local=piece.local(toe);boolean graded=piece.hasCourse()&&Math.abs(local.getX())<=3&&local.getZ()>=-27&&local.getZ()<=-5;
                int last=graded?Math.max(toe.getY(),base.getY()+piece.approachFloor(local.getZ())):toe.getY();
                row.put("entryGraded",graded);
                BlockPos lowest=null;
                for(int y=base.getY()+GinsoShape.minY(piece.shapeVersion());y<=last;y++){
                    var at=new BlockPos(toe.getX(),y,toe.getZ());if(level.getBlockState(at).is(cn.wwl.orimod.block.OriBlocks.GINSO_WOOD)){lowest=at;break;}
                }
                if(lowest!=null){var below=lowest.below();var state=level.getBlockState(below);row.put("lowest",xyz(lowest));row.put("floor",state.toString());row.put("supported",state.isFaceSturdy(level,below,net.minecraft.core.Direction.UP)&&state.getFluidState().isEmpty());}
                else row.put("error","terminal column absent");
            }out.add(row);
        }return out;
    }
    private static List<Map<String,Object>> rootArches(ServerLevel level,GinsoPiece piece){
        if(piece.shapeVersion()<3)return List.of();
        var out=new ArrayList<Map<String,Object>>();var random=new Random(piece.shapeSeed());
        random.nextInt(11);double phase=random.nextDouble()*.4;
        for(int i=0;i<8;i+=2){
            double a=i*Math.PI/4+.28+phase+.18;
            var center=piece.origin().offset((int)Math.round(Math.cos(a)*16),0,(int)Math.round(Math.sin(a)*16));
            int clear=0;boolean loaded=true;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=1;y<=6;y++){
                var p=center.offset(dx,y,dz);
                if(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null){loaded=false;continue;}
                if(level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&level.getFluidState(p).isEmpty())clear++;
            }
            out.add(Map.of("center",xyz(center),"angle",a,"loaded",loaded,"clearCells",clear,"testedCells",54));
        }return out;
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
}
