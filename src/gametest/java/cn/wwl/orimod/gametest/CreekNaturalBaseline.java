package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Actual CARVERS terrain, never an interpolated noise approximation. Audit only. */
final class CreekNaturalBaseline {
    private static final int MIN=-32,MAX=40;
    private record Column(BlockState[] states,int ground,boolean beforeFeatures,BlockState[] wholeColumn) {}
    private final RuinPiece piece;
    private final Map<BlockPos,Column> columns;
    private final long evidenceSequence;
    private CreekNaturalBaseline(RuinPiece p,Map<BlockPos,Column> c,long seq){piece=p;columns=Map.copyOf(c);evidenceSequence=seq;}
    static CreekNaturalBaseline capture(ServerLevel level,RuinPiece piece){
        var chunks=new HashMap<ChunkPos,ChunkAccess>();var b=piece.getBoundingBox();
        for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++)
            chunks.put(new ChunkPos(x,z),level.getChunk(x,z,ChunkStatus.CARVERS,true));
        CreekForeignTerrainTrace.register(piece,chunks.values());
        ForestLavaEvidence.baselineChunks(chunks.values());
        long evidenceSequence=ForestLavaEvidence.sequence();
        var out=new HashMap<BlockPos,Column>();var placement=piece.placement();
        for(var q:CreekTerrainGameTests.scanColumns(level,piece)){
            var states=new BlockState[MAX-MIN+1];var chunk=chunks.get(new ChunkPos(placement.block(q)));int floor=MIN-1;
            boolean known=chunk!=null&&!chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES);
            if(chunk==null)throw new IllegalStateException("Baseline column outside captured chunks "+q);
            for(int y=MIN;y<=MAX;y++){
                var at=placement.block(q.atY(y));var state=chunk.getBlockState(at);states[y-MIN]=state;
                if(CreekTerrainSurface.ground(state,level,at))floor=y;
            }
            BlockState[] whole=null;
            if(ForestLavaEvidence.target(placement.block(q))){
                whole=new BlockState[384];var at=placement.block(q);
                for(int y=-64;y<=319;y++)whole[y+64]=chunk.getBlockState(at.atY(y));
            }
            out.put(q,new Column(states,floor,known,whole));
            ForestLavaEvidence.baselineColumn(placement.block(q));
        }
        return new CreekNaturalBaseline(piece,out,evidenceSequence);
    }
    private int waterTop(Column c){
        int y=c.ground+1;
        while(y<=MAX&&c.states[y-MIN].getFluidState().is(FluidTags.WATER))y++;
        return y-1;
    }
    private boolean unchangedWet(ServerLevel level,BlockPos q,int actualGround){
        var c=columns.get(q);if(c==null)return false;
        return unchangedWet(c.states,c.ground,actualGround,c.beforeFeatures,y->level.getBlockState(piece.placement().block(q.atY(y))));
    }
    static boolean unchangedWet(BlockState[] before,int ground,int actualGround,boolean known,java.util.function.IntFunction<BlockState> after){
        if(!known||ground<MIN||ground>=MAX||ground!=actualGround)return false;
        int top=ground;
        while(top<MAX&&before[top+1-MIN].getFluidState().is(FluidTags.WATER))top++;
        if(top-ground<2)return false;
        // Compare the floor block and entire water column, not merely height.
        // Ore FEATURES beneath the riverbed are irrelevant to a walking edge. Changed
        // ground, filled water, or an unknown pre-feature history cannot be waived.
        for(int y=ground;y<=Math.min(MAX,top+1);y++)if(after.apply(y)!=before[y-MIN])return false;
        return true;
    }
    boolean unchangedSubmergedStep(ServerLevel level,BlockPos p,int y,BlockPos q,int other){
        return unchangedWet(level,p,y)&&unchangedWet(level,q,other);
    }
    Map<String,Object> untouchedSubmergedStep(ServerLevel level,BlockPos p,int y,BlockPos q,int other){
        if(piece.kind()!=RuinKind.FOREST||piece.forestTerrainVersion()!=1)return null;
        var a=evidenceColumn(level,p,y);var b=evidenceColumn(level,q,other);
        return a==null||b==null?null:ForestLavaEvidence.submergedStep(a,b);
    }
    private ForestSubmergedStepProof.Column evidenceColumn(ServerLevel level,BlockPos p,int actual){
        var c=columns.get(p);if(c==null||!c.beforeFeatures||c.wholeColumn==null)return null;
        var at=piece.placement().block(p);var after=new ArrayList<String>();
        for(int y=-64;y<=319;y++)after.add(level.getBlockState(at.atY(y)).toString());
        return new ForestSubmergedStepProof.Column(at.getX(),at.getZ(),evidenceSequence,
                piece.origin().getY()+c.ground,piece.origin().getY()+actual,-64,
                Arrays.stream(c.wholeColumn).map(Object::toString).toList(),List.copyOf(after));
    }
    Map<String,Object> auditColumn(ServerLevel level,BlockPos p,int currentGround){
        var c=columns.get(p);var at=piece.placement().block(p.atY(currentGround));
        var out=new LinkedHashMap<String,Object>();
        out.put("local",List.of(p.getX(),currentGround,p.getZ()));
        out.put("world",List.of(at.getX(),at.getY(),at.getZ()));
        out.put("state",level.getBlockState(at).toString());
        out.put("currentTop",CreekTerrainSurface.standingTop(level.getBlockState(at),level,at));
        out.put("carversKnown",c!=null&&c.beforeFeatures);
        out.put("wholeColumnRecorded",c!=null&&c.wholeColumn!=null);
        if(c!=null){out.put("carversGround",piece.origin().getY()+c.ground);out.put("baselineSequence",evidenceSequence);}
        return out;
    }
    Map<String,Object> exception(BlockPos p,BlockPos q){
        var a=columns.get(p);var b=columns.get(q);
        return Map.of("from",List.of(p.getX(),a.ground,p.getZ()),"to",List.of(q.getX(),b.ground,q.getZ()),
                "beforeStage","CARVERS","unchangedGroundAndWater",true,"waterDepthFrom",waterTop(a)-a.ground,"waterDepthTo",waterTop(b)-b.ground);
    }
    Map<String,Object> report(ServerLevel level){
        int before=0,after=0,unknown=0;var removed=new ArrayList<List<Integer>>();var added=new ArrayList<List<Integer>>();var changed=new ArrayList<Map<String,Object>>();
        for(var e:columns.entrySet()){
            var q=e.getKey();var c=e.getValue();if(!c.beforeFeatures){unknown++;continue;}
            int beforeCount=0,afterCount=0,groundAfter=MIN-1;
            for(int y=MIN;y<=MAX;y++){
                var at=piece.placement().block(q.atY(y));var s=level.getBlockState(at);
                boolean old=c.states[y-MIN].getFluidState().is(FluidTags.WATER),now=s.getFluidState().is(FluidTags.WATER);
                if(old){before++;beforeCount++;}if(now){after++;afterCount++;}
                if(old&&!now)removed.add(List.of(q.getX(),y,q.getZ()));if(!old&&now)added.add(List.of(q.getX(),y,q.getZ()));
                if(CreekTerrainSurface.ground(s,level,at))groundAfter=y;
            }
            if(beforeCount>0||afterCount>0)changed.add(Map.of("local",List.of(q.getX(),q.getZ()),"groundBefore",c.ground,"groundAfter",groundAfter,"waterBefore",beforeCount,"waterAfter",afterCount));
        }
        return Map.of("stage","CARVERS before FEATURES","knownColumns",columns.size()-unknown,"unavailableBeforeColumns",unknown,
                "waterBefore",before,"waterAfter",after,"removed",removed,"added",added,"waterColumns",changed);
    }
}
