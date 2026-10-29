package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.RuinPiece;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;

/** Test-only proof for foreign internal edges. Snapshots are outside outer write timing;
 * the write observer only increments an existing per-column integer (no allocation/log). */
public final class CreekForeignTerrainTrace {
    private static final Map<Long,Column[]> CHUNKS=new ConcurrentHashMap<>();
    private static final ThreadLocal<Column[]> ACTIVE=new ThreadLocal<>();
    private static final class Column {
        final int x,z; BoundingBox portal;
        CreekAuditPlan.Column plan; int originY; boolean cave,seen; BlockState[] afterOwn; int beforeH;
        BlockState[] before; int minY; volatile int writes;
        Column(int x,int z,BoundingBox portal){this.x=x;this.z=z;this.portal=portal;}
    }
    static void register(RuinPiece creek,Collection<ChunkAccess> chunks){
        var b=creek.getBoundingBox();
        for(var plan:CreekAuditPlan.observationColumns(creek)){
            var pos=creek.placement().block(new BlockPos(plan.x(),0,plan.z()));
            var columns=CHUNKS.computeIfAbsent(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4),k->new Column[256]);
            var c=new Column(pos.getX(),pos.getZ(),null);c.plan=plan;c.originY=creek.origin().getY();
            columns[(pos.getZ()&15)*16+(pos.getX()&15)]=c;
        }
        for(var chunk:chunks)for(var start:chunk.getAllStarts().values())if(start.isValid())
            for(var piece:start.getPieces())if(piece instanceof RuinedPortalPiece){
                var box=piece.getBoundingBox();
                if(!b.intersects(box))continue;
                for(int x=Math.max(box.minX(),b.minX());x<=Math.min(box.maxX(),b.maxX());x++)
                    for(int z=Math.max(box.minZ(),b.minZ());z<=Math.min(box.maxZ(),b.maxZ());z++){
                        var columns=CHUNKS.computeIfAbsent(ChunkPos.asLong(x>>4,z>>4),k->new Column[256]);
                        int i=(z&15)*16+(x&15);
                        if(columns[i]==null)columns[i]=new Column(x,z,box);else columns[i].portal=box;
                    }
            }
    }
    public static void begin(WorldGenLevel level,ChunkPos chunk){
        var columns=CHUNKS.get(chunk.toLong());ACTIVE.set(columns);
        if(columns==null)return;
        for(var c:columns)if(c!=null&&!c.seen){
            c.seen=true;c.beforeH=height(level,c);c.cave=isCave(c,c.beforeH);
            if(!c.cave&&c.portal==null)continue;
            c.minY=level.getMinBuildHeight();c.before=new BlockState[level.getHeight()];
            for(int y=0;y<c.before.length;y++)c.before[y]=level.getBlockState(new BlockPos(c.x,c.minY+y,c.z));
        }
    }
    public static void end(WorldGenLevel level){
        var columns=ACTIVE.get();
        if(columns!=null)for(var c:columns)if(c!=null&&c.before!=null&&(c.cave||c.portal!=null)){
            c.afterOwn=new BlockState[level.getHeight()];
            for(int y=0;y<c.afterOwn.length;y++)c.afterOwn[y]=level.getBlockState(new BlockPos(c.x,c.minY+y,c.z));
        }
        ACTIVE.remove();
    }
    private static int height(WorldGenLevel level,Column c){
        for(int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,c.x,c.z)-1;y>=level.getMinBuildHeight();y--){
            var p=new BlockPos(c.x,y,c.z);var state=level.getBlockState(p);
            if(cn.wwl.orimod.world.CreekTerrainSurface.standingCandidate(state,level,p))return y-c.originY;
        }
        return Integer.MIN_VALUE;
    }
    private static boolean isCave(Column c,int h){return c.plan!=null&&!c.plan.entrance()&&h<Math.min(c.plan.natural(),c.plan.planned())-6;}
    static boolean cave(net.minecraft.server.level.ServerLevel level,RuinPiece piece,BlockPos p){
        var c=column(p);if(c==null)return false;
        int h=p.getY()-c.originY;
        if(!caveEvidence(c,h,cn.wwl.orimod.world.CreekTerrainSurface.natural(level.getBlockState(p))))return false;
        for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++){
            var at=p.atY(y);var state=level.getBlockState(at);
            if(CreekAuditPlan.ownsGuard(piece,at,state)||CreekAuditPlan.ownsSupport(piece,at,state))return false;
        }
        return true;
    }
    private static boolean caveEvidence(Column c,int h,boolean natural){
        return c.cave&&c.before!=null&&c.afterOwn!=null&&c.writes==0&&Arrays.equals(c.before,c.afterOwn)&&isCave(c,h)&&natural;
    }
    static int verifyCaveProofContract(){
        var c=new Column(0,0,null);c.plan=new CreekAuditPlan.Column(0,0,10,9,false);c.cave=true;
        c.before=new BlockState[]{net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()};c.afterOwn=c.before.clone();
        require(caveEvidence(c,2,true),"untouched cave");
        require(!caveEvidence(c,3,true),"strict H < min(N,P)-6");
        require(!caveEvidence(c,2,false),"natural only");
        c.writes=1;require(!caveEvidence(c,2,true),"identical own write is disqualifying");c.writes=0;
        c.afterOwn[0]=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();require(!caveEvidence(c,2,true),"changed column");
        c.afterOwn=null;require(!caveEvidence(c,2,true),"unknown after snapshot");c.afterOwn=c.before.clone();
        c.plan=new CreekAuditPlan.Column(0,0,9,10,false);require(!caveEvidence(c,3,true),"min includes natural");
        c.plan=new CreekAuditPlan.Column(0,0,10,9,true);require(!caveEvidence(c,2,true),"approach is owned");
        c.plan=new CreekAuditPlan.Column(0,0,10,9,false);c.cave=false;require(!caveEvidence(c,2,true),"cave must predate writes");
        return 9;
    }
    private static void require(boolean value,String name){if(!value)throw new IllegalStateException("Cave proof negative control: "+name);}
    static Map<String,Object> caveProof(BlockPos p){
        var c=column(p);var m=new LinkedHashMap<String,Object>(proof(c));
        m.put("natural",c.plan.natural());m.put("planned",c.plan.planned());m.put("beforeH",c.beforeH);
        m.put("finalH",p.getY()-c.originY);m.put("caveThreshold",Math.min(c.plan.natural(),c.plan.planned())-6);
        m.put("comparisonPhase","complete column immediately before/after own slice; final H still below threshold");
        m.put("kind","cave_internal_natural_drop");return m;
    }
    public static void wrote(BlockPos p){
        var columns=ACTIVE.get();if(columns==null)return;
        var c=columns[(p.getZ()&15)*16+(p.getX()&15)];
        if(c!=null&&c.x==p.getX()&&c.z==p.getZ())c.writes++;
    }
    private static Column column(BlockPos p){
        var a=CHUNKS.get(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));
        return a==null?null:a[(p.getZ()&15)*16+(p.getX()&15)];
    }
    static boolean unchanged(net.minecraft.server.level.ServerLevel level,RuinPiece piece,BlockPos from,BlockPos to){
        var a=column(from);var b=column(to);
        if(a==null||b==null||a.portal==null||!a.portal.equals(b.portal)||!a.portal.isInside(from)||!b.portal.isInside(to))return false;
        return unchanged(level,a,(p,s)->CreekAuditPlan.ownsGuard(piece,p,s)||CreekAuditPlan.ownsSupport(piece,p,s))
                &&unchanged(level,b,(p,s)->CreekAuditPlan.ownsGuard(piece,p,s)||CreekAuditPlan.ownsSupport(piece,p,s));
    }
    private static boolean unchanged(net.minecraft.server.level.ServerLevel l,Column c,java.util.function.BiPredicate<BlockPos,BlockState> owned){
        if(c.before==null||c.writes!=0)return false;
        for(int y=0;y<c.before.length;y++){
            var p=new BlockPos(c.x,c.minY+y,c.z);var state=l.getBlockState(p);
            if(state!=c.before[y]||owned.test(p,state))return false;
        }
        return true;
    }
    static Map<String,Object> proof(BlockPos from,BlockPos to){
        return Map.of("kind","foreign_structure_internal_drop","from",List.of(from.getX(),from.getY(),from.getZ()),
                "to",List.of(to.getX(),to.getY(),to.getZ()),"portal",column(from).portal.toString(),
                "columns",List.of(proof(column(from)),proof(column(to))));
    }
    private static Map<String,Object> proof(Column c){
        var states=new ArrayList<Map<String,Object>>();
        for(int y=0;y<c.before.length;y++)if(!c.before[y].isAir())states.add(Map.of("y",c.minY+y,"state",c.before[y].toString()));
        return Map.of("x",c.x,"z",c.z,"ourWriteAttempts",c.writes,"beforeStage","immediately before first Ori ruin/approach slice",
                "unchangedFullBuildHeight",true,"minimumY",c.minY,"height",c.before.length,"nonAirBefore",states);
    }
    static void proofContract(net.minecraft.gametest.framework.GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));
        var c=new Column(p.getX(),p.getZ(),new BoundingBox(p));
        java.util.function.BiPredicate<BlockPos,BlockState> never=(at,state)->false;
        h.assertFalse(unchanged(l,c,never),"Unknown before snapshot was waived");
        c.minY=l.getMinBuildHeight();c.before=new BlockState[l.getHeight()];
        for(int y=0;y<c.before.length;y++)c.before[y]=l.getBlockState(new BlockPos(c.x,c.minY+y,c.z));
        h.assertTrue(unchanged(l,c,never),"Untouched foreign column rejected");
        c.writes=1;
        h.assertFalse(unchanged(l,c,never),"An identical-state write was waived");
        c.writes=0;
        h.assertFalse(unchanged(l,c,(at,state)->at.equals(p)),"Owned column was waived");
        l.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        h.assertFalse(unchanged(l,c,never),"Changed column was waived");
        l.setBlock(p,c.before[p.getY()-c.minY],2);
        h.assertTrue(unchanged(l,c,never),"Restored no-write fixture rejected");
        h.assertValueEqual(verifyCaveProofContract(),9,"Cave negative controls");
        h.succeed();
    }
    private CreekForeignTerrainTrace(){}
}
