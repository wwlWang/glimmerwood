package cn.wwl.orimod.gametest;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/** Test-only isolation: climate ties retain the thread's previous nearest leaf.
 * Replay a production check and its exhaustive diagnostic with the SAME initial
 * cache, so diagnostic query order cannot change a boundary biome. */
record CreekAuditBiomeCache(ThreadLocal<Object> cache,Object value) {
    @SuppressWarnings("unchecked") static CreekAuditBiomeCache capture(BiomeSource source){
        if(!(source instanceof MultiNoiseBiomeSource))return new CreekAuditBiomeCache(null,null);
        try{var m=MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");m.setAccessible(true);Object p=m.invoke(source);var f=p.getClass().getDeclaredField("index");f.setAccessible(true);Object index=f.get(p);f=index.getClass().getDeclaredField("lastResult");f.setAccessible(true);var local=(ThreadLocal<Object>)f.get(index);return new CreekAuditBiomeCache(local,local.get());}
        catch(Exception e){throw new RuntimeException(e);}
    }
    void restore(){if(cache!=null){if(value==null)cache.remove();else cache.set(value);}}
}
