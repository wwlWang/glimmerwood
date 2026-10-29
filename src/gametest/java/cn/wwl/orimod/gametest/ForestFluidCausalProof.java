package cn.wwl.orimod.gametest;

import java.util.*;
import java.util.function.Predicate;

/** Read-only evidence adjudication. No seed/coordinate/type allowance: every proof is run-local. */
final class ForestFluidCausalProof {
    private final List<Map<String,Object>> events,writes,own;
    private final Map<Integer,List<String>> stacks;
    private final Map<List<Integer>,List<Map<String,Object>>> writesByPosition=new HashMap<>(),ownByPosition=new HashMap<>();
    private final Predicate<List<Integer>> recorded;
    private final Map<String,Object> result=new LinkedHashMap<>();
    private final Map<List<Integer>,Map<String,Object>> gravityCache=new HashMap<>();
    private final Set<List<Integer>> gravityVisiting=new HashSet<>();
    private ForestFluidCausalProof(List<Map<String,Object>> events,Map<Integer,List<String>> stacks,Predicate<List<Integer>> recorded){
        this.events=events.stream().sorted(Comparator.comparingLong(ForestFluidCausalProof::seq)).toList();this.stacks=stacks;this.recorded=recorded;
        writes=this.events.stream().filter(e->"write".equals(e.get("type"))).toList();own=writes.stream().filter(this::ori).toList();
        for(var e:writes)writesByPosition.computeIfAbsent(pos(e),k->new ArrayList<>()).add(e);
        for(var e:own)ownByPosition.computeIfAbsent(pos(e),k->new ArrayList<>()).add(e);
    }
    static Map<String,Object> inspect(List<Map<String,Object>> events,Map<Integer,List<String>> stacks,List<Integer> target,Predicate<List<Integer>> recorded){
        var proof=new ForestFluidCausalProof(events,stacks,recorded);proof.result.put("position",target);
        try{proof.verify(target);proof.result.put("accepted",true);}catch(Missing e){proof.result.put("accepted",false);proof.result.put("missing",e.getMessage());}
        return proof.result;
    }
    private static final class Missing extends RuntimeException {Missing(String message){super(message);}}
    private static void require(boolean condition,String message){if(!condition)throw new Missing(message);}
    private void verify(List<Integer> target){
        require(own.stream().anyMatch(e->"main".equals(e.get("owner")))&&own.stream().anyMatch(e->"approach".equals(e.get("owner"))),"Missing own main/approach write coverage");
        var reversed=new ArrayList<Map<String,Object>>();var seen=new HashSet<List<Integer>>();var at=target;
        Map<String,Object> root;
        while(true){
            require(recorded.test(at)&&seen.add(at),"Missing coverage or cyclic flow chain at "+at);
            var pos=at;
            var sources=writes.stream().filter(e->pos(e).equals(pos)&&Boolean.TRUE.equals(e.get("changed"))
                    &&"Block{minecraft:water}[level=0]".equals(e.get("after"))&&!water(e.get("before"))).toList();
            if(!sources.isEmpty()){root=sources.getFirst();break;}
            var flows=writes.stream().filter(e->pos(e).equals(pos)&&Boolean.TRUE.equals(e.get("changed"))&&water(e.get("after"))&&!water(e.get("before"))
                    &&e.get("activeFluidTicks") instanceof List<?> p&&!p.isEmpty()&&caller(e,"FlowingFluid.spreadTo")).toList();
            require(!flows.isEmpty(),"Missing actual fluid tick parent at "+pos);
            var flow=flows.getFirst();reversed.add(flow);var parents=(List<?>)flow.get("activeFluidTicks");at=coordinates(parents.getLast());
            require(reversed.size()<=4096,"Flow chain recording bound exceeded");
        }
        require(!reversed.isEmpty(),"No observed expansion from source");Collections.reverse(reversed);
        require(!ori(root)&&!stack(root).isEmpty(),"Source has Ori or missing caller");
        boolean spring=caller(root,"SpringFeature.place"),carver=caller(root,"WorldCarver.carveBlock");
        require(spring||carver,"Source producer not yet adjudicated: "+pos(root));
        var cells=new ArrayList<List<Integer>>();cells.add(pos(root));long previous=seq(root);
        for(var flow:reversed){require(seq(flow)>previous&&!ori(flow),"Flow order/Ori failure");previous=seq(flow);cells.add(pos(flow));}
        for(var cell:cells)require(!ownByPosition.containsKey(cell),"Ori write on flow path "+cell);
        var source=pos(root);
        var schedules=events.stream().filter(e->e.get("type").toString().startsWith("schedule_before_")&&e.containsKey("fluid")&&pos(e).equals(source)).toList();
        require(!schedules.isEmpty(),"Missing first source fluid scheduling");var schedule=schedules.getFirst();
        require(!ori(schedule)&&!stack(schedule).isEmpty()&&seq(schedule)>seq(root)&&Boolean.FALSE.equals(schedule.get("alreadyScheduled")),"Invalid first source schedule");
        neighboursUntouched(source,seq(schedule));
        var ticks=events.stream().filter(e->"fluid_tick_begin".equals(e.get("type"))&&pos(e).equals(source)&&seq(e)>seq(schedule)&&seq(e)<seq(reversed.getFirst())).toList();
        require(!ticks.isEmpty()&&seq(ticks.getFirst())>seq(schedule)&&seq(ticks.getFirst())<seq(reversed.getFirst()),"Missing source scheduled tick execution");
        var gravity=new ArrayList<Map<String,Object>>();
        for(var cell:cells){
            var falls=writes.stream().filter(e->pos(e).equals(cell)&&Boolean.TRUE.equals(e.get("changed"))&&"Block{minecraft:gravel}".equals(e.get("before"))
                    &&"Block{minecraft:air}".equals(e.get("after"))&&caller(e,"FallingBlockEntity.fall")).toList();
            for(var fall:falls)gravity.add(gravityProof(fall));
        }
        if(spring){require(caller(schedule,"SpringFeature.place")&&caller(schedule,"LevelAccessor.scheduleTick"),"Spring initial scheduling not from its placement");}
        else {
            require(!gravity.isEmpty(),"Carver water lacks reviewed foreign gravity trigger");
            if(caller(schedule,"FallingBlockEntity.fall"))require(gravity.stream().anyMatch(g->adjacent(coordinates(g.get("position")),source)
                    &&((Number)g.get("fallSequence")).longValue()<seq(schedule)),"Fluid schedule has no matching proved gravel change");
            else {
                require(caller(schedule,"SinglePoolElement.place")&&caller(schedule,"SimpleWaterloggedBlock.placeLiquid"),"Unknown foreign source scheduling trigger");
                require(writes.stream().anyMatch(e->pos(e).equals(source)&&seq(e)<seq(schedule)&&caller(e,"SinglePoolElement.place")&&!ori(e)),"Missing foreign waterlogged block change");
            }
        }
        var arrivals=new ArrayList<Map<String,Object>>();arrivals.add(root);arrivals.addAll(reversed);
        indirectSlopeSearch(arrivals);
        var distances=new ArrayList<Map<String,Object>>();double minimum=Double.POSITIVE_INFINITY;
        var changed=own.stream().filter(e->Boolean.TRUE.equals(e.get("changed"))).toList();require(!changed.isEmpty(),"Missing changed own writes");
        for(var cell:cells){var nearest=changed.stream().min(Comparator.comparingDouble(e->distance(cell,pos(e)))).orElseThrow();double d=distance(cell,pos(nearest));minimum=Math.min(minimum,d);
            distances.add(Map.of("position",cell,"nearestOriWrite",pos(nearest),"distance3D",d,"sequence",seq(nearest)));}
        result.put("classification",spring?"natural_spring_with_recorded_fill_constraints":"foreign_structure_internal_gravel_and_carver_water");
        result.put("source",source);result.put("sourceWrite",root);result.put("sourceStack",stack(root));
        result.put("firstFluidSchedule",schedule);result.put("firstFluidScheduleStack",stack(schedule));result.put("firstSourceTick",ticks.getFirst());
        result.put("flowChain",reversed);result.put("gravityTriggers",gravity);result.put("pathOriWrites",0);result.put("sourceSixNeighboursOriWritesBeforeSchedule",0);
        result.put("nearestOriDistances",distances);result.put("minimumDistance3D",minimum);result.put("evidence","forest-lava-evidence.json");
    }
    /** Run-local conservative check of the fluid's four-block horizontal hole search. */
    private void indirectSlopeSearch(List<Map<String,Object>> arrivals){
        var rows=new ArrayList<Map<String,Object>>();var unique=new HashMap<Long,Map<String,Object>>();
        for(var arrival:arrivals){
            var center=pos(arrival);var removed=new ArrayList<Map<String,Object>>();var filled=new ArrayList<Map<String,Object>>();
            var retained=new ArrayList<Map<String,Object>>();var partial=new ArrayList<Map<String,Object>>();
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=-1;dy<=0;dy++){
                var q=List.of(center.get(0)+dx,center.get(1)+dy,center.get(2)+dz);
                require(recorded.test(q),"Slope-search coverage incomplete "+q);
                for(var write:ownByPosition.getOrDefault(q,List.of())){
                    if(seq(write)>=seq(arrival)||!Boolean.TRUE.equals(write.get("changed")))continue;
                    require(write.get("beforeFullCollision") instanceof Boolean&&write.get("afterFullCollision") instanceof Boolean,
                            "Missing full-collision evidence at "+q+" sequence "+seq(write));
                    boolean before=(Boolean)write.get("beforeFullCollision"),after=(Boolean)write.get("afterFullCollision");
                    (before&&!after?removed:!before&&after?filled:before?retained:partial).add(write);unique.put(seq(write),write);
                }
            }
            var row=new LinkedHashMap<String,Object>();row.put("chainCell",center);row.put("waterSequence",seq(arrival));row.put("waterTick",arrival.get("gameTick"));
            row.put("solidToNonSolid",removed);row.put("nonSolidToSolid",filled);row.put("solidToSolid",retained);row.put("nonSolidToNonSolid",partial);rows.add(row);
            result.put("indirectSlopeSearch",rows);
            require(removed.isEmpty(),"Ori solid-to-non-solid within slope search before water "+center+": "+removed.stream().map(ForestFluidCausalProof::pos).toList());
        }
        result.put("indirectUniqueWrites",unique.size());
        result.put("indirectUniqueFills",unique.values().stream().filter(e->Boolean.FALSE.equals(e.get("beforeFullCollision"))&&Boolean.TRUE.equals(e.get("afterFullCollision"))).count());
        result.put("indirectSolidToNonSolid",0);
    }
    private Map<String,Object> gravityProof(Map<String,Object> fall){
        var at=pos(fall);if(gravityCache.containsKey(at))return gravityCache.get(at);
        require(gravityVisiting.add(at)&&gravityVisiting.size()<=64,"Cyclic/missing upstream gravity history");
        require(!ori(fall),"Ori gravel fall trigger");
        var placements=writes.stream().filter(e->pos(e).equals(at)&&seq(e)<seq(fall)&&"Block{minecraft:gravel}".equals(e.get("after"))
                &&caller(e,"SinglePoolElement.place")&&!ori(e)).toList();require(!placements.isEmpty(),"Gravel was not placed by foreign template");
        var schedules=events.stream().filter(e->e.get("type").toString().startsWith("schedule_before_")&&"minecraft:gravel".equals(e.get("block"))&&pos(e).equals(at)).toList();
        require(!schedules.isEmpty(),"Missing gravel first block tick scheduling "+at);var schedule=schedules.getFirst();
        require(seq(schedule)<seq(fall)&&!ori(schedule)&&!stack(schedule).isEmpty()&&Boolean.FALSE.equals(schedule.get("alreadyScheduled")),"Invalid gravel scheduling");
        require(caller(schedule,"FallingBlock.onPlace")||caller(schedule,"FallingBlock.updateShape"),"Unproved gravel scheduling caller");
        Map<String,Object> trigger;
        if(caller(schedule,"SinglePoolElement.place"))trigger=Map.of("kind","foreign_template","sequence",seq(schedule));
        else if(caller(schedule,"FallingBlockEntity.fall")){
            var parents=writes.stream().filter(e->seq(e)<seq(schedule)&&adjacent(pos(e),at)&&Boolean.TRUE.equals(e.get("changed"))
                    &&"Block{minecraft:gravel}".equals(e.get("before"))&&"Block{minecraft:air}".equals(e.get("after"))&&caller(e,"FallingBlockEntity.fall")).toList();
            require(!parents.isEmpty(),"Missing parent gravel change");trigger=gravityProof(parents.getLast());
        }else{
            require(caller(schedule,"LevelChunk.postProcessGeneration")&&caller(schedule,"FlowingFluid.spreadTo")
                    &&schedule.get("activeFluidTicks") instanceof List<?> ps&&!ps.isEmpty(),"Unproved initial gravel trigger");
            var ps=(List<?>)schedule.get("activeFluidTicks");var waterSource=coordinates(ps.getLast());
            var roots=writes.stream().filter(e->pos(e).equals(waterSource)&&seq(e)<seq(schedule)&&"Block{minecraft:water}[level=0]".equals(e.get("after"))
                    &&caller(e,"WorldCarver.carveBlock")&&!ori(e)).toList();require(!roots.isEmpty(),"Missing carver source of gravel-triggering flow");
            var changes=writes.stream().filter(e->seq(e)<seq(schedule)&&adjacent(pos(e),at)&&Boolean.TRUE.equals(e.get("changed"))&&water(e.get("after"))
                    &&List.of(waterSource).equals(e.get("activeFluidTicks"))&&caller(e,"FlowingFluid.spreadTo")&&!ori(e)).toList();
            require(!changes.isEmpty(),"Missing water change scheduling gravel");var change=changes.getLast();
            var direct=events.stream().filter(e->"fluid_tick_begin".equals(e.get("type"))&&pos(e).equals(waterSource)&&seq(e)<seq(change)
                    &&caller(e,"LevelChunk.postProcessGeneration")&&!ori(e)).toList();require(!direct.isEmpty(),"Missing direct vanilla chunk postprocess tick");
            require(recorded.test(waterSource)&&!ownByPosition.containsKey(waterSource)&&!ownByPosition.containsKey(pos(change)),"Ori on upstream water trigger path");
            neighboursUntouched(waterSource,seq(direct.getLast()));
            trigger=Map.of("kind","carver_water_direct_vanilla_postprocess_tick","sourceWrite",roots.getFirst(),"sourceStack",stack(roots.getFirst()),
                    "directTick",direct.getLast(),"directTickStack",stack(direct.getLast()),"triggeringChange",change,"triggeringChangeStack",stack(change));
        }
        neighboursUntouched(at,seq(schedule));
        var below=List.of(at.get(0),at.get(1)-1,at.get(2));
        var supports=writes.stream().filter(e->pos(e).equals(below)&&seq(e)<seq(fall)&&"Block{minecraft:air}".equals(e.get("after"))&&caller(e,"SinglePoolElement.place")&&!ori(e)).toList();
        require(!supports.isEmpty(),"Missing foreign support removal");
        for(int y=-64;y<=at.get(1);y++){var cell=List.of(at.get(0),y,at.get(2));require(recorded.test(cell),"Falling column outside observer");
            require(!ownByPosition.containsKey(cell),"Ori write in falling/support column "+cell);}
        var r=new LinkedHashMap<String,Object>();r.put("position",at);r.put("placement",placements.getFirst());r.put("placementStack",stack(placements.getFirst()));
        r.put("fallSequence",seq(fall));r.put("fallTick",fall.get("gameTick"));r.put("firstBlockSchedule",schedule);r.put("firstBlockScheduleStack",stack(schedule));
        r.put("sixNeighboursOriBeforeSchedule",0);r.put("supportChanges",supports);r.put("supportStacks",supports.stream().map(this::stack).toList());r.put("fallColumnOriWrites",0);r.put("upstreamTrigger",trigger);gravityVisiting.remove(at);gravityCache.put(at,r);return r;
    }
    private void neighboursUntouched(List<Integer> p,long before){
        for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}){var q=List.of(p.get(0)+d[0],p.get(1)+d[1],p.get(2)+d[2]);
            require(recorded.test(q),"Six-neighbour coverage incomplete "+q);
            require(ownByPosition.getOrDefault(q,List.of()).stream().noneMatch(e->seq(e)<before),"Ori write before scheduling beside "+p);}
    }
    private List<String> stack(Map<String,Object> e){return e.get("stackId") instanceof Number n?stacks.getOrDefault(n.intValue(),List.of()):List.of();}
    private boolean caller(Map<String,Object> e,String text){return stack(e).stream().anyMatch(s->s.contains(text));}
    private boolean ori(Map<String,Object> e){return e.containsKey("owner")||caller(e,"cn.wwl.orimod.world.");}
    private static boolean water(Object state){return state!=null&&state.toString().startsWith("Block{minecraft:water}");}
    private static long seq(Map<String,Object> e){return ((Number)e.get("sequence")).longValue();}
    private static List<Integer> pos(Map<String,Object> e){return coordinates(e.get("position"));}
    private static List<Integer> coordinates(Object raw){var a=(List<?>)raw;return List.of(((Number)a.get(0)).intValue(),((Number)a.get(1)).intValue(),((Number)a.get(2)).intValue());}
    private static boolean adjacent(List<Integer> a,List<Integer> b){return Math.abs(a.get(0)-b.get(0))+Math.abs(a.get(1)-b.get(1))+Math.abs(a.get(2)-b.get(2))==1;}
    private static double distance(List<Integer> a,List<Integer> b){double x=a.get(0)-b.get(0),y=a.get(1)-b.get(1),z=a.get(2)-b.get(2);return Math.sqrt(x*x+y*y+z*z);}
}
