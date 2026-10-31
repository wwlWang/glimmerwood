package cn.wwl.orimod.gametest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Replay actual observation; each mutation revokes one necessary proof clause. */
public final class ForestFeatureYieldReplay {
    private static final Gson G=new GsonBuilder().setPrettyPrinting().create();
    public static void main(String[] args)throws Exception{
        var file=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var input=G.fromJson(file.get("input"),ForestStructureConflictProof.Input.class);
        var positive=ForestStructureConflictProof.inspect(input);
        if(!Boolean.TRUE.equals(positive.get("accepted"))||((List<?>)positive.get("yieldedFeatureSiteExceptions")).size()!=1)throw new AssertionError(positive);
        var key=input.yielded().keySet().stream().filter(k->k.contains("blighted_grounds")).findFirst().orElseThrow();
        var proof=input.yielded().get(key);var tests=new ArrayList<Map<String,Object>>();
        for(String name:List.of("piece_write","preflight_pass","missing_coverage","unrelated_ruin_guard","other_foreign_on_own")){
            var events=new ArrayList<>(input.writes());var yields=new TreeMap<>(input.yielded());
            switch(name){
                case "piece_write"->events.add(Map.of("piece",key,"position",List.of(proof.coverage().maxX(),proof.coverage().minY(),proof.coverage().maxZ()),"before","stone","after","stone","attempts",1,"unchangedAttempts",1));
                case "preflight_pass"->{var a=new ArrayList<>(proof.attempts());a.set(0,new ForestStructureConflictProof.Attempt("PASS",false,a.getFirst().ruinBounds()));yields.put(key,new ForestStructureConflictProof.Yielded(proof.structureClass(),proof.site(),proof.coverage(),true,a));}
                case "missing_coverage"->yields.put(key,new ForestStructureConflictProof.Yielded(proof.structureClass(),proof.site(),null,false,proof.attempts()));
                case "unrelated_ruin_guard"->yields.put(key,new ForestStructureConflictProof.Yielded(proof.structureClass(),proof.site(),proof.coverage(),true,List.of(new ForestStructureConflictProof.Attempt("RUIN",false,List.of(new ForestStructureConflictProof.Box(0,0,0,1,1,1))))));
                case "other_foreign_on_own"->{var p=events.stream().filter(w->input.ownPieces().contains(w.get("piece"))&&proof.coverage().contains(ForestStructureConflictProof.position(w))).findFirst().orElseThrow();events.add(Map.of("piece","other_foreign/0","position",p.get("position"),"before","stone","after","stone","attempts",1,"unchangedAttempts",1));}
            }
            var changed=new ForestStructureConflictProof.Input(input.ownBoxes(),input.ownPieces(),input.references(),input.starts(),events,input.ownRecordingComplete(),input.foreignRecordingComplete(),yields);
            var result=ForestStructureConflictProof.inspect(changed);if(!Boolean.FALSE.equals(result.get("accepted")))throw new AssertionError(name);
            tests.add(Map.of("name",name,"accepted",result.get("accepted"),"conflicts",result.get("conflictCount"),"missing",result.get("missing")));
        }
        var out=Map.of("source",args[0],"positiveAccepted",true,"positiveYielded",positive.get("yieldedFeatureSiteExceptions"),"negativeMutations",tests,"allAssertionsPassed",true);
        Files.writeString(ReportFiles.output(Path.of(args[1])),G.toJson(out));System.out.println("FEATURE YIELD REPLAY PASS: actual positive + 5 negative mutations");
    }
}
