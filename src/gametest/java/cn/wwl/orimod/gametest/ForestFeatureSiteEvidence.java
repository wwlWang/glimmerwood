package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.FeatureSitePiece;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Observes the calls already made by the feature. No second preflight or world read. */
public final class ForestFeatureSiteEvidence {
    private static final class Recording {
        final List<ForestStructureConflictProof.Attempt> attempts=new ArrayList<>();
        final Set<ForestStructureConflictProof.Box> guards=new LinkedHashSet<>();
        boolean placed,complete;
    }
    private static final ThreadLocal<Recording> ACTIVE=new ThreadLocal<>();
    private static final Map<String,Recording> PIECES=new ConcurrentHashMap<>();
    public static void begin(FeatureSitePiece piece,ChunkPos chunk){
        if(!ForestStructureEvidence.ENABLED||!chunk.equals(new ChunkPos(piece.origin())))return;
        String key=ForestStructureEvidence.currentActorKey();
        if(key==null)return;
        var r=new Recording();PIECES.put(key,r);ACTIVE.set(r);
    }
    public static void end(boolean normal){var r=ACTIVE.get();if(r!=null){r.complete=normal;ACTIVE.remove();}}
    public static void attempt(){var r=ACTIVE.get();if(r!=null){r.guards.clear();r.placed=false;}}
    public static void placed(boolean result){var r=ACTIVE.get();if(r!=null){r.placed=result;if(result)r.attempts.add(new ForestStructureConflictProof.Attempt("PASS",true,List.copyOf(r.guards)));}}
    public static void reason(String reason){var r=ACTIVE.get();if(r!=null)r.attempts.add(new ForestStructureConflictProof.Attempt(reason,r.placed,List.copyOf(r.guards)));}
    public static void guard(BoundingBox b){var r=ACTIVE.get();if(r!=null)r.guards.add(box(b));}
    static ForestStructureConflictProof.Box box(BoundingBox b){return new ForestStructureConflictProof.Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
    static ForestStructureConflictProof.Yielded proof(String key,String structureClass,FeatureSitePiece piece){
        var r=PIECES.get(key);return r==null?null:new ForestStructureConflictProof.Yielded(structureClass,piece.site().id(),box(piece.getBoundingBox()),r.complete,List.copyOf(r.attempts));
    }
    private ForestFeatureSiteEvidence(){}
}
