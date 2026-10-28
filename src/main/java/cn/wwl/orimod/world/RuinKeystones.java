package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.List;

/** One saved-piece identity shared by pedestal keys, doors and dormant altar eligibility. Never loads chunks. */
public final class RuinKeystones {
    public enum DoorState { CLOSED, OPEN, UNLOADED }
    public record Site(String id,List<BlockPos> doors) {
        public Site { doors=List.copyOf(doors); }
        public DoorState state(ServerLevel level){
            if(KeystoneTimers.get(level).opened(id))return DoorState.OPEN;
            if(doors.isEmpty()||doors.stream().anyMatch(p->level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null))return DoorState.UNLOADED;
            return doors.stream().anyMatch(p->level.getBlockState(p).is(OriBlocks.KEYSTONE_DOOR.get()))?DoorState.CLOSED:DoorState.OPEN;
        }
    }
    public static Site site(ServerLevel level,RuinPiece piece){
        var p=piece.placement();
        String id=level.dimension().location()+"/"+piece.kind().id+"/"+piece.layoutVersion()+"/"+p.origin().asLong()+"/"+p.rotation().name();
        return new Site(id,doors(level,piece));
    }
    private static List<BlockPos> doors(ServerLevel level,RuinPiece piece){
        var p=piece.placement();var vault=piece.layout().vault();
        if(vault!=null)return vault.door().stream().map(p::block).toList();
        return level.getStructureManager().getOrCreate(piece.kind().template(piece.layoutVersion()))
                .filterBlocks(p.origin(),new StructurePlaceSettings().setRotation(p.rotation()),OriBlocks.KEYSTONE_DOOR.get()).stream().map(c->c.pos()).toList();
    }
    public static Site atPedestal(ServerLevel level,BlockPos pos){
        var piece=RuinLayouts.find(level,pos,p->p.layout().keystones().stream().anyMatch(local->p.placement().block(local).equals(pos)));
        return piece==null?null:site(level,piece);
    }
    public static Site atDoor(ServerLevel level,BlockPos pos){
        var piece=RuinLayouts.find(level,pos,p->p.getBoundingBox().isInside(pos)&&doors(level,p).contains(pos));
        return piece==null?null:site(level,piece);
    }
    public static Site atCaveAltar(ServerLevel level,BlockPos pos){
        var piece=RuinLayouts.find(level,pos,p->p.kind()==RuinKind.CAVE&&p.placement().block(p.layout().bossAltar()).equals(pos));
        return piece==null?null:site(level,piece);
    }
    /** Distinguish a player-built altar from an unavailable referenced start. */
    public static boolean unresolvedCaveReference(ServerLevel level,BlockPos pos){
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null)return true;
        for(var e:chunk.getAllReferences().entrySet())if(e.getKey() instanceof RuinStructure && !e.getValue().isEmpty()){
            var registry=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            if(!RuinKind.CAVE.location().equals(registry.getKey(e.getKey())))continue;
            for(long packed:e.getValue()){
                var cp=new net.minecraft.world.level.ChunkPos(packed);
                var origin=level.getChunkSource().getChunkNow(cp.x,cp.z);
                if(origin==null||origin.getStartForStructure(e.getKey())==null||!origin.getStartForStructure(e.getKey()).isValid())return true;
            }
        }
        return false;
    }
    private RuinKeystones(){}
}
