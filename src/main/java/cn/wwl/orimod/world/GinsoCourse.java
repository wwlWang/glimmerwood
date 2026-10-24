package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.Optional;

/** Loaded structure references only; exploration never loads a start chunk or allocates a global session. */
public final class GinsoCourse {
    public static GinsoPiece at(ServerLevel level,BlockPos at){
        var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);if(chunk==null)return null;
        for(var entry:chunk.getAllReferences().entrySet())if(entry.getKey() instanceof GinsoStructure)
            for(long packed:entry.getValue()){
                var startChunk=level.getChunkSource().getChunkNow(ChunkPos.getX(packed),ChunkPos.getZ(packed));if(startChunk==null)continue;
                var start=startChunk.getStartForStructure(entry.getKey());if(start==null||!start.isValid())continue;
                for(var piece:start.getPieces())if(piece instanceof GinsoPiece g&&g.hasCourse()&&g.getBoundingBox().isInside(at))return g;
            }
        return null;
    }
    public static boolean hint(ServerPlayer player){
        var piece=at(player.serverLevel(),player.blockPosition());if(piece==null)return false;
        var local=piece.local(player.blockPosition());if(Math.abs(local.getX())>6||Math.abs(local.getZ())>12)return false;
        var section=piece.course().section(local.getY());if(section==null)return false;
        var hint=CompanionHints.Hint.byWireId("ginso_"+section.id());
        return CompanionHints.offer(player,hint,piece.point(section.start()));
    }
    /** A deliberate right-click replaces the Soul Link at a known, dry landing beside this well. */
    public static boolean checkpoint(ServerPlayer player,BlockPos well){
        var piece=at(player.serverLevel(),well);if(piece==null||!piece.course().wells().contains(piece.local(well)))return false;
        var feet=piece.point(piece.local(well).north(2));var level=player.serverLevel();
        if(!level.hasChunkAt(feet)||!SoulLink.usable(level,feet)||!level.noCollision(player,player.getBoundingBox().move(
                feet.getX()+.5-player.getX(),feet.getY()-player.getY(),feet.getZ()+.5-player.getZ())))return false;
        player.setData(SpiritData.SOUL_LINK,Optional.of(new SoulLink.Link(level.dimension(),feet)));
        player.displayClientMessage(Component.translatable("message.orimod.ginso.checkpoint"),true);
        return true;
    }
    private GinsoCourse(){}
}
