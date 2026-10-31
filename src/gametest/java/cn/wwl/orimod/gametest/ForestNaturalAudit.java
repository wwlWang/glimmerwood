package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Natural forest acceptance, real 220 server ticks per site. No production hooks. */
final class ForestNaturalAudit {
    private final MinecraftServer server;
    private final ServerLevel level;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final List<ChunkPos> forced=new ArrayList<>();
    private final Set<BlockPos> seen=new HashSet<>();
    private final List<Double> guardRatios=new ArrayList<>();
    private RuinPiece piece;
    private net.minecraft.world.level.levelgen.structure.StructureStart ownStart;
    private List<RuinApproachPiece> approaches=List.of();
    private CreekNaturalBaseline baseline;
    private Map<String,Object> row;
    private Set<BlockPos> waterBefore;
    private Map<BlockPos,String> fluidTypesBefore;
    private long tick;
    private int index,queries,slopes;
    private boolean ended;
    private ForestNaturalAudit(MinecraftServer server){this.server=server;level=server.overworld();report.put("seed",level.getSeed());report.put("status","running");report.put("sites",rows);report.put("caveProofNegativeControls",CreekForeignTerrainTrace.verifyCaveProofContract());}
    static void started(ServerStartedEvent event){
        var audit=new ForestNaturalAudit(event.getServer());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(audit::tick);
        audit.next();
    }
    private void next(){try{
        var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,RuinKind.FOREST.location()));
        do {
            int q=queries++;
            var from=q==0?new BlockPos(0,110,128):new BlockPos(q*4096,110,q*3072);
            var located=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),from,100,false);
            if(located==null)throw new IllegalStateException("No forest at query "+from);
            var cp=new ChunkPos(located.getFirst());var start=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
            ownStart=start;
            approaches=start.getPieces().stream().filter(RuinApproachPiece.class::isInstance).map(RuinApproachPiece.class::cast).toList();
            piece=(RuinPiece)start.getPieces().stream().filter(RuinPiece.class::isInstance).findFirst().orElseThrow();
            if(queries>12)throw new IllegalStateException("Repeated/absent sloping natural sites");
        }while(!seen.add(piece.origin()));
        if(piece.forestTerrainVersion()!=1)throw new IllegalStateException("Expected new forest terrain");
        row=new LinkedHashMap<>();rows.add(row);var p=piece.origin();row.put("index",index);row.put("origin",List.of(p.getX(),p.getY(),p.getZ()));row.put("rotation",piece.getRotation().name());
        // Pure-noise exhaustive diagnostics are outside performance runs.
        var cp=new ChunkPos(piece.getBoundingBox().minX()+8,piece.getBoundingBox().minZ()+8);
        var context=new ForestSitingAudit(level).context(RuinKind.FOREST,cp);var heights=new ForestSitingAudit.Heights(context);
        var relief=ForestSiteRules.inspect((x,z)->heights.at(piece.placement().block(new BlockPos(x,0,z))));
        row.put("coreRelief",relief.coreMax()-relief.coreMin());row.put("outerRelief",relief.footprintMax()-relief.footprintMin());
        boolean recovered=relief.footprintMax()-relief.footprintMin()>4&&relief.allowed();row.put("recoveredSlope",recovered);if(recovered)slopes++;
        baseline=CreekNaturalBaseline.capture(level,piece);
        ForestWaterEvidence.phase(level,piece,"CARVERS");
        ForestLavaEvidence.phase(level,piece,"CARVERS");
        int prepared=0;
        var b=piece.getBoundingBox();for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++){
            level.getChunk(x,z,ChunkStatus.FULL,true);level.setChunkForced(x,z,true);forced.add(new ChunkPos(x,z));
            GameTestDiskBackpressure.preparedChunk(level,++prepared,"forest-natural-prepare");
        }
        CreekFixtureChunks.settle(level,"forest-natural-ready");
        ForestWaterEvidence.phase(level,piece,"AFTER_FEATURES");
        ForestLavaEvidence.phase(level,piece,"AFTER_FEATURES");
        waterBefore=water();fluidTypesBefore=fluidTypes(waterBefore);tick=level.getGameTime();row.put("waterWaitStartTick",tick);write();
        System.out.println("FOREST_NATURAL_WAIT index="+index+" origin="+piece.origin()+" outer="+row.get("outerRelief"));
    }catch(Exception e){fail(e);}}
    private void tick(ServerTickEvent.Post event){if(ended||event.getServer()!=server||piece==null||level.getGameTime()-tick<220)return;
        try{
            row.put("elapsedWorldTicks",level.getGameTime()-tick);
            ForestWaterEvidence.phase(level,piece,"AFTER_220");
            ForestLavaEvidence.phase(level,piece,"AFTER_220");
            var scan=CreekTerrainGameTests.naturalScan(level,piece,baseline,approaches);row.put("terrain",scan);row.put("carversWater",baseline.report(level));
            double ratio=((Number)scan.get("guards")).doubleValue()/((Number)scan.get("boundary")).doubleValue();guardRatios.add(ratio);row.put("guardRatio",ratio);
            var hard=new ArrayList<Map<String,Object>>();var ownLights=new ArrayList<Map<String,Object>>();var foreignHard=new ArrayList<Map<String,Object>>();var blocked=new ArrayList<Map<String,Object>>();
            var template=level.getStructureManager().getOrCreate(RuinKind.FOREST.template(1));
            var settings=new StructurePlaceSettings();int airCount=0,criticalCount=0;
            for(var cell:template.filterBlocks(BlockPos.ZERO,settings,Blocks.AIR)){
                var q=cell.pos();var at=piece.placement().block(q);var state=level.getBlockState(at);airCount++;
                if(state.hasBlockEntity()||state.getDestroySpeed(level,at)<0){
                    var witness=Map.<String,Object>of("local",xyz(q),"world",xyz(at),"state",state.toString());hard.add(witness);
                    boolean own=state.is(Blocks.LIGHT)&&state.getValue(LightBlock.LEVEL)==7
                            &&(piece.ownsTerrainGuard(at.below(),level.getBlockState(at.below()))||approaches.stream().anyMatch(a->a.ownsGuard(at.below(),level.getBlockState(at.below()))));
                    (own?ownLights:foreignHard).add(witness);
                }
                boolean critical=(q.getY()==6||q.getY()==7)&&(q.getX()>=17&&q.getX()<=21&&q.getZ()<=47||q.getX()>=7&&q.getX()<=31&&q.getZ()>=12&&q.getZ()<=15)
                        ||q.getX()==19&&q.getZ()>=18&&q.getZ()<=27&&q.getY()>=8&&q.getY()<=10;
                if(critical){criticalCount++;if(!state.getCollisionShape(level,at).isEmpty())blocked.add(Map.of("local",xyz(q),"world",xyz(at),"state",state.toString()));}
            }
            row.put("templateAirCells",airCount);row.put("hardProtectedInAir",hard);row.put("hardProtectedInAirCount",hard.size());
            row.put("ownGuardLightsInAir",ownLights);row.put("ownGuardLightsInAirCount",ownLights.size());
            row.put("foreignHardProtectedInAir",foreignHard);row.put("foreignHardProtectedInAirCount",foreignHard.size());
            row.put("criticalClearanceCells",criticalCount);row.put("criticalClearanceBlocked",blocked);row.put("criticalClearanceBlockedCount",blocked.size());
            var now=water();var added=new HashSet<>(now);added.removeAll(waterBefore);
            row.put("fluidBefore",waterBefore.size());row.put("fluidAfter",now.size());row.put("newFluidAfter220",added.stream().sorted().map(ForestNaturalAudit::xyz).toList());
            row.put("fluidOutsideNew",added.size());
            var typesAfter=fluidTypes(now);
            for(String type:List.of("water","lava","other")){
                row.put(type+"Before",fluidTypesBefore.values().stream().filter(type::equals).count());
                row.put(type+"After",typesAfter.values().stream().filter(type::equals).count());
                var cells=added.stream().filter(p->type.equals(typesAfter.get(p))).sorted().map(ForestNaturalAudit::xyz).toList();
                row.put("new"+Character.toUpperCase(type.charAt(0))+type.substring(1)+"After220",cells);row.put(type+"OutsideNew",cells.size());
            }
            var waterExceptions=new ArrayList<Map<String,Object>>();var unexplainedWater=new ArrayList<List<Integer>>();
            for(var at:added.stream().sorted().toList()){
                var proof=ForestLavaEvidence.approvedFlow(at);
                if(proof==null)unexplainedWater.add(xyz(at));else waterExceptions.add(proof);
            }
            row.put("approvedForeignWaterExceptions",waterExceptions);row.put("unexplainedNewFluids",unexplainedWater);
            row.put("unexplainedNewFluidCount",unexplainedWater.size());
            for(String type:List.of("water","lava","other")){
                var cells=unexplainedWater.stream().filter(p->type.equals(typesAfter.get(new BlockPos(p.get(0),p.get(1),p.get(2))))).toList();
                row.put("unexplainedNew"+Character.toUpperCase(type.charAt(0))+type.substring(1),cells);
                row.put("unexplainedNew"+Character.toUpperCase(type.charAt(0))+type.substring(1)+"Count",cells.size());
            }
            var structureProof=ForestStructureEvidence.inspect(level,ownStart,piece,approaches,index);row.put("foreignStructureWrites",structureProof);
            boolean passed=Boolean.TRUE.equals(structureProof.get("accepted"))&&((Number)scan.get("violations")).intValue()==0&&foreignHard.isEmpty()&&blocked.isEmpty()&&unexplainedWater.isEmpty();row.put("passed",passed);write();
            System.out.println("FOREST_NATURAL_RESULT index="+index+" violations="+scan.get("violations")+" guards="+scan.get("guards")+" boundary="+scan.get("boundary")+" hardRaw="+hard.size()+" ownLights="+ownLights.size()+" hardForeign="+foreignHard.size()+" blocked="+blocked.size()+" water="+added.size());
            CreekFixtureChunks.release(level,forced);
            piece=null;baseline=null;waterBefore=null;index++;
            if(!passed){finish("threshold_failed");return;}
            if(ForestLavaEvidence.ENABLED&&index==ForestLavaEvidence.STOP_AFTER){finish("observation_complete");return;}
            if(index<5||slopes==0){next();return;}
            guardRatios.sort(Double::compareTo);report.put("guardRatioP50",guardRatios.get((guardRatios.size()-1)/2));
            double p90=guardRatios.get((int)Math.ceil(guardRatios.size()*.9)-1);report.put("guardRatioP90",p90);report.put("guardRatioMax",guardRatios.getLast());report.put("recoveredSlopeSites",slopes);
            report.put("guardRatioReviewRequired",p90>.25);
            finish("complete"); // Guard ratio is the explicit art-review line; hard geometry/water checks above stay unchanged.
        }catch(Exception e){fail(e);}
    }
    private Set<BlockPos> water(){var out=new HashSet<BlockPos>();var b=piece.getBoundingBox().inflatedBy(1);
        for(var at:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()))if(!level.getFluidState(at).isEmpty())out.add(at.immutable());return out;
    }
    private Map<BlockPos,String> fluidTypes(Set<BlockPos> cells){
        var result=new HashMap<BlockPos,String>();for(var p:cells){var f=level.getFluidState(p);
            result.put(p,f.is(net.minecraft.tags.FluidTags.WATER)?"water":f.is(net.minecraft.tags.FluidTags.LAVA)?"lava":"other");}return result;
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private void fail(Exception e){report.put("error",e.toString());e.printStackTrace();finish("failed");}
    private void finish(String status){ForestLavaEvidence.save();ended=true;report.put("status",status);try{CreekFixtureChunks.release(level,forced);}finally{write();server.halt(false);}}
    private void write(){try{Files.writeString(ReportFiles.output(Path.of("forest-natural.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
