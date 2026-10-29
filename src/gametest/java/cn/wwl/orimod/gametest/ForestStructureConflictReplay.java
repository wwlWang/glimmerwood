package cn.wwl.orimod.gametest;
import java.nio.file.*;import java.util.*;import com.google.gson.*;
public final class ForestStructureConflictReplay {
    static final Gson G=new GsonBuilder().setPrettyPrinting().create();
    static void require(boolean value,Object why){if(!value)throw new AssertionError(why);}
    static ForestStructureConflictProof.Input copy(ForestStructureConflictProof.Input p,List<Map<String,Object>> writes,Map<String,List<String>> refs,Map<String,List<ForestStructureConflictProof.Piece>> starts,boolean coverage){
        return new ForestStructureConflictProof.Input(p.ownBoxes(),p.ownPieces(),refs,starts,writes,true,coverage);
    }
    static Map<String,Object> compact(Map<String,Object> p){var out=new LinkedHashMap<>(p);var conflicts=(List<?>)p.get("conflicts");out.put("conflicts",conflicts.stream().limit(3).toList());return out;}
    static boolean rule(Map<String,Object> proof,String rule){return ((List<Map<String,Object>>)proof.get("conflicts")).stream().anyMatch(c->rule.equals(c.get("rule")));}
    public static void main(String[] args)throws Exception{
        var dir=Path.of(args[0]);var trail=G.fromJson(Files.readString(dir.resolve("trail.json")),ForestStructureConflictProof.Input.class);
        var village=G.fromJson(Files.readString(dir.resolve("village.json")),ForestStructureConflictProof.Input.class);
        var report=new LinkedHashMap<String,Object>();var observed=ForestStructureConflictProof.inspect(village);
        require(Boolean.FALSE.equals(observed.get("accepted")),observed);
        var witness=((List<Map<String,Object>>)observed.get("conflicts")).stream().filter(c->c.get("position").equals(List.of(-1407,67,-773)))
            .filter(c->((List<Map<String,Object>>)c.get("ownWrites")).stream().anyMatch(w->((List<String>)w.get("stack")).stream().anyMatch(s->s.contains("RuinApproachPiece.put")))).findFirst().orElseThrow();
        report.put("village_actual_recorded_failure",Map.of("conflicts",observed.get("conflictCount"),"witness",witness,"missing",observed.get("missing")));
        var oldTrail=ForestStructureConflictProof.inspect(trail);require(((Number)oldTrail.get("conflictCount")).intValue()==0,oldTrail);
        require(Boolean.FALSE.equals(oldTrail.get("accepted"))&&oldTrail.get("missing").toString().contains("FOREIGN_WRITE_COVERAGE_INCOMPLETE"),oldTrail);
        report.put("historical_trail_full_gate",compact(oldTrail));
        report.put("coverage_note","Historical fluid observer covered a finite foreign window. The actual archived run is NOT certified by the new whole-own gate. The closed in-memory fixture below consists exactly of recorded events; its positive tests the classifier, not missing world history.");
        var fixture=copy(trail,trail.writes(),trail.references(),trail.starts(),true);var positive=ForestStructureConflictProof.inspect(fixture);
        require(Boolean.TRUE.equals(positive.get("accepted")),positive);report.put("closed_recorded_event_fixture_positive",compact(positive));
        var tests=new ArrayList<Map<String,Object>>();
        for(String name:List.of("ori_unchanged_in_trail_piece","foreign_write_on_own_cell","missing_reference_chunk","unresolved_reference_start")){
            var events=new ArrayList<>(fixture.writes());var refs=new TreeMap<>(fixture.references());var starts=new TreeMap<>(fixture.starts());
            switch(name){
                case "ori_unchanged_in_trail_piece"->{var e=new LinkedHashMap<String,Object>();e.put("piece",fixture.ownPieces().iterator().next());e.put("position",List.of(8220,48,6818));e.put("owner","main_template");e.put("changed",false);e.put("before","stone");e.put("after","stone");events.add(e);}
                case "foreign_write_on_own_cell"->{var own=events.stream().filter(e->fixture.ownPieces().contains(e.get("piece"))).findFirst().orElseThrow();events.add(Map.of("piece","minecraft:trail_ruins@516,426/18","position",own.get("position"),"before","air","after","gravel"));}
                case "missing_reference_chunk"->refs.remove(refs.firstKey());
                case "unresolved_reference_start"->starts.remove("minecraft:trail_ruins@516,426");
            }
            var result=ForestStructureConflictProof.inspect(copy(fixture,events,refs,starts,true));require(Boolean.FALSE.equals(result.get("accepted")),name);
            String expected=switch(name){case "ori_unchanged_in_trail_piece"->"OWN_ATTEMPT_IN_FOREIGN_PIECE";case "foreign_write_on_own_cell"->"FOREIGN_WRITE_ON_OWN_CELL";case "missing_reference_chunk"->"REFERENCE_CHUNK_MISSING";default->"REFERENCED_START_UNRESOLVED";};
            require(rule(result,expected)||result.get("missing").toString().contains(expected),name+" "+result);
            tests.add(Map.of("name",name,"accepted",false,"expectedRule",expected,"result",compact(result)));
        }
        report.put("negativeMutations",tests);report.put("allAssertionsPassed",true);Files.writeString(dir.resolve("result.json"),G.toJson(report));
        System.out.println("STRUCTURE CONFLICT REPLAY PASS: village witness + closed fixture positive + 4 negative mutations; archived trail whole-run coverage remains explicitly incomplete");
    }
}
