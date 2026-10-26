import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
import cn.wwl.orimod.world.RuinKind;

/** Plain java: no NeoForge launcher, server, registry bootstrap or GreatTreeSites initialization. */
public final class ForestLayoutProbe {
    public static void main(String[] args)throws Exception{
        var root=Path.of(args[0]);
        // Optional single version allows genuinely separate cold JVM samples.
        int first=args.length>1?Integer.parseInt(args[1]):0,last=args.length>1?first:1;
        if(first<0||last>1)throw new IllegalArgumentException("version must be 0 or 1");
        for(int version=first;version<=last;version++){
            var path=root.resolve("src/main/resources/data/orimod/structure/ruins/forest_spring"+(version==0?"":"_v1")+".nbt");
            long start=System.nanoTime();var data=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());long read=System.nanoTime()-start;
            var palette=data.getList("palette",Tag.TAG_COMPOUND);var cells=data.getList("blocks",Tag.TAG_COMPOUND);
            var counts=new TreeMap<String,Integer>();var parts=new TreeMap<String,Integer>();var slice=new TreeMap<String,Integer>();
            var occupied=new HashSet<String>();
            for(var v:cells){var c=(CompoundTag)v;var state=palette.getCompound(c.getInt("state"));String name=state.getString("Name");
                counts.merge(name,1,Integer::sum);var pos=c.getList("pos",Tag.TAG_INT);
                if(!occupied.add(pos.toString()))throw new AssertionError("Repeated cell "+pos);
                slice.merge((pos.getInt(0)>>4)+","+(pos.getInt(2)>>4),1,Integer::sum);
                if(name.equals("orimod:sanctuary_part"))parts.merge(state.getCompound("Properties").getString("kind"),1,Integer::sum);
                if(version==1 && name.equals("orimod:keystone_door") && state.getCompound("Properties").getString("part").equals("legacy"))throw new AssertionError("Legacy door");
            }
            if(version==1 && !parts.equals(Map.of("forest_spring_shrine",26,"storm_altar",26,"map_stone",2)))throw new AssertionError(parts);
            System.out.println("v"+version+" size="+data.getList("size",Tag.TAG_INT)+" readMs="+read/1e6+" cells="+cells.size()+" maximumChunkCells="+Collections.max(slice.values())+" parts="+parts);
            System.out.println("counts="+counts);System.out.println("slices="+slice);
            System.out.println("layout="+RuinKind.FOREST.layout(version).size());
        }
    }
}
