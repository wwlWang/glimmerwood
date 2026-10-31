package cn.wwl.orimod.gametest;

import com.google.gson.GsonBuilder;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.*;
import java.util.*;

/** Identical dedicated-server probe copied into the read-only baseline export. */
public final class P5ChunkPerformanceAudit {
    public static double prepareMs;
    public static boolean enabled(){return "p5_chunks".equals(System.getProperty("orimod.worldgen.audit.mode"));}
    public static void prepared(double ms){if(enabled()){prepareMs=ms;System.out.println("P5_PREPARE_LEVELS ms="+ms);}}
    static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        var rows=new ArrayList<Map<String,Object>>();var random=new Random(117006L);
        var centers=new ArrayList<ChunkPos>();
        for(int i=0;i<20;i++){
            double a=(i+.2+random.nextDouble()*.6)*Math.PI/10,r=60000+random.nextInt(30000);
            centers.add(new ChunkPos((int)Math.round(Math.cos(a)*r)>>4,(int)Math.round(Math.sin(a)*r)>>4));
        }
        var report=new LinkedHashMap<String,Object>();report.put("seed",level.getSeed());report.put("prepareLevelsMs",prepareMs);
        report.put("method","20 fixed remote rings, each 5x5 chunks synchronously requested to FULL on the server thread; no existing terrain. Generation timed; save/unload outside timed interval.");
        report.put("rows",rows);report.put("status","running");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{
            if(e.getServer()!=server||rows.size()>=20)return;
            try{
                var cp=centers.get(rows.size());long begin=System.nanoTime();
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)level.getChunk(cp.x+dx,cp.z+dz);
                var row=new LinkedHashMap<String,Object>();row.put("center",List.of(cp.x,cp.z));row.put("fullChunks",25);row.put("ms",(System.nanoTime()-begin)/1e6);
                rows.add(row);System.out.println("P5_CHUNK_RING seed="+level.getSeed()+" n="+rows.size()+" "+row);
                GameTestDiskBackpressure.unloadAndDrain(level,"p5-throughput");
                if(rows.size()==20){
                    double total=rows.stream().mapToDouble(r->((Number)r.get("ms")).doubleValue()).sum();
                    report.put("generationMs",total);report.put("chunksPerSecond",500000.0/total);report.put("status","complete");
                }
                save(report);if(rows.size()==20)server.halt(false);
            }catch(Exception ex){report.put("status","failed");report.put("error",ex.toString());save(report);server.halt(false);}
        });
    }
    private static void save(Map<String,Object> report){try{Files.writeString(ReportFiles.output(Path.of("p5-chunk-performance.json")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new IllegalStateException(e);}}
}
