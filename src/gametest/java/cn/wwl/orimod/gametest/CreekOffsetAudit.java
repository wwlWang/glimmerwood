package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Diagnostic offset search, no pieces/world writes, no changes to production siting. */
final class CreekOffsetAudit {
    private final ServerLevel level;
    private final Structure structure;
    private final CreekReservationAudit reservations=new CreekReservationAudit();
    private final CreekOverlapAudit overlaps;
    private final RuinKind.Layout layout=RuinKind.CREEK.layout(1);
    private static final long ORDER_SALT=0x435245454b4f4646L;
    CreekOffsetAudit(ServerLevel level){this.level=level;structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.CREEK.location());overlaps=new CreekOverlapAudit(level);}
    private Structure.GenerationContext context(ChunkPos cp){var cs=level.getChunkSource();var g=cs.getGenerator();return new Structure.GenerationContext(level.registryAccess(),g,g.getBiomeSource(),cs.randomState(),level.getStructureManager(),level.getSeed(),cp,level,structure.biomes()::contains);}
    private static BoundingBox box(RuinPlacement p,RuinKind.Layout layout){
        var b=p.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1));b=new BoundingBox(b.minX()-8,0,b.minZ()-8,b.maxX()+8,0,b.maxZ()+8);
        var a=RuinApproachPiece.doorway(RuinKind.CREEK,layout,p).blocks(new BlockPos(-2,0,-9),new BlockPos(2,0,-1));b.encapsulate(new BoundingBox(a.minX(),0,a.minZ(),a.maxX(),0,a.maxZ()));return b;
    }
    static int limit(String policy){return switch(policy){case "c"->1;case "e"->0;case "d4r8"->104;case "d8r8"->108;default->Integer.parseInt(policy.substring(1));};}
    Map<String,Object> sample(ChunkPos cp,int limit){
        return sample(cp,limit,true);
    }
    Map<String,Object> sample(ChunkPos cp,int limit,boolean includeOverlap){
        long start=System.nanoTime();var c=context(cp);var rotation=Rotation.getRandom(c.random());
        var base=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);
        var offsets=new ArrayList<BlockPos>();
        for(int radius:limit>=100?new int[]{8}:new int[]{16,32}){if(limit==1)continue;if(limit<17&&radius==32)continue;for(int i=0;i<8;i+=(limit==5||limit==104?2:1))offsets.add(new BlockPos((int)Math.round(radius*Math.cos(i*Math.PI/4)),0,(int)Math.round(radius*Math.sin(i*Math.PI/4))));}
        Collections.shuffle(offsets,new Random(level.getSeed()^cp.toLong()^ORDER_SALT));offsets.addFirst(BlockPos.ZERO);
        var cells=new CreekRejectionAudit.Cells(c);var heights=new HashMap<BlockPos,CreekRejectionAudit.Surface>();
        var attempts=new ArrayList<Map<String,Object>>();RuinPlacement selected=null;int calls=0;long biomeQueries=0;
        for(var offset:offsets){
            var p=new RuinPlacement(base.origin().offset(offset),rotation);var row=new LinkedHashMap<String,Object>();row.put("offset",List.of(offset.getX(),offset.getZ()));
            long at=System.nanoTime();int n0=heights.size();
            var used=new HashSet<BlockPos>();int[] asks={0};
            java.util.function.ToIntFunction<BlockPos> height=q->{asks[0]++;used.add(q.atY(0));return heights.computeIfAbsent(q.atY(0),k->cells.at(k.getX(),k.getZ())).floor();};
            boolean originTerrainGate=limit==0&&offset.equals(BlockPos.ZERO);
            String reason=check(c,p,offset,height,row,originTerrainGate);
            if(reason.equals("PASS")&&used.stream().anyMatch(q->!heights.get(q).dry()))reason="WATER";
            boolean stopForOrigin=originTerrainGate&&!reason.equals("PASS");
            if(originTerrainGate&&!stopForOrigin){
                var variants=reservations.sample(c,p,layout,List.of("c"));var reservation=(Map<?,?>)variants.get("c");
                row.put("reservationBiomeQueries",reservation.get("biomeQueries"));var blocks=(List<?>)reservation.get("reasons");
                if(!blocks.isEmpty())reason=(String)blocks.getFirst();
            }
            row.put("originTerrainRejected",stopForOrigin);
            calls+=asks[0];biomeQueries+=((Number)row.getOrDefault("reservationBiomeQueries",0)).longValue();
            row.put("reason",reason);row.put("heightCalls",asks[0]);row.put("newHeightColumns",heights.size()-n0);row.put("ms",(System.nanoTime()-at)/1e6);attempts.add(row);
            if(reason.equals("PASS")){selected=p;break;}
            if(stopForOrigin)break;
        }
        var out=new LinkedHashMap<String,Object>();out.put("chunk",List.of(cp.x,cp.z));out.put("rotation",rotation.name());out.put("accepted",selected!=null);out.put("attempts",attempts);out.put("tried",attempts.size());out.put("heightCalls",calls);out.put("uniqueHeightColumns",heights.size());out.put("noiseCells",cells.count());out.put("reservationBiomeQueries",biomeQueries);out.put("ms",(System.nanoTime()-start)/1e6);out.put("thread",Thread.currentThread().getName());
        // Geometric diagnostics deliberately outside the siting timer.
        if(includeOverlap)out.put("zeroOverlap",overlaps.sample(cp,box(base,layout)));
        if(selected!=null){out.put("selectedOriginXZ",List.of(selected.origin().getX(),selected.origin().getZ()));if(includeOverlap)out.put("selectedOverlap",overlaps.sample(cp,box(selected,layout)));}
        return out;
    }
    private String check(Structure.GenerationContext c,RuinPlacement p,BlockPos offset,java.util.function.ToIntFunction<BlockPos> height,Map<String,Object> row){
        return check(c,p,offset,height,row,false);
    }
    private String check(Structure.GenerationContext c,RuinPlacement p,BlockPos offset,java.util.function.ToIntFunction<BlockPos> height,Map<String,Object> row,boolean skipReservations){
        int margin=(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8))+12;
        int x=c.chunkPos().getMiddleBlockX()+offset.getX(),z=c.chunkPos().getMiddleBlockZ()+offset.getZ();
        if(GreatTreeSites.reserved(c.randomState(),x,z,margin))return "GREAT_TREE";
        // Only this coordinate-based reservation uses the translated centre chunk;
        // the registered start chunk, orientation RNG and neighbour region remain original.
        if(GinsoStructure.reserved(context(new ChunkPos(BlockPos.containing(x,0,z))),margin))return "GINSO";
        var entry=BlockPos.containing(p.point(layout.entry()));int delta=(int)Math.floor(layout.entry().y)-layout.surfaceY();boolean biome=false;
        for(int y=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE+delta);y<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE+delta);y++)if(c.validBiome().test(c.biomeSource().getNoiseBiome(entry.getX()>>2,y,entry.getZ()>>2,c.randomState().sampler()))){biome=true;break;}
        if(!biome)return "BIOME_COARSE";
        if(!skipReservations){var reserve=reservations.sample(c,p,layout,List.of("c"));var v=(Map<?,?>)reserve.get("c");row.put("reservationBiomeQueries",v.get("biomeQueries"));var reasons=(List<?>)v.get("reasons");if(!reasons.isEmpty())return (String)reasons.getFirst();}
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(var q:CreekRejectionAudit.points("CORE")){int y=height.applyAsInt(p.block(new BlockPos(q[0],0,q[1])));min=Math.min(min,y);max=Math.max(max,y);if(!RuinSiteRules.footprintOk(min,max))return "CORE_RELIEF";}
        int allMin=min,allMax=max,limit=((Number)CreekRejectionAudit.staticField("CreekSiteRules","OUTER_RELIEF")).intValue();
        for(var q:CreekRejectionAudit.points("OUTER")){int y=height.applyAsInt(p.block(new BlockPos(q[0],0,q[1])));allMin=Math.min(allMin,y);allMax=Math.max(allMax,y);if(!RuinSiteRules.surfaceInRange(y)||allMax-allMin>limit)return "OUTER_RELIEF";}
        var door=RuinApproachPiece.doorway(RuinKind.CREEK,layout,p);
        for(int dx=-2;dx<=2;dx++){int y=height.applyAsInt(door.block(new BlockPos(dx,0,-1)));min=Math.min(min,y);max=Math.max(max,y);if(!RuinSiteRules.footprintOk(min,max))return "ENTRY_RELIEF";}
        int surface=(min+max+1)/2;row.put("originY",surface-layout.surfaceY());var placed=new RuinPlacement(p.origin().atY(surface-layout.surfaceY()),p.rotation());door=RuinApproachPiece.doorway(RuinKind.CREEK,layout,placed);
        for(int dx=-2;dx<=2;dx++)for(int dz=-9;dz<=-1;dz++)if(!RuinSiteRules.approachOk(height.applyAsInt(door.block(new BlockPos(dx,0,dz))),door.origin().getY()))return "APPROACH";
        entry=BlockPos.containing(placed.point(layout.entry()));if(!c.validBiome().test(c.biomeSource().getNoiseBiome(entry.getX()>>2,entry.getY()>>2,entry.getZ()>>2,c.randomState().sampler())))return "BIOME_ENTRY";
        var bounds=(BoundingBox)CreekRejectionAudit.callStatic("CreekRuinTerrain","bounds",new Class<?>[]{BoundingBox.class},placed.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1)));
        if(!GinsoGenerationGuard.checkBounds(c.randomState(),bounds).allowed())return "GENERATED";
        if(!RuinSiteRules.fitsWorld(placed.origin().getY(),layout.size().getY(),c.heightAccessor().getMinBuildHeight(),c.heightAccessor().getMaxBuildHeight()))return "BUILD_HEIGHT";
        return "PASS";
    }
    @SuppressWarnings("unchecked") List<StructurePiece> materialize(ChunkPos cp,Map<String,Object> result){
        var c=context(cp);var rotation=Rotation.getRandom(c.random());
        var attempts=(List<Map<String,Object>>)result.get("attempts");var selected=attempts.getLast();var v=(List<Number>)selected.get("offset");
        var offset=new BlockPos(v.get(0).intValue(),0,v.get(1).intValue());
        var p=RuinPlacement.atMinimum(layout,cp.getWorldPosition(),rotation);p=new RuinPlacement(p.origin().offset(offset),rotation);
        var cells=new CreekRejectionAudit.Cells(c);var heights=new HashMap<BlockPos,CreekRejectionAudit.Surface>();
        java.util.function.ToIntFunction<BlockPos> height=q->heights.computeIfAbsent(q.atY(0),k->cells.at(k.getX(),k.getZ())).floor();
        var measured=new LinkedHashMap<String,Object>();String reason=check(c,p,offset,height,measured);
        if(!reason.equals("PASS")||heights.values().stream().anyMatch(q->!q.dry()))throw new IllegalStateException("Selected offset drift "+cp+" "+offset+" "+reason);
        p=new RuinPlacement(p.origin().atY(((Number)measured.get("originY")).intValue()),rotation);
        var door=RuinApproachPiece.doorway(RuinKind.CREEK,layout,p);int[] approach=new int[45];
        for(int x=-2;x<=2;x++)for(int z=-9;z<=-1;z++)approach[(x+2)*9+z+9]=height.applyAsInt(door.block(new BlockPos(x,0,z)));
        // No blocks are written. The normal piece constructors supply their exact
        // write boxes; the natural snapshot changes terrain contents, not boxes.
        return List.of(new RuinPiece(c.structureTemplateManager(),RuinKind.CREEK,p,1,approach),new RuinApproachPiece(door,approach));
    }
    static void started(ServerStartedEvent event){
        var server=event.getServer();var report=new LinkedHashMap<String,Object>();var rows=new ArrayList<Map<String,Object>>();report.put("rows",rows);report.put("seed",server.overworld().getSeed());
        int limit=Integer.getInteger("orimod.offsetLimit",17);report.put("limit",limit);report.put("orderSalt",ORDER_SALT);report.put("source",System.getProperty("orimod.offsetReference"));
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"creek-offset-audit-worker"));
        try{executor.submit(()->{try{
            var audit=new CreekOffsetAudit(server.overworld());
            report.put("boundaryProbe",CreekBiomeBoundaryAudit.run(server.overworld()));
            var source=JsonParser.parseString(Files.readString(Path.of(System.getProperty("orimod.offsetReference")))).getAsJsonObject();
            var points=source.getAsJsonArray("rows");
            for(var point:points){var in=point.getAsJsonObject();var cp=in.getAsJsonArray("chunk");var row=audit.sample(new ChunkPos(cp.get(0).getAsInt(),cp.get(1).getAsInt()),limit);row.put("v0",in.get("v0").getAsBoolean());
                if(limit==0){
                    var origin=in.getAsJsonObject("allReasonAudit");boolean terrain=true;
                    for(var reason:origin.getAsJsonArray("reasons"))if(!reason.getAsString().startsWith("STRUCTURE_"))terrain=false;
                    boolean originC=terrain&&origin.getAsJsonObject("variants").getAsJsonObject("c").getAsJsonArray("reasons").isEmpty();
                    row.put("originTerrainEligible",terrain);row.put("originCAccepted",originC);
                    if(Boolean.TRUE.equals(row.get("accepted"))&&!terrain)throw new IllegalStateException("e accepted terrain-rejected origin "+cp);
                    if(originC&&!Boolean.TRUE.equals(row.get("accepted")))throw new IllegalStateException("e lost accepted c origin "+cp);
                    if(!terrain&&((Number)row.get("tried")).intValue()!=1)throw new IllegalStateException("e searched terrain-rejected origin "+cp);
                }
                rows.add(row);
                if(rows.size()%50==0||rows.size()==points.size()){write(report);System.out.println("CREEK_OFFSETS completed="+rows.size()+" accepted="+rows.stream().filter(r->Boolean.TRUE.equals(r.get("accepted"))).count());}
            }report.put("status","complete");
        }catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(Exception e){report.put("status","failed");report.put("error",e.toString());e.printStackTrace();}
        finally{executor.shutdown();write(report);server.halt(false);}
    }
    private static void write(Map<String,Object> report){try{Files.writeString(Path.of("offsets.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
}
