package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.util.*;

/** R103: command/devtools share the saved piece metadata; no block search or chunk loading. */
public final class RuinSecrets {
    public record Found(RuinKind kind, BlockPos entrance, Direction facing, String blocks) {
        public Map<String,Object> debug() { return Map.of("kind",kind.id,"entrance",List.of(entrance.getX(),entrance.getY(),entrance.getZ()),"facing",facing.getName(),"blocks",blocks); }
    }
    public record Result(boolean inRuin, List<Found> secrets) {
        public List<Map<String,Object>> debug() { return secrets.stream().map(Found::debug).toList(); }
    }
    public static Result find(ServerPlayer player) { return find(player.serverLevel(),player.blockPosition()); }
    public static Result find(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if (chunk == null) return new Result(false,List.of());
        var starts = new LinkedHashSet<StructureStart>(chunk.getAllStarts().values());
        for (var entry : chunk.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof RuinStructure)) continue;
            for (long reference : entry.getValue()) {
                var at = new ChunkPos(reference); var origin = level.getChunkSource().getChunkNow(at.x,at.z);
                if (origin != null) { var start = origin.getStartForStructure(entry.getKey()); if (start != null) starts.add(start); }
            }
        }
        var result = new ArrayList<Found>(); boolean inside = false;
        for (var start : starts) if (start.isValid() && start.getStructure() instanceof RuinStructure
                && start.getPieces().stream().anyMatch(p -> p.getBoundingBox().isInside(pos))) {
            inside = true;
            for (var piece : start.getPieces()) if (piece instanceof RuinPiece ruin && ruin.secret() != null) {
                var secret = ruin.secret();
                result.add(new Found(ruin.kind(),ruin.placement().block(secret.entrance()),ruin.getRotation().rotate(secret.facing()),secret.blocks()));
            }
        }
        return new Result(inside,List.copyOf(result));
    }
    private RuinSecrets() {}
}
