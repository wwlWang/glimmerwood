package cn.wwl.orimod.world;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The generated template's contract is copied into each piece and its altar chunk journal. */
public record ShrineContract(BlockPos altar,BlockPos entry,List<BlockPos> wards,List<BlockPos> spawns) {
    public static final BlockPos SIZE=new BlockPos(25,14,25), ALTAR=new BlockPos(12,4,12), ENTRY=new BlockPos(12,5,0);
    public ShrineContract {altar=altar.immutable();entry=entry.immutable();wards=List.copyOf(wards);spawns=List.copyOf(spawns);}
    private static final JsonObject LAYOUT=readLayout();
    private static JsonObject readLayout(){
        try(var in=ShrineContract.class.getResourceAsStream("/orimod/spirit_shrine.json")){
            if(in==null)throw new IllegalStateException("Missing shrine contract");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static ShrineContract at(RuinPlacement at){return new ShrineContract(at.block(ALTAR),at.block(ENTRY),positions(at,"wards"),positions(at,"spawns"));}
    private static List<BlockPos> positions(RuinPlacement at,String key){
        var out=new ArrayList<BlockPos>();for(var v:LAYOUT.getAsJsonArray(key)){var a=v.getAsJsonArray();out.add(at.block(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt())));}return out;
    }
    /** Centre-to-centre cylinder; the 3-block grace is only the escape failure threshold. */
    public boolean inside(Vec3 p,double grace){double dx=p.x-altar.getX()-.5,dz=p.z-altar.getZ()-.5;return dx*dx+dz*dz<=(10.5+grace)*(10.5+grace) && p.y>=altar.getY()-3-grace && p.y<=altar.getY()+10+grace;}
    public Set<Long> chunks(){var out=new HashSet<Long>();for(var p:wards)out.add(net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));return out;}
    public CompoundTag save(){var t=new CompoundTag();t.putLong("altar",altar.asLong());t.putLong("entry",entry.asLong());t.putLongArray("wards",wards.stream().mapToLong(BlockPos::asLong).toArray());t.putLongArray("spawns",spawns.stream().mapToLong(BlockPos::asLong).toArray());return t;}
    public static ShrineContract load(CompoundTag t){return new ShrineContract(BlockPos.of(t.getLong("altar")),BlockPos.of(t.getLong("entry")),Arrays.stream(t.getLongArray("wards")).mapToObj(BlockPos::of).toList(),Arrays.stream(t.getLongArray("spawns")).mapToObj(BlockPos::of).toList());}
}
