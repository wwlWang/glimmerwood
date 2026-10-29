package cn.wwl.orimod.gametest;

import java.util.*;

/** Run-local structure evidence, deliberately independent of Minecraft for mutation replay. */
final class ForestStructureConflictProof {
    record Box(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        boolean contains(List<Integer> p){return p.get(0)>=minX&&p.get(0)<=maxX&&p.get(1)>=minY&&p.get(1)<=maxY&&p.get(2)>=minZ&&p.get(2)<=maxZ;}
        boolean intersects(Box b){return minX<=b.maxX&&maxX>=b.minX&&minY<=b.maxY&&maxY>=b.minY&&minZ<=b.maxZ&&maxZ>=b.minZ;}
    }
    record Piece(String key,String start,Box box) {}
    record Attempt(String reason,boolean placed,List<Box> ruinBounds) {}
    record Yielded(String structureClass,String site,Box coverage,boolean complete,List<Attempt> attempts) {}
    record Input(List<Box> ownBoxes,Set<String> ownPieces,Map<String,List<String>> references,
                 Map<String,List<Piece>> starts,List<Map<String,Object>> writes,
                 boolean ownRecordingComplete,boolean foreignRecordingComplete,Map<String,Yielded> yielded) {
        Input(List<Box> boxes,Set<String> own,Map<String,List<String>> refs,Map<String,List<Piece>> starts,List<Map<String,Object>> writes,boolean ownComplete,boolean foreignComplete){
            this(boxes,own,refs,starts,writes,ownComplete,foreignComplete,Map.of());
        }
    }
    static Set<String> chunks(List<Box> boxes){var out=new TreeSet<String>();for(var b:boxes)
        for(int x=b.minX>>4;x<=b.maxX>>4;x++)for(int z=b.minZ>>4;z<=b.maxZ>>4;z++)out.add(x+","+z);return out;}
    static Map<String,Object> inspect(Input input){
        var missing=new ArrayList<String>();var conflicts=new ArrayList<Map<String,Object>>();
        if(input.ownBoxes.isEmpty()||input.ownPieces.isEmpty())missing.add("OWN_IDENTITY_MISSING");
        if(!input.ownRecordingComplete)missing.add("OWN_WRITE_COVERAGE_INCOMPLETE");
        if(!input.foreignRecordingComplete)missing.add("FOREIGN_WRITE_COVERAGE_INCOMPLETE");
        var pieces=new TreeMap<String,Piece>();
        for(var chunk:chunks(input.ownBoxes)){
            var refs=input.references.get(chunk);if(refs==null){missing.add("REFERENCE_CHUNK_MISSING "+chunk);continue;}
            for(var ref:refs){var start=input.starts.get(ref);if(start==null||start.isEmpty()){missing.add("REFERENCED_START_UNRESOLVED "+ref);continue;}
                for(var piece:start)if(!input.ownPieces.contains(piece.key))pieces.put(piece.key,piece);}
        }
        var own=new HashMap<List<Integer>,List<Map<String,Object>>>();var foreign=new ArrayList<Map<String,Object>>();
        for(var write:input.writes){var p=position(write);var actor=Objects.toString(write.get("piece"),"");
            if(actor.isEmpty()){missing.add("WRITE_ACTOR_MISSING "+p);continue;}
            if(input.ownPieces.contains(actor)){
                own.computeIfAbsent(p,k->new ArrayList<>()).add(write);
                if(input.ownBoxes.stream().noneMatch(b->b.contains(p)))missing.add("OWN_WRITE_OUTSIDE_REGISTERED_BOX "+p);
            }else foreign.add(write);
        }
        var yielded=new TreeSet<String>();var yieldProofs=new ArrayList<Map<String,Object>>();
        for(var p:pieces.values()){
            var proof=yielded(input,p,foreign,own);if(proof!=null){yielded.add(p.key);yieldProofs.add(proof);}
        }
        for(var e:own.entrySet())for(var p:pieces.values())if(p.box.contains(e.getKey())&&!yielded.contains(p.key))
            conflicts.add(Map.of("rule","OWN_ATTEMPT_IN_FOREIGN_PIECE","position",e.getKey(),"foreignPiece",p,"ownWrites",e.getValue()));
        for(var w:foreign){var p=position(w);if(own.containsKey(p))
            conflicts.add(Map.of("rule","FOREIGN_WRITE_ON_OWN_CELL","position",p,"foreignWrite",w,"ownWrites",own.get(p)));}
        long attempts=own.values().stream().flatMap(List::stream).mapToLong(w->w.get("attempts") instanceof Number n?n.longValue():1).sum();
        var result=new LinkedHashMap<String,Object>();result.put("classification","foreign_structure_write_conflict");
        result.put("requiredReferenceChunks",chunks(input.ownBoxes).size());result.put("foreignPieces",pieces.size());
        result.put("ownCells",own.size());result.put("ownAttemptsIncludingUnchanged",attempts);
        result.put("bboxOnlyCandidates",pieces.values().stream().filter(p->input.ownBoxes.stream().anyMatch(b->b.intersects(p.box))).toList());
        result.put("conflicts",conflicts);result.put("conflictCount",conflicts.size());result.put("missing",missing.stream().distinct().toList());
        result.put("yieldedFeatureSiteExceptions",yieldProofs);
        result.put("accepted",missing.isEmpty()&&conflicts.isEmpty());return result;
    }
    private static Map<String,Object> yielded(Input input,Piece piece,List<Map<String,Object>> foreign,Map<List<Integer>,List<Map<String,Object>>> own){
        var e=input.yielded==null?null:input.yielded.get(piece.key);
        if(e==null||!input.foreignRecordingComplete||!e.complete||e.coverage==null||!e.coverage.equals(piece.box)
                ||!"cn.wwl.orimod.world.FeatureSiteStructure".equals(e.structureClass)||e.attempts==null||e.attempts.isEmpty())return null;
        String reason=switch(e.site){case "spirit_tree"->"RUIN_GUARD";case "blighted_grounds"->"RUIN";default->"";};
        if(reason.isEmpty())return null;
        for(var a:e.attempts)if(a.placed||!(reason.equals(a.reason)||a.reason.startsWith(reason+" at "))||a.ruinBounds==null||a.ruinBounds.stream().noneMatch(b->input.ownBoxes.stream().allMatch(x->
                b.contains(List.of(x.minX,x.minY,x.minZ))&&b.contains(List.of(x.maxX,x.maxY,x.maxZ)))))return null;
        if(foreign.stream().anyMatch(w->piece.key.equals(w.get("piece"))))return null;
        if(foreign.stream().anyMatch(w->{var p=position(w);return piece.box.contains(p)&&own.containsKey(p);}))return null;
        return Map.of("classification","foreign_feature_site_yielded","piece",piece,"evidence",e,"pieceWrites",0,"otherForeignWritesOnOwnCells",0,"accepted",true);
    }
    static List<Integer> position(Map<String,Object> write){var p=(List<?>)write.get("position");return p.stream().map(v->((Number)v).intValue()).toList();}
}
