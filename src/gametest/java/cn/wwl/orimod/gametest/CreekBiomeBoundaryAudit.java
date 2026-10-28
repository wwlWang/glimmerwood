package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.*;

/** Read-only explanation of a climate nearest-neighbour tie. Restores thread-local cache. */
final class CreekBiomeBoundaryAudit {
    @SuppressWarnings({"rawtypes","unchecked"}) static Map<String,Object> run(ServerLevel level)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();for(var cp:List.of(new ChunkPos(62,-125),new ChunkPos(137,628)))rows.add(point(level,cp));return Map.of("points",rows);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static Map<String,Object> point(ServerLevel level,ChunkPos cp)throws Exception {
        var cs=level.getChunkSource();var g=cs.getGenerator();var source=g.getBiomeSource();
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RuinKind.CREEK.location());
        var c=new Structure.GenerationContext(level.registryAccess(),g,source,cs.randomState(),level.getStructureManager(),level.getSeed(),cp,level,structure.biomes()::contains);
        var l=RuinKind.CREEK.layout(1);var p=RuinPlacement.atMinimum(l,cp.getWorldPosition(),Rotation.getRandom(c.random()));var entry=BlockPos.containing(p.point(l.entry()));
        var pm=source.getClass().getDeclaredMethod("parameters");pm.setAccessible(true);Object parameters=pm.invoke(source),index=field(parameters,"index");
        ThreadLocal last=(ThreadLocal)field(index,"lastResult");Object saved=last.get();var leaves=new ArrayList<Object>();collect(field(index,"root"),leaves);
        var node=Class.forName("net.minecraft.world.level.biome.Climate$RTree$Node");var distance=node.getDeclaredMethod("distance",long[].class);distance.setAccessible(true);
        var array=Climate.TargetPoint.class.getDeclaredMethod("toParameterArray");array.setAccessible(true);
        var rows=new ArrayList<Map<String,Object>>();
        try{int delta=(int)Math.floor(l.entry().y)-l.surfaceY();
            for(int y=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE+delta);y<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE+delta);y++){
                var target=cs.randomState().sampler().sample(entry.getX()>>2,y,entry.getZ()>>2);var values=(long[])array.invoke(target);
                long min=Long.MAX_VALUE;var nearest=new ArrayList<Object>();
                for(var leaf:leaves){long d=(long)distance.invoke(leaf,(Object)values);if(d<min){min=d;nearest.clear();}if(d==min)nearest.add(leaf);}
                var results=new TreeSet<String>();var valid=new TreeSet<Boolean>();
                for(var leaf:nearest){last.set(leaf);var biome=source.getNoiseBiome(entry.getX()>>2,y,entry.getZ()>>2,cs.randomState().sampler());results.add(biome.unwrapKey().orElseThrow().location().toString());valid.add(c.validBiome().test(biome));}
                if(results.size()>1)rows.add(Map.of("quartY",y,"fitness",min,"leafCount",nearest.size(),"returnedBiomes",results,"validValues",valid));
            }
        }finally{if(saved==null)last.remove();else last.set(saved);}
        return Map.of("chunk",List.of(cp.x,cp.z),"entryXZ",List.of(entry.getX(),entry.getZ()),"ties",rows,"cacheRestored",true,"noProductionChanges",true);
    }
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void collect(Object n,List<Object> result)throws Exception{if(n.getClass().getSimpleName().equals("Leaf"))result.add(n);else for(var child:(Object[])field(n,"children"))collect(child,result);}
}
