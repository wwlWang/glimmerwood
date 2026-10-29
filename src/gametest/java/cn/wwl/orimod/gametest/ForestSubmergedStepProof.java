package cn.wwl.orimod.gametest;

import java.util.*;
import java.util.function.Predicate;

/** Independent, run-local exception. Does not change the existing deep-water contract. */
final class ForestSubmergedStepProof {
    record Column(int x,int z,long baselineSequence,int baselineGround,int currentGround,
                  int minY,List<String> before,List<String> after) {}
    private static final class Missing extends RuntimeException {Missing(String text){super(text);}}
    private static void require(boolean yes,String why){if(!yes)throw new Missing(why);}
    static Map<String,Object> inspect(List<Map<String,Object>> events,Map<Integer,List<String>> stacks,
                                      Column high,Column low,Predicate<List<Integer>> recorded){
        var result=new LinkedHashMap<String,Object>();
        result.put("classification","natural_submerged_untouched_step");
        try{
            require(Math.abs(high.x-low.x)+Math.abs(high.z-low.z)==1,"Not an adjacent edge");
            var proofs=new ArrayList<Map<String,Object>>();int waterLevel=Integer.MIN_VALUE;
            for(var c:List.of(high,low)){
                for(int y=-64;y<=319;y++)require(recorded.test(List.of(c.x,y,c.z)),"Column coverage incomplete "+List.of(c.x,y,c.z));
                require(c.currentGround==c.baselineGround,"Ground differs from CARVERS "+List.of(c.x,c.z));
                require(c.before.size()==c.after.size()&&!c.before.isEmpty(),"Missing baseline/current states");
                int ground=c.currentGround-c.minY;
                require(ground>=0&&ground+1<c.after.size(),"Ground outside snapshots");
                int top=ground;
                while(top+1<c.after.size()&&water(c.after.get(top+1)))top++;
                require(top>ground&&top+1<c.after.size(),"Dry side or incomplete water surface "+List.of(c.x,c.z));
                int surface=top+c.minY;
                require(waterLevel==Integer.MIN_VALUE||waterLevel==surface,"Water surfaces differ");waterLevel=surface;
                var replay=new ArrayList<>(c.before);var changes=new ArrayList<Map<String,Object>>();
                for(var e:events.stream().filter(e->"write".equals(e.get("type"))).sorted(Comparator.comparingLong(ForestSubmergedStepProof::seq)).toList()){
                    var p=pos(e);if(p.get(0)!=c.x||p.get(2)!=c.z)continue;
                    var stack=stacks.getOrDefault(number(e.get("stackId")),List.of());
                    require(!e.containsKey("owner")&&stack.stream().noneMatch(s->s.contains("cn.wwl.orimod.world.")),"Ori attempt in whole column "+p);
                    if(seq(e)<=c.baselineSequence||!Boolean.TRUE.equals(e.get("changed")))continue;
                    require(!stack.isEmpty(),"Missing non-Ori change stack "+p);
                    require(e.get("features") instanceof List<?>,"Missing feature context "+p);
                    changes.add(Map.of("event",e,"stack",stack,"features",e.get("features")));
                    int i=p.get(1)-c.minY;
                    if(i>=0&&i<replay.size()){
                        String before=Objects.toString(e.get("before")),after=Objects.toString(e.get("after"));
                        // Nested ProtoChunk/WorldGenLevel observations may record one write twice.
                        require(replay.get(i).equals(before)||replay.get(i).equals(after),"Unrecorded intervening change "+p);
                        replay.set(i,after);
                    }
                }
                require(replay.equals(c.after),"Recorded changes do not explain final column "+List.of(c.x,c.z));
                proofs.add(Map.of("column",List.of(c.x,c.z),"baselineSequence",c.baselineSequence,
                        "baselineGround",c.baselineGround,"currentGround",c.currentGround,"waterSurface",surface,
                        "waterDepth",surface-c.currentGround,"fullColumnOriAttempts",0,"changes",changes));
            }
            result.put("columns",proofs);result.put("accepted",true);
        }catch(Missing e){result.put("accepted",false);result.put("missing",e.getMessage());}
        return result;
    }
    private static boolean water(String state){return state.startsWith("Block{minecraft:water}[");}
    private static int number(Object n){return n instanceof Number v?v.intValue():-1;}
    private static long seq(Map<String,Object> e){return ((Number)e.get("sequence")).longValue();}
    private static List<Integer> pos(Map<String,Object> e){var p=(List<?>)e.get("position");return List.of(number(p.get(0)),number(p.get(1)),number(p.get(2)));}
}
