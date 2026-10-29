package cn.wwl.orimod.gametest;

import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;

/** Opt-in, test-only pre-refactor evidence. Full records accompany every digest. */
final class RuinFreezeSnapshot {
    static boolean enabled(){return System.getProperty("orimod.freezeOutput")!=null;}
    static Path directory(){return Path.of(System.getProperty("orimod.freezeOutput"));}
    static String canonical(Tag tag){
        if(tag instanceof CompoundTag c){var j=new StringJoiner(",","{","}");for(var k:new TreeSet<>(c.getAllKeys()))j.add(StringTag.quoteAndEscape(k)+":"+canonical(c.get(k)));return j.toString();}
        if(tag instanceof ListTag l){var j=new StringJoiner(",","[","]");for(var t:l)j.add(canonical(t));return j.toString();}
        return tag.toString();
    }
    record Image(List<String> states,List<String> entities,List<String> fluids) {}
    static Image capture(ServerLevel l,BoundingBox b){
        var states=new ArrayList<String>();var entities=new ArrayList<String>();
        for(var p:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())){
            var s=l.getBlockState(p);states.add(s.toString());
            if(s.hasBlockEntity()){var be=l.getBlockEntity(p);if(be!=null)entities.add(p.toShortString()+"\t"+canonical(be.saveWithFullMetadata(l.registryAccess())));}
        }
        var ticks=new ArrayList<ScheduledTick<Fluid>>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++){
            var container=(LevelChunkTicks<Fluid>)l.getChunk(x,z).getFluidTicks();container.getAll().filter(t->b.isInside(t.pos())).forEach(ticks::add);
        }
        ticks.sort(Comparator.comparingLong((ScheduledTick<Fluid> t)->t.triggerTick()).thenComparingInt(t->t.priority().ordinal()).thenComparingLong(t->t.subTickOrder()));
        var fluids=ticks.stream().map(t->t.pos().toShortString()+"\t"+BuiltInRegistries.FLUID.getKey(t.type())+"\t"+(t.triggerTick()-l.getGameTime())+"\t"+t.priority()).toList();
        return new Image(states,entities,fluids);
    }
    static Map<String,Object> save(ServerLevel l,BoundingBox b,String label){return save(capture(l,b),b,label);}
    static Map<String,Object> save(Image image,BoundingBox b,String label){
        if(!enabled())return Map.of();
        String name=label.replaceAll("[^a-zA-Z0-9_.-]","_");var out=new LinkedHashMap<String,Object>();
        out.put("label",label);out.put("bounds",List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));out.put("order","x fastest, then y, then z; absolute BE/tick coordinates; fluid relative schedule order/delay");
        try{
            Files.createDirectories(directory());
            for(var e:Map.of("states",image.states,"blockEntities",image.entities,"fluidTicks",image.fluids).entrySet()){
                var md=MessageDigest.getInstance("SHA-256");var file=directory().resolve(name+"."+e.getKey()+".txt.gz");
                try(var stream=new GZIPOutputStream(Files.newOutputStream(file))){for(String line:e.getValue()){byte[] bytes=(line+"\n").getBytes(StandardCharsets.UTF_8);md.update(bytes);stream.write(bytes);}}
                out.put(e.getKey(),Map.of("count",e.getValue().size(),"sha256",HexFormat.of().formatHex(md.digest()),"file",file.getFileName().toString()));
            }
            Files.writeString(directory().resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));return out;
        }catch(Exception e){throw new IllegalStateException("Cannot preserve freeze evidence "+label,e);}
    }
    static BoundingBox pinnedBounds(String name){
        try(var stream=RuinFreezeSnapshot.class.getResourceAsStream("/r116/forest-terrain-pre-kernel.json")){
            var a=com.google.gson.JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("fixtures").getAsJsonObject(name).getAsJsonArray("bounds");
            return new BoundingBox(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt(),a.get(4).getAsInt(),a.get(5).getAsInt());
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    static void verifyPinned(Image image,BoundingBox bounds,String name){
        try(var stream=RuinFreezeSnapshot.class.getResourceAsStream("/r116/forest-terrain-pre-kernel.json")){
            if(stream==null)throw new IllegalStateException("Missing pre-kernel baseline");
            var reference=com.google.gson.JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("fixtures").getAsJsonObject(name);
            if(reference==null)throw new IllegalStateException("Unknown baseline "+name);
            var actualBounds=List.of(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ());
            for(int i=0;i<6;i++)if(actualBounds.get(i)!=reference.getAsJsonArray("bounds").get(i).getAsInt())throw new AssertionError("Fixture coordinates changed "+name);
            for(var e:Map.of("states",image.states,"blockEntities",image.entities,"fluidTicks",image.fluids).entrySet()){
                var md=MessageDigest.getInstance("SHA-256");for(String line:e.getValue())md.update((line+"\n").getBytes(StandardCharsets.UTF_8));
                String actual=HexFormat.of().formatHex(md.digest()),expected=reference.getAsJsonObject(e.getKey()).get("sha256").getAsString();
                if(!actual.equals(expected))throw new AssertionError("Frozen "+name+" "+e.getKey()+" changed: expected="+expected+" actual="+actual+"; use raw freeze records for coordinate diff");
            }
        }catch(IOException e){throw new UncheckedIOException(e);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static void tag(String name,CompoundTag tag){if(!enabled())return;try{Files.createDirectories(directory());Files.writeString(directory().resolve(name+".snbt"),canonical(tag));}catch(IOException e){throw new UncheckedIOException(e);}}
    private RuinFreezeSnapshot(){}
}
