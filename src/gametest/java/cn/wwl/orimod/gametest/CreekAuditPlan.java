package cn.wwl.orimod.gametest;
import cn.wwl.orimod.world.RuinPiece;
import cn.wwl.orimod.world.RuinKind;

import java.util.*;
import java.lang.reflect.*;
import java.util.function.IntBinaryOperator;

/** Test-only access to the exact saved production plan; no duplicated grade formula. */
public final class CreekAuditPlan {
    public record Column(int x,int z,int natural,int planned,boolean entrance) {}
    public static List<Column> columns(RuinPiece piece){
        if(piece.kind()!=RuinKind.CREEK||piece.layoutVersion()!=1)return List.of();
        try{
            var f=RuinPiece.class.getDeclaredField("creekTerrain");f.setAccessible(true);var terrain=f.get(piece);
            var terrainClass=terrain.getClass();
            var cf=terrainClass.getDeclaredField("contract");cf.setAccessible(true);var contract=cf.get(terrain);
            var rm=contract.getClass().getDeclaredMethod("rules");rm.setAccessible(true);var rules=rm.invoke(contract);
            var n=terrainClass.getDeclaredMethod("natural",int.class,int.class);n.setAccessible(true);
            var a=terrainClass.getDeclaredMethod("approachFloor",int.class,int.class);a.setAccessible(true);
            var pm=rules.getClass().getDeclaredMethod("plan",int.class,int.class,int.class,int.class,IntBinaryOperator.class,IntBinaryOperator.class);pm.setAccessible(true);
            var columns=(List<?>)pm.invoke(rules,-8,-8,52,74,call(n,terrain),call(a,terrain));var out=new ArrayList<Column>();
            for(var c:columns)out.add(new Column((int)value(c,"x"),(int)value(c,"z"),(int)value(c,"natural"),(int)value(c,"top"),(boolean)value(c,"entrance")));
            return List.copyOf(out);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static Object value(Object o,String key)throws ReflectiveOperationException{var m=o.getClass().getDeclaredMethod(key);m.setAccessible(true);return m.invoke(o);}
    private static IntBinaryOperator call(Method m,Object target){return (x,z)->{
        try{return (int)m.invoke(target,x,z);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    };}
    private CreekAuditPlan(){}
}
