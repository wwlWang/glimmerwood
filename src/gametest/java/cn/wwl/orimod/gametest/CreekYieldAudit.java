package cn.wwl.orimod.gametest;

import com.google.gson.JsonArray;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.*;
import java.util.function.Function;

/** Counterfactual only: simultaneous, monotone creek veto + vanilla weighted fallback. */
final class CreekYieldAudit {
    private record Node(ChunkPos pos, Optional<CreekActualNeighbourAudit.Site> initial) {}
    private record Solution(Set<Long> banned, List<Map<String,Object>> rounds) {}

    static Map<String,Object> run(CreekActualNeighbourAudit audit, String policy, JsonArray points) {
        var nodes=new LinkedHashMap<Long,Node>();
        var targets=new LinkedHashSet<Long>();
        var queue=new ArrayDeque<Long>();
        for(var value:points){
            var q=value.getAsJsonObject().getAsJsonArray("chunk");
            var cp=new ChunkPos(q.get(0).getAsInt(),q.get(1).getAsInt());
            var node=new Node(cp,audit.selected(policy,cp));
            nodes.put(cp.toLong(),node);targets.add(cp.toLong());
            if(creek(node.initial))queue.add(cp.toLong());
            if(targets.size()%250==0)System.out.println("YIELD_TARGETS "+policy+" "+targets.size());
        }
        // Only initially selected creeks can ever change. Follow their entire
        // neighbour dependency component, including starts outside the sample.
        // Stable forest/cave/empty starts terminate a branch; no arbitrary halo.
        var expanded=new HashSet<Long>();
        while(!queue.isEmpty()){
            long key=queue.remove();if(!expanded.add(key))continue;
            for(var cp:audit.neighbours(nodes.get(key).pos)){
                var node=nodes.computeIfAbsent(cp.toLong(),unused->new Node(cp,audit.selected(policy,cp)));
                if(creek(node.initial)&&!expanded.contains(cp.toLong()))queue.add(cp.toLong());
            }
            if(nodes.size()>100_000)throw new IllegalStateException("Yield dependency component unexpectedly unbounded");
        }
        Function<Node,List<Long>> neighbours=n->audit.neighbours(n.pos).stream().map(ChunkPos::toLong).toList();
        Function<Node,Optional<CreekActualNeighbourAudit.Site>> fallback=n->audit.fallback(policy,n.pos);
        var initialCounts=counts(nodes,targets,Set.of(),fallback);
        var solution=solve(nodes,neighbours,fallback);
        var finalCounts=counts(nodes,targets,solution.banned,fallback);
        int initialCollisions=collisions(nodes,targets,Set.of(),neighbours,fallback,true);
        int finalHorizontal=collisions(nodes,targets,solution.banned,neighbours,fallback,true);
        int finalVolume=collisions(nodes,targets,solution.banned,neighbours,fallback,false);
        if(finalHorizontal!=0||finalVolume!=0)throw new IllegalStateException("Yield fixed point still collides");
        var changes=new LinkedHashMap<String,Integer>();
        for(String k:List.of("creek_ruins","forest_spring","cave_altar","NONE"))changes.put(k,finalCounts.get(k)-initialCounts.get(k));
        var details=new ArrayList<Map<String,Object>>();
        for(var e:nodes.entrySet())if(creek(e.getValue().initial)){
            var n=e.getValue();details.add(Map.of("chunk",List.of(n.pos.x,n.pos.z),"inSample",targets.contains(e.getKey()),
                    "initial",CreekActualNeighbourAudit.describe(n.initial),"banned",solution.banned.contains(e.getKey()),
                    "final",CreekActualNeighbourAudit.describe(current(e.getKey(),n,solution.banned,fallback))));
        }
        var out=new LinkedHashMap<String,Object>();out.put("targetCount",targets.size());out.put("closureStarts",nodes.size());
        out.put("closureInitiallySelectedCreeks",expanded.size());out.put("initialCounts",initialCounts);out.put("finalCounts",finalCounts);
        out.put("kindChanges",changes);out.put("initialCreeksColliding",initialCollisions);
        out.put("staticSelectedCreeksRemaining",initialCounts.get("creek_ruins")-initialCollisions);
        out.put("finalCreekHorizontalCollisions",finalHorizontal);out.put("finalCreekWriteBoxCollisions",finalVolume);
        out.put("rounds",solution.rounds);out.put("creekDetails",details);
        System.out.println("YIELD_COMPLETE "+policy+" "+initialCounts+" -> "+finalCounts+" rounds="+solution.rounds.size()+" closure="+nodes.size());
        return out;
    }

    private static Solution solve(Map<Long,Node> nodes,Function<Node,List<Long>> neighbours,
                                  Function<Node,Optional<CreekActualNeighbourAudit.Site>> fallback){
        var banned=new LinkedHashSet<Long>();var rounds=new ArrayList<Map<String,Object>>();
        for(;;){
            var next=new LinkedHashSet<Long>();var causes=new ArrayList<Map<String,Object>>();
            for(var e:nodes.entrySet()){
                long key=e.getKey();var node=e.getValue();if(banned.contains(key)||!creek(node.initial))continue;
                var hits=new ArrayList<Map<String,Object>>();
                for(long other:neighbours.apply(node)){
                    var adjacent=nodes.get(other);
                    if(adjacent==null)throw new IllegalStateException("Incomplete yield dependency closure at "+node.pos+" -> "+new ChunkPos(other));
                    var site=current(other,adjacent,banned,fallback);
                    if(site.isPresent()&&CreekActualNeighbourAudit.overlap(node.initial.orElseThrow(),site.get(),true))
                        hits.add(Map.of("chunk",List.of(adjacent.pos.x,adjacent.pos.z),"kind",site.get().kind()));
                }
                if(!hits.isEmpty()){next.add(key);causes.add(Map.of("chunk",List.of(node.pos.x,node.pos.z),"hits",hits));}
            }
            if(next.isEmpty())return new Solution(Set.copyOf(banned),List.copyOf(rounds));
            // Batch decisions, never update earlier starts while examining later
            // ones. Creek-creek conflicts veto both; forests/caves never yield.
            banned.addAll(next);rounds.add(Map.of("round",rounds.size()+1,"newlyBanned",next.size(),"causes",causes));
            if(rounds.size()>nodes.size())throw new IllegalStateException("Non-monotone yield solver");
        }
    }
    private static boolean creek(Optional<CreekActualNeighbourAudit.Site> s){return s.isPresent()&&s.get().kind().equals("creek_ruins");}
    private static Optional<CreekActualNeighbourAudit.Site> current(long key,Node n,Set<Long> banned,Function<Node,Optional<CreekActualNeighbourAudit.Site>> fallback){return banned.contains(key)?fallback.apply(n):n.initial;}
    private static Map<String,Integer> counts(Map<Long,Node> nodes,Set<Long> targets,Set<Long> banned,Function<Node,Optional<CreekActualNeighbourAudit.Site>> fallback){
        var out=new LinkedHashMap<String,Integer>();for(String kind:List.of("creek_ruins","forest_spring","cave_altar","NONE"))out.put(kind,0);
        for(long key:targets)out.merge(current(key,nodes.get(key),banned,fallback).map(CreekActualNeighbourAudit.Site::kind).orElse("NONE"),1,Integer::sum);return out;
    }
    private static int collisions(Map<Long,Node> nodes,Set<Long> targets,Set<Long> banned,Function<Node,List<Long>> neighbours,Function<Node,Optional<CreekActualNeighbourAudit.Site>> fallback,boolean horizontal){
        int count=0;for(long key:targets){var n=nodes.get(key);var own=current(key,n,banned,fallback);if(!creek(own))continue;
            for(long k:neighbours.apply(n)){var other=nodes.get(k);if(other==null)throw new IllegalStateException("Missing neighbour");
                var site=current(k,other,banned,fallback);if(site.isPresent()&&CreekActualNeighbourAudit.overlap(own.get(),site.get(),horizontal)){count++;break;}}
        }return count;
    }
    static void selfTest(){
        var nodes=new LinkedHashMap<Long,Node>();
        for(int i=0;i<4;i++){int low=new int[]{0,3,10,40}[i];nodes.put((long)i,new Node(new ChunkPos(i,0),Optional.of(new CreekActualNeighbourAudit.Site("creek_ruins",1,"NONE",List.of(new BoundingBox(low,0,0,low+4,4,4))))));}
        Function<Node,List<Long>> near=n->nodes.keySet().stream().filter(k->k!=n.pos.x).toList();
        Function<Node,Optional<CreekActualNeighbourAudit.Site>> back=n->n.pos.x==1?Optional.of(new CreekActualNeighbourAudit.Site("forest_spring",1,"NONE",List.of(new BoundingBox(3,0,0,11,4,4)))):Optional.empty();
        var solution=solve(nodes,near,back);
        if(!solution.banned.equals(Set.of(0L,1L,2L))||solution.rounds.size()!=2)throw new IllegalStateException("Yield fallback propagation self test");
        if(collisions(nodes,nodes.keySet(),solution.banned,near,back,true)!=0)throw new IllegalStateException("Yield survivor self test");
        var counts=counts(nodes,nodes.keySet(),solution.banned,back);
        if(counts.get("forest_spring")!=1||counts.get("creek_ruins")!=1)throw new IllegalStateException("Yield fixed-type preservation self test");
        System.out.println("YIELD_SELF_TEST PASS mutual veto, fallback propagation, survivor, forest preservation");
    }
}
