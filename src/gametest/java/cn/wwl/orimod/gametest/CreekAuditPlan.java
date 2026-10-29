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
        if(piece.layoutVersion()!=1||piece.kind()!=RuinKind.CREEK&&piece.forestTerrainVersion()!=1)return List.of();
        try{
            var f=RuinPiece.class.getDeclaredField(piece.kind()==RuinKind.FOREST?"gradedForest":"creekTerrain");f.setAccessible(true);var terrain=f.get(piece);
            var terrainClass=fieldOwner(terrain.getClass(),"contract");
            var cf=terrainClass.getDeclaredField("contract");cf.setAccessible(true);var contract=cf.get(terrain);
            var rm=contract.getClass().getDeclaredMethod("rules");rm.setAccessible(true);var rules=rm.invoke(contract);
            var n=terrainClass.getDeclaredMethod("natural",int.class,int.class);n.setAccessible(true);
            var a=terrainClass.getDeclaredMethod("approachFloor",int.class,int.class);a.setAccessible(true);
            var pm=method(rules.getClass(),"plan",int.class,int.class,int.class,int.class,IntBinaryOperator.class,IntBinaryOperator.class);pm.setAccessible(true);
            var columns=(List<?>)pm.invoke(rules,-8,-8,piece.layout().size().getX()+7,piece.layout().size().getZ()+7,call(n,terrain),call(a,terrain));var out=new ArrayList<Column>();
            for(var c:columns)out.add(new Column((int)value(c,"x"),(int)value(c,"z"),(int)value(c,"natural"),(int)value(c,"top"),(boolean)value(c,"entrance")));
            return List.copyOf(out);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    /** Observe both ends of a rim/seam edge. Read-only columns have no grading
     * target: their unchanged target P is the saved natural N, never a value
     * inferred from the current (possibly already modified) world. */
    static List<Column> observationColumns(RuinPiece piece){
        var out=new ArrayList<>(columns(piece));
        if(out.isEmpty())return List.of();
        try{
            var f=RuinPiece.class.getDeclaredField(piece.kind()==RuinKind.FOREST?"gradedForest":"creekTerrain");f.setAccessible(true);var terrain=f.get(piece);
            var owner=fieldOwner(terrain.getClass(),"contract");
            var cf=owner.getDeclaredField("contract");cf.setAccessible(true);var contract=cf.get(terrain);
            var rm=contract.getClass().getDeclaredMethod("rules");rm.setAccessible(true);var rules=rm.invoke(contract);
            var seam=method(rules.getClass(),"readOnlySeam",int.class,int.class);seam.setAccessible(true);
            var n=owner.getDeclaredMethod("natural",int.class,int.class);n.setAccessible(true);
            for(int x=-9;x<=piece.layout().size().getX()+8;x++)for(int z=-9;z<=piece.layout().size().getZ()+8;z++)
                if((boolean)seam.invoke(rules,x,z)){
                    int natural=(int)n.invoke(terrain,x,z);
                    out.add(new Column(x,z,natural,natural,false));
                }
            return List.copyOf(out);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    // The historical creek API remains available on both binary generations.
    // Forest-only access stays on the new API; this does not duplicate ownership rules.
    static boolean ownsGuard(RuinPiece piece,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        return piece.kind()==RuinKind.CREEK?piece.ownsCreekGuard(pos,state):piece.ownsTerrainGuard(pos,state);
    }
    static boolean ownsSupport(RuinPiece piece,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        return piece.kind()==RuinKind.CREEK?piece.ownsCreekSupport(pos,state):piece.ownsTerrainSupport(pos,state);
    }
    private static Class<?> fieldOwner(Class<?> type,String field)throws NoSuchFieldException{
        for(var t=type;t!=null;t=t.getSuperclass())try{t.getDeclaredField(field);return t;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(type.getName()+"."+field);
    }
    static Method method(Class<?> type,String name,Class<?>... parameters)throws NoSuchMethodException{
        for(var t=type;t!=null;t=t.getSuperclass())try{return t.getDeclaredMethod(name,parameters);}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static Object value(Object o,String key)throws ReflectiveOperationException{var m=o.getClass().getDeclaredMethod(key);m.setAccessible(true);return m.invoke(o);}
    private static IntBinaryOperator call(Method m,Object target){return (x,z)->{
        try{return (int)m.invoke(target,x,z);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    };}
    private CreekAuditPlan(){}
}
